## Context

参见 [proposal.md](./proposal.md) 的动机与范围。当前 P1-05 已把浏览器的 `LANG_SESSION`/`LANG_UID` 转换为冻结版 New API v0.13.2 所需的 `session` Cookie 与 `New-Api-User` Header，并在每个受保护请求中建立经过上游确认的普通用户安全上下文；P1-06 必须复用这一身份边界，不能引入另一套 Key 或授权状态。

P1-02 的脱敏实测证明：列表与单项读取返回掩码；创建 `POST /api/token/` 仅返回成功，不返回新 id 或明文；明文需通过 `POST /api/token/{id}/key` 取回；一般编辑要求完整 Token 对象；启停使用 `PUT /api/token/?status_only=true`；读取、删除和 reveal 都以 `id + 当前用户` 做上游归属约束。冻结版源码还声明了分页名称搜索 `/api/token/search`，但 P1-02 尚未把它纳入本地契约证据，因此实现时先补脱敏 fixture 与契约测试再接入。

New API 字段仍使用 Unix 秒、`-1`、数字 status、整数 quota、逗号分隔模型和换行分隔 IP；`group`、`cross_group_retry` 等字段属于上游路由实现，不应成为第一版普通用户契约。前端已有统一 Portal 客户端、TanStack Query、真实会话状态、CSRF 引导、DataTable、Pagination、Dialog、Toast 和中英文基础设施，可直接扩展。

## Goals / Non-Goals

**Goals:**

- 在现有模块化单体中建立单一 `apikey` 业务边界，保持 Controller DTO、领域命令和上游 DTO 分离。
- 对列表、详情和写操作提供稳定、可验证的自有协议，同时正确保留 New API 完整更新所需但未公开的字段。
- 让名称搜索、状态筛选和分页返回全结果语义正确的 `total`，不做“仅过滤当前页”的伪实现。
- 将完整 Key 当作短生命周期 secret：显式确认、非缓存响应、最少内存驻留和不可记录。
- 为 P1-07 生成可直接使用的 `sk-` 形式 Key，但本阶段不开放 Relay。

**Non-Goals:**

- 不增加批量创建、批量删除、批量 reveal、Key 轮换、子 Key、使用统计图或管理员视图。
- 不公开上游 Token 值搜索、group、cross-group retry、渠道或其他路由内部概念。
- 不把 `accessed_time` 展示为“最后调用时间”；冻结版在创建时就写该值，真实调用历史留给 P1-09。
- 不把 quota 转换为货币或 Token 数；P1-06 明确显示原生 quota 单位，集中金额换算留给 P1-10。
- 不支持 CIDR、域名或通配 IP；第一阶段只接受明确 IPv4/IPv6 字面量。

## Decisions

### 1. 使用三段式边界，而不是 Controller 直接调用上游

后端按现有传统分层增加：

```text
web/apikey                 Controller、request/response、应用服务
upstream/newapi/token      冻结版路径、上游 DTO、字段与错误转换
base                       复用响应、会话、CSRF、异常、日志与脱敏
```

Controller 只接受 Lang API request；应用服务从 `PortalAuthenticatedUser` 取得当前用户与会话，并编排读取/写入；Token 适配器独占 `/api/token/*` 字符串和上游 DTO。架构测试继续阻止 `web`/前端引用上游路径与类型。

备选方案是做通用 JSON 代理，代码更短但会把 New API 字段、错误和新增能力直接暴露给浏览器，违反现有白名单适配原则，不采用。另建 Key 数据库会产生双写、撤销和一致性问题，也不采用。

### 2. 固定资源式 Portal API，写操作全部不可自动重试

接口固定为：

