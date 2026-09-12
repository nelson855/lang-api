## Context

LANG-P1-01 已归档，仓库已有可构建的 `lang-api` JAR/镜像、`dev`/`test`/`prod` `.properties` 配置和健康端点，但尚无 `deploy/`、`gateway/` 或可运行的 New API 环境。行为契约见 `specs/new-api-baseline/spec.md` 与 `specs/local-compose-environment/spec.md`。

现有文档引用的 New API 提交 `bdef117505247769268b209665fb3ad7554c3da7` 是功能调研快照，不是已验证的部署版本。2026-09-12 核对官方资料时，New API 官方 Compose 仍示例使用 `calciumion/new-api:latest` 并直接发布 3000 端口；这不符合本项目的不可变版本和私网边界。官方环境变量文档确认 PostgreSQL、Redis、`SESSION_SECRET` 和 `CRYPTO_SECRET` 分别通过 `SQL_DSN`、`REDIS_CONN_STRING`、`SESSION_SECRET`、`CRYPTO_SECRET` 配置。

上游发布历史显示 `v0.13.2` 是进入 v1 alpha/RC 之前最后一个非预发布 Release；当前 v1 RC 发布说明仍标注实验性插件系统及“不建议生产使用”。因此设计不能简单选择最新版本，需要先验证稳定候选能否覆盖本项目第一阶段接口，再决定是否升级候选。许可证也必须读取最终 tag/commit 自身的 `LICENSE`、`NOTICE` 和第三方声明，不能把 `main` 分支条款直接当作历史版本结论。

