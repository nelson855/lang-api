# account-topups Specification

## Purpose

为当前用户提供可审计的充值能力状态和只属于自己的充值记录，同时在未接入经验证支付渠道时从接口与页面两层安全关闭资金入口。

## Requirements

### Requirement: 充值能力状态接口
系统 SHALL 提供受 Portal 会话保护的 `GET /portal/api/account/topup-options`。在 LANG-P1-10 当前没有受支持支付渠道的基线中，响应 MUST 返回 `enabled=false`、`methods=[]`、`currency=USD`，并以 `reason=NOT_CONFIGURED` 表示上游所有支付渠道均关闭，或以 `reason=UNSUPPORTED_PROVIDER` 表示上游开启了 Portal 尚未实现的渠道；不得把上游支付地址、产品配置、折扣、颜色、商户标识或原始渠道字段返回浏览器。

#### Scenario: 所有支付渠道关闭
- **WHEN** 已登录用户查询充值能力且冻结版上游的所有支付启用标志均为 false
- **THEN** 系统返回 `enabled=false`、空方法列表和 `reason=NOT_CONFIGURED`

#### Scenario: 上游意外开启未支持渠道
- **WHEN** 上游配置声称某个支付渠道已启用，但 Portal 没有该渠道的专用下单、验签和幂等实现
- **THEN** 系统仍返回 `enabled=false`、空方法列表和 `reason=UNSUPPORTED_PROVIDER`，页面不能据此发起支付

#### Scenario: 匿名查询充值能力
- **WHEN** 未登录或会话失效的客户端请求充值能力
- **THEN** 系统返回 `UNAUTHENTICATED`/401，且不披露支付配置状态

### Requirement: 当前用户充值记录
系统 SHALL 提供受 Portal 会话保护的 `GET /portal/api/account/topups`，只接受 `page` 和 `pageSize`，默认页大小为 20、最大为 100，并使用统一分页包装。每条记录 SHALL 只包含不透明 `orderId`、`requestedAmount`、`currency=USD`、稳定 `paymentMethod`、`status`、`createdAt` 和可空 `completedAt`；`requestedAmount` 表示上游记录的账户充值额度而不是支付凭证、回调金额或余额实际增量。系统 MUST NOT 返回 userId、上游数据库 id、支付提供商内部标识、回调数据或完整原始记录。

#### Scenario: 查询自己的充值记录
- **WHEN** 已登录用户请求合法的充值记录页
- **THEN** 系统只返回当前会话用户的记录、稳定字段和准确分页元数据

#### Scenario: 充值记录为空
- **WHEN** 当前用户在上游可查询窗口内没有充值记录
- **THEN** 系统返回空 `items` 和 `total=0`，页面显示真实空状态而不是示例订单

#### Scenario: 非法分页或越权参数
- **WHEN** 请求包含非法页码、超上限页大小、userId、username、状态筛选、订单搜索或其他未知参数
- **THEN** 系统返回 `INVALID_ARGUMENT`，且不访问上游

### Requirement: 充值记录状态与数据完整性
系统 SHALL 将冻结版上游的 `pending`、`success`、`failed`、`expired` 分别转换为 `PENDING`、`SUCCEEDED`、`FAILED`、`EXPIRED`，按创建时间倒序返回。金额、时间、订单号或状态缺失、为负、溢出或不属于声明集合时，整页 MUST 以 `UPSTREAM_ERROR` 失败，不得混合展示部分可信记录。

#### Scenario: 处理中记录
- **WHEN** 上游记录状态为 `pending` 且完成时间为零
- **THEN** Portal 返回 `status=PENDING` 和 `completedAt=null`，不把它误报为失败或成功

#### Scenario: 最终状态记录
- **WHEN** 上游返回合法的 `success`、`failed` 或 `expired` 记录
- **THEN** Portal 分别返回 `SUCCEEDED`、`FAILED` 或 `EXPIRED` 及合法完成时间

#### Scenario: 未知或非法记录
- **WHEN** 页面中的任一上游记录包含未知状态或无法安全表示的已声明字段
- **THEN** 整页返回 `UPSTREAM_ERROR`，响应和日志不包含完整上游正文

### Requirement: 未启用支付时不存在可操作入口
LANG-P1-10 当前基线 MUST NOT 声明或实现 `POST /portal/api/account/topup-orders` 以及任何公开支付回调路径。钱包页面在 `enabled=false`、充值能力请求失败或返回不受支持配置时，MUST 只显示明确的充值未开放说明，不得渲染金额输入、支付方式选择、支付按钮、外链或可触发写请求的控件。

#### Scenario: 直接调用未启用的下单路径
- **WHEN** 客户端向 `/portal/api/account/topup-orders` 发送 POST
- **THEN** 系统按未知 Portal API 返回统一 `NOT_FOUND`/404，且不请求 New API

#### Scenario: 钱包处于关闭状态
- **WHEN** 钱包页面收到 `enabled=false`
- **THEN** 页面展示当前余额、充值记录和关闭说明，但不存在可误操作的充值控件

#### Scenario: 充值能力查询失败
- **WHEN** 余额或充值记录可用但充值能力查询失败
- **THEN** 页面保留已成功的只读数据并安全关闭充值操作，提供只读区域各自的重试入口
