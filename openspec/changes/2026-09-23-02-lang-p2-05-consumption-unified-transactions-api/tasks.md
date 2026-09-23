## 1. 流水与可用性响应契约

- [ ] 1.1 先为消费汇总和流水 DTO 编写构造与 JSON 序列化测试，冻结 baselineVersion、range、指标、coverage、分页和 item 字段名与类型
- [ ] 1.2 先覆盖 AVAILABLE/PARTIAL/UNAVAILABLE 与 reasonCode、QUOTA/CURRENCY 与 currency、CREDIT/DEBIT、状态和非负金额的合法/矛盾组合，再实现 Portal 自有枚举和值对象守卫
- [ ] 1.3 增加响应白名单测试，确认用户 ID、token ID、完整 API Key、渠道、节点、供应商、回调、商户字段、原始日志和 New API 包装不能进入 JSON

## 2. 安全消费快照与汇总

- [ ] 2.1 先为多条 type=2、空范围、缺 requestId、最大 quota、累计溢出和非法记录编写快照/汇总测试，固定 recordCount 与 raw quota 精确结果
- [ ] 2.2 实现只保留 occurredAt、requestId、model、rawQuota 和缺失引用计数的不可变消费快照，不把 tokenId、Key 名称或原始 DTO带入缓存值
- [ ] 2.3 实现消费汇总计算器，使记录数和 quota 可用、空范围为真实零值，moneyTotal 固定遵循 baseline policy 返回 `CURRENCY_CONVERSION_NOT_VERIFIED`
- [ ] 2.4 增加守卫测试，确认当前基线代码路径不读取 catalog quota-per-usd、不调用 `QuotaMoneyConverter`，也不访问 `/api/log/self/stat` 或 `/api/data/self`

## 3. 稳定交易投影、覆盖状态与分页

- [ ] 3.1 先为 transactionId 确定性、跨用户隔离、来源前缀、同 requestId 重复冲突和缺 requestId 编写失败测试，再实现 SHA-256 稳定 ID
- [ ] 3.2 实现当前消费 item 投影，固定 CONSUMPTION/DEBIT/SUCCEEDED、非负 quota、`unit=QUOTA`、`currency=null`、安全 model remark 和 requestId reference
- [ ] 3.3 先为消费完整/部分覆盖、充值基线未验证、退款无来源、单类型和默认全部类型编写测试，再实现逐来源与总体 availability/reasonCode 聚合
- [ ] 3.4 先为 occurredAt 降序、transactionId 升序、相邻页、准确 total、空页和重复 ID 编写测试，再实现完整候选集排序后分页
- [ ] 3.5 增加 page/pageSize 精确乘法和深分页保护测试，确认请求窗口超过 max-records 时在上游访问前返回稳定错误

## 4. 共享读取、缓存与观测

- [ ] 4.1 先为相同用户/范围跨汇总与流水复用、不同用户/范围/粒度/时区隔离编写测试，再实现 `account-consumption-snapshot` 缓存键和共享快照加载
- [ ] 4.2 增加同键并发、首次失败、超时、取消和保护性拒绝测试，确认只合并完整成功加载且失败不进入缓存
- [ ] 4.3 接入 `AggregationReadBudget` 与 `AggregationLogReader.readSuccessLogs`，保证一次快照只完整读取一轮 type=2 日志且沿用 7 天、10 页/200 条和 30 秒边界
- [ ] 4.4 扩展聚合观测操作白名单与测试，记录 account-consumption-summary、account-transactions、快照缓存和 SUCCESS_LOG 读取，不增加用户、类型、时间、模型或 requestId 标签
- [ ] 4.5 核对现有 `application.properties` 与 dev/test/prod aggregation 配置均被复用；确认本变更没有新增配置项、YAML 或账户专用保护常量

## 5. 受保护 HTTP 接口

- [ ] 5.1 先为两个 GET 路由编写未认证、四参数缺失、未知参数、客户端 baseline/userId、非法类型、非法粒度/时区/范围和超过 7 天测试，确认上游访问前拒绝
- [ ] 5.2 实现账户聚合控制器，严格绑定认证主体与 New API 会话，解析汇总和流水参数，并通过统一 `{requestId,data}` 包装返回当前请求标识
- [ ] 5.3 增加合法消费汇总 HTTP 契约测试，确认规范化 UTC 范围、原始 IANA 时区、baseline、quota 可用和 moneyTotal 不可用正确序列化
- [ ] 5.4 增加 CONSUMPTION、TOPUP、REFUND 和默认全部类型 HTTP 测试，确认不可用单类型不调用无关来源，默认查询只读取消费日志并返回 partial coverage
- [ ] 5.5 增加重复认证 Cookie、主体与 Cookie 用户不一致、跨用户参数和缓存命中 requestId 测试，确认用户隔离且每次 HTTP 响应使用新的 requestId

## 6. 失败链、兼容性与纵向验证

- [ ] 6.1 使用 MockWebServer 编写多页成功链，确认汇总与流水复用同一安全快照、quota 与 item 可抽样对应且缓存命中不重复访问上游
- [ ] 6.2 增加 total 变化、提前空页、超过 10 页/200 条、上游 5xx、连接失败、超时、非法字段、累计溢出和重复 transactionId 测试，确认整体失败且无部分 data
- [ ] 6.3 增加调用次数断言，确认当前 baseline 下不调用 New API topup、退款替代源、管理员接口、旧 summary/hourly 或正式 USD 换算
- [ ] 6.4 回归 `/portal/api/account/balance`、`/portal/api/account/topups`、`/portal/api/account/topup-options`、Dashboard 和请求日志，确认路径、响应、缓存及充值关闭行为未改变
- [ ] 6.5 扩展架构与只读边界测试，禁止账户聚合 Web DTO 引用 `upstream.newapi` 类型，确认没有新增 POST/PUT/PATCH/DELETE 账户流水路由或数据写入

## 7. 真实证据、接口文档与阶段状态

- [ ] 7.1 在可用的冻结环境重新抽样核对 type=2 消费记录数与 raw quota，保存脱敏证据；无法取得环境或典型数据量时保持对应任务未完成并记录外部条件
- [ ] 7.2 重跑 quotaPerUsd、真实充值状态/金额/时间/稳定引用和退款来源探测；只有发布新 baseline 后才调整当前 unavailable coverage，不依据 P1 fixture 修改结论
- [ ] 7.3 新增 `docs/14_LANG-P2-05-消费汇总与统一流水接口说明.md` 并更新文档索引，记录请求参数、完整响应示例、单位/方向/状态、coverage、稳定 ID、分页和错误行为
- [ ] 7.4 更新 `docs/11_第二阶段聚合能力开发与子需求拆分.md` 的 P2-05 实际结果；若 USD、充值、退款或典型性能证据仍缺失，状态 MUST 保持“受阻”并分别列出已交付消费范围与外部缺口

## 8. 验证与交付记录

- [ ] 8.1 使用 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml` 运行新增账户聚合测试、`portal-api` 全量测试和根工程 `verify`，区分本变更失败与外部证据阻塞
- [ ] 8.2 运行 OpenSpec 严格校验、JSON/架构白名单、敏感信息检查和 `git diff --check`，确认 proposal、spec、design、tasks、接口文档与实现一致
- [ ] 8.3 逐项核对时间参数、汇总精度、来源 coverage、稳定 ID、排序分页、认证、缓存、失败链和旧接口兼容，只勾选已实际完成且验证通过的任务
- [ ] 8.4 运行 `git status --short` 并报告全部未提交改动、测试结果、当前不可用货币/充值/退款能力及性能阻塞；不执行提交、推送、PR、发布或部署
