## Purpose

为已登录用户提供以 New API 为唯一数据权威的 API Key 全生命周期管理能力，同时让额度、期限和访问限制具有稳定易懂的 Lang API 语义，并将明文凭证暴露限制在用户主动确认的最小范围内。

## ADDED Requirements

### Requirement: API Key 列表查询与脱敏
系统 SHALL 为当前用户提供从 1 开始分页的 API Key 列表，并支持名称片段搜索和 `enabled`、`disabled`、`expired`、`exhausted` 状态筛选。列表结果 SHALL 按创建时间倒序排列，`total` SHALL 表示应用全部查询条件后的结果数；每项只包含 Lang API 声明字段，Key 只能以带 `sk-` 前缀的掩码形式返回。

#### Scenario: 默认分页列表
- **WHEN** 已登录用户请求第一页且未提供搜索或状态条件
- **THEN** 系统返回该用户最新的 Key、正确分页总数和掩码，不返回其他用户的数据或任何明文 Key

#### Scenario: 组合搜索和筛选
- **WHEN** 用户提交合法名称片段及一个状态筛选
- **THEN** 系统只返回同时满足名称和状态条件的当前用户 Key，分页总数基于完整过滤结果而不是当前页

#### Scenario: 空结果
- **WHEN** 当前用户没有 Key 或查询条件无匹配项
- **THEN** 系统返回空 `items`、`total=0` 和合法分页元数据，而不是把空结果视为错误

### Requirement: API Key 稳定字段语义
API Key 详情与列表 SHALL 使用稳定字段：`id`、`name`、`maskedKey`、`status`、`createdAt`、可空 `expiresAt`、`quota`、`usedQuota`、`modelRestrictions` 和 `allowedIps`。额度 SHALL 以非负整数 quota 单位表达，并显式包含 `unit="quota"` 与是否无限；上游 `-1` 永不过期值 SHALL 映射为 `expiresAt=null`。模型限制 SHALL 使用去重字符串数组；IP 限制第一阶段 SHALL 只接受规范化的 IPv4 或 IPv6 字面量数组，不承诺 CIDR 或域名匹配。冻结版在创建时即写入 `accessed_time`，因此本阶段 MUST NOT 将其错误展示为“最后调用时间”。

#### Scenario: 有限额度和过期时间
- **WHEN** 上游 Key 具有有限剩余额度、已用额度和 Unix 过期时间
- **THEN** Portal 返回带 `quota` 单位的非负整数及带时区 ISO 8601 时间，前端无需执行上游换算

#### Scenario: 无限额度和永不过期
- **WHEN** 上游 Key 标记为无限额度且过期时间为 `-1`
- **THEN** Portal 返回 `quota.unlimited=true`、明确的 `unit="quota"` 和 `expiresAt=null`

#### Scenario: 无效限制字段
- **WHEN** 创建或编辑请求包含重复/空模型名、非法 IP、负额度、过去的过期时间或互相矛盾的无限额度字段
- **THEN** 系统在调用上游前返回 `INVALID_ARGUMENT` 并指出公开字段，不回显完整请求正文

### Requirement: 创建 API Key
系统 SHALL 允许当前用户创建名称不超过 50 个字符的 API Key，并配置有限或无限额度、可空过期时间、可选模型限制及 IP 限制。创建是不可自动重试的写操作；成功响应 MUST NOT 包含明文 Key，随后 SHALL 使列表数据失效，并向当前交互提供一次明确的“显示并复制”提示。

#### Scenario: 创建受限 Key
- **WHEN** 用户提交合法名称、有限 quota、未来过期时间、模型数组和 IP 数组
- **THEN** 系统只创建一次上游 Key，返回不含明文的成功结果，并使新 Key 可在刷新后的列表中看到

#### Scenario: 重复提交被前端阻止
- **WHEN** 创建请求仍在处理中且用户再次触发提交
- **THEN** 前端不发送并发创建请求，也不因超时或网络错误自动重放原请求

#### Scenario: 上游达到 Key 数量或额度限制
- **WHEN** New API 拒绝创建，因为用户达到 Key 数量上限或额度参数超出允许范围
- **THEN** Portal 返回稳定安全的业务错误，不透传上游原始消息，且前端保留用户可修正的非敏感表单值

