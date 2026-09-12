## MODIFIED Requirements

### Requirement: 占位页真实表达阶段状态
模型广场和开发文档在对应业务尚未交付时 SHALL 使用明确的阶段占位内容，说明功能尚未开放或将在后续阶段接入。占位页 MUST NOT 展示伪造模型、价格、用量、余额、认证结果或可点击但无行为的主要操作；登录、注册和控制台 SHALL 在 LANG-P1-05 使用真实认证状态，不再展示认证占位内容。

#### Scenario: 尚未接入模型数据
- **WHEN** 用户在 LANG-P1-08 前访问模型广场
- **THEN** 页面显示功能准备状态，不展示静态伪造模型或价格

#### Scenario: 尚未接入认证
- **WHEN** 部署版本早于 LANG-P1-05，尚无真实认证接口
- **THEN** 登录或注册页不得提交伪造认证请求，也不得宣称账户操作成功

#### Scenario: 认证阶段已经交付
- **WHEN** 用户访问登录、注册或控制台入口
- **THEN** 页面使用真实认证选项与会话状态，不宣称未接入认证或展示伪造成功结果

## ADDED Requirements

### Requirement: 可用登录页面
`/login` SHALL 提供用户名和密码表单、客户端基础校验、提交中状态和安全错误反馈。登录成功后 SHALL 导航至合法 `returnTo` 或默认控制台；已登录用户访问登录页 SHALL 被引导到控制台。页面 MUST NOT 提供未实现的 OAuth、MFA、Passkey 或忘记密码流程。

#### Scenario: 提交正确凭据
- **WHEN** 匿名用户填写合法凭据并登录成功
- **THEN** 页面进入安全返回地址或 `/dashboard`，且不会在 URL 或浏览器存储中保留密码

#### Scenario: 提交错误凭据
- **WHEN** 服务端返回 `INVALID_CREDENTIALS`
- **THEN** 页面显示统一凭据错误，不说明用户名是否存在或账户是否被禁用

#### Scenario: 已登录访问登录页
- **WHEN** authenticated 用户打开 `/login`
- **THEN** 页面不重复显示登录表单并导航到控制台

### Requirement: 注册页面服从运行时策略
`/register` SHALL 以认证选项为唯一展示依据：公开注册开启时显示用户名、密码和确认密码表单；关闭时显示由管理员创建账号的说明且不显示可提交表单。公开布局的注册入口 MUST 同步隐藏或改为不可注册说明，且页面不得展示未启用的邮箱、验证码或邀请码字段。

#### Scenario: 注册开放
- **WHEN** 认证选项返回 `registrationEnabled=true`
- **THEN** 注册页显示可提交的用户名密码表单，成功后引导到登录页

#### Scenario: 注册关闭
- **WHEN** 认证选项返回 `registrationEnabled=false`
- **THEN** 注册页显示暂未开放和联系管理员获取账号的说明，不发送注册请求

#### Scenario: 注册策略在加载中
- **WHEN** 认证选项尚未返回
- **THEN** 页面显示加载状态，不短暂展示注册表单

### Requirement: 会话状态与退出交互
控制台 SHALL 显示当前用户的安全展示名和退出操作。会话失效后页面 SHALL 清除用户态内容并引导至登录；退出操作在请求进行中 SHALL 防止重复提交，完成或服务器已清除本地凭证的失败结果都 SHALL 进入匿名界面。

#### Scenario: 用户主动退出
- **WHEN** 已登录用户触发退出
- **THEN** 页面等待一次退出请求完成、清除用户态并导航到公开页或登录页

#### Scenario: 控制台会话过期
- **WHEN** profile 或受保护请求返回 `UNAUTHENTICATED`
- **THEN** 控制台不继续显示旧用户内容，并引导到带安全 returnTo 的登录页

#### Scenario: 重复点击退出
- **WHEN** 首次退出请求仍在进行中
- **THEN** 后续点击不会产生并发退出请求
