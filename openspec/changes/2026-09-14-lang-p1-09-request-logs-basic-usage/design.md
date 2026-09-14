## Context

P1-07 已打通真实模型请求，P1-08 已建立模型目录、示例代码和统一 `quota-per-usd` 金额口径；当前 Dashboard 仍是占位页，Portal 也没有面向普通用户的日志、统计或余额契约。变更动机见 [proposal.md](proposal.md)。

冻结版 New API v0.13.2 已提供四个受 `UserAuth` 保护的个人接口：`/api/log/self`、`/api/log/self/stat`、`/api/data/self` 和 `/api/user/self`。它们分别返回分页日志、区间消费与一分钟速率、小时用量以及当前 quota，但字段命名、时间边界和隐私范围都不适合直接暴露给浏览器。小时用量还可能晚于实时日志落盘。

Portal 已具备 `NewApiExchange`、服务端会话、统一成功/错误包装和分页契约；前端已具备 `/dashboard` 下的受保护布局、TanStack Query 和响应式导航。本阶段沿用这些边界，不增加 Portal 数据库、缓存或后台聚合任务。

## Goals / Non-Goals

**Goals:**

- 以 New API 个人接口为唯一权威源，通过稳定 Portal DTO 提供请求日志、摘要、趋势和余额。
- 在服务端统一处理用户身份、时间范围、分页、隐私裁剪和 quota→USD 换算。
- 让 Dashboard 各区域独立加载和失败，并使请求日志筛选、分页可以直接验证真实模型调用。
- 使 P1-09 的余额接口和金额转换可在 P1-10 钱包能力中复用。

**Non-Goals:**

- 不查询或展示充值、退款、系统、管理日志，也不实现钱包、支付和充值记录。
- 不承诺冻结版无法可靠提供的成功率、输入/输出 Token 汇总、平均延迟、协议或首字延迟。
- 不从分页日志计算正式统计，不补齐小时空桶，不为近实时趋势建立本地存储或同步任务。
- 不提供管理员查询、跨用户查询、自定义上游路径或任意 URL 代理能力。

## Decisions

### 1. 四个独立 Portal API 对应四个上游个人操作

Portal 提供：

| Portal API | New API 操作 | 职责 |
|---|---|---|
| `GET /portal/api/request-logs` | `GET /api/log/self` | 单一结果类型的服务端分页日志 |
| `GET /portal/api/usage/summary` | `GET /api/log/self/stat` | 区间消费与最近 60 秒 RPM/TPM |
| `GET /portal/api/usage/timeseries` | `GET /api/data/self` | 小时请求、Token 与消费趋势 |
| `GET /portal/api/account/balance` | `GET /api/user/self` | 当前剩余 quota 与 USD 金额 |

每个 `NewApiOperation` 固定方法、相对路径和认证要求，统一从服务端 `NewApiSession` 产生 `session` Cookie 与 `New-Api-User` Header。Controller 只接受声明的查询参数，身份和上游地址不进入请求 DTO。

选择独立接口而不是组合 `/dashboard` 聚合接口，是因为余额、摘要和趋势的时效、失败方式与刷新触发不同；前端并行查询可以保留局部成功数据，也避免任一上游异常拖垮整页。另一方案是只拉分页日志并在浏览器聚合，但它无法得到全量、稳定的统计，故不采用。

### 2. 请求日志一次只查询一种结果类型

Portal 的 `SUCCESS` 固定映射 New API `type=2`（消费），`ERROR` 固定映射 `type=5`（错误），默认 `SUCCESS`。不提供“两类全部”选项，因为冻结版只能按单一类型返回一套有序分页；Portal 若分别查询后合并，就无法在不全量扫描的情况下给出准确 `total` 和跨类型页序。

Key 使用上游 `token_name` 精确筛选；模型使用长度受限的普通文本包含筛选，适配层对 `%`、`_` 和转义符做安全编码，不能让输入改变 LIKE 语义。所有筛选在上游分页查询中执行，Portal 不拉取未过滤数据后二次分页。

### 3. 统一时间对象负责校验和边界转换

Web 层把 `startTime`、`endTime` 解析为一个不可变的查询区间对象，并注入 `Clock` 计算默认值。契约使用带时区 ISO 8601、秒级和 `[startTime,endTime)`；两端必须同时出现或同时省略，默认最近 24 小时，最大 30 天。

适配层把区间转换为 epoch seconds。由于 New API 的结束秒为包含语义，发送 `end_timestamp = endTimeEpochSecond - 1`。开始早于结束且只允许秒级，因此减一不会产生空区间；所有范围校验都在访问上游前完成。摘要与趋势由页面传入同一对时间值，避免各请求分别取当前时间造成边界漂移。

时间与分页默认值放入 `PortalCommonProperties` 的 usage 配置组，并在 `application.properties`、`application-dev.properties`、`application-test.properties`、`application-prod.properties` 中显式保持环境边界；继续使用 `.properties`，不引入 YAML。

### 4. 适配 DTO 在最靠近上游处完成白名单解析

日志适配 DTO 只声明时间、类型、Key 名称、模型、quota、输入/输出 Token、耗时、流式标识和 requestId；统计、小时行和余额也各自只声明所需字段，并配置忽略未知字段。`content`、`other`、IP、用户、Token、渠道、分组和上游错误正文不进入业务对象、异常或日志。

