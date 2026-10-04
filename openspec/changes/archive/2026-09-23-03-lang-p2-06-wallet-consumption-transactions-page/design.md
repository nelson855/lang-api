## Context

参见 [proposal.md](proposal.md) 的动机。P2-05 已提供两个受保护的只读接口，并把当前基线限制明确编码为 `availability`、`reasonCode` 与逐来源 `coverage`。现有 `WalletPage` 只调用余额、充值能力和充值记录，三块数据彼此独立；前端使用 React 19、React Router、TanStack Query、Zod、i18next、共享 `Select`/`DataTable`/`Pagination`/`Feedback` 和 Clear Circuit 令牌。

`DESIGN.md` 规定控制台采用自然文档滚动、冷调画布、白色工作面、细边框和克制的 Cobalt 强调；`UX-CONTRACT.md` 要求数据区具备稳定 loading/error/empty 状态、URL 可恢复筛选、局部横向滚动和 WCAG 2.2 AA 基线。运行时共享 `Select` 已是 Radix authored listbox，但 `UX-CONTRACT.md` 与 `premium-ui.json` 仍写成 native，这是实施前必须消除的既有文档漂移，不是本页新建另一套下拉组件的理由。

高风险账务语义以 `account-balance`、`account-topups`、`account-consumption-transactions` 三份主规范和 `docs/14_LANG-P2-05-消费汇总与统一流水接口说明.md` 为权威来源。设计不得把余额 USD 与消费 quota 互相换算，也不得用旧充值记录提升 P2-05 的 `TOPUP` coverage。

## Goals / Non-Goals

**Goals:**

- 用单一、可恢复的范围状态同时驱动消费汇总和统一流水，并在快速切换与翻页时隔离迟到响应。
- 把服务端的可用性、覆盖率、单位、方向与状态转换为不会造成账务误解的本地化视图模型。
- 在一个自然滚动页面中建立“余额 → 范围消费 → 统一流水”的清晰主线，同时保留充值关闭说明与既有充值记录。
- 复用现有组件、令牌与状态模式，在桌面表格和窄屏卡片间保持信息等价。

**Non-Goals:**

- 不修改 P2-05、余额或充值 API，不新增后端配置、依赖、数据库或写操作。
- 不开放 30D、充值、退款、提现、导出、发票、调账或来源详情跳转。
- 不把旧充值记录并入统一流水，不在前端补算 USD、余额或 coverage。
- 不重做控制台壳、全局视觉身份或通用表格体系，也不为单页增加密度切换、复杂日期选择器或新图表。

## Decisions

### 1. 采用单页纵向账本主线，而不是标签页或替换旧充值记录

页面结构保持自然文档滚动：

```text
钱包标题 + 数据性质说明
┌ 当前余额（不随筛选变化） ┐
│        ↓ 细路由轨迹       │
├ 范围控件 + 范围内消费汇总 ┤
│        ↓ 同一范围          │
├ 类型控件 + 统一流水        ┤
└ 充值未开放说明 + 既有充值记录（独立来源） ┘
```

桌面端用一条克制的 Cobalt/Slate “ledger rail”连接前三个语义层，并以真实范围和口径文字作为节点；窄屏只保留纵向顺序和短分隔线。它延续 Clear Circuit 的路由图签名，同时承担“当前值、区间值、历史事件”信息关系，不引入渐变、玻璃效果或装饰性动画。所有静态区域继续使用既有背景、边框、圆角和字体令牌，不修改 `DESIGN.md`。

**替代方案 A：标签页分隔消费与充值。** 放弃，因为余额、范围汇总和流水需要同时建立认知关系，标签会隐藏 coverage，且两个分页面板仍易被误认为同一数据源。

**替代方案 B：用统一流水完全替换旧充值记录。** 放弃，因为当前 `TOPUP` 明确不可用，替换会丢失第一阶段已承诺能力或诱导前端合并。

### 2. 提取共享聚合范围内核，钱包只声明允许的 preset

把 P2-04 已验证的秒级截断、IANA 时区检查、rolling 与自然日/DST 边界算法提取到中性的聚合范围模块；保留 Dashboard 现有导出作为兼容包装，钱包定义自己的 preset 清单：

| preset | 范围 | granularity | 当前可请求 |
|---|---|---|---|
| `24h` | 当前时刻向前 24 小时 | `HOUR` | 是 |
| `today` | 用户时区今日 00:00 至当前 | `HOUR` | 是 |
| `yesterday` | 用户时区上一自然日 | `HOUR` | 是 |
| `7d` | 当前时刻向前 7×24 小时 | `HOUR` | 是 |
| `30d` | 当前时刻向前 30×24 小时 | `DAY` | 否 |

