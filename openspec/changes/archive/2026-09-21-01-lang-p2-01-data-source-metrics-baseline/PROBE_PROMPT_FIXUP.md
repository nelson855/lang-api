# LANG-P2-01 实测补测 Prompt（交给另一个 Coding Agent 执行）

> **背景**：上一轮探测已完成基础框架，但有 4 个场景的 fixture 实际上是同一响应的复制品，需要分别用不同参数真实调用，产出**不同的**真实响应。本 Prompt 描述补测范围，不要求重做全部探测。

## 0. 已就绪（不要重做）

- New API 地址：`http://127.0.0.1:13000`（本机回环）
- 普通测试用户：`test01` / `12345678`，**user_id = 2**
- opencode 渠道已配置，模型 `muse-spark-1.3-contributor-free` 与 `deepseek-v4.1-flash` 可用
- 已成功产生 9 条真实日志（见 `docs/new-api/samples/aggregation/log-self.nonempty.json`）
- 管理员账号：`root` / `Abc12345678!`

## 1. 红线（仍然适用）

1. 原始响应只写入 `portal-api/target/probe-raw/raw/`，不脱敏不提交
2. 脱敏用 `AggregationSanitizer` + `AggregationSensitiveScanner.scanOrThrow`
3. 凭证只从环境变量读取
4. 不修改 New API 数据库
5. 标 `已验证` 必须有真实证据；没证据就标 `条件可用` 或 `不可用`

## 2. 补测任务

### 任务 P1：`/api/log/self/stat` 分场景真实调用

当前问题：`log-self-stat.{nonempty,empty,bad-time,adjacent}.json` 4 个文件内容完全一样（sha256 相同），说明 Agent 只调了一次然后复制了 3 份。

**补测**：用 test01 session 分别调用以下 4 次，每次记录真实响应到 `portal-api/target/probe-raw/raw/log-self-stat/<场景>.json`：

```bash
SESSION=<test01 的 session cookie>
NOW=$(date +%s)
DAY_AGO=$((NOW - 86400))

# P1.1 nonempty: 覆盖过去 24 小时（test01 有 9 条日志）
curl -s "http://127.0.0.1:13000/api/log/self/stat?type=0&start_timestamp=${DAY_AGO}&end_timestamp=${NOW}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self-stat/nonempty.json

# P1.2 empty: 未来时间窗口（不应有任何数据）
FUTURE_START=$((NOW + 86400))
FUTURE_END=$((NOW + 172800))
curl -s "http://127.0.0.1:13000/api/log/self/stat?type=0&start_timestamp=${FUTURE_START}&end_timestamp=${FUTURE_END}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self-stat/empty.json

# P1.3 bad-time: start_timestamp > end_timestamp
curl -s "http://127.0.0.1:13000/api/log/self/stat?type=0&start_timestamp=${NOW}&end_timestamp=${DAY_AGO}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self-stat/bad-time.json

# P1.4 adjacent: 两个相邻区间分别调用，各自写入不同文件
#   区间 A: [DAY_AGO, MID)  区间 B: [MID, NOW)
MID=$((DAY_AGO + 43200))
curl -s "http://127.0.0.1:13000/api/log/self/stat?type=0&start_timestamp=${DAY_AGO}&end_timestamp=${MID}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self-stat/adjacent-a.json
curl -s "http://127.0.0.1:13000/api/log/self/stat?type=0&start_timestamp=${MID}&end_timestamp=${NOW}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self-stat/adjacent-b.json
```

**验证**：4 个文件的 sha256 应该**不全相同**（至少 nonempty 应该和 empty 不同，因为一个有数据一个没数据）。

**如果 nonempty 和 empty 仍然相同**（例如 New API 忽略时间参数），那就**如实记录**：「New API `/api/log/self/stat` 似乎不按 start/end_timestamp 过滤，所有查询返回同一汇总」，并把这 4 个场景在兼容性矩阵里标 `存在差异`，不要伪装成 `已验证`。

