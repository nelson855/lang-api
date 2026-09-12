## Context

当前仓库只有产品与架构文档和 OpenSpec 配置，没有可运行代码。总体架构已经确定为 React 静态前端与 Spring Boot Portal API 逻辑分层、物理合包；根 Maven 是正式构建入口，生产运行时不包含 Node.js。行为契约见 `specs/integrated-application-build/spec.md`，需求动机见 `proposal.md`。

本设计同时受以下约束：Java 使用 21，Spring Boot 使用 3.5.x 稳定补丁版本；本机正式 Maven 为 3.8.4，并必须使用项目指定的 `settings.xml`；生成的前端资源不得写回源码目录；后端配置只使用 `.properties` 并明确区分 `dev`、`test`、`prod`。

## Goals / Non-Goals

**Goals:**

- 形成后续阶段可直接扩展的根工程、前端模块、Portal API 模块和集成测试目录。
- 用 Maven Reactor 明确构建顺序，使单条根 Maven 命令成为本地和容器构建的共同入口。
- 让同一个 JAR 同时提供前端静态资源、SPA 页面回退、健康检查和版本信息。
- 让工具链、依赖、制品和容器运行身份都能自动验证。
- 将环境差异限定在四个 `.properties` 文件及外部配置，不把环境地址或密钥编译进前端。

**Non-Goals:**

- 不在本阶段建立业务模块、Portal API 通用响应体系或 New API 适配层。
- 不建立 Compose 全栈环境、Nginx 模型网关、数据库或 Redis。
- 不实现正式页面设计体系；前端只提供验证构建与路由所需的最小应用壳。
- 不建立独立前端发布物或运行时 Node.js 服务。

## Decisions

### 1. 使用 Maven Reactor 编排两个源码模块

根 `pom.xml` 使用 `packaging=pom`，按顺序聚合 `frontend` 和 `portal-api`。`frontend` 作为 Maven 构建模块拥有自己的 `pom.xml`，通过 `frontend-maven-plugin` 在 Maven 生命周期中安装锁定的 Node.js/npm、执行 `npm ci`、检查、Vitest 测试和 Vite 构建。`portal-api` 随后在 `process-resources` 阶段把 `frontend/dist` 复制到自身的 `target/classes/static`，再生成 Spring Boot 可执行 JAR。

版本在根 POM 的 properties 与 Maven `dependencyManagement`/`pluginManagement` 中集中管理；`package-lock.json` 锁定完整前端依赖树。使用 Maven Enforcer 校验 Java 21 与 Maven 3.8.4，构建文档记录本机正式命令并显式传入既定 `settings.xml`。

选择该方案而不是根 POM 调用任意 shell 脚本，是因为 Reactor 能表达模块顺序和失败传播，Windows/Linux 行为也更一致。未采用前后端两个独立 CI 流水线，因为这会破坏单制品和原子发布约束。

### 2. 复制生成物，不修改源码资源目录

`frontend/dist` 和复制后的静态资源都属于构建输出：前者由前端模块的清理生命周期管理，后者只进入 `portal-api/target/classes/static`。Portal API 的 `src/main/resources` 仅存放手写配置和必要源码资源，不接收构建生成文件。

复制发生在 Portal API 的 `process-resources`，使编译、测试和 Spring Boot 重打包看到同一份静态资源。没有选择把 `dist` 提交到 Git，因为这会产生不可审查的重复源和过期制品风险。

### 3. Portal API 以最小模块化单体启动

`portal-api` 使用 Java 21、Spring Boot 3.5.x、Spring MVC 和 Actuator。初始包结构只建立应用入口及支撑工程基线所需的 `infrastructure.web`、`infrastructure.monitoring`；认证、Key、用量等业务包等到对应子需求再创建，避免空抽象。

Spring Boot Maven Plugin 生成可执行 JAR，并通过 `build-info` 从根 Maven 项目版本生成版本元数据。应用名称固定为 `lang-api`，JAR 文件名由同一 Maven 版本体系推导；构建脚本和 Dockerfile 不各自维护版本号。

### 4. SPA 回退位于 MVC 映射末端并按命名空间排除

静态资源先按正常规则解析；没有服务端 Controller、没有真实静态文件且属于页面路径的 GET 请求才回退到 classpath 的 `static/index.html`。回退判定明确拒绝 `/portal/api`、`/portal/api/**`、`/actuator`、`/actuator/**` 以及看起来是静态文件的路径。

在 LANG-P1-03 的统一异常体系建立前，增加最小 Portal API 未匹配处理，使未知 `/portal/api/**` 始终返回 `application/json` 的 404；后续由公共错误响应替换。没有采用“所有 404 都转发 index.html”，因为它会掩盖 API 拼写错误并导致前端把 HTML 当 JSON 解析。

