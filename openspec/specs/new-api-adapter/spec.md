# new-api-adapter Specification

## Purpose

建立 Lang API 与冻结版 New API 之间唯一且可测试的适配边界，集中控制上游路径、数据结构、传输可靠性及敏感认证信息，防止上游实现细节扩散到业务与前端。

## Requirements

### Requirement: New API 适配边界隔离
所有 New API HTTP 调用、路径常量和上游请求/响应 DTO SHALL 只存在于 `upstream.newapi` 适配边界。业务代码 MUST 通过语义化操作使用适配器，且 MUST NOT 拼接上游路径、引用上游 DTO、访问 New API 数据库或接受调用方提供的任意上游 URL。

#### Scenario: 执行声明过的上游操作
- **WHEN** 业务层调用一个已声明的 New API 语义化操作
- **THEN** 适配器使用该操作固定的方法与相对路径访问配置的唯一 New API Base URL

#### Scenario: 阻止边界外依赖
- **WHEN** 自动化架构测试扫描 Portal API 代码
- **THEN** `upstream.newapi` 之外不存在 New API DTO、路径常量或底层 HTTP 客户端调用

### Requirement: 上游传输资源受控
New API 客户端 SHALL 使用有界连接池并分别配置连接池获取、建立连接、读取和写入超时。连接池耗尽、连接失败、读写超时 MUST 转换为 Portal API 稳定错误；客户端默认 MUST 禁用传输层自动重试，尤其不得自动重放登录、注册、资料修改、创建 Key 或支付等写操作。

#### Scenario: 连接池有界
- **WHEN** 并发请求达到配置的连接池上限
- **THEN** 额外请求只等待配置的连接池获取时限，超时后释放资源并返回稳定上游不可用错误

#### Scenario: 建连失败
- **WHEN** New API 地址不可达或无法在连接时限内建立连接
- **THEN** 调用返回 `UPSTREAM_UNAVAILABLE`，连接资源被释放且响应不包含目标地址

#### Scenario: 上游读写超时
- **WHEN** 向 New API 写入请求或等待其响应超过对应时限
- **THEN** 调用被终止并返回 `UPSTREAM_TIMEOUT`，连接不会泄漏

#### Scenario: 写操作不被重放
- **WHEN** 一个写操作在发送期间断开连接或超时
- **THEN** 客户端最多发起一次上游请求，不进行自动重试

### Requirement: Cookie 与 Header 集中转换
适配器 SHALL 对上游请求 Header、响应 Header 和 Cookie 采用显式白名单。普通请求仅允许发送操作需要的 `Accept`、`Content-Type`、`X-Request-Id`；认证操作可额外声明 New API 所需的会话 Cookie 与 `New-Api-User`。浏览器的 `Host`、`Forwarded`、`X-Forwarded-*`、`Authorization` 及其他 Header MUST NOT 被整批透传。上游响应 Header 默认全部丢弃；只有操作明确声明的内容类型或会话 Cookie可被转换。

#### Scenario: 普通公开调用过滤请求 Header
- **WHEN** 浏览器请求携带 Cookie、Authorization、代理 Header 和自定义 Header，业务触发无需认证的上游调用
- **THEN** 上游只收到该操作白名单内的 Header 和统一 `X-Request-Id`

#### Scenario: 转换允许的会话 Cookie
- **WHEN** 认证操作收到已声明的 New API 会话 Cookie
- **THEN** 适配器移除上游 Domain，限制 Path，设置 `HttpOnly` 与适用环境的 `Secure`、`SameSite` 属性后才允许返回浏览器

#### Scenario: 丢弃未声明的响应 Header
- **WHEN** New API 返回服务标识、代理信息、内部地址或未列入操作白名单的 Header
- **THEN** Portal API 响应不包含这些 Header

### Requirement: 上游响应与错误显式翻译
每个 New API 操作 SHALL 只读取其响应 DTO 中明确允许的字段并转换为 Lang API DTO，未知字段 MUST 被忽略。适配器 MUST 同时识别非 2xx HTTP 状态和 HTTP 200 下 `success=false` 的上游业务失败，并转换为稳定内部异常；原始上游消息不得直接成为 Portal API 对外消息。

#### Scenario: 上游新增未知字段
- **WHEN** New API 在兼容响应中增加适配器未声明的字段
- **THEN** 已声明字段仍可转换，新增字段不会出现在 Lang API 响应中

#### Scenario: HTTP 200 业务失败
- **WHEN** New API 返回 HTTP 200 且响应包装为 `success=false`
- **THEN** 适配器按当前操作的错误映射处理失败，而不是把它当作成功数据返回

