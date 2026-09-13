## 1. 配置与公共契约

- [x] 1.1 在 `application.properties` 增加 `lang.auth.*` 共享配置，覆盖会话/CSRF Cookie 名与 Path、SameSite、会话上限、Origin 校验、可信代理解析和登录/注册限流默认值，且不增加任何 YAML 配置
- [x] 1.2 在 `application-dev.properties` 明确配置公开注册默认开启、Cookie `Secure=false`、本地 Allowed Origin 与 Compose edge 可信代理范围，并允许使用无敏感值的环境变量覆盖
- [x] 1.3 在 `application-test.properties` 明确配置公开注册默认开启、Cookie `Secure=false`、固定测试 Origin、测试可信代理范围及缩短的确定性限流窗口
- [x] 1.4 在 `application-prod.properties` 配置公开注册默认关闭且仅由 `PORTAL_REGISTRATION_ENABLED` 显式开启、Cookie `Secure=true`，并要求外部提供 `PORTAL_ALLOWED_ORIGINS` 与 `PORTAL_TRUSTED_PROXY_CIDRS`
- [x] 1.5 扩展配置绑定与启动校验测试，拒绝生产空 Origin/可信代理、不安全 Cookie、非法 Cookie 名、过宽阈值和非正窗口，并确认 Cookie Domain 不可配置
- [x] 1.6 扩展 Portal 稳定错误码及全局映射，覆盖 `INVALID_CREDENTIALS`、`REGISTRATION_DISABLED`、`CSRF_REJECTED`、`RATE_LIMITED` 与 `Retry-After`，补齐统一响应契约测试

## 2. New API 认证适配

- [x] 2.1 先为注册、登录、当前用户和退出编写 New API 契约失败测试，固定 v0.13.2 的方法、路径、请求字段、HTTP 200 `success=false`、401、非法响应和上游故障行为
- [x] 2.2 增加认证专用上游 request/response DTO 与语义化操作，只读取允许字段，确保适配边界外无上游路径或 DTO 依赖
- [x] 2.3 实现注册和登录适配，登录只接受合法数字用户 id 与唯一 `session` Cookie，缺少任一项时按 `UPSTREAM_ERROR` 失败且不产生部分会话
- [x] 2.4 实现 `/api/user/self` 和 GET `/api/user/logout` 适配，所有受保护调用只发送重建的 `session` Cookie、`New-Api-User` 与既有安全 Header 白名单
- [x] 2.5 将通用 Cookie 转换收紧为认证专用白名单和双向映射，丢弃额外 `Set-Cookie`，实现 `LANG_SESSION`、`LANG_UID` 创建与属性一致的过期 Cookie
- [x] 2.6 完成认证错误翻译测试，确认错误密码、未知用户和禁用用户归一化，会话 401 可识别，且原始 message、私网地址、角色及 `/api/user/token` 不离开适配边界

## 3. 会话安全、CSRF 与限流

- [x] 3.1 先编写 Portal 会话认证过滤测试，覆盖 Cookie 缺失/不完整/篡改、上游 profile 成功/401/禁用/故障，以及客户端伪造 `New-Api-User` Header
- [x] 3.2 实现每请求上游 profile 校验与普通用户安全上下文，不做跨请求身份缓存，不授予或映射任何上游管理员权限
- [x] 3.3 配置认证路由白名单与 JSON 安全入口：options、CSRF、login 和按策略执行的 register 可匿名，profile/refresh 受保护，logout 无会话时按幂等规则处理
- [x] 3.4 实现匿名 CSRF 引导端点、双提交 Cookie/Header 校验和精确 Allowed Origin 校验，确认四个认证 mutation 在失败时均于上游调用前返回 `CSRF_REJECTED`
- [x] 3.5 先编写可信代理与限流测试，覆盖可信/非可信转发头、组合登录阈值、客户端总阈值、注册阈值、窗口过期、容量淘汰和 `Retry-After`
- [x] 3.6 实现有界并发安全的单实例限流器，使用进程随机盐摘要限流键，并确保被限制请求不访问 New API
- [x] 3.7 增加认证安全事件日志及脱敏测试，覆盖 `invalid_credentials`、`session_invalid`、`rate_limited`、`revocation_unconfirmed`，验证不记录用户名、邮箱、完整 IP、密码、Cookie、CSRF Token 或摘要键

## 4. Portal 认证与会话接口

