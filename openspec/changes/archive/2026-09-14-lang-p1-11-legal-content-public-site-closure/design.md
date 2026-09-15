## Context

See `proposal.md` - Why。当前 Portal 已有统一响应、New API 适配边界、认证策略、public-config 和 React 三类布局，但 public-config 只有站点名与模型协议地址，公开页脚没有法律入口，首页与 404 仍是阶段性最小实现。冻结样例中的 `/api/user-agreement` 与 `/api/privacy-policy` 均返回空字符串，因此设计必须把“机制已实现”和“内容可正式发布”分开。

New API v0.13.2 只提供每类一份全局正文，没有 locale、版本或发布时间字段。Portal 不直接读上游数据库，也不能从正文可靠推断语言或格式。后端配置继续只使用 `.properties`，并显式区分共享、dev、test、prod 四组配置。

## Goals / Non-Goals

**Goals:**

- 形成从固定上游接口、服务端安全转换、Portal API 到公开页面的最小法律内容链路。
- 用静态发布配置与动态法律就绪状态共同控制公开注册，默认失败关闭。
- 让同一前端制品根据运行时配置完成公开导航、地区、联系、语言和 Meta 表达。
- 给出可自动验证的安全白名单、缓存、配置和品牌清理边界。

**Non-Goals:**

- 不判断法律文本是否足以满足特定司法辖区要求；正式文本仍需具备资质的人员确认。
- 不建设 CMS、法律版本库、同意审计、历史正文、多语言法律内容存储或开发者条款系统。
- 不新增邮件发送、在线客服、支付地区推导或搜索引擎站点地图能力。
- 不重做现有视觉系统；只补齐现有 `L/` 标识的 favicon、可访问名称和集中替换入口。

## Decisions

### 1. 用 `PREVIEW` / `PUBLIC` 显式发布模式，而不是从环境或字段完整度推断

`lang.publication.mode` 默认 `PREVIEW`。静态配置在应用启动时校验：`PUBLIC` 必须具有合法 `site-url`、`support-url`、地区和单一启用语言；动态法律可用性不作为启动条件，因为上游短暂故障不应让整个 Portal 无法启动。前端在 `PREVIEW` 下统一 `noindex` 并准确展示未发布状态。

备选方案是生产 Profile 自动等于正式发布，或配置不完整时才降级预览。两者都可能因误配置意外公开内容，因此不采用。另一个备选是 `PUBLIC` 时上游法律正文不可用便启动失败，但这会把运行时依赖变成部署可用性的单点，改为仅关闭注册和法律正文。

### 2. 扩展现有 public-config，不另建公开站点配置接口

`PublicConfigResponse` 增加：

- `publicationMode`: `PREVIEW | PUBLIC`
- `siteUrl`: 可空规范化绝对 URL
- `supportUrl`: 可空的 HTTPS 或 `mailto:` URL
- `supportedRegions`: 去重且稳定排序的 ISO 3166-1 alpha-2 代码
- `enabledLocales`: `zh-CN | en-US` 的非空有序子集

保留 `siteName` 与 `apiBaseUrls`，继续返回 `Cache-Control: no-store`。前端只消费这个 DTO，不读取构建变量。URL 禁止 user-info、query 和 fragment；`PUBLIC` 的 `siteUrl` 必须 HTTPS，canonical 使用其 origin 与路由 pathname 组合。

属性集中绑定到现有公开配置类的嵌套段，建议键为 `lang.publication.*`、`lang.public.*` 和 `lang.legal.*`。共享边界、枚举和限制放入 `application.properties`；`application-dev.properties`、`application-test.properties` 明确为 `PREVIEW` 并使用本地值；`application-prod.properties` 以环境变量提供正式值且默认仍为 `PREVIEW`。所有 Profile 都显式声明语言、正文格式、大小和缓存 TTL，不引入 YAML。

