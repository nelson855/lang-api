## Context

见 `proposal.md` 的动机。当前 `frontend` 是 React 19、React Router 7、TypeScript 5.9、Vite 7 和 Vitest 3 的最小单页应用，仅有根路由与 `/dashboard` 文本占位；根 Maven 已负责 `npm ci`、lint、test、build 和静态资源装配。P1-03 已提供同源 `GET /portal/api/public-config`、统一 `{requestId,data}` / `{requestId,error}` 响应及严格页面 CSP，公开配置可能合法地返回空 `apiBaseUrls`。

站点名称、最终 Logo、正式域名和完整视觉素材允许延后到 P1-11，本阶段必须使用可替换的临时自有标识。认证 API 到 P1-05 才存在，模型数据到 P1-08 才存在，因此 P1-04 只能建立页面与状态边界，不能伪造会话、模型或业务结果。

## Goals / Non-Goals

**Goals:**

- 让后续页面只需组合稳定的布局、组件、文案和数据访问设施，不再各自决定基础模式。
- 让应用在 320px 移动端到常见桌面端均可导航和操作，并从一开始具备键盘与基础无障碍能力。
- 将 Portal API 包装、运行时公开配置、错误和 requestId 处理从页面组件中移除。
- 以真实空状态和阶段说明交付可运行页面骨架，同时保持单 JAR 构建部署。

**Non-Goals:**

- 不实现 P1-05 之后的登录表单提交、会话查询、用户菜单、Key、模型、文档正文、Dashboard 数据或钱包数据。
- 不制作最终品牌 Logo、营销插画、暗色主题、主题编辑器、Storybook、完整数据表格框架或图表组件。
- 不引入 SSR、静态预渲染、微前端或独立 Node.js 生产服务。
- 不修改后端 Portal API、Security Header 或四个 `.properties` 文件；若实现发现现有 CSP 与选定组件确实不兼容，应停止并形成独立后端变更，不在前端任务中暗改安全策略。

## Decisions

### 1. 采用自有轻量设计系统，而不是完整 UI 套件

考虑三种方案：

1. **Tailwind CSS + CSS 语义变量 + 少量 Radix primitives（采用）**：样式完全由项目控制，Dialog 等复杂交互复用无样式可访问行为，最符合既定技术栈和独立视觉要求。
2. 完整 UI 套件：开发更快，但会带入显著的默认布局、主题和升级视觉变化，容易成为“换色面板”。
3. 全部原生 CSS/React 自研：依赖最少，但 Dialog 焦点限制、恢复和键盘交互容易出错，维护成本不符合 MVP。

Tailwind 使用 Vite 插件生成静态 CSS；Radix 只按需安装 Dialog、Slot 等实际使用的包，不安装整套组件集合。Toast 使用自有 live-region + Portal 实现，避免某些定位 primitive 依赖 CSP 会阻止的运行时 inline style。图标使用锁定版本的 Lucide React，装饰图标统一 `aria-hidden`。

### 2. 视觉方向采用“技术编辑”，最终品牌可无损替换

本阶段建立独立但克制的浅色视觉：

- 画布为温暖浅灰，主要文字为深墨色；矿物青作为主操作色，珊瑚色只用于重点和警示，成功/警告/失败各有独立语义色。
- 标题与正文使用本地打包的 Manrope Variable，代码、Base URL 和 requestId 使用 JetBrains Mono Variable；字体资源来自固定 `@fontsource-variable` 包，不请求外部 CDN，符合当前 `'self'` CSP。
- 边界使用细线和留白，卡片只在确有分组时出现；圆角使用 6/10/16px 三档，阴影轻量，不做大面积玻璃拟态、霓虹渐变或模板化卡片墙。
- 临时品牌是纯文本 `Lang API` 加可替换的几何 `L/` SVG 标记。站点文字始终使用 public-config 的 `siteName`，SVG 不包含 New API 或参考站元素。
- 首页用清晰标题、真实产品边界和接入流程占位组织信息，不复制 QinghuaAPI hero 比例、卡片排列或文案。

第一阶段只实现浅色主题。CSS 变量按 primitive → semantic → component 三层命名，保留未来增加暗色值的能力，但不增加无验收需求的主题开关。设计变量集中在 `src/design/tokens.css`，全局排版与 reset 在 `global.css`；组件不得散落十六进制颜色或自定义阴影。

### 3. 源码结构沿用已定页面/功能/通用基础分层

目标结构：