#### Scenario: 非法或非 JSON 上游响应
- **WHEN** New API 返回无法解析、字段缺失或非预期内容类型的响应
- **THEN** 系统返回安全的 `UPSTREAM_ERROR`，不透传原始正文

### Requirement: 可复用上游契约测试基座
工程 SHALL 提供不依赖真实 New API、数据库或网络的 New API 契约测试基座，可记录收到的方法、路径、Header 和正文，并可模拟正常响应、业务失败、非 2xx、断连、延迟及未知字段。测试夹具 MUST 使用 P1-02 脱敏样例或等价的无敏感数据结构。

#### Scenario: 验证请求与字段转换
- **WHEN** 某个后续业务模块新增 New API 适配操作
- **THEN** 它能复用测试基座断言请求白名单、响应裁剪和错误映射，而无需启动 Compose

#### Scenario: 模拟超时和断连
- **WHEN** 契约测试配置模拟服务器延迟或连接中断
- **THEN** 测试能够验证超时分类、无自动重试及连接资源释放行为

### Requirement: 冻结版认证操作适配
New API 适配器 SHALL 为 v0.13.2 提供注册、登录、当前用户和退出四个语义化操作，分别固定使用已验证的方法、路径、请求字段及认证方式。受保护操作 MUST 同时发送上游 `session` Cookie 与 `New-Api-User` Header；Portal API MUST NOT 调用或暴露上游长期 Access Token 接口。

#### Scenario: 登录响应转换
- **WHEN** 上游登录返回 HTTP 200、成功用户数据与 `session` Cookie
- **THEN** 适配器仅提取声明的用户字段和会话值，不向业务层暴露原始 Header 或上游包装

#### Scenario: 当前用户双要素认证
- **WHEN** Portal 校验当前会话
- **THEN** 适配器同时发送对应的上游 `session` Cookie 与 `New-Api-User` Header

#### Scenario: Portal 退出映射
- **WHEN** Portal 收到 POST 退出请求
- **THEN** 适配器以冻结版实际支持的 GET `/api/user/logout` 调用上游，且不允许浏览器直接决定上游路径或方法

#### Scenario: 禁止获取长期令牌
- **WHEN** 执行登录、刷新或当前用户操作
- **THEN** 适配器不调用 `/api/user/token`，任何响应也不包含该令牌

### Requirement: 认证 Cookie 专用映射
适配器 SHALL 只接受上游名为 `session` 的会话 Cookie，并将其映射到 Lang API 自有会话名；其他上游 `Set-Cookie` MUST 被丢弃。转发到上游时 SHALL 执行反向映射，且不得转发浏览器提供的其他 Cookie。过期或清除动作 MUST 同时覆盖 Portal 会话 Cookie 的相同 Path 和安全属性。

#### Scenario: 上游返回额外 Cookie
- **WHEN** 登录响应同时包含 `session` 与其他上游 Cookie
- **THEN** 只有 `session` 被转换为 Lang API 会话，其他 Cookie 不返回浏览器

#### Scenario: 受保护调用反向映射
- **WHEN** 浏览器携带有效 Lang API 会话访问受保护接口
- **THEN** 上游只收到重建后的 `session` Cookie 与所需认证 Header，不收到 Lang API Cookie 名或其他浏览器 Cookie

#### Scenario: 清除属性一致
- **WHEN** 会话过期或用户退出
- **THEN** 清除 Cookie 使用与创建时一致的名称、Path、SameSite 和 Secure 属性并设置立即过期

### Requirement: 认证错误安全翻译
适配器 SHALL 识别 New API 认证操作的 HTTP 200 `success=false`、非 2xx、缺失会话 Cookie和非法用户 DTO。登录时未知用户、错误密码和禁用用户 MUST 归一为同一内部凭据错误；注册重复、会话失效及上游故障 SHALL 按操作语义分类，原始消息不得离开适配边界。

#### Scenario: 登录业务失败
- **WHEN** New API 以 HTTP 200 和 `success=false` 拒绝登录
- **THEN** 适配器产生不可枚举的凭据错误，不保留可对外显示的原始 message

#### Scenario: 登录成功但缺 Cookie
- **WHEN** 上游宣称登录成功却没有返回合法 `session` Cookie
- **THEN** 适配器将响应视为 `UPSTREAM_ERROR`，不建立部分会话

#### Scenario: 当前用户返回未认证
- **WHEN** 上游当前用户操作返回 401
- **THEN** 适配器将其分类为会话失效，使 Portal 能清除本地 Cookie

