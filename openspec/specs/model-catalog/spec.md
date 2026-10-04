# model-catalog Specification

## Purpose

向未登录访客和已登录用户提供与冻结版 New API 公共定价口径一致、字段稳定且不泄露内部倍率与渠道信息的基础模型目录。

## Requirements

### Requirement: 公共模型目录接口
系统 SHALL 提供无需认证的 `GET /portal/api/models`，并通过统一成功包装返回 `{models, pricingVersion}`。`models` MUST 是完整数组而不是分页结果；每个模型 MUST 使用 Lang API 自有字段，至少包含 `id`、可空 `displayName`、可空 `provider`、`availability` 和可空 `pricing`。接口 MUST NOT 返回 New API 原始包装、用户分组、分组倍率、渠道 ID、内部路由或上游地址。

#### Scenario: 匿名获取模型目录
- **WHEN** 未登录访客请求 `GET /portal/api/models`
- **THEN** 系统返回当前公共模型目录，且不要求 Portal 会话或 API Key

#### Scenario: 使用错误方法
- **WHEN** 客户端使用 GET 以外的方法请求 `/portal/api/models`
- **THEN** 系统返回统一 JSON `METHOD_NOT_ALLOWED`/405，且不把请求转发为其他 New API 操作

#### Scenario: 上游增加未知字段
- **WHEN** New API `/api/pricing` 返回适配器未声明的字段、内部倍率结构或渠道信息
- **THEN** 系统忽略这些字段，Portal 响应结构保持不变且不包含内部信息

### Requirement: 模型范围沿用 New API 公共定价口径
目录 SHALL 只来源于当前 New API `/api/pricing` 返回的 `data`，不得调用需登录的 `/api/models` 形成用户级交集，也不得根据 API Key 状态、余额、模型限制或名称规则自行增加、删除或推断模型。`availability` MUST 只表达该模型在 New API 公共定价目录中的全局状态，不得宣称任一用户 Key 必然可以调用。

#### Scenario: New API 返回已公开模型
- **WHEN** `/api/pricing` 返回一个结构合法的模型条目
- **THEN** Portal 目录包含相同模型 ID，并将其标记为 New API 当前公开目录中的可用模型

#### Scenario: 模型不在公共定价结果中
- **WHEN** 某模型仅存在于 New API 内部模型列表或历史配置但未出现在当前 `/api/pricing` 数据中
- **THEN** Portal 目录不展示该模型，也不使用静态列表补齐

#### Scenario: 用户 Key 存在额外限制
- **WHEN** 某模型出现在公共目录中，但用户 Key 被禁用、额度不足或限制了模型
- **THEN** 目录仍保持全局口径，并明确说明最终可调用性由实际 Key 和 P1-07 数据面校验

### Requirement: 模型展示字段不得伪造
系统 SHALL 将模型 ID 作为唯一稳定标识。显示名称、厂商名称及其他展示元数据只有在上游提供明确且可关联的数据时才可返回；缺失或无法可靠关联时 MUST 返回 `null`。前端 MUST 以模型 ID 作为主标签，不得通过名称前缀、品牌常识或静态映射猜测厂商、上下文窗口、发布日期、模态或能力。

#### Scenario: 厂商关联有效
- **WHEN** 模型条目的厂商标识能与同一 `/api/pricing` 响应中的厂商记录唯一匹配
- **THEN** Portal 返回裁剪后的厂商名称，不返回厂商 ID、图标地址或内部描述

#### Scenario: 展示元数据缺失
- **WHEN** 上游模型没有明确显示名称或厂商无法匹配
- **THEN** 对应字段为 `null`，页面继续显示模型 ID 且不生成推测值

#### Scenario: 上游返回非法模型标识
- **WHEN** 模型 ID 为空、包含控制字符或不满足接口长度边界
- **THEN** 该条目不进入成功目录，并产生不含原始敏感内容的可观测映射记录

### Requirement: 基础价格语义稳定且可核对
模型 `pricing` SHALL 使用十进制字符串返回金额，避免二进制浮点误差，并明确提供 `mode`、`currency`、`unit` 及与模式相符的价格字段。按 Token 计费时 SHALL 返回每百万 Token 的基础输入价和基础输出价；按次计费时 SHALL 返回每次请求的基础价。换算 MUST 在后端集中完成并采用当前部署与 New API 一致的基础计费参数；前端不得读取或重算 `model_ratio`、`completion_ratio`、`group_ratio` 或 quota 公式。无法安全换算的价格 MUST 为 `null`，不得显示为 `0` 或“免费”。

