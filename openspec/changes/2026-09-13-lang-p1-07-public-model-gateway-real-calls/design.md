## Context

参见 [proposal.md](./proposal.md) 的动机与范围。当前 `edge-nginx` 是唯一发布宿主机端口的服务，但配置明确拒绝所有 `/v1/*` 与 `/v1beta/*`；它只把页面和 `/portal/api/*` 转发给 `lang-api`。`edge-nginx` 与 `new-api` 已共同加入内部 `relay` 网络，New API v0.13.2 的 `/v1/models` 与 `/v1/chat/completions` 无 Key 401 边界已实测，因此不需要改变容器拓扑即可开放最小数据面。

P1-06 已完成 API Key 创建、限制、启停与 reveal，Key 对外规范为 `sk-...`。New API 继续负责 Key 校验、渠道路由、额度扣减、模型限制和计费；Portal API 的 5 秒控制面超时及 1 MiB 正文限制不适合模型长连接，也不应出现在数据面链路中。

当前公开配置已经支持 `OPENAI`、`ANTHROPIC`、`GEMINI` 地址，但各环境默认不启用协议。P1-07 只开放已有本地证据支撑的 OpenAI 两条路径；真实成功调用仍依赖运维在 New API 私网后台配置至少一个供应商渠道、可用模型和测试额度。该外部条件不阻塞网关契约实现，但阻塞最终真实调用验收完成。

## Goals / Non-Goals

**Goals:**

- 让模型请求保持 `SDK → edge-nginx → New API Relay` 的短链路，控制面与数据面互不耦合。
- 用虚拟主机、精确 location、方法门禁和 Header 重建形成默认拒绝的公网边界。
- 同时正确支持普通 JSON 与 SSE 流式响应，并可验证首块、逐块、取消和无重试。
- 对网关自身及可拦截的上游失败提供稳定 JSON，不暴露 New API 品牌或私网实现。
- 让网关配置随自有 edge 镜像版本化，并让 public-config 只发布实际开放的 `/v1` Base URL。

**Non-Goals:**

- 不开放 `/v1/responses`、Embeddings、图片、音频、Anthropic `/v1/messages` 或 Gemini `/v1beta/*`；每种新增协议都需先完成真实兼容性验证并单独更新白名单。
- 不在 Nginx 或 Portal API 解析提示词、统计 Token、执行计费、选择渠道或复制 Key 状态。
- 不在本阶段增加语义缓存、请求内容审计、WAF、API Key 级边缘限流、跨节点全局限流或独立 Java/Go 数据面。
- 不在仓库保存供应商 Key、用户 API Key、真实生产域名或测试响应正文。
- 不完成生产 TLS/CDN/多节点可信代理设计；这些在 P1-12 收口，本阶段保留清晰配置入口。

## Decisions

### 1. 继续由 Nginx 承担数据面，不经过 Portal API

模型链路固定为：

```text
SDK / API 客户端
  → MODEL_API_SERVER_NAME:EDGE_HTTP_PORT
  → edge-nginx 精确白名单
  → relay 网络
  → new-api:3000
  → 真实模型供应商
```

用户站点仍为 `客户端 → edge-nginx → lang-api`，两个虚拟主机共享一个 edge 容器和端口，但路由表完全分离。模型 API 主机不代理页面、Portal API 或 New API 管理面；用户站点主机不代理模型路径。

备选方案是 Spring WebFlux 转发，可以统一 Java 错误结构，但会让大正文、长连接、SSE 取消和背压进入控制面，并需要重复实现成熟代理能力，不采用。宽泛转发 `/v1/` 更省配置，但会在 New API 升级时静默公开新接口，也不采用。

### 2. 首批只开放两个 exact location

模型虚拟主机只声明：

- `location = /v1/chat/completions`：仅 POST；同一路径通过请求体 `stream=false/true` 支持普通或 SSE；
- `location = /v1/models`：仅 GET。

其他请求统一落入模型主机的固定 JSON 404。每个 exact location 在代理前用只产生 `return` 的安全方法门禁返回 405；不启用浏览器 CORS，因此 OPTIONS 同样返回 405。尾随斜杠、大小写变化或子路径不会命中。

不采用正则聚合 `/v1/(chat/completions|models)`，因为不同方法、请求正文与未来超时策略容易在同一块中相互污染；两个短小 location 的重复比隐藏边界更安全。

### 3. 把 gateway 配置烘焙进自有 edge 镜像

