# Lang API Deep Ink 全站视觉重构 Implementation Plan

> **执行约束：** 本项目禁止未经用户明确授权的独立 Review、Git 提交和发布。本计划按当前会话内联执行，每个任务完成后只运行相关测试与核验。

**Goal:** 在不改变 Portal API、鉴权与业务流程的前提下，将所有既有前端页面重构为一致的 Deep Ink 深色产品体验。

**Architecture:** 视觉令牌集中于 `design/`，基础组件与三类布局承接跨页面样式，页面只组合共享的面板、标题、表格与状态模式。保留 React Query、React Router、Radix Dialog、i18n 与现有业务 hooks，禁止新建屏幕级数据请求或虚构业务数据。

**Tech Stack:** React 19、TypeScript、Vite、CSS、React Router 7、TanStack Query、Radix Dialog、Vitest、Playwright、vitest-axe。

**Spec:** [2026-09-15-lang-api-deep-ink-redesign-design.md](../specs/2026-09-15-lang-api-deep-ink-redesign-design.md)

## 全局约束

- 全部新增颜色只能定义在 `frontend/src/design/tokens.css`；组件和页面只能消费语义令牌。
- 默认深色 Deep Ink；`prefers-reduced-motion` 下关闭装饰动效；文本、焦点与控件达到 WCAG 2.2 AA。
- 不改变任何 `/portal/api/*` 路径、Zod 契约、认证缓存、搜索取消、分页、危险操作确认、密钥显示或复制语义。
- 未经用户授权不执行 `git commit`、`git push`、创建 PR 或独立 Review；所有改动保留未提交。
- 所有列表、模型、日志、余额与状态来自现有真实接口；加载、空数据、错误、未授权和未知结果不能用示例数据掩盖。

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `frontend/DESIGN.md` | 长期视觉意图、令牌所有权与运行时 CSS 映射 |
| `frontend/src/design/tokens.css` | Deep Ink 原始、语义和组件令牌 |
| `frontend/src/design/global.css` | 排版、滚动条、页面容器、焦点、动效基线 |
| `frontend/src/components/ui/PageHeader.tsx` / `.css` | 可复用页面标题、说明、操作区 |
| `frontend/src/components/ui/Surface.tsx` / `.css` | Panel、metric、toolbar 等业务命名表面变体 |
| `frontend/src/components/ui/*.css` | Button、Input、DataTable、Dialog、Pagination 的统一状态 |
| `frontend/src/layouts/*.tsx` / `.css` | Public/Auth/Console 的导航与内容壳 |
| `frontend/src/pages/*.tsx` / `.css` | 路由级页面结构；只组合共享组件和现有 hooks |
| 现有 `*.test.tsx` 与新增页面/组件测试 | 视觉结构、状态、可访问性与回归约束 |

## Task 1：建立 Deep Ink 设计上下文与令牌基线

**Files:**

- Create: `frontend/DESIGN.md`
- Modify: `frontend/src/design/tokens.css`
- Modify: `frontend/src/design/global.css`
- Modify: `frontend/src/design/tokens.test.ts`
- Modify: `frontend/src/design/noHardcodedStyle.guard.test.ts`

**Interfaces:**

- Consumes: 当前三层 CSS 令牌约束和 `tokens.test.ts`。
- Produces: `--color-bg`、`--color-bg-raised`、`--color-bg-muted`、`--color-text`、`--color-text-muted`、`--color-primary`、`--color-border`、`--color-focus` 等不变的语义变量；新增 Deep Ink 组件变量只由共享 CSS 消费。

- [ ] **Step 1: 写入会失败的令牌断言**

  在 `tokens.test.ts` 中断言 Deep Ink 关键值和语义映射存在：

  ```ts
  expect(css).toContain('--primitive-void-950: #0d1117;');
  expect(css).toContain('--color-bg: var(--primitive-void-950);');
  expect(css).toContain('--color-primary: var(--primitive-signal-400);');
  ```

