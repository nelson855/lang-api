# new-api-adapter Specification

## Purpose

建立 Lang API 与冻结版 New API 之间唯一且可测试的适配边界，集中控制上游路径、数据结构、传输可靠性及敏感认证信息，防止上游实现细节扩散到业务与前端。

## Requirements

### Requirement: New API 适配边界隔离
所有 New API HTTP 调用、路径常量和上游请求/响应 DTO SHALL 只存在于 `upstream.newapi` 适配边界。业务代码 MUST 通过语义化操作使用适配器，且 MUST NOT 拼接上游路径、引用上游 DTO、访问 New API 数据库或接受调用方提供的任意上游 URL。

#### Scenario: 执行声明过的上游操作
- **WHEN** 业务层调用一个已声明的 New API 语义化操作
- **THEN** 适配器使用该操作固定的方法与相对路径访问配置的唯一 New API Base URL

#### Scenario: 阻止边界外依赖
- **WHEN** 自动化架构测试扫描 Portal API 代码
- **THEN** `upstream.newapi` 之外不存在 New API DTO、路径常量或底层 HTTP 客户端调用

### Requirement: 上游传输资源受控
New API 客户端 SHALL 使用有界连接池并分别配置连接池获取、建立连接、读取和写入超时。连接池耗尽、连接失败、读写超时 MUST 转换为 Portal API 稳定错误；客户端默认 MUST 禁用传输层自动重试，尤其不得自动重放登录、注册、资料修改、创建 Key 或支付等写操作。

#### Scenario: 连接池有界
- **WHEN** 并发请求达到配置的连接池上限
- **THEN** 额外请求只等待配置的连接池获取时限，超时后释放资源并返回稳定上游不可用错误

#### Scenario: 建连失败
- **WHEN** New API 地址不可达或无法在连接时限内建立连接
- **THEN** 调用返回 `UPSTREAM_UNAVAILABLE`，连接资源被释放且响应不包含目标地址

#### Scenario: 上游读写超时
- **WHEN** 向 New API 写入请求或等待其响应超过对应时限
- **THEN** 调用被终止并返回 `UPSTREAM_TIMEOUT`，连接不会泄漏

#### Scenario: 写操作不被重放
- **WHEN** 一个写操作在发送期间断开连接或超时
- **THEN** 客户端最多发起一次上游请求，不进行自动重试

### Requirement: Cookie 与 Header 集中转换
适配器 SHALL 对上游请求 Header、响应 Header 和 Cookie 采用显式白名单。普通请求仅允许发送操作需要的 `Accept`、`Content-Type`、`X-Request-Id`；认证操作可额外声明 New API 所需的会话 Cookie 与 `New-Api-User`。浏览器的 `Host`、`Forwarded`、`X-Forwarded-*`、`Authorization` 及其他 Header MUST NOT 被整批透传。上游响应 Header 默认全部丢弃；只有操作明确声明的内容类型或会话 Cookie可被转换。

#### Scenario: 普通公开调用过滤请求 Header
- **WHEN** 浏览器请求携带 Cookie、Authorization、代理 Header 和自定义 Header，业务触发无需认证的上游调用
- **THEN** 上游只收到该操作白名单内的 Header 和统一 `X-Request-Id`

#### Scenario: 转换允许的会话 Cookie
- **WHEN** 认证操作收到已声明的 New API 会话 Cookie
- **THEN** 适配器移除上游 Domain，限制 Path，设置 `HttpOnly` 与适用环境的 `Secure`、`SameSite` 属性后才允许返回浏览器

#### Scenario: 丢弃未声明的响应 Header
- **WHEN** New API 返回服务标识、代理信息、内部地址或未列入操作白名单的 Header
- **THEN** Portal API 响应不包含这些 Header

### Requirement: 上游响应与错误显式翻译
每个 New API 操作 SHALL 只读取其响应 DTO 中明确允许的字段并转换为 Lang API DTO，未知字段 MUST 被忽略。适配器 MUST 同时识别非 2xx HTTP 状态和 HTTP 200 下 `success=false` 的上游业务失败，并转换为稳定内部异常；原始上游消息不得直接成为 Portal API 对外消息。

#### Scenario: 上游新增未知字段
- **WHEN** New API 在兼容响应中增加适配器未声明的字段
- **THEN** 已声明字段仍可转换，新增字段不会出现在 Lang API 响应中

#### Scenario: HTTP 200 业务失败
- **WHEN** New API 返回 HTTP 200 且响应包装为 `success=false`
- **THEN** 适配器按当前操作的错误映射处理失败，而不是把它当作成功数据返回

#### Scenario: 非法或非 JSON 上游响应
- **WHEN** New API 返回无法解析、字段缺失或非预期内容类型的响应
- **THEN** 系统返回安全的 `UPSTREAM_ERROR`，不透传原始正文

### Requirement: 可复用上游契约测试基座
工程 SHALL 提供不依赖真实 New API、数据库或网络的 New API 契约测试基座，可记录收到的方法、路径、Header 和正文，并可模拟正常响应、业务失败、非 2xx、断连、延迟及未知字段。测试夹具 MUST 使用 P1-02 脱敏样例或等价的无敏感数据结构。

#### Scenario: 验证请求与字段转换
- **WHEN** 某个后续业务模块新增 New API 适配操作
- **THEN** 它能复用测试基座断言请求白名单、响应裁剪和错误映射，而无需启动 Compose

#### Scenario: 模拟超时和断连
- **WHEN** 契约测试配置模拟服务器延迟或连接中断
- **THEN** 测试能够验证超时分类、无自动重试及连接资源释放行为
