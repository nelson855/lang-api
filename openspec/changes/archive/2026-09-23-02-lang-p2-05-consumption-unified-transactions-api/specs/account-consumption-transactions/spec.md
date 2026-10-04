## Purpose

为当前用户提供有明确时间边界、金额单位和来源覆盖状态的消费汇总与统一只读流水，使可验证的消费能够查询和追溯，同时避免把未验证的充值、退款或货币换算表达为确定账务事实。

## ADDED Requirements

### Requirement: 消费汇总与统一流水使用显式查询契约
系统 SHALL 提供受 Portal 会话保护的 `GET /portal/api/account/consumption-summary` 和 `GET /portal/api/account/transactions`。两个接口 MUST 要求 `startTime`、`endTime`、`granularity` 和 `timezone` 四个参数，并复用聚合基础设施按秒级、左闭右开 `[startTime,endTime)`、IANA 时区和口径版本完成校验。流水接口 SHALL 额外接受 `page`、`pageSize` 和可选单值 `type`；`type` 只允许 `TOPUP`、`CONSUMPTION` 或 `REFUND`，省略表示查询全部类型。页码从 1 开始，默认页大小为 20、最大为 100；本次请求需要扫描或跳过的候选记录超过聚合记录上限时 MUST 在访问或继续读取高成本来源前拒绝，不得返回深分页近似结果。

接口 MUST 拒绝页面快捷范围、客户端口径版本、用户 ID、未知类型和其他未声明参数。当前 `p2-2026-09-22-a` 的实时日志范围上限为 7 天；超过范围 MUST 在读取上游前返回 `INVALID_ARGUMENT`，不得改用旧统计接口或无界分页降级。

#### Scenario: 查询合法消费汇总
- **WHEN** 已登录用户提供合法四参数且范围不超过当前实时上限
- **THEN** 系统只读取当前会话用户的数据，并返回服务端口径版本和规范化范围

#### Scenario: 查询指定类型流水
- **WHEN** 已登录用户提供合法时间参数、分页和 `type=CONSUMPTION`
- **THEN** 系统只返回当前口径可投影的消费流水及消费来源覆盖状态，不读取未请求的充值或退款来源

#### Scenario: 参数缺失或试图越权
- **WHEN** 请求缺少任一时间参数，包含非法分页、未知参数、客户端 baseline、userId 或其他身份筛选
- **THEN** 系统在访问 New API 前返回 `INVALID_ARGUMENT`/400，身份只取自服务端会话

#### Scenario: 未认证查询
- **WHEN** 请求没有有效 Portal 会话
- **THEN** 系统返回 `UNAUTHENTICATED`/401，且不访问任何用户级流水来源

#### Scenario: 范围超过实时保护边界
- **WHEN** 当前基线下请求超过 7 天的消费汇总或流水
- **THEN** 系统在上游分页前返回 `INVALID_ARGUMENT`，不改用 `/api/log/self/stat`、现有充值记录或浏览器计算补齐

### Requirement: 消费汇总分别表达 quota 与正式货币金额
消费汇总成功响应 SHALL 包含 `baselineVersion`、规范化 `range`、`recordCount`、`quotaTotal`、`moneyTotal` 和 `coverage`。`recordCount` 与 `quotaTotal` MUST 各自包含可空值、明确单位、`availability` 和可空 `reasonCode`；`moneyTotal` MUST 包含可空十进制 `value`、可空 `currency`、`availability` 和可空 `reasonCode`。可用值不得带原因，不可用值必须为 `null` 且具有稳定原因。

在 `p2-2026-09-22-a` 下，系统 SHALL 从完整读取的 type=2 消费日志精确计算 `recordCount` 和原始 `quotaTotal`，二者均标记为 `AVAILABLE`，单位分别为 `records` 与 `quota`；空范围返回可用零值。由于 `quotaPerUsd` 未取得独立证据，`moneyTotal` MUST 返回 `UNAVAILABLE + CURRENCY_CONVERSION_NOT_VERIFIED`，且 `value` 与 `currency` 均为 `null`，不得使用现有 catalog 配置生成正式 USD 消费金额。

#### Scenario: 汇总多条消费日志
- **WHEN** 范围内完整读取三条 type=2 日志且原始 quota 分别为 10、20 和 30
- **THEN** `recordCount` 返回可用值 3，`quotaTotal` 返回可用值 60 quota，`moneyTotal` 保持货币换算未验证

#### Scenario: 合法空范围
- **WHEN** 范围内没有 type=2 消费日志且读取完整
- **THEN** 记录数与 quota 返回 `AVAILABLE` 的零值，货币金额仍返回 `UNAVAILABLE + CURRENCY_CONVERSION_NOT_VERIFIED`

#### Scenario: 配置存在 quotaPerUsd
- **WHEN** Portal 配置包含 catalog `quota-per-usd` 但当前聚合基线仍未独立验证该换算
- **THEN** 消费汇总不得调用该配置生成 USD 值，`moneyTotal` 继续明确不可用

#### Scenario: quota 累计溢出
- **WHEN** 原始 quota 精确累计超出支持范围
- **THEN** 整个汇总请求安全失败，不返回截断、负数或部分累计结果