#### Scenario: 按 Token 计费模型
- **WHEN** 上游条目声明按 Token 计费且倍率、输出倍率和部署计费参数均合法
- **THEN** Portal 返回 `mode=TOKEN`、`currency=USD`、`unit=PER_MILLION_TOKENS` 及非负的 `input`、`output` 十进制字符串

#### Scenario: 按次计费模型
- **WHEN** 上游条目声明固定模型价格且数值合法
- **THEN** Portal 返回 `mode=REQUEST`、`currency=USD`、`unit=PER_REQUEST` 及非负的 `request` 十进制字符串

#### Scenario: 价格字段缺失或非法
- **WHEN** 上游价格为缺失值、非有限数、负数、未知计费类型或部署计费参数不完整
- **THEN** 模型仍可按目录口径展示，但 `pricing` 为 `null` 并显示“价格暂不可用”，不得推断或默认为零

#### Scenario: 抽样核对实际扣费
- **WHEN** 使用默认计费组测试用户、目录中的真实模型和有效 Key 完成一次可计量调用
- **THEN** 按请求日志 Token/请求次数计算的基础费用与页面价格口径一致；因用户组或请求特征产生的额外调整被明确区分

### Requirement: 短期缓存和失败行为
模型目录 SHALL 使用有界的短期进程内缓存减少对 New API `/api/pricing` 的重复请求，默认新鲜时间为 60 秒并允许通过分环境 `.properties` 调整。并发缓存未命中 SHALL 合并为至多一次上游加载；上游失败时不得用无期限旧价格冒充当前数据，也不得写入失败结果或部分映射结果。

#### Scenario: 新鲜缓存命中
- **WHEN** 多个请求在缓存新鲜时间内读取模型目录
- **THEN** 系统返回同一完整快照且不为每个请求重复访问 New API

#### Scenario: 并发缓存未命中
- **WHEN** 多个请求同时触发同一目录缓存加载
- **THEN** 系统至多执行一次上游定价请求，其余请求共享该次完整结果或失败

#### Scenario: 首次加载失败
- **WHEN** 缓存中没有有效快照且 New API 超时、不可用或返回非法数据
- **THEN** 系统返回稳定 Portal 上游错误，页面显示可重试错误状态且不展示伪造目录

#### Scenario: 已缓存快照到期后刷新失败
- **WHEN** 新鲜缓存已经过期且刷新 New API 失败
- **THEN** 系统返回稳定错误而不是无限延长旧价格，并保留下一次请求重新加载的能力

### Requirement: 模型广场的搜索筛选和状态表达
`/models` 页面 SHALL 展示真实完整目录，并提供对模型 ID 和明确显示名称的大小写不敏感搜索，以及单选厂商筛选。厂商选项 MUST 来源于 `GET /portal/api/model-providers` 的 `value`、`label` 和 `modelCount`，不得从列表自行补齐、使用静态品牌表或将未知厂商归为“其他”。厂商计数 MUST 表达完整目录数量而不是当前搜索命中数。搜索和筛选 SHALL 在已取得的完整目录上完成，不为每次输入或选择新增目录网络请求。

页面 MUST 提供加载、目录为空、筛选无结果、价格缺失和请求失败状态，并在桌面与窄屏下保持可操作。供应商接口失败时 SHALL 保留成功加载的列表与搜索，将厂商控件标记为暂不可用并提供重试；已有厂商筛选 MUST 保留并继续按名称精确过滤，不得静默清空或生成替代选项。

#### Scenario: 搜索模型
- **WHEN** 用户输入模型 ID 或显示名称的一部分
- **THEN** 页面即时显示匹配项，并且不新增模型目录网络请求

#### Scenario: 按厂商筛选
- **WHEN** 用户选择一个由供应商目录返回的筛选项
- **THEN** 页面只显示 `provider` 精确等于该项 `value` 的模型，并保持接口给出的目录计数；未知厂商模型不会被错误归入已知厂商

#### Scenario: 目录为空
- **WHEN** 接口成功返回空模型数组
- **THEN** 页面说明当前尚未配置公开模型，不显示静态示例模型或虚假价格

