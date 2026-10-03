# LANG-P2-09 请求日志基础信息与费用展示增强说明

> **现行范围（2026-10-03 用户确认）：协议和首 Token 延迟展示及非空映射已取消。** 页面保留流式状态、总耗时、Token、结果、费用、quota 与请求标识；两个既有 API 可空字段仅保留兼容。原任务 6.1 记为需求取消，不记为验证通过。第 1～8 节是取消前的实施/实测历史，现行交付与验证见第 9 节。


本文件说明请求日志（`/dashboard/request-logs`）在协议、首 Token 延迟（TTFT）与费用口径上的增强实现，覆盖字段证据表、前端兼容归一、页面闭环、费用精度与验证结果，并单独记录仍未解除的外部阻塞。

本次不修改后端 DTO 字段名称、分页包装、错误码与查询参数集合，也不新增日志详情接口、协议/TTFT 筛选、网关埋点或历史日志回填。

## 1. 字段证据表

证据来源为 `docs/new-api/aggregation-baseline.json` 与 `docs/new-api/samples/aggregation/` 下的真实脱敏样本，冻结基线 `p2-2026-09-22-a`（New API `v0.13.2`）。

以下为旧裁剪样本的复核，不能推导原始 other 不存在；2026-10-03 新实测证据见第 8 节。本轮按任务要求复核了该基线全部 19 个 `/api/log/self` 与 `/api/data/self` 样本，逐个扫描 `protocol`、`first_token_time`、`channel`、`endpoint`、`path` 等候选键，结果如下：

| Portal 字段 | 上游候选路径 | 复核结论 | 证据 |
| --- | --- | --- | --- |
| `protocol` | `$.data.items[*].protocol` | **不可用** | 全部 `log-self.*` 样本中不存在该键；`channel` 为渠道数字标识，语义不是协议，不可替代 |
| `firstTokenLatencyMs` | `$.data.items[*].first_token_time` | **不可用** | 全部 `log-self.*` 样本中不存在该键，单位与测量起止点均无证据 |
| `occurredAt` | `$.data.items[*].created_at` | 2026-10-03 实测验证（Unix 秒）；兼容旧 `created_time` 别名 | 本文第 8 节及新增实测样本 |
| `keyName` | `$.data.items[*].token_name` | 已验证 | 同上 |
| `model` | `$.data.items[*].model_name` | 已验证 | 同上 |
| `inputTokens` / `outputTokens` | `prompt_tokens` / `completion_tokens` | 已验证 | 同上 |
| `durationMs` | `use_time`（秒）→ 毫秒 | 已验证 | 同上 |
| `stream` | `is_stream` | 已验证 | 同上 |
| `quota` / `amount` / `currency` | `quota` → `QuotaMoneyConverter` | 已验证（2026-10-03 本地实例换算证据见第 8 节） | `log-self.nonempty.json` |

**结论：两个增强字段继续返回 `null`。** 依据本变更的设计决策，实测证据是启用非空映射的前提；名称相似（如上游出现名为 `protocol` 或 `first_token_time` 的值）不构成语义证据，不得按名称相似性自动启用映射。`RequestLogEnhancementFieldFreezeTests` 用同时携带这些同名上游字段的成功与错误样本固定了该行为。

## 2. 前端可选字段归一

后端 DTO 字段名称、类型与分页包装保持不变，正常响应显式序列化两个 `null`。前端在 `frontend/src/api/requestLogs.ts` 对两个可选字段做独立归一，其余字段与未声明字段仍按严格 schema（`.strict()`）校验：

| 输入 | `protocol` | `firstTokenLatencyMs` |
| --- | --- | --- |
| 键缺失 / `null` | `null` | `null` |
| `OPENAI` / `ANTHROPIC` / `GEMINI` | 原值保留 | — |
| 词表外字符串、空白、异常类型、可疑文本 | `null` | — |
| 非负安全整数（含 `0`） | — | 原值（毫秒） |
| 负数、小数、非整数、字符串、非有限值、超出 `MAX_SAFE_INTEGER` | — | `null` |

关键规则：

