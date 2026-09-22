## Context

见 `proposal.md` 的动机和 `specs/aggregation-query-infrastructure/spec.md` 的行为契约。

当前 `portal-api` 已有三类可复用基础：`NewApiLogClient` 能按成功/错误类型读取单页日志，`QuotaMoneyConverter` 能执行 quota 换算，Caffeine 与 Actuator/Micrometer 已在依赖中。但现有 `UsageTimeRange` 只处理起止时间，不携带时区、粒度和口径版本；`UsageQueryService` 依赖的 `/api/log/self/stat` 与 `/api/data/self` 又分别被 P2-01 证实为时间过滤不生效和条件可用，不能直接升级为第二阶段正式聚合基础。

冻结基线 `p2-2026-09-22-a` 只可靠支持小规模 `/api/log/self` 扫描。典型 24 小时、7 天、30 天负载、跨 UTC 日界、DST 样例和 quota/USD 换算仍缺独立证据。因此本设计优先建立“可完整读取或明确失败”的同步单体内聚合路径，使用保守上限上线，并把预聚合存储保留为有证据后单独决策。

## Goals / Non-Goals

**Goals:**

- 为后续 Dashboard、消费分析和日志增强提供同一套不可变查询上下文、分桶、读取预算、字段支持状态、缓存和观测组件。
- 让所有成本边界在访问上游前或读取过程中可判定，任何不完整结果都不能穿过聚合边界。
- 在不新增外部基础设施的前提下合并同键并发查询，并保证用户、时区、口径和筛选条件隔离。
- 将 `p2-2026-09-22-a` 的已验证结论与条件/不可用结论固化为可测试策略，而不是仅依赖开发者阅读文档。

**Non-Goals:**

- 不实现 `/portal/api/dashboard/stats`、消费汇总、统一流水、模型详情或任何前端页面。
- 不改变第一阶段 `/portal/api/usage/**` 的公开契约；它们不作为第二阶段正式指标来源，后续由 P2-03 决定迁移或替代方式。
- 不实现 Portal 数据库、Redis、消息队列、小时统计表或跨实例缓存一致性。
- 不在缺少新证据时开放 30 天实时日志扫描、确认 type=5 失败语义或输出 USD 消费金额。

## Decisions

### 1. 按现有技术分层拆分聚合基础，不建立新的业务大包

实现分布在现有职责层次中：

- `base/aggregation`：时间上下文、粒度、桶边界、字段支持状态、读取预算和 Portal 自有聚合记录；这些类型不依赖 Spring Web 或 New API DTO。
- `upstream/newapi/log`：补齐 `token_id` 等基线已验证字段，并提供只负责单页访问的适配器。
- `infrastructure/aggregation`：受控分页协调、Caffeine 缓存/并发合并和 Micrometer 观测。
- `config`：聚合配置绑定与启动校验。

后续业务查询服务通过这些小接口组合能力，不机械增加只有单一实现的 service 接口。公共层不得返回 `NewApiLogPage`、`NewApiLogRecord` 等上游类型。

备选方案是建立独立 `aggregation-service` 模块，但当前只有一个 Spring Boot 单体和一个上游，新增模块会增加依赖与构建复杂度，且没有隔离收益。另一方案是继续把逻辑放进各 `web/*QueryService`，会复制时间、分页和缓存规则，无法保证口径一致，故不采用。

### 2. 查询上下文先规范化，再执行数据源级成本策略

`AggregationQueryContext` 保存：

- UTC `start`/`end` Instant；
- 原始 `ZoneId`；
- `AggregationGranularity`；
- `baselineVersion`；
- 由调用方单独提供、最终进入缓存键的规范化筛选条件。

解析器要求起止时间均为带偏移量的 ISO 8601 且无亚秒，严格执行 `[start,end)`。`timezone` 独立解释自然日和分桶，不要求 ISO 字符串中的 offset 与该 ZoneId 当前偏移相同：前端可以发送 UTC 的 `Z` 边界，同时用 `Asia/Shanghai` 表达分桶与展示时区。

