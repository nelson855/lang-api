# 第二阶段聚合验收报告

运行：ac09c6b2-82fd-4633-8794-0bc9476fcb4b；集合：performance；UTC：2026-10-03T10:15:47.407233+00:00

所选现行契约检查：**BLOCKED**

第二阶段上线条件：**BLOCKED**

部分集合和模拟传输结果不能证明完整真实验收或容量达标。

| 检查 | 状态 | 预期 | 实测 | 类型 | 原因 / 位置 |
|---|---|---|---|---|---|
| build | PASS | 运行产物归属确认 | 见构建清单 | real-build | 运行产物与前端摘要及非敏感配置核对 report.json#build |
| performance-smoke | PASS | 真实 1h/24h/7d 冷热明细与 30d 保护 | 7 次；仅冒烟 | real-small-sample | 隔离实例逐请求日志差分；完整固定成功样本计数；TTL过期后一次来源读取，热查询无来源读取且结果一致；不是容器指标注册表 report.json#samples |
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

## B6 实测明细

固定结束时间：2026-10-03T09:20:00Z；UTC。每个成功窗口1条真实成功日志。

| 范围 | 冷查询 ms | 热查询 ms | 冷/热日志HTTP调用 |
|---|---:|---:|---|
| 1h | 98.127 | 21.803 | 1/0 |
| 24h | 30.521 | 17.492 | 1/0 |
| 7d | 26.43 | 18.487 | 1/0 |

30d返回400/INVALID_ARGUMENT，日志调用0；相同合法DAY参数的7d对照返回200。缓存由隔离实例来源事件差分、固定结果和TTL确认，不按耗时推断；没有读取运行时指标注册表。

临时实例和镜像已删除，登出并清除客户端状态；未新增模型调用费用。

完整verify当次后端5项失败/错误；前端766项与lint/build通过，之后单独跳过后端测试打包用于采样。验证摘要见[validation.json](validation.json)。B6 PASS不覆盖完整构建失败，B7/B8继续BLOCKED。
