## Context

变更动机见 [proposal.md](proposal.md)，行为契约见本变更的五组 delta specs。

P1-09 已实现 `GET /portal/api/account/balance`、统一 quota→USD 转换和 Dashboard 余额区；P1-05 已实现 `GET /portal/api/profile`、服务端上游会话、CSRF、认证限流与唯一 profile 缓存。前端已有 `/dashboard` 控制台壳、TanStack Query、响应式桌面/移动导航和中英文资源，因此本阶段应沿用这些边界，而不是建立第二套账户或缓存体系。

冻结版 New API v0.13.2（commit `bee339d`）的现有实测样本显示：`/api/user/topup/info` 的所有支付启用标志均为 false，但仍返回预置 `pay_methods` 和金额选项；`/api/user/topup/self` 当前为空页。上游源码进一步表明用户充值记录只查询最近 30 天，状态集合为 `pending/success/failed/expired`。

资料能力与现有兼容性矩阵存在实质差异：保存的 `update_display_name.json` 实际是“原密码错误”。冻结版 `PUT /api/user/self` 只把 `username`、`display_name` 和可选 `password` 写入用户更新对象，并且即使不改密码也会校验 `original_password`；email 未进入该更新路径，模型中也没有 phone 字段。规划据此只开放用户名、显示名和密码，并把真实链路复核放在实现首项。

## Goals / Non-Goals

**Goals:**

- 让钱包以 New API 为唯一权威源展示当前余额和当前用户充值记录，并明确表达充值未开放。
- 在上游存在残留支付配置或意外开启未知渠道时保持 fail-closed，即默认关闭资金操作。
- 用固定字段、固定路径、当前密码校验和结果确认实现最小个人资料更新闭环。
- 复用现有会话、CSRF、错误、金额、分页、缓存和响应式页面模式，保持用户切换与会话失效安全。

**Non-Goals:**

- 不实现任何真实或模拟支付下单、支付回调、人工入账、兑换码、退款或统一交易流水。
- 不增加 Portal 账务表、订单表、用户资料副本、缓存或后台同步任务。
- 不开放 email、phone、OAuth 绑定、通知偏好、2FA、Passkey、找回密码或邮件重置。
- 不把 New API 的支付方法、商户配置、回调协议或任意用户更新字段透明代理给浏览器。

## Decisions

### 1. 先交付关闭支付的完整钱包，而不是等待或泛化支付

本变更交付余额、充值记录和 `topup-options`，但没有 `topup-orders` 与回调路由。Portal 内部受支持支付渠道集合在本阶段为空：上游所有启用标志为 false 时返回 `NOT_CONFIGURED`；若上游意外开启任一渠道，则返回 `UNSUPPORTED_PROVIDER`。两种情况都使用 `enabled=false` 和空方法列表，前端只显示关闭说明。

这使只读钱包和个人设置可以按当前条件完成，同时满足“未启用支付时不能误操作”的验收。另一方案是等支付渠道选定后再做整个 P1-10，会无谓阻塞独立能力；通用转发 `/api/user/pay`、Stripe、Creem 或 Waffo 则无法统一其金额、重定向、签名、回调和幂等语义，且会扩大资金风险，因此不采用。

未来选定首个渠道后单独创建 OpenSpec 变更，明确币种、手续费、最低额、商户账号、回调域名、测试环境、下单 DTO、返回跳转、验签、订单状态机与重复回调证据。该变更届时再把受支持集合从空集扩展为一个具体渠道。

### 2. 钱包三个只读资源独立请求并独立失败

钱包页面并行使用：

| Portal API | 上游来源 | 页面职责 |
|---|---|---|
| `GET /portal/api/account/balance` | `GET /api/user/self` | 当前权威余额 |
| `GET /portal/api/account/topups` | `GET /api/user/topup/self` | 当前用户充值记录分页 |
| `GET /portal/api/account/topup-options` | `GET /api/user/topup/info` | 充值是否可用及关闭原因 |

