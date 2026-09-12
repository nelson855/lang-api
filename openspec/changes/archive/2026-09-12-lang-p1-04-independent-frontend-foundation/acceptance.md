# LANG-P1-04 验收记录

验收日期：2026-09-12。以下逐条对照 4 个 delta spec 的场景，给出实际证据。

## frontend-design-system

- 独立视觉语言：`tokens.test.ts` 断言三层命名、圆角档、本地字体、无外部引用；`noHardcodedStyle` 守卫 + ESLint 保证组件层无散落颜色/阴影。产物 `dist` 无 Qinghua/New API 文案、无外部字体请求。结论：通过。
- 基础交互组件：Button/FormField/Dialog/DataTable/Pagination/Toast 的交互与可访问性测试通过；键盘打开/焦点限制/Escape/焦点恢复（Dialog、抽屉）通过；分页边界、表单关联、通知 live-region 与 requestId 通过。结论：通过。
- 通用页面状态：Loading/Empty/RetryableError（含 requestId 与重试）/Forbidden/NotFound 测试通过。结论：通过。
- 响应式与可访问性：容器 1180px、断点 768px、窄屏 16px、表格横向滚动、可见焦点、减少动效均有测试；代表页面与复合组件 axe 无违规；320px 真机行为由 Playwright `mobile-320` 覆盖（抽屉、Escape、无横向滚动）。结论：通过（真机目视确认待用户启动应用后补）。

## frontend-application-shell

- 三类布局：Public/Auth/Console 布局测试通过（桌面导航、当前选中、运行时品牌、语言切换、最小页脚；认证壳无虚假登录方式；控制台仅展示已交付入口）。结论：通过。
- 响应式导航：移动抽屉打开/焦点限制/选中关闭/Escape/焦点落点测试通过。结论：通过。
- 第一批路由：路由元数据测试锁定 7 条路由的布局、访问级别与标题键；生产构建输出 7 个页面懒加载分包；深层刷新与站内导航由 Playwright 覆盖。结论：通过。
- 占位真实性：各占位页断言无伪造模型/价格/用户/余额/认证结果、无无行为表单。结论：通过。

## frontend-api-integration

- 统一客户端：路径白名单、结构化查询参数、fetch 契约（同源凭证、Accept、X-Request-Id、AbortSignal、不泄露原文）测试通过。结论：通过。
- 响应校验：成功/失败包、DTO、非 JSON、非法结构、requestId 提取（含响应头回退）测试通过；取消为独立错误类型。结论：通过。
- 服务端状态：Query 默认（不重试、不聚焦刷新、mutation 不重放）、public-config 固定 key 与手动 refetch、取消信号透传测试通过。结论：通过。
- 启动门禁：加载/成功/空地址/失败 requestId/手动重试测试通过；配置只读冻结，无示例域名回退。结论：通过。
- 鉴权守卫：四状态、returnTo 站内校验、默认匿名（不读 Cookie/localStorage/query 伪造）、登录/控制台双向守卫测试通过；非法返回地址回退 `/dashboard`。结论：通过。
- 边界扫描：源码与产物扫描无 New API 品牌、私网地址、原始管理路径、示例域名、外部字体、业务 `VITE_*` 地址与 sourcemap（测试内的否定断言除外）。结论：通过。

## frontend-localization

- 资源完整：中英键集合、插值参数、非空文案一致性测试通过；P1-04 必需键抽查通过。结论：通过。
- 选择与持久化：优先级与非法回退、持久化、无刷新切换、`<html lang>`、路由保持测试通过；浏览器英文偏好、切换、刷新持久由 Playwright 覆盖。结论：通过（文档标题同步由路由标题钩子覆盖，已测）。
- 安全格式化：日期/数字/金额/额度测试通过；缺币种或单位返回空状态；类 HTML 插值只显示为文本。结论：通过。

## 未验证条件与遗留风险

- 一体化 JAR 的启动验证（`/`、深层路由、public-config、未知 Portal API 边界）待用户启动应用后确认；`mvn verify` 已全绿。
- 真机目视核验（焦点可见性、对比度观感、减少动效、触控目标）待用户确认。
- Playwright 浏览器 `chromium-1243` 经镜像下载，未进入 Maven 默认链，仅 `npm run test:e2e` 使用。
