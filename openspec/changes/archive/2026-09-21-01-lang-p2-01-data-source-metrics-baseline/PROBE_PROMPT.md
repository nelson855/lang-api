# LANG-P2-01 实测探测 Prompt（交给另一个 Coding Agent 执行）

> **使用方式**：把本文件全文复制给另一个不受沙箱限制的 Coding Agent（例如在本机终端运行 `claude`），让它按下述步骤完成实测，产出物写入 `docs/new-api/samples/aggregation/` 与 `docs/new-api/`。完成后回来告诉我（nelson 主会话）即可继续任务 4.x–8.x。

## 0. 任务目标

在冻结版 New API v0.13.2 上完成 LANG-P2-01 的真实探测：
- 为六个接口（`/api/log/self`、`/api/log/self/stat`、`/api/data/self`、`/api/user/self`、`/api/user/topup/self`、`/api/pricing`）收集真实响应
- 通过 opencode 渠道产生足够的成功/失败/流式/非流式/跨日界日志
- 所有原始响应**只写入 `portal-api/target/probe-raw/`**（不得提交），**脱敏后**才能写入 `docs/new-api/samples/aggregation/`
- 脱敏工具、敏感扫描、manifest 校验器已就绪：`com.lang.portal.upstream.newapi.probe.AggregationSanitizer`、`AggregationSensitiveScanner`、`AggregationManifestValidator`

## 1. 已就绪的环境与凭证

- New API 地址：`http://127.0.0.1:13000`（本机回环，不暴露公网）
- 冻结镜像：`calciumion/new-api:v0.13.2@sha256:0c6aa7afce4747f0fc4fab9c7934d7c2e4b69fda6844065acca6a5e1bf258506`
- 冻结 commit：`bee339d279ccecbf8c8a89e14ddbbd902f78bd5d`
- 管理员账号：用户名 `root`（如不是请 `GET /api/setup` 确认），密码 `Abc12345678!`
- 普通测试用户：`test01` / `12345678`，**user_id = 2**
- opencode 渠道：
  - Base URL：`https://opencode.ai/zen/go/v1`
  - API Key：`oc_sk_623b415ead1a_OkHSaItqWrg8oZSjla5i-avk9lff0Pfc`
  - 目标模型：`muse-spark-1.3-contributor-free`（免费模型）

## 2. 必须遵守的红线（违反任何一条都不要提交）

1. **原始响应只写入 `portal-api/target/probe-raw/`**（该目录已被 `.gitignore` 覆盖；写入前确认 `git status` 中看不到这些文件）
2. **不脱敏不提交**：进入 `docs/new-api/samples/aggregation/` 的文件必须通过 `AggregationSanitizer` + `AggregationSensitiveScanner.scanOrThrow` 检查
3. **凭证只从环境读取**：把以下变量放进你的进程环境（不要写入任何文件）：
   ```
   LANG_PORTAL_AGGREGATION_PROBE_ENABLED=true
   LANG_PORTAL_PROBE_USERNAME=test01
   LANG_PORTAL_PROBE_PASSWORD=12345678
   LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR=portal-api/target/probe-raw
   ```
   opencode API Key 与管理员密码也只在内存里使用，不写入仓库
4. **不使用 New API 数据库直接造数据**——所有日志必须来自真实 API 调用（哪怕是失败的）
5. **不能把金额负值当作退款**——没找到显式退款字段就把退款标 `不可用`

## 3. 步骤总览

### 阶段 A：环境与登录（对应任务 2.1）

1. 访问 `GET http://127.0.0.1:13000/api/status` 确认 200
2. 访问 `GET http://127.0.0.1:13000/api/setup` 确认已初始化
3. 用管理员账号 `POST /api/user/login` 拿 admin session cookie，记录到内存
4. 用 test01 账号 `POST /api/user/login` 拿 user session cookie，记录到内存
5. 记录探测环境元数据到 `portal-api/target/probe-raw/environment.json`：
   - `measuredAt`（UTC ISO 8601）
   - `machine`（`uname -a`）
   - `dockerInfo`（`docker version --format json` 的 Server 段）
   - `composeProject`：`lang-api`
   - `frozenImage`、`frozenCommit`、`frozenRelease`

### 阶段 B：配置 opencode 渠道（管理员身份）

通过 admin session 调 New API 后台：

1. `POST /api/channel/` 创建一个 OpenAI 兼容渠道：
   - `type=1`（OpenAI 兼容）
   - `name=opencode-muse`
   - `base_url=https://opencode.ai/zen/go/v1`
   - `key=oc_sk_623b415ead1a_OkHSaItqWrg8oZSjla5i-avk9lff0Pfc`
   - `models=muse-spark-1.3-contributor-free`
   - `group=default`
2. `GET /api/channel/?p=1&page_size=20` 确认渠道创建成功，记录返回的 `id`
3. `POST /api/channel/test` 或对应端点测试连通性（如果 New API 提供）