```text
frontend/src/
├── app/
│   ├── providers/       # Query、i18n、public config、toast、auth state
│   ├── router/          # 路由表、lazy 边界、守卫和 route meta
│   └── App.tsx
├── api/                 # fetch client、envelope、error、schemas
├── components/
│   ├── ui/              # Button/Input/FormField/Dialog/Table/Pagination
│   ├── feedback/        # Loading/Empty/Error/Forbidden/NotFound/Toast
│   └── brand/           # 可替换临时标识
├── design/              # tokens、global styles、字体入口
├── features/            # 后续业务；P1-04 仅保留必要 auth contract
├── i18n/                # 初始化、locale、zh-CN/en-US 资源
├── layouts/             # Public/Auth/Console layout
├── pages/               # 本阶段页面与占位页
└── test/                # render helpers、fixtures、a11y helpers
```

UI 组件只负责表现与交互，不请求接口；pages 组合 layout、query 和 feature；API 层不知道 React；Provider 负责应用级生命周期。没有在 P1-04 为每个未来 feature 建空目录，实际业务进入相应阶段时再创建。

### 4. 路由采用集中 route objects、懒加载和页面元数据

使用 React Router 7 library mode 的 `createBrowserRouter` / `RouterProvider`，按公开、认证和控制台布局建立嵌套路由。页面模块用 `lazy` 分割，layout 与启动门禁保留在初始包；每条路由携带稳定 id、i18n 标题键、访问级别和 layout 信息，导航从同一声明派生，避免路径在多处复制。

首批路由：

| 路径 | 布局 | P1-04 内容 | 访问级别 |
|---|---|---|---|
| `/` | Public | 独立首页骨架、真实能力与阶段入口 | public |
| `/models` | Public | 明确“数据尚未接入”的空状态 | public |
| `/docs` | Public | 文档框架与后续接入说明，不伪造调用示例 | public |
| `/login` | Auth | 认证功能尚未开放的状态 | public-only contract |
| `/register` | Auth | 注册策略待 P1-05 确认的状态 | public-only contract |
| `/dashboard` | Console | 控制台骨架与真实占位状态 | protected |
| `*` | Public | 自有 404 | public |

`/dashboard` 从一开始绑定 `RequireAuth`。由于 P1-04 没有会话接口，生产默认 AuthState 为 `anonymous`，访问会安全跳转登录；ConsoleLayout 通过注入 authenticated state 的组件/浏览器 fixture 完成响应式验收，不使用 localStorage 假登录或隐藏 query 参数。P1-05 替换 AuthState provider 的数据来源，而不是重写守卫与布局。

路由切换后由 layout 将焦点移动到主标题，并使用运行时 siteName + 翻译标题更新 `document.title`。未知页面路由只由前端 `*` 处理；`/portal/api/*` 和 `/actuator/*` 仍保持服务端边界。

### 5. 三种布局共享品牌，但信息密度与导航不同

- `PublicLayout`：最大内容宽度约 1180px；桌面为品牌、首页/模型/文档、语言、登录/注册，移动端用 Dialog 抽屉；页脚只含真实可用入口，不提前放法律正文链接。
- `AuthLayout`：居中窄栏表单区域配简短品牌说明；P1-04 只放真实阶段状态，不渲染无提交行为的假表单。
- `ConsoleLayout`：桌面左侧窄导航栏 + 顶部上下文条，移动端折叠为顶部栏和抽屉；P1-04 只声明 Dashboard，后续 Key、日志、钱包等导航项在对应功能落地时增加，不展示不可用菜单。

公共和控制台移动导航均复用 Radix Dialog 行为，但内容与 aria label 独立。断点以内容是否拥挤为准，初始采用 768px；页面容器内边距在窄屏降至 16px。数据表使用语义 table 和自身 `overflow-x:auto`，不把移动端强制转换为另一套未验证的卡片数据结构。

### 6. 基础组件按最小可用 API 设计

- `Button`：primary/secondary/quiet/danger，sm/md，loading/disabled，加载时保持宽度和可访问名称。
- `Input` + `FormField`：label、description、error、required 与 id/aria 自动关联；提供与 React Hook Form/Zod 组合的薄适配，不把表单库类型泄漏到纯输入组件。
- `Dialog`：基于 Radix，统一标题、说明、操作区、关闭行为和最大宽度；业务内容由调用方提供。
- `DataTable`：只处理语义表格、空状态和横向容器，不在 P1-04 实现排序、选择、虚拟滚动等数据网格能力。
- `Pagination`：接收 page/pageSize/total 与回调，计算边界并提供上一页/下一页及必要页码；不自行请求。
- `ToastProvider`：success/info/warning/error、自动关闭、手动关闭和 `aria-live`；错误通知可显示安全 message 和 requestId，不显示原始异常。
- 页面状态：Loading、Empty、Error、Forbidden、NotFound，共享标题/说明/action contract。

