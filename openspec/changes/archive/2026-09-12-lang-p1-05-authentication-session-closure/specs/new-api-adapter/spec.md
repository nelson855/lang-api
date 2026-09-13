## ADDED Requirements

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
