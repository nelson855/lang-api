# public-model-gateway Specification

## Purpose

通过唯一公网边缘入口向 SDK 和 API 客户端提供最小、可控且可观测的 OpenAI 兼容模型数据面，使 Lang API 创建的 Key 能完成真实普通与 SSE 流式调用，同时隔离 Portal 控制面和 New API 管理能力。

## Requirements

### Requirement: 首批 OpenAI 路径和方法精确白名单
公共模型 API SHALL 仅开放 `POST /v1/chat/completions` 与 `GET /v1/models`，并将其直接转发到 New API Relay。路径匹配 MUST 是精确匹配；尾随斜杠、子路径、大小写变体、其他方法以及 `/v1/responses`、`/v1/embeddings`、`/v1/images/*`、`/v1/audio/*`、`/v1/messages`、`/v1beta/*`、`/api/*`、`/setup/*` 和 New API 默认页面 MUST NOT 到达 New API。

#### Scenario: 调用聊天完成接口
- **WHEN** 客户端使用 POST 请求精确路径 `/v1/chat/completions`
- **THEN** 网关将请求直接转发到 New API Relay，且请求不经过 Portal API

#### Scenario: 获取模型列表
- **WHEN** 客户端使用 GET 请求精确路径 `/v1/models`
- **THEN** 网关将请求直接转发到 New API Relay，并保留模型协议成功响应

#### Scenario: 白名单路径使用错误方法
- **WHEN** 客户端使用 GET 请求 `/v1/chat/completions` 或使用 POST 请求 `/v1/models`
- **THEN** 网关返回自有 `METHOD_NOT_ALLOWED`/405，且 New API 不收到请求

#### Scenario: 请求未开放模型路径
- **WHEN** 客户端请求 `/v1/responses`、`/v1/chat/completions/`、`/v1beta/models` 或其他未声明路径
- **THEN** 网关返回自有 `NOT_FOUND`/404，且 New API 不收到请求

#### Scenario: 请求管理面
- **WHEN** 客户端通过公网入口请求 `/api/status`、`/setup/` 或 New API 默认页面
- **THEN** 请求不会到达 New API，并返回不含其品牌或内部地址的自有响应

### Requirement: Bearer Key 与请求 Header 最小转发
公开模型路径 SHALL 要求格式为 `Authorization: Bearer <api-key>` 的凭证，并把该 Header 原值仅转发给 New API Relay。网关 SHALL 为每次请求生成自有 `requestId`，通过 `X-Request-Id` 转发并在响应 Header 返回；客户端提供的 Cookie、Portal 会话、`New-Api-User`、`Host`、`Forwarded`、`X-Forwarded-*` 及未声明 Header MUST NOT 原样进入上游。允许的协议 Header 第一阶段仅包含 `Authorization`、`Content-Type` 和 `Accept`，内部 Host 与连接 Header由网关重建。

#### Scenario: 合法 Bearer Key
- **WHEN** 客户端携带合法 `Authorization: Bearer sk-...` 调用白名单路径
- **THEN** New API 收到该 Authorization、受控协议 Header 和网关生成的 `X-Request-Id`，但不收到浏览器 Cookie 或客户端伪造的身份/代理 Header

#### Scenario: 缺少或格式错误的 Authorization
- **WHEN** 客户端未提供 Authorization，或 scheme/凭证为空或不符合 Bearer 结构
- **THEN** 网关在访问 New API 前返回自有 `AUTHENTICATION_REQUIRED`/401

#### Scenario: 客户端伪造请求标识和转发头
- **WHEN** 客户端提交自己的 `X-Request-Id`、`X-Forwarded-For`、`Forwarded` 或 `New-Api-User`
- **THEN** 网关不把这些值作为上游身份或日志标识，并使用自己生成的 requestId 与受信连接信息