### Requirement: 统一流水使用稳定的 Portal 自有模型
流水响应 SHALL 包含 `baselineVersion`、规范化 `range`、请求的可选类型、`page`、`pageSize`、`total`、总体 `availability`、可空总体 `reasonCode`、逐来源 `coverage` 和 `items`。每条 item MUST 包含：

- `transactionId`：带来源命名空间且不暴露内部数据库 ID 的稳定不透明标识；
- `occurredAt`：UTC ISO 8601 发生时间；
- `type`：`TOPUP`、`CONSUMPTION` 或 `REFUND`；
- `direction`：`CREDIT` 或 `DEBIT`，金额本身始终为非负值；
- `amount`：保持来源原始精度的非负十进制字符串；
- `unit`：`QUOTA` 或 `CURRENCY`；
- `currency`：`unit=CURRENCY` 时必填，`unit=QUOTA` 时必须为 `null`；
- `status`：`PENDING`、`SUCCEEDED`、`FAILED`、`REFUNDED` 或 `UNKNOWN`；
- 可空 `remark` 与可空 `referenceId`，只包含能够安全返回的用户级来源引用。

DTO MUST NOT 复用或暴露 New API 原始结构。任何 `currency`、方向、类型和状态组合矛盾的记录 MUST 被拒绝，不得根据金额正负号修正。

#### Scenario: 映射消费流水
- **WHEN** 一条可投影消费日志具有发生时间、非空 requestId、模型和原始 quota
- **THEN** 系统返回 `CONSUMPTION`、`DEBIT`、`SUCCEEDED`、`unit=QUOTA`、`currency=null`，安全 remark 可使用模型名且 referenceId 使用 requestId

#### Scenario: 金额方向不依赖正负号
- **WHEN** 流水类型为消费且原始 quota 为合法非负值
- **THEN** item 使用 `direction=DEBIT` 表达方向，`amount` 保持非负；负金额被视为非法来源而不是退款

#### Scenario: 货币项缺少币种
- **WHEN** 某来源投影声明 `unit=CURRENCY` 但无法确认币种
- **THEN** 该记录不得进入正式流水，不得把裸数字标记为 USD

### Requirement: 当前基线明确表达三类来源覆盖状态
`coverage` SHALL 对本次请求涉及的每种类型返回 `type`、`availability` 和可空 `reasonCode`；可用性只允许 `AVAILABLE`、`PARTIAL` 或 `UNAVAILABLE`。总体可用性 MUST 由所请求来源计算：全部完整可用时为 `AVAILABLE`，至少一个来源可安全返回但存在缺口时为 `PARTIAL`，没有任何来源可用时为 `UNAVAILABLE`。来源不可用或部分可用不得被解释为真实零记录。

在 `p2-2026-09-22-a` 下：

- `CONSUMPTION` 只纳入具有非空 `requestId` 的 type=2 记录；全部记录均可稳定标识时为 `AVAILABLE`，存在缺失 requestId 的记录时为 `PARTIAL + MISSING_STABLE_REFERENCE`；
- `TOPUP` 固定为 `UNAVAILABLE + BASELINE_NOT_VERIFIED`，不得因 `/api/user/topup/self` 返回空页而宣称没有充值；
- `REFUND` 固定为 `UNAVAILABLE + SOURCE_NOT_AVAILABLE`，不得从负数、错误日志、充值状态或备注推断。

#### Scenario: 默认查询全部来源
- **WHEN** 用户省略类型且范围内存在可投影消费记录
- **THEN** 响应包含消费 items，总体为 `PARTIAL`，coverage 同时说明消费可用、充值基线未验证和退款来源不可用

#### Scenario: 仅查询不可用充值来源
- **WHEN** 用户指定 `type=TOPUP`
- **THEN** 响应成功返回空 items、`total=0`、总体 `UNAVAILABLE + BASELINE_NOT_VERIFIED` 和充值 coverage，不把空页描述为真实无充值

#### Scenario: 仅查询不可用退款来源
- **WHEN** 用户指定 `type=REFUND`
- **THEN** 响应成功返回空 items、`total=0`、总体 `UNAVAILABLE + SOURCE_NOT_AVAILABLE`，且不访问消费或充值来源寻找替代记录

#### Scenario: 消费记录缺少 requestId
- **WHEN** 完整日志集中至少一条 type=2 记录没有 requestId
- **THEN** 该记录仍参与消费汇总但不进入流水，消费 coverage 为 `PARTIAL + MISSING_STABLE_REFERENCE`，响应不为其伪造 transactionId

### Requirement: 稳定 ID、排序和分页不重复不漏项
可投影消费记录 SHALL 使用 `CONSUMPTION` 来源命名空间与 requestId 的确定性不可逆摘要生成 `transactionId`；重复读取同一来源记录 MUST 产生相同 ID。一次完整查询中出现重复 transactionId MUST 使整个请求以安全上游数据错误失败，不得静默去重或追加不稳定序号。

