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
Portal API SHALL 至少提供以下稳定错误码与 HTTP 状态映射：`INVALID_ARGUMENT`/400、`UNAUTHENTICATED`/401、`FORBIDDEN`/403、`NOT_FOUND`/404、`METHOD_NOT_ALLOWED`/405、`PAYLOAD_TOO_LARGE`/413、`UPSTREAM_ERROR`/502、`UPSTREAM_UNAVAILABLE`/503、`UPSTREAM_TIMEOUT`/504、`INTERNAL_ERROR`/500。未知异常 MUST 映射为 `INTERNAL_ERROR`，不得把异常消息直接返回客户端。

#### Scenario: 参数校验失败
- **WHEN** 请求参数、路径参数或 JSON 正文未通过声明式校验
- **THEN** 系统返回 HTTP 400 和 `INVALID_ARGUMENT`，消息能够指出公开字段但不包含内部类型或堆栈

#### Scenario: 未认证访问
- **WHEN** 客户端访问一个声明为需要认证的 Portal API 且没有有效会话
- **THEN** 系统返回 JSON 格式的 HTTP 401 和 `UNAUTHENTICATED`

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
