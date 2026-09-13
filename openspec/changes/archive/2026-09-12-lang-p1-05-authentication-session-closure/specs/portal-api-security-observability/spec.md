## ADDED Requirements

### Requirement: 认证接口与会话访问边界
`GET /portal/api/auth/options`、认证 CSRF 引导端点、登录和按策略开放的注册 SHALL 允许匿名访问；refresh、logout 与 `GET /portal/api/profile` SHALL 只接受有效 Portal 会话，其中无会话的 logout 仍按幂等规则成功。所有其他受保护 Portal API SHALL 使用同一会话校验结果，且不得接受客户端自行提交的角色或 `New-Api-User` Header 作为身份依据。

#### Scenario: 匿名读取认证选项
- **WHEN** 未登录客户端查询认证选项或获取 CSRF 凭证
- **THEN** 请求被允许且不创建认证会话

#### Scenario: 伪造上游用户头
- **WHEN** 客户端携带自定义 `New-Api-User` Header 访问受保护接口
- **THEN** 系统丢弃该 Header，只依据受保护 Cookie 与上游校验确定身份

#### Scenario: 受保护接口复用身份
- **WHEN** 一个有效会话访问 profile 或后续受保护 Portal API
- **THEN** 安全上下文只包含经过上游确认的普通用户身份，不包含管理权限

### Requirement: 认证写操作 CSRF 与来源防护
系统 SHALL 对 register、login、refresh 和 logout 强制执行 CSRF 防护。客户端 SHALL 先通过匿名安全端点取得可由脚本读取的短期 CSRF Cookie/Token，并在写请求 Header 中回传；生产环境还 MUST 校验 `Origin` 是否属于显式允许的同源来源。CSRF Cookie MUST NOT 承载身份信息，且其 Path、SameSite 和 Secure SHALL 与部署环境一致。

#### Scenario: 合法同源登录
- **WHEN** 同源页面提交匹配的 CSRF Cookie 与 Header 并携带允许的 Origin
- **THEN** 登录请求进入凭据校验

#### Scenario: 缺失 CSRF Token
- **WHEN** 外部页面或脚本直接提交认证写请求且没有匹配 Token
- **THEN** 系统在调用 New API 前返回 `CSRF_REJECTED`

#### Scenario: 生产来源不在白名单
- **WHEN** `prod` 写请求的 Origin 不在显式允许列表
- **THEN** 系统返回 `CSRF_REJECTED`，且不依据 Host 或代理 Header 自动放宽来源

### Requirement: 登录与注册基础频率限制
系统 SHALL 分别限制登录与注册频率，并同时考虑可信客户端地址和规范化用户名维度；超过限制时 SHALL 在请求上游前返回 `RATE_LIMITED`。客户端地址只有在直接连接来源属于配置的可信代理范围时才可从转发 Header 解析，否则 MUST 使用直接连接地址。限流键和日志 MUST NOT 保存明文用户名或完整客户端地址。

#### Scenario: 多次错误登录触发限制
- **WHEN** 同一客户端与用户名组合在窗口内超过登录尝试上限
- **THEN** 后续请求返回 `RATE_LIMITED` 和 Retry-After，且不请求 New API

#### Scenario: 注册频率触发限制
- **WHEN** 同一客户端在注册窗口内超过允许次数
- **THEN** 后续注册返回 `RATE_LIMITED`，即使公开注册策略仍开启

#### Scenario: 不信任任意转发头
- **WHEN** 非可信直接来源伪造 `X-Forwarded-For` 或 `X-Real-IP`
- **THEN** 限流使用直接连接地址，不使用伪造值

### Requirement: 认证安全事件日志
每次登录和注册失败、限流、会话失效及退出撤销失败 SHALL 产生可关联的结构化安全事件，至少包含 `requestId`、操作、结果类别和安全原因码。日志 MUST NOT 包含密码、Cookie、CSRF Token、明文用户名、完整邮箱、完整客户端地址、上游响应正文或能够恢复这些值的限流键。

#### Scenario: 错误密码日志
- **WHEN** 登录因凭据错误被拒绝
- **THEN** 日志记录不可枚举的 `invalid_credentials` 类别及 requestId，不记录用户名和密码

#### Scenario: 会话失效日志
- **WHEN** 上游拒绝现有会话
- **THEN** 日志记录 `session_invalid` 类别且不记录 Cookie 值或上游原始消息

#### Scenario: 上游退出未确认
- **WHEN** logout 已清除本地 Cookie 但上游撤销调用失败
- **THEN** 日志记录可关联的 `revocation_unconfirmed` 结果，响应仍遵守统一错误契约
