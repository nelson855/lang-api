# user-authentication Specification Delta

## MODIFIED Requirements

### Requirement: 认证选项与注册策略一致
系统 SHALL 提供可匿名访问的 `GET /portal/api/auth/options`，返回 `registrationEnabled`、`registrationDisabledReason`、`emailVerificationEnabled` 和 `captchaEnabled`。LANG-P1-05 的已验证基线 MUST 固定后两项为 `false`；`registrationEnabled` 只有在管理员注册开关开启、发布模式为 `PUBLIC` 且用户协议与隐私政策均可用时才为 `true`。关闭公开注册时，已有账号仍可登录，新账号只能由管理员在 New API 私网后台创建；关闭原因 MUST 为 `ADMIN_DISABLED`、`PREVIEW_MODE` 或 `LEGAL_UNAVAILABLE` 之一，并在开放时为 `null`。

#### Scenario: 生产环境默认关闭注册
- **WHEN** `prod` 未显式开启管理员注册开关
- **THEN** 认证选项返回 `registrationEnabled=false` 和 `ADMIN_DISABLED`，公开注册接口不可创建用户

#### Scenario: 预览环境不开放注册
- **WHEN** 管理员注册开关开启但发布模式为 `PREVIEW`
- **THEN** 认证选项返回 `registrationEnabled=false` 和 `PREVIEW_MODE`

#### Scenario: 开发测试默认开放注册
- **WHEN** 应用分别以 `dev` 或 `test` 默认配置运行
- **THEN** 管理员注册开关保持开启，但默认 `PREVIEW` 门禁使认证选项返回 `registrationEnabled=false` 和 `PREVIEW_MODE`

#### Scenario: 法律正文缺失
- **WHEN** 发布模式为 `PUBLIC`、管理员注册开关开启但任一法律正文不可用
- **THEN** 认证选项返回 `registrationEnabled=false` 和 `LEGAL_UNAVAILABLE`

#### Scenario: 正式发布且法律正文完整
- **WHEN** 管理员注册开关开启、发布模式为 `PUBLIC` 且两份法律正文均可用
- **THEN** 认证选项返回 `registrationEnabled=true` 和空关闭原因，并允许用户名密码注册

#### Scenario: 不宣称未启用验证方式
- **WHEN** 客户端查询认证选项
- **THEN** 邮箱验证和验证码均返回关闭，页面不得展示对应输入或伪造验证流程

### Requirement: 条件用户名密码注册
系统 SHALL 通过 `POST /portal/api/auth/register` 接受经过长度和格式校验的 `username`、`password` 与 `confirmPassword`。只有管理员注册开关开启、发布模式为 `PUBLIC` 且用户协议与隐私政策在提交时均可用，才可调用上游注册；注册成功 MUST NOT 自动建立登录会话，并 SHALL 引导用户使用新账号登录。任一条件不满足时 MUST 在调用上游前返回 `REGISTRATION_DISABLED`。

#### Scenario: 注册成功后登录
- **WHEN** 全部有效注册条件满足且用户提交合法、未占用的用户名与一致密码
- **THEN** 系统创建上游用户但不设置会话 Cookie，返回可继续登录的成功结果

#### Scenario: 注册策略关闭
- **WHEN** 管理员开关、发布模式或法律正文可用性任一条件不满足
- **THEN** 系统返回 `REGISTRATION_DISABLED`，且不请求 New API 注册操作

#### Scenario: 选项读取后法律正文失效
- **WHEN** 客户端先看到注册开放，但提交前法律正文缓存失效且重新读取失败
- **THEN** 注册接口重新校验有效策略并拒绝调用上游

#### Scenario: 注册输入不合法
- **WHEN** 用户名或密码不符合公开校验规则，或两次密码不一致
- **THEN** 系统返回 `INVALID_ARGUMENT`，不在响应或日志中记录密码

#### Scenario: 用户名已存在
- **WHEN** New API 拒绝一个重复用户名
- **THEN** 系统返回稳定且不含 New API 品牌或原始消息的注册失败信息