粒度初始定义为 `FIVE_MINUTES`、`HOUR`、`DAY`。语法策略默认分别允许最长 24 小时、7 天和 30 天，用于限制桶数量；数据源策略另行限制 `/api/log/self` 当前最大实时扫描为 7 天，并始终受 10 页/200 条预算约束。这样“能生成 30 天日桶”不等于“可以实时扫描 30 天日志”，避免把表现层粒度与数据源成本混为一谈。

非法查询统一在业务接口进入上游前映射为 `INVALID_ARGUMENT`。读取首屏后才发现数据量超过预算时也返回 `INVALID_ARGUMENT`，使用安全消息提示缩小范围；总预算耗尽映射为 `UPSTREAM_TIMEOUT`，分页协议矛盾映射为 `UPSTREAM_ERROR`。本变更不新增公开错误码。

备选方案是让每个业务接口自行决定默认范围和粒度；这会使同一时间参数在 Dashboard 与钱包中产生不同结果。另一方案是强制时间字符串 offset 与 ZoneId 匹配，会拒绝“UTC 边界 + 用户时区”这一合法且常见的调用形式，故不采用。

### 3. 桶边界在用户时区生成，归桶比较统一使用 Instant

`AggregationBucketPlan` 先从查询起点生成连续 `[bucketStart,bucketEnd)`：五分钟桶按绝对时间推进，小时和自然日桶使用 `ZonedDateTime` 在用户时区推进，再将边界转换为 Instant。首尾非整桶范围允许产生裁剪桶，确保完整覆盖查询范围且不扩张查询。

记录只通过 Instant 与桶边界比较，结果按 `bucketStart` 排序。空桶在计划阶段就存在，聚合器只向已有桶累加；某项指标不可用时，该指标值保持 `null`/不可用状态，不能因为桶存在而填零。整数累加使用精确加法并捕获溢出，quota 使用 `BigDecimal` 累计原始整数值。

备选方案是全部按固定 Duration 推进，代码更短但会把 DST 自然日固定成 24 小时，并在回退时混淆重复小时，故不采用。

### 4. 使用单页数据源加共享 `AggregationReadBudget` 完成同步受控读取

受控读取器不接管 HTTP 协议，只接收“按页读取”的函数和本次聚合共享的 `AggregationReadBudget`。默认每页 20 条，预算为最多 10 页、200 条、单次调用最多 5 秒、总计最多 30 秒。成功日志、错误日志或未来充值记录若在一次业务聚合中组合，必须使用同一个预算实例，不能各自获得 200 条额度。

读取流程为：

1. 调用前检查剩余时间、页数和记录预算；
2. 读取第一页并冻结其 `total`，若 `total` 已超过剩余记录预算则立即拒绝；
3. 后续页必须保持相同 `total`，非末页必须返回预期页大小，页序指纹不得重复；
4. 累计数量达到 `total` 后才返回；提前空页、异常短页、总数变化或超预算全部失败；
5. 最终再次按 `[start,end)` 检查记录，边界外数据视为上游契约异常，不静默丢弃。

页序指纹只用于本次内存读取的重复页检测，可由页号、首尾稳定字段及记录数构成，不写入日志或缓存。固定 `endTime` 能避免读取期间新请求进入范围；若上游仍因迟到数据导致 `total` 变化，选择失败而非估算。读取器不自动重试整次多页查询，以免放大上游负载；失败结果由调用方在新请求中重试。

单次调用超时复用现有 New API HTTP 客户端的 read timeout，并校验聚合配置不得声明比客户端更宽松但实际无法执行的值。总预算在每次调用前后检查；每个在途调用最迟受单次 HTTP 超时约束。

备选方案是看到短页就直接结束，无法区分正常末页与截断。另一方案是无上限读取到 `total`，会把成本风险转移给请求线程与上游，均不采用。

