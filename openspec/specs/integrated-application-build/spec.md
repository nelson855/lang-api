# integrated-application-build Specification

## Purpose

建立 Lang API 可重复、可验证的一体化工程基线，使独立前端与 Portal API 能通过单一正式入口完成测试、打包和安全容器化，并为后续阶段提供稳定的运行与配置约定。

## Requirements

### Requirement: 单一正式构建入口
系统 SHALL 提供根 Maven 正式构建入口，从不包含历史构建产物的工作区完成前端依赖安装、前端检查与测试、前端生产构建、后端编译与测试、静态资源装配和可执行 JAR 打包。构建所用 Java、Maven、Node.js、npm 和前端依赖版本 MUST 可追溯且不可浮动。

#### Scenario: 干净工作区构建成功
- **WHEN** 在满足已锁定工具链版本的干净工作区执行文档指定的根 Maven 构建命令
- **THEN** 构建成功完成前后端测试并生成包含前端静态资源的可执行 JAR

#### Scenario: 前端测试失败阻止打包
- **WHEN** 前端检查或测试返回失败
- **THEN** 根 Maven 构建 MUST 失败且不得报告可发布制品构建成功

#### Scenario: 后端测试失败阻止打包
- **WHEN** 后端测试返回失败
- **THEN** 根 Maven 构建 MUST 失败且不得报告可发布制品构建成功

### Requirement: 构建产物与源代码隔离
前端生成文件 SHALL 只写入 Maven `target` 管理的构建目录，并由构建过程装配到 JAR 的 classpath 静态资源目录；构建过程 MUST NOT 将生成文件写入 `src/main/resources` 或其他受版本控制的源码目录。

#### Scenario: 构建后检查工作区
- **WHEN** 根 Maven 构建完成
- **THEN** 前端生产资源存在于后端 `target/classes/static` 和最终 JAR 中，且源码资源目录没有新增前端生成文件

#### Scenario: 清理后移除生成产物
- **WHEN** 执行根 Maven 清理生命周期
- **THEN** Maven 管理的前后端装配产物被移除，源码文件保持不变

### Requirement: JAR 独立运行
最终 JAR SHALL 在仅提供兼容 Java 21 运行时、未安装 Node.js 和 npm 的环境中启动并提供已装配的前端页面与健康检查。

#### Scenario: 无 Node.js 环境启动
- **WHEN** 在没有 Node.js 和 npm 的运行环境中启动构建生成的 JAR
- **THEN** 应用成功启动，健康检查可用，且根路径返回已打包的 React 页面

### Requirement: 单页应用路由回退边界
应用 SHALL 对非文件型前端页面路径返回 `index.html` 以支持客户端路由刷新；该回退 MUST 排除 `/portal/api/*`、`/actuator/*` 和静态资源请求。

#### Scenario: 根页面可访问
- **WHEN** 客户端请求 `/`
- **THEN** 应用返回 React 应用入口页面

#### Scenario: 刷新前端路由
- **WHEN** 客户端直接请求一个不存在服务端映射的前端页面路径，例如 `/dashboard`
- **THEN** 应用返回 React 应用入口页面而不是服务器 404

#### Scenario: 未知 Portal API 不回退页面
- **WHEN** 客户端请求不存在的 `/portal/api/test`
- **THEN** 应用返回 JSON 格式的 HTTP 404，响应体 MUST NOT 是 `index.html`

#### Scenario: 未知 Actuator 路径不回退页面
- **WHEN** 客户端请求不存在的 `/actuator/not-found`
- **THEN** 应用返回非页面回退响应，响应体 MUST NOT 是 `index.html`

### Requirement: 环境配置分离
后端 SHALL 仅使用 `.properties` 配置文件：共享配置位于 `application.properties`，环境差异分别位于 `application-dev.properties`、`application-test.properties` 和 `application-prod.properties`。仓库中的配置 MUST NOT 包含真实密钥，生产环境 MUST 能通过外部配置覆盖敏感值。

#### Scenario: 开发环境启动
- **WHEN** 应用以 `dev` Profile 启动
- **THEN** 应用加载共享配置和开发环境配置，且不加载测试或生产环境专属值

#### Scenario: 测试环境启动
- **WHEN** 自动化测试以 `test` Profile 运行
- **THEN** 应用加载共享配置和测试环境配置，且不依赖开发或生产环境专属值

#### Scenario: 生产环境启动
- **WHEN** 应用以 `prod` Profile 启动并从外部提供所需敏感配置
- **THEN** 应用加载共享配置和生产环境配置，且仓库内文件不提供真实密钥默认值

#### Scenario: 配置格式检查
- **WHEN** 检查后端应用配置文件
- **THEN** 不存在用于替代上述配置的 `application.yml` 或 `application.yaml`

### Requirement: 健康与版本可识别
应用 SHALL 提供最小健康检查，并在构建制品中包含可追溯的应用名称和版本信息。健康响应 MUST NOT 泄露环境变量、密钥、内部地址或不必要的组件细节。

#### Scenario: 应用健康
- **WHEN** 应用已完成启动且请求受支持的健康检查端点
- **THEN** 返回成功状态和最小健康信息

#### Scenario: 制品版本核对
- **WHEN** 运维人员检查构建制品或受控版本信息端点
- **THEN** 能识别 Lang API 应用名称和由 Maven 项目版本派生的版本

### Requirement: 精简非 root 运行镜像
系统 SHALL 生成只包含兼容 JRE 21、应用运行所需文件和已构建 JAR 的最终镜像。最终镜像 MUST 使用非 root 用户，且 MUST NOT 包含 Node.js、npm、Maven、源码、`node_modules`、测试报告或构建缓存。

#### Scenario: 镜像身份检查
- **WHEN** 启动最终 `lang-api` 镜像并检查容器进程身份
- **THEN** 应用进程以非 root 用户运行

#### Scenario: 镜像内容检查
- **WHEN** 检查最终镜像文件系统和可执行程序
- **THEN** 仅存在运行应用所需内容，且不包含源码、`node_modules`、Node.js、npm、Maven、测试报告或构建工具

#### Scenario: 容器健康启动
- **WHEN** 使用最终镜像启动容器并等待规定的启动时间
- **THEN** 容器保持运行且健康检查成功

### Requirement: 测试基线可扩展
工程 SHALL 为后端单元与 Web 测试、前端单元测试和跨模块集成测试提供明确目录与最小可运行样例；根 Maven 正式构建 MUST 执行后端和前端最小测试。

#### Scenario: 初始测试基线
- **WHEN** 在新建工程基线上执行根 Maven 正式构建
- **THEN** 至少一个后端测试和一个前端测试被执行并通过，集成测试目录可供后续阶段扩展
