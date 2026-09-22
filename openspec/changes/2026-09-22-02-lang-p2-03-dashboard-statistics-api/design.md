## Context

见 `proposal.md` 的动机和 `specs/dashboard-statistics/spec.md` 的外部行为契约。

P2-02 已经落地 `AggregationQueryContext`、`AggregationBucketPlan`、`AggregationBaselinePolicy`、`AggregationLogReader`、共享 `AggregationReadBudget`、Caffeine 聚合缓存和低基数观测。现有 Dashboard 则仍分别调用余额、`/portal/api/usage/summary` 和 `/portal/api/usage/timeseries`；后两者依赖已被 P2-01 判定存在差异或条件可用的上游统计接口，不能作为 P2-03 正式契约的数据源。

当前 `p2-2026-09-22-a` 明确支持从 type=2 日志计算 Token 用量、活跃 Key 和平均延迟，但请求总数依赖未验证的 type=5，消费金额依赖未验证的 `quotaPerUsd`，成功率同时依赖两者。P2-02 还把实时日志扫描限制为 7 天、10 页和 200 条；因此 P2-03 必须先交付诚实的部分可用接口，而不能假装完成 30 天正式统计。

## Goals / Non-Goals

**Goals:**

- 用一个 HTTP 请求返回同一日志快照派生的查询元数据、六项指标状态、趋势状态和最近请求。
- 让前端只根据稳定 `availability/reasonCode` 决定展示，不再通过 `null`、零值或异常文本猜测数据含义。
- 复用 P2-02 已验证的查询、预算、缓存和观测组件，不建立第二套统计基础。
- 保持接口结构可承载后续新基线，但当前只计算已有证据支持的内容。

**Non-Goals:**

- 不修改 `DashboardPage`、React Query hooks、图表或快捷范围；这些属于 P2-04。
- 不迁移、删除或修正第一阶段 `/portal/api/usage/**`，避免在 P2-03 产生无关兼容变化。
- 不为填满六项指标读取 `/api/log/self/stat`、`/api/data/self`，也不把 raw quota 换算成美元。
- 不引入预聚合表、数据库、Redis 或 30 天特殊旁路。

## Decisions

### 1. 在 `web/dashboard` 建立一个薄控制器和一个聚合查询服务

新增结构保持现有传统技术分层：

- `DashboardStatsController`：路由、参数白名单、认证会话绑定、异常翻译和统一响应包装；
- `DashboardStatsQueryService`：缓存编排、共享预算读取和单快照计算；
- `DashboardStatsCalculator`：纯内存指标、可用性与最近请求投影；
- `Dashboard*Dto` 与小型枚举：只表达 Portal API 契约。

控制器不直接访问 `NewApiLogClient`，calculator 不依赖 Spring、HTTP 或上游 DTO。查询服务直接组合已有具体实现，不为每个类机械新增单实现接口；测试通过构造参数和现有可替换客户端边界隔离。

备选方案是扩展 `UsageController`，但新接口的显式时区、粒度、可用性和单快照语义与旧 `/usage/**` 不同，混在同包会模糊兼容边界。另一方案是建立独立 Maven 模块，当前单接口没有对应隔离收益。

### 2. 公共参数严格限制为四项，口径版本只由服务端决定

控制器使用查询参数 Map 白名单，只允许 `startTime`、`endTime`、`granularity`、`timezone`。四项都必须非空；粒度按 `FIVE_MINUTES`、`HOUR`、`DAY` 精确解析，时间和时区交给 `AggregationQueryContext`。任何 `IllegalArgumentException` 在控制器边界转换为安全 `INVALID_ARGUMENT`，不把内部校验文本直接暴露。

`baselineVersion` 读取 `lang.portal.aggregation.baseline-version`，不允许客户端选择旧口径或虚构版本。响应回显实际使用的版本与 UTC 规范化范围。认证继续使用 `@ProtectedEndpoint` 和现有会话 Cookie；控制器校验 `PortalAuthenticatedUser.id` 与构造出的 `NewApiSession.userId` 一致，缓存键使用认证主体 ID。

备选方案是允许可选参数和最近 24 小时默认值，但第二阶段总纲要求前端把快捷范围转换为明确边界，后端默认会让缓存与验收范围不可解释。允许客户端传口径版本也会形成未测试的多版本运行路径，均不采用。

### 3. 当前查询只读取一次 type=2 日志并生成完整响应数据

缓存未命中时，查询服务执行：

1. 根据四项参数和服务端版本创建 `AggregationQueryContext`；
2. 创建截止时间为当前时刻加 `totalTimeout` 的 `AggregationReadBudget`；
3. 通过 `AggregationLogReader.readSuccessLogs` 完整读取 type=2 记录；
4. 将同一不可变 List 交给 calculator，同时生成六项指标、趋势状态和最近请求；
5. 只有 calculator 完整成功后才把 `DashboardStatsData` 放入缓存。

