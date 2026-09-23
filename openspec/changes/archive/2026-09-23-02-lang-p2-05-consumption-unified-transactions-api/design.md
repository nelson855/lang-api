## Context

见 `proposal.md` 的动机和 `specs/account-consumption-transactions/spec.md` 的行为契约。

P2-02 已经提供 `AggregationQueryContext`、`AggregationLogReader`、共享 `AggregationReadBudget`、进程内聚合缓存与低基数观测。P2-03 证明 type=2 日志可以在 7 天、10 页/200 条和 30 秒总预算内完整读取，并且原始 quota 可精确累计。现有账户接口则分别读取余额、充值能力和充值记录，未形成第二阶段统一流水。

当前 `p2-2026-09-22-a` 的财务证据有明确缺口：catalog 的 `quota-per-usd=500000` 是既有运行配置，不是 P2-01 独立验证的结算证据；`/api/user/topup/self` 只有真实空页，P1 的状态映射来自源码/fixture；退款没有来源字段。消费日志也没有可对外承诺的上游数据库 ID，只有非空 requestId 可以作为当前稳定用户级引用。这些限制决定 P2-05 必须交付带覆盖状态的部分能力，而不能直接复用 P1 DTO 拼成“完整账单”。

## Goals / Non-Goals

**Goals:**

- 在 Portal API 内建立消费汇总与统一流水的稳定外部模型，使金额单位、方向、状态和来源缺口可机器判断。
- 复用 P2-02 的时间、读取预算、缓存和观测能力，保证结果完整、用户隔离且成本有界。
- 让消费汇总包含全部合法 type=2 quota，同时只让具有稳定引用的记录进入可分页流水。
- 为未来新基线启用充值和退款预留来源适配边界，但当前运行时只注册证据允许的消费来源。

**Non-Goals:**

- 不把 catalog 配置转换出的 USD 当作正式消费金额。
- 不复用 P1 topups 的 fixture 结论作为 P2 真实充值证据，也不读取空页后宣称用户无充值。
- 不实现退款推断、余额重算、账务总账、对账、冲正、写入、导出或钱包前端。
- 不放宽 7 天实时日志、10 页、200 条、单次 5 秒和总计 30 秒保护边界。
- 不增加数据库、共享缓存、新密钥配置或环境 profile 配置项。

## Decisions

### 1. 在账户域内增加独立聚合控制器与共享消费快照服务

新增结构保持项目现有传统分层：

- 账户聚合控制器只负责严格参数白名单、认证会话绑定、分页/类型解析和统一响应；
- 消费快照服务负责规范化查询上下文、共享预算读取、缓存和观测；
- 汇总计算器只从不可变消费快照计算记录数、quota 和货币可用性；
- 流水投影器负责稳定 ID、覆盖状态、排序与分页；
- Web DTO 只依赖 Portal 自有枚举和值对象，不引用 New API DTO。

消费快照是两个接口的共同内部输入，内容只保留 `occurredAt`、可空 `requestId`、可空 `model` 和 `rawQuota`，以及缺失稳定引用计数；不缓存 token ID、Key 名称、渠道、原始日志或会话。缓存命名空间使用 `account-consumption-snapshot`，键覆盖用户、规范化范围、粒度、时区和 baseline。这样相同范围先查汇总再查流水时可以复用一次完整日志读取，同时不会缓存 P2-02 禁止的敏感原始记录。

**替代方案：** 两个接口各自读取日志。实现更直接，但钱包页面会对同一范围产生重复上游分页和不同时刻快照，既增加成本也使汇总与流水难以抽样一致，因此不采用。

### 2. 查询统一使用 P2-02 四参数，分页窗口在读取前受限

两个接口都通过 `AggregationQueryContext` 解析四个时间参数，即使汇总和列表不返回分桶，也让跨度/粒度组合、时区、缓存键和口径版本保持统一。服务端继续注入 baseline，不接受客户端指定。

流水解析 `page/pageSize/type` 后先使用精确整数运算计算 `endOffset = page * pageSize`；`endOffset` 超过 `aggregation.max-records` 时直接拒绝。当前最大 200 条，因此可支持例如 20 条页大小的前 10 页，不能让任意大页码迫使服务端扫描后再丢弃大量记录。

