# portal-api-security-observability Specification

## Purpose

为 Portal API 建立默认安全边界和可关联但不泄密的访问记录，使后续认证与业务接口能够在一致的安全响应、日志字段和敏感信息规则上扩展。

## Requirements

### Requirement: Portal API 安全访问基线
系统 SHALL 使用显式白名单区分公开资源、公开 Portal API、受保护 Portal API 和 Actuator 端点。`/portal/api/public-config`、页面静态资源与受控健康检查可匿名访问；受保护 Portal API 必须认证；未公开的 Actuator 端点 MUST 被拒绝。系统 MUST NOT 启用允许任意来源的 CORS。

#### Scenario: 匿名访问公开配置
- **WHEN** 未登录客户端请求 `/portal/api/public-config`
- **THEN** 请求被允许且返回统一成功响应

#### Scenario: 匿名访问受保护接口
- **WHEN** 未登录客户端请求一个声明为受保护的 Portal API
- **THEN** 系统返回统一 JSON `UNAUTHENTICATED`/401，而不是重定向到 HTML 登录页

#### Scenario: 访问未公开执行器端点
- **WHEN** 客户端请求未列入白名单的 `/actuator/**` 端点
- **THEN** 系统拒绝访问且不返回管理信息

### Requirement: 基础安全响应头
页面和 Portal API 响应 SHALL 设置 `X-Content-Type-Options: nosniff`、拒绝被第三方页面嵌入的 Frame 策略、限制 Referrer 的策略和最小权限的 Permissions Policy。页面响应 SHALL 使用只允许本源资源的 Content Security Policy；生产环境的 HTTPS 响应 SHALL 启用 HSTS。错误响应 MUST 保留适用的安全 Header。

#### Scenario: 页面响应头
- **WHEN** 客户端请求应用页面
- **THEN** 响应包含 CSP、Frame、Content-Type、Referrer 和 Permissions 安全策略

#### Scenario: Portal API 错误响应头
- **WHEN** Portal API 返回 4xx 或 5xx JSON 错误
- **THEN** 响应仍包含适用的安全 Header，且不包含 New API 标识性 Header

#### Scenario: 生产 HTTPS 响应
- **WHEN** `prod` 环境通过受信代理识别为 HTTPS 请求
- **THEN** 响应包含 HSTS；非安全请求不得错误声明当前连接已受 HSTS 保护

### Requirement: 结构化访问日志
每个 Portal API 请求 SHALL 产生一条完成态结构化访问日志，至少包含时间、`requestId`、HTTP 方法、规范化路由、状态码和耗时毫秒数；发生上游调用时 SHALL 记录安全的上游操作名、结果类别和耗时，但不得记录上游 URL。日志 MUST 能用同一个 `requestId` 关联入口与上游事件。

#### Scenario: 成功请求日志
- **WHEN** 一个 Portal API 请求成功完成
- **THEN** 仅产生一条包含必需字段的完成态访问记录，且其中 `requestId` 与响应一致

#### Scenario: 上游失败日志
- **WHEN** 请求因 New API 超时或不可用而失败
- **THEN** 入口和上游日志共享 `requestId`，记录错误类别与耗时但不记录目标地址或原始错误正文

#### Scenario: 查询参数不进入规范化路由
- **WHEN** 请求 URL 含令牌、邮箱或其他查询参数
- **THEN** 访问日志只记录匹配的路由模板或安全路径，不记录原始查询串

### Requirement: 敏感信息不得进入日志
应用日志、访问日志和异常日志 MUST NOT 输出密码、Cookie、`Set-Cookie`、Access Token、完整 API Key、`Authorization`、支付签名或完整请求/响应正文。统一脱敏规则 MUST 对大小写不敏感，并在敏感值意外出现在异常消息或结构化字段时将其替换为固定占位符。

#### Scenario: 请求包含多类凭证
- **WHEN** 请求 Header、Cookie、查询参数或 JSON 字段包含密码、Token、完整 API Key 与支付签名
- **THEN** 请求完成后的可捕获日志中不存在这些原始值

