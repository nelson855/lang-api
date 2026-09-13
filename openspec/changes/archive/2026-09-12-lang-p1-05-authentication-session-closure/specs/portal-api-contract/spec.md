## MODIFIED Requirements

### Requirement: 稳定错误目录
Portal API SHALL 至少提供以下稳定错误码与 HTTP 状态映射：`INVALID_ARGUMENT`/400、`UNAUTHENTICATED`/401、`INVALID_CREDENTIALS`/401、`FORBIDDEN`/403、`REGISTRATION_DISABLED`/403、`CSRF_REJECTED`/403、`NOT_FOUND`/404、`METHOD_NOT_ALLOWED`/405、`PAYLOAD_TOO_LARGE`/413、`RATE_LIMITED`/429、`UPSTREAM_ERROR`/502、`UPSTREAM_UNAVAILABLE`/503、`UPSTREAM_TIMEOUT`/504、`INTERNAL_ERROR`/500。未知异常 MUST 映射为 `INTERNAL_ERROR`，不得把异常消息直接返回客户端；`RATE_LIMITED` 响应 SHALL 提供可解析的 `Retry-After` Header。

#### Scenario: 参数校验失败
- **WHEN** 请求参数、路径参数或 JSON 正文未通过声明式校验
- **THEN** 系统返回 HTTP 400 和 `INVALID_ARGUMENT`，消息能够指出公开字段但不包含内部类型或堆栈

#### Scenario: 未认证访问
- **WHEN** 客户端访问一个声明为需要认证的 Portal API 且没有有效会话
- **THEN** 系统返回 JSON 格式的 HTTP 401 和 `UNAUTHENTICATED`

#### Scenario: 登录凭据错误
- **WHEN** 登录因未知用户、错误密码或禁用账户被拒绝
- **THEN** 系统统一返回 HTTP 401 和 `INVALID_CREDENTIALS`

#### Scenario: 注册策略关闭
- **WHEN** 客户端在公开注册关闭时调用注册接口
- **THEN** 系统返回 HTTP 403 和 `REGISTRATION_DISABLED`

#### Scenario: CSRF 校验失败
- **WHEN** 认证写操作缺少或携带无效 CSRF 凭证或来源
- **THEN** 系统返回 HTTP 403 和 `CSRF_REJECTED`

#### Scenario: 认证请求被限流
- **WHEN** 登录或注册请求超过对应频率限制
- **THEN** 系统返回 HTTP 429、`RATE_LIMITED` 和 `Retry-After`，且不调用上游

#### Scenario: 上游超时或不可用
- **WHEN** New API 调用分别发生超时或无法建立可用连接
- **THEN** 系统分别返回 `UPSTREAM_TIMEOUT`/504 或 `UPSTREAM_UNAVAILABLE`/503，且消息不暴露上游地址

#### Scenario: 未知内部异常
- **WHEN** 未被更具体规则识别的异常到达全局异常边界
- **THEN** 系统返回 HTTP 500 和 `INTERNAL_ERROR`，响应仍包含当前 `requestId`