**替代方案：** 使用 cursor pagination。它更适合长期大流水，但当前 New API 来源均是 page/total 模型，且 P2-02 只允许最多 200 条实时记录；在没有可持久化全局游标和多来源证据时引入 cursor 会制造不真实的稳定性承诺。

### 3. 汇总把原始 quota 与货币金额拆成两个独立可用性对象

汇总计算器使用精确整数/十进制累计 type=2 `rawQuota`，空集合得到可用零值。DTO 分为：

- `recordCount`：整数、`records`；
- `quotaTotal`：十进制字符串、`quota`；
- `moneyTotal`：十进制字符串 + currency，但当前无值；
- `coverage`：说明消费日志读取是否完整。

`moneyTotal` 的可用性只取决于 `AggregationBaselinePolicy` 对正式 USD 金额的结论。当前策略为 conditional，因此返回 `CURRENCY_CONVERSION_NOT_VERIFIED`，代码路径不得读取 `catalog.quotaPerUsd()` 或调用 `QuotaMoneyConverter`。这与第一阶段余额/请求日志仍使用配置换算并不矛盾：P2 正式聚合选择更高证据门槛，旧接口继续保持兼容但不作为新契约的证明来源。

**替代方案：** 同时返回配置估算 USD 并标“仅供参考”。金额语义容易被页面或下游误当作结算值，也会让汇总和未来正式流水产生两个 USD 口径，因此不采用。

### 4. 来源注册表决定运行时会读什么，不可用来源使用状态而非空集合冒充

内部定义统一来源接口，负责声明 type、当前 baseline 支持级别和投影记录。运行时注册表在 `p2-2026-09-22-a` 只启用 `CONSUMPTION` 读取器；`TOPUP` 与 `REFUND` 由基线策略直接生成 unavailable coverage，不实例化或调用 P1 topup client。

请求单一不可用类型时立即返回空 items + unavailable coverage；默认全部类型时只读取消费来源，并把总体标为 partial。coverage 顺序固定为 `TOPUP`、`CONSUMPTION`、`REFUND`，单类型请求只返回该类型，便于稳定序列化和契约测试。

后续启用来源必须先发布新 baseline，再实现对应 Portal 自有投影和排序测试，最后加入注册表；不能仅把现有 `TopupRecord` 强转为统一 item。

**替代方案：** 无论证据状态都并行调用三类来源并把空结果合并。该方案会把“来源不存在/未验证”与“用户确实没有记录”混为一谈，也浪费预算，故不采用。

### 5. 当前消费流水只使用 requestId 生成稳定不透明 ID

消费记录有非空 requestId 时，transactionId 采用：

```text
CONSUMPTION_ + lowercaseHex(SHA-256("CONSUMPTION\0" + authenticatedUserId + "\0" + requestId))
```

来源前缀保证未来 TOPUP/REFUND 不冲突，用户作用域避免两个账户相同 requestId 得到同一 ID，摘要避免把引用原文嵌入主标识。`referenceId` 仍可返回 requestId，因为现有请求日志契约已允许用户用它排查；两者职责不同。

缺少 requestId 的记录参与汇总，但不进入流水，并把消费 coverage 设为 `PARTIAL + MISSING_STABLE_REFERENCE`。同一快照中若两个记录得到相同 transactionId，则视为来源完整性冲突并整体失败，不能静默去重或加入列表位置，因为位置会随新增记录改变。

**替代方案：** 哈希时间、模型、quota 和 Token 组合。两条合法请求可能完全相同，碰撞后无法区分；加入读取序号又会破坏跨页稳定性，因此不采用。

### 6. 消费 item 保留 quota 单位，方向与状态使用显式枚举

当前消费投影固定为：

- `type=CONSUMPTION`；
- `direction=DEBIT`；
- `amount=rawQuota` 的非负十进制字符串；
- `unit=QUOTA`、`currency=null`；
- `status=SUCCEEDED`；
- `remark=model`（非空时）或 `null`；
- `referenceId=requestId`。

金额不使用负号表达方向。未来货币来源只有在 `unit=CURRENCY` 且币种已验证时才能填 currency；UNKNOWN 状态只能用于新基线明确允许保留但尚无稳定枚举的来源，当前消费不会产生 UNKNOWN。

这一 DTO 比统一使用 `amount/currency=USD` 多一个 unit，但避免 raw quota 被货币格式化，是当前证据下满足“统一可读”与“金额不造假”的最小结构。