映射采用“整页/整批可信”规则：已声明字段出现负数、非法时间、溢出或错误类型时，整体返回安全 `UPSTREAM_ERROR`，不混合部分可信数据。日志序号不作为公开 ID；`requestId` 可以为空。`use_time` 以秒转为 `durationMs`，转换前检查范围；冻结版无可靠来源的 `protocol` 和 `firstTokenLatencyMs` 明确返回 `null`。

这比把完整上游 JSON 传到 Web 层再删除字段更安全，因为敏感字段从一开始就不会进入 Portal 领域对象。

### 5. 金额使用一个后端转换器和十进制字符串

从 P1-08 的目录价格映射中提取可复用的 quota 金额转换器，仍读取既有 `lang.portal.catalog.quota-per-usd`，由目录价格、请求费用、摘要消费、趋势点和余额共同调用。为避免配置迁移，本阶段不改配置键名。

转换使用 `BigDecimal` 除法和既有舍入/格式规则；API 以非负十进制字符串表达 quota 与金额，并显式返回 `currency=USD`。前端只格式化显示，不复制公式。配置非法、数值为负或溢出时安全失败。

### 6. 趋势只做确定性的小时合并

`/api/data/self` 可能按“小时 + 模型”返回多行。Portal 先验证每行属于请求区间、时间为 UTC 整点且计数非负，再按桶开始时间合并 `requestCount`、`tokenCount` 和 quota，按时间升序输出，并通过统一金额转换器计算每个桶的 USD 值。

不补零、不插值，也不拿实时日志填补尚未导出的小时数据；页面用提示解释趋势可能存在异步延迟。这样返回值始终可以追溯到上游权威数据，不制造看似精确的推断值。

### 7. 前端按功能拆分查询并把缓存限定到当前会话

前端增加 request logs、usage、balance 的 schema/API 模块和 feature 组件。Dashboard 同时启动余额、摘要和趋势三个查询；摘要与趋势 query key 包含同一规范化时间范围，余额不依赖历史范围。每个区域拥有独立 loading、empty、error 与 retry 状态，只有 `UNAUTHENTICATED` 进入统一会话失效流程。

所有认证数据的 query key 包含当前用户稳定标识，并在登出或会话失效时清除认证范围缓存，防止同一浏览器切换账户后短暂显示旧数据。余额响应额外使用 `Cache-Control: no-store`，服务端不做跨请求用户缓存。

请求日志位于既有 ConsoleLayout 下的 `/dashboard/request-logs`；筛选提交重置到第一页，翻页保留已提交筛选。桌面可使用紧凑表格，窄屏使用不横向溢出的卡片或可控局部滚动，筛选、刷新与分页保持键盘可用。

### 8. 测试以契约 fixture 为主，真实环境只做最终闭环

后端先用 MockWebServer 和脱敏 fixture 覆盖固定路径、双重认证、参数编码、时间边界、字段裁剪、非法响应、金额换算和小时合并，再做 Controller/安全契约测试。前端用单元/组件测试覆盖并行请求、局部失败、筛选分页、窄屏和会话失效。

最终真实验收使用 P1-06 Key 发起一次 P1-07 模型调用，确认日志出现、摘要/趋势按上游时效变化且余额减少。该验收依赖 New API 开启消费日志和小时数据导出；自动化测试不依赖真实账户或供应商。

## Risks / Trade-offs

- [小时趋势有异步延迟] → 页面明确提示，以实时日志和当前余额分别表达近实时状态，不伪造趋势点。
- [成功与错误无法提供统一准确分页] → 一次只查询一种结果类型，保留上游 `total` 与顺序的真实性。
- [冻结版字段或响应结构漂移] → 使用版本固定的最小 DTO、严格已声明字段校验和契约 fixture；未知字段忽略。
- [复杂模型筛选可能扩大 SQL LIKE 范围] → 限制长度、按普通文本转义并为通配字符建立适配器测试。
- [用户数据被 HTTP 或客户端缓存串用] → 余额 `no-store`、服务端不缓存、前端 query key 包含用户标识并在会话结束时清理。
- [共享配置键仍带 `catalog` 命名] → 本阶段复用以避免破坏 P1-08 部署配置；后续若统一命名，单独提供兼容迁移。
- [时间范围最多 30 天限制长周期分析] → 保持冻结版能力和查询成本边界；更长周期与日/月聚合留给后续统计阶段。

## Migration Plan

1. 先加入共享时间/分页配置和金额转换器，并在四套 `.properties` 中显式配置、启动校验。
2. 以契约测试驱动实现四个 New API 只读操作、白名单 DTO 与查询转换。
3. 实现 Portal 查询服务、Controller、安全和缓存响应策略，再接入前端 API/schema。
4. 替换 Dashboard 占位内容并增加 `/dashboard/request-logs` 导航、页面和响应式状态。
5. 运行后端、前端和集成检查；在具备真实依赖时完成一次“调用→日志→统计/趋势→余额”的人工验收。

部署时先确认 New API v0.13.2、消费日志与数据导出配置，再发布 Portal API，最后发布前端。所有新增 API 和页面均为只读且不包含数据库迁移；回滚只需恢复上一版本 Portal API 与前端配置/产物。回滚不会修改 New API 日志或余额，也不需要清理用户数据。
