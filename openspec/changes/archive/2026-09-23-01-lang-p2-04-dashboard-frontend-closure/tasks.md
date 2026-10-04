## 1. Dashboard 契约与时间范围

- [x] 1.1 先为 P2-03 完整成功响应、当前部分可用响应、矛盾 availability/value 组合、未知枚举和非法趋势点编写 Zod 契约测试
- [x] 1.2 实现 Dashboard API schema、四参数 URL 构造与 `fetchDashboardStats`，确认只访问相对 `/portal/api/dashboard/stats` 并传递取消信号
- [x] 1.3 先为 1H、24H、今天、昨天、7D、30D 编写范围测试，覆盖秒级截断、IANA 时区、UTC 日界、`America/New_York` 春秋夏令时 23/25 小时和无效时区回退
- [x] 1.4 实现不可变 Dashboard 范围值与 preset 生成器，固定粒度映射并让 30D 在当前 capability 配置下可见但不可请求
- [x] 1.5 先覆盖真实零值、NO_DATA、BASELINE_NOT_VERIFIED、SOURCE_FIELD_MISSING、PARTIAL_SOURCE_COVERAGE、百分比/金额/毫秒格式化，再实现指标与集合展示模型

## 2. 查询、缓存与会话一致性

- [x] 2.1 先为 Dashboard 查询键编写测试，确认包含用户、起止时间、粒度和时区，不同用户或范围不共享缓存且同一范围键稳定
- [x] 2.2 实现 Dashboard TanStack Query hook，传递 AbortSignal、禁用自动重试，并保证 30D disabled 状态不会触发 HTTP 请求
- [x] 2.3 扩展认证作用域缓存清理并编写登录、退出、profile 401 和 stats 401 测试，确认 Dashboard 快照与既有 usage/request-log 缓存一并清除
- [x] 2.4 增加范围快速切换和请求取消测试，确认迟到响应不会覆盖新范围，语言切换与手动重试不会重新生成时间边界

## 3. Dashboard 页面闭环

- [x] 3.1 先改写 Dashboard 页面测试，断言余额继续独立查询、概览只调用一个 stats 接口、旧 summary/timeseries 不再被调用，以及任一 UNAUTHENTICATED 进入统一会话失效流程
- [x] 3.2 将页面重构为 `dashboard-page` 根容器、范围工具栏、独立余额、六指标网格、双趋势和最近请求四层结构，复用现有 Button、Select、Feedback、DataTable 与设计令牌
- [x] 3.3 为六指标逐项实现值、单位、真实零、无数据和不可用状态，显示服务端回显范围与 baselineVersion，并明确区分当前余额和区间消费
- [x] 3.4 先为 AVAILABLE 点列、UNAVAILABLE 空点、非法数值、用户时区标签、文本数据替代和 reduced-motion 编写测试，再实现轻量可访问 SVG 趋势组件
- [x] 3.5 先为最近请求稳定显示、空列表、部分覆盖、可空 Key/模型和安全下钻 URL 编写测试，再实现桌面表格、窄屏卡片与明确“查看日志”链接
- [x] 3.6 增加余额失败/统计成功、余额成功/统计失败、统计内部分不可用和单次重试测试，确认状态区域高度稳定且不混用旧范围数据
- [x] 3.7 增加 30D disabled 交互与说明测试，确认键盘和触摸用户能发现阻塞原因且不会产生 stats 请求或旧接口降级

## 4. 请求日志 URL 下钻

- [x] 4.1 先为 request-log URL 解析与序列化编写测试，覆盖合法 Dashboard 参数、URL 编码、缺少可选 Key/模型、非法 result/page、单边时间和未知参数
- [x] 4.2 实现 URL 与规范化筛选状态的单一适配层；首次进入、刷新和前进后退从 URL 恢复，非法状态使用 replace 规范化而不发送非法请求
- [x] 4.3 改造请求日志页，使提交筛选 push `page=1`、翻页只更新 page、刷新只 refetch 当前 URL 查询，并为非预设下钻范围显示“Dashboard 所选范围”
- [x] 4.4 增加 Dashboard 到请求日志的路由集成测试，确认服务端回显边界、SUCCESS、非空 Key/模型和第 1 页完整传递，浏览器返回后仍恢复先前 Dashboard

## 5. 本地化、响应式与可访问性

- [x] 5.1 补齐 `zh-CN` 与 `en-US` 的六指标、快捷范围、可用性原因、30D 阻塞、趋势、最近请求、下钻和明确范围文案，并保持资源键集合与插值参数一致
- [x] 5.2 使用活动 locale 和响应时区统一格式化日期、数字、百分比与货币；增加语言切换测试，确认不改变路由、范围对象或重新请求数据
- [x] 5.3 完成桌面 3×2、平板 2×3、窄屏单列布局及最近请求卡片样式，复用 Clear Circuit token，避免整页横向溢出、固定视口高度和新的屏幕级样式系统
- [x] 5.4 增加 Dashboard 与请求日志的语义、可访问名称、焦点、键盘、状态 live region、200% 缩放和窄屏测试，确认趋势有文本数据替代且不可用状态不只依赖颜色

## 6. 文档与阶段状态

- [x] 6.1 新增 LANG-P2-04 Dashboard 前端闭环说明并更新文档索引，记录 preset 映射、时区边界、可用性展示、趋势替代信息、最近请求下钻和错误行为
- [x] 6.2 更新 `docs/11_第二阶段聚合能力开发与子需求拆分.md` 的 P2-04 实际结果；若双趋势、三项指标或 30 天能力仍受 P2-01/P2-03 阻塞，状态 MUST 保持“受阻”并分别列出已完成前端范围与外部缺口
- [x] 6.3 核对 `DESIGN.md`、`UX-CONTRACT.md` 与运行时 token；只有出现经批准的持久设计决策时才同步更新，页面局部样式不得反向改写现有设计系统

## 7. 验证与交付记录

- [x] 7.1 运行 Dashboard API、范围/DST、展示模型、Dashboard 页面、请求日志 URL/页面、缓存清理、本地化和可访问性定向测试，修复所有新增失败
- [x] 7.2 在 `frontend` 运行全量 `npm run lint`、`npm run test`、`npm run build` 和相关 Playwright 流程，记录实际结果且不以静态检查替代浏览器状态验证
- [x] 7.3 运行前端设计静态审计、设计上下文检查和反模式扫描，实测桌面、窄屏、键盘、加载、错误、空/不可用、部分覆盖、语言切换和 reduced-motion 状态
- [ ] 7.4 使用 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml verify` 验证一体化构建，确认前端产物可被根工程正确打包
- [x] 7.5 运行 `openspec validate 2026-09-23-01-lang-p2-04-dashboard-frontend-closure --strict`、敏感信息检查和 `git diff --check`，逐项核对 delta spec 验收后只勾选已实际完成任务
- [x] 7.6 运行 `git status --short` 并报告所有未提交改动、测试结果、当前不可用指标/趋势和 30 天外部阻塞；不执行提交、推送、PR、发布或部署