- **合法零值必须保留。** 判定使用空值判断而非真假判断，`0 ms` 是真实测得值，不显示为"暂无数据"，也不与总耗时混为一谈。
- **不以总耗时推断上下界。** `durationMs` 的单位分辨率与测量起止点尚不能支撑"TTFT 必然小于总耗时"这类推断。
- **异常增强值只降级自身。** 一页中某条记录的可选字段异常不影响同页其他条目、分页总数与基础字段；基础 Token、quota、时间、金额违反原有契约时整页仍安全失败，不用降级规则掩盖。
- **旧响应向前兼容。** 旧版本省略这两个键时整页正常解析并显示"暂无数据"，回退制品不会使整页校验失败。
- **前端不猜单位。** 若后续映射需要秒转毫秒或舍入，须先由证据冻结规则并在后端做受检转换。

展示词表 `OPENAI` / `ANTHROPIC` / `GEMINI` 仅用于 Portal 值的显示支持，词表本身不证明任何上游映射或公共协议已开放。

## 3. 页面闭环

列顺序：时间、Key、模型、**协议**、**流式状态**、Token（输入/输出）、耗时、**首 Token 延迟**、结果、费用、请求标识。

- 协议与 TTFT 不可用时逐项显示本地化"暂无数据"，不回填为零、`OPENAI` 或总耗时。
- 流式状态只读取 `stream` 字段，不从其他列推断。
- 桌面与窄屏共用的日志数据区带可键盘聚焦的"说明"按钮（`aria-expanded` + `aria-controls`），说明首次解释为"首 Token 延迟：从请求开始到收到第一个输出 Token 的耗时，单位毫秒。显示'暂无数据'表示当前未采集该指标，不等于 0；总耗时不等于首 Token 延迟。"。说明默认折叠但按钮始终可访问，不依赖悬停或颜色。
- 窄屏（≤760px）由卡片承接全部字段，含协议、流式状态、总耗时与 TTFT；表格在窄屏隐藏但不丢内容，整页不横向溢出。
- 沿用既有筛选提交、服务端分页、URL 恢复、Dashboard 下钻、加载/空态/错误重试、迟到响应隔离与会话失效缓存清理。本次**不新增**协议或 TTFT 的前端页内筛选，`protocol` / `firstTokenLatencyMs` / `ttft` 查询参数与任何指定用户参数一样被后端拒绝（`INVALID_ARGUMENT`）。

## 4. 费用口径

- 请求日志继续使用原始 `quota` 与统一后端换算口径 `QuotaMoneyConverter` 产生十进制字符串 `amount` 与 `currency=USD`；本次不修改舍入策略，请求日志、Dashboard 与消费汇总复用同一路径。
- 页面完整保留金额与币种原文，不调用 `Number` / `parseFloat` 或 `Intl` 数值格式化重算，不出现科学计数法；`quota` 以十进制字符串原样展示在费用单元格的次级文本中（"额度：N quota"），超过浏览器安全整数范围也不转浮点。
- 真实零费用显示为 `0.0 USD`，不推断为免费模型。
- 费用说明按钮（"费用说明" / "Cost explanation"）说明"费用按日志额度和配置换算，列表不是额外收费项目或正式账单；逐条金额相加可能与汇总存在舍入差异。"
- `RequestLogFeeConsistencyTests` 固定"同一原始 quota 经同一换算器得到同一金额"，覆盖真实零、最小金额、大额度与逐条/合计舍入差异的表述约束。**逐条已舍入金额的前端求和不是 Dashboard 或消费汇总的权威**，也不在浏览器修改正式汇总去"对齐"页面。
- **2026-10-03 实测补充：** 上游 `/api/status` 明确返回 `quota_per_unit=500000`；受控请求扣除 4050 quota，日志、消费汇总和模型价格核算均为 `0.0081 USD`，见第 8 节。这验证本地当前实例的额度换算，不等于供应商正式账单。

## 5. 验证结果

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 后端单元/契约测试 | `mvn -s .../settings.xml verify`（根构建） | 通过，762 项测试，0 失败，1 跳过 |
| 前端单元与组件测试 | `npx vitest run` | 通过，114 个文件 / 768 项测试 |
| 前端类型检查与规范 | `npm run lint` | 通过（`tsc --noEmit` + eslint，0 error 0 warning） |
| 前端构建 | `npm run build` | 通过 |
| 浏览器端到端 | `npx playwright test` | 通过，61 项（既有 52 项 + 本次新增 9 项） |

