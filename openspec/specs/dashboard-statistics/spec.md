# dashboard-statistics Specification

## Purpose

为已登录用户提供单一、稳定且可解释的 Dashboard 服务端聚合契约，使六项指标、趋势和最近请求来自同一受控数据快照，并明确表达当前证据无法支持的值。

## Requirements

### Requirement: Dashboard 统计接口使用显式查询契约
系统 SHALL 提供受保护的 `GET /portal/api/dashboard/stats`。接口 MUST 只接受 `startTime`、`endTime`、`granularity` 和 `timezone` 四个必填查询参数，并复用聚合基础设施对带偏移量 ISO 8601 秒级时间、`[startTime,endTime)`、IANA 时区、粒度和跨度组合的校验。接口 MUST NOT 接受 `1H`、`24H`、`7D`、`30D`、`今天` 等页面快捷范围、客户端口径版本、用户 ID 或未声明参数。

#### Scenario: 已登录用户查询合法范围
- **WHEN** 已登录用户提供合法的四个查询参数
- **THEN** 系统只查询当前会话用户的数据，并使用服务端配置的冻结口径版本返回统一 `{requestId,data}` 响应

#### Scenario: 查询参数缺失或包含未知参数
- **WHEN** 任一必填参数缺失、值非法或请求包含未声明参数
- **THEN** 系统在访问 New API 前返回 `INVALID_ARGUMENT`/400，不补默认范围、不推断时区也不忽略未知参数

#### Scenario: 未认证用户访问统计接口
- **WHEN** 请求没有有效 Portal 会话
- **THEN** 系统返回统一 `UNAUTHENTICATED`/401，且不访问任何用户级上游接口

#### Scenario: 客户端尝试指定其他用户
- **WHEN** 请求通过查询参数、Header 或其他公开输入携带用户标识
- **THEN** 系统拒绝未声明输入或忽略非契约 Header，只使用认证主体绑定的上游会话，不允许跨用户查询

### Requirement: 响应明确回显规范化范围和口径
成功响应的数据 MUST 包含 `baselineVersion`、规范化 `range`、`metrics`、`requestTrend`、`spendTrend` 和 `recentRequests`。`range` MUST 包含 UTC 规范化后的 `startTime`、`endTime`、原始 IANA `timezone` 和稳定 `granularity`，使前端无需根据本地状态猜测实际统计边界。字段名称与枚举 MUST 由 Portal API 所有，不得暴露 New API 原始包装或 DTO。

#### Scenario: 不同偏移量表示相同时间范围
- **WHEN** 两次请求使用不同 ISO 偏移量但表示相同 Instant 范围，并使用相同 IANA 时区和粒度
- **THEN** 响应回显相同 UTC 起止边界和口径版本，并产生相同统计语义

#### Scenario: 响应不暴露上游结构
- **WHEN** Dashboard 聚合成功
- **THEN** 响应只包含声明的 Portal 字段，不包含 New API 的 `success`、`message`、原始 `items`、渠道、节点、供应商或私网信息

### Requirement: 六项指标同时返回值和可用性
`metrics` SHALL 固定包含 `requestTotal`、`tokenUsage`、`spend`、`activeKeys`、`successRate` 和 `averageLatency` 六项。每项 MUST 包含可空 `value`、可空 `unit`、`availability` 和可空 `reasonCode`；`availability` 只允许 `AVAILABLE` 或 `UNAVAILABLE`。`AVAILABLE` 时 MUST 有合法值且 `reasonCode=null`；`UNAVAILABLE` 时 MUST 有稳定原因且 `value=null`，不得用零、100% 或估算值代替缺失语义。

在 `p2-2026-09-22-a` 下，系统 MUST 仅将 Token 用量、活跃 Key 和有分母时的平均延迟标为 `AVAILABLE`；请求总数、消费总额和成功率 MUST 返回 `UNAVAILABLE + BASELINE_NOT_VERIFIED`。整数计数使用 JSON 整数；平均延迟以毫秒十进制值返回；未来可用的成功率使用 0 至 1 的十进制比率，金额使用十进制字符串和明确币种。

