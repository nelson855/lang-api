## Context

见 `proposal.md` 的动机。当前 `portal-api` 基于 Spring Boot 3.5.16、Java 21 和 Servlet MVC，仅包含 Actuator、SPA 回退及一个返回临时 `{status,error,message,path}` 的 Portal API catch-all。P1-02 已确认 New API v0.13.2 可在 `control` 网络通过 `http://new-api:3000` 访问，并形成了脱敏样例；其中认证需要会话 Cookie 与 `New-Api-User` Header，且部分业务失败使用 HTTP 200 + `success=false`。

本设计同时受以下约束：继续使用传统技术分层包结构；New API DTO 不得离开适配边界；浏览器不能访问 New API；模型数据面仍绕过 Java 服务；后端只能使用共享及 `dev`/`test`/`prod` 四个 `.properties` 文件；本变更不能提前实现认证、Key 或支付业务。

## Goals / Non-Goals

**Goals:**

- 冻结后续模块可以直接复用的响应、错误、请求标识、安全和日志设施。
- 形成一个有界、无自动重试且可通过本地 Mock 服务验证的 New API HTTP 适配底座。
- 让公开配置完全由 Lang API 所有，并能随运行环境变化而不重建前端。
- 用自动化测试证明未知 API、超时、上游异常、敏感信息和包依赖边界。

**Non-Goals:**

- 不实现注册、登录、刷新、退出或实际会话保存；Cookie 策略只建立集中转换组件和测试入口。
- 不实现 Key、模型、日志、余额、支付和法律内容适配。
- 不开放 `/v1`、`/v1beta` 等模型网关路径，也不让 Portal API 代理模型流量。
- 不从 New API `/api/status` 派生品牌或公开配置，不引入 Lang API 数据库、缓存或分布式追踪系统。
- 不在此阶段实现业务级重试、熔断、限流或生产监控告警。

## Decisions

### 1. 使用不可变响应对象和单一错误目录

在 `base.response` 中建立泛型成功包装、分页数据和失败包装，在 `base.exception` 中建立 `PortalErrorCode`、安全的业务异常和全局异常处理器。Controller 只返回 Lang API response DTO；全局处理器统一处理 Bean Validation、JSON 解析、方法不匹配、正文过大、认证/授权、上游和未知异常。

错误码与 HTTP 状态以 `portal-api-contract` spec 的表为唯一基线。面向客户端的消息由错误码或显式安全消息产生，不使用 `exception.getMessage()`；详细异常只在日志边界记录类别。现有 `PortalApiNotFoundHandler` 改为使用同一失败包装，不能保留第二套错误结构。

没有采用 Spring Boot `ProblemDetail`，因为项目已经冻结 `{requestId,error}` 契约；也不把字段级校验错误全部回显，避免一次性暴露过多内部字段和校验实现。公开字段可以返回首个安全校验提示，测试不得依赖约束类名。

### 2. requestId 由最前置 Servlet Filter 确定

`RequestIdFilter` 使用 `OncePerRequestFilter`，只处理 `/portal/api/**`，按规格校验 `X-Request-Id`，否则生成 `req_` + UUID 无连字符形式。它在进入安全与 MVC 前写入 request attribute 和 MDC，在响应 Header 中回传，并在 `finally` 清理 MDC。响应工厂从 request attribute 取值，因此正常响应、异常处理器和 Security entry point 使用同一来源。

相比完全信任客户端标识，格式和长度限制可防止日志注入；相比总是覆盖，保留合法入口标识方便浏览器、Nginx 与 Portal API 关联。上游适配器只从上下文读取最终值，不接受业务层自行传入另一个 requestId。

### 3. 路由、方法、正文限制分别在最合适的边界执行

- Controller 的显式 `@RequestMapping` 是路径和方法白名单；不存在任意代理 Controller。
- catch-all 只负责未知 `/portal/api/**` 的统一 404，Spring MVC 方法不匹配由全局处理器统一为 405。
- `PortalRequestBodyLimitFilter` 先检查可信的 `Content-Length`，再用有界 `HttpServletRequestWrapper` 统计实际读取字节，默认上限 1 MiB；超过限制抛出专用异常并生成统一 413。
- response DTO 使用显式字段；Jackson 忽略上游新增字段只发生在适配 DTO 反序列化，序列化端不返回 Map 或上游对象。