### 7. 完整快照先排序再分页，total 只统计可投影记录

当前消费读取最多 200 条，因此流水服务在完整快照内：

1. 投影所有有稳定 requestId 的记录；
2. 检测 transactionId 冲突；
3. 按 `occurredAt DESC, transactionId ASC` 排序；
4. 计算可投影 `total`；
5. 最后执行 page slice。

缺 requestId 的条数通过 coverage 表达，不计入 total。此选择保证每页内部和相邻页稳定，也使 `total` 与实际可下钻 item 一致；消费汇总的 `recordCount` 可能更大，这是预期且可由 coverage 解释。

未来多来源启用后仍先把各来源完整 Portal 投影放入同一有界候选集再排序分页；如果真实数据量证明 200 条不足，应先做预聚合/游标存储决策，而不是在本接口中无界扩容。

### 8. 认证、缓存和观测沿用聚合基础设施的低基数边界

控制器沿用双重会话校验，只使用认证主体 ID 构造缓存键和 transactionId。成功快照可缓存，失败和保护性拒绝不缓存；摘要与流水响应每次仍使用当前 HTTP requestId。

观测增加固定操作名 `account-consumption-summary`、`account-transactions` 与快照加载结果，复用 `SUCCESS_LOG` 来源标签。指标只记录成功/失败、缓存结果、页数/记录数、耗时和保护原因，不增加用户、type、时间范围、requestId 或 model 等高基数标签。日志只能记录当前 Portal requestId 和有限原因枚举。

本变更没有新配置。所有范围、预算、超时、缓存 TTL/容量继续来自现有 `application.properties` 及 dev/test/prod profile 的 aggregation 配置，不新增 YAML，也不复制一组账户专用数值。

## Risks / Trade-offs

- **[“统一流水”当前只有消费条目]** → 响应始终返回逐来源 coverage，总体标为 PARTIAL；阶段状态保持受阻，不用产品文案暗示已形成完整账务历史。
- **[消费汇总与流水条数可能不同]** → 缺稳定 requestId 的记录仍参与汇总但不能生成 item，coverage 明确 `MISSING_STABLE_REFERENCE`，文档给出两者差异原因。
- **[requestId 可能重复]** → 在完整快照内检测 transactionId 冲突并整体失败，保留安全诊断；不选择会漂移的序号补丁。
- **[现有 P1 接口已经显示 USD，P2 汇总却不可用]** → 文档明确证据等级不同，P2 不复用未经独立验证的换算；补齐证据后通过新 baseline 一次性开放正式金额。
- **[页码分页只能覆盖前 200 条]** → 在解析阶段限制分页窗口并使用稳定错误；典型数据量与更长范围留给 P2-10 或独立预聚合方案。
- **[短期缓存使刚产生的消费最多延迟一个 TTL]** → 保持 P2-02 的 30 秒只读缓存语义并在接口文档说明，不用于余额权威值；失败不缓存且可在 TTL 后读取新快照。
- **[未来充值/退款接入可能需要不同单位和状态]** → 统一模型预留 CURRENCY、CREDIT、PENDING/FAILED/REFUNDED/UNKNOWN，但启用必须由新证据和 delta spec 驱动，不在当前代码中猜测。

## Migration Plan

1. 先增加 Portal 自有 DTO、枚举与构造守卫测试，冻结 availability、reasonCode、金额单位、方向、状态和 coverage 组合。
2. 实现安全消费快照、稳定 ID 和汇总/流水纯计算器，使用脱敏 P2-01 fixture 验证 quota、缺失 requestId、重复 ID、排序与分页。
3. 接入 P2-02 查询上下文、日志读取预算、缓存与观测，再实现两个受保护 Controller 路由和严格参数白名单。
4. 完成 MockWebServer 纵向测试，证明只读、用户隔离、完整多页、上限失败、无 topup/refund 调用和旧账户接口兼容。
5. 新增 P2-05 接口说明并更新阶段总纲；在真实环境重新抽样核对消费 quota，明确记录充值、退款、USD 和 30 天性能仍受阻。

本变更没有数据库或配置迁移。部署顺序只涉及 Portal API；现有前端在 P2-06 前不会调用新路由。回滚恢复上一版本 Portal API 即可，所有来源均只读且没有需要逆向恢复的数据。
