# New API 本地环境测试（LANG-P1-02）

## 脚本一览

| 脚本 | 用途 | 是否需要 Docker |
|---|---|---|
| `compose-check.sh` | Compose 静态校验：五服务、无 container_name、digest、必填变量、端口、网络、命名卷 | 否 |
| `gateway-check.sh` | 网关静态校验：只代理 Lang API，管理/Relay 路径自有 404 | 否 |
| `baseline-check.sh` | 基线一致性：Compose digest 与基线文档一致，无浮动引用 | 否 |
| `sanitize.sh` + `sanitize-test.sh` | 脱敏过滤器及其自检 | 否 |
| `smoke.sh` | 五服务冒烟：状态、端口、连通性、隔离、入口、关闭路径 | 是 |
| `probe.sh` | 接口探测框架：请求、Cookie、断言、脱敏落盘 | 是 |

## 本地可运行的静态验证

```bash
bash integration-tests/new-api/compose-check.sh
bash integration-tests/new-api/gateway-check.sh
bash integration-tests/new-api/baseline-check.sh
bash integration-tests/new-api/sanitize-test.sh
```

## 需要真实环境的部分

`smoke.sh` 与 `probe.sh` 需要 Docker 守护进程与已填写的 `deploy/.env`，
并按 `docs/new-api/初始化与升级手册.md` 启动环境后执行。原始响应只写入
`integration-tests/new-api/tmp/`（已忽略），落盘样例前必须经 `sanitize.sh`。
