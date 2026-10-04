# LANG-P2-04 Dashboard 前端闭环说明

> 范围：Dashboard 页面与请求日志 URL 下钻的前端落地。服务端契约见 `docs/12_LANG-P2-03-Dashboard统计接口说明.md`，本文件只描述前端结构与行为。

## 1. 页面结构

Dashboard 页面位于 `frontend/src/pages/DashboardPage.tsx`，按四层组织：

1. 范围工具栏：快捷范围 `Select`（1H/24H/今天/昨天/7D/30D），30D 在当前 P2-03 边界下标记 `disabled` 并展示阻塞说明。
2. 当前余额：独立 `useBalanceQuery` 查询，明确提示与区间消费相互独立；失败时显示可重试错误，不影响统计区。
3. 六指标网格 + 双趋势：仅调用一次 `GET /portal/api/dashboard/stats`，指标可用性由 `availability/reasonCode` 决定；趋势由轻量 SVG `TrendChart` 渲染，包含屏幕阅读器可读的文本数据替代表。
4. 最近请求：`DataTable` 桌面表格 + 窄屏卡片，每行提供「查看日志」下钻入口。

页面复用 `Button`、`Select`、`Feedback`、`DataTable` 与 Clear Circuit 设计令牌，未引入新的屏幕级样式系统。

## 2. 范围与 preset 映射

`frontend/src/features/dashboard/dashboardRange.ts` 定义 `DASHBOARD_PRESETS`：

| preset | 粒度 | 类型 | 说明 |
|--------|------|------|------|
| `1h` | `FIVE_MINUTES` | rolling | 滚动 1 小时 |
| `24h` | `HOUR` | rolling | 滚动 24 小时 |
| `today` | `HOUR` | calendar | 用户时区自然日 00:00 起 |
| `yesterday` | `HOUR` | calendar | 用户时区前一自然日 |
| `7d` | `HOUR` | rolling | 滚动 7 天 |
| `30d` | `DAY` | rolling | `enabled=false`，当前不可请求 |

- 滚动范围按绝对时长（秒）计算，边界统一截断到秒（毫秒清零）。
- 自然日使用 `Intl.DateTimeFormat` 在用户 IANA 时区中求真实瞬时边界，可正确处理 `America/New_York` 等 DST 切换日的 23/25 小时。
- 无效时区回退 UTC。
- `buildDashboardRange` 返回不可变对象；同一 `(preset, now, timezone)` 输入产生等价值，便于查询键稳定。

## 3. 查询与缓存

`useDashboardStatsQuery` 位于 `frontend/src/features/dashboard/useDashboard.ts`：

- `queryKey` 为 `['portal','dashboard-stats', userId, startTime, endTime, granularity, timezone]`，不同用户或范围不共享缓存。
- `enabled: dashboardRequestsEnabled(range)`，30D preset 因为 `enabled=false` 不会发出 HTTP 请求。
- `retry: false`，所有失败进入页面错误区，由用户主动点击「重试」触发单次 refetch。
- 任一 `UNAUTHENTICATED` 错误通过 `clearAuthenticatedScope` 进入统一会话失效流程，与认证、usage、request-logs 的缓存清理一致。

## 4. 可用性展示模型

`frontend/src/features/dashboard/displayModel.ts` 把服务端 `(availability, reasonCode, value)` 映射到三种展示状态：

- `value`：可用，按 kind （count/money/ratio/average） 用当前 locale 与 `Intl.NumberFormat` 格式化；真实零照常显示。
- `no-data`：`NO_DATA`，显示「该范围内暂无数据」。
- `unavailable`：其他 reasonCode，显示对应原因文案；文案见 `pages.dashboard.reasonBaselineNotVerified`、`reasonSourceFieldMissing`、`reasonPartialSourceCoverage`。

集合（趋势、最近请求）额外支持 `partial`：在数据上方展示部分覆盖提示。

## 5. 趋势组件

`frontend/src/features/dashboard/TrendChart.tsx` 输出：

- `<figure>` 语义容器 + `<svg role="presentation">` 折线（aria-hidden）。
- 屏幕阅读器可读的 `<table>` 文本替代（`.visually-hidden`），逐点列出 bucket 与 value。
- 横轴标签使用用户时区与活动 locale 的 `Intl.DateTimeFormat` 格式化，不展示原始 UTC 字符串。
- 空点列不挂载 SVG，直接渲染 `role="img"` 的说明区。
- `prefers-reduced-motion: reduce` 时跳过路径动画（通过 `matchMedia` 与 CSS `@media` 双保险）。

非法数值点在构造折线时被丢弃；若整列都非法，按空点列处理。

## 6. 最近请求下钻

每行末尾的「查看日志」生成：

```
/dashboard/request-logs?startTime=<r.startTime>&endTime=<r.endTime>&result=SUCCESS[&keyName=...][&model=...]&page=1
```

- `startTime/endTime` 来自当前 Dashboard 范围（服务端回显边界）。
- `keyName/model` 仅当行内非空时附加，URL 编码。
- 总是从 `page=1` 开始，不带额外排序或预设参数。

## 7. 请求日志 URL 适配

`frontend/src/features/requestLogs/requestLogsUrl.ts` 是 URL 与筛选状态的单一适配层：

- `parseRequestLogsSearch` 把 `location.search` 解析为 `NormalizedRequestLogsParams`；非法 `result/page` 回退默认值、单边时间或非法 ISO 字符串清空两侧、未知参数忽略。
- `serializeRequestLogsSearch` 反向序列化，省略默认值保持 URL 干净。
- `isDashboardDrilldownRange` 判定是否携带完整起止时间，用于切换范围输入形态。

`RequestLogsPage` 完全由 URL 驱动：

- 进入、刷新、前进/后退从 URL 恢复；首次进入若 URL 含非法参数，用 `replace` 回写规范化后的 search，不留下脏历史。
- 提交筛选 `push` 一条新历史并强制 `page=1`；翻页只更新 `page`。
- 携带完整起止时间时（Dashboard 下钻），范围选择器被替换为 `Dashboard 所选范围：{{start}} — {{end}}` 说明，提交筛选不会覆盖起止时间。
- 刷新按钮只 `refetch` 当前 URL 对应的查询，不重设筛选。

## 8. 错误与一致性

- 余额失败 / 统计失败：互不影响，各自区域显示可重试的错误提示，不混用旧范围数据。
- 统计内部分不可用：可用指标照常展示，不可用指标显示文本原因，部分覆盖显示说明，不只依赖颜色。
- 任一 `UNAUTHENTICATED`：进入统一会话失效流程，清除认证范围缓存（含 Dashboard 快照与既有 usage/request-log 缓存）。
- 加载、错误、空、不可用区域在 CSS 中保持高度稳定，避免重试时跳动。

## 9. 本地化

`zh-CN` 与 `en-US` 均覆盖：六指标、六个快捷范围、四种可用性原因、30D 阻塞说明、趋势/最近请求文案、范围汇总插值（`{{start}}/{{end}}/{{timezone}}/{{granularity}}`）、下钻范围提示（`{{start}}/{{end}}`）。键集合在两语言间保持一致。

## 10. 外部阻塞

- 30D 实时查询仍受 P2-01 性能证据缺失约束，前端已将 30D 标记 `enabled=false` 并显示阻塞说明，**不会**发出 stats 请求。
- 双趋势、请求总数、消费、成功率在 P2-03 服务端保持 `UNAVAILABLE`，前端按可用性模型诚实展示，不伪造数据。
