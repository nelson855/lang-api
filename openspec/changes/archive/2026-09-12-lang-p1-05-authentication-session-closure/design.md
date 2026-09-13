## Context

参见 [proposal.md](proposal.md) 的变更动机。当前前端已有 `checking/authenticated/anonymous/forbidden` 状态和登录、注册占位页，Portal API 已有统一响应、Spring Security 基线、New API 传输层和通用 Cookie 转换；但尚无真实认证业务。

P1-02 已实测并冻结 `calciumion/new-api:v0.13.2`（commit `bee339d`）：登录成功返回 `session` Cookie；所有受保护调用必须同时携带该 Cookie 与 `New-Api-User: <用户 id>`；`/api/user/self` 可校验会话；退出使用 GET `/api/user/logout`；该版本没有独立 refresh-token 接口。公开注册、邮箱验证和 Turnstile 的实测状态分别为开启、关闭、关闭。本阶段采用项目自己的注册开关覆盖公开行为，不跟随上游开关动态漂移。

浏览器、页面与 `/portal/api/*` 同源，New API 只在私网可见。生产默认关闭公开注册，管理员通过 New API 私网后台预建账号；`dev` 和 `test` 为联调默认开启用户名密码注册。

## Goals / Non-Goals

**Goals:**

- 让 New API 继续作为用户和会话权威，Portal 只负责协议适配与浏览器安全边界。
- 使一次登录产生的会话可在页面刷新后恢复，并能被后续 Portal 业务接口复用。
- 让注册策略、Cookie 属性、CSRF、来源校验、限流和日志行为可配置、可测试、可按环境审计。
- 对外只暴露普通用户所需资料和稳定错误，不暴露上游品牌、角色、Cookie 名或原始消息。

**Non-Goals:**

- 不自建用户表、密码哈希、会话库或 Refresh Token 服务。
- 不实现邮箱验证、Turnstile、邀请码、OAuth、MFA、Passkey 和找回密码；若未来启用，应作为独立变更重新设计。
- 不提供管理员权限映射和普通前端管理入口。
- 不在 P1-05 实现多实例共享限流或跨请求身份缓存。

## Decisions

### 1. Portal 使用上游会话，不引入第二套身份状态

登录、注册、当前用户和退出分别适配 New API v0.13.2 的 `/api/user/login`、`/api/user/register`、`/api/user/self` 和 `/api/user/logout`。Portal 不保存用户副本，也不调用 `/api/user/token`。

每个受保护 Portal 请求先用浏览器会话映射出上游 `session` 与用户 id，再调用 `/api/user/self` 建立本次请求的普通用户安全上下文；后续业务操作使用同一身份。P1-05 不做跨请求 profile 缓存，以保证上游退出、禁用或过期能尽快生效。代价是每个受保护请求至少多一次上游校验，后续只有在有监控数据证明必要时才增加短时缓存。

备选方案是 Portal 自签 JWT 或把 profile 长期缓存在本地，但会产生第二套撤销和禁用状态，违背 New API 权威边界，因此不采用。

### 2. 两个 HttpOnly Cookie 封装 New API 的双要素要求

浏览器只接收：

- `LANG_SESSION`：保存上游 session 值；
- `LANG_UID`：保存登录响应中的数字用户 id，仅用于构造上游所需 Header，不能单独证明身份。

适配器只允许上游名为 `session` 的 Cookie，并在两个方向显式映射。即使 `LANG_UID` 被篡改，上游仍会将它与 session 中的身份共同校验；失败即清除两个 Cookie。两者统一设置 Path `/portal`、`HttpOnly`、SameSite `Lax`，生产设置 `Secure`。不提供 Domain 配置，始终省略 Domain 形成 Host-only Cookie，从结构上避免写入 New API 私网域名或过宽父域。Max-Age 不得超过上游会话的剩余期限和 30 天上限。

备选方案是把用户 id 放入可读 Cookie，虽然仍需上游校验，但会扩大脚本可见身份数据，没有必要，因此不采用。服务端 Session Store 可以隐藏两项上游值，但会引入 Redis 状态和额外生命周期，本阶段也不采用。

### 3. `refresh` 只重新校验，不伪造 Refresh Token 语义