范围生成函数必须接收显式 `now` 与 `timezone`。选择 preset 时只取一次时钟快照；之后请求、范围摘要、URL、分页、语言切换和重试都复用不可变范围。这样避免复制 P2-04 的 DST 逻辑，也不让钱包依赖 Dashboard 文案或页面状态。

**替代方案：直接调用 `buildDashboardRange`。** 代码最少，但会让钱包依赖 Dashboard 专属类型、标签和 1H 配置，后续两页能力变化会产生错误耦合。

### 3. URL 保存 preset 与完整规范化查询，页面不维护第二份已提交状态

钱包 URL 使用以下白名单参数：

- `range=24h|today|yesterday|7d|30d`
- `startTime`、`endTime`、`timezone`、`granularity`
- `type=ALL|TOPUP|CONSUMPTION|REFUND`
- `page`（统一流水页，从 1 开始；`pageSize` 固定 20，不进入 URL）

建立纯 `parse/normalize/serialize` 适配层。首次无参数进入时，用同一时钟快照创建 24H 状态并 `replace` 写回完整 URL；合法 URL 直接恢复原边界。未知参数被移除，单边时间、非法时间/时区/枚举、preset 与范围语义冲突、`page<1` 或超过当前深分页上限的状态整体回退到安全默认并 `replace`，在规范化完成前禁用聚合查询。

用户切换范围会生成新边界并把 `page` 设为 1；切换类型只保留范围并把 `page` 设为 1；翻页只改 `page`。响应返回后若 `page` 超过根据 `total/pageSize` 得到的最后一页，使用 `replace` 收敛到最后有效页再查询，避免 URL、分页控件与数据不一致。30D URL 状态可以被解析和展示，但 `requestsEnabled=false`，不请求 P2-05。

旧充值记录保留自己的局部页码，因为它属于独立兼容区域；两个分页状态和标签不得复用。若未来要求其可分享，再用独立 `topupPage` 参数扩展，当前不为未提出需求增加 URL 复杂度。

**替代方案：URL 只保存 preset。** 放弃，因为刷新或浏览器返回会重新取当前时间，导致第 2 页对应的数据窗口漂移，破坏稳定分页。

### 4. API 边界严格镜像 P2-05，视图层只消费 Portal 自有类型

在钱包 API 模块增加 P2-05 所有响应的 strict Zod schema，覆盖：

- baseline、规范化 range、三态 availability 与原因枚举；
- 汇总的 record/quota/money/coverage；
- 流水的 type/direction/unit/currency/status、可空字段、coverage 与分页；
- 条件约束：可用值与原因互斥、`CURRENCY` 必须有 currency、`QUOTA` 必须无 currency、amount 非负、总体 coverage 组合合法。

URL 构造只接受规范化范围、单值 type、page 和固定 pageSize，使用 `buildPortalApiUrl` 产生相对路径并传递 `AbortSignal`。不复用上游 DTO，不接受未知字段，也不把 schema 宽松化以兼容未来未声明枚举。

查询键分别为：

- summary：命名空间、userId、完整范围；
- transactions：命名空间、userId、完整范围、type、page、pageSize。

两个查询并行且互不以对方成功为前提，后端自行通过 P2-05 共享快照缓存消除重复读取。TanStack Query 负责取消或忽略旧键响应，关闭自动重试；30D 时两个 hook 均 `enabled=false`。401 统一清除认证、usage、wallet、summary 与 transactions 用户作用域缓存。

### 5. 先构造精度安全的展示模型，再渲染页面

新增纯展示映射，页面组件不直接拼接 reasonCode 或推断状态：

- `AVAILABLE`：显示真实值，包括零；
- `PARTIAL`：显示可用值/items，同时返回持久 warning 文案与逐来源说明；
- `UNAVAILABLE`：返回不可用面板，不进入普通 empty 分支；
- 只有 `AVAILABLE + total=0` 才形成真实空状态。

方向使用本地化文字和图标/形状双重表达；amount 始终按非负字符串处理，不把 `DEBIT` 改写回负数。quota 以原字符串加 `quota` 单位显示。货币格式化使用“十进制字符串分解 → locale 分组/小数分隔符 → currency code”的精度安全路径，不先转换为 JavaScript `number`；无法安全格式化时保留原字符串和币种，绝不截断。时间用活动 locale 与响应 `range.timezone` 格式化，同时为辅助文本或 `dateTime` 保留原始 ISO 值。

`referenceId` 仅以等宽可换行文本显示。当前请求日志接口不支持 requestId 精确筛选，因此不创建虚假的下钻链接；`remark`/`referenceId=null` 映射为本地化“无”。coverage 使用页面级可见说明，不塞入只在 hover 出现的 tooltip。