#### Scenario: 目录请求失败
- **WHEN** 模型目录接口返回错误
- **THEN** 页面显示安全错误和重试操作，并保留公开导航可用性，不将旧目录标记为当前成功结果

#### Scenario: 供应商选项失败
- **WHEN** 列表成功而供应商接口失败，URL 中已有厂商条件
- **THEN** 页面保留搜索和该条件对应的过滤结果，单独显示厂商加载失败与重试，并允许清除原条件，不从模型数组生成可选厂商

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
模型详情的 `pricing` SHALL 与相同 `pricingVersion` 下模型目录中的基础价格完全一致，继续使用已有 TOKEN/REQUEST、USD 和单位契约。当前基线的真实扣费核对 SHALL 至少覆盖一个已接入的 TOKEN 模型；REQUEST 仅在当前目录实际存在来源、单位和固定按次语义均已验证的模型时才纳入实测，不得为了验收接入无业务需求的供应商或人为改写 TOKEN 模型的计费类型。`enhancedPricing` 只有在来源、计费维度、币种、单位和数值均已验证时才能成为数组；无法确认、部分确认或只存在内部倍率时 MUST 为 `null`，不得返回零价或空数组暗示已验证无额外收费。

增强价格项的 `currency` 和 `unit` MUST 为明确来源值，`price` MUST 是非负十进制字符串；同一 `type`、币种和单位出现冲突或重复时整个详情映射失败，不得任选其一。

#### Scenario: 详情基础价格与列表一致
- **WHEN** 客户端在相同 `pricingVersion` 下对照模型列表与详情
- **THEN** 两处模型 ID、供应商、可用性和基础 `pricing` 完全一致

#### Scenario: 当前样本只有基础倍率
- **WHEN** 当前基线只提供 model ratio、completion ratio 或固定请求价，没有独立增强计费证据
- **THEN** 详情沿用可安全换算的基础 `pricing`，`enhancedPricing` 返回 `null`

#### Scenario: 当前 TOKEN 样本交叉核对真实扣费
- **WHEN** 使用冻结计费参数、默认计费组、真实可调用 TOKEN 模型和有效 Key 完成可计量调用
- **THEN** 列表与详情的基础价格可由实际 Token 用量交叉核对，任何缓存、图片、音频、视频、搜索或用户组调整均被单独识别而非混入基础价

#### Scenario: 当前目录没有固定按次模型
- **WHEN** 当前目录没有来源、单位和固定按次语义均已验证的 REQUEST 模型
- **THEN** 系统保留 REQUEST 契约兼容性但不伪造 REQUEST 目录项、价格或真实扣费证据，后续接入该类能力时再增量验证

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

### Requirement: 搜索厂商与详情导航状态可由 URL 恢复
系统 SHALL 使用 `/models?q=<search>&provider=<value>` 表达搜索与单选厂商条件，并将同样的条件保留在详情链接 `/models/:modelRef` 的查询参数中。刷新、分享、直接进入和浏览器前进后退 MUST 恢复 URL 表达的条件。空参数 SHALL 省略；搜索 SHALL 忽略首尾空白，厂商 SHALL 保持大小写敏感的精确名称。未知厂商条件 MUST 保留为可清除条件并显示筛选无结果，不静默展示全部模型。

搜索输入 MUST 支持中文输入法组合过程，提供本地化、可键盘操作的清除按钮；清除后立即更新结果并将焦点回到输入框。详情的“返回模型广场” MUST 使用受控同源路径恢复条件，不依赖必须存在的浏览器历史，也不接受任意返回地址。

#### Scenario: 组合搜索和筛选进入详情
- **WHEN** 用户从带搜索与厂商条件的列表进入详情，再使用返回模型广场
- **THEN** 列表恢复相同条件与结果，详情中的模型不会因搜索条件改变身份

#### Scenario: 直接刷新有条件的地址
- **WHEN** 用户刷新 `/models?q=flash&provider=DeepSeek` 或携带这些参数的详情地址
- **THEN** 页面恢复相同搜索与厂商状态，且详情可直接加载，无需先访问列表

#### Scenario: 厂商已经从目录消失
- **WHEN** URL 中的厂商不在成功加载的当前供应商目录中
- **THEN** 页面显示可清除的未知厂商条件与无匹配状态，不添加虚假厂商选项或静默修改条件

