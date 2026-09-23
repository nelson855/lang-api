## Context

见 `proposal.md` 的动机和四份 delta spec 的外部行为约束。

当前 `DashboardPage` 同时请求余额、`/portal/api/usage/summary` 和 `/portal/api/usage/timeseries`，范围只有 24H/7D/30D，时间通过固定小时数相减，页面也没有消费 P2-03 的 `availability/reasonCode`。P2-03 已交付单一 `/portal/api/dashboard/stats` 响应，但在 `p2-2026-09-22-a` 下只有 Token、活跃 Key、平均延迟和部分最近请求有可信数据；请求总数、消费、成功率与双趋势不可用，实时范围仍限制为 7 天。

前端现有技术边界是 React 19、React Router、TanStack Query、Zod、i18next、Radix Select 与 Clear Circuit 共享组件。`DESIGN.md` 要求控制台保持明亮工程化、数据优先和克制视觉，`UX-CONTRACT.md` 已把 Select、DataTable、分页、反馈和会话缓存清理的 canonical owner 固定下来。现有依赖中没有图表或时区库，本变更不需要改动持久化数据、后端配置或 Portal API。

## Goals / Non-Goals

**Goals:**

- 用可独立测试的 API schema、范围值和查询 hook，把 Dashboard 正式数据源迁移为 P2-03 单快照接口。
- 让“值为零”“没有数据”“来源未验证”“字段缺失”和“部分覆盖”在视图模型与界面上保持不同语义。
- 用同一范围对象驱动请求、范围说明、趋势横轴和日志下钻，避免时间边界漂移。
- 在不引入新运行时依赖的前提下提供可访问、响应式、与现有 Clear Circuit 一致的指标、趋势和最近请求布局。
- 保持旧用量接口兼容、余额独立加载、用户缓存隔离和统一会话失效。

**Non-Goals:**

- 不改变 P2-03 DTO、统计口径、7 天扫描门禁或 30 天后端能力。
- 不在前端补算请求数、费用、成功率、趋势或自然日空桶。
- 不增加自定义任意时间选择器、高级分析、导出、自动轮询或实时推送。
- 不重做控制台壳、全局设计令牌、钱包或完整请求日志视觉体系。
- 不移除第一阶段 `/portal/api/usage/**` 代码；只有 Dashboard 停止调用它们。

## Decisions

### 1. 为 Dashboard 建立独立的契约层和用户作用域查询键

新增 Dashboard API 模块，严格校验以下层次：

- 查询：`startTime`、`endTime`、`granularity`、`timezone`；
- 响应元数据：`baselineVersion` 与服务端回显 `range`；
- 六类指标：计数、金额、比率和平均值保留各自实际 JSON 类型；
- 双趋势：可用性、原因、单位/币种和点；
- 最近请求：部分可用集合及白名单字段。

schema 通过 refinement 拒绝“AVAILABLE 但无值”“UNAVAILABLE 但带值”“PARTIAL 用在单指标”等矛盾组合。查询键使用独立 `['portal','dashboard',userId,start,end,granularity,timezone]` 命名空间，查询函数传递 TanStack Query 的 `AbortSignal`，禁止自动重试。认证清理函数同时移除 dashboard key，防止换用户后复用旧快照。

余额继续复用现有 account balance 查询，不合并进 stats hook。这样余额失败和统计失败仍可隔离，同时 stats 内部所有板块来自同一服务端快照。

**替代方案：** 扩展现有 usage schema/hook。放弃该方案，因为旧接口的 DTO、默认范围和可用性语义不同，混用会让缓存键和类型继续暗示两套数据可以互补。

### 2. 快捷范围先形成不可变查询值，再触发请求

建立纯函数 `buildDashboardRange(preset, now, timezone)`，输入显式时钟和 IANA 时区，输出秒级 ISO 边界、粒度、时区和用于界面显示的 preset。范围只在首次进入页面或用户切换快捷项时生成一次；重渲染、语言切换和重试复用同一对象，避免起止时间在多个组件中分别调用 `Date.now()`。

映射固定为：

| 快捷项 | 边界 | 粒度 | 当前可请求 |
|---|---|---|---|
| 1H | `now - 1h` 至 `now` | `FIVE_MINUTES` | 是 |
| 24H | `now - 24h` 至 `now` | `HOUR` | 是 |
| 今天 | 用户时区当天 00:00 至 `now` | `HOUR` | 是 |
| 昨天 | 用户时区前一天 00:00 至当天 00:00 | `HOUR` | 是 |
| 7D | `now - 7d` 至 `now` | `HOUR` | 是 |
| 30D | `now - 30d` 至 `now` | `DAY` | 否，当前保护边界下禁用 |