React Hook Form 和 Zod 在测试 fixture 中证明可组合，但登录/注册实际 schema 留给 P1-05。没有引入通用 `Card`、`Stack` 等包装所有 DOM 的抽象；重复达到三处且语义稳定后再抽取。

### 7. API 客户端以 URL 白名单和 Zod 校验建立运行时边界

`portalClient.request<T>` 只接受以单个 `/portal/api/` 开头的站内路径和响应 Zod schema。调用前拒绝带 scheme、`//`、反斜线、fragment、user-info、编码或明文 `..` 段以及非白名单前缀；query 参数通过 `URLSearchParams` 从结构化值构造，不接受拼接后的任意 URL。

请求使用 `credentials: 'same-origin'`、`Accept: application/json`，有 JSON body 时设置 Content-Type，并生成 `crypto.randomUUID()` 作为合法 `X-Request-Id`。调用方的 `AbortSignal` 原样传入。响应只解析一次为 `unknown`：

1. 依据 Content-Type 和 JSON 解析结果区分 transport/protocol 错误；
2. 校验成功/失败 envelope；
3. 成功时再校验业务 `data` schema；
4. 失败时形成 `PortalApiError(status, code, message, requestId)`；
5. 非法响应只形成安全 `InvalidPortalResponseError`，绝不把 HTML 或原始正文渲染到 DOM。

取消使用独立错误类型，Query/UI 静默处理。可展示错误从 body 取 requestId；body 不可解析时仅采用响应 Header 中符合 P1-03 规则的值。所有 API path 和 schema 在 `src/api` 或相应 feature 内声明，页面不得直接调用 `fetch`。ESLint 增加 restricted globals/imports 规则阻止绕过。

### 8. QueryClient 默认保守，mutation 永不自动重放

全局 QueryClient 默认 `retry:false`、窗口聚焦不自动刷新、网络恢复不自动重放 mutation，并将 query 的 AbortSignal 传至客户端。具体只读业务将来可显式选择有限重试，但必须在对应需求中测试。

`public-config` 使用固定 query key、`staleTime: Infinity`、`gcTime: Infinity`、`retry:false`。这里的内存缓存只持续当前应用会话，不使用 localStorage/service worker，也不会绕过服务端 `Cache-Control: no-store`。失败后只有错误页的重试按钮调用 `refetch`。

根 Provider 顺序为：i18n → Query → PublicConfigGate → AuthState → Toast → Router。PublicConfigGate 在成功前显示启动状态；失败显示安全错误和 requestId；成功后通过 Context 暴露经过冻结的只读数据。这样导航、文档标题和页面只消费一个运行时品牌来源。

### 9. 鉴权守卫只冻结状态机，不猜测会话

`AuthState` 只有 `checking | authenticated | anonymous | forbidden`，并预留已认证用户的最小展示接口但 P1-04 不提供用户对象。`RequireAuth` 在 checking 时不渲染子树，anonymous 时跳 `/login?returnTo=...`，forbidden 时渲染 403，authenticated 时放行。

returnTo 只接受以单个 `/` 开头且不以 `//` 开头、不含 scheme/backslash 的站内路径，非法值回退 `/dashboard`。P1-04 的默认 provider 为 anonymous；测试通过显式 provider 注入其他状态。没有使用 Cookie 是否存在来判断登录，因为 HttpOnly Cookie 不可读且“有 Cookie”不等于会话有效；真实状态由 P1-05 的当前用户接口决定。

### 10. 国际化以完整资源树和确定的检测顺序运行

i18next + react-i18next 使用 `zh-CN` 与 `en-US` 两棵同构资源。所有 P1-04 文案按 `common`、`nav`、`pages`、`states`、`errors` 分域；资源键是语义标识，不以中文原文作为 key。测试递归比较键集合和插值占位符，禁止缺键静默上线。

初始 locale 按：受控 localStorage 值 → `navigator.languages` 中首个支持项（`zh*`/`en*` 映射）→ `zh-CN`。切换后写入单一 namespaced key，立即更新 `<html lang>` 和文档标题，不改变 route/query 状态。i18next 保持插值转义；React 也按文本节点渲染 siteName 和服务端安全 message，不使用 `dangerouslySetInnerHTML`。

日期、数字和金额通过集中 formatter 使用 `Intl`；金额 formatter 必须同时收到 currency 或明确 quota unit，否则返回未知单位状态而不是猜测。P1-04 页面尚无业务金额，只建立接口和测试。

