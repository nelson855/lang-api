## 1. 设计与行为合同

- [ ] 1.1 清点全部现有路由、共享组件、页面局部样式和表单/Select/date/scrollbar/Toast/CRUD 所有者，记录需要迁移或删除的旧实现，并确认没有更近层级的前端规范文件
- [x] 1.2 使用 Premium 模板维护项目根级 `DESIGN.md`，完整记录 Clear Circuit North Star、反向参考、六个核心颜色、字体、密度、间距、圆角、层级、图标、动效、数据可视化、内容语气和组件状态
- [x] 1.3 在 `DESIGN.md` 中声明 `tokens.css` 为运行时令牌权威来源，建立 `DESIGN.md → tokens.css → Tailwind @theme → 共享组件` 映射，并运行 `designmd lint` 修复全部错误
- [ ] 1.4 根据现有主规格、API 契约和测试创建项目根级 `UX-CONTRACT.md`，固化导航、状态、表单、Toast、Select/date 所有权、分页、风险确认、敏感值清理、会话失效、失败恢复和焦点结果
- [x] 1.5 创建 `premium-ui.json`，声明 product/admin profile、源码根、Canonical UI Map、适用能力、项目验证命令和现有行为证据路径
- [ ] 1.6 在 `frontend/package.json` 与锁文件中新增维护中的 Radix Select/Listbox 依赖，确认不引入其他 UI、动画、图表、字体或图片依赖

## 2. Clear Circuit 令牌与全局基础

- [ ] 2.1 先扩充令牌测试，覆盖 Clear Circuit 核心色、语义色、文字/控件 WCAG 2.2 AA 对比、圆角、层级、动效和禁止页面硬编码颜色的约束
- [ ] 2.2 重构 `frontend/src/design/tokens.css` 的 primitive、semantic、component 与 Tailwind `@theme` 映射，实现 Canvas/Paper/Mist/Graphite/Slate/Cobalt 及 info/success/warning/danger 独立语义
- [x] 2.3 重构 `global.css` 的字体回退、排版比例、内容容器、焦点、选区、链接、页面背景和减少动效规则，确保加载时不出现旧深色主题闪现
- [x] 2.4 在全局应用样式实现可见的浅色滚动条 baseline，覆盖标准属性、WebKit fallback、hover/active、稳定 gutter 和 forced-colors，不依赖容器 opt-in 类
- [ ] 2.5 建立统一 z-index 令牌和 overlay 层级，覆盖 sticky、dropdown、popover、header、backdrop、dialog、drawer 与 toast，并移除页面局部任意大数值

## 3. 共享组件体系

- [ ] 3.1 先为 Button/IconButton 增加 intent、emphasis、size、hover、focus、active、disabled、busy 和稳定尺寸测试，再实现新 API 与临时旧 variant 兼容映射
- [ ] 3.2 先为 Input、FormField、Textarea 的标签激活、错误关联、只读/禁用、帮助文本、标准/紧凑密度和 `resize: none` 增加测试，再统一实现 Clear Circuit 表单样式
- [ ] 3.3 先编写 PasswordField/SecretField 的默认遮蔽、显示/隐藏、动态可访问名称、焦点/值保持和 autocomplete 测试，再实现共享组件并迁移密码输入
- [ ] 3.4 先编写 SearchField 的非空清除按钮、焦点恢复、本地即时筛选、显式远程提交兼容和 IME 安全测试，再实现共享组件并保持现有查询时机
- [ ] 3.5 先编写 Select/Listbox 的值映射、键盘、Escape、焦点恢复、禁用、长选项、弹层宽度与碰撞测试，再基于 Radix 实现共享浅色 Select
- [ ] 3.6 明确 `datetime-local` 继续由平台拥有弹层，为共享日期时间 Input 补充标签、值传递、主题适配和支持平台浏览器测试，不改变既有 ISO 转换语义
- [ ] 3.7 先扩充 Dialog/AlertDialog/Drawer 测试，覆盖焦点、Escape、背景 inert、滚动锁、长内容、pending 失败恢复和窄屏边界，再统一浅色 overlay 实现
- [ ] 3.8 先扩充 Toast 与 Loading/Empty/NoResults/RetryableError/Forbidden/NotFound 状态测试，覆盖语义、去重、稳定几何、requestId 和可访问 live region，再迁移视觉
- [ ] 3.9 先扩充 DataTable 与 Pagination 测试，覆盖标题/表头语义、空状态、边界页、当前页、稳定禁用几何、内部溢出和窄屏表示，再统一数据区域样式
- [ ] 3.10 重构 CodeBlock、StatusBadge、链接、徽标和复制反馈，使技术信息统一使用等宽字体且状态不只依赖颜色

