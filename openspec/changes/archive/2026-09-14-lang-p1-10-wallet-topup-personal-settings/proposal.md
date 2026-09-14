## Why

P1-09 已提供当前余额，但用户仍无法查看充值配置与记录，也没有修改基础资料的安全入口。当前冻结版 New API 没有启用支付渠道，资料修改能力也与阶段文档中的初始假设存在差异，因此 LANG-P1-10 需要先建立可验证的只读钱包、明确关闭的充值状态和严格按实测能力开放的个人设置闭环。

## What Changes

- 新增受保护的钱包页面，复用 `GET /portal/api/account/balance`，并新增分页充值记录和稳定的充值能力说明。
- 新增 `GET /portal/api/account/topups`，只返回当前用户最近可查询范围内的充值记录，并把上游字段转换为稳定的金额、时间和状态模型。
- 新增 `GET /portal/api/account/topup-options`；在当前未配置支付渠道的基线中明确返回 `enabled=false`、空支付方式和面向用户的关闭说明，前端不展示金额输入、支付方式选择或充值按钮。
- 本变更不实现 `POST /portal/api/account/topup-orders`、支付回调或人工入账；选定首个支付渠道、币种、手续费、最低充值额、商户账号、回调域名和测试环境后，再以独立变更补充渠道专用下单、验签、状态映射和幂等设计。
- 新增受保护的个人设置页面和 `PUT /portal/api/profile`，只允许修改冻结版 New API v0.13.2 实际支持的 `username`、`displayName` 和可选新密码；所有更新均要求当前密码，邮箱与手机号保持只读或不展示为可编辑字段。
- 资料更新成功后重新读取上游 profile 并刷新前端唯一认证缓存；用户名或密码变更后的登录与现有会话行为以真实链路验证结果为准，不伪造重新认证或会话撤销语义。
- 补充 New API v0.13.2 钱包、资料修改的契约证据，修正现有资料修改样本与兼容性矩阵之间的矛盾。

## Capabilities

### New Capabilities

- `account-topups`: 定义当前用户充值能力状态、充值记录分页、字段转换、状态语义和未启用支付时的安全关闭行为。
- `profile-management`: 定义当前用户可修改字段、当前密码校验、资料刷新、敏感字段处理和页面交互。

### Modified Capabilities

- `account-balance`: 将现有只读余额能力复用于独立钱包页面，并保持当前值、新鲜度和金额口径一致。
- `new-api-adapter`: 增加冻结版充值配置、充值记录和个人资料更新的固定语义化操作及白名单字段转换。
- `frontend-application-shell`: 增加受保护的钱包与个人设置路由、控制台导航和响应式页面状态。

## Impact

- Portal API：增加 account/topup 与 profile update 的 Web DTO、查询/更新服务、Controller 和 New API 适配类型；扩展现有 profile 返回刷新流程。
- 前端：增加 `/dashboard/wallet`、`/dashboard/settings` 路由及导航，新增 wallet/profile API schema、查询、mutation、缓存同步和页面组件。
- 配置：本基线不增加支付密钥、回调地址或渠道配置；充值可用性完全来自经过白名单转换的上游只读配置，并在所有渠道关闭时安全收敛为 disabled。
- 数据与资金：不新增 Portal 数据库、账务总账、订单表或支付回调；余额和充值记录继续以 New API 为唯一权威源。
- 兼容性：需要补充脱敏 fixture，并真实验证 `PUT /api/user/self` 对当前密码、用户名、显示名、新密码和会话的实际行为；未验证字段不得进入 Portal 契约。
