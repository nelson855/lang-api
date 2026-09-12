# public-portal-config Specification

## Purpose

向同一前端制品提供随部署环境变化的最小公开运行配置，使页面可以展示自有品牌和当前真正开放的模型协议地址，而无需在构建时固化域名或读取 New API 配置。

## Requirements

### Requirement: 公开配置接口
系统 SHALL 提供无需认证的 `GET /portal/api/public-config`。成功响应的 `data` MUST 且只能包含非空 `siteName` 和 `apiBaseUrls`；`apiBaseUrls` 是由零个或多个 `{protocol, url}` 组成的数组，`protocol` 使用稳定的大写标识，且只列出当前环境已启用并已配置的公开模型协议地址。

#### Scenario: 返回当前环境公开配置
- **WHEN** 客户端请求公开配置且当前环境启用了一个或多个模型协议地址
- **THEN** 系统通过统一成功包装返回站点名称和已启用地址，数组不包含未启用或空地址项

#### Scenario: 模型网关尚未开放
- **WHEN** 当前环境尚未启用任何公开模型协议地址
- **THEN** 系统返回成功响应、站点名称和空的 `apiBaseUrls` 数组，不伪造可用地址

#### Scenario: 使用错误方法
- **WHEN** 客户端使用 GET 以外的方法请求 `/portal/api/public-config`
- **THEN** 系统返回统一 JSON `METHOD_NOT_ALLOWED`/405

### Requirement: 公开配置最小披露
公开配置 SHALL 只来源于 Lang API 自有 `.properties` 配置，不请求 New API `/api/status`，也不得返回 New API 地址、版本、系统名称、功能开关、OAuth 标识、价格倍率、内部域名、密钥或其他未声明字段。响应 SHALL 使用 `Cache-Control: no-store`，使环境切换无需重建前端且不会长期使用旧地址。

#### Scenario: New API 状态包含额外公开字段
- **WHEN** New API `/api/status` 包含系统名、版本、价格、登录方式或第三方客户端配置
- **THEN** `/portal/api/public-config` 的响应不受这些字段影响且不包含其中任何内容

#### Scenario: 切换部署配置
- **WHEN** 使用同一前端制品启动另一个 Profile 并提供不同站点名称或已启用公开地址
- **THEN** 接口返回该环境配置，客户端无需重新构建前端

#### Scenario: 配置包含非法地址
- **WHEN** 启用的公开地址为空、不是绝对 HTTP(S) URL、包含用户信息、查询串或片段
- **THEN** 应用启动失败并指出公开配置项非法，但不输出可能存在的敏感值