### Requirement: 详情与编辑保持完整状态
系统 SHALL 允许当前用户读取单个 Key 的脱敏详情，并编辑名称、额度、过期时间、模型限制和 IP 限制。编辑时 Portal MUST 先读取当前上游对象并合并受支持字段，避免 New API 的完整对象更新语义意外清空状态、已用额度或未暴露字段；客户端不得提交或覆盖 Key 值、所有者、创建时间和已用额度。

#### Scenario: 编辑单个限制字段
- **WHEN** 用户只修改一个 Key 的模型限制
- **THEN** 系统保留该 Key 的名称、额度、期限、IP 限制、状态和已用额度，并只更新允许修改的目标字段

#### Scenario: 修改不存在或不属于当前用户的 Key
- **WHEN** 用户请求读取或编辑一个不存在或属于其他用户的 id
- **THEN** 系统统一返回 `NOT_FOUND`，不暴露对象是否属于其他用户

### Requirement: 启停 API Key
系统 SHALL 允许当前用户将 Key 在 `enabled` 与 `disabled` 间切换，并将状态变更与一般编辑分成独立操作。`expired` 或 `exhausted` Key 在期限或额度未修复前不得伪装为启用成功；状态写入失败或结果不确定时不得乐观显示最终状态。

#### Scenario: 停用有效 Key
- **WHEN** 用户确认停用一个 `enabled` Key
- **THEN** 系统执行一次专用状态更新，成功后使详情和列表缓存失效并显示 `disabled`

#### Scenario: 启用已过期 Key
- **WHEN** 用户尝试直接启用一个期限仍在过去的 `expired` Key
- **THEN** 系统返回稳定的状态冲突错误，提示先修改期限，且不显示为已启用

### Requirement: 删除 API Key
系统 SHALL 在用户明确确认后删除当前用户的单个 Key。删除 SHALL 是不可自动重试的操作；成功后该 Key 必须从详情和列表消失，并且后续不能再通过 reveal 或 LANG-P1-07 的模型网关使用。

#### Scenario: 确认删除
- **WHEN** 用户在包含 Key 名称的确认界面中确认删除
- **THEN** 系统只发送一次删除请求，成功后移除相关缓存并返回列表的有效页面

#### Scenario: 删除结果不确定
- **WHEN** 删除请求发送后发生上游超时或连接中断
- **THEN** 系统返回明确的结果未确认错误且不自动重试，前端提供刷新列表核对状态的操作

### Requirement: 受控显示与复制明文 Key
系统 SHALL 仅通过 `POST /portal/api/api-keys/{id}/reveal` 为当前用户返回单个 Key 的完整值，并统一规范为带 `sk-` 前缀的 `secret`。调用前必须显示风险说明并由用户明确确认；响应 MUST 使用 `Cache-Control: no-store`。明文 SHALL 仅保存在当前组件的短生命周期内，关闭对话框、复制成功、路由离开、会话失效或设定的短时窗口结束时必须清除。

#### Scenario: 用户确认 reveal
- **WHEN** 已登录用户确认显示其拥有的 Key
- **THEN** 系统返回一次完整 `secret`，页面允许复制并明确提示此值敏感且不会在列表中持续显示

#### Scenario: reveal 他人或已删除 Key
- **WHEN** 用户请求显示不存在、已删除或属于其他用户的 Key
- **THEN** 系统返回 `NOT_FOUND`，响应与日志均不包含明文或所有者线索

#### Scenario: 关闭明文对话框
- **WHEN** 用户关闭 reveal 对话框、复制成功、离开页面或会话失效
- **THEN** 前端立即从组件状态移除明文，且不将其写入查询缓存、浏览器持久化、URL、剪贴板以外的持久位置或遥测事件

### Requirement: New API 保持唯一数据权威
Portal API SHALL 不创建 API Key 数据表、Key 明文缓存或第二套状态机。每次列表、详情、写操作和 reveal SHALL 以当前已校验用户身份访问 New API；Portal 的查询缓存只能存在于前端会话内且不得包含明文。

#### Scenario: 页面刷新后读取 Key
- **WHEN** 用户刷新 API Key 页面
- **THEN** 页面从 Portal API 重新读取 New API 当前数据，不依赖 Portal 本地持久化副本
