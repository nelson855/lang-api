## MODIFIED Requirements

### Requirement: 新接口不改变第一阶段用量接口
Dashboard 统计接口 SHALL 与现有 `/portal/api/usage/summary`、`/portal/api/usage/timeseries` 并存，旧接口的路径、参数和响应 MUST 保持兼容。LANG-P2-04 起，`/dashboard` MUST 只使用 `/portal/api/dashboard/stats` 作为六项指标、双趋势和最近请求的正式来源；旧接口不得被前端用来补齐不可用指标、趋势或超出实时保护边界的范围。其他既有调用方仍可按原契约使用旧接口。

#### Scenario: P2-04 页面迁移后加载 Dashboard
- **WHEN** 用户访问已部署 P2-04 的 `/dashboard`
- **THEN** 页面以明确的 `startTime`、`endTime`、`granularity` 和 `timezone` 调用新统计接口，不调用旧摘要或小时趋势接口生成概览

#### Scenario: 新接口返回不可用数据
- **WHEN** 新统计接口把指标或趋势标记为不可用
- **THEN** 页面直接呈现该契约状态，不查询旧接口、请求日志分页或其他来源补值

#### Scenario: 第一阶段接口保持兼容
- **WHEN** Dashboard 完成迁移后其他既有调用方继续访问 `/portal/api/usage/summary` 或 `/portal/api/usage/timeseries`
- **THEN** 两个接口仍按原路径、参数、响应和权限契约工作，不因 P2-04 被删除或改变

#### Scenario: P2-03 部署但前端尚未迁移
- **WHEN** 环境只部署 P2-03 后端而仍运行 P2-04 之前的前端
- **THEN** 旧 Dashboard 和旧用量接口继续按原契约工作，新 stats 接口可独立调用；部署 P2-04 前端后才切换正式概览来源