### 阶段 C：为测试用户创建两个 API Key（普通用户身份）

用 test01 session：
1. `POST /api/token/` 创建 Key A：`name=probe-key-a`、`remain_quota=1000000`、`expired_time=-1`
2. `POST /api/token/` 创建 Key B：`name=probe-key-b`、`remain_quota=1000000`、`expired_time=-1`
3. `GET /api/token/?p=1&page_size=10` 确认两个 Key 都在，记录 `id` 与 `key` 字段（**key 值只在内存用，不写入仓库**）

### 阶段 D：产生真实调用日志（用 API Key 调 opencode）

用 Key A 和 Key B，向 `http://127.0.0.1:13000/v1/chat/completions` 发请求（New API 会把请求转发到 opencode 渠道）：

- **成功非流式 ×5**：Key A 发 3 次、Key B 发 2 次，`"model":"muse-spark-1.3-contributor-free"`、`"stream":false`、`"messages":[{"role":"user","content":"hi"}]`、`"max_tokens":20`
- **成功流式 ×3**：Key A 发 2 次、Key B 发 1 次，`"stream":true`，其余同上
- **故意失败 ×2**：用 Key A 调不存在的模型 `"model":"gpt-nonexistent"`，预期 New API 返回 4xx/5xx 并在日志里记录 type=5
- **跨日界时间点**：记录至少两组日志跨越 UTC 00:00（例如当前 UTC 时间接近 00:00 时各发一组，或用 `created_time` 字段过滤）

每次请求记录：请求时间、`x-request-id`、HTTP 状态、响应 body（**只写到 `portal-api/target/probe-raw/calls/`**）

### 阶段 E：采集六个接口的响应（用 test01 session）

对每个接口，分别采集「**非空**」「**空数据**」「**未认证**」「**参数边界**」四类场景。所有响应原始 JSON 先写到 `portal-api/target/probe-raw/raw/<接口名>/<场景>.json`，再脱敏后写到 `docs/new-api/samples/aggregation/<接口名>.<场景>.json`。

#### E.1 `/api/log/self`（对应任务 3.1）
- 非空：`GET /api/log/self?p=1&page_size=20&type=0&start_timestamp=<24h前>&end_timestamp=<现在>` 携带 test01 session
- 空数据：用一个新建的、无任何请求的用户的 session（或用 test01 但查一个未来时间范围）
- 未认证：不带 cookie 调用，预期 401
- 非法分页：`page_size=99999`、`page_size=0`、负数 page
- 时间边界：`start_timestamp=end_timestamp`、`start_timestamp>end_timestamp`
- 日志类型筛选：`type=2`（消费）、`type=5`（错误）、`type=1`（充值）

#### E.2 `/api/log/self/stat`（任务 3.2）
- 非空：`GET /api/log/self/stat?type=0&start_timestamp=<24h前>&end_timestamp=<现在>`
- 空数据：未来时间范围
- 未认证：不带 cookie
- 非法时间：字符串时间戳、负时间戳
- 相邻区间：`[t0,t1)` 与 `[t1,t2)` 两个查询，确认 total 之和 = `[t0,t2)` 的 total

#### E.3 `/api/data/self`（任务 3.3）
- 非空：`GET /api/data/self?start_date=<7天前 YYYY-MM-DD>&end_date=<今天 YYYY-MM-DD>`
- 空数据：未来日期
- 未认证
- 非法时间：错误日期格式
- 小时桶边界：跨小时边界的请求是否被正确分桶（通过两次相邻小时请求对比）
- 数据延迟：发起一次请求后立刻查 `/api/data/self`，观察是否即时反映

#### E.4 `/api/user/self`（任务 3.4）
- 有效 session：`GET /api/user/self` 携带 test01 session
- 未认证：不带 cookie
- 余额/累计字段边界：观察 `quota`、`used_quota`、`request_count` 等字段类型与非空值

#### E.5 `/api/user/topup/self`（任务 3.5）
- 非空或可用数据：`GET /api/user/topup/self?p=1&page_size=10`
- 空数据：如果 test01 没充值过，观察空列表返回形状
- 未认证
- 非法分页：同上
- 各状态：如果测试环境无法构造真实充值/退款，**不要造假**——记录缺失条件，该接口的"状态语义"标记 `条件可用`

#### E.6 `/api/pricing`（任务 3.6）
- 匿名：`GET /api/pricing` 不带 cookie
- 认证：带 test01 session
- 非空：观察 `data` 数组是否包含 `muse-spark-1.3-contributor-free`，记录 `model_ratio`、`model_price`、`supported_endpoint` 字段
- 多厂商/多计费类型：如果只接入 opencode，记录 `vendors` 数组实际形状

### 阶段 F：脱敏 + 写入 fixture

对每个 `portal-api/target/probe-raw/raw/...` 的原始 JSON：