### Requirement: 普通与 SSE 响应正确转发
成功的非流式模型 JSON 和 `GET /v1/models` JSON SHALL 保持协议正文与状态码传递。`stream=true` 的聊天完成响应 SHALL 逐块转发 `text/event-stream` 数据，禁用代理响应缓冲、模型响应缓存和会改变逐块交付的压缩；客户端必须在上游完成前收到首个及后续数据块，`[DONE]` 等协议内容不得被网关拼接或改写。

#### Scenario: 非流式真实调用
- **WHEN** 有效 Key 调用已配置模型且 `stream=false`
- **THEN** 客户端收到一次完整的 OpenAI 兼容 JSON 响应，状态码和业务正文来自真实 New API Relay 链路

#### Scenario: SSE 逐块到达
- **WHEN** 上游按时间间隔发送多个 SSE 数据块
- **THEN** 客户端在响应结束前依次收到这些数据块，而不是等待完整响应被代理缓冲后一次返回

#### Scenario: 模型响应不被缓存
- **WHEN** 相同客户端重复提交相同模型请求
- **THEN** 每次请求都到达 New API，网关不复用先前正文、ETag 或缓存结果

### Requirement: 客户端取消和上游重试边界
网关 SHALL 在客户端断开普通或流式请求后及时关闭对应上游请求并释放连接。模型请求一旦开始向上游发送或开始向客户端输出，MUST NOT 因连接错误、超时、上游状态或备用地址而自动重试；同一客户端请求最多触发一次 New API Relay 调用。

#### Scenario: 流式请求中途取消
- **WHEN** 客户端在 SSE 完成前关闭连接
- **THEN** 上游在有界时间内观察到取消或连接关闭，网关释放资源且不启动第二次请求

#### Scenario: 上游发送后断开
- **WHEN** New API 在已接收模型请求后断开连接
- **THEN** 网关不重放请求，并返回尚可发送的安全网关错误或直接结束已开始的流

### Requirement: 请求与连接资源有界
模型网关 SHALL 配置有限请求正文、连接建立超时、请求发送超时、响应读取超时、单客户端请求速率和并发连接数。超过正文上限 MUST 在访问 New API 前返回 `PAYLOAD_TOO_LARGE`/413；触发边缘速率或并发限制 SHALL 返回 `RATE_LIMITED`/429 与 `Retry-After`。读取超时必须允许合理的模型生成与 SSE 心跳间隔，但不能无限占用连接。

#### Scenario: 请求正文过大
- **WHEN** Content-Length 已超上限或读取中的请求超过上限
- **THEN** 网关返回自有 413，New API 不收到完整模型请求且错误中不回显正文

#### Scenario: 触发边缘限流
- **WHEN** 同一受信客户端地址超过配置的请求速率或并发连接上限
- **THEN** 网关返回自有 429 和可解析的 `Retry-After`，且被拒绝请求不进入 New API

#### Scenario: 上游长时间无数据
- **WHEN** New API 在配置的响应读取时限内未返回任何数据或 SSE 心跳
- **THEN** 尚未开始响应时返回 `GATEWAY_TIMEOUT`/504；已开始流式响应时关闭连接且不注入伪造 SSE 完成事件

### Requirement: 稳定且无品牌的网关错误
网关在尚未开始客户端响应时 SHALL 使用 UTF-8 JSON 返回稳定错误，结构为 `{requestId,error:{code,message,type}}`，其中 `type="gateway_error"`。至少 SHALL 覆盖 `NOT_FOUND`/404、`METHOD_NOT_ALLOWED`/405、`AUTHENTICATION_REQUIRED`/401、`PAYLOAD_TOO_LARGE`/413、`RATE_LIMITED`/429、`UPSTREAM_REJECTED`/400、`GATEWAY_UNAVAILABLE`/502 或 503、`GATEWAY_TIMEOUT`/504。网关生成或拦截的错误 MUST NOT 包含 New API/One API 品牌、私网主机、渠道、节点、供应商密钥、完整 Authorization 或上游原始正文。

