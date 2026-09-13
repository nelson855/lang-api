## ADDED Requirements

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