没有依赖 Servlet 容器的单一全局限制，因为它无法稳定覆盖无 `Content-Length` 的 JSON 请求，也会不必要地影响静态资源。限制只应用于 Portal API；后续模型数据面的正文限制由 Nginx 在 P1-07 单独处理。

### 4. 采用 RestClient + Reactor Netty 传输层

对上层暴露同步的 Spring `RestClient`，底层使用 `ReactorClientHttpRequestFactory` 与专用 Reactor Netty `ConnectionProvider`。这样保持项目既定 RestClient 编程模型，同时具备 Apache HttpClient 方案缺少的独立写入超时：

- 有界池：默认最大连接 50、pending acquire 最大 100、获取超时 1 秒；
- 建连超时：默认 2 秒；
- 读取与响应超时：默认 5 秒；
- 写入超时：Netty `WriteTimeoutHandler`，默认 5 秒；
- 连接空闲/存活时间：默认 30 秒/5 分钟，并定期回收；
- `disableRetry(true)`：传输层对所有操作禁用自动重试。

所有值通过类型安全的 `@ConfigurationProperties` 绑定与启动期校验。应用关闭时显式释放连接池资源。没有选 WebClient 作为业务接口，因为当前 Portal API 是 Spring MVC，同步适配更简单；没有选默认 JDK 客户端，因为它不能同时提供明确的连接池上限、获取时限和独立写入超时。

### 5. 上游适配按“传输—策略—操作”三层收敛

`upstream.newapi` 内部划分为：

```text
upstream/newapi/
├── config/        # Base URL、连接池和超时配置
├── transport/     # RestClient、统一 exchange、传输异常分类
├── policy/        # Header/Cookie 白名单、错误与脱敏策略
├── dto/           # 仅上游请求/响应 DTO
└── operation/     # 后续按能力新增的语义化操作
```

`NewApiExchange` 只接受内部定义的操作描述，不接受绝对 URL；每个操作固定 HTTP 方法、相对路径、是否认证、允许的 Header/响应类型和错误翻译器。Base URL 必须是绝对 HTTP(S) URL、不得包含 user-info、query 或 fragment；相对路径规范化后必须仍在目标 origin 内。

P1-03 只实现传输、策略和一个测试用最小操作夹具，不新增未被业务使用的生产 `/api/status` 调用。后续模块在 `operation` 中增加真实适配时必须复用相同 exchange 和契约基座。包边界由 ArchUnit 测试约束：Controller/Service 不得依赖 `upstream.newapi.dto` 或传输实现，`upstream.newapi` 外不得声明 New API 路径。

### 6. Header 和 Cookie 采用显式重建，不做代理式复制

请求 Header 从空集合开始构造，只加入操作声明需要的 `Accept`、`Content-Type`、当前 `X-Request-Id`；认证操作未来可再加入会话 Cookie 与 `New-Api-User`。禁止从浏览器请求批量复制 Header。响应同样从空集合开始，普通操作不转发任何上游 Header。

会话转换器在 P1-03 定义浏览器 Cookie 名、上游 Cookie 名和属性策略，但不接入尚未存在的登录流程。它解析 `Set-Cookie` 后重新创建浏览器 Cookie，移除 Domain，Path 固定为 `/portal`，使用 `HttpOnly`、`SameSite=Lax`，`Secure` 在 prod 强制为 true；登出时用同一策略生成过期 Cookie。实际 Cookie 名和值来源在 P1-05 依据登录抓包再次确认，不能靠 P1-03 猜测。

没有透传上游 Cookie，因为它可能包含私网 Domain、过宽 Path 或未来新增 Cookie；也不把 Access Token 改存浏览器，第一阶段仍围绕服务端会话适配。

### 7. HTTP 状态与 success=false 使用同一错误翻译链

传输层先区分连接池获取、建连、读写超时、I/O 断连和响应解析错误，再将状态与受限响应摘要交给操作级错误翻译器。New API 的 `{success,message,data}` 仅存在于上游 DTO；即使 HTTP 200，只要 `success=false` 就进入错误翻译。没有明确业务映射时统一为 `UPSTREAM_ERROR`/502，绝不将 `message` 原样对外。

