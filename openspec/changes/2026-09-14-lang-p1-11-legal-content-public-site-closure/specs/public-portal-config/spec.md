# public-portal-config Specification Delta

## MODIFIED Requirements

### Requirement: 公开配置接口
系统 SHALL 提供无需认证的 `GET /portal/api/public-config`。成功响应的 `data` MUST 且只能包含非空 `siteName`、`publicationMode`、`siteUrl`、`supportUrl`、`supportedRegions`、`enabledLocales` 和 `apiBaseUrls`。`publicationMode` 只能为 `PREVIEW` 或 `PUBLIC`；URL 和数组可以在 `PREVIEW` 中为空，但 `PUBLIC` 必须满足正式发布校验；`apiBaseUrls` 是由零个或多个 `{protocol, url}` 组成的数组，`protocol` 使用稳定的大写标识，且只列出当前环境已启用并已配置的公开模型协议地址。

#### Scenario: 返回当前环境公开配置
- **WHEN** 客户端请求公开配置且当前环境启用了一个或多个模型协议地址
- **THEN** 系统通过统一成功包装返回发布模式、站点与支持信息、地区、语言和已启用地址，数组不包含未启用或空地址项

#### Scenario: 预览环境尚未配置公开信息
- **WHEN** 当前环境为 `PREVIEW` 且站点 URL、支持入口、地区或模型协议尚未配置
- **THEN** 系统返回成功响应和明确空值或空数组，前端展示未发布状态而不伪造内容

#### Scenario: 模型网关尚未开放
- **WHEN** 当前环境尚未启用任何公开模型协议地址
- **THEN** 系统返回成功响应和空的 `apiBaseUrls` 数组，不伪造可用地址

#### Scenario: 使用错误方法
- **WHEN** 客户端使用 GET 以外的方法请求 `/portal/api/public-config`
- **THEN** 系统返回统一 JSON `METHOD_NOT_ALLOWED`/405

### Requirement: 公开配置最小披露
公开配置 SHALL 只来源于 Lang API 自有 `.properties` 配置，不请求 New API `/api/status`，也不得返回 New API 地址、版本、系统名称、功能开关、OAuth 标识、价格倍率、内部域名、密钥、法律正文或其他未声明字段。响应 SHALL 使用 `Cache-Control: no-store`，使环境切换无需重建前端且不会长期使用旧配置。

#### Scenario: New API 状态包含额外公开字段
- **WHEN** New API `/api/status` 包含系统名、版本、价格、登录方式或第三方客户端配置
- **THEN** `/portal/api/public-config` 的响应不受这些字段影响且不包含其中任何内容

#### Scenario: 切换部署配置
- **WHEN** 使用同一前端制品启动另一个 Profile 并提供不同站点名称、发布信息或已启用公开地址
- **THEN** 接口返回该环境配置，客户端无需重新构建前端

#### Scenario: 配置包含非法地址
- **WHEN** 任一非空公开 URL 不是绝对 HTTP(S) URL，或包含用户信息、查询串或片段
- **THEN** 应用启动失败并指出公开配置项非法，但不输出可能存在的敏感值

## ADDED Requirements

### Requirement: 正式发布配置完整且来源唯一
系统 SHALL 通过 `application.properties` 及 dev、test、prod profile 绑定发布模式、正式站点 URL、支持入口、ISO 3166-1 alpha-2 地区代码和启用语言。`PREVIEW` 是安全默认值；`PUBLIC` MUST 提供 HTTPS 正式站点 URL、HTTPS 或 `mailto:` 支持入口、至少一个合法地区，并且在当前单一法律源模型下只启用与 `legal.source-locale` 相同的一种语言。站点 URL 的 origin MUST 与对外用户站点一致。

#### Scenario: 正式发布配置完整
- **WHEN** `prod` 显式选择 `PUBLIC` 并提供全部合法字段
- **THEN** 应用启动并向前端公开经规范化的配置值

#### Scenario: 正式发布缺少必需字段
- **WHEN** `PUBLIC` 缺少站点 URL、支持入口、地区或法律源语言匹配
- **THEN** 应用启动失败并给出配置键级别的安全错误

#### Scenario: 未显式选择正式发布
- **WHEN** 任一环境没有配置发布模式
- **THEN** 系统使用 `PREVIEW`，不会因其他字段碰巧存在而推断为正式发布
