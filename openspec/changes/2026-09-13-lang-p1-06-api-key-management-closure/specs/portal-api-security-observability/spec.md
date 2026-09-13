## ADDED Requirements

### Requirement: API Key 会话、CSRF 与来源边界
所有 API Key 接口 SHALL 只接受有效 Portal 会话，并复用同一请求中已由 New API 校验的普通用户身份。创建、编辑、启停、删除和 reveal MUST 通过现有双提交 CSRF 防护及生产精确 Origin 校验；缺失或失配时必须在任何 Token 上游调用前返回 `CSRF_REJECTED`。客户端提交的用户 id、上游身份 Header、owner、role 或 Key 值 MUST 被拒绝或忽略，不能用于授权。

#### Scenario: 匿名读取列表
- **WHEN** 未登录客户端请求 API Key 列表
- **THEN** 系统返回统一 `UNAUTHENTICATED`，不调用 New API Token 路径

#### Scenario: 伪造 owner 或上游 Header
- **WHEN** 已登录用户在请求中提交其他用户 id、owner 或 `New-Api-User`
- **THEN** 系统只使用当前已校验会话身份，且不能读取或修改其他用户的 Key

#### Scenario: reveal 缺少 CSRF
- **WHEN** 会话有效但 reveal 请求缺少匹配 CSRF Cookie/Header 或生产 Origin 不允许
- **THEN** 系统返回 `CSRF_REJECTED`，不向 New API 请求明文

### Requirement: API Key 明文最小暴露
reveal 成功响应 SHALL 设置 `Cache-Control: no-store`，并不得携带可共享缓存的验证器或通过 URL 返回 secret。服务端 MUST NOT 缓存明文，也 MUST NOT 将创建/编辑请求正文、reveal 响应、`Authorization`、掩码前后片段组合或可恢复 Key 的派生值写入访问日志、应用日志、异常、指标标签、追踪属性或安全事件。

#### Scenario: 捕获 reveal 请求日志
- **WHEN** 测试捕获一次成功或失败的 reveal 全部日志与追踪字段
- **THEN** 记录只包含规范化路由、资源 id、requestId、结果类别和耗时，不包含 secret、掩码片段或上游正文

#### Scenario: 上游异常包含 Key
- **WHEN** 上游或底层异常文本意外包含完整 Key
- **THEN** 对外响应与所有结构化日志只保留固定脱敏占位符和安全错误类别

### Requirement: API Key 安全事件
创建、编辑、启停、删除和 reveal SHALL 产生可关联的完成态安全事件，至少包含 `requestId`、操作、当前用户的不可逆主体标识、资源 id、结果类别和安全原因码。事件 MUST NOT 包含 Key 名称、完整或掩码 Key、模型/IP 限制正文、额度数值、Cookie、CSRF Token、上游消息或客户端提交的用户标识。

#### Scenario: 成功 reveal 事件
- **WHEN** 当前用户成功显示一个 Key
- **THEN** 系统记录 `api_key_reveal` 成功类别及必要标识，但无法从事件恢复 secret 或业务限制

#### Scenario: 跨用户尝试
- **WHEN** 用户请求不存在或不属于自己的 Key
- **THEN** 系统记录统一 `resource_not_found` 结果，不记录推测的所有者或对象内容