`POST /portal/api/auth/refresh` 使用现有两个 Cookie 调用 `/api/user/self`，成功时返回最新裁剪 profile；失败时清除 Cookie。它不会调用长期 Token 接口，也不会无条件重发 Cookie 或滚动延长 30 天会话。

页面首次启动使用 GET `/portal/api/profile` 恢复登录态，避免仅为启动执行一次 mutation；refresh 留给用户显式恢复或后续需要重新同步 profile 的场景。备选方案是用 New API Access Token 作为浏览器 Bearer Token，但它是长期凭证且会落入脚本控制范围，不采用。

### 4. 注册策略由 Portal 明确拥有并按环境冻结

新增匿名 `GET /portal/api/auth/options`。当前只返回布尔值：公开注册是否开启，以及固定为关闭的邮箱验证和验证码能力。注册关闭时 Controller 在访问上游前拒绝请求；页面不展示表单，并说明账号由管理员创建。注册开启时仅支持 `username/password/confirmPassword`，成功后跳转登录，不自动登录。

不直接把 New API `/api/status` 作为运行时注册开关来源，避免上游配置变化绕过 Portal 的生产策略。部署前需要保证上游自身注册开关与 Portal 一致：生产关闭时上游也应关闭；若上游意外要求邮箱、验证码或邀请机制，适配器安全失败并记录配置不兼容，不把其原始页面或提示暴露出去。

### 5. profile 是普通用户最小投影

对外 profile 只包含 `id`、`username`、`displayName` 和可选 `email`。`role`、`group`、`status`、`quota`、`used_quota`、请求次数和其他上游字段全部丢弃。Spring Security 中统一授予普通用户能力，不根据上游管理员角色创建管理权限。

错误密码、未知用户和禁用用户统一映射为 `INVALID_CREDENTIALS`/401，避免账户枚举。已有会话对应用户被禁用时按会话失效处理为 `UNAUTHENTICATED`/401。

### 6. 所有认证写操作使用双提交 CSRF 与 Origin 校验

新增匿名 GET `/portal/api/auth/csrf`，返回 Token 并设置专用 `XSRF-TOKEN` Cookie。这个 Cookie 可由脚本读取，但只用于跨站请求伪造防护，不包含身份信息。统一客户端在 register、login、refresh、logout 前取得 Token并通过 `X-XSRF-TOKEN` Header 回传。

服务端同时校验 Cookie/Header，并在生产校验精确 Origin 允许列表。来源判断不使用未经信任的任意转发 Header。缺失、失配或来源不允许统一返回 `CSRF_REJECTED`/403，且绝不自动重放原请求。

SameSite Cookie 本身不能覆盖所有代理、浏览器和部署变化，因此不作为唯一 CSRF 防线。完全禁用登录 CSRF 的方案可能允许攻击者把受害者登录到攻击者账号，也不采用。

### 7. 使用有界单实例限流，可信代理显式配置

P1-05 采用并发安全、有界的内存固定窗口计数器，不新增 Redis 依赖。默认策略：

- 登录：同一客户端地址与规范化用户名组合 5 次/5 分钟；同一客户端地址 20 次/5 分钟；
- 注册：同一客户端地址 3 次/小时。

限流键使用进程内随机盐做不可逆摘要；日志只记录结果类别，不记录明文用户名、完整 IP 或摘要键。只有请求直接来源属于 `trusted-proxy-cidrs` 时，才按受控规则读取最右侧可信代理链之前的客户端地址；否则使用 TCP 直接来源。超过限制返回 `RATE_LIMITED`/429 和 Retry-After，并在调用 New API 前终止。

分布式限流需要共享存储和全局代理拓扑，留到 P1-12。当前限流重启清空、每实例独立是已接受的 MVP 取舍。

### 8. 退出优先撤销上游，同时保证本地凭证清除

有会话时 Portal 调用上游 GET logout；上游成功或返回已未认证都视为幂等成功。无会话直接成功。无论结果如何，响应都以相同 Path 和属性过期 `LANG_SESSION`、`LANG_UID`。

上游超时或不可用时，接口保留对应 5xx 错误，提示撤销状态未确认，但仍清除本地 Cookie；前端无论该结果如何都清除 profile 与用户作用域缓存，进入 anonymous。这样不会假称上游撤销成功，同时避免浏览器继续使用不确定凭证。