新增测试：

- `portal-api`：`RequestLogEnhancementFieldFreezeTests`（同名上游字段不启用映射）、`RequestLogEnhancementSerializationTests`（两个 `null` 字段显式出现在 JSON 中）、`RequestLogFeeConsistencyTests`（费用一致性与精度）；`RequestLogControllerTests` 增补协议/TTFT 与任意用户参数拒绝用例。
- `frontend`：`src/api/requestLogEnhancements.test.ts`（归一规则、旧响应、混合可用性、基础字段严格失败）、`src/pages/RequestLogsPageEnhancements.test.tsx`（列展示、全 null、0 ms、流式状态、中英文、费用说明与 quota）；`e2e/requestLogs.spec.ts`（模拟夹具的非空展示、375px 窄屏与英文路径）。

## 6. 未解除的外部阻塞

以下事项需要真实环境证据，**不因代码和模拟测试完成而视为通过**：

| 编号 | 缺口 | 状态 |
| --- | --- | --- |
| P2-09-6.1 | 协议与 TTFT 的真实字段、单位和覆盖范围证据 | 已找到候选字段；frt 不保证首输出 Token，尚未冻结非空映射，接口继续返回 `null` |
| P2-09-6.2 | type=5 真实错误日志样本，核对状态、Token、quota、总耗时与增强字段缺失处理 | 2026-10-03 已取得受控上游 400 的真实记录，见第 8 节 |
| P2-09-6.3 | `quotaPerUsd` 与实际扣费的独立换算证据 | 2026-10-03 当前本地实例已核验，见第 8 节；不外推供应商结算 |

**边界提醒：**

- 端到端与单元测试中的非空协议/TTFT 均为**合成的展示夹具**，仅验证归一与展示规则，**不构成实测证据**，不得据此修改基线结论。
- 不采集提示词或响应正文，不透传上游原始扩展字段（如 `content`、`other`、`ip`、`channel`），不调用管理员日志接口，不写入历史日志，不增加网关计时埋点。
- 不由总耗时计算 TTFT，也不假定非流式或错误记录具有 TTFT。
- 不启用任何新运行配置或功能开关；`application.properties` 与 `application-dev/test/prod.properties` 的现有分离与取值保持不变。

## 7. 2026-10-03 窄屏验收问题修复

- TTFT 说明入口从桌面表头移到共用日志数据区，375px 下可用键盘展开、收起并保留按钮焦点；中英文均可访问。
- 移动卡片补齐总耗时，保持总耗时与 TTFT 两个独立字段和单位。
- 新增两个浏览器回归场景，修复前均因对应入口/字段不可见而失败；修复后日志相关 11 项浏览器测试全部通过，英文窄屏场景也验证了两个字段及键盘说明。
- 前端 114 个文件 / 768 项测试、Lint 与构建通过。本段为窄屏修复时记录；后续一体化联调及 6.2、6.3 已通过，最新状态见第 8 节。

根构建验证：默认并发运行出现 5 个既有异步用例失败，限制并发为 2 后完整根构建成功（后端 762 项、0 失败、1 跳过）。复现命令：

```bash
VITEST_MAX_FORKS=2 VITEST_MIN_FORKS=1 VITEST_MAX_THREADS=2 VITEST_MIN_THREADS=1 \
  /Users/nelson/software/apache-maven-3.8.4/bin/mvn \
  -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml verify
```

OpenSpec strict 与 `git diff --check` 通过。UI 静态检查仍有 7 项既有 Select/Listbox 规则冲突，与验收时一致。


## 8. 2026-10-03 最新 Compose 实测补验

用户明确授权本地 Docker 验证。使用已通过验证的最新一体化 JAR 生成 `lang-api-lang-api:p209-local-20261003` 测试镜像，保留原有数据卷。最终宿主机 JAR 与容器内 `/app/app.jar` SHA-256 均为 `368361b94aed257d4c99f2fe57e3b1c4a5c675c1186bf7cc3b82bf75ea4746ac`。镜像基础来自本地既有运行镜像，替换的是完整最新 JAR；不以容器运行时长判断代码版本。

