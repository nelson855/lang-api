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
