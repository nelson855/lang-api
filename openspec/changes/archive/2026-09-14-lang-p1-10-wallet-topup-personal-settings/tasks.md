## 1. 冻结版契约证据与范围确认

- [ ] 1.1 在隔离的 New API v0.13.2 环境重新实测 `GET /api/user/topup/info` 与 `GET /api/user/topup/self`，记录双重认证、分页、全部支付关闭但存在残留方法配置、空记录和失败响应
- [ ] 1.2 使用一次性普通用户分别实测错误/正确当前密码下的显示名、用户名和密码更新，确认 `PUT /api/user/self` 的完整请求字段、长度约束、用户名冲突和成功响应
- [ ] 1.3 验证资料更新后的 `GET /api/user/self`、当前 session、旧密码登录和新密码登录行为，确认 email 不可通过该路径更新且 phone 不属于冻结版 profile
- [ ] 1.4 补充脱敏钱包/profile fixture 与可重复命令，修正 `docs/new-api/第一阶段接口兼容性矩阵.md` 及错误标记的 `update_display_name.json` 证据
- [ ] 1.5 若真实结果改变已冻结的字段、状态或会话行为，先同步更新本变更 proposal/specs/design/tasks 并重新严格校验，再进入实现

## 2. 分页与资料更新限流配置

- [x] 2.1 先为充值记录默认 pageSize=20、最大 pageSize=100、正整数页码和未知参数拒绝编写配置/值对象测试，再复用现有统一分页边界实现
- [x] 2.2 先为 profile-update 按用户与可信客户端 IP 双维度限流、窗口重置、`Retry-After` 和不记录密码编写失败测试，再扩展现有认证限流器
- [x] 2.3 在 `application.properties` 增加 profile-update 限流共享默认值，并保持既有充值分页与认证配置分组清晰
- [x] 2.4 在 `application-dev.properties` 显式配置开发环境 profile-update 限流值
- [x] 2.5 在 `application-test.properties` 显式配置确定性的测试环境 profile-update 限流值
- [x] 2.6 在 `application-prod.properties` 显式配置生产环境 profile-update 限流值及环境变量覆盖入口
- [x] 2.7 补充四套 `.properties` 的绑定、边界和非法启动配置测试，确认未创建或读取 YAML 配置

## 3. New API 充值只读适配

- [ ] 3.1 先编写充值配置客户端契约测试，覆盖固定 `/api/user/topup/info`、`session` Cookie、`New-Api-User` Header、401、失败 envelope、超时和非法响应
- [x] 3.2 定义只包含已知支付启用标志的最小上游 DTO，实现关闭与未知已启用渠道的 fail-closed 转换，证明残留 `pay_methods`、产品、折扣和地址无法离开适配边界
- [x] 3.3 先编写充值记录客户端契约测试，覆盖固定 `/api/user/topup/self`、page/page_size、空页、分页元数据、401、失败 envelope 和上游故障
- [x] 3.4 先为 `pending/success/failed/expired`、完成时间、金额边界、排序、未知支付方式和未知状态编写映射测试，再实现充值记录最小 DTO 与整页严格转换
- [ ] 3.5 增加隐私裁剪测试，确认 `id/user_id/payment_provider/money`、回调数据、未知字段和完整上游正文不进入业务对象、异常或日志

## 4. New API 个人资料更新适配

- [x] 4.1 先编写资料更新契约测试，固定 `PUT /api/user/self`、双重认证以及 `username/display_name/original_password/password` 白名单请求，拒绝其他用户字段
- [x] 4.2 先覆盖只改资料、同时改密、错误当前密码、用户名冲突、未知业务失败、非 2xx、非法成功响应、超时和断连，再实现不可自动重试的更新操作
- [x] 4.3 实现更新成功后通过既有 `GET /api/user/self` 重新读取裁剪 profile；写后读取失败统一返回 `OPERATION_RESULT_UNKNOWN`
- [x] 4.4 增加凭据泄漏测试，确认 request/record 的字符串表示、异常、访问日志和应用日志均不包含当前密码、新密码或上游请求正文

## 5. Portal 钱包只读 API

- [x] 5.1 先编写 `GET /portal/api/account/topup-options` Controller 契约测试，覆盖 `NOT_CONFIGURED`、`UNSUPPORTED_PROVIDER`、固定 USD、空 methods、匿名访问和上游错误
- [x] 5.2 实现充值能力 DTO、查询服务和 Controller，只返回稳定关闭状态且不声明任何下单能力
- [x] 5.3 先编写 `GET /portal/api/account/topups` Controller 契约测试，覆盖统一分页、字段枚举、时间、空页、非法分页、未知/越权参数、匿名访问和整体失败
- [x] 5.4 实现当前用户充值记录 DTO、查询服务和 Controller，不接受用户身份、搜索或状态参数，不从记录推算余额
- [x] 5.5 扩展安全与路由测试，确认两个只读接口仅允许有效会话，`POST /portal/api/account/topup-orders` 和任何支付回调路径保持 JSON `NOT_FOUND` 且不会调用上游
- [x] 5.6 复核 `GET /portal/api/account/balance` 的 `no-store`、统一 USD/quota 口径和钱包复用，不增加第二个余额实现或跨请求缓存