### Requirement: 冻结版 Token 管理操作适配
New API 适配器 SHALL 为 v0.13.2 提供当前用户 Token 分页列表、名称搜索、单项读取、创建、完整更新、专用状态更新、删除和明文取回的语义化操作。所有操作 MUST 同时使用已验证的上游 `session` Cookie 与 `New-Api-User` Header，且资源 id 和用户 id 必须由服务端安全上下文确定或校验；适配器 MUST NOT 暴露批量操作、Token 值搜索或允许调用方选择上游路径。

#### Scenario: 分页列表与名称搜索
- **WHEN** 业务层请求当前用户 Token 列表或名称搜索
- **THEN** 适配器分别使用冻结版已声明的列表或 `/api/token/search` 操作，传入受控分页和转义后的名称条件，并只返回掩码字段

#### Scenario: 创建 Token
- **WHEN** 业务层提交已验证的 Lang API 创建命令
- **THEN** 适配器将数组和时间/额度语义转换为 v0.13.2 字段并最多发送一次 POST，且不假设成功响应包含 id 或明文 Key

#### Scenario: 完整更新前合并
- **WHEN** 业务层编辑一个 Token 的部分公开字段
- **THEN** 适配器先按当前用户读取该 Token，再构造 New API 所需完整对象执行一次 PUT，不允许客户端覆盖 owner、key、used_quota 或内部字段

#### Scenario: 专用状态更新
- **WHEN** 业务层启用或停用 Token
- **THEN** 适配器使用 `status_only=true` 的冻结版更新语义，只提交经映射的 id 与目标状态

#### Scenario: 明文取回
- **WHEN** 业务层请求显示当前用户拥有的 Token
- **THEN** 适配器调用 `POST /api/token/{id}/key`，只提取 key 字段并交给敏感值专用结果，不把上游包装或其他字段带出边界

### Requirement: Token 状态、限制与错误安全转换
适配器 SHALL 将冻结版数字状态转换为 Lang API 的 `enabled`、`disabled`、`expired` 或 `exhausted`，将 `-1` 过期时间与 quota/model/IP 字符串转换为稳定领域值。不存在与跨用户资源 SHALL 统一分类为 `NOT_FOUND`；达到数量上限、无效额度、过期或额度耗尽导致的启用失败 SHALL 转换为稳定业务错误；未知状态、非法掩码或缺失字段 SHALL 视为 `UPSTREAM_ERROR`。原始消息和完整 Token 值不得进入异常。

#### Scenario: 未知上游状态
- **WHEN** New API 返回适配器未声明的 Token 状态值
- **THEN** 操作安全失败为 `UPSTREAM_ERROR`，不把未知数字或原始正文直接返回前端

#### Scenario: 跨用户资源
- **WHEN** New API 对当前会话和资源 id 返回不存在
- **THEN** 适配器产生统一 `NOT_FOUND`，不尝试使用管理员接口确认真实所有者

#### Scenario: 写请求超时
- **WHEN** 创建、更新、状态更新或删除在发送期间超时或断连
- **THEN** 适配器返回结果未确认的稳定上游错误且不重试，不在异常或日志中保留请求正文

### Requirement: 冻结版个人日志与用量操作适配
New API 适配器 SHALL 为 v0.13.2 提供个人日志分页、个人日志统计、个人小时用量和当前余额四个语义化只读操作，分别固定调用 `GET /api/log/self`、`GET /api/log/self/stat`、`GET /api/data/self` 与 `GET /api/user/self`。所有操作 MUST 使用服务端会话中的上游 `session` Cookie 和 `New-Api-User` Header；调用方不得提供 userId、username、channel、group、上游路径或任意 URL。

#### Scenario: 调用个人日志操作
- **WHEN** 业务层以当前认证上下文查询请求日志
- **THEN** 适配器只调用 `/api/log/self` 并同时发送匹配的会话 Cookie 与用户 Header

#### Scenario: 调用摘要和趋势操作
- **WHEN** 业务层查询同一时间范围的用量摘要与小时趋势
- **THEN** 适配器分别调用 `/api/log/self/stat` 与 `/api/data/self`，不改用管理员或数据库接口

#### Scenario: 查询当前余额
- **WHEN** 业务层请求账户余额
- **THEN** 适配器调用现有当前用户操作或等价的最小只读操作，并只把 quota 交给账户边界

#### Scenario: 调用方提交越权参数
- **WHEN** Portal 请求包含 userId、username、channel、group 或上游路径参数
- **THEN** 参数在进入适配器前被拒绝，适配器始终使用认证上下文中的用户身份

