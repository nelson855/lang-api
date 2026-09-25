## ADDED Requirements

### Requirement: 公共模型详情接口使用稳定 Portal 契约
系统 SHALL 提供无需认证的 `GET /portal/api/models/{modelId}`，并通过统一成功包装返回 `{pricingVersion, model}`。`model` MUST 包含 `id`、可空 `displayName`、可空 `provider`、`availability`、可空 `pricing`、可空 `contextWindowTokens`、可空 `maxOutputTokens`、可空 `inputModalities`、可空 `outputModalities`、`capabilities`、可空 `releaseDate`、可空 `description`、可空 `tags`、可空 `sortOrder` 和可空 `enhancedPricing`。接口不接受查询参数；除 GET 外的方法 MUST 返回统一 405。

`capabilities` SHALL 固定包含可空布尔字段 `toolCalling`、`reasoning`、`structuredOutput` 和 `attachments`。`inputModalities` 与 `outputModalities` 有证据时只允许 `TEXT`、`IMAGE`、`AUDIO`、`VIDEO` 或 `FILE`；`releaseDate` 有值时使用 ISO 8601 日历日期。`enhancedPricing` 有证据时为项目数组，每项包含 `type`、`currency`、`unit` 和非负十进制字符串 `price`；`type` 只允许 `CACHE_INPUT`、`CACHE_OUTPUT`、`IMAGE_INPUT`、`IMAGE_OUTPUT`、`AUDIO_INPUT`、`AUDIO_OUTPUT`、`VIDEO` 或 `SEARCH`。

#### Scenario: 匿名获取存在的模型详情
- **WHEN** 未登录访客使用目录中模型 ID 的规范引用请求详情
- **THEN** 系统返回该模型的 Portal 自有详情和生成该详情的 `pricingVersion`，且不要求会话或 API Key

#### Scenario: 查询不存在模型
- **WHEN** 规范模型引用解码成功但当前目录快照中没有完全相同的模型 ID
- **THEN** 系统返回统一 `NOT_FOUND`/404，不返回相似模型、内部配置或上游原始错误

#### Scenario: 详情接口包含未声明参数
- **WHEN** 请求包含任意查询参数或使用 GET 以外的方法
- **THEN** 系统在访问上游前分别返回统一 `INVALID_ARGUMENT`/400 或 `METHOD_NOT_ALLOWED`/405

### Requirement: 路径模型引用必须可逆且抵抗特殊字符歧义
详情路径中的 `{modelId}` SHALL 是目录模型 ID 的 UTF-8 字节经 URL-safe Base64 无填充编码后的规范模型引用，而不是原始模型 ID。解码结果 MUST 完全匹配目录返回的模型 ID，不得大小写折叠、Unicode 归一化、路径归一化或前缀匹配。系统 MUST 拒绝空值、填充符、非 URL-safe 字符、非规范重编码、非法 UTF-8 以及不满足目录模型 ID 边界的引用，并且不得在日志或错误中回显解码出的可疑原文。

#### Scenario: 模型 ID 包含斜杠与 Unicode
- **WHEN** 客户端对包含 `/`、`%`、空格或非 ASCII 字符的合法目录模型 ID 按规范生成 URL-safe Base64 引用
- **THEN** 单一路径段可无歧义定位原模型，返回的 `model.id` 保持原值

#### Scenario: 非规范编码被拒绝
- **WHEN** 引用带 `=` 填充、非法字符、无效 UTF-8、额外路径段或解码后不满足模型 ID 边界
- **THEN** 系统在目录查找和上游访问前返回统一 `INVALID_ARGUMENT`/400

#### Scenario: 特殊值不能穿透目录边界
- **WHEN** 客户端尝试使用编码后的 `.`、`..`、路径分隔符组合、控制字符或内部配置名称
- **THEN** 系统只进行严格解码和当前快照精确 ID 查找，不能访问文件、其他路由、渠道配置或敏感信息

### Requirement: 模型详情只返回可信元数据
详情字段 SHALL 由明确、可追溯且语义与单位已验证的来源填充；不得根据模型名、品牌常识、静态猜测表、厂商图标、`supported_endpoint_types` 或内部渠道能力推断。未知字段 MUST 保持响应结构不变并返回 `null`；已验证为空的集合才可返回空数组。布尔能力的 `false` MUST 表示来源明确证明不支持，不能用来代替未知。

在 `p2-2026-09-22-a` 下，只有现有目录的模型 ID、供应商名称、可用性、基础价格和 `pricingVersion` 具备当前证据；显示名以及新增限制、模态、能力、发布信息、标签、排序和增强价格均 MUST 为 `null`。上游新增未知字段时不得自动改变该结论。

#### Scenario: 当前基线返回基础详情
- **WHEN** 当前冻结 `/api/pricing` 样本中的模型被查询
- **THEN** 系统返回已验证的基本目录字段，所有尚无证据的新增字段为 `null`，`capabilities` 内四个字段也均为 `null`

#### Scenario: 上游字段名称看似表示能力
- **WHEN** 上游返回 `supported_endpoint_types`、所有者、厂商图标或其他未纳入基线的字段
- **THEN** 详情不会把它们映射成模态、工具、推理、附件、描述、标签或其他对外能力