### 6. 每个数据区拥有独立状态，统一流水保持稳定表面

余额、汇总、流水、充值能力、旧充值记录分别拥有 pending/error/success 分支和重试按钮。任何非 401 错误只影响所属区域；重试调用当前 query 的 `refetch`，不改变 URL 或重新生成范围。聚合查询切换时不展示旧范围/旧页 rows；表头、coverage 区和分页占位保持稳定，由共享 loading/feedback 表面承载等待状态，避免把旧数据误认为新筛选结果。

统一流水桌面使用语义 `DataTable`，列为时间、类型、方向、金额、状态、备注、来源；窄屏卡片重复所有标签和值并保持服务端排序。页面继续自然滚动，只有桌面表格横向溢出由表格容器拥有；不增加 `100vh`、固定高度或共享外壳 `overflow:hidden`。分页始终位于流水区，旧充值分页位于兼容区。

共享 Radix `Select` 负责范围和类型，继续使用 collision-aware portal、键盘模型和 compact density。实施时将 `UX-CONTRACT.md` 与 `premium-ui.json` 的 Select owner 从过时的 native 修正为 authored shared `Select`，并用既有组件测试和真实浏览器打开态验证；不修改控件视觉令牌，也不创建钱包专属 select。

### 7. 本地化、可访问性与验证矩阵作为契约的一部分

`zh-CN` 与 `en-US` 同构资源覆盖范围、类型、方向、状态、单位、coverage 原因、数据性质、30D 阻塞、空/部分/不可用/错误/重试文案。技术枚举不得直接出现在面向用户的句子中。页面结构使用 `h1/h2`、具名 section、表格语义、`role=status/alert/note` 的适当分工；颜色只辅助语义，focus-visible、减少动效、强制颜色和长文本换行沿用全局系统。

浏览器验证至少覆盖：共享 Select 打开态、URL 前进后退、快速范围/类型切换、桌面表格、窄屏卡片、200% 缩放、键盘、zh-CN/en-US、loading、真实空、部分覆盖、来源不可用、网络失败、401、30D 和充值关闭。现有页面没有新 mutation，离线时保留其他已成功只读区域并对失败区域提供显式重试，不引入自动重放。

## Risks / Trade-offs

- **[同页五类只读请求增加首屏并发]** → 保持区域独立并让 P2-05 汇总/流水复用服务端快照；不串行化请求，也不增加前端聚合缓存层。
- **[旧充值记录存在而统一 TOPUP 不可用，用户可能困惑]** → 两个区域使用明确标题、独立说明和不同分页；coverage 以 P2-05 为准，绝不合并。
- **[完整范围进入 URL 较长]** → 换取刷新、Back 和跨分页的确定性；只存非敏感范围/筛选，不存 userId、requestId、token 或金额。
- **[共享范围提取可能回归 Dashboard]** → 先锁定现有 Dashboard DST/preset 测试，再提取纯内核并保持原导出兼容。
- **[当前正式消费金额不可用，页面视觉上信息不对称]** → 把 quota 作为有证据的主值，把 USD 不可用作为说明而非占位金额，不为视觉完整伪造数据。
- **[ authored Select 与维护文档漂移]** → 在同一实施变更中只修正所有权记录并运行 strict premium audit；不借机扩大为设计系统重构。
- **[strict premium audit 当前被 11 条既有测试 fixture finding 阻塞]** → 实施开始时先复现并把它们作为基线问题处理：确认是否为静态检查误报，再通过让测试控件显式表达动作/禁用状态、复用共享 Textarea 或最小且有证据的审计配置修正；不得降低生产代码检查强度，也不得把既有 finding 误报为本变更新增。
- **[页面内容增长导致移动端冗长]** → 保持自然滚动和紧凑标题，卡片只重复必要字段，不折叠 coverage 或隐藏来源信息。

## Migration Plan

1. 先增加 P2-05 前端 schema、精度安全展示模型、共享范围内核和 URL 适配的测试与实现，保持现有钱包页面调用不变。
2. 增加用户隔离 query keys、取消/401 清理和 summary/transactions hooks，验证 30D 不发请求。
3. 按余额、消费汇总、统一流水、充值兼容区顺序重构页面，补齐桌面/窄屏、状态、本地化与可访问性测试。
4. 修正 Select canonical owner 文档漂移，新增 P2-06 说明并更新阶段总纲；运行项目既有前端与 premium 验证。
5. 部署不需要数据库、配置或后端迁移。回滚只需恢复旧前端构建；P2-05 接口仍是向后兼容的只读能力，旧余额与充值页面接口不受影响。
