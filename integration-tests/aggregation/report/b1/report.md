# 第二阶段聚合验收报告

运行：170e7bd4-c4c4-4039-a9e0-d472079509fa；集合：B1-follow-up；UTC：2026-10-03T07:31:28.559274+00:00

所选现行契约检查：**PASS**

第二阶段上线条件：**BLOCKED**

部分集合和模拟传输结果不能证明完整真实验收或容量达标。

| 检查 | 状态 | 预期 | 实测 | 类型 | 原因 / 位置 |
|---|---|---|---|---|---|
| backend | PASS | 后端时间、保护、缓存、安全、观测契约 | 783项；0失败、0错误、1项既有跳过 | controlled-contract | 指定Maven/settings.xml后端test通过 portal-api/target/probe-raw/p210/b1/lang-b1-full.log |
| tool-tests | PASS | 验收入口、判定、证据与性能分类自测 | 17项通过 | controlled-contract | Python unittest通过 portal-api/target/probe-raw/p210/b1/lang-b1-python-final.log |
| observability | PASS | 现有指标在真实聚合调用链产生，拒绝及HTTP计数含义明确 | 双聚合入口16场景通过 | controlled-http-and-process-registry | 受控HTTP来源及进程内注册表；不代表运行容器指标 observability.json |

| 阶段条件 | 状态 | 依据 |
|---|---|---|
| P2-01 | BLOCKED | 跨日/DST 上游证据、小时趋势、stat 时间过滤、流水与新基线未闭合；integration-tests/aggregation/matrix.md |
| P2-02 | PASS | 本轮适用检查结论；缺项不得用历史证据代替；integration-tests/aggregation/matrix.md |
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