**adjacent 场景**：把 adjacent-a 和 adjacent-b 两个响应合并成一个 JSON 对象写入 `adjacent.json`（格式 `{"a":<响应A>,"b":<响应B>}`），或在兼容性矩阵里如实说明。

### 任务 P2：`/api/data/self` 等待并再测

当前问题：所有场景返回相同空 `data:[]`。原因可能是：
- New API 的小时聚合 `quota_data` 表有延迟（一般几分钟到 1 小时）
- 或者 `start_date`/`end_date` 参数格式不对

**补测**：

1. **先观察现有数据**：`GET /api/data/self?start_date=<3天前>&end_date=<今天>` 带 test01 session，看响应是否仍为空。
2. **等待小时聚合**：如果 New API 有后台定时任务（通常每小时跑），等 1-2 小时再测。
3. **如果依然空**：
   - 不要造假数据
   - 在 `data-self.nonempty.json` 里**保留空响应**（因为它就是真实响应）
   - 但在兼容性矩阵里把这个接口的「小时桶数据」字段标 `条件可用`，注明「需要更长观察窗口」
4. **如果能等到非空数据**：
   - 重新调用并将真实非空响应写入 `raw/data-self/nonempty.json`
   - 用 `start_date=<昨天>&end_date=<昨天>` 调一次验证单日
   - 用 `start_date=<今天>&end_date=<今天>` 调一次验证当日（hourly-edge）
   - 用错误格式 `start_date=abc` 调一次（bad-time）
   - 请求一次日志后立刻调用一次，记录是否即时反映（latency）

### 任务 P3：`/api/user/topup/self` 尝试构造充值记录

当前问题：test01 无充值记录，nonempty 与 empty 相同。

**补测选项**（按优先级）：

**选项 A（推荐，用 New API 后台模拟）**：
- 用管理员账号 `POST /api/user/topup`（如果 New API 提供管理员代充接口）或在管理员后台「钱包/充值」页面手工给 test01 加一笔 1 元测试充值
- 然后 `GET /api/user/topup/self?p=1&page_size=10` 带 test01 session 拿真实非空响应

**选项 B（如果 New API 不支持管理员代充）**：
- 保持现状（empty 状态），并在兼容性矩阵和 baseline JSON 中**如实记录**：「测试环境无法构造真实充值记录，充值接口标记 `条件可用`」
- 删除 `topup.nonempty.json` 这个文件名（因为它是误导性的，内容其实是空的），或者把它重命名为 `topup.empty-admin-view.json` 并调整 manifest

**不要**为了凑 nonempty 而把 empty 的内容复制改名。

### 任务 P4：`/api/log/self` 补 3 个真实场景

当前问题：`log-self.time-edge.json`、`log-self.type-1.json`、`log-self.type-5.json` 都是空 items（`total:0`）。

**补测**：

```bash
# P4.1 time-edge: 用 start_timestamp == end_timestamp 调用，观察是否返回空、还是报错
curl -s "http://127.0.0.1:13000/api/log/self?p=1&page_size=20&type=0&start_timestamp=${NOW}&end_timestamp=${NOW}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self/time-edge.json

# P4.2 type-1: 查询充值类型日志（如果 test01 充值过，应该有；没充值过就接受空响应）
curl -s "http://127.0.0.1:13000/api/log/self?p=1&page_size=20&type=1&start_timestamp=${DAY_AGO}&end_timestamp=${NOW}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self/type-1.json

# P4.3 type-5: 错误日志。如果之前 P1 没产生 type=5 日志，用 Key A 调一次不存在的模型产生 1 条
curl -s -X POST "http://127.0.0.1:13000/v1/chat/completions" \
  -H "Authorization: Bearer <test01 的 Key A 完整 key 值>" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-nonexistent-for-probe","messages":[{"role":"user","content":"hi"}],"max_tokens":5}'
# 等 5 秒让日志落库
sleep 5
curl -s "http://127.0.0.1:13000/api/log/self?p=1&page_size=20&type=5&start_timestamp=${DAY_AGO}&end_timestamp=${NOW}" \
  -H "Cookie: session=${SESSION}" -H "New-Api-User: 2" \
  -o portal-api/target/probe-raw/raw/log-self/type-5.json
```