### 5. `.properties` 配置按共享与环境差异分层

后端配置文件固定为：

```text
portal-api/src/main/resources/
├── application.properties
├── application-dev.properties
├── application-test.properties
└── application-prod.properties
```

`application.properties` 只保存应用名、通用序列化行为、Actuator 最小暴露范围等共享值；三个 Profile 文件只保存各环境差异。仓库中不设置真实密钥，也不创建 `application.yml`/`application.yaml`。应用不在共享配置里默认激活 `dev`：本地命令显式指定 `dev`，测试使用 `test`，生产容器显式要求 `prod`，从而避免把开发配置误带入生产。

敏感项和部署相关地址由环境变量或外部 `.properties` 覆盖。前端仍只使用相对路径，本阶段不引入环境专属 Vite API 地址。

### 6. 健康信息公开最小化，版本信息保持受控

Actuator 仅暴露 `health` 和 `info`，健康详情默认不公开。`info` 使用 Maven build-info 提供应用名与项目版本，不注入 Git 工作区脏状态、环境变量或依赖明细。测试分别验证健康端点、版本元数据存在以及响应不包含敏感配置。

没有自建健康 Controller，因为 Actuator 提供稳定的生命周期语义，并可在后续阶段接入 Micrometer 和容器健康检查。

### 7. 多阶段镜像复用正式构建入口

构建阶段使用锁定版本（实施时记录确切 tag，并优先固定 digest）的 JDK 21 + Maven 镜像，通过 BuildKit secret 挂载 Maven `settings.xml` 后执行同一根 Maven `clean package`。Node.js/npm 由 Maven 前端插件安装在构建目录，不依赖构建主机的全局 Node.js。

运行阶段使用锁定的精简 JRE 21 镜像，只复制最终 JAR，创建固定 UID/GID 的非 root 用户并以该用户启动。`.dockerignore` 排除 `.git`、IDE 文件、源码外的本地构建产物、`node_modules`、测试报告、密钥和无关文档。镜像健康检查调用应用健康端点。

没有使用单阶段镜像，因为它会把编译器、Maven、Node.js 和源码带入生产；也没有从宿主机直接复制预构建 JAR作为唯一方案，因为那会使 Docker 构建无法独立复现。

### 8. 测试分层与正式构建绑定

- 前端：Vitest 验证最小应用渲染与客户端路由入口；测试命令为非 watch 模式并由 Maven 调用。
- 后端：JUnit 5 / Spring Boot Test 验证上下文、健康与版本信息、根页面、前端路由回退、Portal API JSON 404 和 Actuator 排除。
- 构建集成：检查 JAR 包含 `static/index.html`，并在无 Node.js 的运行阶段启动 JAR进行冒烟验证。
- 容器：验证非 root 身份、健康状态，以及运行层没有 Node.js/npm/Maven、源码和 `node_modules`。
- `integration-tests/`：本阶段建立用途与执行约定，不添加伪造的 New API 或数据库测试；首个真实跨模块场景在后续子需求接入正式构建。

## Risks / Trade-offs

- [前端 Maven 模块产生额外 POM] → 只保留工具链和 npm 生命周期编排，不在该 POM 重复维护前端依赖。
- [Portal API 依赖相邻模块的 `dist`] → 通过 Reactor 固定模块顺序，并增加缺失 `index.html` 时立即失败的构建校验。
- [SPA 通配回退误吞未来接口] → 对 `/portal/api`、`/actuator` 和静态文件做明确排除，并用 Web 测试锁定边界。
- [Maven 本机构建与容器构建下载来源不同] → 两者使用同一根 POM、锁文件和 Maven settings；容器凭据只通过 BuildKit secret 注入。
- [精确依赖版本逐渐过期] → 本阶段锁定并记录确切版本，后续升级作为独立变更执行，禁止浮动版本自动漂移。
- [默认不激活 Profile 增加启动参数] → 在开发、测试和容器命令中显式设置，换取避免生产误用开发配置的安全性。

## Migration Plan

1. 在当前文档型仓库中新增工程骨架，不迁移现有运行数据或服务。
2. 先建立根 Maven 与前后端最小模块，跑通本机正式构建。
3. 再接入静态资源装配、路由边界、Actuator、版本信息和三环境配置测试。
4. 最后使用多阶段 Dockerfile 重跑正式构建并完成镜像级验收。
5. 若实施失败，可删除本变更新增的工程骨架文件恢复到纯文档仓库；本阶段没有数据库或外部状态需要回滚。
