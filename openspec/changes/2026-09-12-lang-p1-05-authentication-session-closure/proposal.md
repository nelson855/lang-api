## Why

LANG-P1-04 已提供认证页面与控制台守卫骨架，但应用仍没有真实身份来源，用户无法安全地注册、登录、恢复会话或退出。LANG-P1-05 需要在不自建身份系统的前提下，将冻结版 New API v0.13.2 的认证能力收敛为 Lang API 自有且稳定的浏览器会话契约，为后续 API Key、用量和账户功能提供可信身份边界。

## What Changes

- 新增注册策略与认证选项接口：`dev`、`test` 默认允许用户名密码注册，`prod` 默认关闭；关闭时仅能由管理员在 New API 私网后台创建账号，页面与注册接口保持一致。
- 新增 `POST /portal/api/auth/register`、`POST /portal/api/auth/login`、`POST /portal/api/auth/refresh`、`POST /portal/api/auth/logout` 与 `GET /portal/api/profile`，统一使用 Lang API DTO、错误码和响应包装。
- 将 New API v0.13.2 的 `session` Cookie 与 `New-Api-User` 双要素认证封装在 Portal API 内，浏览器仅接收 Lang API 自有命名、Host-only、`HttpOnly` 的会话 Cookie。
- 将 `refresh` 定义为会话重新校验和当前用户刷新，不引入 New API 长期 Access Token，也不承诺滚动延长上游会话有效期。
- 为认证写操作增加 CSRF 防护、同源检查、基础内存限流与脱敏失败日志；错误密码和禁用账户统一使用不可枚举的对外错误。
- 用真实认证状态替换前端占位状态，实现登录、条件注册、刷新恢复、会话失效、重复退出和安全站内返回。
- 后端配置继续只使用 `.properties`，共享项放在 `application.properties`，并在 `application-dev.properties`、`application-test.properties`、`application-prod.properties` 中显式区分环境策略。

## Capabilities

### New Capabilities

- `user-authentication`: 规定认证选项、条件注册、登录及不可枚举失败行为。
- `portal-session-management`: 规定自有浏览器会话、当前用户、会话重新校验、过期处理与退出行为。

### Modified Capabilities

- `new-api-adapter`: 增加冻结版 New API 注册、登录、当前用户与退出的语义化适配，以及专用会话 Cookie/Header 转换规则。
- `portal-api-contract`: 扩充认证、CSRF、注册策略和限流所需的稳定错误目录。
- `portal-api-security-observability`: 明确认证接口白名单、受保护接口鉴权、CSRF、来源校验、认证限流与安全事件日志。
- `frontend-api-integration`: 将鉴权状态接入真实 profile 会话查询，并统一处理认证 mutation、401 失效和缓存清理。
- `frontend-application-shell`: 将登录和注册占位页替换为可用表单，并按注册策略控制入口和页面状态。

## Impact

- 后端：认证 Controller、DTO、认证服务、Spring Security 会话鉴权、New API 适配器、Cookie 策略、CSRF、限流、异常映射与结构化日志。
- 前端：认证 API 契约、真实 Auth Provider、登录/注册页、退出入口、会话失效反馈和受保护路由恢复。
- 配置：四个 `application*.properties` 文件增加注册开关、Cookie、可信来源、代理识别和限流参数；生产环境保持 Host-only Cookie，不配置 `Domain`。
- 上游：继续使用 New API v0.13.2 作为用户与会话权威，不修改其源码、不开放其私网接口、不向浏览器暴露上游 Cookie 名、私网地址、角色或原始错误。
- 运维：生产新用户需由管理员预先创建，除非显式开启公开注册；单实例内存限流满足 MVP，多实例共享限流留待生产化阶段。