- [ ] **Step 2: 运行定向测试确认失败**

  Run: `cd frontend && npm run test -- src/design/tokens.test.ts`

  Expected: FAIL，因为 Deep Ink 令牌尚未定义。

- [ ] **Step 3: 最小实现令牌与全局基线**

  将现有米白/青绿色板替换为 `Void #0D1117`、`Carbon #131920`、`Slate #1B232D`、`Paper #EEF2F4`、`Mist #929DAA`、`Signal #7CF2C7`，并保持既有语义变量名。为 `body`、选择文本、可见焦点、原生滚动条和高对比模式增加全局规则；保留 `.app-container` 与 `.table-scroll` 的滚动所有权。

- [ ] **Step 4: 写入 `frontend/DESIGN.md`**

  记录色板、中文/英文/等宽字体角色、圆角和表面层级、唯一信号色、减少动效规则，以及令牌从 `tokens.css` 到共享组件/布局 CSS 的唯一映射。

- [ ] **Step 5: 运行相关测试**

  Run: `cd frontend && npm run test -- src/design/tokens.test.ts src/design/noHardcodedStyle.guard.test.ts`

  Expected: PASS。

## Task 2：升级共享表面、控件与反馈状态

**Files:**

- Create: `frontend/src/components/ui/PageHeader.tsx`
- Create: `frontend/src/components/ui/PageHeader.css`
- Create: `frontend/src/components/ui/PageHeader.test.tsx`
- Create: `frontend/src/components/ui/Surface.tsx`
- Create: `frontend/src/components/ui/Surface.css`
- Create: `frontend/src/components/ui/Surface.test.tsx`
- Modify: `frontend/src/components/ui/Button.css`
- Modify: `frontend/src/components/ui/Input.css`
- Modify: `frontend/src/components/ui/DataTable.css`
- Modify: `frontend/src/components/ui/Dialog.css`
- Modify: `frontend/src/components/feedback/Feedback.css`
- Modify: `frontend/src/components/feedback/Toast.css`
- Modify: 现有对应组件测试

**Interfaces:**

- Consumes: Task 1 的语义颜色与现有 `Button`、`Input`、`DataTable`、`Dialog` API。
- Produces: `PageHeader({ title, description, actions, eyebrow? })` 和 `Surface({ variant: 'panel' | 'metric' | 'toolbar', children, className? })`；不改变现有 Button/Input/Dialog props。

- [ ] **Step 1: 写入 PageHeader 失败测试**

  ```tsx
  render(<PageHeader title="API 密钥" description="管理调用凭据" actions={<Button>新建密钥</Button>} />);
  expect(screen.getByRole('heading', { name: 'API 密钥' })).toBeVisible();
  expect(screen.getByRole('button', { name: '新建密钥' })).toBeVisible();
  ```

- [ ] **Step 2: 运行测试确认失败**

  Run: `cd frontend && npm run test -- src/components/ui/PageHeader.test.tsx`

  Expected: FAIL，因为模块不存在。

- [ ] **Step 3: 实现两个纯表现组件**

  `PageHeader` 渲染语义 `<header>`、唯一的 `<h1>`、可选说明和非空操作区；`Surface` 只在 `section` 上输出 `surface surface--${variant}`。CSS 使用 token，定义深灰表面、细边框、稳定间距和 `focus-visible`，不加入业务逻辑。

- [ ] **Step 4: 重写基础控件 CSS**

  保持 Button 的 primary/secondary/quiet/danger 语义和 busy 尺寸；Input 保持标签/错误关联；DataTable 保持原生表格和内部滚动；Dialog/Toast/Feedback 使用 Deep Ink 表面并保证危险/错误信息不只靠颜色表达。

- [ ] **Step 5: 运行组件与可访问性测试**

  Run: `cd frontend && npm run test -- src/components/ui src/components/feedback`

  Expected: PASS，既有组件行为与 axe 断言不退化。

