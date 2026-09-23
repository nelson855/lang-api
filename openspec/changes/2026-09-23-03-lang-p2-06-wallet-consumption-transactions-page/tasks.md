## 1. P2-05 前端 API 契约

- [ ] 1.1 先为消费汇总与统一流水 strict schema 编写失败/成功测试，覆盖 range、availability/reasonCode、coverage、方向/单位/币种/状态组合、非负十进制、可空字段、分页和未知字段拒绝
- [ ] 1.2 实现 Portal 自有的消费汇总与统一流水 schema/type，保证 `AVAILABLE` 值、不可用原因、`CURRENCY` 币种和 `QUOTA` 空币种等条件约束在 API 边界收敛
- [ ] 1.3 先为两个 P2-05 URL 构造器和 fetch 函数编写测试，覆盖四个范围参数、可选单值 type、固定 20 条分页、非法/深分页拒绝、相对路径与 AbortSignal 透传
- [ ] 1.4 实现 `fetchConsumptionSummary` 与 `fetchTransactions`，确认不接受 userId、baseline、客户端多类型、未知参数或任何上游原始字段

## 2. 共享时间范围与钱包 URL 状态

- [ ] 2.1 锁定现有 Dashboard 范围/DST 测试，并补充共享内核测试，覆盖秒级截断、UTC 日界、IANA 校验和 `America/New_York` 春秋夏令时 23/25 小时
- [ ] 2.2 提取中性聚合范围内核并保留 Dashboard 原导出兼容，新增钱包 24H/今天/昨天/7D/30D preset 定义及 30D disabled 能力标记
- [ ] 2.3 先为钱包 URL parse/normalize/serialize 编写测试，覆盖完整范围恢复、默认 24H、ALL/page=1、类型/范围切换重置页码、翻页保留筛选、未知参数、单边时间、非法时区/枚举/页码、preset 冲突和 30D 直达
- [ ] 2.4 实现 URL 作为已提交状态唯一来源的适配层：首次进入用单次时钟快照 replace 规范化，刷新/前进/后退复用原边界，响应 total 收敛越界页且规范化完成前不发送请求

## 3. 查询键、取消与会话隔离

- [ ] 3.1 先为 summary/transactions 查询键编写测试，确认键包含用户、完整范围、类型与分页，不同用户/范围/筛选不共享缓存且同一语义键稳定
- [ ] 3.2 实现两个 TanStack Query hook，传递 AbortSignal、禁用自动重试、并行独立请求，并在 30D 或 URL 尚未规范化时保持 disabled
- [ ] 3.3 增加快速切换范围、类型和页码的测试，确认取消或忽略迟到响应，新筛选从第 1 页开始且旧范围/旧页数据不覆盖当前界面
- [ ] 3.4 扩展钱包会话失效清理，增加任一只读请求 401 时清除认证、余额、wallet、summary 与 transactions 用户作用域缓存的测试；普通区域失败不得清除其他成功数据

## 4. 精度安全展示模型

- [ ] 4.1 先为消费汇总展示映射编写测试，覆盖真实零、quota、正式货币不可用、缺稳定引用的部分覆盖和未知/矛盾状态安全失败
- [ ] 4.2 先为流水展示映射编写测试，覆盖三种类型、收入/支出文字、五种状态、QUOTA/CURRENCY、可空备注/引用和全部/单类型 coverage 状态
- [ ] 4.3 实现不经 JavaScript `number` 的十进制字符串本地化与币种/单位格式化，并用超安全整数、长小数、zh-CN/en-US 和非法输入测试证明不截断、不补算 USD
- [ ] 4.4 实现按活动 locale 与响应 timezone 格式化时间、保留原始 ISO/技术标识的纯展示模型，确保界面不直接输出枚举、reasonCode、`null` 或原始错误

## 5. 钱包页面信息架构与状态

