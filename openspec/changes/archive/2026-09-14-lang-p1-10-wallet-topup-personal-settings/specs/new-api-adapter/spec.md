## ADDED Requirements

### Requirement: 冻结版充值只读操作适配
New API 适配器 SHALL 为 v0.13.2 提供充值配置和当前用户充值记录两个语义化只读操作，分别固定调用 `GET /api/user/topup/info` 与 `GET /api/user/topup/self`，并同时发送服务端会话中的 `session` Cookie 和 `New-Api-User` Header。适配器 MUST 只接受业务层提供的页码和页大小，不得接受 userId、username、任意上游路径或 URL。

#### Scenario: 查询充值配置
- **WHEN** 业务层查询当前用户充值能力
- **THEN** 适配器只调用 `/api/user/topup/info`，并把支付启用标志转换为受支持或关闭状态

#### Scenario: 查询充值记录
- **WHEN** 业务层查询当前用户充值记录页
- **THEN** 适配器只调用 `/api/user/topup/self` 并发送声明的分页参数，不改用管理员充值接口

#### Scenario: 调用方提交用户身份
- **WHEN** Portal 请求包含 userId、username 或任意上游地址参数
- **THEN** 参数在进入适配器前被拒绝，适配器只使用认证会话中的身份

### Requirement: 充值响应白名单与安全收敛
充值配置适配 SHALL 只解析已知支付启用布尔值；在本阶段没有受支持渠道时，不得把 `pay_methods`、`creem_products`、折扣、最低金额、颜色或地址交给业务层。充值记录适配 SHALL 只解析 trade number、账户充值额度、支付方式、创建/完成时间和状态，并严格验证分页及声明字段；userId、数据库 id、payment provider、支付金额、回调数据和未知字段 MUST NOT 离开适配边界。

#### Scenario: 关闭配置包含残留方法
- **WHEN** 所有启用标志均为 false 但上游仍返回预置 `pay_methods` 或金额选项
- **THEN** 适配结果为关闭且不包含任何可用支付方式或预置金额

#### Scenario: 记录包含内部字段
- **WHEN** 上游充值记录包含 user_id、id、payment_provider、money 或其他未声明字段
- **THEN** 这些字段不会进入业务对象、Portal 响应、异常或日志

#### Scenario: 分页记录非法
- **WHEN** 任一已声明充值记录字段或分页元数据非法
- **THEN** 整页安全失败为 `UPSTREAM_ERROR`，不返回部分记录

### Requirement: 冻结版个人资料更新适配
New API 适配器 SHALL 通过固定 `PUT /api/user/self` 执行当前用户资料更新，将 Portal 的 `username`、`displayName`、`currentPassword` 和可选新密码分别转换为上游 `username`、`display_name`、`original_password` 和 `password`。请求 MUST 同时携带匹配的会话 Cookie 与用户 Header，不得发送 email、phone、role、group、quota、status、用户 id 或其他未允许字段；更新成功后 SHALL 使用既有当前用户只读操作重新读取裁剪 profile。

#### Scenario: 只修改基础资料
- **WHEN** 业务层提交用户名、显示名和当前密码且不修改密码
- **THEN** 适配器发送空的上游 password 或冻结版要求的等价省略语义，并保留原密码校验

#### Scenario: 同时修改密码
- **WHEN** 业务层提交合法新密码
- **THEN** 适配器在同一次更新中发送新密码和当前密码，不调用邮件重置或管理员接口

#### Scenario: 更新成功后读取资料
- **WHEN** 上游确认资料更新成功
- **THEN** 适配器再调用 `GET /api/user/self`，只把最新裁剪 profile 返回业务层

### Requirement: 资料更新凭据与结果安全
适配器 MUST 把上游“原密码错误”归一化为当前密码不匹配，把用户名占用归一化为资源冲突，并把未知业务失败转换为安全上游错误。资料更新属于不可自动重试写操作；发送后超时、断连或响应非法 MUST 转换为结果未确认。当前密码、新密码和上游原始请求/响应不得进入日志、异常、缓存或遥测。

#### Scenario: 上游拒绝当前密码
- **WHEN** 上游因 `original_password` 不正确返回业务失败
- **THEN** 适配器返回可由 profile 边界映射的当前密码错误，不返回上游原始消息

#### Scenario: 写请求超时
- **WHEN** `PUT /api/user/self` 发送后超时或断连
- **THEN** 适配器返回结果未确认且不自动重试，调用方随后只能重新读取 profile 核对

#### Scenario: 凭据日志检查
- **WHEN** 资料更新成功、被拒绝或发生异常
- **THEN** 访问日志和应用日志均不包含 `original_password`、`password` 或其值