## Task 3：重构 Public、Auth 与 Console 三类布局

**Files:**

- Modify: `frontend/src/layouts/PublicLayout.tsx`
- Modify: `frontend/src/layouts/PublicLayout.css`
- Modify: `frontend/src/layouts/AuthLayout.tsx`
- Modify: `frontend/src/layouts/AuthLayout.css`
- Modify: `frontend/src/layouts/ConsoleLayout.tsx`
- Modify: `frontend/src/layouts/ConsoleLayout.css`
- Modify: `frontend/src/layouts/PublicLayout.test.tsx`
- Modify: `frontend/src/layouts/AuthLayout.test.tsx`
- Modify: `frontend/src/layouts/ConsoleLayout.test.tsx`
- Modify: `frontend/src/layouts/MobileNav.test.tsx`

**Interfaces:**

- Consumes: Task 1 tokens、Task 2 PageHeader/Surface、现有路由与移动抽屉行为。
- Produces: 三类布局相同的品牌/焦点/导航语言；现有 `Outlet` context 与路由不变。

- [ ] **Step 1: 增加布局语义回归测试**

  测试 Public 顶栏含当前导航和主行动入口；Auth 只含品牌和返回首页入口；Console 侧栏的工作区/账户组、用户显示名和退出操作均可通过角色访问。

- [ ] **Step 2: 运行测试确认当前结构不足**

  Run: `cd frontend && npm run test -- src/layouts/PublicLayout.test.tsx src/layouts/AuthLayout.test.tsx src/layouts/ConsoleLayout.test.tsx`

  Expected: FAIL，直到新增结构和标签完成。

- [ ] **Step 3: 实现布局结构与 CSS**

  PublicLayout 使用紧凑深色顶栏与三栏页脚；AuthLayout 用聚焦表单容器和低干扰背景；ConsoleLayout 使用 224px 侧栏、内容顶栏与独立内容区。保留原 Dialog 移动抽屉、`usePageChrome`、auth options 和 logout mutation，所有导航仍为 `Link`/`NavLink`。

- [ ] **Step 4: 调整移动断点**

  在 768px 以下隐藏桌面导航和侧栏，使用既有抽屉；确保菜单按钮保留可访问名称、焦点和关闭行为。

- [ ] **Step 5: 运行布局测试**

  Run: `cd frontend && npm run test -- src/layouts`

  Expected: PASS。

## Task 4：重构公开内容、模型、文档、地区、法律与 404 页面

**Files:**

- Modify: `frontend/src/pages/HomePage.tsx`
- Modify: `frontend/src/pages/ModelsPage.tsx`
- Modify: `frontend/src/pages/DocsPage.tsx`
- Modify: `frontend/src/pages/RegionsPage.tsx`
- Modify: `frontend/src/pages/TermsPage.tsx`
- Modify: `frontend/src/pages/PrivacyPage.tsx`
- Modify: `frontend/src/pages/NotFoundPage.tsx`
- Create: `frontend/src/pages/PublicPages.css`
- Modify: `frontend/src/components/CodeBlock.tsx`
- Modify: `frontend/src/components/legal/LegalDocumentContent.css`
- Modify: `frontend/src/pages/pages.test.tsx`
- Modify: `frontend/src/components/legal/LegalDocumentView.test.tsx`

**Interfaces:**

- Consumes: 现有 `useModels`、`usePublicConfigData`、`useLegalDocument`、`CodeBlock` 和 Task 2 基础组件。
- Produces: B2 中文主导首页、真实数据驱动的模型/文档内容和阅读型地区/法律/404 页面；不新增 API。

- [ ] **Step 1: 写入公开页结构测试**

  为首页断言 h1、主行动、文档入口和模型/协议的真实状态分支；为 ModelsPage 断言搜索/厂商筛选/空结果；为 DocsPage 断言调用地址不可用时不显示复制动作；为法律和 404 保留支持链接。

