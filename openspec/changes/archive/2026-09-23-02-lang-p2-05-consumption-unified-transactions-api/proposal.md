## Why

现有钱包只有当前余额和独立充值记录，缺少按明确时间范围查询消费以及统一追溯资金变化的 Portal 契约。P2-02 已提供受控聚合基础，但 P2-01 当前只验证了消费日志的原始 quota，尚未验证 quota/USD 换算、真实充值记录和退款来源，因此需要先交付诚实表达来源覆盖程度的只读接口，而不是把未验证数据包装成完整账单。

## What Changes

- 新增受保护的 `GET /portal/api/account/consumption-summary`，按明确时间范围聚合当前用户 type=2 消费日志的记录数和原始 quota，并分别表达 quota 与正式货币金额的可用性。
- 新增受保护的 `GET /portal/api/account/transactions`，提供类型筛选、稳定排序和受保护分页，统一返回 `transactionId`、`occurredAt`、`type`、`direction`、`amount`、`unit`、可空 `currency`、`status`、`remark` 和可空 `referenceId`。
- 两个接口返回 `baselineVersion`、规范化范围和来源覆盖状态，使调用方能够区分“该类型真实为空”“来源尚未验证”“记录缺少稳定引用而仅部分覆盖”。
- 当前 `p2-2026-09-22-a` 下，消费汇总使用已验证的 type=2 日志和原始 quota；USD 金额返回 `UNAVAILABLE + CURRENCY_CONVERSION_NOT_VERIFIED`，不得调用现有配置换算成正式消费金额。
- 当前统一流水只纳入具有非空 `requestId` 的消费记录，使用来源命名空间和不可逆摘要构造稳定交易 ID；缺少稳定引用的消费记录不进入明细并将消费来源标记为部分覆盖，汇总仍包含其 quota。
- 当前充值来源返回 `UNAVAILABLE + BASELINE_NOT_VERIFIED`，退款来源返回 `UNAVAILABLE + SOURCE_NOT_AVAILABLE`；不得把现有空充值页解释为“没有充值”，不得根据负数、错误日志或备注推断退款。
- 复用 P2-02 的显式时间上下文、共享读取预算、用户隔离缓存、同键并发合并和低基数观测；达到页数、记录数、时间范围或总超时上限时整体失败，不返回伪完整结果。
- 保留现有 `/portal/api/account/balance`、`/portal/api/account/topups` 和充值关闭行为，不修改 New API 记录，也不建立 Portal 账务总账。

## Capabilities

### New Capabilities

- `account-consumption-transactions`: 定义消费汇总、统一只读流水、来源可用性、金额单位、稳定 ID、排序分页、权限与失败语义。

### Modified Capabilities

无。

## Impact

- 主要影响 `portal-api` 的账户 Web 契约、消费聚合服务、Portal 自有交易投影、分页合并、缓存/观测白名单及对应测试。
- 复用 `AggregationQueryContext`、`AggregationLogReader`、`AggregationReadBudget`、`AggregationCache` 和 `AggregationMetrics`；不引入数据库、消息队列、共享缓存或新外部依赖。
- 现有充值 DTO 与接口不直接并入正式统一流水，只有 P2-01 补充真实充值状态、金额、时间和稳定引用证据并发布新基线后才启用该来源；退款同理。
- LANG-P2-01 尚缺 quotaPerUsd 独立证据、真实充值/退款样本和典型数据量性能结果，因此 LANG-P2-05 实施后仍应标记为“受阻”，已交付范围与外部缺口必须分别记录。