### Requirement: 日志和用量查询参数安全转换
适配器 SHALL 把 Portal 页码、页大小、结果类型与时间范围转换为冻结版参数，并只允许日志 `type=2` 或 `type=5`。Key 名称使用上游精确 `token_name`；模型包含搜索 MUST 转换为经过转义且长度有界的 `model_name` 模式。Portal 左闭右开秒级范围 MUST 转换为上游 `start_timestamp` 左端包含与 `end_timestamp` 右端包含，且不得转发浏览器提供的未知查询参数。

#### Scenario: 转换右端不包含时间
- **WHEN** Portal 查询 `[10:00:00Z,11:00:00Z)`
- **THEN** 适配器发送开始秒 `10:00:00` 和结束秒 `10:59:59`，不会包含 `11:00:00` 的记录

#### Scenario: 转换模型普通文本搜索
- **WHEN** 模型搜索包含 SQL LIKE 通配或转义字符
- **THEN** 上游收到的模式只表达对原文本的包含匹配，不改变其他筛选条件

#### Scenario: 结果类型不受支持
- **WHEN** 业务层传入消费和错误之外的日志类型
- **THEN** 适配器拒绝调用，不以 `type=0` 查询全部日志

### Requirement: 日志与统计响应最小裁剪
适配器 SHALL 只解析个人日志的时间、类型、Key 名称、模型、quota、输入/输出 Token、耗时、流式标识和 requestId；统计只解析 quota、rpm、tpm；小时用量只解析桶时间、请求数、Token 与 quota；余额只解析 quota。未知字段 MUST 被忽略，`content`、`other`、IP、用户、Token、渠道、分组和上游消息 MUST NOT 离开适配边界。

#### Scenario: 用户日志包含内部字段
- **WHEN** `/api/log/self` 返回 channel、group、IP、content、other、userId 或 tokenId
- **THEN** 适配结果不包含这些字段，业务和前端无法访问它们

#### Scenario: 上游新增未知统计字段
- **WHEN** 任一响应新增缓存 Token、协议、节点或其他未知字段
- **THEN** 已声明数据继续转换，新增字段不会出现在 Portal API

#### Scenario: 上游返回部分非法页
- **WHEN** 分页 items 中任一已声明字段违反数值、时间或类型约束
- **THEN** 整页按 `UPSTREAM_ERROR` 失败，不返回部分映射结果

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

### Requirement: 冻结版法律正文只读适配
New API 适配器 SHALL 为 v0.13.2 提供用户协议和隐私政策两个匿名只读操作，分别固定调用 `GET /api/user-agreement` 与 `GET /api/privacy-policy`。操作 MUST 不发送浏览器 Cookie、`Authorization`、`New-Api-User` 或任意透传 Header，只允许公共操作白名单中的 `Accept` 和统一 `X-Request-Id`。

#### Scenario: 查询用户协议
- **WHEN** 法律内容服务请求用户协议
- **THEN** 适配器只调用 `GET /api/user-agreement` 并提取成功包装中的字符串 `data`

#### Scenario: 查询隐私政策
- **WHEN** 法律内容服务请求隐私政策
- **THEN** 适配器只调用 `GET /api/privacy-policy` 并提取成功包装中的字符串 `data`

#### Scenario: 浏览器携带认证信息
- **WHEN** 匿名法律请求携带 Cookie、Authorization 或代理 Header
- **THEN** 上游法律操作不收到这些值，且调用行为与匿名请求一致

### Requirement: 法律正文响应最小转换
法律正文适配 SHALL 只接受成功包装中的字符串 `data`，忽略未知字段，并把业务失败、非 JSON、非字符串或缺失字段转换为稳定上游错误。适配器不得解释、净化、缓存或记录正文；原始上游 `message`、品牌、Header 和响应包装不得离开适配边界。

#### Scenario: 响应包含未知字段
- **WHEN** 上游成功响应在字符串正文之外增加版本、站点名或其他字段
- **THEN** 适配器仅返回正文字符串，新增字段不会进入业务层

#### Scenario: 上游返回非法正文类型
- **WHEN** `data` 缺失、为对象或其他非字符串类型
- **THEN** 适配器返回安全 `UPSTREAM_ERROR`，不把原始响应交给调用方

#### Scenario: 上游业务失败
- **WHEN** 上游以 HTTP 200 和 `success=false` 返回消息
- **THEN** 适配器转换为稳定错误且不透传原始消息