### Requirement: 公开详情深链接严格定位原始模型
系统 SHALL 提供无需登录的 `/models/:modelRef`，其引用规则与现有详情 API 一致：原始模型 ID 的 UTF-8 字节经网址安全 Base64 无填充编码。页面 MUST 保持大小写、Unicode 和空白原值，严格拒绝非法字母表、填充、无效 UTF-8、非规范重编码和不满足 ID 边界的引用；非法引用 MUST 在发送详情请求前显示安全参数错误，不回显可疑解码内容。

详情 MUST 只使用匹配引用的接口响应；成功响应中的 `model.id` 不等于解码 ID 时 MUST 作为契约错误，不展示另一个模型。404 MUST 显示模型不存在或已下架，不自动选择其他模型；上游失败 MUST 提供安全重试。模型切换时旧响应 MUST NOT 覆盖新页面或短暂显示旧模型。

#### Scenario: 特殊字符模型深链接
- **WHEN** 用户打开由包含 `/`、`%`、空格、中文或表情的合法模型 ID 生成的详情链接
- **THEN** 页面和 API 均以单一路径段定位原模型，展示与复制的 ID 完全等于原始 ID

#### Scenario: 非法引用
- **WHEN** 地址包含填充符、非法字符、非法 UTF-8 或非规范引用
- **THEN** 页面显示安全参数错误和返回列表入口，不向详情 API 发起请求

#### Scenario: 有效引用但模型不存在
- **WHEN** 详情 API 返回 404
- **THEN** 页面明确显示模型不存在或已下架，不展示列表中的首个模型或历史详情

#### Scenario: 快速切换模型
- **WHEN** 用户从 A 详情进入 B 且 A 的请求较晚返回
- **THEN** B 页面只展示 B 的数据，A 的迟到响应不会覆盖它

### Requirement: 详情展示区分未知不支持与已验证空值
详情 SHALL 显示原始模型 ID、可用显示名、厂商、全局目录可用性、上下文 Token 上限、最大输出 Token、输入和输出模态、工具调用/推理/结构化输出/附件四项能力、发布日期、描述和标签。页面 MUST 使用详情接口值，不从品牌、模型名、端点或静态表推断。

`null` SHALL 显示本地化“暂无数据”；布尔 `false` SHALL 显示“不支持”，`true` SHALL 显示“支持”；已验证空数组 SHALL 表达“无”，不能等同于未知；合法数值零 MUST 保留为零。发布日期 MUST 按日历日期展示，不因时区转换偏移一天。描述和标签 MUST 作为文本展示，不执行接口提供的 HTML。`sortOrder` 只属于可空排序元数据，不在详情构造“推荐”标签，当前列表保持服务端顺序。

#### Scenario: 当前冻结基线
- **WHEN** 限制、模态、能力、发布信息、描述、标签和增强价格均为 null
- **THEN** 页面各信息区展示暂无数据，保持可用布局，不展示推测能力或静态默认参数

#### Scenario: 可空布尔能力
- **WHEN** 响应分别包含 true、false 和 null 能力
- **THEN** 页面分别显示支持、不支持与暂无数据，不使用真值判断合并 false 和 null

#### Scenario: 已验证空集合与长文本
- **WHEN** 响应含空标签数组及很长的模型 ID、描述或标签
- **THEN** 页面区分无标签与暂无标签数据，长文本可换行或局部滚动且完整值可访问，不撑宽页面

### Requirement: 列表与详情价格展示遵循十进制与单位契约
列表与详情 SHALL 展示接口提供的基础价格及其币种和单位，TOKEN 使用每百万 Token 的输入与输出价格，REQUEST 使用每次请求价格。前端 MUST 保持金额十进制字符串的数值精度，不使用二进制浮点转换、再次换算、硬编码倍率或静默舍入。零价格 MUST 展示数字零而非缺失，`pricing=null` MUST 显示价格暂不可用。

详情 SHALL 展示每项已确认增强价格的项目、币种、单位及十进制价格；`enhancedPricing=null` MUST 显示暂无数据，不能解释为无额外收费；已验证空数组才可表述暂无增强计费项目。未知单位 SHALL 保留接口单位原文，不猜测换算。基础价格说明 MUST 区分目录基础参考价与最终实际扣费，目录可用性 MUST NOT 承诺任一 Key 可调用。

#### Scenario: 十进制价格与真实零
- **WHEN** 基础或增强价格包含极小小数、较大十进制数或字符串零
- **THEN** 页面完整保留数值和币种单位，零显示为零，不显示科学计数法、错误舍入或免费推断

