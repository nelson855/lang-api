## Why

LANG-P1-03 及后续业务适配必须依赖一个真实运行、版本不可漂移且接口行为经过验证的 New API，而当前仓库只有源码调研提交，没有可复现的部署基线。LANG-P1-02 需要同时冻结上游版本与接口证据，并建立五服务本地环境，避免后续根据文档猜测接口或直接依赖浮动镜像开发。

## What Changes

- 采用“稳定版优先、实测后冻结”的选择流程：优先验证最后一个满足条件的非预发布 Release；仅在缺少第一阶段必需能力时评估预发布版本，并明确记录风险与接受依据。
- 记录最终 New API Release、对应完整 Git commit、容器镜像仓库、不可变 manifest digest、实际平台 digest、核验日期和上游许可证/NOTICE 义务。
- 对第一阶段需要的初始化、认证、当前用户、API Key、模型与价格、日志与用量、余额与充值、个人设置、法律内容及 Relay 路径逐项实测。
- 建立内部接口兼容性记录，保存脱敏请求/响应样例，并将每项标记为已验证、存在差异、条件可用或不可用。
- 新增 `edge-nginx`、`lang-api`、`new-api`、`postgres`、`redis` 五服务 Docker Compose 本地环境；外部镜像全部使用不可变 digest。
- 划分 Web、Portal 控制面、Relay 和数据网络，默认只发布 `edge-nginx` 端口；New API 管理后台通过显式启用的本机回环运维覆盖配置访问。
- 为 PostgreSQL、Redis、New API 和 Lang API 配置持久化、健康检查及基于健康状态的启动依赖。
- 提供不含真实密钥的环境变量模板，并使缺少必填密码、Session/Crypto 密钥时启动立即失败。
- 编写 New API 首次初始化、停止/重启、数据持久化、备份后升级、失败回退和兼容性复验流程。
- 本变更不修改 New API 源码，不实现 Portal API 上游客户端，不开放公共模型 Relay 路径，也不建立生产部署和高可用能力。

## Capabilities

### New Capabilities

- `new-api-baseline`: 定义 New API 版本冻结、供应链标识、许可证记录、第一阶段内部接口实测和升级兼容性证据。
- `local-compose-environment`: 定义五服务本地 Compose 环境、网络暴露、健康依赖、密钥模板、初始化与持久化行为。

### Modified Capabilities

无。

## Impact

- 新增 `deploy/` 下的 Compose 主文件、运维访问覆盖文件、环境变量模板和版本锁定记录。
- 新增 `gateway/` 下的最小 Nginx 配置；公共模型路径在 LANG-P1-07 前保持关闭。
- 扩展 `integration-tests/`，加入 Compose 冒烟、网络隔离、持久化和 New API 接口探测工具。
- 新增 New API 基线、兼容性矩阵、初始化与升级运行手册等中文文档。
- 使用现有 `lang-api` Dockerfile，不改变 P1-01 的 JAR、路由或后端 `.properties` 配置契约。
- 引入并固定 New API、Nginx、PostgreSQL、Redis 及测试辅助镜像；仓库不保存真实凭证。
