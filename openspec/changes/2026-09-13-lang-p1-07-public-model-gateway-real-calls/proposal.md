## Why

LANG-P1-06 已交付可用的 API Key 生命周期，但公共数据面仍被 `edge-nginx` 全部关闭，用户无法通过 Lang API 的公开 Base URL 调用模型。LANG-P1-07 需要在不把长连接流量引入 Portal API、也不开放 New API 管理面的前提下，建立最小且可验证的 OpenAI 兼容模型网关，完成第一条真实调用链路。

## What Changes

- 将首批公网模型协议严格限定为 OpenAI 兼容的 `POST /v1/chat/completions` 与 `GET /v1/models`；Anthropic、Gemini、Responses、Embeddings、图片、音频及其他未实测路径继续返回自有 404。
- 在 `edge-nginx` 增加专用 New API Relay upstream 和精确路径/方法白名单，模型流量直接进入 New API，不经过 Spring Boot Portal API。
- 只向上游传递受控 Header：保留 `Authorization`、模型协议所需内容类型与自有 `X-Request-Id`，丢弃 Cookie、Portal 会话、客户端伪造的转发头和无关 Header。
- 为请求正文、连接、发送、读取、并发和请求速率设置有界策略；显式关闭代理缓存、SSE 响应缓冲、响应压缩和上游自动重试，并在客户端取消后及时释放连接。
- 为非法路径/方法、正文过大、边缘限流、上游不可用和超时提供不含 New API 品牌的 OpenAI 风格稳定 JSON 错误；已开始输出的流式响应不重试、不改写。
- 增加模型网关专用结构化访问日志，只记录请求 ID、规范化路径、方法、状态、耗时与安全结果类别，不记录查询串、Authorization、Cookie、请求/响应正文、模型输入或生成内容。
- 让 `/portal/api/public-config` 只在 OpenAI 网关已部署且配置了合法公开 URL 时发布 `OPENAI` Base URL；本地使用 `api.localhost` 测试域名，正式域名保留为部署配置。
- 增加 Nginx 配置校验、精确白名单、Header、安全错误、普通响应、SSE 首块/逐块传输、取消、超时、限流及真实供应商调用验收；真实调用只有在提供供应商账号和测试额度后才能标记完成。

## Capabilities

### New Capabilities

- `public-model-gateway`: 定义首批 OpenAI 兼容公开模型路径、认证转发、流式行为、资源限制、稳定错误、日志隐私和真实调用验收。

### Modified Capabilities

- `local-compose-environment`: 将 P1-07 前“公共 Relay 保持关闭”的阶段约束替换为仅通过 edge 精确开放已验证 OpenAI 路径，同时继续隔离 New API 管理面与数据服务。
- `public-portal-config`: 增加公开 Base URL 与网关实际开放协议保持一致的发布门禁，防止页面宣传未启用或不可达的协议。

## Impact

- 网关与部署：修改 `gateway/nginx.conf`、`deploy/compose.yml`、环境变量模板和运行手册；`edge-nginx` 开始通过现有 `relay` 内部网络访问 `new-api:3000`。
- Portal API：不参与模型请求转发；仅使用现有公开配置能力发布 OpenAI Base URL，必要时补充配置一致性校验。
- 测试：扩展 `integration-tests`，增加可控普通/SSE 上游 fixture、Nginx 静态与容器测试，以及需外部供应商凭证的显式真实验收脚本。
- 安全：公网仍不能访问 New API `/api/*`、`/setup/*`、默认页面、未声明 `/v1/*` 或 `/v1beta/*`；不新增 CORS、数据库、计费或路由实现。
- 前置条件：LANG-P1-06 已完成；最终真实调用验收需要至少一个已配置且可用的 New API 模型渠道、测试模型和额度，这些凭证不得进入版本库。