日志只记录固定操作名、上游状态类别、耗时和当前 requestId，不记录 Base URL、请求/响应正文或 Header。后续认证模块可以把已验证的“错误密码”映射为更具体的自有业务码，但不改变基础传输分类。

### 8. public-config 是本地运行配置，不依赖上游可用性

`PublicConfigController` → 具体 `PublicConfigService` → 类型安全 `PublicPortalProperties`，返回：

```json
{
  "requestId": "req_xxx",
  "data": {
    "siteName": "Lang API",
    "apiBaseUrls": [
      {"protocol": "OPENAI", "url": "https://api.example.com/v1"}
    ]
  }
}
```

协议使用内部枚举产生稳定大写标识，顺序固定为 `OPENAI`、`ANTHROPIC`、`GEMINI`；只有同时列入 enabled protocols 且 URL 合法的项才返回。P1-03 默认不启用任何协议，因此地址数组为空；P1-07 验证并开放某个网关协议时再修改对应环境配置。接口设置 `Cache-Control: no-store`。

未采用 New API `/api/status`，因为实测响应包含版本、内部 server address、OAuth 标识、价格倍率和第三方客户端模板等大量不属于 Lang API 契约的字段，而且会让公开页面可用性依赖上游状态。

### 9. Spring Security 使用 URL 粗粒度规则和方法级受保护声明

Security Filter Chain 关闭 form login、HTTP Basic 和默认 HTML 错误页，启用方法安全，并配置：

- 精确放行 `/portal/api/public-config`、`/actuator/health`、`/actuator/info`、页面入口和静态资源；
- 拒绝其他 `/actuator/**`；
- 允许 `/portal/api/**` 进入 MVC，以便未知路径仍返回统一 404；
- 任何实际非公开 Controller 方法必须用统一的受保护注解/方法安全表达式要求认证，架构测试扫描并阻止漏标；
- Security authentication entry point 和 access denied handler 复用统一 JSON 错误工厂；
- 不配置宽泛 CORS，保留同源策略。

CSRF 保持启用；当前只读公开接口按安全方法自然放行。P1-05 增加状态修改接口时必须同时确定 Origin 校验和 CSRF Token 交互，不能在本基础上全局禁用。为确保 `POST /portal/api/public-config` 返回 405，该精确公开只读路径在 CSRF matcher 中排除，实际方法白名单仍由 MVC 判定。

安全 Header 统一配置 CSP `default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`、`X-Content-Type-Options: nosniff`、DENY frame options、`Referrer-Policy: no-referrer` 和收紧的 Permissions Policy。HSTS 只在 prod 且请求经受信代理识别为 HTTPS 时输出；不盲目信任来自公网的 `X-Forwarded-*`。

### 10. 结构化日志以最小字段和“不采集”为首要脱敏策略

`PortalAccessLogFilter` 在请求完成时使用 SLF4J fluent key-value 记录单条事件：`event=portal_access`、时间、requestId、method、匹配路由模板、status、durationMs。上游调用另记 `event=new_api_call`、operation、outcome、durationMs。无法取得路由模板时只使用固定 `<unmatched-portal-route>`，不记录原始 query string。

默认不采集 Header、Cookie、参数或正文；`SensitiveDataRedactor` 仅作为异常与受控诊断字段的第二道保护，按大小写不敏感的敏感键和凭证模式替换为 `[REDACTED]`。生产 Profile 使用 Spring Boot 结构化 JSON 控制台日志，dev 保留易读控制台格式，test 降低级别但可由测试捕获 key-value 事件。

没有记录客户端 IP、User-Agent 或用户 ID，因为 P1-03 的验收不需要这些可能涉及隐私的字段；后续确有安全审计需求时再单独定义保留期与可信代理规则。

### 11. 配置继续使用四个 .properties 文件

`application.properties` 放置共享的 requestId 规则、1 MiB 正文限制、连接池与超时默认值、Cookie 安全策略、公开协议键和安全 Header 策略。环境文件职责如下：