## 4. 应用壳与品牌导航

- [x] 4.1 先更新 PublicLayout 测试，覆盖真实导航、注册门禁、语言入口、移动抽屉、页脚和路由焦点，再实现 Clear Circuit 公开头部、宽松内容容器与结构化页脚
- [x] 4.2 先更新 AuthLayout 测试，覆盖返回首页、法律入口、长错误和窄屏，再实现聚焦账户操作的认证面板且不新增虚假认证方式
- [x] 4.3 先更新 ConsoleLayout 测试，覆盖当前路由、用户身份、退出 pending、桌面侧栏、顶部上下文和移动抽屉，再实现灰白画布与白色工作面的控制台壳
- [ ] 4.4 更新 Brand 资产与 favicon 在 Clear Circuit 背景上的前景与对比规则，保留运行时站点名、文本替代和集中替换入口
- [ ] 4.5 验证三类布局在 320px、常见桌面和 200% 缩放下没有页面级横向溢出，导航焦点不被 sticky 区域遮挡且抽屉正确恢复焦点

## 5. 认证、设置与 API 密钥高风险流程

- [ ] 5.1 先更新登录与注册组件测试，覆盖 PasswordField、提交 busy、通用凭据错误、注册开关、法律入口和中英文长文案，再迁移页面结构与样式
- [ ] 5.2 先更新个人设置测试，覆盖只读邮箱、三组密码字段、校验、首错聚焦、服务器错误恢复和重复提交，再迁移为分区表单布局
- [ ] 5.3 先更新 API Key 搜索/筛选测试，覆盖 SearchField 清除、Radix Select、分页、首次空数据、无结果和结果未知，再迁移列表工具栏与数据区域
- [ ] 5.4 先更新 API Key 创建/编辑表单测试，覆盖 quota、原生过期时间、高级限制、Textarea、错误关联、值保持和重复提交，再迁移表单与响应式对话框
- [ ] 5.5 先更新 API Key reveal/复制/启停/删除测试，确保目标名称、危险语义、least-destructive focus、pending 内留、失败重试和明文清理行为不变，再迁移 AlertDialog 视觉
- [ ] 5.6 用真实浏览器完成登录、注册、资料修改、API Key 创建/编辑/reveal/复制/启停/删除的键盘与失败路径验证

## 6. 控制台数据页面

- [ ] 6.1 先更新 Dashboard 测试，保持余额、摘要、趋势独立状态与现有字段，再实现标题区、范围 Select、指标面板和不虚构维度的轻量趋势可视化
- [ ] 6.2 先更新请求日志测试，保持显式查询、刷新、服务端分页、局部错误和会话失效，再迁移筛选工具栏、桌面表格与窄屏记录布局
- [ ] 6.3 先更新钱包测试，保持余额、充值关闭说明、记录分页与局部失败隔离，再迁移余额面板、能力说明和充值记录数据区域
- [ ] 6.4 验证控制台表格、对话框和长表单具有单一明确滚动所有者，10/20/50 行及短视口不会把表单裁切或制造竞争纵向滚动条
- [ ] 6.5 验证 Dashboard、请求日志和钱包在初始加载、后台刷新、空数据、局部错误、重试、结果未知及会话失效时保持稳定几何和安全反馈