| 方法与路径 | 用途 | 关键输入/输出 |
|---|---|---|
| `GET /portal/api/api-keys` | 列表/搜索/筛选 | `page` 默认 1、`pageSize` 默认 20 且最大 100、可选 `name/status`；返回统一分页 |
| `POST /portal/api/api-keys` | 创建 | 名称、quota、expiresAt、modelRestrictions、allowedIps；成功不返回 secret |
| `GET /portal/api/api-keys/{id}` | 脱敏详情 | 返回完整公开限制但只有 maskedKey |
| `PUT /portal/api/api-keys/{id}` | 编辑 | 只接受可编辑公开字段 |
| `PUT /portal/api/api-keys/{id}/status` | 启停 | `{enabled: boolean}` |
| `DELETE /portal/api/api-keys/{id}` | 删除 | 成功返回删除确认数据或空对象 |
| `POST /portal/api/api-keys/{id}/reveal` | 取回明文 | 返回 `{secret}`，`Cache-Control: no-store` |

使用 `PUT .../status` 而不是把启停混入一般编辑，让确认、审计、错误与上游 `status_only` 语义一一对应。未采用 PATCH，是为了保持当前项目简单的 request DTO 与完整命令风格；对外路径仍不暴露上游 query 结构。

所有 mutation 与 reveal 复用现有 CSRF Token/Origin 校验且 `retry=0`。请求已发出后发生断连或超时统一视为 `OPERATION_RESULT_UNKNOWN`，页面引导刷新核对，不能自动重放可能已成功的写入。

### 3. 列表直通分页，名称搜索走上游 search，状态筛选在 Portal 完整聚合后分页

无名称和状态条件时直接调用 `/api/token/`，保留上游按 id 倒序和分页 total。只有名称条件时使用 `/api/token/search` 的 `keyword`，Portal 把用户原文首尾各补一个 `%` 后直传，由上游按 `ESCAPE '!'` 自行转义 `!` 与 `_`；Portal 不得预转义，否则会形成双重转义。含 `%` 的名称上游按通配符计数拒绝（安全失败），单字符名称不满足上游“去通配后至少 2 字符”规则同样安全失败；`token` 明文搜索参数永不使用。

冻结版没有 status 查询条件。存在 status 时，Portal 对“普通列表”或“名称搜索”按上游允许的最大页大小依次读取完整匹配集合，先映射状态、再筛选、最后应用 Portal page/pageSize，从而返回过滤后的真实 total。读取循环必须校验上游 total/页进度，发现 total 漂移时以已去重 id 的稳定快照继续一次，不重复无限拉取；整个编排受当前请求超时预算约束。New API 已有单用户 Token 数量上限，因此 MVP 可接受这一受限聚合；记录查询页数和耗时但不记录名称条件。

备选方案一是只过滤当前页，速度最快但会产生错误 total 和漏项，不采用。备选方案二是直接访问 New API 数据库，可高效筛选但破坏适配边界，不采用。若 P1-12 的实测显示聚合开销不可接受，应先推动上游支持 status 条件或引入专用只读聚合设计，而不是静默降低语义。

### 4. Lang API 拥有字段语义，上游内部字段只在适配器中保留

对外 `ApiKey` 投影包含：

- `id`、`name`、`maskedKey`；掩码与 reveal 均统一补一个 `sk-` 前缀，重复前缀会规范化为一个；
- `status`: `enabled | disabled | expired | exhausted`；只有前两者可作为 status mutation 目标；
- `createdAt`、`expiresAt`：Unix 秒转 UTC ISO 8601；`-1` 转 `null`；
- `quota`: `{unlimited, remaining, unit:"quota"}` 与 `usedQuota`: `{value, unit:"quota"}`；
- `modelRestrictions`: `{enabled, models[]}`；
- `allowedIps: string[]`。

创建/编辑先在 Portal 验证：trim 后名称 1～50 字符；有限 quota 为非负安全整数；expiresAt 为空或严格晚于当前时刻；模型名 trim、去空、去重且不含逗号/控制字符；IP 逐项解析为 IPv4/IPv6 字面量并规范化，不接受 CIDR、域名、端口或换行注入。模型用逗号、IP 用换行转换，仅存在于适配器。

`group`、`cross_group_retry`、Key 原值、owner、used quota、created/accessed time等不可编辑字段不进入 request。编辑采用 read-modify-write：先按当前用户读取完整上游对象，将公开修改合并到已读取对象，再 PUT，保留未公开字段。这样适配 New API 的完整对象更新而不让浏览器控制内部路由字段。