备选方案是为 contact、regions、SEO 分别建接口，字段少且更新生命周期一致，增加接口只会扩大加载与错误状态，因此不采用。

### 3. 法律内容经固定匿名适配操作进入独立领域服务

在 `upstream.newapi` 增加 `getUserAgreement()` 与 `getPrivacyPolicy()`，只解析成功包装中的字符串 `data`。适配器固定路径，不携带会话，不接受 URL、locale 或格式参数。`LegalContentService` 负责类型到操作、标题、配置语言、安全转换、大小检查和缓存；Controller 只暴露 `TERMS` / `PRIVACY` 两个固定端点。

空白、过大、净化为空统一视为 `NOT_FOUND`，上游通信与非法响应沿用稳定 `UPSTREAM_*` 错误。前端可以区分未发布和暂时失败，但两者都不能展示替代正文。日志只记录法律类型、失败分类和 requestId，不记录正文。

备选方案是前端直接请求 New API，无法维持私网隔离、响应裁剪和品牌边界；在 Portal 写死正文又要求每次内容更新重建应用，均不采用。

### 4. Markdown 与 HTML 统一进入服务端 HTML sanitizer

引入 `commonmark-java` 完成 Markdown 到 HTML 的确定性转换，引入 OWASP Java HTML Sanitizer 执行最终白名单。`MARKDOWN` 和 `HTML` 都必须经过同一 sanitizer；禁用远程图片、style、script、iframe、object、embed、form 和事件属性，只允许法律长文需要的结构标签。链接只接受相对同源、`https` 和 `mailto`，外链补齐安全 rel。

先以 UTF-8 字节数限制原文，再限制净化后的 UTF-8 字节数。建议默认原文与结果上限均为 256 KiB、允许范围 16 KiB 到 1 MiB；具体默认值在实现测试中固化。前端用唯一的 `LegalDocumentContent` 包装受控 `dangerouslySetInnerHTML`，其他组件不得复用该入口。

备选方案是只在 React 端 sanitizer，无法保护其他 API 消费者且更容易误用原文；只做字符串替换不足以抵御畸形 HTML，因此不采用。

### 5. 只缓存成功净化结果，注册提交进行同一策略的即时复核

使用进程内有界缓存，key 只有 `TERMS`、`PRIVACY`，value 是最终 DTO；默认 TTL 建议 5 分钟、允许 10 秒到 1 小时。命中时不访问上游；过期后同步重新加载。失败不进入缓存，也不提供无限期 stale 结果，使撤下或清空法律正文能在一个 TTL 内生效。

`RegistrationPolicyService` 统一计算：

`adminEnabled && publicationMode == PUBLIC && termsAvailable && privacyAvailable`

`auth/options` 返回结果与关闭原因；`auth/register` 在调用 New API 注册前再次调用同一服务，避免读取选项后正文失效的竞态。法律检查可以复用成功缓存，因此正常注册不增加两次上游读取。

备选方案是只由前端隐藏表单，能够被直接调用绕过；只在启动时检查正文则无法响应运行时撤下，均不采用。

### 6. 当前 `PUBLIC` 一次只开放一个法律正文语言

代码资源继续完整维护 `zh-CN`、`en-US`，`PREVIEW` 可启用两者以验证界面。正式模式要求 `enabled-locales` 恰好包含 `legal.source-locale` 一个值；法律 DTO 带该 locale，前端校验它属于当前启用集合。语言切换器、浏览器偏好和已保存语言都受运行时列表约束。

备选方案是在一个全局正文存在时仍开放两个界面语言，用户可能把另一语言界面中的正文误认为正式译文；前端机器翻译也不能替代确认后的法律文本，因此不采用。未来如需多语言法律内容，应新增按 locale 存储和版本管理的独立变更。

### 7. 公开信息架构沿用现有 PublicLayout

