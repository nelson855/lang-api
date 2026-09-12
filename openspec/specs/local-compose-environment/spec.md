# local-compose-environment Specification

## Purpose

提供可通过单一 Docker Compose 入口重复启动的 Lang API 本地完整依赖环境，并以默认安全的网络、密钥、健康检查和持久化约束支撑后续内部接口开发。

## Requirements

### Requirement: 五服务环境可重复启动
本地环境 SHALL 通过一条文档化的 Docker Compose 命令构建或启动 `edge-nginx`、`lang-api`、`new-api`、`postgres` 和 `redis` 五个服务。外部基础镜像 MUST 使用不可变 digest，服务名称和内部 DNS 名称 MUST 保持稳定。

#### Scenario: 从干净容器环境首次启动
- **WHEN** 开发人员准备有效的本地环境文件并执行文档化启动命令
- **THEN** 五个服务按健康依赖顺序启动，最终均达到健康或明确的待初始化状态

#### Scenario: 重复执行启动命令
- **WHEN** 环境已经创建后再次执行同一启动命令
- **THEN** Compose 收敛到相同服务拓扑，不重复初始化业务数据或创建冲突容器

#### Scenario: 外部镜像锁定检查
- **WHEN** 检查 Compose 中 New API、Nginx、PostgreSQL、Redis 和测试辅助镜像
- **THEN** 每个外部镜像均包含不可变 digest，且不存在 `latest` 等浮动引用

### Requirement: 默认网络暴露遵循最小权限
默认 Compose 拓扑 SHALL 只向宿主机发布 `edge-nginx` 的本地访问端口。`lang-api`、`new-api`、`postgres` 和 `redis` MUST NOT 在默认配置中发布宿主机端口；PostgreSQL 与 Redis MUST 仅加入内部数据网络。服务间 SHALL 使用独立的 Web、Portal 控制面、Relay 和数据网络限制可达关系。

#### Scenario: 默认端口检查
- **WHEN** 使用主 Compose 文件启动环境并检查宿主机监听与容器端口发布
- **THEN** 只有 `edge-nginx` 存在宿主机端口映射，New API、PostgreSQL 和 Redis 不能通过宿主机地址直接连接

#### Scenario: Portal API 内部访问 New API
- **WHEN** 从 `lang-api` 容器请求 New API 内部健康或状态地址
- **THEN** 请求可通过 Portal 控制面网络到达 New API，且不需要宿主机端口

#### Scenario: 数据服务隔离
- **WHEN** 从未加入数据网络的 `edge-nginx` 或 `lang-api` 容器尝试直接解析或连接 PostgreSQL、Redis
- **THEN** 连接失败；New API 则能通过数据网络访问两项依赖

### Requirement: New API 管理入口显式受控
New API 默认前端、初始化页面和管理 API MUST NOT 通过 `edge-nginx` 或默认宿主机端口公开。项目 SHALL 提供单独的运维访问覆盖配置，仅在显式启用时把 New API 管理端口绑定到 `127.0.0.1`，并记录使用完成后撤销该入口的方式。

#### Scenario: 普通用户入口访问管理路径
- **WHEN** 客户端通过 `edge-nginx` 请求 New API 默认页面、`/setup/*` 或任意未声明管理 API
- **THEN** 请求被拒绝或返回 Lang API 自有 404，且不会到达 New API 管理服务

#### Scenario: 本机执行首次初始化
- **WHEN** 运维人员显式启用运维覆盖配置
- **THEN** New API 管理端只绑定本机回环地址，可用于初始化或管理，局域网其他主机不能直接访问

#### Scenario: 撤销运维入口
- **WHEN** 运维操作完成并使用主 Compose 配置重新收敛环境
- **THEN** New API 不再具有宿主机端口映射，已初始化数据仍保留

### Requirement: 公共 Relay 在本阶段保持关闭
`edge-nginx` 与 New API MAY 预先具备 Relay 专用内部网络，但在 LANG-P1-07 前，网关 MUST NOT 将 `/v1/*`、`/v1beta/*` 或其他模型协议路径转发给 New API。