滚动小时/天按绝对时长计算；自然日使用 `Intl.DateTimeFormat(..., {timeZone})` 的日期分量与偏移解析计算真实瞬时边界，不能用 `24 * 60 * 60 * 1000` 推导昨天。实现必须以跨 UTC 日界和 `America/New_York` 春秋夏令时用例验证 23/25 小时自然日。浏览器无法返回有效 IANA 名称时显式使用 `UTC`。

30D 作为 disabled Select item 保持可发现性，并在控件旁提供可被辅助技术读取的说明。能力开放条件属于后端基线，不从错误文本猜测；后续只需把 capability 常量改为可用，无需改变范围形状。

**替代方案：** 让后端接受 `7D`/`今天` 等 preset。该方案违反 P2-03 四参数契约，也会把浏览器时区和产品快捷语义重新扩散到服务端。

### 3. 先把服务端状态映射为展示模型，组件不直接猜测 null

增加小型纯函数层，将每项 metric/collection 映射为以下展示状态：

- `value`：格式化后的真实值、单位/币种和辅助口径；
- `zero`：仍是 value，只在文案和视觉上保持正常数据语义；
- `no-data`：只对应 `NO_DATA`，说明范围内没有可计算分母；
- `unavailable`：按 `BASELINE_NOT_VERIFIED` 或 `SOURCE_FIELD_MISSING` 给出稳定本地化说明；
- `partial`：只用于最近请求集合，并显示不遮挡条目的提示。

成功率把 0–1 比率格式化为 locale 百分比；平均延迟按毫秒显示；计数和 Token 使用 locale 数字格式；金额必须同时有 currency 才进入货币格式化。未知或矛盾响应在 Zod 层失败，进入整个 stats 的可重试错误状态，而不是在卡片中静默降级。

**替代方案：** 在 JSX 中用 `value ?? 0` 和条件文案。放弃该方案，因为这正是会把“未采集”显示为零的主要风险，也难以完整测试 reasonCode。

### 4. 使用现有视觉系统组织四层信息，不重做 Dashboard 风格

页面根节点补齐 `dashboard-page`，保持自然文档滚动。信息层级为：

```text
标题 + 范围控件 + 时区/实际边界说明
┌ 当前余额（独立查询，不随范围变化） ┐
┌ 六项指标网格：3×2 桌面 / 2×3 平板 / 1 列窄屏 ┐
┌ 请求趋势 ┐  ┌ 消费趋势 ┐
┌ 最近请求（表格；窄屏为已有卡片模式） ┐
```

沿用 Canvas/Paper/Mist、细边框、Cobalt 焦点和既有圆角，不新增渐变、玻璃效果或大面积装饰。范围与 `baselineVersion` 使用等宽辅助文本形成“数据路由标记”，作为本页唯一轻量技术签名；它表达真实口径，不是装饰编号。

指标卡保持固定最小高度，加载、错误和不可用说明占用兼容几何。颜色只做辅助，状态同时有文字和图标/标签。最近请求复用语义表格密度与移动卡片惯例；每行使用明确的“查看日志”链接，不把整行 `div` 设为点击目标。

**替代方案：** 引入一套新的 Dashboard 卡片/主题。放弃该方案，因为现有 `DESIGN.md` 已冻结 Clear Circuit，并明确禁止为图表效果建立独立样式系统。

### 5. 趋势采用轻量 SVG，并始终提供文本替代

新增共享范围仅限 Dashboard 的 `TrendChart` 展示组件：输入已经由服务端排序的 bucket 和十进制字符串值，按容器 viewBox 绘制折线/柱形、轴标签和焦点点位。显示值保留原字符串，缩放计算才转换为有限 number；遇到非有限或超出安全绘图范围的值时不绘图并显示可重试契约错误，不能截断。

每张图包含可本地化的标题、范围摘要和屏幕阅读器说明，并提供可展开或视觉隐藏但语义可读的数据表；横轴标签通过响应范围和用户时区格式化，不能直接显示原始 UTC 字符串。`prefers-reduced-motion` 下不执行路径动画。不可用或空数据时不挂载 SVG，直接渲染对应状态面板。

**替代方案：** 增加图表库。当前只有两条简单时间序列且项目没有既有图表依赖，引入库会扩大 bundle、主题和无障碍适配面；轻量 SVG 足以覆盖固定点数和现有视觉语言。

### 6. 最近请求下钻使用可恢复 URL，而不是页面内隐式状态