- [ ] **Step 2: 运行相关测试确认失败**

  Run: `cd frontend && npm run test -- src/pages/pages.test.tsx src/components/legal/LegalDocumentView.test.tsx`

  Expected: FAIL，直到新的语义结构完成。

- [ ] **Step 3: 实现首页 B2 结构**

  用 `PageHeader` 之外的专用 hero 组成左侧中文价值主张、右侧代码窗口；从 `apiBaseUrls` 和模型查询构造可用/不可用状态。模型预览必须是当前 query 返回的前若干项；为空时使用 `Empty`，绝不硬编码可调用模型或成功延迟。

- [ ] **Step 4: 实现其他公开页视觉结构**

  模型页使用 `Surface` 工具栏和可访问搜索/筛选控件；文档页用阅读主栏、锚点导航和改造后的 `CodeBlock`；地区、法律、404 共享 `PublicPages.css` 的阅读宽度、标题和状态表面。保持 `LegalDocumentView` 的索引性 outlet 回调。

- [ ] **Step 5: 运行公开页测试与构建**

  Run: `cd frontend && npm run test -- src/pages src/components/legal && npm run build`

  Expected: PASS。

## Task 5：重构认证页与真实表单呈现

**Files:**

- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `frontend/src/pages/RegisterPage.tsx`
- Create: `frontend/src/pages/AuthPages.css`
- Modify: `frontend/src/pages/LoginPage.test.tsx`
- Modify: `frontend/src/pages/RegisterPage.test.tsx`

**Interfaces:**

- Consumes: 现有 login/register mutation、`resolveReturnTo`、`fetchAuthOptions` 和 Task 2 表单组件样式。
- Produces: 视觉升级的表单卡片；请求体、导航结果、注册关闭态和错误分类完全不变。

- [ ] **Step 1: 增加可访问表单测试**

  断言每个输入都有可见 label 和正确 `autocomplete`，提交时按钮进入 disabled/busy，错误保持 `role="alert"`；注册关闭态仍不渲染可提交表单。

- [ ] **Step 2: 运行测试确认失败**

  Run: `cd frontend && npm run test -- src/pages/LoginPage.test.tsx src/pages/RegisterPage.test.tsx`

  Expected: FAIL，直到测试要求的表单结构出现。

- [ ] **Step 3: 用 FormField/Input/Button 组合重写页面标记**

  保留 `noValidate`、原始 state、mutation 和登录跳转，仅把原生 label/input/button 迁移到既有 `FormField`、`Input`、`Button`。在 `AuthPages.css` 中实现深色表单卡、辅助文案和密码安全提示区。

- [ ] **Step 4: 运行认证测试**

  Run: `cd frontend && npm run test -- src/pages/LoginPage.test.tsx src/pages/RegisterPage.test.tsx`

  Expected: PASS。

## Task 6：重构 Dashboard、密钥、日志、钱包与设置页面

**Files:**

- Modify: `frontend/src/pages/DashboardPage.tsx`
- Modify: `frontend/src/pages/DashboardPage.css`
- Modify: `frontend/src/pages/ApiKeysPage.tsx`
- Modify: `frontend/src/pages/ApiKeysPage.css`
- Modify: `frontend/src/pages/RequestLogsPage.tsx`
- Modify: `frontend/src/pages/RequestLogsPage.css`
- Modify: `frontend/src/pages/WalletPage.tsx`
- Modify: `frontend/src/pages/WalletPage.css`
- Modify: `frontend/src/pages/SettingsPage.tsx`
- Modify: `frontend/src/pages/SettingsPage.css`
- Modify: 现有 Dashboard/API Key/Request Logs/Wallet/Settings 测试

**Interfaces:**

- Consumes: 现有 features hooks、DataTable、Pagination、Dialog、FormField、Toast 以及 Task 2 PageHeader/Surface。
- Produces: 统一的控制台标题、工具栏、指标/表格表面和局部状态布局；所有现有 mutation、确认和缓存失效 API 不变。