- `application-dev.properties`：New API 默认 `http://new-api:3000`，允许环境变量覆盖；站点名和公开地址可用本地变量覆盖，enabled protocols 默认为空；Cookie `Secure=false`。
- `application-test.properties`：使用不可达的回环默认地址，契约测试以动态属性覆盖为 MockWebServer URL；最小池和短超时保证测试快速；enabled protocols 默认为空。
- `application-prod.properties`：`NEW_API_BASE_URL` 必填且启动时校验；Cookie `Secure=true`；站点名、已启用协议和每个公开 URL 均由外部环境或外部 `.properties` 提供，启用项缺地址时启动失败；开启 JSON 结构化日志。

所有配置前缀使用 `lang.*`，仓库不保存真实域名、Cookie 值或密钥，不创建 `application.yml`/`application.yaml`。Compose 继续只激活 `dev`；P1-12 再确定正式 Secret 注入和代理信任配置。

### 12. 测试分为纯单元、MVC、安全、契约和架构五层

- 单元测试：响应工厂、requestId 校验、URL/配置校验、错误映射、Cookie/Header 策略、正文计数和脱敏。
- MVC 测试：成功包装、校验、404、405、413、Content-Type、requestId Header/Body 和公开配置字段。
- Security 测试：匿名公开、模拟受保护端点 401/403、安全 Header、Actuator 拒绝和无宽泛 CORS。
- MockWebServer 契约测试：方法/路径/Header、HTTP 200 + `success=false`、非 2xx、未知字段、非 JSON、断连、延迟、无重试和资源释放。
- ArchUnit 测试：上游 DTO/传输依赖和受保护 Controller 声明边界。

测试依赖使用 MockWebServer 而不是 WireMock：它更轻量，能直接检查请求次数和模拟 socket 行为，适合本阶段验证写入/读取超时与“只调用一次”。测试夹具优先复用 `docs/new-api/samples/` 的脱敏结构。

## Risks / Trade-offs

- [Servlet MVC 上使用 Reactor Netty 增加一种底层运行模型] → 只在专用 request factory 内使用，业务层仍同步；增加关闭资源和连接泄漏测试。
- [1 MiB 限制未来可能不足以承载某些控制面附件] → 本阶段没有上传接口；需要附件时为明确端点单独设计，不能放宽全部 Portal API。
- [允许 `/portal/api/**` 进入 MVC 依赖方法级安全防止业务接口漏保护] → 提供统一受保护注解，并用 ArchUnit/上下文测试强制所有非公开 Controller 标注；保持未知路径 404 语义。
- [写入超时只能终止本地 socket，不能证明上游未处理请求] → 全局禁用自动重试；未来写操作仍需按业务设计幂等或明确不确定结果提示。
- [Cookie 的实际名称和过期行为尚未接入真实登录] → P1-03 只实现可配置转换策略；P1-05 必须依据 P1-02 原始登录证据完成真实端到端验证。
- [严格 CSP 可能与 P1-04 选定的字体或前端工具冲突] → P1-04 只能按所需来源最小增量调整并补安全测试，不使用 `*` 或长期保留 `unsafe-eval`。
- [公开地址配置错误会阻止生产启动] → 只校验已启用协议；未启用时允许地址为空并返回空数组，避免公布不可用入口。

## Migration Plan

1. 先引入依赖和类型安全配置，保持 public protocols 为空，确认四个 `.properties` Profile 均能按预期绑定。
2. 建立 requestId、响应/错误、正文限制和访问日志 Filter，再将临时 Portal 404 切换到统一错误结构。
3. 接入 Spring Security 及安全 Header，验证现有页面、健康检查、SPA 回退与 JSON 404 不回归。
4. 建立 New API transport/policy/测试基座，使用 MockWebServer 验证超时、无重试和响应裁剪，不接入业务端点。
5. 新增本地 `public-config` Controller 并完成 MVC、安全与环境配置测试。
6. 运行项目正式 Maven `verify`、配置格式检查、敏感信息扫描和 Compose 内部连通性抽查；P1-03 通过后，P1-04 才开始依赖该契约。

回滚时可整体回退 Lang API 制品，不涉及数据迁移。若只回退接口基础，需要同时恢复旧 `PortalApiNotFoundHandler` 测试预期；前端在 P1-04 开始依赖 `public-config` 后，不得单独回退到缺少该接口的版本。
