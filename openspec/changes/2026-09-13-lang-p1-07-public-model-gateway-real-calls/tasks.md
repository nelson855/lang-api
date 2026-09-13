## 1. 建立网关测试基线

- [x] 1.1 增加仅供测试使用、版本固定的 Relay fixture，支持普通 JSON、分段延时 SSE、指定状态/延时/断连、请求计数、上游取消观测和安全 Header 回显
- [x] 1.2 增加独立 Compose 测试 overlay，把 fixture 接入 `relay` 网络且不改变生产 Compose 拓扑或发布宿主机端口
- [x] 1.3 增加旧配置回归基线，证明模型路径当前保持关闭，并覆盖页面、Portal API、健康检查及 New API 管理面不公开的既有行为

## 2. 网关镜像与虚拟主机

- [x] 2.1 新增 `gateway/Dockerfile`，基于 digest 固定的 Nginx 镜像烘焙配置模板，并声明项目版本和 Git revision 的 OCI label
- [x] 2.2 将 `gateway/nginx.conf` 改为受限环境变量替换模板，配置 `PORTAL_SERVER_NAME` 与 `MODEL_API_SERVER_NAME` 两个互斥虚拟主机
- [x] 2.3 在用户站点虚拟主机中迁移并验证现有页面、`/portal/api/*`、健康检查和模型路径拒绝行为
- [x] 2.4 在模型 API 虚拟主机中默认拒绝页面、Portal API、`/api/*`、`/setup/*`、未声明协议及所有兜底路径
- [x] 2.5 增加镜像配置的 `nginx -t`、受限 envsubst 变量和 OCI label 自动化检查

## 3. 精确路由与请求边界

- [x] 3.1 为 `POST /v1/chat/completions` 和 `GET /v1/models` 增加 exact location，并直接代理到 `new-api:3000`
- [x] 3.2 为白名单路径增加方法门禁，确保错误方法返回自有 405，尾随斜杠、大小写变体、子路径及 OPTIONS 返回自有拒绝且不触达 Relay
- [x] 3.3 增加严格 Bearer 格式检查，确保缺失、空值或格式错误的 Authorization 在 edge 返回自有 401
- [x] 3.4 关闭默认请求 Header 转发，只重建 Authorization、Content-Type、Accept、内部 Host、连接/正文传输 Header 和网关生成的 `X-Request-Id`
- [x] 3.5 确保 Cookie、Portal 会话、客户端 request id、`New-Api-User`、`Forwarded`、`X-Forwarded-*` 及未声明 Header 不进入 Relay

## 4. 流式传输与资源保护

- [x] 4.1 配置 10 MiB 请求正文上限和请求缓冲，验证超限请求在到达 Relay 前返回自有 413
- [x] 4.2 配置 3 秒建连、60 秒发送和 600 秒读取超时，并让数值可通过受限网关模板变量调整
- [x] 4.3 配置单地址 10 请求/秒、burst 20 和并发 20 的边缘保护，超限时返回自有 429 与可解析 `Retry-After`
- [x] 4.4 对模型响应禁用代理缓冲、缓存和压缩，使用 HTTP/1.1 上游连接并保留普通 JSON 与 SSE 协议正文
- [x] 4.5 配置客户端断开向上游传播、单一 upstream 和 `proxy_next_upstream off`，保证一次客户端请求最多触发一次 Relay 调用

## 5. 错误、响应 Header 与日志

- [x] 5.1 为 401、404、405、413、429、可拦截上游 4xx、不可用和超时建立 `{requestId,error:{code,message,type}}` UTF-8 JSON 错误映射
- [x] 5.2 丢弃被拦截的上游原始错误正文和默认 Nginx HTML，验证错误不含 New API/One API 品牌、私网地址、渠道或凭证
- [x] 5.3 对已开始的 SSE 上游失败只终止连接并记录结果，不注入 JSON、伪造 `[DONE]` 或重试请求
- [x] 5.4 覆盖响应 `X-Request-Id` 与 `Cache-Control: no-store`，移除已知品牌、内部 request id、Server、缓存和加速调试 Header，且不启用通配 CORS
- [x] 5.5 增加模型数据面 JSON access log，仅记录规范化路由、requestId、方法、状态、耗时和安全结果类别，并区分正常流、取消、超时与失败
- [x] 5.6 增加日志敏感信息守卫，使用假 Key、Cookie、查询串、提示词和响应标记证明访问日志及 error log 不记录凭证、正文、模型名、完整地址或内部 upstream