```java
// 用项目里已就绪的工具，写一个 main 方法或 junit 测试触发
AggregationSanitizer sanitizer = new AggregationSanitizer();
AggregationSensitiveScanner scanner = new AggregationSensitiveScanner();

JsonNode raw = MAPPER.readTree(原始字符串);
JsonNode sanitized = switch (接口) {
  case "log-self" -> sanitizer.sanitizeLogList(raw);
  case "topup-self" -> sanitizer.sanitizeTopupRecords(raw);
  case "user-self" -> sanitizer.sanitizeProfile(raw);
  case "pricing" -> sanitizer.sanitizePricing(raw);
  default -> raw;
};
scanner.scanOrThrow(sanitized);  // 命中敏感数据就抛出，此时不要写入
Files.writeString(docs/new-api/samples/aggregation/<接口>.<场景>.json, MAPPER.writeValueAsString(sanitized));
```

### 阶段 G：生成 manifest.json

写入 `docs/new-api/samples/aggregation/manifest.json`，结构：

```json
{
  "baselineVersion": "p2-2026-09-22-a",
  "measuredAt": "<UTC ISO 8601>",
  "environment": {
    "description": "本地 Docker Compose 隔离环境",
    "machine": "<uname -a 输出>",
    "network": "本机回环 127.0.0.1"
  },
  "files": [
    {
      "file": "log-self.nonempty.json",
      "scenario": "log-self.nonempty",
      "interface": "log-self",
      "sha256": "<文件 SHA-256>"
    },
    ...
  ]
}
```

`sha256` 必须**真实计算**（用 `shasum -a 256 <file>`），不能写 `PLACEHOLDER`。

### 阶段 H：生成 aggregation-baseline.json

写入 `docs/new-api/aggregation-baseline.json`，结构参考 `AggregationBaselineValidator.validate` 的必填字段，并且：
- `baselineVersion` 与 manifest.json 一致：`p2-2026-09-22-a`
- `interfaces`：六个接口，每个接口包含 `id`、`route`、`method`、`auth`、`结论`、`sampleRefs`
- `fields`：观察到的每个字段一条记录（`jsonPath`、`type`、`unit`、`nullable`、`适用日志类型`、`结论`、`sampleRef`）
- `metrics`：六项 Dashboard 指标，每项含 `分子`、`分母`、`权威来源`、`结论`（**只对真正有证据的字段标 `已验证`，其余标 `不可用` 或 `条件可用`**）
- `ledger`：充值/消费/退款映射
- `timeRules`、`moneyRules`、`performance`

### 阶段 I：跑校验

```bash
cd /Users/nelson/ai-projects/2026/20260910-lang-api/lang-api
mvn -pl portal-api test \
  -Dtest='AggregationProbeGateTests,AggregationSanitizerTests,AggregationSensitiveScannerTests,AggregationBaselineJsonContractTests,AggregationManifestContractTests' \
  -q
```

5 个测试类必须全绿。

### 阶段 J：清理

1. `git status` 确认 `portal-api/target/probe-raw/` **不出现**（被 .gitignore 覆盖）
2. `git status` 确认 `docs/new-api/samples/aggregation/` 与 `docs/new-api/aggregation-baseline.json` 是新增的待提交文件
3. 通知用户（主会话 nelson）继续任务 4.x–8.x
4. **安全建议**：提醒用户在 opencode 后台禁用 API Key、在 New API 里改管理员密码、登出所有 session

## 4. 输出物清单（完成后必须存在）

- `docs/new-api/samples/aggregation/manifest.json`
- `docs/new-api/samples/aggregation/log-self.{nonempty,empty,unauth,bad-page,time-edge,type-1,type-2,type-5}.json`
- `docs/new-api/samples/aggregation/log-self-stat.{nonempty,empty,unauth,bad-time,adjacent}.json`
- `docs/new-api/samples/aggregation/data-self.{nonempty,empty,unauth,bad-time,hourly-edge,latency}.json`
- `docs/new-api/samples/aggregation/user-self.{valid,unauth}.json`
- `docs/new-api/samples/aggregation/topup.{nonempty,empty,unauth,bad-page}.json`
- `docs/new-api/samples/aggregation/pricing.{anon,auth,nonempty}.json`
- `docs/new-api/aggregation-baseline.json`
- `portal-api/target/probe-raw/`（**必须被 .gitignore 覆盖，不提交**）

## 5. 不允许的事

- ❌ 不修改 New API 数据库
- ❌ 不修改 Portal 任何业务代码
- ❌ 不把任何真实凭证、API Key、原始 Cookie、原始 IP、原始邮箱写入仓库
- ❌ 不为了实现功能而"调试"出标 `已验证` 的结论——没有真实证据就标 `不可用`
- ❌ 不在 `docs/` 下提交任何包含 `sk-`、`oc_sk_`、`Cookie: session=`、真实邮箱/IP 的内容

完成所有阶段后，回来告诉主会话 nelson：「任务 2.x–3.x 已完成，可以继续 4.x–8.x」。