### 5. 字段支持状态与规范化记录共同阻止未经验证的计算

增加代码内可枚举的 `AggregationBaselinePolicy`，只支持显式列出的基线版本。应用配置的版本不在支持列表时启动失败。策略为每项字段或派生指标返回 `VERIFIED`、`DIFFERENT`、`CONDITIONAL`、`UNAVAILABLE`，并由聚合器在计算前检查，而不是仅在最终 DTO 阶段隐藏结果。

`NewApiLogEntryRaw` 与映射结果补充可空 `tokenId`。随后转换为 Portal 自有 `AggregationLogRecord`，包含发生时间、日志结果、tokenId、模型、requestId、输入/输出 Token、耗时、流式标记和原始 quota。缺失字段保留缺失语义；不会因一条历史记录缺字段而伪造默认值。

当前基线下：Token 用量、活跃 Key 和平均延迟可按已验证字段计算；请求总数、成功率和 USD 消费金额仍为条件可用。原始 quota 可以用 `BigDecimal` 汇总用于后续核对，但 `QuotaMoneyConverter` 的配置默认值不能让聚合金额升级为已验证。待 `quotaPerUsd` 取得独立证据后，发布新基线并同时更新策略和契约测试。

备选方案是运行时解析 `docs/new-api/aggregation-baseline.json`。该文件是设计/验收证据而非生产配置，运行时读取会把文档部署、解析失败和安全边界引入主链，故采用代码策略加 fixture 契约测试保持同步。

### 6. 使用现有 Caffeine 实现进程内短缓存和同键 single-flight

`AggregationCacheKey` 由操作命名空间、用户 ID、UTC 起止时间、粒度、IANA 时区 ID、基线版本和排序后的规范化筛选条件组成。会话、Cookie、完整 Key 和原始响应不进入键；完整 API Key 筛选若未来存在，必须先映射为非可逆稳定内部标识或禁止缓存，不能直接落入键。

缓存只保存不可变的 Portal 自有最终投影，默认 TTL 30 秒、最大 1,000 条。使用 Caffeine 的原子 `get(key, mappingFunction)` 合并同 JVM 同键并发加载；加载抛出的异常不会形成缓存值，占位会被清理。调用方等待同一加载结果，但每个实际上游加载仍受 30 秒总预算约束。

缓存是性能优化，不参与正确性。多实例之间允许短期重复计算与 TTL 内轻微时间差；所有结果仍由显式时间范围决定。P2-02 不做主动失效，因为 New API 没有可靠的用户日志变更事件；短 TTL、明确边界和口径版本已经限制陈旧窗口。

备选方案是 Redis/共享缓存，当前没有跨节点一致性或负载证据，新增运维依赖不符合单体优先原则。手写 `ConcurrentHashMap<CompletableFuture>` 可以实现 single-flight，但还需自行处理 TTL、容量和异常清理，已有 Caffeine 更稳妥。

### 7. 配置使用 `.properties` 并在所有环境显式可见

在 `PortalCommonProperties` 下增加 `aggregation` 配置组，核心键为：

- `baseline-version=p2-2026-09-22-a`
- `page-size=20`
- `max-pages=10`
- `max-records=200`
- `single-call-timeout=5s`
- `total-timeout=30s`
- `max-live-log-range=168h`
- `cache.ttl=30s`
- `cache.maximum-size=1000`
- 三种粒度各自的最大跨度

共享安全默认值放在 `application.properties`。`application-dev.properties`、`application-test.properties`、`application-prod.properties` 显式声明环境是否允许覆盖及对应值；prod 只允许通过受控环境变量收紧或在新性能基线发布后显式放宽，test 使用更短 TTL/预算以验证边界。启动校验覆盖正值、`pageSize × maxPages` 与 `maxRecords` 的关系、single-call/total timeout 关系、粒度跨度单调性和支持的基线版本。