### 5. 创建成功后不自动 reveal，也不猜测新 id

New API 创建成功不返回 id。Portal 不能靠名称、时间或“列表第一项”猜测新对象，因为名称可重复且多标签页/并发请求会形成竞态。因此 `POST /portal/api/api-keys` 成功只表示创建完成，不组合自动 reveal；前端清除搜索/筛选、回到第一页并刷新列表，在最新项区域显示一次“显示并复制”引导，由用户对明确 id 再发 reveal。

这一方案比“创建前后 diff 后自动找 id”少一次脆弱推断，也避免 reveal 失败被误解为创建失败后重复提交。代价是用户多一次确认，但与敏感凭证最小暴露一致。若列表刷新失败，仍显示“创建可能已完成，请刷新核对”，不能重新提交。

### 6. reveal 使用专用敏感值通道

服务端 reveal 返回前先由上游 `id + userId` 校验所有权，只读取 key 字段，使用不可被默认 `toString` 展开的敏感值类型在调用栈中传递，响应写出后不缓存。Controller 设置 `Cache-Control: no-store`、`Pragma: no-cache`，不生成 ETag；访问日志继续只记录规范化路由。

前端 reveal 不使用 TanStack Query，也不放入全局 Store；对话框确认后用一次性请求将 secret 放入本地 state，60 秒后自动清除。复制成功、对话框关闭、路由卸载或会话失效立即清除。剪贴板失败时允许用户在仍打开的 60 秒窗口内手工复制，但不写入 localStorage/sessionStorage、URL、Toast、错误对象或日志。

备选方案是创建后只显示一次且永不允许再次查看，安全面更小，但冻结版已有按用户取回能力且阶段文档明确要求受控 reveal，不采用。长期展示明文或在列表行内切换显示会扩大泄露窗口，也不采用。

### 7. 对象归属依赖上游用户约束，并统一隐藏存在性

应用服务从安全上下文取得 userId，不接受 request body/query 中的 owner。Token 详情、更新前读取、删除和 reveal 都调用上游用户级接口；返回不存在时统一映射 `NOT_FOUND`，不调用管理员接口区分“真的不存在”与“属于他人”。客户端伪造的 `New-Api-User` Header 已由现有入口过滤器丢弃。

为防止未来适配器误用，契约测试必须对不同用户 id 断言上游 Header 来源，并用 404 fixture 覆盖详情、编辑、启停、删除和 reveal。Portal 本身保持无状态，不持久化 owner 映射。

### 8. 风险操作采用确认、完成态审计和保守缓存更新

停用确认说明调用会立即失败；删除确认显示 Key 名称并说明不可恢复；reveal 确认说明持有者可消耗额度。mutation 期间目标行/对话框禁用重复提交，但不阻塞其他不相关 Key 的读取。

安全事件覆盖 create/update/status/delete/reveal，记录 requestId、操作、资源 id（创建成功但无 id 时为空）、结果类别、原因码和使用进程随机盐摘要后的当前用户标识；不记录 Key 名、掩码、额度或限制正文。成功后按资源失效查询；结果未知时不做乐观更新，显示 requestId 和“刷新核对”。

### 9. 前端按 feature 收敛数据、表单与页面

新增结构：

```text
frontend/src
├── api/apiKeys.ts                 Portal DTO 运行时校验与请求函数
├── features/apiKeys/              query keys、hooks、表单模型、secret 对话框
└── pages/ApiKeysPage.tsx          页面编排
```

`/api-keys` 放在现有鉴权守卫和 ConsoleLayout 下，控制台导航增加入口。桌面复用 DataTable，窄屏使用同一语义数据的卡片/纵向详情；操作菜单不能只靠 hover。搜索输入防抖并取消旧请求，条件改变重置到第一页。创建/编辑表单共享公开字段模型，常用字段默认展开，模型/IP 放高级区域。