Dashboard 使用服务端回显的 `range.startTime/endTime` 构造目标 URL，而不是使用可能与服务端规范化结果不同的本地边界。链接固定带 `page=1&result=SUCCESS`，只在值非空时增加 `keyName` 和 `model`，使用 URLSearchParams 编码；不传 requestId，因为现有日志接口不支持该筛选。

请求日志页建立 URL ↔ 规范化查询状态的单一适配层：

1. 首次渲染和浏览器导航时解析受支持参数；
2. 非法结果、页码或非成对/非法时间回退默认值并 replace 为规范化 URL；
3. 提交筛选重置 `page=1` 并 push 新 URL；
4. 翻页只更新 page；刷新只 refetch 当前 URL 状态；
5. Dashboard 传入的非预设明确范围在控件中显示“Dashboard 所选范围”，用户改选快捷项后再替换为新边界。

TanStack Query key 继续包含全部规范化条件，路由快速变化时由 signal 取消旧请求，避免迟到响应覆盖新筛选。

**替代方案：** 只通过 React Router location state 传递。该状态刷新后丢失，也无法复制链接或使用浏览器前进后退恢复，不满足闭环要求。

### 7. 错误、缓存与本地化遵循现有共享行为

stats 查询失败时以一个统计区域错误面板替代指标、趋势和最近请求，避免把旧范围的局部数据与新范围混合；余额仍独立保留。成功响应中的 unavailable/partial 是业务状态，不走 error UI。所有 `UNAUTHENTICATED` 复用统一认证清理和登录跳转，不在 Dashboard 自建提示。

新增文案全部进入 `pages.dashboard` 与 `pages.requestLogs` 的 `zh-CN`/`en-US` 同构资源，包括六指标、单位辅助、reasonCode、部分覆盖、30D 阻塞、图表/数据表说明和日志下钻。日期、数字、百分比和货币使用活动 locale，技术标识、模型名和 requestId 保留原文。切换语言不重建范围或重新请求，仅重新格式化同一响应。

## Risks / Trade-offs

- **[当前真实响应大部分不可用，容易只测到状态面板]** → 用严格 fixture 同时覆盖未来 AVAILABLE 趋势、当前 UNAVAILABLE、零值、NO_DATA、字段缺失和 PARTIAL；页面实现不能只针对当前基线硬编码。
- **[30D 出现在需求中但服务端明确拒绝]** → 保持可发现但禁用并解释，任务与阶段状态继续标记外部阻塞；只有后端基线和性能证据变更后才开放，不捕获通用 400 作为能力探测。
- **[浏览器时区自然日换算在 DST 边界出错]** → 时间逻辑集中为纯函数，显式传入 clock/timezone，以 23/25 小时、UTC 日界和相邻区间测试为发布门禁。
- **[严格 schema 遇到服务端新增枚举时整页失败]** → 这是有意的契约保护；新增 reasonCode/availability 必须先同步前端文案与行为，避免未知金融或统计状态被误显示。
- **[轻量 SVG 的图表能力有限]** → 第二阶段只支持两条固定时间序列和基础 tooltip/数据表，不承诺缩放、刷选、多轴或高级分析；若后续需求扩大，再以独立决策引入图表库。
- **[URL 中模型或 Key 名称较长]** → 只放现有非敏感筛选字段并正确编码，不放 token、完整 API Key 或内部 ID；服务端仍执行用户隔离和参数校验。
- **[统一 stats 失败会同时失去多个板块]** → 这是同快照一致性的必要取舍；保留独立余额和单次重试，不使用旧接口形成口径混合。

## Migration Plan

1. 先交付纯契约层：Dashboard schema、URL 构造、范围生成、展示状态映射和针对 P2-03 fixture 的测试，不改页面调用。
2. 增加 dashboard query hook、用户作用域缓存清理和会话失效测试，再把 Dashboard 从两个旧 usage 查询切换到一个 stats 查询；余额查询保持原样。
3. 分区落地六指标、趋势和最近请求 UI，并补齐当前不可用状态、30D disabled 说明、中英文与响应式/可访问性测试。
4. 最后接入日志下钻 URL 与请求日志 URL 状态同步，验证刷新、前进后退、非法参数和分页。
5. 运行前端 lint、全量测试、生产构建、相关 Playwright 流程、设计系统静态检查和 OpenSpec 严格校验；更新第二阶段状态时继续保留 P2-01/P2-03 外部阻塞。

部署不需要数据库或后端配置迁移。回滚只需恢复上一版前端 bundle；P2-03 新接口和第一阶段旧接口均继续存在，因此回滚不会改变服务端数据或契约。