三个接口不组合成一个聚合响应，因为其刷新频率、分页和失败方式不同。余额继续 `Cache-Control: no-store`；记录查询键包含用户 id、page 和 pageSize；能力状态也限定在当前用户作用域。任一普通失败只影响对应区域，401 才触发统一会话失效。

另一方案是在服务端聚合全部数据，但记录翻页会重复拉取余额与配置，且任一上游失败会拖垮整页，因此不采用。前端也不得从充值记录计算余额，两者的含义和上游查询窗口不同。

### 3. 充值配置只解析启用标志，记录只暴露可解释字段

配置适配器只声明冻结版的五个支付启用布尔值，并忽略未知字段；`pay_methods`、颜色、折扣、产品 JSON、最低额和支付地址不进入领域对象。这样可避免当前“全部关闭但仍有预置方法”的响应制造虚假入口。

充值记录按服务端分页读取，只声明 `trade_no`、`amount`、`payment_method`、`create_time`、`complete_time` 和 `status`。Portal 输出：

- `orderId`：非空、长度受限的不透明订单号；
- `requestedAmount`：上游 `amount` 的非负十进制字符串，表示账户充值额度，统一按现有账户显示口径标记 USD，但不声称等于实际支付金额或余额增量；
- `paymentMethod`：已知用户可见类型映射到稳定枚举，未知合法值收敛为 `OTHER`，不返回原文或内部 provider；
- `status`：严格映射为 `PENDING/SUCCEEDED/FAILED/EXPIRED`；
- `createdAt/completedAt`：epoch seconds 转带时区 ISO 8601，处理中零完成时间转 `null`。

上游 `money` 没有逐单币种且不同 provider 的语义不完全一致，因此本阶段不展示“实付金额”。`id`、`user_id`、`payment_provider` 及未知字段也不进入适配边界。与把完整上游 DTO 传给 Web 层再删除字段相比，最小 DTO 更能防止未来字段漂移造成泄漏。

### 4. 资料更新采用完整目标状态和统一当前密码确认

`PUT /portal/api/profile` 接收完整 `username`、`displayName`、`currentPassword`，只有修改密码时才额外接收 `newPassword/confirmPassword`。Portal 在访问上游前完成未知字段、长度、空白规范化和密码确认校验，然后显式构造上游：

```text
username          -> username
displayName       -> display_name
currentPassword   -> original_password
newPassword       -> password（未修改时为空或按实测确认的省略语义）
```

使用完整目标状态而不是 PATCH，是因为冻结版上游更新对象会同时写 username 和 display name；由 Portal 明确发送两者可避免省略字段被意外清空。email 保持现有 profile 的只读展示，phone 不进入页面。另一方案是直接暴露上游 user 对象，会允许角色、组、额度或绑定字段进入请求，故不采用。

### 5. 写成功后必须重新读取 profile，写后不确定不得重放

上游返回 `success=true` 后，Portal 用同一会话执行一次现有 `GET /api/user/self`，只有读到合法最新 profile 才返回成功。前端随后原子替换唯一 profile 缓存，使控制台展示名、设置表单和后续用户作用域 query key 同步；用户名改变时，旧用户 id 仍保持缓存作用域稳定。

如果更新请求在发送后超时、断连、响应非法，或上游确认写入但后续 profile 读取失败，统一返回 `OPERATION_RESULT_UNKNOWN`，不自动重试。页面清空全部密码并允许用户显式重新读取资料核对。当前密码错误使用 `INVALID_ARGUMENT`，避免错误地触发全局 401 会话失效；用户名占用使用既有 `RESOURCE_CONFLICT`。

冻结版源码没有主动撤销当前 session，设计保留经重新读取确认有效的当前会话。真实验收必须证明旧密码不能新登录、新密码可以新登录；若实测发现会话被上游撤销，则在写代码前更新 specs 与任务，而不是伪造兼容行为。

### 6. 资料更新复用 CSRF，并增加独立的账户/IP 限流

资料更新沿用现有同源 CSRF 和 Origin 校验，mutation 永不自动重试。服务端增加独立 profile-update 限流，至少同时按当前用户 id 和可信客户端 IP 计数，避免已登录会话被用于高速猜测当前密码；限制结果复用 `RATE_LIMITED` 和 `Retry-After`。

