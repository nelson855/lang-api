## 1. 模型引用与详情响应契约

- [ ] 1.1 先为 URL-safe Base64 无填充编码、UTF-8 往返、斜杠/百分号/空格/Unicode、非法字母表、填充、非规范编码、非法 UTF-8 和目录 ID 边界编写测试，再实现集中式模型引用编解码器
- [ ] 1.2 先为详情 DTO 编写构造与 JSON 序列化测试，冻结基本字段、限制、模态、四项可空能力、发布信息、标签、排序和 enhancedPricing 字段名与类型
- [ ] 1.3 增加 null/空数组/false 的语义守卫测试，确认未知为 null，只有已验证空集合可返回 `[]`，只有明确不支持可返回 `false`
- [ ] 1.4 为增强价格项增加 type、currency、unit、非负十进制 price 和唯一性测试，再实现 Portal 自有枚举和值对象，不复用 New API DTO

## 2. 原子目录快照与可信映射

- [ ] 2.1 先为列表、详情索引、供应商计数和 pricingVersion 原子生成编写纯映射测试，再引入不可变内部 `CatalogSnapshot`
- [ ] 2.2 调整目录映射，使详情基础字段直接复用最终 `CatalogModel`，当前基线下所有新增元数据和 enhancedPricing 均为 null
- [ ] 2.3 增加上游未知字段、`supported_endpoint_types`、owner、vendor icon、倍率、分组和渠道白名单测试，确认这些字段不能进入详情或被推断成能力
- [ ] 2.4 先覆盖同名供应商合并、大小写不同名称分离、未知供应商排除、模型计数和稳定排序，再从最终模型数组派生 `{value,label,modelCount}`
- [ ] 2.5 增加重复模型 ID、非法 ID、冲突增强价格和映射中途失败测试，确认整个快照失败且不会产生部分索引或供应商结果

## 3. 共享缓存与服务接口

- [ ] 3.1 将 `CatalogService` 缓存值迁移为内部快照，并保持现有 `current()` 或等价列表服务输出及 JSON 完全兼容
- [ ] 3.2 增加按精确解码 ID 查询详情和读取供应商选项的服务方法，合法但不存在的模型返回可映射 404 结果，不做模糊、大小写或归一化匹配
- [ ] 3.3 先为列表/详情/providers 混合并发未命中、缓存命中、TTL 到期、首次失败和刷新失败编写测试，确认三类读取只共享完整成功快照且失败不缓存
- [ ] 3.4 核对 `application.properties` 与 dev/test/prod profile 继续复用现有 catalog TTL、quota-per-usd 和上游超时；确认本变更不新增 YAML、配置项或环境硬编码

## 4. 匿名只读 HTTP 接口

- [ ] 4.1 先为 `GET /portal/api/models/{modelId}` 编写匿名成功、非法引用 400、不存在 404、未知查询参数 400、错误方法 405、requestId 和 `Cache-Control: no-store` 测试
- [ ] 4.2 实现模型详情 Controller 路由，严格先校验规范引用，再读取共享快照并通过统一 `{requestId,data}` 包装返回 `{pricingVersion,model}`
- [ ] 4.3 先为 `GET /portal/api/model-providers` 编写匿名成功、空数组、计数、顺序、未知查询参数 400、错误方法 405、requestId 和 no-store 测试
- [ ] 4.4 实现供应商 Controller 路由和安全白名单，只允许两个新增 GET 接口匿名访问，不放宽其他 catalog、账户或管理路由
- [ ] 4.5 增加响应 JSON 敏感字段检查，确认 vendor ID/icon、model/group ratio、enable/usable groups、owner、channel、endpoint、上游地址和原始包装不泄露

## 5. 纵向一致性与兼容回归

- [ ] 5.1 使用 MockWebServer 编写单次 `/api/pricing` 加载后依次查询列表、详情和 providers 的纵向测试，断言三者 pricingVersion、基本模型字段、供应商计数和调用次数一致
- [ ] 5.2 增加包含特殊字符模型 ID 的真实 HTTP 路由测试，确认额外路径段、`.`/`..`、双重编码、控制字符和内部配置名不能穿透目录精确查找
- [ ] 5.3 增加上游 5xx、连接失败、超时、非法 JSON、重复 ID、厂商关联失败和到期刷新失败测试，确认统一错误、无部分 data 且不回退无期限旧快照
- [ ] 5.4 回归现有 `GET /portal/api/models` 的匿名访问、排序、价格、缓存、错误方法、字段白名单及当前前端严格 schema，确认没有新增列表字段或行为变化
- [ ] 5.5 扩展架构与只读边界测试，禁止 catalog Web DTO 引用 `upstream.newapi` 类型，确认没有新增 POST/PUT/PATCH/DELETE、数据库写入或需登录 `/api/models` 调用

## 6. 真实证据与文档

- [ ] 6.1 在可用冻结环境抽取至少一个 TOKEN 和一个 REQUEST 模型，按默认组记录脱敏调用用量与实际扣费，交叉核对列表和详情基础价格；无法取得模型或换算证据时保持任务未完成并记录外部条件
- [ ] 6.2 探测缓存、图片、音频、视频和搜索计费以及上下文、输出上限、模态和四项能力的可信来源；只有证据、单位和语义同时确认后才发布新 baseline 并填充值，否则保持 null
- [ ] 6.3 新增 `docs/16_LANG-P2-07-模型详情与供应商筛选接口说明.md` 并更新文档索引，记录 Base64url 引用算法、请求/响应示例、字段空值、价格单位、版本一致性和错误行为
- [ ] 6.4 更新 `docs/11_第二阶段聚合能力开发与子需求拆分.md` 的 P2-07 实际结果；若 quotaPerUsd、真实扣费或增强元数据证据仍缺失，状态 MUST 保持“受阻”并区分已交付能力与外部缺口

## 7. 验证与交付记录

- [ ] 7.1 使用 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml` 运行新增 catalog 测试、`portal-api` 全量测试和根工程 `verify`，区分实现失败与外部证据阻塞
- [ ] 7.2 运行 OpenSpec 严格校验、JSON/架构白名单、敏感信息检查和 `git diff --check`，确认 proposal、spec、design、tasks、接口文档与实现一致
- [ ] 7.3 逐项核对模型引用、可信字段、供应商去重、价格、快照缓存、匿名权限、失败链和旧列表兼容，只勾选已实际完成且验证通过的任务
- [ ] 7.4 运行 `git status --short` 并报告全部未提交改动、测试结果、当前 null 字段与真实证据阻塞；不执行提交、推送、PR、发布或部署