当前不调用 `readErrorLogs`：type=5 语义尚未验证，即使读取结果为空也不能证明没有失败请求；额外读取只会消耗共享预算而无法产生正式指标。最近请求因此明确标为部分覆盖。新基线若确认 type=5，需要通过独立 OpenSpec 变更把错误日志加入同一预算和同一快照计算，而不是静默改变当前响应含义。

保护上限、分页不一致、转换异常或上游错误直接穿过既有安全错误映射，使整个请求失败。字段级 `UNAVAILABLE` 只用于“证据不足、无数据或某个允许为空的来源字段缺失”，不能用于掩盖操作失败。

备选方案是并行调用 summary、hourly、成功日志和错误日志后拼装，数据时点不同且部分接口已被证实不可靠；另一方案是各板块失败隔离，会让一次响应的指标和趋势来自不同快照，均不采用。

### 4. 用显式 DTO 类型表达指标值，不使用 `Object` 或隐式 null

`DashboardStatsData` 包含：

- `baselineVersion`；
- `DashboardRangeDto`；
- `DashboardMetricsDto`；
- `DashboardRequestTrendDto`；
- `DashboardSpendTrendDto`；
- `DashboardRecentRequestsDto`。

可用性枚举为 `AVAILABLE`、`PARTIAL`、`UNAVAILABLE`；指标只允许 AVAILABLE/UNAVAILABLE，集合才允许 PARTIAL。原因枚举初始只包含 `BASELINE_NOT_VERIFIED`、`NO_DATA`、`SOURCE_FIELD_MISSING`、`PARTIAL_SOURCE_COVERAGE`。

为保持 JSON 类型稳定，采用三种小 DTO，而不是 `Object value`：

- 计数指标：可空 `Long value`；
- 比率/平均值指标：可空 `BigDecimal value`；
- 金额指标：可空十进制字符串 `value` 与可空 `currency`。

每个 DTO 都携带固定 unit、availability 和 reasonCode，并在构造时校验合法组合：AVAILABLE 必须有值且无原因；UNAVAILABLE 必须无值且有原因。这样非法状态无法进入缓存或序列化层。

备选方案是只返回裸 `null`，前端无法区分无数据、证据不足和字段缺失；通用 `Object value` 则把类型错误推迟到运行时，均不采用。

### 5. 三项已验证指标按 type=2 记录计算，其他指标由基线策略直接裁决

calculator 先查询 `AggregationBaselinePolicy`，再计算：

- `tokenUsage`：对所有 type=2 记录执行 `inputTokens + outputTokens` 精确累计；空列表为 AVAILABLE/0；
- `activeKeys`：对非空 tokenId 去重；只要任一纳入记录缺 tokenId，整项为 `UNAVAILABLE + SOURCE_FIELD_MISSING`，不返回偏小计数；空列表为 AVAILABLE/0；
- `averageLatency`：使用 durationMs 总和除以 type=2 记录数，以毫秒 `BigDecimal` 返回，最多 3 位小数、`HALF_UP`、去除非必要尾零；空列表为 `UNAVAILABLE + NO_DATA`；
- `requestTotal`、`spend`、`successRate`：当前策略不是 VERIFIED，直接返回 `UNAVAILABLE + BASELINE_NOT_VERIFIED`，不执行猜测计算。

累计复用 `AggregationAccumulator` 与精确溢出检查。calculator 不调用 `QuotaMoneyConverter`，也不把 type=2 数量作为 requestTotal 或成功率分母。后续基线启用某指标时，必须先让策略变为 VERIFIED，并增加真实 fixture 复算后才能进入 AVAILABLE 分支。

### 6. 当前趋势返回明确不可用，不生成无意义桶或隐藏计算

请求趋势与消费趋势的 DTO 分别固定单位和点类型。当前基线下 calculator 直接返回：

- `availability=UNAVAILABLE`；
- `reasonCode=BASELINE_NOT_VERIFIED`；
- `points=[]`；
- 消费趋势的 currency 为 null。

不调用 `AggregationBucketPlan` 生成一串 null/零点，也不在后台计算 raw quota 后隐藏，因为这些工作不能改善当前契约且容易被后续误用。新基线正式启用趋势时，再复用已有桶计划，以同一记录集逐桶累计，并增加“桶合计等于汇总”的契约测试。

备选方案是返回 type=2 数量趋势并命名为请求趋势，用户会把“成功消费日志”误解为全部请求；返回 raw quota 趋势同样会被误解为货币消费，均不采用。

### 7. 最近请求只投影安全字段，并显式声明部分覆盖

calculator 从同一 type=2 List 中排序并截取 10 条。排序键依次为：`occurredAt` 倒序、非空 requestId 字典序、非空 tokenId 数值、非空 model 字典序、Token 与耗时；若两条记录所有键相同，它们的公开投影也等价，不影响稳定输出。