不新增 YAML。30 天实时日志扫描没有单独的 boolean 绕过开关；只有发布新基线并把 `max-live-log-range` 与代码支持策略一起调整，才能启用，避免单个环境变量绕过证据门禁。

### 8. 观测使用低基数 Micrometer 指标，requestId 只进入安全日志

复用 Actuator/Micrometer，记录：

- 聚合耗时与结果：`operation`、`outcome`；
- 上游调用、页数、记录数：`source`、`outcome`；
- 缓存事件：`operation`、`cacheOutcome`（hit/miss/coalesced）；
- 保护性拒绝：`operation`、`reason`（range/pages/records/deadline/inconsistent-page）。

所有 tag 都来自代码枚举。用户 ID、模型、时间范围、requestId 和异常文本不进入指标 tag。结构化警告日志可包含当前 requestId、固定原因和计数，不包含用户身份、筛选值、会话或上游响应。这样既能在 P2-10 统计拒绝率和缓存命中，又不会产生高基数或泄密。

## Risks / Trade-offs

- [200 条/10 页的保守限制可能让真实用户频繁被拒绝] → 记录保护性拒绝和实际首页总数区间；取得典型数据量性能证据后发布新基线并显式调整，不在运行时自动放宽。
- [New API offset 分页缺少快照，迟到数据仍可能改变页总数] → 固定查询 endTime、校验总数和页形状；发现不一致时整体失败，不返回近似结果。
- [进程内缓存无法跨实例合并请求] → 接受短 TTL 下的重复计算，P2-10 用上游放大指标判断是否确需共享缓存，再单独设计。
- [同步 single-flight 会占用等待线程] → 每次加载受单次与总超时约束，且不嵌套获得第二份预算；暂不引入异步框架增加复杂度。
- [代码策略与基线 JSON 可能漂移] → 用现有真实 fixture 和 baseline JSON 做契约测试，校验版本、字段状态与策略一致；未知版本启动失败。
- [P2-01 尚未验证 quota/USD、type=5、DST 真实样例和 30 天性能] → 对应正式值保持不可用，DST 先通过 JDK ZoneRules 确定性单测验证，真实证据补齐后升级基线而非修改旧版本含义。
- [现有 `/portal/api/usage/**` 与新基础并存造成理解成本] → 文档明确其为第一阶段基础用法能力；P2-02 不悄然改变公开结果，P2-03 再决定兼容迁移。

## Migration Plan

1. 先增加配置绑定、启动校验和对应失败测试，在四份 `.properties` 中显式记录共享值与环境差异。
2. 增加时间上下文、粒度和桶计划的单元测试与实现，覆盖左右边界、裁剪桶、空桶和 DST 23/25 小时场景。
3. 扩展日志 raw/mapper 以保留 `token_id`，增加冻结 fixture 契约测试，再实现 Portal 自有记录和字段支持策略。
4. 以伪分页源先建立共享读取预算的失败测试，再接入 `NewApiLogClient`，覆盖完整多页、总数变化、重复页、提前空页、上限和超时。
5. 增加 Caffeine 缓存隔离、同键并发、失败清理和 TTL/容量测试；随后接入低基数 Micrometer 观测。
6. 建立一个不暴露新路由的纵向聚合测试夹具，证明“查询解析 → 分页 → 规范化 → 分桶 → 缓存”完整链路可被 P2-03 等后续需求复用。
7. 使用指定 Maven 配置运行 `portal-api` 测试与可执行的根工程验证，执行 OpenSpec 严格校验、`git diff --check` 和 `git status`；只有适用验收通过后才更新阶段文档中的 P2-02 状态。

本变更没有数据库迁移或外部资源。回滚时移除未被下游使用的聚合组件与配置即可；如果已有下游变更依赖它，必须先回滚下游。缓存仅在进程内存中，重启即清空，不需要数据清理。