## 6. Portal 个人资料更新 API

- [x] 6.1 先为完整 `username/displayName/currentPassword`、可选成对新密码、长度/空白规范、未知字段和 email/phone/权限字段拒绝编写请求校验测试
- [x] 6.2 先编写 `PUT /portal/api/profile` Controller 测试，覆盖 CSRF、匿名、限流、当前密码错误、用户名冲突、结果未确认、成功后最新 profile 和统一错误包装
- [x] 6.3 实现 profile 更新 DTO、应用服务和 Controller，复用当前认证会话，只返回既有 `AuthProfile` 白名单字段
- [x] 6.4 扩展 CSRF、正文大小、错误方法、未知路径和日志脱敏测试，确认密码字段不进入缓存、异常、响应或安全事件
- [x] 6.5 增加集成测试覆盖资料修改后刷新、用户名导航数据同步、当前会话继续有效以及旧/新密码登录的冻结行为

## 7. 前端钱包与资料数据层

- [x] 7.1 先为 topup options、topup page 和 profile update 的 Zod schema/API 编写测试，覆盖严格字段、十进制字符串、枚举、可空时间、统一错误和非法额外字段
- [x] 7.2 实现钱包查询 hooks，query key 包含当前用户及分页；余额、能力和记录独立取消、失败、重试且任一 401 复用统一会话失效
- [x] 7.3 先为 profile mutation 的禁止自动重试、成功替换唯一 profile 缓存、用户名变化后用户作用域失效和结果未知后的显式重新读取编写测试
- [x] 7.4 实现 profile mutation 与缓存同步，确保当前/新密码只存在于表单提交内存，不进入 TanStack Query、URL、通知、错误对象或持久化存储

## 8. 钱包页面与控制台导航

- [x] 8.1 先编写 `/dashboard/wallet` 路由和导航测试，覆盖已登录访问、匿名安全 returnTo、桌面/移动选中状态及顶层 `/wallet`、`/topup` 404
- [x] 8.2 先编写钱包组件测试，覆盖余额/能力/记录三请求独立状态、支付关闭、残留/不支持配置、空记录、分页、局部失败和会话失效
- [x] 8.3 实现钱包页面，复用现有余额展示，加入明确关闭说明和充值记录，不渲染金额输入、渠道选择、充值按钮或支付外链
- [x] 8.4 在 Dashboard 余额区域增加进入钱包的次要导航，并确认历史时间筛选不改变当前余额语义
- [x] 8.5 补充钱包窄屏、键盘/触摸分页、订单号展示、状态非颜色表达、中英文文案与页面标题测试

## 9. 个人设置页面

- [x] 9.1 先编写 `/dashboard/settings` 路由和导航测试，覆盖已登录访问、匿名安全 returnTo、桌面/移动选中状态及顶层 `/settings` 404
- [x] 9.2 先编写设置表单测试，覆盖 profile 预填、email 只读、无 phone、基础资料与改密区域、当前密码必填、新密码确认和客户端边界校验
- [x] 9.3 实现个人设置页面和提交交互，防止重复提交，并在成功、失败、路由离开和会话失效时清空所有密码字段
- [x] 9.4 覆盖当前密码错误的字段关联、用户名冲突、结果未确认后的重新读取、成功后导航展示名更新及安全 requestId 反馈
- [x] 9.5 补充个人设置窄屏、键盘、标签/错误可访问关系、中英文资源和页面标题测试

## 10. 集成验证与交付记录

- [x] 10.1 运行 Portal API 单元、契约、集成、安全与配置测试，并使用 `/Users/nelson/software/apache-maven-3.8.4/conf/settings.xml` 完成根工程 `mvn verify`
- [ ] 10.2 运行前端 lint、类型检查、Vitest 与相关 Playwright 流程，验证钱包、设置、局部失败、匿名跳转、缓存切换和移动端导航
- [ ] 10.3 在一体化 JAR/Compose 中验证两个钱包只读 API、profile update、SPA 深层路由、未知下单/回调 JSON 404，确认浏览器不直接访问 New API
- [ ] 10.4 在冻结版真实环境执行“读取资料→修改显示名/用户名/密码→刷新→当前会话→旧/新密码登录”闭环并保存脱敏结果
- [ ] 10.5 验证支付关闭时钱包仍可读取余额与空记录，页面和网络请求中均不存在下单、支付跳转或回调能力，并记录当前仅适用于内部验证/邀请制试运营
- [x] 10.6 更新 `docs/06_第一阶段MVP开发与子需求拆分.md` 的 LANG-P1-10 实际状态和必要接口说明，不把未实现在线支付描述为已完成
- [x] 10.7 运行 OpenSpec 严格校验、`git diff --check` 和 `git status`，即时勾选所有实际完成且已验证的任务并报告未提交改动
