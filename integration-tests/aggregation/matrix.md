# P2-10 验收矩阵（2026-10-03）

现行契约与原阶段上线条件分开判定。历史测试数量不能作为本轮通过依据；冻结基线仍为 `p2-2026-09-22-a`。

| 原目标 | 现行契约 | 历史证据 / 取消依据 | 必需剩余条件 | 本 spec 映射 |
|---|---|---|---|---|
| P2-01 来源与统计冻结 | 保守基线；未知数据不回填零 | docs/new-api/aggregation-baseline.json、统计与流水口径.md | 真实跨日/DST、小时趋势、stat 时间过滤、TOPUP/REFUND、完整错误记录、新基线与容量 | 对账、上线条件 |
| P2-02 查询基础 | 168h 日志、共享预算、缓存隔离、低基数观测 | 基础聚合与 infrastructure/aggregation 测试 | 当前构建回归；30d 容量属于原阶段缺口，不开放扫描 | 查询保护、观测 |
| P2-03 六指标 | Token/Key/平均延迟可用；请求数/金额/成功率不可用 | docs/12 与 DashboardStats*Tests | 完整请求来源、正式换算基线、30d | 查询保护、对账、上线条件 |
| P2-04 Dashboard | 不可用趋势、部分最近请求、30D 禁用 | docs/13、DashboardPage/useDashboard 测试 | 双趋势与三指标权威证据、完整最近请求、30d | 页面、安全 |
| P2-05 钱包接口 | quota 可用；金额不可用；流水逐来源 PARTIAL/UNAVAILABLE | docs/14、AccountAggregation*Tests | TOPUP/REFUND、正式金额口径、容量 | 对账、查询保护 |
| P2-06 钱包页面 | 不回填零；窄屏卡片；区域独立错误 | docs/15、wallet.spec.ts、WalletPage 测试 | 正式金额、真实充值/退款、30d | 页面、安全 |
| P2-07 模型接口 | 基础价格和厂商可用，增强字段 null | docs/16、模型控制器与 pricing 契约 | 增强元数据可信来源、正式计费证据范围 | 对账、安全 |
| P2-08 模型页面 | URL 筛选、懒加载详情、三值元数据 | docs/17、catalog/catalogStates.spec.ts | 真实增强字段及增强价格 | 页面、安全 |
| P2-09 日志增强 | 流式/总耗时/Token/结果/精确费用；取消字段不展示 | docs/18 第8～10节、archive/2026-10-02-03-lang-p2-09-request-log-enhancements | 当前构建复测；错误样本不能证明持续完整 | 页面、对账、上线条件 |
| P2-10 统一验收 | 四入口、两结论、版本归属与安全报告 | 本目录 | 真实身份/浏览器/对账、代表性数据、部署资源、性能目标 | 全部要求 |

只取消 P2-09 协议和首 Token 延迟：用户 2026-10-03 明确取消，见 docs/18 第9节。其他受阻项保持 BLOCKED。

## 配置核对

静态有效值 = application.properties 与对应 profile 覆盖，不代表容器运行有效值；后者须读取运行 JAR 配置并检查非敏感环境覆盖。

| 键（lang.aggregation.*） | 公共 | dev | test | prod |
|---|---|---|---|---|
| baseline-version | p2-2026-09-22-a | 同公共 | 同公共 | 同公共 |
| page-size / max-pages / max-records | 20 / 10 / 200 | 同公共 | 10 / 5 / 50 | 同公共 |
| single-call-timeout / total-timeout | 5s / 30s | 同公共 | 1s / 5s | 同公共 |
| max-live-log-range | 168h | 同公共 | 同公共 | 同公共 |
| cache-ttl / cache-maximum-size | 30s / 1000 | 同公共 | 5s / 100 | 同公共 |
| five-minutes-max-span / hour-max-span / day-max-span | 24h / 168h / 720h | 同公共 | 同公共 | 同公共 |

所有 profile 显式声明聚合配置。test cache-maximum-size=100 与 design 表 1000 不同，记录为更紧保护的既存差异，不修改配置。公共 actuator 仅 health/info，health 不显示详情；未开放 metrics/env/configprops。粒度跨度 720h 不等于实时日志可扫描 30d。

## 复用与缺口

- 时间：AggregationQueryContext/TimeBounds/BucketPlan、DashboardStatsGuard；UTC、左闭右开、DST 23/25h、非法输入及无调用早拒绝已有覆盖。
- 预算：AggregationPagedReader/ReadBudgetTimeout、Dashboard/Account Vertical；分页超限、记录超限、共享预算、单次/总超时、失败恢复已有覆盖；补充 0 条、准确上限验收。
- 缓存：Cache/Failure/Outcome/Concurrency/Key；补充 10 并发以及以实际加载结果验证每一个身份维度隔离。
- 安全：Dashboard/Account/RequestLog/Model Controller 与 NewApi 客户端测试；未登录、越权参数、非法模型标识、错误脱敏已有覆盖。
- 观测：AggregationMetrics/MetricWhitelist、Vertical；计数与有限标签已有覆盖。upstream.calls 是 recordUpstream 的逻辑记录次数，不等于 retry 后的 HTTP 尝试；页数和记录来自已处理页，拒绝之前未接纳页不能当作真实总量。进程内 SimpleMeterRegistry 是契约来源，不是容器运行指标。进一步核对实际生产调用链：recordProtection目前只有定义、没有生产调用；上游计数也只在完整读取成功后记录，不能表示失败HTTP尝试。拒绝指标集成仍缺证据，3.5保持未完成，不以直接调用指标API的测试代替生产观测。
- 浏览器：已有 desktop/mobile/wallet/catalog/catalogStates/requestLogs；使用 Playwright route 的合成响应为 controlled-contract，不能证明真实 New API 来源。真实四页面与双身份验证须独立证据。
- 脱敏：复用 AggregationSensitiveScanner；补充验收工具白名单投影与报告扫描桥接。
- 性能：缺少冷热采样与独立百分位分类；需新工具。真实小样本仅冒烟，模拟大数据仅保护契约，代表性真实规模各范围冷热≥20次才可发布基线，未确认目标仍 BLOCKED。
