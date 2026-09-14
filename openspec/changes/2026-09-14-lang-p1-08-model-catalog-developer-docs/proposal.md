## Why

LANG-P1-07 已开放最小 OpenAI 兼容调用链路，但公开模型广场和开发文档仍是占位页，用户无法判断当前有哪些模型、基础价格如何，也无法从运行时配置得到可直接使用的调用示例。LANG-P1-08 需要沿用 New API 的公开定价口径，以最小适配完成“选择模型—复制示例—真实调用”的用户闭环。

## What Changes

- 新增无需登录的 `GET /portal/api/models`，固定适配 New API v0.13.2 的 `/api/pricing`，返回 Lang API 自有模型目录 DTO，不暴露上游包装、分组倍率、渠道或内部字段。
- 以 New API 当前公开定价结果作为第一阶段模型范围和可用状态口径；不结合登录用户、API Key 或 `/api/models` 计算个性化可调用集合。
- 统一模型 ID、显示名称、厂商、基础输入/输出价格、计价单位和可用状态；上游无法可靠提供的增强元数据返回 `null` 或不展示，禁止按模型名称推断。
- 对定价结果增加短期进程内缓存；缓存未命中且上游失败时使用稳定 Portal 错误，不长期展示可能过期的价格。
- 将 `/models` 从占位页替换为真实模型列表，提供客户端搜索、厂商筛选、加载、空结果和错误状态。
- 将 `/docs` 从占位页替换为自有静态开发文档，说明 Bearer Key、运行时 Base URL、首批开放路径、最小非流式请求和 SSE 流式请求。
- 至少提供可复制的 cURL 与 OpenAI SDK 示例；模型广场可将所选模型带入文档示例，Key 始终使用安全占位符。
- 所有示例从 `/portal/api/public-config` 读取当前启用的 OpenAI Base URL，不把环境域名写入前端构建产物；未启用协议时禁用复制并显示明确状态。
- 增加模型价格映射、缓存、页面筛选、复制交互、运行时 URL 替换、响应式布局与可访问性测试，以及使用真实模型和请求日志进行的价格口径抽样验收。

## Capabilities

### New Capabilities

- `model-catalog`: 定义公共模型目录、New API 定价适配、稳定字段、价格与可用状态口径、缓存和异常行为。
- `developer-documentation`: 定义运行时 Base URL、鉴权说明、可复制的 cURL/SDK 非流式与流式示例，以及模型选择联动行为。

### Modified Capabilities

- `frontend-application-shell`: 将模型广场和开发文档的阶段占位行为替换为 LANG-P1-08 的真实公开页面，并补充页面间模型选择导航。

## Impact

- Portal API：新增模型目录 Controller、应用服务、公开 DTO，以及 `upstream.newapi` 下的 pricing 客户端、DTO、映射和短期缓存；沿用统一响应、错误和请求 ID 契约。
- 前端：新增模型目录 API schema/query、模型广场功能组件、开发文档内容与代码块复制交互，修改 `/models`、`/docs` 页面和中英文文案。
- 现有能力：复用 `public-portal-config` 提供的 `OPENAI` Base URL 和 P1-07 的 `/v1/models`、`/v1/chat/completions` 契约；不修改模型数据面、API Key 状态、计费或路由逻辑。
- 依赖与配置：使用项目既定 Caffeine 本地缓存方案；后端配置继续放在分环境 `.properties` 文件中，不增加数据库表或模型运营后台。
- 验收条件：需要目标环境的 `/api/pricing` 返回至少一个真实模型，并提供一个可调用模型、测试 Key 和可核对扣费的请求日志；缺少这些条件时自动化契约可完成，但真实价格抽样保持未完成。