- [ ] **Step 1: 为每页添加结构和状态测试**

  Dashboard 断言局部余额/摘要/趋势状态互不覆盖；API Key 断言搜索清除、状态筛选、创建和风险 Dialog；日志页断言筛选、表格和分页；钱包/设置断言表单、错误、重试和真实空态仍可访问。

- [ ] **Step 2: 运行领域测试确认失败**

  Run: `cd frontend && npm run test -- src/pages/DashboardPage.test.tsx src/pages/ApiKeysPage.test.tsx src/pages/RequestLogsPage.test.tsx src/pages/WalletPage.test.tsx src/pages/SettingsPage.test.tsx`

  Expected: FAIL，直到各页采用新的共享结构。

- [ ] **Step 3: 实现控制台页面组合**

  每页以 `PageHeader` 开始；数据区域使用业务命名的 `Surface` 变体。Dashboard 只在真实趋势数据存在时绘制图表/摘要；API Key、日志与钱包的表格保留 `DataTable`/`.table-scroll` 的内部滚动；设置表单保留修改密码、重新读取和请求标识反馈；删除、停用、reveal 继续使用原 Dialog。

- [ ] **Step 4: 校验小屏与交互状态**

  在页面 CSS 中为窄屏将工具栏换行、指标卡变单列/双列、表格保持内部滚动；禁止在页面容器添加 `100vh` 或 `overflow: hidden`。为 loading、empty、error、disabled/busy 保留与成功状态兼容的高度。

- [ ] **Step 5: 运行控制台领域测试**

  Run: `cd frontend && npm run test -- src/pages/DashboardPage.test.tsx src/pages/ApiKeysPage.test.tsx src/pages/ApiKeysPageA11y.test.tsx src/pages/ApiKeysPageDialogs.test.tsx src/pages/ApiKeysPageStatusDelete.test.tsx src/pages/RequestLogsPage.test.tsx src/pages/WalletPage.test.tsx src/pages/SettingsPage.test.tsx`

  Expected: PASS。

## Task 7：全量核验与浏览器可视验证

**Files:**

- Modify: `frontend/e2e/desktop.spec.ts`（如现有断言未覆盖新首页/控制台结构）
- Modify: `frontend/e2e/mobile.spec.ts`（如现有断言未覆盖移动导航、筛选或表格）
- Modify: `frontend/e2e/language.spec.ts`（如新文案增加翻译键）

**Interfaces:**

- Consumes: Tasks 1–6 的构建产物与既有 mocked Portal API。
- Produces: 可重复的桌面、320px、键盘和中英文回归证据。

- [ ] **Step 1: 检查 E2E 当前覆盖并补充断言**

  增加首页 B2 主行动和代码窗口可见性、Console 侧栏当前项、API Key 搜索/移动抽屉、中文/英文切换后的可访问标签断言；测试不得依赖示例余额或固定模型名称。

- [ ] **Step 2: 运行静态检查与单元测试**

  Run: `cd frontend && npm run lint && npm run test && npm run brand-scan && npm run build`

  Expected: 全部 PASS。

- [ ] **Step 3: 运行浏览器测试**

  Run: `cd frontend && npm run test:e2e`

  Expected: desktop、mobile-320 与 language 项目全部 PASS。

- [ ] **Step 4: 人工浏览器核验**

  使用本地 preview 与 mock/开发后端检查：首页、模型空态/加载/错误、文档无 Base URL、登录失败、API Key 删除确认、请求日志空态、钱包和设置错误态；检查桌面、320px、键盘焦点、减少动效和滚动条。记录实际结果，不将静态审计当作运行时验证。

- [ ] **Step 5: 检查未提交改动**

  Run: `git diff --check && git status --short`

  Expected: 无空白错误；所有本次改动保持未提交，等待用户决定提交或发布。