新增 `gateway/Dockerfile`，以当前 digest 锁定的 `nginx:1.27-alpine` 为基础，将 `gateway/nginx.conf` 复制到 `/etc/nginx/templates/default.conf.template`。官方入口在启动时仅替换 `NGINX_ENVSUBST_FILTER` 明确允许的变量，Nginx 自身的 `$request_id`、`$uri`、`$status` 等变量保持原样。Compose 不再用宿主机只读挂载覆盖运行配置，避免“镜像版本不变但网关行为漂移”。

edge 镜像使用项目版本和 Git revision 的 OCI label；Compose 构建参数从部署入口显式传入，缺失时本地可标识为 `development`，生产发布不得使用该占位值。`nginx -t`、配置静态守卫和镜像 label 检查进入验证流程。

备选方案是继续挂载配置，开发修改更快，但无法把线上网关行为绑定到不可变镜像，不满足可追溯性；生产/开发两份配置又容易漂移，也不采用。

### 4. 两个虚拟主机用部署变量区分

模板使用 `PORTAL_SERVER_NAME` 与 `MODEL_API_SERVER_NAME`。本地默认分别为 `localhost` 和 `api.localhost`，客户端通过 `http://api.localhost:${EDGE_HTTP_PORT}/v1` 调用；`localhost`/`127.0.0.1` 继续落到默认用户站点。生产在 P1-12 由外部配置替换正式域名与 TLS 入口，但两个名字不得相同。

`deploy/.env.example` 增加非敏感的 server name、公开 OpenAI URL 和网关资源边界。部署预检验证：两个主机非空且不同；`PORTAL_OPENAI_URL` 的 host 与模型主机一致并以 `/v1` 结束；启用协议只能是 `OPENAI`；New API 私网名和运维端口不得出现在公开 URL。

### 5. 请求 Header 采用关闭默认转发后逐项重建

模型 location 使用 `proxy_pass_request_headers off`，再显式设置：

- `Authorization`：原值转发，入口先用大小写不敏感的严格 Bearer 格式检查；
- `Content-Type`、`Accept`：保留协议协商；
- `X-Request-Id`：忽略客户端值，使用 Nginx `$request_id`；
- `Host`：重建为内部 Relay host；
- 必需的连接/正文传输 Header：由 Nginx 自身生成。

Cookie、`LANG_SESSION`、`LANG_UID`、`New-Api-User`、`Forwarded`、`X-Forwarded-*`、浏览器来源和任意扩展 Header 均不透传。首批不支持 OpenAI Organization/Project/Beta 等扩展 Header；需要时必须以单个兼容性证据增加，不能恢复整包转发。

缺少或格式错误的 Bearer 在 edge 直接 401，避免无意义触达 New API。网关不解析、哈希或记录 Key，也不按 Key 限流；Key 真伪仍完全由 New API 判定。

### 6. 请求先有界接收，响应禁用缓冲与缓存

共享初始值：

| 策略 | 默认值 | 原因 |
|---|---:|---|
| 请求正文上限 | 10 MiB | 足够承载 MVP 聊天 JSON，又能在未开放文件接口时限制内存/磁盘占用 |
| 建连超时 | 3 秒 | 内部网络不可达应快速失败 |
| 请求发送超时 | 60 秒 | 覆盖大聊天正文与慢客户端，但不无限占用 |
| 响应读取超时 | 600 秒 | 支持长生成；每次上游数据或 SSE 心跳会重置计时 |
| 单地址速率 | 10 请求/秒，burst 20 | 防止明显洪泛，不替代 New API 额度/用户限流 |
| 单地址并发 | 20 | 限制长流占用；后续按监控调整 |

`proxy_request_buffering on` 保留：Nginx 先完整接收并执行 10 MiB 边界，再向上游发送，可保证超大请求不产生部分 Relay 调用。响应侧设置 `proxy_buffering off`、`proxy_cache off`、`gzip off`，并隐藏/覆盖会触发缓冲的 Header。使用 HTTP/1.1 上游连接，不把 hop-by-hop Header 原样传递。

这些值通过 gateway 模板环境变量配置，不进入 Spring YAML；`application.properties` 只负责 Portal 公共协议配置。dev、test、prod 的 Portal `.properties` 显式保持协议状态：dev 可由 Compose 注入本地 OPENAI URL，test 默认不发布真实地址，prod 默认空且必须由部署变量显式启用。

### 7. 取消传播和零重试显式配置

设置 `proxy_ignore_client_abort off`，客户端断开后 Nginx 关闭上游连接。设置 `proxy_next_upstream off` 与单一 Relay upstream，确保连接失败、超时、非幂等 POST 和已开始 SSE 都不会重试；不配置 backup server。