- [x] 4.1 先编写 `GET /portal/api/auth/options` 测试，覆盖 dev/test 开启、prod 默认关闭和固定关闭的邮箱/验证码选项
- [x] 4.2 实现认证 options DTO、服务和 Controller，保证注册页面与后端注册门禁共享同一配置来源
- [x] 4.3 先编写 register Controller/Service 测试，覆盖字段校验、策略关闭不访问上游、注册成功不自动登录、重复用户和限流
- [x] 4.4 实现 `POST /portal/api/auth/register` 及 Lang API request/response DTO，只支持用户名、密码与确认密码
- [x] 4.5 先编写 login 测试，覆盖成功 Cookie/profile、错误密码、未知用户、禁用用户、缺失上游 Cookie、CSRF、限流与上游故障
- [x] 4.6 实现 `POST /portal/api/auth/login`，原子地设置两个 Host-only HttpOnly Cookie并返回最小 profile
- [x] 4.7 先编写 profile 与 refresh 测试，覆盖刷新页面恢复、最小字段投影、会话过期、Cookie 不完整、禁用用户和不获取长期 Access Token
- [x] 4.8 实现 `GET /portal/api/profile` 与 `POST /portal/api/auth/refresh`，共用一次请求的已校验身份并在失效时清除 Cookie
- [x] 4.9 先编写 logout 测试，覆盖有效退出、无会话、重复退出、上游已 401、上游超时/不可用时仍清 Cookie，以及旧会话无法继续访问
- [x] 4.10 实现 `POST /portal/api/auth/logout`，将 Portal POST 映射到上游 GET，保证本地清除与上游撤销结果语义一致

## 5. 前端认证数据层

- [x] 5.1 为 options、CSRF、register、login、profile、refresh 和 logout 定义运行时校验 DTO 与相对 Portal API 调用，补齐拒绝上游字段和非法响应的客户端测试
- [x] 5.2 实现认证写操作的 CSRF 预取与 Header 附加，验证 mutation、CSRF 失败和网络失败不会自动重放
- [x] 5.3 用唯一 profile Query 替换硬编码匿名 Auth Provider，覆盖 checking、authenticated、anonymous、forbidden 与刷新页面恢复测试
- [x] 5.4 实现登录/refresh 成功写入 profile 缓存，以及 logout 或 `UNAUTHENTICATED` 清理用户作用域缓存和单次失效跳转
- [x] 5.5 增加前端安全守卫测试，确认密码、会话、Access Token 不进入 URL、localStorage、sessionStorage、错误展示或生产构建产物

## 6. 登录、注册与退出界面

- [x] 6.1 先为登录页编写组件与路由测试，覆盖基础校验、提交中、统一凭据错误、合法 returnTo、外部 returnTo 拒绝和已登录重定向
- [x] 6.2 将 `/login` 占位页替换为真实用户名密码表单，接入安全错误与成功导航，不展示 OAuth、MFA、Passkey 或未实现的找回密码入口
- [x] 6.3 先为注册页和公共导航编写测试，覆盖 options 加载、开启表单、关闭说明、策略错误、成功引导登录，以及不显示邮箱/验证码/邀请码字段
- [x] 6.4 将 `/register` 替换为策略驱动页面，并让公开布局的注册入口与同一 options 状态保持一致
- [x] 6.5 在控制台接入最小用户展示与退出操作，覆盖防重复提交、成功退出、撤销未确认仍进入匿名状态和会话过期清除旧内容
- [x] 6.6 补齐认证相关中英文文案与资源完整性测试，确保页面和错误中没有 New API 品牌、上游原始消息或虚假能力

## 7. 集成验证与交付记录

- [x] 7.1 增加后端集成测试，串联注册开启/关闭、登录、profile、refresh、logout、旧 Cookie 失效、错误凭据、禁用用户、CSRF、Origin 与限流
- [x] 7.2 增加前端浏览器流程，覆盖开发注册登录、生产关闭注册说明、刷新恢复、未登录访问 dashboard、会话过期和重复退出
- [x] 7.3 扫描源码与构建产物，确认不存在 New API 私网地址、原始管理路径、上游 Cookie 名、Access Token、默认品牌、密码或长期浏览器凭证
- [x] 7.4 使用项目指定 Maven 配置运行完整 `mvn verify`，并执行适用的前端单测、Lint、构建和 Playwright 流程，记录命令及实际结果
- [x] 7.5 更新 LANG-P1-05 接口/运维文档和变更验收记录，写明生产默认关闭注册、管理员预建账号、Cookie/Origin/可信代理配置、单实例限流限制及回滚后需重新登录
