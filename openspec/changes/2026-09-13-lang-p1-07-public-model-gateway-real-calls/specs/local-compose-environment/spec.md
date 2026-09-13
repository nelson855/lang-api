## REMOVED Requirements

### Requirement: 公共 Relay 在本阶段保持关闭
**Reason**: LANG-P1-07 正式交付首批公共模型数据面，原先“所有 Relay 路径保持关闭”的阶段门禁已完成使命。

**Migration**: 用新的“公共 Relay 只精确开放已验证路径”要求替代；继续拒绝所有管理路径、未验证协议与未声明模型路径。

## ADDED Requirements

### Requirement: 公共 Relay 只精确开放已验证路径
默认 Compose 拓扑 SHALL 允许 `edge-nginx` 仅通过现有内部 `relay` 网络访问 New API Relay，并只在模型 API 虚拟主机上开放 `POST /v1/chat/completions` 与 `GET /v1/models`。New API、Portal API、PostgreSQL 和 Redis 的端口暴露与网络隔离 MUST 保持不变；用户站点虚拟主机不得把模型路径转发到 Relay。

#### Scenario: 从模型 API 主机调用白名单路径
- **WHEN** 客户端通过本地模型测试域名请求一个声明的 OpenAI 路径
- **THEN** edge 经 relay 网络到达 New API，且 New API 仍没有宿主机端口映射

#### Scenario: 从用户站点请求模型路径
- **WHEN** 浏览器或客户端通过用户站点主机请求 `/v1/chat/completions`
- **THEN** 网关返回自有 404，且请求不进入 New API Relay

#### Scenario: 从模型 API 主机请求管理面
- **WHEN** 客户端通过模型 API 主机请求 `/api/status`、`/setup/*`、Portal API 或默认页面
- **THEN** 网关返回自有 404，New API 管理面和 Lang API 页面均不被代理

#### Scenario: 检查默认端口与网络
- **WHEN** 使用主 Compose 文件启动环境并检查服务网络与已发布端口
- **THEN** 仍只有 edge-nginx 发布宿主机端口，edge 只能通过 relay 网络访问 New API 且不能访问 PostgreSQL/Redis