新增 `/terms`、`/privacy`、`/regions`，并扩展首页、公共导航、移动导航、页脚、注册页与 404；不新建第四类布局。地区页只把后端代码映射成本地化地区名称并显示代码，不根据地区推导其他承诺。support URL 统一由共享 Link 组件验证协议并渲染；空值显示未公布状态，不生成无行为链接。

公共页的查询状态使用现有前端 API/schema/query 模式。法律页对 404 显示“未发布”，对网络或上游错误显示“暂时不可用”，不显示原始服务消息。长表格只允许正文容器局部横向滚动。

### 8. 以路由 Meta 管理器统一 title、description、canonical 与 robots

新增一个接收 route meta、locale、public-config 和页面有效状态的组件，更新或创建唯一的 title、description、canonical 和 robots 元素。`PUBLIC` 且页面有效时允许 `index,follow`；其他情况使用 `noindex,nofollow` 并移除 canonical。SPA 路由切换与语言切换均重新计算，组件卸载不残留上一页标签。

favicon 由现有 `L/` 几何标识生成仓库内静态 SVG，Brand 组件和 favicon 引用同一个可替换资产边界。自动化扫描范围包含 `frontend/src`、静态资源、生产构建文本、Portal 公开 DTO/错误资源和 P1-11 文档；测试夹具中的上游路径可以存在，但不得把私网地址或上游品牌展示给用户。

备选方案是只修改 `index.html` 的固定标签，不能处理运行时站点名、语言、404 和预览状态，因此不采用。

### 9. 验证以契约、安全与真实浏览器路径分层

后端先通过单元/契约测试固定两条上游操作、Header 白名单、格式转换、攻击样例、大小、TTL 与注册门禁，再补 Controller 集成测试。前端先通过 schema/API、locale 键、Meta 管理和页面状态测试，再执行桌面/窄屏浏览器路径，覆盖首页到法律页、地区页、注册页和 404。构建后执行品牌/私网字符串扫描。

正式发布验收不以空样例冒充成功：必须在受控环境填入经确认正文并验证两个接口；仓库默认测试仍使用脱敏夹具。

## Risks / Trade-offs

- [Risk] New API 全局正文没有版本和语言元数据，配置声明可能与实际正文不一致 → `PUBLIC` 限制为单一声明语言，部署清单要求人工核对，页面显示 DTO locale；未来用独立多语言版本系统解决。
- [Risk] 上游短暂故障会在缓存过期后关闭注册 → 短 TTL 成功缓存降低请求频率，失败关闭优先于在无法展示法律正文时继续注册。
- [Risk] HTML 白名单可能移除正式正文中的复杂排版 → 在上线前用真实正文跑净化预览；仅扩展有明确法律展示需要且安全可证明的标签，不开放脚本或样式。
- [Risk] `supportUrl` 使用 `mailto:` 会依赖本地邮件客户端 → 同时展示可读地址；若使用 HTTPS 支持页则直接导航，不宣称站内已具备客服系统。
- [Risk] 动态 Meta 对禁用 JavaScript 的爬虫支持有限 → MVP 保持 SPA 架构并通过 robots/noindex 防止误收录；服务端渲染不在本阶段引入。

## Migration Plan

1. 先加入依赖、配置绑定和四个 `.properties` 文件的 `PREVIEW` 默认值；此时现有部署继续可启动且有效注册关闭。
2. 实现并验证 New API 法律适配、安全转换、缓存、公开接口和注册策略，再扩展 public-config。
3. 部署前端公开路由、导航、页脚、地区、注册状态、Meta 与品牌资产；验证直接刷新和窄屏路径。
4. 在预览环境录入经确认正文，核对净化结果、语言、联系入口、地区和全部公开链接。
5. 生产配置完整后显式切换 `PUBLIC`；只有两份法律正文可用且管理员开关开启时注册才开放。
6. 回滚时把 `lang.publication.mode` 改回 `PREVIEW` 即可立即禁止索引和注册；如需代码回滚，新增配置均保持安全默认且无数据库迁移。