#### Scenario: 异常消息包含敏感值
- **WHEN** 底层异常消息包含 Cookie、Authorization 或私网上游地址
- **THEN** 对外响应不包含这些内容，结构化日志只保留安全错误类别和脱敏后的诊断信息

### Requirement: 认证接口与会话访问边界
`GET /portal/api/auth/options`、认证 CSRF 引导端点、登录和按策略开放的注册 SHALL 允许匿名访问；refresh、logout 与 `GET /portal/api/profile` SHALL 只接受有效 Portal 会话，其中无会话的 logout 仍按幂等规则成功。所有其他受保护 Portal API SHALL 使用同一会话校验结果，且不得接受客户端自行提交的角色或 `New-Api-User` Header 作为身份依据。

#### Scenario: 匿名读取认证选项
- **WHEN** 未登录客户端查询认证选项或获取 CSRF 凭证
- **THEN** 请求被允许且不创建认证会话

#### Scenario: 伪造上游用户头
- **WHEN** 客户端携带自定义 `New-Api-User` Header 访问受保护接口
- **THEN** 系统丢弃该 Header，只依据受保护 Cookie 与上游校验确定身份

#### Scenario: 受保护接口复用身份
- **WHEN** 一个有效会话访问 profile 或后续受保护 Portal API
- **THEN** 安全上下文只包含经过上游确认的普通用户身份，不包含管理权限

### Requirement: 认证写操作 CSRF 与来源防护
系统 SHALL 对 register、login、refresh 和 logout 强制执行 CSRF 防护。客户端 SHALL 先通过匿名安全端点取得可由脚本读取的短期 CSRF Cookie/Token，并在写请求 Header 中回传；生产环境还 MUST 校验 `Origin` 是否属于显式允许的同源来源。CSRF Cookie MUST NOT 承载身份信息，且其 Path、SameSite 和 Secure SHALL 与部署环境一致。

#### Scenario: 合法同源登录
- **WHEN** 同源页面提交匹配的 CSRF Cookie 与 Header 并携带允许的 Origin
- **THEN** 登录请求进入凭据校验

#### Scenario: 缺失 CSRF Token
- **WHEN** 外部页面或脚本直接提交认证写请求且没有匹配 Token
- **THEN** 系统在调用 New API 前返回 `CSRF_REJECTED`

#### Scenario: 生产来源不在白名单
- **WHEN** `prod` 写请求的 Origin 不在显式允许列表
- **THEN** 系统返回 `CSRF_REJECTED`，且不依据 Host 或代理 Header 自动放宽来源

### Requirement: 登录与注册基础频率限制
系统 SHALL 分别限制登录与注册频率，并同时考虑可信客户端地址和规范化用户名维度；超过限制时 SHALL 在请求上游前返回 `RATE_LIMITED`。客户端地址只有在直接连接来源属于配置的可信代理范围时才可从转发 Header 解析，否则 MUST 使用直接连接地址。限流键和日志 MUST NOT 保存明文用户名或完整客户端地址。

#### Scenario: 多次错误登录触发限制
- **WHEN** 同一客户端与用户名组合在窗口内超过登录尝试上限
- **THEN** 后续请求返回 `RATE_LIMITED` 和 Retry-After，且不请求 New API

#### Scenario: 注册频率触发限制
- **WHEN** 同一客户端在注册窗口内超过允许次数
- **THEN** 后续注册返回 `RATE_LIMITED`，即使公开注册策略仍开启

#### Scenario: 不信任任意转发头
- **WHEN** 非可信直接来源伪造 `X-Forwarded-For` 或 `X-Real-IP`
- **THEN** 限流使用直接连接地址，不使用伪造值