#### Scenario: 当前基线计算已验证指标
- **WHEN** 范围内存在基线已验证的 type=2 请求日志
- **THEN** `tokenUsage` 等于输入与输出 Token 精确总和，`activeKeys` 等于非空 token ID 去重数，`averageLatency` 等于总耗时除以记录数且单位为毫秒

#### Scenario: 当前基线保持三项指标不可用
- **WHEN** 使用 `p2-2026-09-22-a` 查询任意范围
- **THEN** `requestTotal`、`spend` 和 `successRate` 的值均为 `null`，可用性为 `UNAVAILABLE`，原因为 `BASELINE_NOT_VERIFIED`

#### Scenario: 空数据范围
- **WHEN** 合法范围内没有已验证请求日志
- **THEN** `tokenUsage` 和 `activeKeys` 返回可用零值，`averageLatency` 返回 `UNAVAILABLE + NO_DATA`，其他三项继续遵循当前基线可用性，不显示零消费、零延迟或 100% 成功

#### Scenario: 活跃 Key 来源字段局部缺失
- **WHEN** 任一纳入范围的已验证请求缺少 token ID
- **THEN** `activeKeys` 返回 `UNAVAILABLE + SOURCE_FIELD_MISSING`，其他可由完整字段计算的指标不受影响

### Requirement: 趋势与汇总使用同一范围和口径
`requestTrend` 与 `spendTrend` MUST 分别包含 `availability`、可空 `reasonCode`、可空单位/币种和按桶开始时间升序的 `points`。可用趋势的每个点 MUST 包含 `[bucketStart,bucketEnd)` 和非空值，完整覆盖响应 `range`，空桶返回确定零值；其分桶合计 MUST 与同响应对应汇总指标在同一精度规则下保持一致。不可用趋势 MUST 返回空 `points` 和稳定原因，不得生成全零曲线或使用其他指标冒充。

在 `p2-2026-09-22-a` 下，请求趋势和消费趋势 MUST 均返回 `UNAVAILABLE + BASELINE_NOT_VERIFIED`，因为请求总数口径和 quota/USD 换算尚未冻结。

#### Scenario: 当前基线不伪造趋势
- **WHEN** 当前基线下存在成功请求日志
- **THEN** 两类趋势仍返回不可用和空点数组，不用成功日志数量冒充全部请求趋势，也不用 raw quota 冒充货币消费趋势

#### Scenario: 后续基线支持趋势
- **WHEN** 后续显式发布的口径版本将对应汇总指标与字段全部标为已验证
- **THEN** 趋势按请求粒度和用户时区返回完整桶，且点值合计与该响应汇总值一致

### Requirement: 最近请求是有边界的安全投影
`recentRequests` MUST 包含 `availability`、可空 `reasonCode` 和 `items`。每个 item 只允许包含 `occurredAt`、可空 `requestId`、可空 `keyName`、可空 `model`、可空 `outcome`、`inputTokens`、`outputTokens`、`durationMs` 和 `stream`；MUST NOT 包含 token ID、完整 API Key、raw quota、渠道、节点、供应商信息或上游原始错误。items MUST 按发生时间倒序和稳定次序返回，最多 10 条，不提供无界列表或独立分页。

在错误日志语义尚未冻结时，系统 SHALL 只把已验证 type=2 记录作为最近请求候选，并将集合标为 `PARTIAL + PARTIAL_SOURCE_COVERAGE`；单条 type=2 记录可将 `outcome` 标为 `SUCCESS`，不得推断未读取或未验证记录的结果。

#### Scenario: 超过最近请求上限
- **WHEN** 范围内存在超过 10 条可安全展示的记录
- **THEN** 系统按稳定排序只返回前 10 条，且不会为了补全 Dashboard 返回其余原始日志

#### Scenario: 当前基线只有成功来源可靠
- **WHEN** 当前基线下查询最近请求
- **THEN** 集合可返回 type=2 的裁剪记录，但明确标记为部分覆盖，不把未验证错误来源描述为不存在

#### Scenario: 最近请求字段缺失
- **WHEN** 某条记录缺少 requestId、keyName 或 model 等可空展示字段
- **THEN** 系统保留该记录并将对应字段返回 `null`，不伪造标识或丢弃其他已验证数据