可控 fixture 记录每个请求的唯一计数和连接关闭时间。取消测试在收到首个 SSE chunk 后终止客户端，断言 fixture 在限定时间内检测到断连且计数仍为 1。Nginx 访问日志中的 499 映射为 `client_cancelled`；已发出响应头后的上游中断只能结束流，不能再注入合法 JSON 错误或 `[DONE]`。

### 8. 网关错误使用独立 OpenAI 风格契约

模型 API 不使用 Portal 的 `{requestId,data}` 包装。尚未发送客户端响应时，所有自有/拦截错误统一为：

```json
{
  "requestId": "<edge request id>",
  "error": {
    "code": "GATEWAY_TIMEOUT",
    "message": "模型服务响应超时",
    "type": "gateway_error"
  }
}
```

server 级 `error_page` 将 Nginx 自身 401/404/405/413/429，以及上游 400/401/403/404/422/429/5xx 映射到 internal named locations。上游 4xx 原始正文一律丢弃：400/404/409/422 归 `UPSTREAM_REJECTED`，401 归 `AUTHENTICATION_FAILED`，403 归 `FORBIDDEN`，429 保持 `RATE_LIMITED` 并增加固定可解析 `Retry-After`；连接/网关故障归 `GATEWAY_UNAVAILABLE`，读超时归 `GATEWAY_TIMEOUT`。不返回默认 Nginx HTML。

这种做法会损失 New API/供应商的详细参数错误，但能确保不暴露 `new_api_error` 品牌、渠道和内部消息。备选的正文 JSON 改写需要 Lua/njs 或自研代理并会破坏 SSE，不为 MVP 引入。未来若需要更细错误，可在 New API 提供稳定无品牌代码后逐项放行。

### 9. 成功正文透明，响应 Header 使用已知标识拒绝清单

2xx 普通 JSON 与 SSE 数据不解析、不改写，保留正确 Content-Type 和流式 chunk。edge 总是覆盖 `X-Request-Id`，设置 `Cache-Control: no-store`，移除 ETag、Age、Via、X-Cache、X-Accel-*、New API/One API 已知请求标识和上游 Server 等 Header。标准 Nginx 镜像不能表达任意响应 Header 白名单，因此采用“协议必需 Header透传 + 已知标识显式隐藏 + 自动化快照守卫”的可实现边界。

流已经开始后，Nginx 无法安全替换其中的上游 SSE 错误事件；设计只保证 edge 生成或在响应开始前拦截的错误无品牌，不伪称能重写已发送数据。真实流式验收同时扫描正常数据和典型失败样例，若冻结版在流内输出 New API 标识，则作为上线阻塞而不是在 Nginx 中脆弱替换。

### 10. 日志使用独立格式且不记录 request_uri

增加模型数据面 JSON access log format，只引用 `$time_iso8601`、`$request_id`、`$request_method`、由 exact location 固定设置的 `$model_route`、`$status`、`$request_time`、`$upstream_header_time`、`$upstream_response_time` 和由状态/连接结果映射的 `$gateway_result`。不使用 `$request`、`$request_uri`、`$args`、Header、正文、upstream_addr、模型字段或完整 remote_addr。

unmatched 请求使用固定 `unmatched` 路由；499 为 `client_cancelled`，正常 SSE 为 `stream_completed`，5xx/超时使用稳定类别。Nginx 原始 error log 保持 warning 级且配置测试确认不会打印 Authorization 或正文；验收脚本用专用假 Key、敏感 query、提示词和响应标记扫描所有捕获日志。

### 11. public-config 与网关部署通过同一发布入口收敛

`lang-api` 仍从 `.properties` 读取 `lang.portal.enabled-protocols` 和 `lang.portal.public-urls.openai`。P1-07 将 dev 默认保持可覆盖，由 Compose 显式注入 `PORTAL_ENABLED_PROTOCOLS=OPENAI` 与 `PORTAL_OPENAI_URL=http://api.localhost:${EDGE_HTTP_PORT}/v1`；test 默认空，单测显式覆盖；prod 默认空，发布时必须提供正式 HTTPS `/v1` 地址。

部署预检先渲染 Compose 与 Nginx 模板、执行 `nginx -t`、运行 exact path/Host/Header 检查，再允许把 OPENAI 放入 public-config。应用配置校验增加 P1-07 约束：当前只接受 OPENAI；URL 必须以 `/v1` 结束。跨进程无法由 Portal 启动时直接证明网关健康，因此“已通过网关检查”由同一部署脚本/CI 门禁保证，而不是让 Portal 请求自己的公网地址形成启动环。