### Requirement: 认证安全事件日志
每次登录和注册失败、限流、会话失效及退出撤销失败 SHALL 产生可关联的结构化安全事件，至少包含 `requestId`、操作、结果类别和安全原因码。日志 MUST NOT 包含密码、Cookie、CSRF Token、明文用户名、完整邮箱、完整客户端地址、上游响应正文或能够恢复这些值的限流键。

#### Scenario: 错误密码日志
- **WHEN** 登录因凭据错误被拒绝
- **THEN** 日志记录不可枚举的 `invalid_credentials` 类别及 requestId，不记录用户名和密码

#### Scenario: 会话失效日志
- **WHEN** 上游拒绝现有会话
- **THEN** 日志记录 `session_invalid` 类别且不记录 Cookie 值或上游原始消息

#### Scenario: 上游退出未确认
- **WHEN** logout 已清除本地 Cookie 但上游撤销调用失败
- **THEN** 日志记录可关联的 `revocation_unconfirmed` 结果，响应仍遵守统一错误契约

### Requirement: API Key 会话、CSRF 与来源边界
所有 API Key 接口 SHALL 只接受有效 Portal 会话，并复用同一请求中已由 New API 校验的普通用户身份。创建、编辑、启停、删除和 reveal MUST 通过现有双提交 CSRF 防护及生产精确 Origin 校验；缺失或失配时必须在任何 Token 上游调用前返回 `CSRF_REJECTED`。客户端提交的用户 id、上游身份 Header、owner、role 或 Key 值 MUST 被拒绝或忽略，不能用于授权。

#### Scenario: 匿名读取列表
- **WHEN** 未登录客户端请求 API Key 列表
- **THEN** 系统返回统一 `UNAUTHENTICATED`，不调用 New API Token 路径

#### Scenario: 伪造 owner 或上游 Header
- **WHEN** 已登录用户在请求中提交其他用户 id、owner 或 `New-Api-User`
- **THEN** 系统只使用当前已校验会话身份，且不能读取或修改其他用户的 Key

#### Scenario: reveal 缺少 CSRF
- **WHEN** 会话有效但 reveal 请求缺少匹配 CSRF Cookie/Header 或生产 Origin 不允许
- **THEN** 系统返回 `CSRF_REJECTED`，不向 New API 请求明文

### Requirement: API Key 明文最小暴露
reveal 成功响应 SHALL 设置 `Cache-Control: no-store`，并不得携带可共享缓存的验证器或通过 URL 返回 secret。服务端 MUST NOT 缓存明文，也 MUST NOT 将创建/编辑请求正文、reveal 响应、`Authorization`、掩码前后片段组合或可恢复 Key 的派生值写入访问日志、应用日志、异常、指标标签、追踪属性或安全事件。

#### Scenario: 捕获 reveal 请求日志
- **WHEN** 测试捕获一次成功或失败的 reveal 全部日志与追踪字段
- **THEN** 记录只包含规范化路由、资源 id、requestId、结果类别和耗时，不包含 secret、掩码片段或上游正文

#### Scenario: 上游异常包含 Key
- **WHEN** 上游或底层异常文本意外包含完整 Key
- **THEN** 对外响应与所有结构化日志只保留固定脱敏占位符和安全错误类别

### Requirement: API Key 安全事件
创建、编辑、启停、删除和 reveal SHALL 产生可关联的完成态安全事件，至少包含 `requestId`、操作、当前用户的不可逆主体标识、资源 id、结果类别和安全原因码。事件 MUST NOT 包含 Key 名称、完整或掩码 Key、模型/IP 限制正文、额度数值、Cookie、CSRF Token、上游消息或客户端提交的用户标识。

#### Scenario: 成功 reveal 事件
- **WHEN** 当前用户成功显示一个 Key
- **THEN** 系统记录 `api_key_reveal` 成功类别及必要标识，但无法从事件恢复 secret 或业务限制

#### Scenario: 跨用户尝试
- **WHEN** 用户请求不存在或不属于自己的 Key
- **THEN** 系统记录统一 `resource_not_found` 结果，不记录推测的所有者或对象内容
