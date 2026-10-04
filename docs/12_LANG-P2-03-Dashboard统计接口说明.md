# LANG-P2-03 Dashboard 统计接口说明

受保护的 `GET /portal/api/dashboard/stats`，一次返回同一受控日志快照派生的六项指标、两类趋势和最近请求。第一阶段 `/portal/api/usage/**` 保持不变，页面迁移属于 LANG-P2-04。

## 请求

只接受四个必填查询参数，不接受页面快捷范围、客户端口径版本、用户 ID 或未声明参数。

| 参数 | 说明 |
|---|---|
| `startTime` | 必填，带偏移量 ISO 8601 秒级时间，`[startTime,endTime)` 左闭右开 |
| `endTime` | 必填，同上，必须晚于 `startTime` |
| `granularity` | 必填，只能是 `FIVE_MINUTES`、`HOUR`、`DAY` 精确大写 |
| `timezone` | 必填，IANA 时区，例如 `UTC`、`Asia/Shanghai` |

- 缺失、为空、未知参数、非法粒度、`7D`/`30D`/`1H` 等快捷写法、客户端 `baselineVersion`/`userId` 一律 `INVALID_ARGUMENT`/400，且不访问上游。
- 未登录 `UNAUTHENTICATED`/401，且不访问用户级上游接口。
- 认证主体 ID 与 Cookie 用户 ID 不一致返回 `UNAUTHENTICATED`，缓存键使用认证主体 ID，不串用户。
- 时间和时区校验复用聚合基础设施：秒级精度、IANA 时区、粒度跨度组合（`FIVE_MINUTES` 24 小时、`HOUR` 7 天、`DAY` 30 天）。

## 成功响应

统一 `{requestId,data}` 包装。`data` 字段：

- `baselineVersion`：服务端冻结口径版本，当前 `p2-2026-09-22-a`，不接受客户端指定。
- `range`：`startTime`/`endTime`（UTC 规范化字符串）、原始 `timezone`、稳定 `granularity`。
- `metrics`：固定六项，每项含 `value`、`unit`/`currency`、`availability`、`reasonCode`。
- `requestTrend`/`spendTrend`：`availability`、`reasonCode`、单位/币种、`points`（按桶开始升序）。
- `recentRequests`：`availability`、`reasonCode`、`items`（最多 10 条）。

### availability / reasonCode

- 可用性：指标只允许 `AVAILABLE`/`UNAVAILABLE`，集合才允许 `PARTIAL`。
- `AVAILABLE` 必须有合法值且 `reasonCode=null`；`UNAVAILABLE` 必须 `value=null` 且有稳定原因。
- 原因初始集合：`BASELINE_NOT_VERIFIED`（证据不足）、`NO_DATA`（无数据）、`SOURCE_FIELD_MISSING`（来源字段缺失）、`PARTIAL_SOURCE_COVERAGE`（部分来源覆盖）。

### 当前口径下的可用性（`p2-2026-09-22-a`）

- 可用：`tokenUsage`（输入+输出精确合计，整数，单位 `tokens`）、`activeKeys`（非空 token ID 去重，整数，单位 `keys`）、`averageLatency`（总耗时/记录数，毫秒，最多三位小数 HALF_UP 去尾零）。
- 不可用：`requestTotal`、`spend`、`successRate` 一律 `UNAVAILABLE + BASELINE_NOT_VERIFIED`，不用零或估算代替。
- 空数据：`tokenUsage`/`activeKeys` 返回可用零值；`averageLatency` 返回 `UNAVAILABLE + NO_DATA`。
- 任一纳入记录缺 token ID 时 `activeKeys` 整项 `UNAVAILABLE + SOURCE_FIELD_MISSING`，不返回偏小计数。
- 累计溢出时整体失败，不返回截断值。
- 请求趋势和消费趋势均 `UNAVAILABLE + BASELINE_NOT_VERIFIED`，`points=[]`，消费币种 `null`。不用成功日志数量冒充请求趋势，不用 raw quota 冒充消费趋势。

### 最近请求裁剪

每条只含 `occurredAt`、可空 `requestId`/`keyName`/`model`、`outcome`、`inputTokens`、`outputTokens`、`durationMs`、`stream`。不含 token ID、完整 Key、raw quota、渠道、节点、供应商信息、原始错误。