## 7. 公开首页与内容页面

- [ ] 7.1 先为首页增加 public-config、认证状态和模型摘要的成功/空/失败/PREVIEW/无协议测试，再实现中文价值主张与右侧快速接入终端的 B2 首屏
- [ ] 7.2 实现首页真实协议摘要、真实模型摘要、三步接入和最终行动区域，确保 Base URL 与模型只来自受验证运行时数据且不出现虚构请求、性能或业务指标
- [ ] 7.3 先更新模型广场测试，覆盖 SearchField、Radix provider Select、真实价格、空目录、无结果和前往文档参数，再迁移模型列表/卡片视觉
- [ ] 7.4 先更新开发文档与 CodeBlock 测试，覆盖真实 Base URL、模型参数、复制、协议未开放和长代码横向滚动，再实现双栏文档/内容导航布局
- [ ] 7.5 更新服务地区页面测试与视觉，只展示 public-config 真实地区、未发布状态和支持入口，不推导合规、付款或数据驻留承诺
- [ ] 7.6 更新用户协议、隐私政策和法律内容组件的浅色长文阅读排版，验证标题层级、正文宽度、代码/表格溢出、正文 locale 和未发布/失败状态
- [ ] 7.7 更新 403、404、RouteError、PublicConfigGate 启动加载/失败和其他通用状态，使其保留适用应用壳、诚实标题和明确恢复入口

## 8. 国际化与内容一致性

- [ ] 8.1 重写首页、布局、状态和新增共享组件的 `zh-CN` 文案，使核心主张与操作使用自然中文，仅在品牌、协议、模型、代码和必要技术标签保留英文
- [ ] 8.2 同步补齐 `en-US` 独立资源、可访问名称和插值参数，保持中英文键集合完全一致且不改变运行时 enabledLocales 规则
- [ ] 8.3 增加混合脚本、长站点名、长模型名、长错误和按钮文案测试，确认中文与英文切换不移动关键操作、不截断重要说明且文档标题同步更新
- [ ] 8.4 运行品牌与文案扫描，确认源码、构建产物、Meta、错误和示例中不残留 QinghuaAPI、New API 私网标识、虚构指标或非必要英文营销主标题

## 9. 清理、文档与完整验证

- [ ] 9.1 删除旧深色令牌、页面局部基础控件样式、已无调用的 CSS 类和 Button 兼容别名，确认所有现有路由均只使用 Clear Circuit 主题与共享组件
- [x] 9.2 更新 `docs/09_前端开发说明.md`，记录 Clear Circuit 令牌、DESIGN/UX 合同、共享组件 API、Select/date 所有权、布局规则和验证命令
- [x] 9.3 运行 `designmd lint`、令牌/硬编码守卫、品牌扫描及 Premium `audit_project.py --mode strict`，修复所有阻塞发现并保留静态结果
- [ ] 9.4 对变更代码执行 anti-pattern 搜索，确保没有原生 alert/confirm/prompt、非语义点击目标、无清除搜索、IME 风险、无 reveal 密码、无 `noValidate` 表单、裸 Select、WebKit-only 滚动条或表格高度泄漏
- [x] 9.5 运行 `npm run lint`、`npm run test`、`npm run build` 和 `npm run test:e2e`，修复失败并记录实际命令结果
- [ ] 9.6 在真实浏览器完成中文/英文、桌面、320px、200% 缩放、键盘、触摸/no-hover、Select 打开、原生日期时间弹层、减少动效、forced-colors、慢网和长内容矩阵
- [x] 9.7 运行 `openspec validate 2026-09-15-lang-p1-12-frontend-visual-redesign --strict`，确认所有已完成任务立即标记为 `[x]`，报告未解决风险与最终 `git status`，保留改动未提交且不发布