### Requirement: 单次响应来自同一受控日志快照
系统 SHALL 使用同一个规范化查询上下文、同一份受控日志记录集和同一口径版本计算指标、趋势与最近请求。一次加载 MUST 共用 P2-02 的页数、记录数和总超时预算；系统 MUST 先完成完整读取，再生成响应。达到范围、页数、记录数或超时保护，或分页总数不一致、上游不可用、映射失败时，整个接口 MUST 使用既有稳定错误失败，不得返回来源不一致的部分成功数据。

#### Scenario: 完整快照生成所有板块
- **WHEN** 上游日志在共享预算内完整读取
- **THEN** 六项指标、趋势状态和最近请求均由该同一记录集派生，响应中不存在第二次独立日志读取

#### Scenario: 读取中途达到保护上限
- **WHEN** 总记录数超过 200、页数超过 10、范围超过当前 7 天实时上限或总预算耗尽
- **THEN** 整个请求分别按既有参数或超时错误失败，不返回已读取页计算出的近似指标

#### Scenario: 30 天实时查询尚无证据
- **WHEN** 当前配置和基线下请求需要扫描 30 天日志
- **THEN** 系统在分页前以 `INVALID_ARGUMENT` 拒绝，并且不调用 `/api/log/self/stat`、`/api/data/self` 或浏览器端明细计算作为降级

### Requirement: Dashboard 响应缓存保持用户和查询隔离
系统 SHALL 使用 P2-02 进程内聚合缓存缓存完整成功响应，并使用 `dashboard-stats` 操作命名空间。缓存键 MUST 覆盖认证用户、规范化起止时间、粒度、IANA 时区和口径版本；失败响应不得缓存。相同用户和查询可在 TTL 内复用，同一键并发请求只执行一次上游加载，不同用户、范围、粒度、时区或口径不得共享结果。

#### Scenario: 相同查询命中缓存
- **WHEN** 同一用户在缓存 TTL 内重复提交语义相同的 Dashboard 查询
- **THEN** 系统复用完整响应数据且不再次访问上游，新的 HTTP 响应仍使用当前请求自己的 requestId

#### Scenario: 跨用户查询不复用
- **WHEN** 两个用户提交完全相同的时间与粒度参数
- **THEN** 两次查询使用不同缓存键并只返回各自数据，不因参数相同发生跨用户缓存命中

#### Scenario: 首次加载失败后重试
- **WHEN** 首次同键加载因上游或保护错误失败
- **THEN** 失败不进入缓存，后续请求可以重新读取上游并在成功后缓存完整响应

### Requirement: 新接口不改变第一阶段用量接口
Dashboard 统计接口 SHALL 与现有 `/portal/api/usage/summary`、`/portal/api/usage/timeseries` 并存，旧接口的路径、参数和响应 MUST 保持兼容。LANG-P2-04 起，`/dashboard` MUST 只使用 `/portal/api/dashboard/stats` 作为六项指标、双趋势和最近请求的正式来源；旧接口不得被前端用来补齐不可用指标、趋势或超出实时保护边界的范围。其他既有调用方仍可按原契约使用旧接口。

#### Scenario: P2-04 页面迁移后加载 Dashboard
- **WHEN** 用户访问已部署 P2-04 的 `/dashboard`
- **THEN** 页面以明确的 `startTime`、`endTime`、`granularity` 和 `timezone` 调用新统计接口，不调用旧摘要或小时趋势接口生成概览

#### Scenario: 新接口返回不可用数据
- **WHEN** 新统计接口把指标或趋势标记为不可用
- **THEN** 页面直接呈现该契约状态，不查询旧接口、请求日志分页或其他来源补值

#### Scenario: 第一阶段接口保持兼容
- **WHEN** Dashboard 完成迁移后其他既有调用方继续访问 `/portal/api/usage/summary` 或 `/portal/api/usage/timeseries`
- **THEN** 两个接口仍按原路径、参数、响应和权限契约工作，不因 P2-04 被删除或改变

#### Scenario: P2-03 部署但前端尚未迁移
- **WHEN** 环境只部署 P2-03 后端而仍运行 P2-04 之前的前端
- **THEN** 旧 Dashboard 和旧用量接口继续按原契约工作，新 stats 接口可独立调用；部署 P2-04 前端后才切换正式概览来源
