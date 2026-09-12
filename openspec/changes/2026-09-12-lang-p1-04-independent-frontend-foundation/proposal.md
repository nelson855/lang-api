## Why

LANG-P1-03 已建立稳定的 Portal API 与公开配置契约，但当前前端仍是只有标题和单一路由的工程占位页，无法承载后续认证、Key、模型、日志和钱包功能。现在需要先冻结独立的视觉语言、应用壳、路由、数据访问和基础交互组件，避免后续页面各自建立不一致的实现或直接依赖 New API。

## What Changes

- 建立可替换品牌的独立视觉体系，定义颜色、字体、间距、圆角、阴影、层级、动效和语义状态变量，并支持键盘焦点、减少动效和可读对比度。
- 建立公开站点、认证页面和登录后控制台三类布局，提供桌面导航、移动端导航、页面标题和一致的内容宽度规则。
- 建立声明式路由与懒加载边界，创建首页、模型广场、开发文档、登录、注册、控制台和前端 404 路由；未实现业务的页面明确显示占位状态，不伪造数据或操作成功。
- 建立可供 LANG-P1-05 接入的鉴权路由守卫协议，但本阶段不虚构登录状态，也不把尚无认证接口的控制台路由强制绑定到伪造会话。
- 引入 TanStack Query、统一 Portal API 客户端和运行时 DTO 校验，只允许相对 `/portal/api/*` 请求，并统一处理成功包装、错误包装、requestId、取消和不可解析响应。
- 在应用启动时读取 `/portal/api/public-config`，显示运行时 `siteName` 和已启用的公开 Base URL；加载失败时提供可重试错误状态，不回退到硬编码的虚假地址。
- 建立加载、空数据、错误、无权限和 404 状态，以及表单字段、按钮、输入框、对话框、表格、分页和通知等可复用基础组件。
- 建立中文、英文资源和语言切换基础设施；本次交付的全部界面文案同时具备完整中英文，不在组件中散落用户可见硬编码文本。
- 增加组件、路由、API 客户端、响应式导航、国际化和基础浏览器流程测试，并保持根 Maven 一体化构建入口有效。

## Capabilities

### New Capabilities

- `frontend-design-system`: 独立视觉变量、基础组件、通用页面状态、可访问性和响应式行为。
- `frontend-application-shell`: 公开站点、认证页、控制台布局、导航和阶段占位路由。
- `frontend-api-integration`: 统一 Portal API 客户端、TanStack Query Provider、公开配置加载和鉴权守卫协议。
- `frontend-localization`: 中英文资源、语言检测与切换、持久化和页面语言属性。

### Modified Capabilities

无。本变更消费既有 `public-portal-config` 与 `portal-api-contract`，不修改其服务端行为。

## Impact

- 主要影响 `frontend/package.json`、锁文件、Vite/测试配置和 `frontend/src` 下的应用入口、路由、页面、组件、API、设计变量与国际化目录。
- 新增并锁定 TanStack Query、React Hook Form、Zod、Tailwind CSS、Radix Headless 组件、i18next、图标与 Playwright 等前端依赖；不引入完整 UI 模板或带默认品牌主题的组件库。
- 继续复用现有 React 19、React Router 7、Vite 7、Vitest 和 Testing Library，正式构建仍由根 Maven 驱动并打入同一 JAR。
- 本阶段不修改 Portal API 业务实现或后端 `.properties` 配置；运行时站点名和公开地址完全读取已有 `/portal/api/public-config`。