**如果 type-5 仍然是空**，就在兼容性矩阵里说明「New API v0.13.2 的 `/api/log/self?type=5` 似乎不返回错误日志（或该类型用途不同）」，并把成功率指标的"分母是否包含 type=5"标为 `存在差异` 待后续裁决。

## 3. 重新生成 fixture 和 manifest

补测完成后，重新跑：

```bash
cd /Users/nelson/ai-projects/2026/20260910-lang-api/lang-api
LANG_PORTAL_AGGREGATION_PROBE_ENABLED=true \
LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR=portal-api/target/probe-raw \
mvn -pl portal-api test -Dtest='AggregationFixtureMaterializationTests' -q
```

这个测试会重新读取 `portal-api/target/probe-raw/raw/...` 的最新原始响应，重新脱敏并写入 `docs/new-api/samples/aggregation/`，重新生成 manifest.json（sha256 会更新）。

**注意**：如果有某个场景你没补测（比如 topup 实在没法构造非空），就从 `AggregationFixtureMaterializationTests.fixtures()` 列表中删除那一项，避免它读到旧原始响应并复用旧 fixture。同时**手动删除** `docs/new-api/samples/aggregation/topup.nonempty.json` 避免误导。

## 4. 更新 aggregation-baseline.json

打开 `docs/new-api/aggregation-baseline.json`：

1. 把 `measuredAt` 改为新的实测时间（与 manifest.json 中的 `measuredAt` 一致）
2. 根据补测结果更新 `interfaces` 中每个接口的 `结论`：
   - `/api/log/self`：如果 type-5/time-edge 拿到了真实响应（哪怕空），保持 `已验证`
   - `/api/log/self/stat`：如果 nonempty/empty 响应**不同**（证明时间参数生效），保持 `已验证`；否则改为 `存在差异`
   - `/api/data/self`：如果拿到了真实非空 hourly 数据，保持 `已验证`；如果依然空，改为 `条件可用` 并注明「小时桶延迟待观察」
   - `/api/user/topup/self`：如果构造出充值记录，保持 `条件可用`；否则也保持 `条件可用` 并注明「无充值数据」
3. 在 `metrics` 里，把对应分母/分子涉及到未验证字段的指标保持 `条件可用` 不变

## 5. 更新三份中文文档

按补测结果修订：
- `docs/new-api/第二阶段聚合数据兼容性矩阵.md`：每个接口的「结论」列改为补测后的真实状态
- `docs/new-api/第二阶段统计与流水口径.md`：如果某项指标的字段依然缺证据，明确写「不可用」
- `docs/new-api/第二阶段聚合性能基线.md`：保持现状（本轮不做性能测量）

## 6. 跑所有测试确认绿

```bash
mvn -pl portal-api test \
  -Dtest='AggregationProbeGateTests,AggregationSanitizerTests,AggregationSensitiveScannerTests,AggregationBaselineJsonContractTests,AggregationManifestContractTests' \
  -q
```

5 个测试类必须全绿。

## 7. 验收清单（完成后回报给我）

- [ ] `log-self-stat` 的 4 个场景 fixture 的 sha256 **不全相同**（或明确记录「时间参数不生效」并标 `存在差异`）
- [ ] `data-self.nonempty.json` 要么包含真实小时桶数据，要么明确标 `条件可用`
- [ ] `topup.nonempty.json` 要么包含真实充值记录，要么从 manifest 中删除此条目
- [ ] `log-self.time-edge/type-1/type-5.json` 是用对应参数真实调用的结果（哪怕是空响应也算）
- [ ] `aggregation-baseline.json` 的 `measuredAt` 与 manifest.json 一致
- [ ] 三份中文文档反映补测后的真实状态
- [ ] 5 个测试类全绿
- [ ] `git status` 里 `portal-api/target/probe-raw/` 不出现

完成后回来告诉主会话 nelson：「补测完成，可以继续任务 4.x–8.x」。
