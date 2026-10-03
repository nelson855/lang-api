# 第二阶段聚合验收报告

## 会话撤销修复复验（2026-10-03 19:33 UTC）

运行：34c3c682-3ae3-4d6b-ae7a-91f671f449bf；集合：local；隔离实例：回环18081，镜像含当前构建JAR，无挂载。

所选现行契约检查中与身份相关的三项：**build PASS、unauthenticated PASS、identity PASS**

| 检查 | 状态 | 实测 | 证据 |
|---|---|---|---|
| build | PASS | 运行JAR、前端58个文件、镜像、上游v0.13.2/digest与有效配置一致（无挂载，配置可确认） | report-reverify.json#build |
| unauthenticated | PASS | 6个受保护接口与监控端点全部拒绝 | report-reverify.json |
| identity | PASS | 真实双身份断言失败数=0 | identity-reverify.json |

**身份断言全部通过**（两名专用测试身份，窗口2026-10-03T08:16:22Z～09:16:22Z，复用既有真实日志，未新增供应商调用）：

- 非空Key列表与跨用户详情404；
- 各一条真实成功日志归属，互不混入；
- Dashboard/账户汇总/消费流水隔离；相同窗口冷查询6次上游调用、热查询0次，由隔离实例真实上游事件计数确认；
- 伪造用户参数与无效会话Cookie均被拒绝；
- 临时Key清理与客户端Cookie清除通过；
- **退出后重放：两名用户均返回401，且 profile、Dashboard、账户汇总、消费流水、请求日志、Key、Usage 共7个受保护接口无一例外（`nonUnauthorizedEndpoints` 为空）**——包含已被预热的聚合缓存路径。

**跨实例与重启**（两个独立容器共享同一 Redis，非进程内模拟）：

- 实例1登录 → 实例2用同一Cookie访问返回200（会话确实共享）；
- 实例1退出 → 实例2立即重放返回401（撤销跨实例立即生效）；
- 应用实例重启后重放退出前Cookie，仍返回401（已撤销会话不复活）。

**失败路径演练**：受控失败注入后临时Key已删除、客户端Cookie已清除，PASS。

**自动化验证**：后端823项通过（0失败、0错误、1项既有跳过）；真实Redis 7项通过（跨实例撤销可见、删除后新连接不复活、TTL自动过期）；验收工具23项通过。

**证据与清理**：复用B2已创建的两个测试账号与既有真实日志，未新增供应商调用与费用；临时Key与会话在正常及失败路径均清理；持久卷与测试账号保留。证据为白名单投影并通过既有敏感扫描。

**剩余限制**：上游New API自身的退出仍不真正作废其会话（本轮已实测确认），Portal侧的撤销由Lang API自身会话记录承担，不依赖上游行为；登录限流仍是进程内状态，多实例部署时不具备跨实例一致性，属独立议题。

## 首轮结果（历史记录，已被上述复验取代）

运行：67fb9785-c7fd-4ba0-91c0-5f191c7905d1；集合：local；UTC：2026-10-03T09:16:31.416950+00:00

所选现行契约检查：**FAIL**

第二阶段上线条件：**BLOCKED**

部分集合和模拟传输结果不能证明完整真实验收或容量达标。

| 检查 | 状态 | 预期 | 实测 | 类型 | 原因 / 位置 |
|---|---|---|---|---|---|
| build | PASS | 运行 JAR、镜像、前端、上游版本与有效配置一致 | 见构建清单 | real-build | 运行产物与前端摘要及非敏感配置核对 report.json#build |
| unauthenticated | PASS | 未登录受保护接口拒绝且监控不公开 | [401, 401, 401, 401, 401, 401] | real-local | 匿名请求与监控关闭核验  |
| identity | FAIL | 专用双用户身份隔离与清理恢复 | 真实双身份断言失败数=2 | real-local-dual-user | 非空日志、Key、聚合缓存、伪造身份、退出旧会话重放及资源清理实测 identity.json |
| browser-real | BLOCKED | 当前产物真实浏览器四页面及登录刷新 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| reconciliation | BLOCKED | 固定范围真实 Token、日志与定价对账 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |
| upstream-evidence | BLOCKED | 原阶段统计、金额、趋势、流水、时区、增强元数据证据 | 未执行 | none | 缺少当前构建、专用身份或经核验的真实证据  |

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