限流配置使用 `.properties`：共享默认值放在 `application.properties`，并在 `application-dev.properties`、`application-test.properties`、`application-prod.properties` 显式设置各环境值。属性沿用 `lang.auth.rate-limit.*` 分组，不创建 YAML，也不引入分布式存储；当前单实例边界与 P1-05 一致。

请求 DTO 的 `toString`、异常与访问日志不得包含任何密码字段。前端密码只留在受控表单内存，成功、失败、路由离开和会话失效时清空，不进入 TanStack Query 缓存、URL、通知或持久化存储。

### 7. 页面沿用控制台信息架构和响应式状态模式

新增 `/dashboard/wallet` 与 `/dashboard/settings`，并在 ConsoleLayout 的桌面侧栏和移动抽屉中加入入口。Dashboard 余额卡只增加进入钱包的次要链接，不承担充值记录或设置功能；顶层 `/wallet`、`/topup`、`/settings` 保持 404。

钱包的余额、能力状态和记录分别拥有 loading/empty/error/retry。桌面记录可使用紧凑表格，窄屏使用卡片或受控局部滚动，不允许整页横向溢出。设置页面把基础资料与密码区域分开，email 只读并说明当前不可修改，不渲染 phone 字段；提交中禁用重复操作，所有反馈使用安全 requestId 和完整中英文资源。

### 8. 契约测试先于实现，真实证据负责纠正文档

后端先以 MockWebServer 和脱敏 fixture 固定双重认证、路径、分页、残留支付配置、字段裁剪、状态转换、资料更新请求白名单、当前密码错误和结果未知，再实现适配及 Web 层。前端覆盖三个钱包查询的局部失败、支付关闭、记录分页、profile 缓存替换、密码清理、窄屏、键盘和中英文。

实现开始前必须在冻结版真实环境重新执行：关闭支付配置、空充值记录、正确/错误当前密码修改显示名、用户名与密码、修改后 profile、当前 session、旧密码和新密码登录。证据写入 `docs/new-api/` 并修正兼容性矩阵；如果结果改变外部契约，应先更新本 OpenSpec，而不是让实现偏离 specs。

## Risks / Trade-offs

- [当前只读钱包不能支持公众自助付费] → 页面明确关闭，仅用于内部验证或邀请制试运营；公众付费上线前必须完成具体渠道的独立变更与真实验收。
- [上游充值记录只有最近 30 天且字段缺少逐单币种] → 页面说明当前记录窗口，不把 `money` 当作可比较货币；更长周期统一流水留到第二阶段。
- [冻结版资料更新契约与旧样本矛盾] → 实现首项重做真实链路并保留脱敏证据，冲突时先修正规格。
- [资料写成功但确认读取失败] → 返回结果未确认、清空密码且不重放，用户通过重新读取核对。
- [用户名变化导致旧缓存短暂显示] → 以稳定用户 id 作为作用域，成功响应原子替换唯一 profile 缓存并使相关用户数据失效。
- [单实例 profile 限流可被多副本绕过] → 当前保持与 P1-05 相同部署边界，P1-12 再按生产拓扑迁移共享限流。

## Migration Plan

1. 补充并复核冻结版钱包与资料修改证据，先修正不一致的兼容性矩阵和脱敏 fixture。
2. 增加 profile-update 限流 `.properties` 配置及四个 profile 的绑定/非法值测试。
3. 以契约测试实现充值配置、充值记录和 profile update 的最小 New API 适配。
4. 实现 Portal 钱包只读接口和资料更新接口，再接入前端 schema、query、mutation 与缓存同步。
5. 增加钱包/设置路由、控制台导航和响应式页面，最后执行后端、前端、一体化 JAR/Compose 与真实资料链路验收。

该变更没有数据库迁移，也不改变 New API 数据模型。发布顺序为 Portal API 后前端；回滚恢复上一版本 Portal API 与前端产物即可，已成功的上游资料修改不会自动回滚，余额和充值记录也不会被 Portal 改写。
