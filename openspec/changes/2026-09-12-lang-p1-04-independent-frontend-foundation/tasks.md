## 1. 依赖、样式与测试工具基线

- [ ] 1.1 从官方 npm registry 核对与现有 React 19、Vite 7、TypeScript 5.9、Node 22 兼容的稳定版本，记录并使用精确版本引入 TanStack Query、Zod、React Hook Form、i18next、Tailwind Vite 插件、必要 Radix primitives、Lucide、本地字体及测试依赖
- [ ] 1.2 更新并核验 `frontend/package-lock.json`，确认无 beta/RC、浮动范围、完整 UI 模板或未实际使用的 Radix 组件包，并运行 `npm ci` 证明锁文件可重现
- [ ] 1.3 配置 Tailwind Vite 插件、CSS 入口、Manrope/JetBrains Mono 本地字体和生产 sourcemap 关闭；新增测试先确认生产页面无外部字体/样式请求且符合现有 CSP
- [ ] 1.4 配置 Testing Library user-event、axe 与共享 render helper，扩展 Vitest setup，确保测试间清理 Query cache、DOM、localStorage 和 i18n 状态
- [ ] 1.5 增加 Playwright 配置、`test:e2e` 脚本和可拦截 public-config 的浏览器 fixture；固定 Chromium 版本但不把浏览器下载加入默认 Maven `verify`

## 2. Portal API 客户端与服务端状态

- [ ] 2.1 先编写 URL 白名单单元测试，覆盖合法 `/portal/api/*`、绝对/协议相对 URL、反斜线、fragment、编码或明文路径穿越和非 Portal 路径，再实现结构化 query 参数与请求路径校验
- [ ] 2.2 先编写 envelope、业务 DTO、非 JSON、非法结构和 requestId 提取测试，再实现 Zod 响应包装 schema、`PortalApiError`、协议错误、网络错误和取消错误类型
- [ ] 2.3 先编写 fetch 契约测试，断言同源 credentials、Accept/Content-Type、合法 `X-Request-Id`、AbortSignal 和原始响应不泄露，再实现无 React 依赖的统一 Portal API 客户端
- [ ] 2.4 增加 ESLint 边界规则和失败 fixture，禁止 pages/components/layouts 直接调用全局 `fetch`、声明绝对 API URL或引用 New API 原始 `/api/*` 路径
- [ ] 2.5 先编写 QueryClient 默认行为测试，覆盖 query 取消信号、窗口聚焦不刷新、公开配置不自动重试及 mutation 不重放，再实现应用级 Query Provider
- [ ] 2.6 先依据既有 `public-portal-config` spec 编写 public-config schema/query 测试，覆盖有效地址、空数组、统一失败和非法响应，再实现固定 query key 与手动 refetch
- [ ] 2.7 先编写 PublicConfigGate 组件测试，覆盖启动加载、成功、空地址、失败 requestId 和手动重试，再实现只读运行时配置 Context，禁止硬编码示例 Base URL 回退

## 3. 国际化与安全格式化

- [ ] 3.1 先编写 locale 检测测试，固定“保存值 → 浏览器支持语言 → zh-CN”的优先级和非法保存值回退，再实现受控 locale 解析与 namespaced localStorage 持久化
- [ ] 3.2 建立 `zh-CN` 与 `en-US` 的 `common`、`nav`、`pages`、`states`、`errors` 资源树，并先编写递归测试确保键集合、插值参数和 P1-04 文案覆盖完全一致
- [ ] 3.3 先编写语言切换组件测试，覆盖无刷新更新、路由/状态保留、`<html lang>` 与 document title 同步，再实现 i18n Provider 和布局可复用的语言切换器
- [ ] 3.4 先编写 HTML-like 插值、日期、数字、货币和未知单位测试，再实现基于 Intl 的集中 formatter，禁止缺少币种或 quota unit 时猜测金额

## 4. 设计变量与基础可访问性

- [ ] 4.1 在测试/静态检查中先约束组件不得散落十六进制颜色和自定义阴影，再建立 primitive、semantic、component 三层 CSS 变量及全局 reset、排版、焦点和选择样式
- [ ] 4.2 实现温暖浅灰、深墨、矿物青、珊瑚强调及完整状态色的浅色主题，使用 6/10/16px 圆角和克制阴影，并通过工具核验正文、链接、按钮、状态和焦点对比度达到 WCAG AA
- [ ] 4.3 编写 320px、移动端和桌面容器测试/浏览器断言，建立 16px 窄屏内边距、约 1180px 公共内容宽度和 768px 初始导航断点，禁止页面级横向滚动
- [ ] 4.4 先编写 `prefers-reduced-motion` 样式测试，再实现全局减少动效规则，关闭非必要位移与装饰过渡但保留状态反馈
- [ ] 4.5 创建可替换的纯文本品牌组件和自有 `L/` SVG 标记，验证显示名只读取 public-config `siteName`，资产不含 QinghuaAPI/New API 标识

## 5. 基础交互组件

