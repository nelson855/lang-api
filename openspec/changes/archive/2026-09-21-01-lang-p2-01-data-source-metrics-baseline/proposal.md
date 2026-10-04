## Why

第二阶段的 Dashboard、钱包统一流水、模型目录和日志增强都依赖 New API 真实字段语义与查询成本，但现有第一阶段契约主要验证了基础读取能力，尚未冻结统计分子/分母、缺失值、退款映射、时间粒度及 30 天查询性能。若在证据不足时直接开发聚合接口，字段猜测会扩散到 DTO、缓存和前端，后续难以兼容修正。

## What Changes

- 对冻结版 New API 的 `/api/log/self`、`/api/log/self/stat`、`/api/data/self`、`/api/user/self`、`/api/user/topup/self` 和 `/api/pricing` 执行可重复的隔离环境实测。
- 保存成功、空数据、失败和边界场景的脱敏样例、探测步骤与兼容性结论，字段状态只允许标记为已验证、存在差异、条件可用或不可用。
- 建立第二阶段数据来源矩阵，核对日志类型、请求状态、Token、费用、耗时、Key 标识、协议、TTFT、充值和退款相关字段；未验证字段不得进入下游正式契约。
- 冻结六项 Dashboard 指标的计算口径，以及消费汇总和统一流水的来源、方向、状态、稳定 ID 与可追溯规则。
- 冻结 `[startTime,endTime)`、IANA 时区、自然日、粒度、最大跨度、金额精度、舍入及缺失/异常值处理规则。
- 对典型测试用户的 24 小时、7 天和 30 天查询记录上游调用次数、响应数据量、耗时与保护边界，形成 LANG-P2-02 可直接采用的性能基线。
- 本变更不实现 Dashboard、统一流水、模型详情、缓存或新的公开 Portal API，也不修改 New API 数据库和业务逻辑。

## Capabilities

### New Capabilities

- `aggregation-data-baseline`: 定义第二阶段聚合能力可依赖的数据来源证据、统计与流水口径、时间金额规则及性能基线。

### Modified Capabilities

- `new-api-baseline`: 将现有接口兼容性矩阵和可重复实测证据扩展到第二阶段六个数据来源，并使字段级证据成为后续聚合开发与上游升级的门禁。

## Impact

- 主要影响 `docs/new-api/` 下的兼容性与统计口径文档、脱敏样例和探测记录，以及相应的契约 fixture/验证工具。
- 复用现有 `NewApiContractTestBase`、New API 只读客户端、`QuotaMoneyConverter` 和第一阶段脱敏规则，不引入 Portal 数据库、共享缓存、消息队列或数据仓库。
- 不新增或改变公开 Portal API；后续 LANG-P2-02～LANG-P2-09 必须以本变更冻结的字段支持状态和口径版本为输入。
- 实测需要隔离的冻结版 New API、专用测试用户以及可构造成功、错误、空数据、充值/退款和典型日志量的测试数据；缺少外部条件的项目必须明确标为条件可用或不可用。