## 6. Compose 与环境配置

- [x] 6.1 更新 `deploy/compose.yml` 使用自有 edge 镜像和受限模板变量，保持只有 edge 发布宿主机端口，且 edge 只能经 `relay` 网络访问 New API
- [x] 6.2 更新 `deploy/.env.example`，声明非敏感的两个 server name、edge 镜像版本信息、公开 OpenAI `/v1` URL及正文/超时/限流参数，不加入真实域名或 Key
- [x] 6.3 更新 `portal-api/src/main/resources/application.properties`，保留共享的 Portal 公共协议配置入口并增加 LANG-P1-07 所需约束绑定
- [x] 6.4 更新 `portal-api/src/main/resources/application-dev.properties`，允许 Compose 显式注入本地 `OPENAI` 与 `http://api.localhost:${EDGE_HTTP_PORT}/v1`
- [x] 6.5 更新 `portal-api/src/main/resources/application-test.properties`，默认不发布真实模型地址，并允许测试用例显式覆盖
- [x] 6.6 更新 `portal-api/src/main/resources/application-prod.properties`，默认保持协议关闭并要求部署显式提供正式 HTTPS `/v1` 地址
- [x] 6.7 增加 public-config 配置校验与测试，当前只接受 `OPENAI`，且 URL 必须是无用户信息、查询和片段并以 `/v1` 结束的绝对 HTTP(S) 地址
- [x] 6.8 增加部署预检，校验两个 server name 非空且不同、公开 URL 与模型主机一致、未引用 New API 私网地址/运维端口，并在网关契约未通过时阻止发布 `OPENAI`

## 7. 自动化网关契约验证

- [x] 7.1 使用 fixture 验证两条精确路径的正常调用、所有错误方法/路径/管理面拒绝和用户站点 Host 隔离
- [x] 7.2 使用 fixture 验证允许 Header、伪造身份/转发 Header 清除、网关 requestId 一致性及一次请求只触发一次 upstream
- [x] 7.3 使用 fixture 验证非流式 JSON 状态/正文透传、响应不缓存且已知品牌与内部 Header 被移除
- [x] 7.4 使用分段 fixture 验证 SSE 首块和后续块在响应结束前到达、顺序不变且 `[DONE]` 不被改写
- [x] 7.5 在收到首个 SSE chunk 后取消客户端，验证 fixture 在时限内观察到断连、网关释放连接、日志为 `client_cancelled` 且无第二次 upstream
- [x] 7.6 验证建连失败、读取超时、上游断连、上游 4xx/5xx、413、速率和并发 429 的状态、稳定错误码及无品牌正文
- [x] 7.7 运行 Maven 测试、前端既有检查、Nginx 配置检查、Compose 配置/网络/端口断言和敏感信息扫描，记录命令与脱敏结果

## 8. 真实供应商验收与文档收口

- [x] 8.1 增加显式读取 `MODEL_API_BASE_URL`、`MODEL_API_KEY` 与 `MODEL_NAME` 的真实验收脚本，禁止 shell xtrace、变量值输出和凭证落盘，缺少条件时报告受阻而非伪造通过
- [ ] 8.2 通过公开 Base URL 和 P1-06 Key 对真实供应商执行 `GET /v1/models` 与非流式聊天，记录脱敏状态、requestId 和时序证据
- [ ] 8.3 通过公开 Base URL 对真实供应商执行 SSE 聊天，验证首块、逐块、正常结束、无缓存及流中无 New API/One API 品牌
- [ ] 8.4 分别用禁用、过期、额度不足和模型受限的 P1-06 Key 发起真实调用，证明限制由 New API 执行且 edge 返回安全失败
- [x] 8.5 更新 New API 兼容性矩阵和阶段验证记录，明确已验证路径、Header、普通/SSE/取消/错误边界以及脱敏真实调用证据
- [ ] 8.6 仅在真实供应商验收全部通过后更新 `docs/06_第一阶段MVP开发与子需求拆分.md` 的 LANG-P1-07 状态；外部渠道、模型或额度未就绪时保持本组未完成并记录阻塞条件
