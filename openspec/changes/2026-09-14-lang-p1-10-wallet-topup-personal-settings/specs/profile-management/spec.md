## Purpose

为已登录用户提供与冻结版 New API 实际能力一致的基础资料和密码修改入口，并用当前密码校验、字段白名单及缓存同步保护敏感写操作。

## ADDED Requirements

### Requirement: 当前资料更新接口
系统 SHALL 提供受 Portal 会话和 CSRF 保护的 `PUT /portal/api/profile`，接受完整的 `username`、可为空的 `displayName`、`currentPassword`，以及成对出现且一致的可选 `newPassword` 与 `confirmPassword`。当前基线只允许修改用户名、显示名和密码；email 只读，phone 不属于 Portal profile，任何角色、用户组、额度、状态、绑定账号、上游路径或未知字段 MUST 在访问上游前被拒绝。

#### Scenario: 修改用户名和显示名
- **WHEN** 已登录用户提交合法的新用户名、显示名和正确当前密码且不提交新密码
- **THEN** 系统更新允许字段，重新读取上游 profile，并返回最新的 `id`、`username`、`displayName` 和可空 `email`

#### Scenario: 修改密码
- **WHEN** 已登录用户提交正确当前密码以及合法且两次一致的新密码
- **THEN** 系统在同一次上游更新中修改密码，并返回重新读取的最新裁剪 profile

#### Scenario: 尝试修改未支持字段
- **WHEN** 请求包含 email、phone、role、group、quota、status、binding 或其他未知字段
- **THEN** 系统返回 `INVALID_ARGUMENT`，不调用 New API

### Requirement: 当前密码校验与失败语义
任一资料更新 MUST 要求当前密码并由 New API 冻结版密码校验作为最终权威。当前密码错误 SHALL 返回 `INVALID_ARGUMENT`，用户名冲突 SHALL 返回 `RESOURCE_CONFLICT`；写请求在发送后超时、断连或响应非法 SHALL 返回 `OPERATION_RESULT_UNKNOWN` 且 MUST NOT 自动重试。响应、日志、错误对象、URL、缓存和遥测不得包含当前密码、新密码或完整上游正文。

#### Scenario: 当前密码错误
- **WHEN** 用户使用错误的当前密码修改任一允许字段
- **THEN** 系统拒绝更新并返回不触发会话失效的 `INVALID_ARGUMENT`，页面把错误关联到当前密码输入

#### Scenario: 新密码确认不一致
- **WHEN** `newPassword` 与 `confirmPassword` 缺少其一或内容不同
- **THEN** 系统在访问上游前返回 `INVALID_ARGUMENT`

#### Scenario: 用户名冲突
- **WHEN** 上游因新用户名已被占用而拒绝更新
- **THEN** 系统返回 `RESOURCE_CONFLICT`，不泄露冲突账号或上游原始消息

#### Scenario: 更新结果无法确认
- **WHEN** 资料更新已经发送但 Portal 无法确认上游最终结果
- **THEN** 系统返回 `OPERATION_RESULT_UNKNOWN`，前端不自动重放密码并提示用户重新读取资料核对

### Requirement: 更新后的会话与缓存一致性
资料更新成功后 Portal MUST 通过当前上游会话重新读取 profile 后才报告成功；前端 SHALL 用成功响应替换唯一认证 profile 缓存，并清除资料表单中的所有密码。用户名改变后当前页面和导航 MUST 立即使用新值；密码改变后当前已验证会话继续按上游实际有效状态工作，旧密码不得再创建新会话，新密码应可用于后续登录。

#### Scenario: 显示名更新后刷新
- **WHEN** 用户成功修改显示名并刷新页面
- **THEN** 页面通过 profile 接口恢复相同新值，不回退到旧缓存

#### Scenario: 用户名更新后导航同步
- **WHEN** 用户成功修改用户名
- **THEN** 控制台用户区域和设置表单立即使用重新读取的新用户名

#### Scenario: 密码更新后再次登录
- **WHEN** 用户成功修改密码并在之后退出
- **THEN** 旧密码登录失败且新密码登录成功，不影响更新响应中已确认有效的当前会话

### Requirement: 个人设置页面安全交互
`/dashboard/settings` SHALL 分离基础资料与密码区域，展示只读 email，并明确不提供 phone 修改。提交期间 MUST 防止重复操作；成功后清空密码字段并给出安全反馈，失败时保留非敏感输入和 requestId。页面 SHALL 提供中英文文案、键盘可用的标签与错误关联，并在窄屏下不产生整页横向溢出。

#### Scenario: 打开个人设置
- **WHEN** 已登录用户进入个人设置页面
- **THEN** 表单以当前 profile 填充用户名和显示名，email 只读，且不显示手机号输入

#### Scenario: 提交期间重复操作
- **WHEN** 一次资料更新请求仍在进行中
- **THEN** 提交控件保持禁用且不会产生并发写请求

#### Scenario: 更新失败
- **WHEN** 更新返回安全业务错误或结果未确认
- **THEN** 页面清空所有密码字段、保留可安全重填的资料字段，并显示错误及 requestId