- 按发生时间倒序、requestId/tokenId/model/Token/耗时稳定排序，最多 10 条，不分页。
- 当前只取已验证 type=2 记录，集合固定 `PARTIAL + PARTIAL_SOURCE_COVERAGE`；单条 `outcome=SUCCESS`。
- 缺可空展示字段保留记录并返回 `null`。

### 响应示例（字段示意）

```json
{
  "requestId": "req_xxx",
  "data": {
    "baselineVersion": "p2-2026-09-22-a",
    "range": {
      "startTime": "2026-09-01T00:00:00Z",
      "endTime": "2026-09-01T02:00:00Z",
      "timezone": "UTC",
      "granularity": "HOUR"
    },
    "metrics": {
      "requestTotal": {"value": null, "unit": "count", "availability": "UNAVAILABLE", "reasonCode": "BASELINE_NOT_VERIFIED"},
      "tokenUsage": {"value": 90, "unit": "tokens", "availability": "AVAILABLE", "reasonCode": null},
      "spend": {"value": null, "currency": null, "availability": "UNAVAILABLE", "reasonCode": "BASELINE_NOT_VERIFIED"},
      "activeKeys": {"value": 2, "unit": "keys", "availability": "AVAILABLE", "reasonCode": null},
      "successRate": {"value": null, "unit": "ratio", "availability": "UNAVAILABLE", "reasonCode": "BASELINE_NOT_VERIFIED"},
      "averageLatency": {"value": 2000, "unit": "ms", "availability": "AVAILABLE", "reasonCode": null}
    },
    "requestTrend": {"availability": "UNAVAILABLE", "reasonCode": "BASELINE_NOT_VERIFIED", "unit": "requests", "points": []},
    "spendTrend": {"availability": "UNAVAILABLE", "reasonCode": "BASELINE_NOT_VERIFIED", "currency": null, "points": []},
    "recentRequests": {
      "availability": "PARTIAL",
      "reasonCode": "PARTIAL_SOURCE_COVERAGE",
      "items": [
        {
          "occurredAt": "2026-09-01T01:05:00Z",
          "requestId": "req-3",
          "keyName": "probe-key-01",
          "model": "gpt-test",
          "outcome": "SUCCESS",
          "inputTokens": 10,
          "outputTokens": 20,
          "durationMs": 1000,
          "stream": false
        }
      ]
    }
  },
  "error": null
}
```

## 单快照与保护边界

- 指标、趋势、最近请求来自同一次受控 type=2 日志快照，共用范围校验、7 天实时扫描、10 页/200 条、30 秒总预算、用户隔离缓存（`dashboard-stats` 命名空间，TTL 30 秒）和低基数观测（`operation=dashboard-stats`，不带用户/模型/时间标签）。
- 达到范围、页数、记录数、超时保护，或分页总数不一致、上游不可用、映射失败时整个接口失败，不返回部分成功数据。
- 失败响应不进缓存；同键并发合并为一次上游加载；缓存命中复用业务数据但每次生成新的 `requestId`。
- 当前无 30 天实时性能证据，30 天日志查询在分页前稳定拒绝 `INVALID_ARGUMENT`，且不调用 `/api/log/self/stat`、`/api/data/self` 或浏览器明细计算降级。只有 P2-01 发布新证据或另行批准预聚合方案后才能放宽。

## 错误行为

| 情况 | 响应 |
|---|---|
| 未登录/会话缺失/主体不一致/重复 Cookie | `UNAUTHENTICATED`/401 |
| 参数缺失、未知参数、快捷范围、非法粒度/时间/时区、跨度超限、30 天实时扫描、200 条保护上限 | `INVALID_ARGUMENT`/400 |
| 上游 5xx/连接失败/超时、分页不一致、累计溢出 | 上游错误映射（`UPSTREAM_ERROR`/`UPSTREAM_TIMEOUT` 等），无部分 `data` |

## 与旧接口的关系

- 不修改、不移除 `/portal/api/usage/summary`、`/portal/api/usage/timeseries` 及 Dashboard 前端调用。
- 新旧口径不同，旧接口数据不得作为新接口正式值。
