## Why

LANG-P1-01 需要先建立后续 11 个子需求共用的工程与构建基线，证明独立 React 前端与 Spring Boot Portal API 能通过一个正式入口完成测试、构建、打包和容器化。若缺少这条可重复验证的基线，后续功能会在目录结构、版本、配置和发布方式上持续分叉。

## What Changes

- 初始化根 Maven 聚合工程、`portal-api` Spring Boot 3.5.x 模块和 `frontend` React + TypeScript + Vite 工程。
- 锁定 Java 21、Maven、Node.js、npm 及前端依赖版本，并以根 Maven 命令作为正式构建入口。
- 在 Maven 生命周期中执行 `npm ci`、前端检查与测试、Vite 构建，并只将 `frontend/dist` 复制到后端 `target/classes/static`。
- 提供 React Router 页面回退；明确排除 `/portal/api/*` 与 `/actuator/*`，使未知 Portal API 返回 JSON 404。
- 提供最小健康检查与应用版本信息。
- 后端配置统一采用 `.properties`，区分 `dev`、`test`、`prod` 三个环境。
- 建立后端、前端和集成测试目录，以及能够产出精简非 root 运行镜像的多阶段 `Dockerfile` 和 `.dockerignore`。
- 本变更不实现业务页面、New API 适配、数据库、消息队列、Compose 完整环境或微服务拆分。

## Capabilities

### New Capabilities

- `integrated-application-build`: 定义前后端工程基线、统一构建、JAR 静态资源封装、SPA 路由回退、环境配置、健康与版本信息、容器镜像及最小测试的可验证行为。

### Modified Capabilities

无。

## Impact

- 新增根 `pom.xml`、`frontend/`、`portal-api/`、`integration-tests/`、`Dockerfile` 与 `.dockerignore` 等工程骨架。
- 引入 Spring Boot 3.5.x、React、TypeScript、Vite、前后端测试工具及 Maven 前端构建插件。
- 确立唯一正式构建命令和 `portal-api/target` 下的 JAR 制品约定。
- 确立后端 `.properties` 配置和 `dev`、`test`、`prod` Profile 约定。
- 不新增业务 API，也不依赖 New API、PostgreSQL 或 Redis。