### 联调发现并修复

1. New API v0.13.2 当前日志时间键为 `created_at`（Unix 秒），旧适配器错误读取 `created_time`，使非空日志整页 502。改为读取真实键并保留旧字段别名兼容；成功和错误类型回归测试修复前失败、修复后通过。
2. 真实用户未填邮箱时，profile 返回空字符串，前端严格校验只接受合法邮箱或 null，刷新后错误回到登录页。认证响应统一空白邮箱为 null；序列化回归测试修复前失败、修复后通过，真实浏览器登录和页面重新加载验证通过。

测试临时配置为 Portal 增加 `http://localhost:8081` 允许来源（保留原有 5173）；只写入忽略目录 `portal-api/target/p209-local/compose.yml`，不改仓库运行配置。临时打开 New API `ERROR_LOG_ENABLED=true` 采集错误日志后恢复默认关闭，探测使用的 13000 loopback 端口采集后关闭。

### 实际验证结果

- 认证后的非空日志 API：pageSize=1 的第一页与第二页均 200，total=9；ERROR 查询 200；未来范围空页 200；非法 protocol/TTFT/userId/page 参数 400；无会话 401。未透传提示词、响应正文或其他上游敏感扩展。
- 无 API mock 的 Playwright 直接访问 `http://localhost:8081`：真实登录、页面重新加载、9 条真实记录、单页边界的下一页禁用、费用说明、375px 键盘展开 TTFT 说明、整页无横向溢出、ERROR 页面以及清理会话后 401 全部通过。API 小页分页有非空第一页/第二页证据；UI 当前只有 9 条，不宣称完成真实多页按钮翻页。
- 后端完整 `mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml -pl portal-api verify`：765 项、0 失败、1 跳过，BUILD SUCCESS。前端代码本轮未再次修改，沿用第 7 节已验证结果。
- 脱敏实测投影：`docs/new-api/samples/p209/2026-10-03-local-verification.json`，仅保留验收字段；用现有 `AggregationSensitiveScanner.scanOrThrow` 检查通过。完整敏感响应与会话不写入版本化样本，原始诊断仅位于忽略目录 `portal-api/target/probe-raw/`；所有本轮创建的测试 Key 已删除，真实消费和错误日志保留作为证据。

### 外部任务更新

**6.2 已完成。** 默认错误日志关闭，解释了最初 type=5 列表为空。临时开启记录后，以受限测试 Key 发送非法上游参数，模型网关实际返回 400；New API 生成一条 type=5 日志：输入/输出 Token 均 0、quota=0、use_time=1 秒、非流式，增强候选字段缺失。Portal 对应为 ERROR、durationMs=1000、amount="0.0" USD、两个增强字段 null。未直接写入数据库或管理员日志。

**6.3 已完成（本地当前实例）。** 上游 `/api/status` 返回版本 v0.13.2、quota_per_unit=500000；独立用户余额从 500000 减至 495950，used_quota 从 0 增至 4050。该唯一新增成功记录输入 85、输出 23 Token，当前模型输入/输出价格均为 75 USD/百万 Token，因而 `(85+23)×75/1000000=0.0081 USD`；`4050/500000=0.0081 USD`，Portal 日志与消费汇总同值，失败调用没有新增扣费。这组值无需额外舍入；逐条与合计可能有舍入差异的既有测试与文案保留。未证明供应商正式账单，也不外推其他实例；stat 的时间参数有效性缺口不因此关闭。

**6.1 仍未完成，但旧证据结论已修正。** 原来的脱敏样本裁剪掉了 other，不能证明真实源中没有 TTFT 候选。本轮真实流式记录存在 other.frt（3220、860、1044），非流式记录为 -1000；other.request_conversion 为 ["OpenAI Compatible"]。对照固定 v0.13.2 源码：