查询键包含用户 id、page、pageSize、规范化 name/status，避免账号切换复用缓存。reveal 完全绕过查询缓存。所有文案进入 zh-CN/en-US 资源，状态图标与文字并用，并补键盘、焦点、axe 和减少动效检查。

### 10. 配置只使用 `.properties`，三个环境显式一致

只新增确有必要的非敏感配置：`lang.api-key.reveal-ttl=60s`、搜索防护/分页上限和状态聚合请求预算。共享默认值放 `application.properties`；`application-dev.properties`、`application-test.properties` 和 `application-prod.properties` 显式声明是否沿用或覆盖，其中 test 使用更短 reveal TTL 与确定性预算，prod 不允许通过配置关闭 CSRF、Origin、`no-store` 或明文清除。

启动校验拒绝非正 TTL、非法 pageSize/预算和会导致无界聚合的值。配置不包含 Key、上游用户或真实域名，也不新增 YAML。

## Risks / Trade-offs

- [状态筛选需要多次上游分页读取] → 只在指定 status 时聚合，使用最大上游页、请求预算和单用户 Token 上限约束；记录安全耗时指标，P1-12 再根据实测决定是否推动上游查询能力。
- [名称搜索路由尚未出现在 P1-02 本地证据] → 实现第一步用 v0.13.2 脱敏 fixture 和契约测试固定方法、参数、分页及 wildcard 转义；若 Compose 抽查与冻结源码不一致，停止接入并更新设计而不做当前页伪搜索。
- [New API 更新需要完整对象] → 强制 read-modify-write，request DTO 不接受 owner/key/used/internal fields，并用保留字段契约测试防止清空。
- [不可重试写操作超时后结果未知] → 返回 `OPERATION_RESULT_UNKNOWN`，不乐观更新、不自动重试，页面提供刷新核对并保留 requestId。
- [reveal 使浏览器短暂持有完整 Key] → 显式确认、专用 POST、CSRF/Origin、no-store、本地短 TTL、复制/关闭/路由/会话清除及全链路敏感信息扫描。
- [`sk-` 前缀由 Lang API 规范化] → 适配器统一移除重复前缀再添加一个，契约测试同时覆盖带/不带前缀的上游值，保证 P1-07 客户端示例一致。
- [仅支持 IP 字面量可能限制高级网络策略] → UI 明确说明范围；CIDR/域名需先验证上游匹配语义并以独立变更扩展，避免当前阶段假支持。
- [进程盐主体摘要跨重启不可关联] → 当前安全事件用于单次运行排障且以 requestId 为主；需要长期合规审计时在 P1-12 设计稳定密钥化标识，当前不引入新 Secret。

## Migration Plan

1. 先补 `/api/token/search` 与各 Token 操作的 v0.13.2 脱敏 fixture/契约测试，确认分页参数、状态值、掩码、IP 分隔符和错误形态；不修改 New API。
2. 扩展 `.properties` 配置、稳定错误目录、CSRF matcher、安全事件与敏感值测试，保持 API Key 路由未实现时默认拒绝。
3. 实现 Token 适配器与字段转换，再实现 `apikey` request/response、应用服务和 Controller；先完成只读列表/详情，再依次开放创建、编辑、启停、删除和 reveal。
4. 实现前端 DTO/query/mutation、`/api-keys` 页面、创建编辑表单与风险对话框，最后接入导航、中英文和响应式布局。
5. 运行后端契约/权限/安全集成测试、前端单测/Lint/构建与 Playwright 流程；用两个用户验证不可越权，并检查源码、日志和生产产物无完整 Key、私网地址或上游路径泄露。
6. 部署时不需要数据库迁移。先以现有测试账号验证列表和 reveal，再创建受限 Key、编辑、启停和删除；P1-07 上线前保留至少一个启用 Key 用于真实 Relay 验收。

回滚只需恢复上一版本应用与配置，不删除 New API 中已创建或修改的 Key。回滚前若本版本已创建测试 Key，应由用户或运维在 New API 私网后台明确处理；不得在回滚脚本中自动删除。旧版本不识别 `/api-keys` 页面和 Portal 路由，用户会看到 404，但上游 Key 仍按最后状态有效。
