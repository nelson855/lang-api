## 1. Dashboard 响应契约

- [ ] 1.1 先编写 DTO 构造与 JSON 序列化测试，冻结 `baselineVersion`、`range`、六项 `metrics`、`requestTrend`、`spendTrend` 和 `recentRequests` 的字段名与类型
- [ ] 1.2 实现计数、十进制和金额指标 DTO，以及 `AVAILABLE`、`PARTIAL`、`UNAVAILABLE` 与四个稳定 reasonCode 枚举，构造时拒绝“可用但无值”或“不可用但有值”等矛盾状态
- [ ] 1.3 增加响应敏感字段白名单测试，确认 token ID、完整 API Key、raw quota、渠道、节点、供应商信息、New API 包装和原始错误不会出现在 Dashboard JSON

## 2. 六项指标计算

- [ ] 2.1 先为多条 type=2 记录编写 calculator 测试，固定 Token 精确合计、非空 token ID 去重和平均延迟毫秒计算结果
- [ ] 2.2 实现 `DashboardStatsCalculator`，复用 `AggregationAccumulator` 与 `AggregationBaselinePolicy`，只在策略为 VERIFIED 时生成正式指标值
- [ ] 2.3 增加空记录测试，确认 Token 和活跃 Key 为 AVAILABLE/0，平均延迟为 `UNAVAILABLE + NO_DATA`，请求总数、消费和成功率仍为 `BASELINE_NOT_VERIFIED`
- [ ] 2.4 增加 token ID 局部缺失测试，确认活跃 Key 整项为 `SOURCE_FIELD_MISSING` 且不影响 Token 与平均延迟
- [ ] 2.5 增加平均延迟除不尽、累计溢出和非法记录测试，确认只在最终 DTO 阶段按最多三位小数 HALF_UP，溢出时整体失败而不返回截断值
- [ ] 2.6 使用 `p2-2026-09-22-a` 脱敏 fixture 复算三项已验证指标，并断言另外三项始终保持不可用且不会调用 `QuotaMoneyConverter`

## 3. 趋势与最近请求投影

- [ ] 3.1 先为当前基线的请求趋势和消费趋势编写契约测试，断言两者均为 `UNAVAILABLE + BASELINE_NOT_VERIFIED`、点数组为空且消费币种为空
- [ ] 3.2 先为发生时间倒序、稳定并列排序、最多 10 条和可空 requestId/keyName/model 编写测试，再实现最近请求安全投影
- [ ] 3.3 增加最近请求字段白名单测试，确认 outcome 只对当前 type=2 投影为 SUCCESS，集合始终标记 `PARTIAL + PARTIAL_SOURCE_COVERAGE`
- [ ] 3.4 增加超过 10 条、完全相同记录和空列表测试，确认结果稳定且不会为补满列表执行第二次读取

## 4. 单快照查询服务与缓存

- [ ] 4.1 先为合法上下文、服务端口径版本和一份成功日志列表编写 query service 测试，断言指标、趋势与最近请求只接收同一个不可变记录集
- [ ] 4.2 实现 `DashboardStatsQueryService`，创建共享 `AggregationReadBudget` 并只调用一次 `AggregationLogReader.readSuccessLogs`，不访问错误日志、summary 或 hourly 数据源
- [ ] 4.3 先为 `dashboard-stats` 缓存键、同用户重复查询、跨用户/范围/粒度/时区隔离编写测试，再缓存完整 `DashboardStatsData`
- [ ] 4.4 增加同键并发与首次加载失败测试，确认只合并完整成功加载，异常、超时和保护性拒绝不进入缓存
- [ ] 4.5 扩展 AggregationMetrics 操作白名单与测试，记录 `dashboard-stats` 的聚合、缓存和上游读取结果，不增加用户、模型或时间等高基数标签

## 5. 受保护 HTTP 接口

- [ ] 5.1 先为 `GET /portal/api/dashboard/stats` 编写未认证、四参数缺失、未知参数、快捷范围、客户端 baseline/userId 和非法粒度测试，确认访问上游前返回稳定错误
- [ ] 5.2 实现 `DashboardStatsController`，严格解析四个必填参数、绑定认证主体与 New API 会话，并通过 `ApiResponses.ok` 返回当前请求自己的 requestId
- [ ] 5.3 增加认证主体 ID 与 Cookie 用户 ID 不一致、重复认证 Cookie 和跨用户输入测试，确认返回 `UNAUTHENTICATED` 或 `INVALID_ARGUMENT` 且不产生缓存串用
- [ ] 5.4 增加合法响应 JSON 契约测试，确认规范化 UTC 范围、原始 IANA 时区、稳定粒度和服务端口径版本正确回显
- [ ] 5.5 增加缓存命中 HTTP 测试，确认业务数据可以复用，但响应 Header 与正文使用本次请求的新 requestId

## 6. 失败链与纵向验证

- [ ] 6.1 使用 MockWebServer 编写多页成功链测试，确认完整日志只读取一轮、指标和最近请求与同一 fixture 一致，缓存命中不再次调用上游
- [ ] 6.2 增加总数变化、提前空页、超过 10 页/200 条、上游 5xx、连接失败和超时测试，确认整个 Dashboard 接口失败且响应中没有部分 data
- [ ] 6.3 增加 30 天 DAY 查询测试，确认在当前 7 天实时范围门禁处返回 `INVALID_ARGUMENT`，且 `/api/log/self`、`/api/log/self/stat` 与 `/api/data/self` 调用次数均为零
- [ ] 6.4 增加现有 `/portal/api/usage/summary`、`/portal/api/usage/timeseries` 和 Dashboard 前端测试回归，确认 P2-03 没有改变第一阶段接口或页面调用
- [ ] 6.5 扩展架构测试，禁止 `web/dashboard` DTO 引用 `upstream.newapi` 类型，并确认 controller 不直接依赖 `NewApiLogClient`

## 7. 接口文档与阶段状态

- [ ] 7.1 新增 LANG-P2-03 Dashboard 统计接口说明并更新 `docs/00_文档索引.md`，记录请求参数、完整响应示例、availability/reasonCode、单位、最近请求裁剪和错误行为
- [ ] 7.2 在接口说明中明确当前 `p2-2026-09-22-a` 仅三项指标可用、趋势不可用、最近请求部分覆盖，以及 7 天/10 页/200 条/30 秒保护边界
- [ ] 7.3 更新 `docs/11_第二阶段聚合能力开发与子需求拆分.md` 的实际结果；若 30 天性能证据仍缺失，LANG-P2-03 必须标记为“受阻”并列出已完成范围，不得标记为“已完成”

## 8. 验证与交付记录

- [ ] 8.1 使用 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml` 运行新增 Dashboard 测试、`portal-api` 全量测试和根工程 `verify`，区分本变更失败与环境/既有阻塞
- [ ] 8.2 运行 OpenSpec 严格校验、JSON 字段白名单、敏感信息检查和 `git diff --check`，确认 proposal、spec、design、tasks、接口文档与实现一致
- [ ] 8.3 逐项核对六项指标、两类趋势、最近请求、认证、缓存和失败链验收，并只勾选已实际完成且验证通过的任务
- [ ] 8.4 运行 `git status` 并报告所有未提交改动、测试结果、当前不可用指标和 30 天外部阻塞，不执行提交、推送、发布或部署
