## Why

LANG-P1-02 已冻结并实测 New API v0.13.2，但当前后端仍只有工程基线和临时 JSON 404，后续前端、认证与 API Key 模块若直接使用上游路径、响应和错误，将形成难以升级且容易泄露内部信息的耦合。现在需要先建立 Lang API 自有的 Portal API 契约、上游适配边界、安全和可观测性基线，为 LANG-P1-04 及后续业务模块提供稳定入口。

## What Changes

- 为 `/portal/api/**` 定义统一成功、失败和分页响应，统一生成、校验、回传并传播 `requestId`。
- 建立参数校验、JSON 404/405、正文大小限制和全局异常映射，使用稳定的 Lang API 错误码，禁止透传 New API 原始错误与内部地址。
- 新增 `upstream.newapi` 适配边界，将 New API 路径、DTO、Cookie、Header 和错误转换集中管理，并建立显式路径、方法、请求头和响应字段白名单。
- 为 New API 客户端建立连接池、连接/读取/写入超时与禁用自动重试的默认策略，并以 MockWebServer 建立可复用契约测试基座。
- 建立敏感信息脱敏、结构化访问日志和请求链路关联，禁止记录密码、Cookie、Access Token、完整 API Key 与支付签名。
- 新增 `GET /portal/api/public-config`，只返回 Lang API 明确定义的站点名称和公开 Base URL，不暴露 New API `/api/status` 的其他字段。
- 引入 Spring Security 基础规则和安全响应头；静态页面、健康检查和公开配置按白名单放行，未声明的 Portal API 默认拒绝或返回统一 JSON 错误。
- 后端配置继续只使用 `.properties`，共享项进入 `application.properties`，地址等环境差异分别进入 `application-dev.properties`、`application-test.properties`、`application-prod.properties`。

## Capabilities

### New Capabilities

- `portal-api-contract`: Portal API 的统一响应、请求标识、校验、异常、路由白名单与正文限制契约。
- `new-api-adapter`: New API 客户端隔离、传输策略、Cookie/Header/错误转换、字段白名单和契约测试边界。
- `public-portal-config`: 对前端公开的站点名称与模型协议 Base URL 接口契约。
- `portal-api-security-observability`: Spring Security 基线、安全响应头、结构化访问日志和敏感信息保护要求。

### Modified Capabilities

- `integrated-application-build`: 将现有 Portal API 未匹配路由的临时 JSON 404 升级为统一错误响应，并保持 SPA 回退与 API 路由隔离。

## Impact

- 主要影响 `portal-api` 的依赖、配置、基础响应与异常类型、Filter/Security 配置、公开配置 Controller/Service、`upstream.newapi` 适配代码和自动化测试。
- `portal-api/pom.xml` 将增加校验、安全、HTTP 客户端传输层和 MockWebServer 测试依赖；不新增数据库或消息队列。
- 对外新增 `GET /portal/api/public-config`，并替换现有未匹配 Portal API 的临时错误结构；前端此后只依赖 Lang API DTO。
- New API 仍是业务数据权威，Lang API 仅通过 Compose `control` 网络访问 `new-api:3000`，不访问其数据库，也不提供任意代理。
