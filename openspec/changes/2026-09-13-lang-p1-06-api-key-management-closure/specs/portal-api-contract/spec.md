## ADDED Requirements

### Requirement: API Key 资源接口契约
Portal API SHALL 只通过受保护的 `/portal/api/api-keys` 资源路径提供单用户 Key 管理：`GET /portal/api/api-keys`、`POST /portal/api/api-keys`、`GET /portal/api/api-keys/{id}`、`PUT /portal/api/api-keys/{id}`、`PUT /portal/api/api-keys/{id}/status`、`DELETE /portal/api/api-keys/{id}` 与 `POST /portal/api/api-keys/{id}/reveal`。列表 SHALL 使用统一分页包装，并只接受声明的 `page`、`pageSize`、`name` 和 `status` 查询参数；未知参数、非法正整数 id、过长搜索词或未声明方法 MUST 在访问上游前失败。

#### Scenario: 合法列表请求
- **WHEN** 已登录用户提供合法分页、名称和状态条件
- **THEN** 系统返回统一分页包装，且字段名、枚举和时间不依赖 New API DTO

#### Scenario: 非法查询参数
- **WHEN** 请求包含 page=0、超上限 pageSize、非法状态或未声明查询参数
- **THEN** 系统返回 `INVALID_ARGUMENT`，不调用 New API

#### Scenario: 错误方法或未知子路径
- **WHEN** 客户端对 API Key 资源使用未声明方法或访问未知子路径
- **THEN** 系统分别返回统一 `METHOD_NOT_ALLOWED` 或 `NOT_FOUND`，不进行透明上游转发

### Requirement: API Key 稳定错误语义
API Key 接口 SHALL 使用现有通用错误，并增加 `RESOURCE_CONFLICT`/409、`API_KEY_LIMIT_REACHED`/409 和 `OPERATION_RESULT_UNKNOWN`/502。`RESOURCE_CONFLICT` 表示当前状态不允许目标操作；`API_KEY_LIMIT_REACHED` 表示上游用户 Key 数量上限；`OPERATION_RESULT_UNKNOWN` 表示不可重试写操作发送后无法确认结果。不存在与跨用户资源统一使用 `NOT_FOUND`；任何错误 MUST NOT 包含完整 Key、上游消息、私网地址或内部字段。

#### Scenario: 过期 Key 直接启用
- **WHEN** Key 仍过期且用户请求启用
- **THEN** 系统返回 HTTP 409 和 `RESOURCE_CONFLICT`，消息只说明需先调整公开限制字段

#### Scenario: 达到数量上限
- **WHEN** New API 拒绝创建，因为当前用户达到 Key 数量上限
- **THEN** 系统返回 HTTP 409 和 `API_KEY_LIMIT_REACHED`，不透传上游配置或原始消息

#### Scenario: 删除结果无法确认
- **WHEN** 删除请求已发送但上游响应在完成前中断
- **THEN** 系统返回 HTTP 502 和 `OPERATION_RESULT_UNKNOWN`，提示用户刷新核对且不自动重试