#### Scenario: 明确不支持与未知相区分
- **WHEN** 某布尔能力没有可信来源而另一个能力由已验证适配器明确证明不支持
- **THEN** 前者返回 `null`，后者才可返回 `false`

### Requirement: 公共供应商选项与可见目录一致
系统 SHALL 提供无需认证且不接受查询参数的 `GET /portal/api/model-providers`，通过统一成功包装返回 `{pricingVersion, providers}`。每个 provider item MUST 只包含 `value`、`label` 和 `modelCount`：`value` 与 `label` 均使用目录模型已验证且裁剪后的供应商名称，`modelCount` 为同一快照中精确匹配该名称的模型数量。系统 SHALL 排除供应商为 `null` 的模型，以大小写敏感的完整名称去重并按 `value` 升序稳定排序；不同上游厂商 ID 映射到相同名称时只返回一个累计项。

接口 MUST NOT 返回上游厂商 ID、图标、渠道、组、倍率、路由或厂商未关联模型。除 GET 外的方法 MUST 返回统一 405。

#### Scenario: 从当前目录派生供应商选项
- **WHEN** 同一快照包含两个 `DeepSeek` 模型、一个 `OpenAI` 模型和一个供应商未知模型
- **THEN** 接口按名称稳定返回 `DeepSeek/2` 与 `OpenAI/1` 两项，且不为未知模型制造“其他”供应商

#### Scenario: 重复厂商名称合并
- **WHEN** 多个可唯一关联的上游厂商记录最终映射为完全相同的公开名称
- **THEN** providers 只包含一个该名称的筛选项，`modelCount` 覆盖全部对应可见模型

#### Scenario: 没有已知供应商
- **WHEN** 当前目录为空或所有可见模型的供应商均为 `null`
- **THEN** 系统成功返回空 `providers` 数组而不是静态选项或错误

### Requirement: 详情价格与增强价格保持证据和单位边界
模型详情的 `pricing` SHALL 与相同 `pricingVersion` 下模型目录中的基础价格完全一致，继续使用已有 TOKEN/REQUEST、USD 和单位契约。`enhancedPricing` 只有在来源、计费维度、币种、单位和数值均已验证时才能成为数组；无法确认、部分确认或只存在内部倍率时 MUST 为 `null`，不得返回零价或空数组暗示已验证无额外收费。

增强价格项的 `currency` 和 `unit` MUST 为明确来源值，`price` MUST 是非负十进制字符串；同一 `type`、币种和单位出现冲突或重复时整个详情映射失败，不得任选其一。

#### Scenario: 详情基础价格与列表一致
- **WHEN** 客户端在相同 `pricingVersion` 下对照模型列表与详情
- **THEN** 两处模型 ID、供应商、可用性和基础 `pricing` 完全一致

#### Scenario: 当前样本只有基础倍率
- **WHEN** 当前基线只提供 model ratio、completion ratio 或固定请求价，没有独立增强计费证据
- **THEN** 详情沿用可安全换算的基础 `pricing`，`enhancedPricing` 返回 `null`

#### Scenario: 抽样交叉核对真实扣费
- **WHEN** 使用冻结计费参数、默认计费组、真实可调用模型和有效 Key 完成可计量调用
- **THEN** 列表与详情的基础价格可由实际 Token/请求次数交叉核对，任何缓存、图片、音频、视频、搜索或用户组调整均被单独识别而非混入基础价

### Requirement: 列表详情与供应商接口共享完整短期快照
模型列表、模型详情和供应商选项 SHALL 从同一完整公共定价快照及同一有界短期进程内缓存派生。详情索引和供应商计数 MUST 与模型数组原子生成；重复模型 ID、非法字段、冲突价格或映射中途失败时不得缓存部分结果。所有接口 MUST 返回快照的 `pricingVersion` 和 `Cache-Control: no-store`，使客户端能够发现跨缓存刷新边界的版本差异。

新接口 MUST 复用现有缓存 TTL、容量、同键并发合并、到期和失败语义，不得建立独立详情/供应商缓存，也不得因访问单个模型而调用需登录的 New API 接口。现有 `GET /portal/api/models` 的路径、响应字段与行为 MUST 保持兼容。

#### Scenario: 三类请求命中同一新鲜快照
- **WHEN** 客户端在缓存新鲜期内依次请求列表、一个模型详情和供应商选项
- **THEN** 三者使用相同 `pricingVersion` 和映射结果，且不会分别重复访问 `/api/pricing`

#### Scenario: 并发首次读取
- **WHEN** 列表、详情和供应商请求同时遇到缓存未命中
- **THEN** 系统至多执行一次上游加载，全部请求共享同一个完整成功快照或同一次稳定失败

#### Scenario: 上游刷新失败
- **WHEN** 快照已到期且 `/api/pricing` 超时、不可用或返回冲突数据
- **THEN** 新接口遵循目录稳定上游错误，不返回过期或部分详情与供应商选项，也不缓存失败结果

#### Scenario: 版本跨越缓存刷新边界
- **WHEN** 列表与后续详情或供应商请求恰好使用不同定价版本
- **THEN** 各响应分别标明实际 `pricingVersion`，客户端可以重新获取同版本数据而系统不伪称跨请求原子一致
