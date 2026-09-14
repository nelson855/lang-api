# account-balance Specification

## Purpose

提供当前用户剩余额度及明确 USD 显示金额的最小只读契约，使基础概览和后续钱包页面共享同一权威数据与换算口径。

## Requirements

### Requirement: 当前账户余额接口
系统 SHALL 提供受 Portal 会话保护的 `GET /portal/api/account/balance`，每次请求从 New API 当前用户信息读取剩余 quota，并通过统一成功包装返回 `quota`、`amount` 与 `currency=USD`。`quota` 和 `amount` MUST 使用非负十进制字符串；该接口只表达当前可用余额，不得返回角色、用户组、累计用量、充值配置、支付方式或其他 profile 字段。

#### Scenario: 查询当前余额
- **WHEN** 已登录用户请求账户余额且上游返回合法剩余 quota
- **THEN** 系统返回对应 quota 字符串、按统一参数换算的 USD 金额和 `currency=USD`

#### Scenario: 零余额
- **WHEN** 上游剩余 quota 为零
- **THEN** 系统返回明确零值，不将其解释为无限额度、缺失或接口关闭

#### Scenario: 匿名查询余额
- **WHEN** 未登录或会话失效的客户端请求余额
- **THEN** 系统返回 `UNAUTHENTICATED`/401，不返回缓存的其他用户余额

### Requirement: 余额换算与新鲜度
余额金额 SHALL 使用与 P1-08 模型价格相同的后端 `quota-per-usd` 和十进制舍入规则集中计算，前端不得复制换算公式。余额响应 MUST 使用 `Cache-Control: no-store`，服务端不得跨请求缓存用户余额；换算参数非法、quota 为负或数值溢出时 SHALL 安全失败，不显示可能错误的余额。

#### Scenario: 与模型价格共用换算参数
- **WHEN** 当前 quota 为 `500000` 且统一配置为每 USD `500000` quota
- **THEN** 余额金额返回 `1.0` USD，并与模型价格和请求费用使用相同格式

#### Scenario: 连续读取余额
- **WHEN** 用户完成一次真实扣费后再次请求余额
- **THEN** Portal 重新访问权威上游并返回扣费后的值，不复用调用前缓存

#### Scenario: 上游余额非法
- **WHEN** New API 返回负 quota、缺失字段或无法安全表示的数值
- **THEN** 系统返回 `UPSTREAM_ERROR`，响应和日志不包含完整上游正文

### Requirement: 余额页面表达
Dashboard SHALL 将余额显示为当前值而不是所选历史区间统计，并提供独立加载、错误和刷新状态。页面 MUST 同时标明 USD 与 quota 语义，且不得在 P1-10 前展示充值按钮、充值记录或虚假支付入口。

#### Scenario: Dashboard 显示余额
- **WHEN** 余额接口成功返回
- **THEN** 页面显示当前 USD 金额和 quota 辅助信息，并明确其不随历史时间筛选变化

#### Scenario: 余额查询失败
- **WHEN** 余额接口失败但其他用量接口成功
- **THEN** 余额区域显示错误和重试，其他用量区域继续展示
