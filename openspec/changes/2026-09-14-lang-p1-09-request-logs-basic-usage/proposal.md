## Why

P1-07 已建立真实模型调用链路，P1-08 已让用户选择模型并复制可运行示例，但控制台仍无法解释调用是否成功、消耗了多少 Token 和额度、当前余额如何变化。LANG-P1-09 需要在继续以 New API 为日志和额度权威的前提下，补齐当前用户可验证的请求明细与基础用量闭环。

## What Changes

- 新增受保护的 `GET /portal/api/request-logs`，适配 New API v0.13.2 `/api/log/self`，只展示模型调用相关的 `CONSUMPTION` 和 `ERROR` 记录。
- 请求日志支持服务端分页以及 Key 名称、模型、结果类型和时间范围筛选；默认每页 20、最大 100，时间默认最近 24 小时、最长 30 天。
- 将日志时间、Key 名称、模型、输入/输出 Token、耗时、流式标识、结果、额度费用和 requestId 转换为稳定自有字段；协议类型、首字延迟等缺失数据返回 `null`。
- 严格移除日志正文、扩展数据、IP、用户/Token/渠道标识、分组和上游错误内容，防止内部路由、供应商和请求内容进入普通用户页面。
- 新增受保护的 `GET /portal/api/usage/summary`，适配 `/api/log/self/stat`，返回区间消费以及 New API 当前的一分钟 RPM/TPM 口径。
- 新增受保护的 `GET /portal/api/usage/timeseries`，适配 `/api/data/self` 的小时粒度数据，并在后端合并同一小时的模型行，返回请求量、Token 和消费趋势。
- 新增受保护的 `GET /portal/api/account/balance`，从 `/api/user/self` 裁剪当前剩余额度并转换为明确的 USD 显示金额，供 P1-09 概览和后续 P1-10 钱包复用。
- 所有时间查询使用带时区 ISO 8601 和左闭右开 `[startTime,endTime)`；Portal 负责转换冻结版 New API 的秒级、右端包含查询语义。
- 将 `/dashboard` 从占位页替换为基础用量概览，各区域独立加载和失败；新增 `/dashboard/request-logs` 控制台导航与请求明细页面。
- 复用 P1-08 已冻结的 `quota-per-usd` 和 `BigDecimal` 金额口径，前端不复制 quota 换算公式，也不从分页日志计算正式统计。
- 增加用户隔离、时间边界、分页筛选、字段裁剪、金额换算、小时合并、局部失败和 P1-07 真实调用后日志/余额变化的验证。

## Capabilities

### New Capabilities

- `request-log-viewing`: 定义当前用户模型请求日志的权限、分页筛选、字段语义、隐私裁剪和页面状态。
- `basic-usage-overview`: 定义区间用量摘要、小时级趋势、时间边界及基础控制台概览。
- `account-balance`: 定义当前用户剩余额度与显示金额的只读接口，作为 P1-09 概览和 P1-10 钱包的共享基础。

### Modified Capabilities

- `new-api-adapter`: 增加冻结版个人日志、日志统计、小时用量和余额查询的语义化受保护操作及安全字段转换。
- `frontend-application-shell`: 将 Dashboard 占位内容替换为真实基础用量概览，并增加 `/dashboard/request-logs` 受保护路由和控制台导航。

## Impact

- Portal API：新增 usage/account Web DTO、查询服务和 Controller，以及 `upstream.newapi` 下的 log、usage 与 balance 适配类型；继续复用统一分页、会话、错误和 requestId 契约。
- 前端：新增请求日志、用量和余额 API schema/query，扩展控制台路由与导航，替换 `DashboardPage` 并增加 `RequestLogsPage` 和对应功能组件。
- 配置：复用 `lang.portal.catalog.quota-per-usd` 作为当前统一 quota→USD 参数；新增时间范围和分页上限时必须在共享及 dev/test/prod `.properties` 中显式配置。
- 数据：不增加 Portal 数据库、统计表、后台同步任务或 Redis；所有权威数据仍来自 New API HTTP API。
- 前置条件：自动化契约使用脱敏 fixture；最终验收需要启用 New API 消费日志与小时数据导出，并提供可真实调用的 P1-06 Key、模型渠道和测试额度。