#### Scenario: 请求模型协议路径
- **WHEN** 客户端通过本地 `edge-nginx` 请求 `/v1/chat/completions` 或其他模型路径
- **THEN** 网关返回关闭或未找到响应，且 New API 不收到该 Relay 请求

### Requirement: 健康检查控制启动依赖
PostgreSQL、Redis、New API、Lang API 和 `edge-nginx` SHALL 提供适合自身启动语义的健康检查。依赖服务 MUST 在上游依赖健康后再进入正常运行，单纯创建容器不得视为依赖已就绪。

#### Scenario: 数据库尚未就绪
- **WHEN** PostgreSQL 容器已创建但健康检查尚未通过
- **THEN** New API 不进入可服务状态，依赖失败在 Compose 状态中清晰可见

#### Scenario: 应用链路健康
- **WHEN** PostgreSQL、Redis、New API 和 Lang API 均健康
- **THEN** `edge-nginx` 健康，且通过唯一入口请求 Lang API 首页成功

#### Scenario: 依赖变为不健康
- **WHEN** 任一关键服务健康检查失败
- **THEN** Compose 状态和诊断命令能够定位具体不健康服务，不以其他服务进程存在掩盖失败

### Requirement: 必填密钥不进入版本库
项目 SHALL 提供只含变量名、用途说明和空占位值的环境变量模板；真实 `.env` 文件 MUST 被版本控制忽略。数据库密码、Redis 密码、New API `SESSION_SECRET` 和 `CRYPTO_SECRET` MUST 由使用者在本地生成，缺少必填值时启动 MUST 失败。首次管理员凭证 MUST 在受控初始化过程中单独输入，不得写入 Compose 环境模板。

#### Scenario: 复制模板并填入本地密钥
- **WHEN** 开发人员从模板创建被忽略的本地环境文件并填入有效随机值
- **THEN** Compose 能解析配置，且生成的渲染配置中服务获得所需变量

#### Scenario: 缺少必填密钥
- **WHEN** 未设置数据库、Redis 或 New API 必填密钥便执行启动命令
- **THEN** Compose 在创建服务前以明确错误失败

#### Scenario: 扫描版本库
- **WHEN** 扫描受版本控制文件中的本地凭证和已知示例值
- **THEN** 不存在真实密码、Cookie、Token、完整 API Key、供应商密钥或支付签名

### Requirement: New API 业务数据可持久化
New API 的 PostgreSQL 业务数据及运行所需持久化目录 SHALL 使用命名卷保存。普通停止、容器重建和不删除卷的 `down`/`up` 操作 MUST 保留初始化状态和抽样业务数据。销毁卷 MUST 是单独、显式且带警告的操作。

#### Scenario: 重建服务后数据保留
- **WHEN** 初始化 New API 并写入抽样数据后停止并重建应用容器但保留卷
- **THEN** 管理员初始化状态和抽样数据仍可查询，且不会重新进入首次设置流程

#### Scenario: 普通停止环境
- **WHEN** 执行文档化的普通停止命令
- **THEN** 容器停止或删除，但 PostgreSQL 命名卷和业务数据保留

#### Scenario: 请求销毁本地数据
- **WHEN** 开发人员需要彻底重置环境
- **THEN** 必须执行与普通停止命令分离的显式销毁命令，并在执行前看到数据不可恢复警告

### Requirement: 初始化与升级路径可重复执行
项目 SHALL 提供首次初始化、日常启动/停止、备份、基线升级、失败恢复和兼容性复验步骤。首次初始化 MUST 通过受控运维入口完成；升级 MUST 先生成可恢复备份，并验证迁移后数据和必需接口。

#### Scenario: 首次初始化成功
- **WHEN** 空数据库环境启动并由运维人员通过本机受控入口创建初始管理员
- **THEN** New API 退出待初始化状态，重启后保持已初始化且管理员可以登录

#### Scenario: 在数据副本上验证升级
- **WHEN** 评估新的 New API 镜像基线
- **THEN** 先备份当前数据库并在隔离副本执行迁移、启动和兼容性探测，不直接覆盖唯一可用数据

#### Scenario: 升级失败恢复
- **WHEN** 数据库迁移、启动或必需接口复验失败
- **THEN** 使用已记录的镜像和备份恢复当前基线，并验证抽样数据仍可读取