- [ ] 5.1 先编写 Button 交互与可访问性测试，再实现 primary/secondary/quiet/danger、sm/md、loading 和 disabled 状态，确保加载时宽度与可访问名称稳定
- [ ] 5.2 先编写 Input/FormField 的 label、description、required、error 与 aria 关联测试，再实现纯展示组件及 React Hook Form + Zod 薄适配测试 fixture，不创建真实认证表单
- [ ] 5.3 先编写仅键盘打开、焦点限制、Escape 关闭和焦点恢复测试，再基于 Radix 实现 Dialog，并验证生产环境不需要 CSP 禁止的 inline style
- [ ] 5.4 先编写语义表头、空状态和容器横向滚动测试，再实现最小 DataTable；不加入排序、选择、虚拟滚动或业务请求逻辑
- [ ] 5.5 先编写第一页、末页、零记录和非法参数测试，再实现只负责页码计算与回调的 Pagination，禁止触发越界页码
- [ ] 5.6 先编写 success/info/warning/error、自动/手动关闭、aria-live 和 requestId 展示测试，再实现自有 Toast Provider/Portal，确认不渲染原始异常内容
- [ ] 5.7 先编写 Loading、Empty、RetryableError、Forbidden 和 NotFound 的文案、语义、操作与 axe 测试，再实现共享反馈组件

## 6. 鉴权守卫与应用 Provider

- [ ] 6.1 先编写 `checking`、`authenticated`、`anonymous`、`forbidden` 状态测试，再实现可注入 AuthState Context 与 `RequireAuth`，确认检查期间不闪现受保护内容
- [ ] 6.2 先编写 returnTo 安全测试，覆盖站内路径、绝对/协议相对 URL、反斜线及恶意编码，再实现非法值回退 `/dashboard` 的校验器
- [ ] 6.3 实现 P1-04 默认 anonymous AuthState，验证不读取 HttpOnly Cookie、不使用 localStorage/query 参数伪造 authenticated；为 P1-05 留出替换数据源而不改守卫 API 的边界
- [ ] 6.4 组合 i18n、Query、PublicConfigGate、AuthState、Toast 和 Router Provider，先用集成测试验证启动顺序、错误隔离和卸载清理，再替换当前最小 `main.tsx`/`App.tsx` 入口

## 7. 布局、导航与路由

- [ ] 7.1 先编写集中路由元数据测试，固定 `/`、`/models`、`/docs`、`/login`、`/register`、`/dashboard`、`*` 的 layout、访问级别、标题键和导航来源，再实现 lazy route objects
- [ ] 7.2 先编写 PublicLayout 桌面导航、当前项、运行时品牌、语言切换和最小页脚测试，再实现约 1180px 的公开站点壳
- [ ] 7.3 先编写移动导航的打开、焦点限制、背景阻断、选中关闭、Escape 和焦点落点测试，再使用共享 Dialog 行为实现公开布局抽屉
- [ ] 7.4 先编写返回首页、无虚假 OAuth/验证方式和窄屏测试，再实现聚焦账户操作的 AuthLayout
- [ ] 7.5 先以 injected authenticated fixture 编写桌面侧栏、移动顶部栏/抽屉、页面标题和仅显示已交付入口的测试，再实现 ConsoleLayout
- [ ] 7.6 实现首页真实产品骨架，文案只描述自有站点、Portal API 与明确阶段能力，不复制参考站结构，不宣称尚未开放的模型协议或业务功能
- [ ] 7.7 实现模型、文档、登录、注册和 Dashboard 阶段占位页，逐页测试不包含伪造模型/价格/用户/余额/认证结果或无行为主要按钮
- [ ] 7.8 实现自有前端 404 和路由 Error Boundary，测试站内导航、深层刷新恢复、路由切换主标题焦点和 runtime siteName + locale 文档标题
- [ ] 7.9 将 `/dashboard` 绑定 RequireAuth，验证生产默认 anonymous 跳转 `/login`，测试 authenticated 时进入控制台，非法 returnTo 不造成开放重定向

## 8. 浏览器验收与工程收口

- [ ] 8.1 编写并运行 Playwright 桌面流程，覆盖 public-config 启动、首页/模型/文档导航、页面标题、空地址状态、配置失败手动重试和自有 404
- [ ] 8.2 编写并运行 320px Playwright 流程，覆盖公开/控制台移动导航、无页面横向滚动、键盘焦点和 authenticated fixture 下的控制台布局
- [ ] 8.3 编写并运行语言流程，验证浏览器语言检测、手动切换、刷新持久化、路由状态保持及中英文全路由无翻译键/空标签
- [ ] 8.4 对代表性页面和复合组件运行 axe 与键盘验收，修复 serious/critical 问题，并人工核验焦点、对比度、减少动效和触控目标
- [ ] 8.5 运行 `npm run lint`、`npm run test`、`npm run build` 与独立 `npm run test:e2e`，记录测试数量、浏览器版本和结果
- [ ] 8.6 扫描 `frontend/src`、`frontend/dist` 和最终 JAR，确认无 New API/QinghuaAPI 默认品牌、私网地址、原始管理 `/api/*` 路径、示例域名、外部字体请求、业务 `VITE_*` 地址或生产 sourcemap
- [ ] 8.7 使用项目指定 Maven 3.8.4 与 `settings.xml` 运行根工程 `verify`，再启动一体化 JAR 验证 `/`、深层路由、`/portal/api/public-config` 和未知 Portal API 边界未回归
- [ ] 8.8 新增前端开发说明，记录目录职责、设计 token、组件 API、路由元数据、i18n、API client、测试命令以及 P1-05 接入 AuthState 的约束
- [ ] 8.9 对照 4 个 delta spec 记录实际验收结果；全部通过后更新文档索引和 `docs/06_第一阶段MVP开发与子需求拆分.md` 中 LANG-P1-04 状态，不修改 AGENTS.md
- [ ] 8.10 运行 `git diff --check` 并报告最终 `git status`、测试、未验证条件和遗留风险，不执行 Git 提交或发布