### 12. 分层测试把无凭证验证与真实调用分开

建立四层验证：

1. 静态守卫：精确路径、方法、Header whitelist、no buffering/cache/retry、日志变量和管理面拒绝；
2. `nginx -t` 与容器契约：使用 test-only Relay fixture 检查请求 Header、普通 JSON、错误映射、正文上限、限流和 Host 隔离；
3. 流式时序：fixture 间隔发送 SSE，检查首块/逐块、`[DONE]`、取消断连和单次上游计数；
4. 真实验收：显式读取进程环境中的 Base URL、用户 Key 和模型名，经 New API 真实渠道执行 models、非流式、SSE，并分别验证 disabled、expired、exhausted 与 model restriction。

测试 fixture 使用单独 Compose overlay 和 digest 锁定的测试镜像，不加入生产拓扑。真实脚本默认不运行、缺少变量即报告 `SKIPPED/受阻` 且绝不打印值；只有实际证据成功后才能勾选真实验收任务，不能用 fixture 代替。

## Risks / Trade-offs

- [首批仅两个 OpenAI 路径，SDK 能力有限] → 与冻结实测一致；新增路径必须单独验证方法、Header、正文、流式和错误后再改白名单。
- [按客户端 IP 的 edge 限流会影响 NAT 后多个用户] → 默认阈值偏保守且可配置，New API 额度仍是业务主限流；上线前根据压测与入口拓扑调整，P1-12 再设计可信代理/CDN。
- [10 MiB 请求缓冲增加 Nginx 临时资源占用] → 首批无文件接口且有并发限制；监控 413、临时文件与延迟，只有真实聊天负载证明需要时才调整。
- [拦截上游 4xx 会丢失详细参数提示] → 用稳定代码和 requestId 换取无品牌、安全边界；后续只对白名单化上游错误恢复细节。
- [SSE 开始后无法改写错误] → 不注入伪造数据、不重试；用真实流内品牌扫描作为上线门禁，必要时推动上游修复或单独引入流协议代理。
- [客户端取消检测受 TCP 与 fixture 时序影响] → 使用确定性多块 fixture、明确上界和上游请求计数，避免只断言客户端 curl 退出。
- [public-config 与独立 Nginx 进程可能配置不一致] → 使用同一 Compose/env 和部署预检收敛，prod 默认不发布，检查失败时阻止发布而不是运行时互相探活。
- [真实供应商凭证当前不可用] → 网关实现与 fixture 验证可独立完成，但 tasks 中真实非流式/SSE和 Key 限制项保持未完成，直到用户提供受控外部条件。

## Migration Plan

1. 先增加 test-only Relay fixture、Nginx 静态守卫和当前“Relay 关闭”回归测试，确认旧配置对模型路径保持 404。
2. 增加 gateway Dockerfile 与模板变量，把现有用户站点配置迁入默认虚拟主机，并用镜像化 `nginx -t` 保证页面、Portal API 和健康检查行为不回归。
3. 增加独立模型 API 虚拟主机、两条 exact location、Header 重建、资源边界、no retry/buffering/cache、JSON errors 与专用日志；逐项跑 fixture 契约。
4. 更新 Compose、`.env.example`、dev/test/prod `.properties` 和 public-config 校验；用部署预检保证 server name、OPENAI `/v1` URL 与网关能力一致。
5. 运行完整 Maven、前端、Nginx、Compose 网络/端口、普通/SSE/取消/超时/限流和敏感信息扫描，确认模型请求从未经过 Portal API。
6. 运维通过 New API 私网后台配置测试供应商渠道和模型，使用 P1-06 Key 完成真实 non-stream、SSE、disabled、expired、exhausted 与 model restriction 验收；证据只保存脱敏状态、时序和 requestId。
7. 真实验收通过后才在目标环境发布 `OPENAI` Base URL；生产正式域名和 TLS 在 P1-12 配置，不修改前端制品。

回滚时部署上一版 edge 镜像并从 `PORTAL_ENABLED_PROTOCOLS` 移除 `OPENAI`，先停止对外宣传再关闭 Relay 路径。回滚不修改 New API 渠道、Key、额度或调用日志；已创建 Key 继续存在，但通过 Lang API 公共域名调用会恢复为自有 404。若仅网关出现故障，可以单独回滚 edge 镜像，不需要回滚 `lang-api` 或数据库。
