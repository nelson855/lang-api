## ADDED Requirements

### Requirement: 公开协议地址与网关能力一致
部署配置 SHALL 只在对应模型协议的完整公开路径已由 edge 精确开放且通过健康/契约检查后，才允许 `/portal/api/public-config` 发布该协议 Base URL。LANG-P1-07 首批只允许发布 `OPENAI`，URL MUST 是不含用户信息、查询或片段并以 `/v1` 结束的绝对 HTTP(S) 地址；本地开发使用 `api.localhost`，生产配置不得把用户站点、New API 私网地址或运维端口作为公开模型地址。

#### Scenario: OpenAI 网关就绪
- **WHEN** 部署已开放两条声明的 OpenAI 路径且配置了合法模型 API Base URL
- **THEN** public-config 返回一个 `{protocol:"OPENAI",url:".../v1"}` 条目，前端无需知道 New API 地址

#### Scenario: 协议配置与网关不一致
- **WHEN** 配置尝试发布未开放的 ANTHROPIC/GEMINI，或 OPENAI URL 指向用户站点、私网地址、运维端口或不以 `/v1` 结束
- **THEN** 部署预检或应用启动失败，不向用户发布该地址

#### Scenario: 网关尚未具备真实调用条件
- **WHEN** OpenAI 路径尚未通过网关检查或环境未准备好对外服务
- **THEN** `OPENAI` 不进入 enabled protocols，public-config 保持空地址而不是宣传不可用能力

