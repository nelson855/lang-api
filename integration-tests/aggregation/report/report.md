# 第二阶段聚合验收报告

运行：607a2a2f-c81b-49cb-9d42-18f7bee90e74；集合：all；UTC：2026-10-03T03:03:07.350441+00:00

所选现行契约检查：**BLOCKED**

第二阶段上线条件：**BLOCKED**

部分集合和模拟传输结果不能证明完整真实验收或容量达标。

| 检查 | 状态 | 预期 | 实测 | 类型 | 原因 / 位置 |
|---|---|---|---|---|---|
| backend | PASS | 后端时间、保护、缓存、安全、观测契约 | exit=0 | controlled-contract | 命令执行完成 portal-api/target/probe-raw/p210/backend.log |
| frontend | PASS | 前端加载、空、错误、不可用、部分与取消字段契约 | exit=0 | controlled-contract | 命令执行完成 portal-api/target/probe-raw/p210/frontend.log |
| browser-contract | PASS | 浏览器可控传输主流程及窄屏契约 | exit=0 | controlled-contract | 命令执行完成 portal-api/target/probe-raw/p210/browser-contract.log |
| tool-tests | PASS | 验收入口、判定、证据与性能分类自测 | exit=0 | controlled-contract | 命令执行完成 portal-api/target/probe-raw/p210/tool-tests.log |
| observability | BLOCKED | 现有指标在真实聚合调用链产生，拒绝及HTTP计数含义明确 | 未执行 | source-and-controlled-contract | 指标API与测试注册表已验证，但生产调用链未调用recordProtection；拒绝计数证据缺失，需后续观测变更 integration-tests/aggregation/matrix.md |
| build | PASS | 运行产物归属确认 | 见构建清单 | real-build | 运行产物与前端摘要及非敏感配置核对 report.json#build |
| unauthenticated | PASS | 未登录受保护接口拒绝且监控不公开 | [401, 401, 401, 401, 401, 401] | real-local | 匿名请求与监控关闭核验  |
| identity | BLOCKED | 专用双用户身份隔离与清理恢复 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| browser-real | BLOCKED | 当前产物真实浏览器四页面及登录刷新 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| reconciliation | BLOCKED | 固定范围真实 Token、日志与定价对账 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| upstream-evidence | BLOCKED | 原阶段统计、金额、趋势、流水、时区、增强元数据证据 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| performance-smoke | BLOCKED | 真实 1h/24h/7d 冷热明细与 30d 保护 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| performance-baseline | BLOCKED | 代表性规模每范围各 20 次冷、热有效请求 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| performance-target | BLOCKED | 已确认负载、部署资源及正式性能目标比较 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |

| 阶段条件 | 状态 | 依据 |
|---|---|---|
| P2-01 | BLOCKED | 跨日/DST 上游证据、小时趋势、stat 时间过滤、流水与新基线未闭合；integration-tests/aggregation/matrix.md |
| P2-02 | BLOCKED | 本轮适用检查结论；缺项不得用历史证据代替；integration-tests/aggregation/matrix.md |
| P2-03 | BLOCKED | 请求总数、成功率、正式金额与30d 原目标受阻；integration-tests/aggregation/matrix.md |
| P2-04 | BLOCKED | 双趋势、完整最近请求与原指标目标受阻；integration-tests/aggregation/matrix.md |
| P2-05 | BLOCKED | 正式金额、TOPUP/REFUND 与容量证据受阻；integration-tests/aggregation/matrix.md |
| P2-06 | BLOCKED | 真实充值/退款、正式金额与30d 原目标受阻；integration-tests/aggregation/matrix.md |
| P2-07 | BLOCKED | 增强元数据与增强计费可信来源缺失；integration-tests/aggregation/matrix.md |
| P2-08 | BLOCKED | 真实增强模型来源缺失，合成展示不解除阻塞；integration-tests/aggregation/matrix.md |
| P2-09 | BLOCKED | 本轮适用检查结论；缺项不得用历史证据代替；integration-tests/aggregation/matrix.md |
| P2-10 | BLOCKED | 真实环境、代表性规模、部署资源与正式性能目标缺失；integration-tests/aggregation/matrix.md |
| P2-09-protocol | CANCELLED | 用户于 2026-10-03 明确取消；docs/18_LANG-P2-09-请求日志协议与TTFT与费用信息增强说明.md#9 |
| P2-09-ttft | CANCELLED | 用户于 2026-10-03 明确取消；docs/18_LANG-P2-09-请求日志协议与TTFT与费用信息增强说明.md#9 |
