## ADDED Requirements

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

