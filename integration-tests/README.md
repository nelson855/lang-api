# 集成测试目录说明

本目录用于存放跨模块（前端 + Portal API + 后续外部依赖）的端到端与契约测试。

## 职责

- 验证“正式构建产物”从用户视角的行为：页面可访问、路由刷新、接口返回、健康检查。
- 不复制后端单元测试或前端单元测试已经覆盖的内容。
- 不伪造 New API、数据库、消息队列等外部依赖；需要外部依赖的场景等到对应子需求接入真实环境后再写。

## 命名约定

- 目录按场景命名：`portal-smoke/`、`new-api-contract/` 等。
- 每个场景自带 `README.md`，说明前置条件、执行命令与通过标准。

## 接入正式构建的条件

- 测试必须是可重复执行的命令（`mvn` 或 `npm` 入口），失败时返回非零退出码。
- 不得依赖开发机上的全局 Node.js、已启动的外部服务或手工步骤。
- 接入前先在本地用正式构建命令跑通，再把调用接到根 `pom.xml` 或独立脚本。

## 当前场景

- `new-api/`：New API 基线、脱敏、Compose 与真实本地环境探测。
- `gateway/`：LANG-P1-07 公共模型网关的静态门禁、可控 Relay fixture、Nginx 容器契约与真实供应商验收入口。

网关离线契约使用 fixture 验证传输与边界，不把 fixture 结果当作真实模型成功证据。真实供应商验收必须显式提供进程环境变量后运行：

```bash
bash integration-tests/gateway/real-provider-check.sh --with-restrictions
```

缺少真实 Base URL、模型、有效 Key 或四类限制 Key 时，脚本返回 `BLOCKED`/退出码 2，相关 OpenSpec 任务保持未完成。