#### Scenario: New API 不可连接
- **WHEN** edge 无法解析、连接或获得可用的 New API Relay 响应
- **THEN** 客户端收到自有 `GATEWAY_UNAVAILABLE` 与当前 requestId，不看到内部 upstream 地址或默认 Nginx HTML 页

#### Scenario: 上游拒绝请求
- **WHEN** New API 在响应正文开始前以 4xx 拒绝无效 Key、模型限制或无效参数
- **THEN** 网关丢弃带品牌的原始错误正文并返回对应安全稳定错误，同时保留 HTTP 失败语义

#### Scenario: 已开始的 SSE 内部错误
- **WHEN** SSE 响应头或数据块已经发送后上游发生错误
- **THEN** 网关不尝试用新的 JSON 错误替换已开始的流、不自动重试，并通过完成态日志记录中断类别

### Requirement: 响应 Header 与缓存边界
模型响应 SHALL 返回自有 `X-Request-Id`，并移除已知 New API/One API 标识性 Header、内部请求 id、upstream 地址、缓存调试 Header 和可暴露实现的信息。模型响应 MUST 使用不缓存策略，且公共模型 API MUST NOT 启用允许任意来源的浏览器 CORS；错误响应不得使用默认 Nginx HTML 页面。

#### Scenario: 检查普通成功响应 Header
- **WHEN** 客户端完成一次非流式模型调用
- **THEN** 响应包含当前 `X-Request-Id` 和正确内容类型，不包含已知 New API 标识、内部地址或缓存命中信息

#### Scenario: 浏览器预检
- **WHEN** 任意网页来源向模型 API 发起未声明的 OPTIONS 预检
- **THEN** 网关返回自有方法错误且不添加通配 `Access-Control-Allow-Origin`

### Requirement: 模型数据面日志最小化
每个模型网关请求 SHALL 产生一条完成态结构化访问记录，至少包含时间、requestId、方法、精确白名单路由或固定 unmatched 标识、状态码、总耗时、上游首字节/响应耗时（可得时）和安全结果类别。日志 MUST NOT 记录原始 URI 查询串、Authorization、Cookie、请求/响应正文、模型名、提示词、生成内容、完整客户端地址、New API 地址或供应商信息；流式中断和客户端取消必须可与普通完成区分。

#### Scenario: 成功流式请求日志
- **WHEN** SSE 请求正常完成
- **THEN** 日志记录规范化路由、requestId、状态、耗时和 `stream_completed`，不含任一 SSE 数据块或凭证

#### Scenario: 客户端取消日志
- **WHEN** 客户端中途取消流式请求
- **THEN** 日志记录 `client_cancelled` 类别及必要时序，不把断开的响应内容写入日志

#### Scenario: 查询串包含敏感值
- **WHEN** 请求 URI 查询参数意外包含 Key 或其他敏感值
- **THEN** 访问日志只记录规范化路径或 unmatched 标识，不记录原始查询串

### Requirement: API Key 限制由 New API 真实执行
网关 SHALL 透明使用 P1-06 生成的 Bearer Key 访问 New API，不在 edge 或 Portal API 复制 Key 状态、额度、过期时间、模型限制、路由或计费逻辑。最终验收 MUST 使用真实供应商渠道分别证明：有效 Key 可完成非流式和 SSE 调用；禁用、过期、额度不足和模型受限 Key 被 New API 实际拒绝。

#### Scenario: 有效受限 Key 调用允许模型
- **WHEN** P1-06 创建的启用且有额度 Key 调用其允许的真实模型
- **THEN** 请求经公开 Base URL 成功完成，且消费、路由与计费仍由 New API 处理

#### Scenario: Key 状态与限制生效
- **WHEN** 分别使用禁用、已过期、额度不足或不允许目标模型的 Key 调用
- **THEN** New API 拒绝请求，网关返回安全失败且不在本地绕过或重实现限制

#### Scenario: 缺少真实供应商前置条件
- **WHEN** 环境尚未配置可用渠道、测试模型或额度
- **THEN** 自动化网关契约测试仍可运行，但真实调用验收必须保持未完成并明确记录外部前置条件
