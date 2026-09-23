## Why

P2-03 已提供统一的 Dashboard 聚合接口，但当前页面仍分别依赖第一阶段用量摘要和小时趋势接口，无法展示六项指标的可用性、最近请求，也不能按冻结时区口径生成“今天/昨天”等自然日范围。现在需要完成前端迁移和下钻闭环，同时诚实呈现当前基线下不可用的指标、趋势与 30 天能力。

## What Changes

- 前端新增 `GET /portal/api/dashboard/stats` 的严格运行时 schema、查询参数构造、用户作用域缓存和会话失效处理，Dashboard 不再使用 `/portal/api/usage/summary` 与 `/portal/api/usage/timeseries` 作为正式概览来源。
- 将 1H、24H、今天、昨天、7D、30D 快捷范围转换为明确的 `[startTime,endTime)`、IANA `timezone` 与稳定 `granularity`；自然日按用户当前时区计算，切换范围时使用单一时钟快照。
- Dashboard 以六项指标、请求/消费趋势、最近请求和独立余额区组成；依据 `availability/reasonCode` 区分真实零值、无数据、部分覆盖和不可用，不在浏览器重算正式指标或伪造趋势。
- 当前 P2-03 保护边界下，30D 快捷项保留但明确标记为暂不可查询，不发送已知必然失败的实时聚合请求；待后端发布支持 30 天的新基线后复用同一参数模型开放。
- 最近请求展示安全裁剪字段和部分覆盖提示，并可携带明确时间边界、结果类型及可用的 Key/模型筛选进入现有请求日志页；请求日志页从受支持的 URL 参数初始化并保持筛选、分页和刷新语义。
- 为 Dashboard 各区域补齐加载、空数据、局部不可用、整体查询错误、会话失效和重试状态，保持余额与统计查询相互独立；完成桌面、窄屏、键盘以及 `zh-CN`/`en-US` 验证。
- 保留现有第一阶段用量接口及其他调用方，不删除或改变其 HTTP 契约。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `basic-usage-overview`: 将 Dashboard 从第一阶段摘要/小时趋势迁移到统一六指标、双趋势和最近请求概览，并规定快捷范围、可用性与 30 天受阻行为。
- `dashboard-statistics`: 明确 P2-04 前端消费新接口、旧用量接口兼容保留及当前保护边界下的客户端行为。
- `request-log-viewing`: 支持从 Dashboard 安全下钻，通过 URL 查询参数恢复明确时间范围和可用筛选条件。
- `frontend-application-shell`: 将 Dashboard 状态隔离模型更新为独立余额查询与统一统计快照内的分区展示，并保持响应式、导航和会话行为。

## Impact

- 主要影响 `frontend/src/api`、Dashboard/请求日志 React Query 数据层、`DashboardPage`、`RequestLogsPage`、中英文资源及相关单元/页面/可访问性测试。
- 复用现有 `Select`、`Button`、`Empty`、`DataTable`、`Pagination`、统一 Portal API 客户端、认证缓存清理、Clear Circuit 设计令牌和控制台布局；不引入新的样式系统或图表数据源。
- 不修改 P2-03 后端响应结构，不修改 New API，也不引入数据库、共享缓存或新第三方运行时依赖。若现有依赖不能满足可访问趋势图要求，优先实现项目内轻量 SVG 与文本数据替代，而不是扩大依赖面。
- LANG-P2-01/P2-03 尚未闭合的请求总数、消费、成功率、双趋势和 30 天性能证据仍是外部阻塞；本变更只负责正确展示其状态，不将其误报为已完成。