#### Scenario: 增强价格不可确认
- **WHEN** 基础价格可用但 enhancedPricing 为 null
- **THEN** 页面展示基础参考价格与增强价格暂无数据，不承诺该模型没有额外收费

### Requirement: 多接口组合不得伪称版本一致
列表与厂商选项，以及需对照的列表与详情，SHALL 比较响应中非空 `pricingVersion`。发现已知版本不一致时 MUST 暂停使用冲突选项或交叉价格，最多自动执行一轮成对重取；仍冲突 MUST 显示可手动重试的冲突状态，不无限请求。任一版本为空时 MUST 表明无法确认跨请求版本一致性，不将 null 相等作为证据；厂商选项只有在与当前列表按精确名称和完整计数核对后才可使用。

详情基本信息和价格 MUST 来源于同一次成功详情响应，不拼接缓存列表价格与新详情元数据。独立详情直达 SHALL 仅依赖详情接口即可展示，不为此强制获取列表。

#### Scenario: 已知版本跨刷新边界
- **WHEN** 列表和供应商响应具有不同的非空版本
- **THEN** 页面暂不使用冲突厂商项，成对重取一轮后相同则恢复，仍不相同则显示冲突与手动重试

#### Scenario: 版本缺失
- **WHEN** 任一个响应的 pricingVersion 为 null
- **THEN** 页面标明无法确认版本一致性，供应商名称和计数核对成功才启用选项，失败则按冲突规则恢复

#### Scenario: 列表与详情版本冲突
- **WHEN** 从已加载列表进入详情且发现不同的非空版本
- **THEN** 页面按有界策略重取列表与详情，持续冲突时仅展示当前详情自包含信息及一致性提示，不混用两次响应价格

### Requirement: 模型选择到复制和接入说明形成公开闭环
列表与详情 SHALL 提供复制原始模型 ID 和进入 `/docs?model=<encoded-original-id>` 的操作，不复制 modelRef、显示名或厂商名称。复制成功与失败 MUST 提供本地化可感知反馈，失败时保留可选中的完整 ID 供手动复制。详情入口与文档入口 MUST 是可键盘访问的导航链接。

进入开发文档时，若 `model` 参数精确匹配当前目录 SHALL 为该模型生成已有接入示例；指定模型不再存在时 MUST 明确说明不能为所选模型生成示例，不静默切换其他模型。没有指定 `model` 参数时 SHALL 保留现有默认模型选择行为。不得根据模态或能力展示尚未开放的协议或 Playground。

#### Scenario: 从详情进入接入示例
- **WHEN** 用户复制一个特殊字符模型 ID 或打开其文档链接
- **THEN** 剪贴板与文档选择均使用完全相同的原始 ID，文档沿用现有公开接入范围

#### Scenario: 剪贴板访问失败
- **WHEN** 浏览器没有剪贴板能力或拒绝复制
- **THEN** 页面显示失败与手动复制提示，完整模型 ID 仍可选择，不使用浏览器 alert

#### Scenario: 文档所选模型消失
- **WHEN** 文档 URL 指定的模型在最新目录中不存在
- **THEN** 页面显示所选模型不可用并提供返回目录入口，不以其他模型示例冒充该模型

### Requirement: 模型广场增强在语言设备与异步状态下可用
列表与详情 SHALL 沿用全站设计令牌、公开导航与共享反馈组件，所有新增文案、单位、状态和可访问名称 SHALL 提供中文与英文。桌面和 375px 窄屏 MUST 能查看字段、筛选、复制、进入详情与文档，页面不得产生整体横向溢出。加载与错误区域 MUST 保留兼容几何，控件具有可见焦点、关联标签和键盘行为；导航后 MUST 以标题或等价可访问公告标识新页面。

#### Scenario: 中英文与键盘路径
- **WHEN** 用户切换语言并仅使用键盘完成搜索、选择厂商、打开详情、复制和返回
- **THEN** 所有交互与反馈使用当前语言，可见焦点和可访问名称完整且不会被布局遮挡

#### Scenario: 窄屏加载失败与恢复
- **WHEN** 用户在 375px 宽度下经历加载、无结果、供应商失败、详情失败及重试
- **THEN** 状态与操作完整可见，成功结果不伪装为未确认旧数据，长 ID 不撑宽全页
