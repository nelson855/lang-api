## Why

前十个子需求已形成可运行的用户与模型调用闭环，但公开站点仍缺少可发布的法律页面、服务地区、联系入口和完整品牌表达；冻结版 New API 的协议与隐私内容当前又都是空字符串。LANG-P1-11 需要在不伪造法律文本的前提下完成公开内容机制，并用明确的预览/公开门禁阻止占位内容被误当成正式上线版本。

## What Changes

- 新增匿名 `GET /portal/api/legal/terms` 与 `GET /portal/api/legal/privacy`，固定适配 New API v0.13.2 的 `/api/user-agreement` 和 `/api/privacy-policy`。
- 将上游法律正文按显式配置的源格式解析，在服务端完成严格 HTML 白名单净化、大小限制和短期缓存，只向浏览器返回安全的标题、内容 HTML 和正文语言。
- 上游法律正文为空、超限或净化后无有效内容时不展示占位条款，返回明确的未发布状态；不把 New API 原始消息、默认品牌或危险链接带到页面。
- 扩展 `/portal/api/public-config`，公开经校验的站点 URL、支持联系入口、支持地区、启用语言和 `PREVIEW/PUBLIC` 发布模式；生产 `PUBLIC` 模式必须提供完整运行时配置。
- 在 `PREVIEW` 模式或任一必需法律文档不可用时，后端有效注册策略保持关闭，注册页仍提供法律链接和未就绪说明，不能绕过前端直接注册。
- 新增 `/terms`、`/privacy`、`/regions` 公开路由；完善首页、公共导航、页脚、联系入口、注册前法律入口、移动导航和 404 页面。
- 完成中英文基础产品文案，但当前单一上游法律正文只允许公开其声明的源语言；未具备完整且经确认法律正文的语言不出现在语言切换器中。
- 将现有自有 `L/` 几何标识作为第一阶段 MVP Logo，补齐同源 favicon、可访问名称和替换边界；站点名称继续来自运行时配置。
- 增加路由级标题、description、canonical 和 robots 管理：`PREVIEW` 与错误/404 页面必须 `noindex`，`PUBLIC` 的有效公开页面才允许索引。
- 扫描页面、构建产物、公开接口、错误文案和开发文档，清除 QinghuaAPI、New API 私网信息及默认品牌残留；当前没有邮件发送能力，不新增虚假邮件模板。
- 明确第一阶段不保存法律版本、用户同意记录或历史正文，也不把开发者条款作为占位内容上线。

## Capabilities

### New Capabilities

- `legal-content`: 定义协议与隐私正文的上游来源、安全净化、缓存、未发布状态、公开页面和无版本同意边界。

### Modified Capabilities

- `public-portal-config`: 增加正式站点 URL、支持联系入口、支持地区、启用语言和发布模式，并强化生产公开配置校验。
- `new-api-adapter`: 增加冻结版用户协议与隐私政策的固定匿名只读操作和最小正文转换。
- `frontend-application-shell`: 增加法律与服务地区路由，收口首页、公共导航、页脚、404、品牌资产和页面 Meta 行为。
- `frontend-localization`: 由运行时启用语言控制语言切换器，只开放内容完整且经确认可公开的语言。
- `user-authentication`: 将有效注册开放状态与 `PUBLIC` 模式及必需法律正文可用性关联，并在注册前提供法律入口。

## Impact

- Portal API：新增 legal 适配、服务、Controller、正文净化与缓存；扩展 public-config DTO、配置绑定、生产校验及认证选项的有效注册判断。
- 前端：新增 legal/regions API schema、公开页面与路由，扩展 Home、PublicLayout、Register、NotFound、语言切换、页面 chrome 和品牌资产。
- 配置：在 `application.properties` 及 dev/test/prod profile 中增加 publication mode、site URL、support URL、region codes、enabled locales、legal source locale/format、正文上限和缓存 TTL；继续只使用 `.properties`。
- 依赖：后端引入受控 Markdown→HTML 与 HTML sanitizer 依赖；不引入 CMS、数据库或远程前端资源。
- 内容与发布：正式法律正文仍由具备资质的人员确认并写入 New API 私网配置；当前空样本只能用于验证未发布状态，不能满足 `PUBLIC` 验收。
