# portal-api-contract Specification

## Purpose

定义所有 Portal API 共同遵守的稳定响应、错误、请求标识和入口限制，使前端无需理解 New API 的原始协议，并让失败行为可以被一致测试和追踪。

## Requirements

### Requirement: 统一响应包装
所有 `/portal/api/**` 成功响应 SHALL 使用 `{requestId, data}` 结构，失败响应 SHALL 使用 `{requestId, error: {code, message}}` 结构；分页数据 MUST 使用 `{items, page, pageSize, total}` 作为 `data`。响应 MUST NOT 包含 New API 的 `success`、`message` 原始包装、内部异常堆栈、私网地址、渠道 ID、节点名或数据库错误。

#### Scenario: 普通成功响应
- **WHEN** 一个 Portal API 请求成功并返回业务数据
- **THEN** HTTP 响应为 JSON，响应体只通过顶层 `requestId` 和 `data` 返回约定内容

#### Scenario: 分页成功响应
- **WHEN** 一个 Portal API 请求成功并返回分页结果
- **THEN** `data` 包含 `items`、从 1 开始的 `page`、正整数 `pageSize` 和非负 `total`

#### Scenario: 失败响应不泄露上游信息
- **WHEN** Portal API 因应用异常或上游异常失败
- **THEN** 响应使用统一失败结构和安全中文消息，且不包含上游原始响应、响应头、私网主机名或异常堆栈

### Requirement: 请求标识生命周期
系统 SHALL 为每个 Portal API 请求确定唯一 `requestId`。客户端提供的 `X-Request-Id` 仅在其长度为 8 至 64 且只含 ASCII 字母、数字、点、下划线、冒号或连字符时可被采用；缺失或不合法时 MUST 生成以 `req_` 开头的新标识。最终标识 MUST 同时出现在响应 `X-Request-Id` Header 和响应体中，并传给实际发生的上游调用。

#### Scenario: 采用合法客户端标识
- **WHEN** 客户端提供符合格式的 `X-Request-Id`
- **THEN** 响应 Header、响应体、访问日志和上游请求使用同一个标识

#### Scenario: 替换不合法客户端标识
- **WHEN** 客户端未提供标识或提供包含控制字符、空白、超长内容等不合法值
- **THEN** 系统生成新标识且不把原值写入响应、日志或上游请求

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

### Requirement: Portal 路由、方法与正文白名单
系统 SHALL 只处理代码中明确声明的 `/portal/api/**` 路径和 HTTP 方法，MUST NOT 提供任意 URL、路径拼接或通配转发能力。已声明路径使用错误方法时 SHALL 返回统一 JSON `METHOD_NOT_ALLOWED`/405；未知 Portal 路径 SHALL 返回统一 JSON `NOT_FOUND`/404。Portal API 请求正文默认上限 SHALL 为 1,048,576 字节，且对有无 `Content-Length` 的请求都生效。

#### Scenario: 未知 Portal 路径
- **WHEN** 客户端请求未声明的 `/portal/api/unknown`
- **THEN** 系统返回 JSON `NOT_FOUND`/404，且不会回退到 `index.html` 或请求 New API

#### Scenario: 已知路径使用错误方法
- **WHEN** 客户端对只允许 GET 的 Portal API 使用 POST
- **THEN** 系统返回 JSON `METHOD_NOT_ALLOWED`/405，且不会请求 New API

#### Scenario: 声明长度超过限制
- **WHEN** Portal API 请求声明的正文长度超过配置上限
- **THEN** 系统在业务处理前返回 JSON `PAYLOAD_TOO_LARGE`/413

#### Scenario: 流式正文超过限制
- **WHEN** 未提供可信 `Content-Length` 的请求在读取过程中超过配置上限
- **THEN** 系统停止继续处理并返回 JSON `PAYLOAD_TOO_LARGE`/413

### Requirement: API Key 资源接口契约
Portal API SHALL 只通过受保护的 `/portal/api/api-keys` 资源路径提供单用户 Key 管理：`GET /portal/api/api-keys`、`POST /portal/api/api-keys`、`GET /portal/api/api-keys/{id}`、`PUT /portal/api/api-keys/{id}`、`PUT /portal/api/api-keys/{id}/status`、`DELETE /portal/api/api-keys/{id}` 与 `POST /portal/api/api-keys/{id}/reveal`。列表 SHALL 使用统一分页包装，并只接受声明的 `page`、`pageSize`、`name` 和 `status` 查询参数；未知参数、非法正整数 id、过长搜索词或未声明方法 MUST 在访问上游前失败。

#### Scenario: 合法列表请求
- **WHEN** 已登录用户提供合法分页、名称和状态条件
- **THEN** 系统返回统一分页包装，且字段名、枚举和时间不依赖 New API DTO

#### Scenario: 非法查询参数
- **WHEN** 请求包含 page=0、超上限 pageSize、非法状态或未声明查询参数
- **THEN** 系统返回 `INVALID_ARGUMENT`，不调用 New API

#### Scenario: 错误方法或未知子路径
- **WHEN** 客户端对 API Key 资源使用未声明方法或访问未知子路径
- **THEN** 系统分别返回统一 `METHOD_NOT_ALLOWED` 或 `NOT_FOUND`，不进行透明上游转发

### Requirement: API Key 稳定错误语义
API Key 接口 SHALL 使用现有通用错误，并增加 `RESOURCE_CONFLICT`/409、`API_KEY_LIMIT_REACHED`/409 和 `OPERATION_RESULT_UNKNOWN`/502。`RESOURCE_CONFLICT` 表示当前状态不允许目标操作；`API_KEY_LIMIT_REACHED` 表示上游用户 Key 数量上限；`OPERATION_RESULT_UNKNOWN` 表示不可重试写操作发送后无法确认结果。不存在与跨用户资源统一使用 `NOT_FOUND`；任何错误 MUST NOT 包含完整 Key、上游消息、私网地址或内部字段。

#### Scenario: 过期 Key 直接启用
- **WHEN** Key 仍过期且用户请求启用
- **THEN** 系统返回 HTTP 409 和 `RESOURCE_CONFLICT`，消息只说明需先调整公开限制字段

#### Scenario: 达到数量上限
- **WHEN** New API 拒绝创建，因为当前用户达到 Key 数量上限
- **THEN** 系统返回 HTTP 409 和 `API_KEY_LIMIT_REACHED`，不透传上游配置或原始消息

#### Scenario: 删除结果无法确认
- **WHEN** 删除请求已发送但上游响应在完成前中断
- **THEN** 系统返回 HTTP 502 和 `OPERATION_RESULT_UNKNOWN`，提示用户刷新核对且不自动重试