每条 DTO 包含时间、可空 requestId/keyName/model、`outcome=SUCCESS`、输入/输出 Token、durationMs 和 stream。tokenId 只参与内部去重和排序，不进入 DTO；raw quota、渠道、节点和原始错误均不返回。缺少可空展示字段时保留记录并返回 null。

集合固定为 `PARTIAL + PARTIAL_SOURCE_COVERAGE`，即使 items 为空也不声称已覆盖所有错误来源。P2-04 可据此展示口径提示，而不是把空列表解释为“没有任何请求”。

### 8. 缓存完整业务数据，HTTP requestId 始终在缓存外生成

查询服务持有按现有 aggregation 配置创建的 `AggregationCache<DashboardStatsData>`，操作命名空间固定为 `dashboard-stats`，filters 为空。键自动包含用户、UTC 范围、粒度、时区和基线版本。

缓存值不包含顶层 ApiResponse 和 requestId。控制器每次都用 `ApiResponses.ok(request,data)` 重新包装，因此缓存命中不会复用旧请求标识。异常不会进入缓存；同键并发由已有 single-flight 行为合并。

复用 `AggregationMetrics` 记录 operation=`dashboard-stats` 的聚合结果、缓存 hit/miss/coalesced 和日志读取计数。既有指标白名单需要显式加入该 operation，不能让用户、模型或时间范围成为 tag。

备选方案是缓存完整 HTTP 响应，可能复用 requestId 并把传输层状态带入业务缓存；按板块拆多个缓存又会破坏同快照语义，均不采用。

### 9. 30 天限制作为显式验收阻塞，不在 P2-03 内解决

`DAY` 粒度允许表达 30 天范围，但 `AggregationLogReader` 会在分页前根据 `maxLiveLogRange=168h` 拒绝。P2-03 保持该行为并测试“零次上游调用”。

只有两条路径可以解除：

1. P2-01 补充典型数据量的 30 天实测，发布新基线并证明扩大实时范围仍满足目标；
2. 实测证明实时扫描不可行，先通过独立设计批准预聚合存储，再让 Dashboard 选择该已验证数据源。

因此实现完成后可以报告接口在当前受支持范围内通过，但 `docs/11_第二阶段聚合能力开发与子需求拆分.md` 的 P2-03 状态必须保持“受阻”或由用户明确接受限制，不能因接口存在就标“已完成”。

## Risks / Trade-offs

- [六项指标中三项和两类趋势当前不可用，首版接口信息密度较低] → 用稳定 DTO 提前固定诚实契约；P2-04 按 availability 展示，不用旧统计接口补造正式值。
- [只读取 type=2 导致最近请求不是全量] → 集合明确标记 PARTIAL；type=5 证据补齐后通过显式基线变更加入同一预算。
- [单次接口失败会让所有板块不可用] → 这是同快照一致性的选择；字段证据不足仍可局部表达，只有操作失败才整体失败。
- [200 条保护上限可能让 7 天查询也被拒绝] → 返回明确参数错误并记录低基数保护指标；不从已读取部分估算。
- [平均延迟三位小数涉及舍入] → 累计阶段保持整数毫秒精度，只在最终除法 DTO 阶段 HALF_UP，fixture 测试固定结果。
- [当前响应结构为未来趋势预留字段] → 只预留总纲明确要求的两类趋势，不增加同比、环比、模型分组等未要求能力。
- [旧 `/usage/**` 与新接口短期并存] → P2-03 不改旧行为，P2-04 一次性迁移 Dashboard；文档明确两者口径不同。

## Migration Plan

1. 先以 JSON 契约测试冻结参数白名单、响应字段、可用性枚举、原因枚举和当前基线下的 null/零值规则。
2. 增加 calculator 失败测试，再实现三项已验证指标、三项不可用指标、两类不可用趋势和最近请求安全投影。
3. 增加 query service 测试，再接入 P2-02 查询上下文、单次成功日志读取、共享预算、完整响应缓存和低基数观测。
4. 增加受保护控制器，覆盖认证主体与会话一致、未知参数、非法时间/时区/粒度、统一响应与既有错误映射。
5. 增加纵向 MockWebServer 测试，验证一份多页日志只读取一次、缓存命中不重复访问、保护/上游失败不返回部分结果、30 天拒绝时零上游调用。
6. 新增 LANG-P2-03 接口说明并更新文档索引，记录响应示例、可用性原因、当前 7 天/200 条限制和 30 天阻塞；不修改前端文档为已接入。
7. 运行指定 Maven 配置下的 `portal-api` 测试与根工程 `verify`、OpenSpec 严格校验、`git diff --check` 和 `git status`。只有 30 天外部条件解决或用户明确接受限制时，才把阶段文档中的 P2-03 标为已完成。

本变更没有数据库迁移和新配置。回滚时删除新路由、`web/dashboard` 代码和接口说明即可；旧 Dashboard 与 `/usage/**` 始终保留，因此无需前端同步回滚。