统一流水 MUST 按 `occurredAt` 降序、`transactionId` 升序稳定排列，再对完整候选集执行分页。`total` MUST 表示本次筛选下可投影 item 的准确总数；相同数据快照和参数重复查询必须返回相同顺序。达到共享读取预算、记录上限或深分页保护时整个请求失败，不得根据已读取页估算 total 或返回部分页。

#### Scenario: 同时刻多条消费记录
- **WHEN** 多条可投影记录具有相同 occurredAt
- **THEN** 系统按 transactionId 升序稳定排列，重复查询不会改变相对次序

#### Scenario: 相邻分页
- **WHEN** 用户依次查询相同快照的第 1 页和第 2 页
- **THEN** 两页 item 不重复、不漏掉分页边界记录，且 total 相同

#### Scenario: 来源 requestId 重复
- **WHEN** 同一完整候选集中两条记录生成相同 transactionId
- **THEN** 整个流水请求安全失败，不合并记录、不丢弃一条也不添加位置序号制造新 ID

#### Scenario: 深分页超过读取保护
- **WHEN** `page` 与 `pageSize` 要求跳过或确定的候选数超过聚合记录上限
- **THEN** 系统在返回任何 items 前以 `INVALID_ARGUMENT` 或保护性错误拒绝，不返回近似页面

### Requirement: 聚合读取、缓存和失败保持单次完整语义
消费汇总与流水 MUST 分别使用单一规范化查询上下文、共享读取预算和当前口径版本完成所需来源读取。系统 MUST 在确认当前支持来源完整读取后才计算汇总或分页；上游超时、分页总数变化、提前空页、超过页数/记录数或映射失败时整个接口失败。不得把已读取部分标记为成功 coverage。

成功响应可使用 P2-02 进程内短期缓存和同键并发合并。缓存键 MUST 覆盖操作命名空间、认证用户、规范化范围、粒度、时区、口径版本、类型和分页；失败、超时与保护性拒绝不得缓存。缓存和值不得包含会话、完整 API Key、内部 token ID 或 New API 原始 DTO。

#### Scenario: 多页消费日志完整读取
- **WHEN** 范围内消费日志在共享预算内跨多页且分页元数据一致
- **THEN** 系统读取完整集合后计算汇总或流水，响应不会因页边界遗漏记录

#### Scenario: 上游分页中途变化
- **WHEN** 后续页 total 变化、记录提前为空或预算耗尽
- **THEN** 整个请求失败且不缓存，不返回已读取部分的汇总、total 或 items

#### Scenario: 同用户同查询命中缓存
- **WHEN** 同一用户在 TTL 内重复请求语义相同的接口、范围、类型和分页
- **THEN** 系统可复用完整 Portal 自有响应且不再次读取上游，不同用户或筛选不得共享结果

### Requirement: 流水只读且隔离敏感与跨用户数据
两个接口 SHALL 只根据认证主体绑定的 New API 会话读取当前用户数据，MUST NOT 接受客户端用户标识或访问管理员流水接口。响应、缓存、观测和日志 MUST NOT 包含用户 ID、token ID、完整 API Key、渠道、节点、供应商、支付回调、商户标识、私网信息、日志正文或上游原始错误。

统一流水只是只读投影，MUST NOT 用于计算或覆盖当前余额，不得修改、补记、冲正或确认 New API 原始记录，也不得新增充值、退款、提现或调账写接口。

#### Scenario: 用户尝试查询其他账户
- **WHEN** 客户端提交 userId、username、tokenId、channel 或其他未声明身份参数
- **THEN** 系统返回 `INVALID_ARGUMENT`，只使用当前认证主体且不访问管理员接口

#### Scenario: 查询流水不改变余额
- **WHEN** 用户重复查询消费汇总或任意流水页
- **THEN** 系统只执行只读上游操作，不写入 New API 或 Portal 数据，也不改变 `/portal/api/account/balance` 的权威值

#### Scenario: 安全来源引用
- **WHEN** 消费 item 返回 referenceId
- **THEN** 它只包含既有请求日志已允许用户查看的 requestId，不包含 token ID、渠道或上游数据库序号

### Requirement: 新接口与现有账户接口兼容并存
消费汇总和统一流水 SHALL 与 `/portal/api/account/balance`、`/portal/api/account/topups` 和 `/portal/api/account/topup-options` 并存。本变更 MUST NOT 改变现有接口的路径、参数、响应、缓存或支付关闭行为。统一流水的充值 coverage 不得通过调用旧 topups 接口绕过当前聚合基线；LANG-P2-06 前端接入属于后续变更。

#### Scenario: P2-05 后端先行部署
- **WHEN** 新接口部署但钱包前端尚未迁移
- **THEN** 现有余额、充值记录、充值关闭说明和 Dashboard 继续按原契约工作，新接口可被独立测试和调用

#### Scenario: 旧充值接口返回记录
- **WHEN** `/portal/api/account/topups` 按第一阶段契约返回 fixture 或上游记录，但 P2 聚合基线仍未验证真实充值语义
- **THEN** 统一流水的 TOPUP coverage 仍为不可用，不把旧接口输出自动提升为第二阶段正式流水
