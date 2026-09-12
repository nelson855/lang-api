## 1. 工具链与根工程

- [ ] 1.1 核对并锁定 Java 21、Maven 3.8.4、Spring Boot 3.5.x、Node.js、npm、React、TypeScript、Vite 和测试工具的确切稳定版本，记录版本来源且不使用浮动版本
- [ ] 1.2 创建根 `pom.xml`，设置统一的 groupId、artifactId、版本、Java 编译参数、依赖与插件管理，并按 `frontend`、`portal-api` 顺序配置 Maven Reactor
- [ ] 1.3 配置 Maven Enforcer 校验 Java 21 与 Maven 3.8.4，并确保失败信息能明确指出不兼容的工具版本
- [ ] 1.4 完善 `.gitignore`，排除 Maven、Node.js、Vite、测试与 IDE 生成物，确认不会忽略需要提交的锁文件和配置模板

## 2. 前端最小工程与测试

- [ ] 2.1 创建 `frontend` Maven 模块和 React + TypeScript + Vite 目录，建立 `package.json`、`package-lock.json`、TypeScript、Vite、Lint 和 Vitest 配置
- [ ] 2.2 先编写失败的前端最小测试，覆盖应用壳渲染与客户端路由入口，再实现最小 React 应用使测试通过
- [ ] 2.3 在前端模块 POM 中使用锁定版本的 `frontend-maven-plugin` 安装本地 Node.js/npm，并依次绑定 `npm ci`、检查、非 watch 测试和生产构建
- [ ] 2.4 验证前端 Maven 生命周期在检查、测试或 Vite 构建失败时向根构建传播失败，并在清理后删除模块生成物

## 3. Portal API 最小工程与配置

- [ ] 3.1 创建 `portal-api` Spring Boot 3.5.x 模块、Java 21 应用入口和最小 `infrastructure.web`、`infrastructure.monitoring` 包，引入 Spring MVC、Actuator 和测试依赖
- [ ] 3.2 创建共享 `application.properties`，仅配置应用名称、通用行为、最小 Actuator 暴露和安全的健康详情，不默认激活任何环境 Profile
- [ ] 3.3 创建 `application-dev.properties`，只保存本地开发环境差异且不包含真实密钥
- [ ] 3.4 创建 `application-test.properties`，只保存自动化测试环境差异，并让后端测试显式激活 `test` Profile
- [ ] 3.5 创建 `application-prod.properties`，只保存安全的生产环境默认值和外部配置占位约定，不包含真实地址或密钥
- [ ] 3.6 增加自动化检查，确保不存在替代上述配置的 `application.yml` 或 `application.yaml`，并验证 `dev`、`test`、`prod` 不互相加载专属值
- [ ] 3.7 配置 Spring Boot Maven Plugin 的可执行 JAR 与 `build-info`，使应用名和版本从根 Maven 项目版本派生

## 4. 静态资源装配与路由边界

- [ ] 4.1 先编写失败的后端 Web 测试，覆盖 `/`、前端深层路由、未知 `/portal/api/test`、未知 `/actuator/not-found`、健康和版本信息
- [ ] 4.2 在 Portal API 的 `process-resources` 阶段将 `frontend/dist` 复制到 `target/classes/static`，并在缺少 `index.html` 时使构建立即失败
- [ ] 4.3 实现 MVC 末端 SPA 回退，只处理合格的页面 GET 请求，并明确排除 `/portal/api`、`/actuator` 和静态文件路径
- [ ] 4.4 实现 LANG-P1-03 前使用的最小 Portal API JSON 404 处理，确保未来更具体的业务路由优先匹配
- [ ] 4.5 使后端 Web、健康和版本测试通过，并检查健康与信息响应不泄露环境变量、密钥、内部地址或组件细节
- [ ] 4.6 验证构建生成物只进入 `frontend` 与 `portal-api` 的 `target` 目录，且 `src/main/resources` 不出现前端生成文件

## 5. 集成测试目录与构建文档

- [ ] 5.1 创建 `integration-tests/` 目录及中文说明，定义后续跨模块测试的职责、命名和接入根构建的条件，不添加伪造业务场景
- [ ] 5.2 编写工程构建与本地启动说明，记录带指定 `settings.xml` 的正式 Maven 命令，以及显式选择 `dev`、`test`、`prod` Profile 的方式
- [ ] 5.3 增加制品级校验，确认 JAR 包含 `static/index.html` 和版本元数据，并能在没有 Node.js/npm 的运行环境启动

## 6. 容器镜像

- [ ] 6.1 创建多阶段 `Dockerfile`，构建阶段通过 BuildKit secret 使用 Maven settings 并执行正式根 Maven 构建，运行阶段只复制 JAR 到锁定的精简 JRE 21 镜像
- [ ] 6.2 在运行镜像中创建固定 UID/GID 的非 root 用户，显式使用 `prod` Profile 启动并配置最小健康检查
- [ ] 6.3 创建 `.dockerignore`，排除 `.git`、IDE 文件、本地构建产物、`node_modules`、源码外无关文档、测试报告、缓存和潜在密钥文件
- [ ] 6.4 构建并检查最终镜像，验证进程非 root、健康检查成功，且镜像中不存在 Node.js、npm、Maven、源码、`node_modules`、测试报告或构建缓存

## 7. 最终验收

- [ ] 7.1 从干净工作区执行 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml clean package`，确认前后端测试均实际执行且制品生成成功
- [ ] 7.2 启动最终 JAR，验证 `/`、前端路由刷新、Portal API JSON 404、Actuator 排除、健康检查和版本信息符合 spec
- [ ] 7.3 执行配置、依赖锁定、敏感信息、源码污染、镜像内容和 `git diff --check` 检查，记录命令及结果
- [ ] 7.4 对照 `integrated-application-build` 的全部 Requirement 和 Scenario 汇总验收证据，并报告最终 `git status`，保留未提交改动等待用户决定
