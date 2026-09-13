## Why

LANG-P1-05 已建立可信的用户会话，但控制台尚不能管理调用模型所必需的 API Key，核心商业链路因此停在登录之后。LANG-P1-06 需要在不复制 New API 数据的前提下，将冻结版 New API v0.13.2 的令牌能力收敛为安全、稳定、可完整操作的 Lang API Key 管理契约，为 LANG-P1-07 的真实模型调用提供凭证基础。

## What Changes

- 新增受保护的 `/portal/api/api-keys` 接口族，提供分页列表、名称搜索、状态筛选、创建、详情、编辑、启停、删除和明文 reveal，统一使用 Lang API DTO、错误码与响应包装。
- 将 New API v0.13.2 的 `/api/token/*` 操作封装为语义化适配器；受保护调用复用已校验会话，只读取当前用户范围的数据，并禁止写操作自动重试。
- 冻结 Key 字段语义：列表始终返回掩码；额度使用带单位的结构表达；过期时间使用 ISO 8601 或永不过期；模型限制与 IP 限制由后端校验和转换，不让前端复制上游格式。
- 创建成功后刷新列表并提供一次明确的“显示并复制”提示；新建和既有 Key 均通过显式确认的 reveal 操作按需取回明文，明文不进入查询缓存、浏览器持久化、URL、日志、监控、错误或分析事件。
- 新增控制台 API Key 页面及导航，覆盖搜索、筛选、分页、加载、空状态、错误、创建、编辑、启停、删除、复制和高级限制字段，并提供移动端可用交互。
- 为所有 Key 写操作与 reveal 强制复用现有 CSRF/Origin 防护，补充对象归属、上游错误翻译、敏感值脱敏、并发 mutation 和缓存失效测试。

## Capabilities

### New Capabilities

- `api-key-management`: 定义用户 API Key 的列表查询、字段语义、创建、详情、编辑、启停、删除、明文 reveal、权限隔离和安全处理。

### Modified Capabilities

- `new-api-adapter`: 增加冻结版 New API Token 列表、读取、创建、编辑、启停、删除和明文取回的语义化适配及契约约束。
- `portal-api-contract`: 增加 API Key 资源冲突、无效限制、明文取回失败等场景所需的稳定错误语义，并明确 Key 接口路径与分页查询契约。
- `portal-api-security-observability`: 将所有 API Key 接口纳入统一会话边界，将写操作和 reveal 纳入 CSRF/Origin 防护，并规定 Key 安全事件的最小审计字段。
- `frontend-api-integration`: 增加 API Key 运行时 DTO、查询/mutation、敏感明文的非缓存处理、用户作用域缓存失效与会话失效行为。
- `frontend-application-shell`: 增加受保护的 API Key 路由、控制台导航和完整页面交互，替换相应阶段占位入口。

## Impact

- 后端：`portal-api` 新增 `apikey` 业务边界、request/response DTO、Controller/Service，以及 `upstream.newapi` 下的 Token 客户端与上游 DTO；扩展错误、安全、日志和架构测试。
- 前端：`frontend` 新增 API Key 数据层、页面、表单/确认/reveal 交互、中英文文案及组件和浏览器测试，复用现有表格、分页、对话框、通知和认证缓存。
- 外部系统：继续使用冻结版 `calciumion/new-api:v0.13.2` 作为唯一 Key 数据权威；不新增 Portal 数据库、第三方依赖或公网 New API 路径。
- 后续阶段：LANG-P1-07 可直接使用本阶段创建的 Key 验证公开模型网关；本阶段本身不开放或代理 `/v1/*` 模型调用。