### 9. 前端使用单一 profile Query 驱动认证状态

真实 Auth Provider 以唯一 profile Query 为状态源：加载为 checking，成功为 authenticated，`UNAUTHENTICATED` 为 anonymous，明确权限错误为 forbidden。登录/refresh 成功直接写入 profile 缓存；logout 和任意受保护请求的 `UNAUTHENTICATED` 清空用户作用域缓存，且用单次状态迁移防止多个并发 401 重复跳转。

登录只接受经过现有 returnTo 校验的站内路径。注册页先等待 options，避免生产环境短暂闪现表单。认证 mutation 禁止自动重试，特别是 CSRF 失败、网络中断和超时后不得自动重交密码。

### 10. 只使用 `.properties` 并显式分离三个环境

共享键写入 `application.properties`：Cookie 名、Path、SameSite、会话上限、CSRF Header/Cookie 名、限流窗口和默认阈值。环境文件负责会改变安全行为的值：

| 文件 | 注册默认值 | Cookie Secure | Allowed Origin | 可信代理 |
|---|---:|---:|---|---|
| `application-dev.properties` | `true` | `false` | 本地开发入口，可被环境变量覆盖 | 本地 Compose edge 范围 |
| `application-test.properties` | `true` | `false` | 测试固定 Origin | 测试显式范围 |
| `application-prod.properties` | `false`，仅 `PORTAL_REGISTRATION_ENABLED` 显式开启 | `true` | 必填外部 `PORTAL_ALLOWED_ORIGINS` | 必填外部 `PORTAL_TRUSTED_PROXY_CIDRS` |

建议属性前缀为 `lang.auth.*`，包括 `registration.enabled`、`cookie.*`、`csrf.*`、`allowed-origins`、`trusted-proxy-cidrs` 和 `rate-limit.*`。不创建 `application.yml` 或其他 YAML 配置。配置绑定与启动校验应拒绝空的生产站点名、New API 地址、Allowed Origin、可信代理范围，以及不安全的生产 Cookie 设置。

## Risks / Trade-offs

- [每个受保护请求增加一次 `/api/user/self`] → 使用现有连接池与短超时监控延迟；只有观测证明需要时才设计可撤销的短缓存。
- [两个 Cookie 中的用户 id 可被客户端篡改] → `LANG_UID` 为 HttpOnly 且不单独可信，每次都由 New API 与 session 联合校验，任何不一致立即清除会话。
- [Portal 与 New API 注册开关配置不一致] → Portal 关闭时先行阻断，部署清单同步关闭上游注册；意外的上游验证要求按配置不兼容安全失败。
- [单实例限流可在重启后清空或被多副本绕过] → 当前部署按单实例 MVP 设计，P1-12 再迁移到共享限流。
- [退出期间上游故障无法确认旧 session 已撤销] → 始终清除浏览器 Cookie、返回明确稳定错误并记录 `revocation_unconfirmed`，不虚假报告撤销成功。
- [Host-only Cookie 无法跨子域共享] → 当前页面与 Portal API 已冻结为同源；若未来拆分认证域，需要独立威胁建模和变更，而不是放宽 Domain。

## Migration Plan

1. 先增加四个 `.properties` 文件中的共享与环境认证配置，并用启动校验阻止不安全的生产组合。
2. 扩展稳定错误目录、CSRF/Origin 防护、限流与安全日志，保持尚未接入的受保护接口默认拒绝。
3. 实现 New API 认证适配与契约测试，再接入 register、login、profile、refresh、logout Controller。
4. 用真实 profile Query 替换前端匿名占位 Provider，随后接入登录、注册选项、注册表单和退出交互。
5. 通过后端契约/安全测试、前端组件测试和一体化浏览器流程验证开放/关闭注册、刷新恢复、错误凭据、禁用用户、过期会话和重复退出。
6. 生产部署先保持注册关闭，确认管理员预建账号可登录且 Cookie/Origin/代理配置正确后再开放服务。

回滚时先停止入口流量，恢复上一版本应用与原配置；本变更不写 Portal 数据库，因此无需数据迁移。若已创建账号，它们仍归 New API 管理；回滚不会删除账号。部署切换会使 Lang API 新命名 Cookie 不被旧版本识别，用户需要重新登录，这是可接受的安全回滚行为。