- `service/log_info_generate.go` 的 frt 是 FirstResponseTime 与 StartTime 的 UnixMilli 差值，单位毫秒；`relay/helper/stream_scanner.go` 在收到首条非空、非 DONE 的 SSE data 时记时，可能是角色/控制事件，未保证第一个输出 Token；未设置的时间初始化为请求开始前一秒，解释 -1000。
- request_conversion 是协议转换链，覆盖这组 OpenAI 成功样本；错误记录未携带该链。它是候选来源，尚不能据此断言所有日志、协议和错误路径均有覆盖。

源码引用：[日志扩展生成](https://github.com/QuantumNous/new-api/blob/v0.13.2/service/log_info_generate.go)、[流式事件采集](https://github.com/QuantumNous/new-api/blob/v0.13.2/relay/helper/stream_scanner.go)、[首次响应时间初始化](https://github.com/QuantumNous/new-api/blob/v0.13.2/relay/common/relay_info.go)。后续需要先决定是否将当前严格首输出 Token 契约改为首响应事件，以及冻结协议链映射/异常降级规则，更新 spec/design 后再实现。当前继续返回 null，不把 frt 自动当成首 Token 延迟。

5.4、6.2、6.3 已完成；6.1 保持未完成，P2-09 仍为部分完成。旧记录中「Up 11 hours 即旧代码」和「项目规则禁止启动本地测试应用」不成立，已撤回。


## 9. 2026-10-03 用户取消协议与首 Token 延迟

用户确认取消两个辅助指标。本次已从桌面表格及移动卡片删除协议、TTFT 和对应占位、说明入口，清理专属状态、函数、样式与中英文文案。流式状态、总耗时、输入/输出 Token、费用完整字符串、原始 quota 与键盘可用的费用说明继续保留。

保留既有 DTO 字段 protocol/firstTokenLatencyMs 和 API 响应兼容归一规则，后端仍显式返回 null；即使兼容响应携带非空值也不展示。不新增 other 解析、计时采集、映射、筛选或历史回填。created_at 时间映射与空白邮箱响应修复保留。

原 6.1 已从活动待办移出并明确标为取消，不勾选为验收通过。真实 type=5、实际余额扣减及换算证据保留；这里只关闭 P2-09 当前范围，不解除其他 P2-01 外部缺口，也不等于自动归档。

当前取消后的验证：前端 114 个文件 / 765 项测试通过（限制并发为 2），npm run lint 与 npm run build 通过；请求日志 Playwright 6 项通过，覆盖非空兼容字段不展示、旧响应、精确费用和键盘说明、分页/刷新/下钻、375px 中英文。取消前展示断言已调整，测试数量减少来自删除已取消场景；保留 API 兼容及费用测试。后端本轮未修改，沿用上轮完整 765 项（0 失败、1 跳过）。

本地测试产物同步：通过指定 Maven/settings.xml 的 `-pl portal-api -DskipTests package` 仅重新打包（不是本轮后端测试结果）；已逐文件核对 JAR 静态资源与最新 frontend/dist 相同。当前取消后 JAR SHA-256 为 `a0bdf280586c1c27a41cf3a7635634ab4c3a4ad9ea28e833f84b60f875b5463f`，替换现有本地测试镜像。第 8 节指纹保留为取消前实测历史。

最新 Docker 无 mock 浏览器验证通过：真实登录、重新加载、9 条真实记录、单页分页边界、费用说明、375px 下取消指标不展示、ERROR 页及清理会话后 401。容器内 JAR 指纹与上列一致；探测端口保持关闭，未新增模型请求或扣费。OpenSpec strict 与 git diff --check 通过，P2-09 当前收缩范围已完成，原 6.1 仅为取消记录。


## 10. 2026-10-03 归档

用户确认满足条件后归档。现行任务 32/32 完成，原 6.1 明确为用户取消而非验证通过；主 spec request-log-viewing 已同步 1 项修改、2 项新增要求，保留其余原有要求和场景。全部 28 项主 spec strict 校验及变更 strict 校验通过。归档目录为 `openspec/changes/archive/2026-10-02-03-lang-p2-09-request-log-enhancements/`，保留原目录名和 .openspec.yaml；保留未提交代码、测试和文档改动，不执行 Git 提交或发布。