本次判断依据均来自上游官方仓库：[Releases](https://github.com/QuantumNous/new-api/releases)、[v0.13.2](https://github.com/QuantumNous/new-api/releases/tag/v0.13.2)、[官方 Compose](https://github.com/QuantumNous/new-api/blob/main/docker-compose.yml)、[环境变量说明](https://github.com/QuantumNous/new-api-docs/blob/main/docs/en/installation/environment-variables.md)、[LICENSE](https://github.com/QuantumNous/new-api/blob/main/LICENSE) 与 [NOTICE](https://github.com/QuantumNous/new-api/blob/main/NOTICE)。这些链接只用于选择候选；实施时仍需读取最终冻结 tag/commit 下的对应文件。

## Goals / Non-Goals

**Goals:**

- 让任何开发人员用一条 Compose 命令得到同一组五服务和相同外部镜像内容。
- 在开始 Portal API 适配前，形成可审计的 New API 版本、许可证和接口行为事实库。
- 默认关闭直接管理访问和模型 Relay，只开放 Lang API 页面与现有 Portal API 边界。
- 证明 New API 可通过内部网络连接 PostgreSQL/Redis，Lang API 可通过内部网络连接 New API，且数据在普通重建后保留。
- 将首次初始化、日常启停、升级和失败恢复写成可重复运行的操作路径。

**Non-Goals:**

- 不修改、fork 或重新构建 New API 源码。
- 不在 Portal API 中实现 New API 客户端、DTO、统一错误或认证适配。
- 不开放任何公共模型协议路径；Relay 白名单、流式和限流属于 LANG-P1-07。
- 不把本地 Compose 直接声明为生产部署；备份自动化、高可用和公网加固属于 LANG-P1-12。
- 不承诺未具备支付渠道、邮件服务或真实模型供应商账号时可以完成外部系统端到端验证。
- 不修改 P1-01 建立的后端 `.properties` 文件；后续新增后端配置仍必须分别落在 `application.properties` 和 `application-{dev,test,prod}.properties`。

## Decisions

### 1. 使用两级候选门禁，而不是直接锁定最新镜像

第一级默认候选是 `v0.13.2`：它是 2026-09-12 可确认的最后一个非预发布 Release。实施时先解析该 tag 的完整 commit，拉取官方镜像，并记录 registry 返回的 OCI manifest digest 与本机架构对应的 image digest。`bdef117...` 只用于发现潜在接口差异，不作为候选身份。

只有当 `v0.13.2` 缺少认证、Key、价格、日志、余额、法律内容或计划支持的协议等第一阶段不可替代能力时，才进入第二级候选：选择当时最新、已修复已知数据库迁移问题的 v1 RC，重复完整验证，并把上游“不建议生产”提示作为显式风险。选择预发布版本会在实施时停下来要求用户接受风险；不会由任务执行者自行决定。

没有采用 `latest` 或直接跟随 `main`，因为二者无法将接口和数据库状态关联到不可变制品。也没有现在就永久冻结 `v0.13.2`，因为现有功能调研来自较新的提交，必须用实测确认稳定版本是否满足后续需求。

### 2. 基线记录与兼容性证据分层保存

计划新增：

```text
docs/new-api/
├── 基线版本与许可证.md
├── 第一阶段接口兼容性矩阵.md
├── 初始化与升级手册.md
└── samples/
    ├── setup/
    ├── auth/
    ├── token/
    ├── pricing/
    ├── usage/
    ├── wallet/
    ├── profile/
    ├── legal/
    └── relay/
```

“基线版本与许可证”是制品身份的唯一说明，包含 Release、commit、tag、manifest digest、平台 digest、来源、核验命令、许可证/NOTICE、是否修改和已知迁移提示。Compose 中仍直接写不可变镜像引用，避免运行时还需拼接另一个版本文件；文档中的值必须与 Compose 自动比对。

兼容性矩阵按业务能力而不是按源码 Controller 排列。每项记录方法、路径、认证、请求关键字段、Cookie/Header、成功/失败状态、响应关键字段、前置开关、结论及样例链接。样例只保留结构，使用统一占位符替换凭证和个人信息。无法完成外部依赖验证时明确标为“条件可用”，不伪造成功结果。

没有把原始流量抓包整体提交，因为它极易包含 Cookie、Token 和密钥；也没有只写一张路径清单，因为后续适配真正依赖的是认证状态、字段与错误行为。

### 3. Compose 主文件与运维覆盖文件分离

文件布局为：

```text
deploy/
├── compose.yml
├── compose.ops.yml
└── .env.example
gateway/
└── nginx.conf
integration-tests/
└── new-api/
    ├── README.md
    ├── smoke.sh
    ├── probe.sh
    └── sanitize.sh
```

主入口使用：

```text
docker compose --env-file deploy/.env -f deploy/compose.yml up -d --build
```

`lang-api` 从仓库根 Dockerfile 构建；New API、Nginx、PostgreSQL 和 Redis 使用写入 Compose 的 digest 引用。Compose 不设置 `container_name`，以保留项目隔离和并行环境能力。所有服务采用稳定的 service name 作为内部 DNS 名。

`compose.ops.yml` 只为 New API 增加 `127.0.0.1:${NEW_API_ADMIN_PORT}:3000`，用于初始化和受控运维。正常环境不加载该文件。完成操作后使用主文件强制重新收敛 New API 容器，测试确认端口映射消失；不会把管理路径添加到 Nginx。

相比永久把 New API 绑定 `127.0.0.1`，覆盖文件能让“是否打开运维入口”成为显式状态；相比 SSH/VPN，本阶段本机开发无需额外基础设施，生产访问方式留给 LANG-P1-12。

### 4. 使用五张职责明确的网络

```text
宿主机
  └── edge-nginx
        ├── web ── lang-api
        └── relay ── new-api       # 仅网络预留，路由关闭

lang-api ── control ── new-api
new-api ── data ── postgres / redis
new-api ── egress ── 外部供应商     # 仅 New API 具备出站网络
```

- `web`：`edge-nginx` 与 `lang-api`；承载页面和 `/portal/api/*`。
- `control`：内部网络，仅 `lang-api` 与 `new-api`；供后续 Portal 适配。
- `relay`：内部网络，仅 `edge-nginx` 与 `new-api`；P1-07 前没有转发 location。
- `data`：内部网络，仅 `new-api`、`postgres`、`redis`。
- `egress`：仅 `new-api` 加入的普通 bridge 网络，为后续供应商请求保留出站能力。

PostgreSQL 和 Redis 不加入任何具有宿主机出口的网络。这样网络成员关系本身表达边界，不依赖应用“自觉不访问”。没有让所有服务共享默认网络，因为那会让 edge 和 lang-api 直接解析数据服务，也会使后续安全验收失去清晰边界。

### 5. Nginx 在 P1-02 只承担入口与拒绝策略

`gateway/nginx.conf` 将 `/` 和 `/portal/api/*` 转发到 `lang-api:8080`，保留必要的 Host 与受控代理头，并提供独立 `/healthz`。对 `/v1/*`、`/v1beta/*`、`/api/*`、`/setup/*` 和 New API 默认静态/管理路径显式返回自有 404；不配置指向 New API 的通配代理。

这既验证 P1-01 镜像可以进入目标拓扑，又避免为了“提前准备”而公开未经 P1-07 验证的模型路径。管理初始化只走回环运维覆盖，不经过面向普通用户的 edge。

### 6. 健康检查表达真实依赖就绪

- PostgreSQL：使用 `pg_isready` 检查目标数据库和用户。
- Redis：使用带本地密码环境变量的 `redis-cli ping`，不把密码写入命令行常量。
- New API：请求冻结版本实测确认的状态端点，并区分进程健康与“尚待首次初始化”。
- Lang API：请求 `/actuator/health`。
- edge-nginx：请求自身 `/healthz` 并在启动依赖中等待 Lang API 健康。

New API 使用 `depends_on.condition: service_healthy` 等待 PostgreSQL 与 Redis；其他依赖同理。健康命令、间隔、超时和重试次数保持短小，启动期使用 `start_period` 吸收迁移耗时。未采用固定 `sleep`，因为不同机器和首次迁移耗时不可预测。

### 7. 密钥模板为空值，并在 Compose 解析期失败

`deploy/.env.example` 只包含变量名、中文用途、生成建议和空值；开发人员复制为被 Git 忽略的 `deploy/.env`。Compose 对 PostgreSQL、Redis、`SESSION_SECRET` 和 `CRYPTO_SECRET` 使用 `${VAR:?message}`，空值时在创建容器前失败。首次管理员凭证不进入该模板，只在临时运维入口的初始化交互中输入并保存在仓库外。

数据库 DSN 和 Redis URL 在 Compose 内由这些变量组合，不要求用户重复填写包含密码的完整连接串。日志和诊断脚本不得输出 Compose 渲染后的完整 environment。P1-02 面向本地环境，密钥仍以容器环境变量注入；生产 secret 文件或外部密钥服务在 P1-12 设计。

现有后端配置文件继续保持 `.properties`：Compose 仅通过 `SPRING_PROFILES_ACTIVE=dev` 选择 `application-dev.properties`，本阶段不创建 YAML 配置，也不添加未被代码消费的 New API URL 属性。P1-03 引入上游客户端时，再将共享配置键及 dev/test/prod 差异补入四个 `.properties` 文件。

### 8. PostgreSQL 是业务数据权威，卷销毁与普通停止分离

命名卷至少覆盖 PostgreSQL 数据、New API 运行数据/日志；Redis 启用认证并使用命名卷保存 AOF，以减少本地重启后的缓存状态突变，但业务恢复仍以 PostgreSQL 为准。普通停止文档只使用不会删除卷的 `stop` 或 `down`；`down -v` 只出现在单独的“彻底重置”章节，并紧邻不可恢复警告。

持久化验收在初始化后创建一个无真实信息的抽样对象，记录其稳定标识，重建应用容器后再次查询。仅看到卷存在不算通过。

### 9. 初始化和升级遵循“备份—副本迁移—复验—切换”

首次启动允许 New API 处于待初始化状态；运维人员加载 `compose.ops.yml`，只从本机完成管理员初始化，然后撤销覆盖并验证默认入口不可达。管理员凭证只保存在本地密钥管理位置，不进入探测样例。

New API 自身负责其数据库 schema 初始化和迁移，本项目不编写或修改上游 SQL。评估升级时：

1. 记录当前镜像和数据库状态；
2. 使用 `pg_dump` 生成受保护的本地备份；
3. 将备份恢复到隔离的数据库卷/Compose 项目；
4. 在副本上启动候选镜像，让上游迁移执行；
5. 重跑兼容性探测和抽样数据核对；
6. 通过后才更新冻结记录和主 Compose digest。

失败时恢复“旧镜像 + 旧数据库备份”，不让旧镜像直接连接已经被新版本迁移的唯一数据库。该方案比在原卷上试升后直接降镜像多占临时磁盘，但避免不可逆迁移导致的数据损坏。

### 10. 探测脚本通过受控回环入口运行并生成脱敏证据

接口探测期间临时启用 `compose.ops.yml`，宿主机脚本使用 `curl` 请求 `127.0.0.1`。`probe.sh` 负责可重复调用与断言，`sanitize.sh` 在任何结果写入 `docs/new-api/samples/` 前移除敏感 Header、Cookie、Token、Key、密码、邮箱、手机号和内部凭证值。原始响应只保存在被 Git 忽略的临时目录，流程结束后删除。

矩阵按后续子需求分批验证：

- P1-03/P1-05：status/setup、注册开关、登录前置流程、登录、刷新、退出、当前用户；
- P1-06：Key 列表、创建、读取、编辑、启停、删除及限制字段；
- P1-08：模型与价格；
- P1-09：用户日志、分页、筛选和用量字段；
- P1-10：余额、充值配置/记录、资料和密码修改；
- P1-11：用户协议、隐私政策和公开配置；
- P1-07：只在内部直接探测计划协议的路由、认证与流式基本行为，不通过 edge 公开。

支付、邮件和真实模型调用若缺少外部账号，只验证可达的配置关闭、校验和错误分支，并标明缺失条件。没有选择在 P1-02 编写 Java New API 客户端测试，因为那会提前固化 P1-03 的实现结构；此阶段的证据目标是冻结事实，而不是形成生产适配代码。

## Risks / Trade-offs

- [稳定版可能缺少调研提交中的新认证或字段] → 先跑完整准入矩阵；只有明确阻塞第一阶段时才升级候选，并要求用户接受预发布风险。
- [不同 CPU 架构解析到不同平台镜像] → 同时记录 OCI manifest digest 与当前平台 digest，并对支持架构分别验证拉取结果。
- [上游状态端点在首次初始化前语义不同] → 把“进程健康”和“初始化完成”分开记录，健康检查只判断服务可响应，初始化由独立验收判断。
- [运维覆盖端口被遗忘] → 冒烟脚本检查默认配置的 published ports，手册要求操作后用主文件强制收敛并再次检查。
- [容器环境变量可被本机 Docker 管理员查看] → P1-02 只用于受控本地环境；不输出渲染配置，生产改用 secret 注入由 P1-12 完成。
- [Redis AOF 增加本地磁盘写入] → 接受少量开销换取重启一致性，同时明确 PostgreSQL 才是业务恢复权威。
- [接口探测证据泄密] → 原始文件进入忽略目录，提交前必须经过自动脱敏扫描和人工抽查；样例只保留必要结构。
- [New API 许可证/附加条款随版本边界变化] → 只基于候选 tag/commit 做记录；若商用义务不明确则阻断冻结并寻求法律确认。

## Migration Plan

1. 建立环境变量模板、忽略规则、固定 digest 的外部镜像和五网络 Compose 骨架。
2. 用 `v0.13.2` 作为默认候选，在全新命名卷上启动 PostgreSQL、Redis 和 New API，核对 tag、commit、digest 与许可证文件。
3. 接入现有 `lang-api` 镜像和最小 edge-nginx，完成健康、默认暴露和内部连通性验收。
4. 通过回环运维覆盖完成首次初始化，撤销端口后验证初始化状态与数据持久化。
5. 执行第一阶段接口矩阵并生成脱敏证据；若稳定候选存在不可替代缺口，暂停冻结并向用户提交差异与 v1 RC 风险选择。
6. 对最终候选执行备份、隔离恢复和同版本重建演练，确认初始化与升级路径可重复。
7. 更新基线、兼容性矩阵、运行手册、文档索引和 MVP 状态；只有全部必需项有结论后才允许 LANG-P1-03 开始。
8. 回退时使用上一个已记录镜像和对应数据库备份重新创建环境；不得删除原数据卷，除非用户明确执行独立的本地数据销毁步骤。