### 11. 测试分层覆盖行为，不建设单独组件展示站

- Vitest 单元测试：URL 白名单、envelope/Zod、错误类型、requestId、returnTo、locale 检测、翻译键、分页计算和 formatter。
- Testing Library 组件测试：按钮/表单、Dialog 焦点、Toast live region、五类状态、layout/navigation、路由重定向、public config gate 和语言切换；使用 `user-event`，不直接调用内部 handler。
- axe 自动检查：布局、状态和复合组件的代表状态无严重/关键可访问性问题；同时保留键盘行为断言，不能只依赖静态扫描。
- Playwright 冒烟：拦截同源 public-config，覆盖桌面公开导航、320px 移动导航、深层路由刷新、404、空地址、配置失败重试、语言持久化和注入 authenticated fixture 后的控制台布局。
- 生产构建扫描：禁止 New API 品牌、私网主机、原始 `/api/` 路径、示例域名和源码 sourcemap；检查字体、CSS 和 JS 全部本地加载且不触发 CSP violation。

现有 Maven `test` 继续运行 Vitest，正式 `verify` 不强制下载浏览器。Playwright 使用独立 `npm run test:e2e`，在已安装锁定 Chromium 的开发/CI 环境执行；验收时两类命令都必须报告。没有引入 Storybook，因为本阶段组件数量有限，测试 fixture 足以验证状态，Storybook 会增加另一套构建和发布边界。

### 12. 依赖和发布保持可追溯

新增包在 `package.json` 使用精确版本并由 `package-lock.json` 固定传递依赖：TanStack Query、Zod、React Hook Form、hookform resolvers、i18next/react-i18next、Tailwind Vite 插件、必要 Radix primitives、Lucide、两种本地字体、Testing Library user-event、axe 与 Playwright。实现开始时从官方 registry 核对与现有 React/Vite/Node 版本兼容的稳定版，不使用 beta、RC、`latest` 或宽泛范围。

Vite 继续输出相对当前站点的 hashed assets，不注入 New API URL 或 `VITE_*` 业务地址。生产 sourcemap 默认关闭。前端无运行时环境配置文件；所有环境差异经既有 public-config 提供，因此本变更不触碰后端 `.properties`。

## Risks / Trade-offs

- [最终品牌尚未确定，临时视觉可能需要调整] → 品牌标识、siteName 和语义 token 分离；P1-11 只替换资产与 token，不改布局和组件 API。
- [严格 CSP 与第三方 Headless 组件的 inline style 行为冲突] → 只选经生产构建和 CSP 测试验证的 primitive，避免浮动定位组件与 inline style；不通过放宽 CSP 掩盖问题。
- [P1-04 无真实认证接口，控制台默认不可进入] → 保持 anonymous 真状态；通过显式测试 provider 验证 ConsoleLayout，P1-05 只接入 AuthState 数据源。
- [根级 public-config 门禁会在后端配置故障时阻止整个 UI] → 提供独立、可重试且保留 requestId 的启动错误页；拒绝用过期或虚假 API 地址继续运行。
- [组件过早抽象会拖慢后续业务] → 只实现 P1-04 明确列出的控件和必要变体，不建立复杂 data grid、通用 schema renderer 或布局 DSL。
- [Playwright 浏览器未进入 Maven 默认工具链] → 将其作为明确的独立验收命令并锁定版本；Vitest 仍由正式 Maven verify 强制执行。
- [本地字体增加前端包体积] → 仅打包实际字重所需的 variable 字体和必要字符资源，检查压缩产物；换取离线一致性和无第三方字体请求。

## Migration Plan

1. 锁定新增依赖并配置 Tailwind、全局 CSS、字体和测试工具，先证明现有最小页面与 Maven 构建不回归。
2. 以测试先行方式建立 API client、Query/PublicConfig Provider、i18n 和 auth state/guard，再替换应用入口。
3. 建立设计 token、基础组件和通用状态，完成键盘、axe 和减少动效测试。
4. 建立三个 layout、集中路由和全部阶段页面；删除旧 `App.tsx` 内的硬编码占位实现。
5. 增加 Playwright fixtures 与桌面/移动浏览器验收，运行生产构建扫描和 CSP 检查。
6. 执行前端 lint/test/build、独立 e2e 和项目正式 Maven verify；全部通过后更新 P1-04 状态和前端开发说明。

回滚只需回退同一 Lang API 制品，不涉及数据迁移。P1-05 开始依赖路由守卫和组件 API 后，不得单独回退 P1-04 前端基础；应整体回退匹配版本的前端与 Portal API。