- [ ] 5.1 先更新钱包页面测试 fixture，使余额、消费汇总、统一流水、充值能力和旧充值记录五类请求可独立成功/失败，并验证每个区域只消费对应接口
- [ ] 5.2 将页面重构为标题说明、当前余额、范围工具栏与消费汇总、类型工具栏与统一流水、充值关闭说明与独立充值记录的自然滚动结构；用 Clear Circuit 既有令牌实现克制 ledger rail，不修改全局视觉令牌
- [ ] 5.3 增加范围和类型筛选交互测试，确认 URL、summary/transactions 同范围、页码重置、重试不重建边界、30D 可发现且不发送聚合请求
- [ ] 5.4 增加消费汇总状态测试并实现 record/quota/正式金额/coverage 展示，明确区分当前余额 USD、范围消费 quota、真实零、部分覆盖和金额不可用
- [ ] 5.5 增加 unified transactions 的 AVAILABLE/PARTIAL/UNAVAILABLE、真实空、TOPUP/REFUND 不可用和缺引用差异测试，确保 items 数量不替代 coverage 判断且旧充值记录从不混入
- [ ] 5.6 实现统一流水桌面语义表格、窄屏等价卡片和独立 Pagination，完整展示时间、类型、方向、金额单位、状态、备注、来源；处理越界页、加载、失败、重试和稳定布局
- [ ] 5.7 保留第一阶段余额、充值关闭说明、旧充值记录与其独立分页/错误状态，增加 options 失败和旧记录非空但 TOPUP coverage 不可用的测试，确认页面始终没有金额输入、支付按钮、退款、提现或其他写控件

## 6. 本地化、可访问性与 UI 契约

- [ ] 6.1 补齐 `zh-CN` 与 `en-US` 的范围、类型、方向、状态、单位、coverage 原因、页面性质、30D、空/部分/不可用/错误文案，并保持两种语言键集合与插值参数一致
- [ ] 6.2 增加钱包可访问性测试，覆盖标题/section 层级、具名控件、status/alert/note、键盘筛选与分页、非颜色语义、长来源引用、减少动效和焦点不被加载状态遮挡
- [ ] 6.3 修正 `UX-CONTRACT.md` 与 `premium-ui.json` 中已过时的 Select/Listbox native owner，使其与共享 Radix `Select` 运行时一致；验证触发器/弹层几何、键盘、Escape、disabled 30D、碰撞与窄屏打开态，不创建页面专属下拉
- [ ] 6.4 增加或扩展 Playwright 状态矩阵，覆盖 URL 刷新/前进后退、快速筛选、桌面、窄屏、200% 缩放、zh-CN/en-US、真实空、部分覆盖、来源不可用、网络失败、401、30D 与充值关闭

## 7. 文档与阶段状态

- [ ] 7.1 新增 `docs/15_LANG-P2-06-钱包消费分析与统一流水页面说明.md`，记录页面结构、URL 参数、preset、接口归属、availability/coverage 展示、响应式、失败恢复和安全边界
- [ ] 7.2 更新 `docs/11_第二阶段聚合能力开发与子需求拆分.md` 的 P2-06 实际结果；若 USD、真实 TOPUP/REFUND 或 30D 仍受 P2-01/P2-05 外部证据阻塞，状态 MUST 保持“受阻”并分别列出已完成前端范围与剩余缺口
- [ ] 7.3 核对设计文档、页面说明、主规范和实际 UI 的术语一致性，确保不出现银行账单/发票承诺、前端换算、旧充值合并或能力已完整覆盖的错误描述

## 8. 验证与交付核对

- [ ] 8.1 运行钱包 API、共享范围/DST、URL、查询键/取消、展示模型、页面、本地化和可访问性定向 Vitest，修复所有失败
- [ ] 8.2 运行 `cd frontend && ./node/npm test`、`./node/npm run lint`、`./node/npm run build` 和 `./node/npm run test:e2e`，记录准确结果
- [ ] 8.3 先复现并妥善处理 strict premium audit 当前落在既有 Button/FormField 测试 fixture 的 11 条基线 finding（不得降低生产检查强度），再运行 `python3 /Users/nelson/.codex/plugins/cache/openai-curated-remote/frontend-design-premium/1.4.0/skills/frontend-design-premium/scripts/audit_project.py . --mode strict --no-write`；同时检查变更代码不存在 native dialog、非语义点击、页面级重复 Select、隐藏滚动条、未取消请求或硬编码敏感/高基数值
- [ ] 8.4 使用真实浏览器核对共享 Select 打开态、键盘、桌面/窄屏/200% 缩放、长内容、慢速/失败/空/部分/不可用、语言切换、前进后退、减少动效与充值关闭状态，保存必要证据
- [ ] 8.5 使用 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml verify` 运行根工程集成验证，确认前端产物仍能被整体构建消费
- [ ] 8.6 运行 `openspec validate 2026-09-23-03-lang-p2-06-wallet-consumption-transactions-page --strict`、`git diff --check`、敏感信息/残留占位扫描和 `git status --short`；只勾选已实现且通过适用验证的任务，不提交或发布改动
