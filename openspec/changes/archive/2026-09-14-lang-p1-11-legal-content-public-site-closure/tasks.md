## 1. 上游契约与依赖基线

- [x] 1.1 为 `/api/user-agreement` 与 `/api/privacy-policy` 补齐脱敏成功、空正文、业务失败、非法类型和未知字段契约夹具，并断言冻结版方法、路径与字符串 `data` 结构
- [x] 1.2 先编写失败测试固定法律操作的匿名 Header 白名单、最小响应转换和安全错误映射，再实现两个 New API 适配操作
- [x] 1.3 在后端 Maven 模块引入并锁定 `commonmark-java` 与 OWASP Java HTML Sanitizer，执行依赖解析和现有测试确认无冲突

## 2. 发布与法律配置

- [x] 2.1 先编写配置绑定和校验测试，覆盖 `PREVIEW` 安全默认、`PUBLIC` 完整字段、URL 安全、地区代码、启用语言、源语言、格式、大小与 TTL 边界
- [x] 2.2 在 `application.properties` 增加共享的 publication/public/legal 配置键、默认值与有界限制，保持纯 `.properties`
- [x] 2.3 在 `application-dev.properties` 显式配置 dev 的 `PREVIEW` 模式、本地站点信息、双语言验证值与法律处理参数
- [x] 2.4 在 `application-test.properties` 显式配置 test 的确定性 `PREVIEW` 值、测试地区/语言与短缓存参数
- [x] 2.5 在 `application-prod.properties` 显式配置 prod 的 `PREVIEW` 安全默认和正式字段环境变量入口，不提供占位品牌、域名、联系或地区
- [x] 2.6 实现配置绑定、规范化和 Profile 启动校验，验证 `PUBLIC` 只启用与法律源语言一致的一个 locale

## 3. 法律内容后端链路

- [x] 3.1 先编写法律正文转换测试，覆盖 Markdown、HTML、混入原始 HTML、危险节点/属性/协议、外链 rel、安全结构标签和净化为空
- [x] 3.2 实现 `LegalContentRenderer` 的格式解析、固定 HTML 白名单、链接后处理及原文/结果 UTF-8 字节限制
- [x] 3.3 先编写缓存测试，覆盖按类型命中、TTL 过期、正文撤下、失败不缓存、并发加载和缓存值不含原文
- [x] 3.4 实现有界进程内成功结果缓存和 `LegalContentService`，统一映射类型、标题、locale、未发布与上游错误
- [x] 3.5 先编写 Controller 集成测试，覆盖两个匿名 GET、统一响应 DTO、404 未发布、上游错误、错误方法、Header 与正文不泄漏
- [x] 3.6 实现 `/portal/api/legal/terms`、`/portal/api/legal/privacy`、响应 DTO 与安全异常映射

## 4. 公开配置与注册门禁

- [x] 4.1 先扩展 public-config 单元/集成契约测试，固定新增字段、空预览状态、正式配置规范化、`no-store` 和最小披露
- [x] 4.2 扩展 `PublicConfigResponse` 与服务映射，返回 publication mode、站点 URL、支持入口、地区和启用语言且不访问 New API 状态接口
- [x] 4.3 先编写注册策略测试，覆盖管理员关闭、预览模式、任一法律正文缺失、全部就绪及关闭原因优先级
- [x] 4.4 实现统一 `RegistrationPolicyService`，让 `auth/options` 返回有效开放状态和 `registrationDisabledReason`
- [x] 4.5 为 `auth/register` 增加提交时门禁复核测试与实现，确保法律内容失效时在 New API 写操作前返回 `REGISTRATION_DISABLED`

## 5. 前端运行时契约与基础组件

- [x] 5.1 先扩展前端 public-config、auth options 与 legal API schema 测试，再实现对应 TypeScript 类型、运行时校验和查询函数
- [x] 5.2 先编写运行时 locale 选择测试，覆盖启用列表、浏览器偏好、失效持久值、切换和法律正文 locale 不匹配
- [x] 5.3 实现由 `enabledLocales` 约束的初始化与语言切换器，并保持 `zh-CN`、`en-US` 资源键集合一致
- [x] 5.4 先编写专用法律正文组件测试，确认只消费服务端 `contentHtml`、正确标记语言、管理链接且长内容局部滚动
- [x] 5.5 实现不可通用复用的 `LegalDocumentContent` 及法律页面加载、正文、未发布和失败状态
- [x] 5.6 实现经过协议校验的运行时支持链接组件和受控地区代码本地化映射

## 6. 公开页面与导航闭环

- [x] 6.1 先编写路由和 PublicLayout 测试，覆盖 `/terms`、`/privacy`、`/regions`、直接刷新契约、桌面/移动导航和完整页脚链接
- [x] 6.2 注册三条公开路由并实现用户协议、隐私政策和服务地区页面，空地区不填充推测数据
- [x] 6.3 扩展 PublicLayout 的桌面导航、移动导航和页脚，接入法律、地区、支持以及有效登录/注册入口
- [x] 6.4 重构首页为运行时驱动的真实产品入口，按发布模式和公开协议表达可用状态，移除阶段占位与无行为主要操作
- [x] 6.5 扩展注册页测试和实现，按三种关闭原因显示准确说明并在加载、开放、关闭状态始终提供法律入口
- [x] 6.6 完善自有 404 页面，提供首页和有效支持入口且不泄漏服务端或上游错误信息

## 7. 品牌资产与页面 Meta

- [x] 7.1 将现有 `L/` 几何标识收口为可集中替换的 Brand 资产，生成同源仓库内 SVG favicon 并补齐可访问名称
- [x] 7.2 先编写路由 Meta 管理测试，覆盖路由/语言切换、唯一标签、canonical 生成、`PREVIEW`、404、错误和未发布法律页的 `noindex`
- [x] 7.3 实现运行时路由 Meta 管理器，并为首页、模型、文档、法律、地区、认证和 404 配置中英文标题与 description
- [x] 7.4 增加自动品牌扫描，检查前端源码、静态资源、生产构建文本、公开 DTO/错误资源和用户文档中的禁止品牌与私网地址

## 8. 验证与发布准备

- [x] 8.1 运行后端单元、契约、架构和 Controller 集成测试，确认适配边界、正文安全、缓存、配置与注册门禁全部通过
- [x] 8.2 运行前端单元测试、类型检查、Lint、生产构建和品牌扫描，确认中英文资源、schema、Meta 与页面状态通过
- [ ] 8.3 在桌面和窄屏执行公开路径浏览器验证，覆盖首页→法律/地区、页脚、移动导航、注册关闭/开放、长正文、404 与可访问焦点
- [x] 8.4 使用仓库空样例验证 `PREVIEW` 未发布状态与注册关闭，不把空内容计为正式发布通过
- [ ] 8.5 由项目方提供并由具备资质人员确认正式站点名、Logo 采用结论、域名、支持入口、地区、法律正文和源语言，再在受控预览环境核对净化结果
- [x] 8.6 运行完整 Maven 验证、前端质量检查、OpenSpec 严格校验和 `git diff --check`，记录 `git status`；不提交、不发布
