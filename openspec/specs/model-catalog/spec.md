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
`/models` 页面 SHALL 展示真实目录，并提供对模型 ID 和明确显示名称的大小写不敏感搜索，以及基于非空厂商名称的单选或多选筛选。搜索和厂商筛选 SHALL 在已取得的目录快照上完成，不为每次输入向服务端发请求。页面 MUST 提供加载、目录为空、筛选无结果、价格缺失和请求失败状态，并在桌面与窄屏下保持可操作。

#### Scenario: 搜索模型
- **WHEN** 用户输入模型 ID 或显示名称的一部分
- **THEN** 页面即时显示匹配项，并且不新增模型目录网络请求

#### Scenario: 按厂商筛选
- **WHEN** 用户选择一个由当前目录非空厂商字段生成的筛选项
- **THEN** 页面只显示该厂商模型；未知厂商模型不会被错误归入已知厂商

#### Scenario: 目录为空
- **WHEN** 接口成功返回空模型数组
- **THEN** 页面说明当前尚未配置公开模型，不显示静态示例模型或虚假价格

#### Scenario: 目录请求失败
- **WHEN** 模型目录接口返回错误
- **THEN** 页面显示安全错误和重试操作，并保留公开导航可用性

