# LANG-P1-07 公共模型网关验证记录

> 验证日期：2026-09-13。记录只包含命令、状态、计数、耗时类别和 requestId 规则，不保存 Key、模型名、提示词或响应正文。

## 1. 自动化结果

| 范围 | 命令 | 结果 |
|---|---|---|
| Nginx 运行时契约 | `bash integration-tests/gateway/run-contract.sh` | 通过；覆盖精确路由、Header、普通 JSON、SSE 时序、取消、上游中断、413、速率/并发 429、上游 4xx/5xx、不可用与读取超时 |
| 网关与 New API 静态门禁 | `bash integration-tests/gateway/*.sh` 及 `bash integration-tests/new-api/{compose-check,gateway-check,baseline-check,sanitize-test}.sh` | 适用脚本全部通过 |
| 部署预检 | `bash deploy/preflight.sh deploy/.env.example` | 通过 |
| Compose 渲染 | 使用进程内测试占位秘密执行 `docker compose --env-file deploy/.env.example -f deploy/compose.yml config --quiet` | 通过；占位值未写入文件 |
| Maven/前后端 | `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml verify` | `BUILD SUCCESS`；前端 51 个测试文件、182 个测试通过，后端 240 个测试通过，前端构建成功 |
| 真实验收脚本自检 | `bash integration-tests/gateway/real-provider-check-test.sh` | 通过；验证受阻退出、三类成功调用、四类限制响应和脱敏输出 |

Maven 输出仍含既有的 React 重复 key/`act`、jsdom canvas、npm audit 和 bundle size 警告；这些警告未导致测试或构建失败，也不是本次网关改动引入的通过条件。

## 2. TDD 证据

- RED：运行时契约发现上游 500 的安全正文正确但状态仍为 500；GREEN：Nginx `error_page` 显式指定 `=502`，复验通过。
- RED：取消已传播到 fixture，但日志误标为 `stream_completed`；GREEN：以 `$request_completion` 修正 SSE 完成态分类，复验记录为 `client_cancelled`。
- RED：真实验收脚本自检因目标脚本不存在失败；GREEN：实现仅使用环境变量和临时目录的验收脚本，自检通过。

## 3. 真实供应商验收状态

当前进程未提供以下外部条件：

- `MODEL_API_BASE_URL`、`MODEL_API_KEY`、`MODEL_NAME`
- `MODEL_API_DISABLED_KEY`、`MODEL_API_EXPIRED_KEY`
- `MODEL_API_EXHAUSTED_KEY`、`MODEL_API_RESTRICTED_KEY`

执行完整验收：

```bash
bash integration-tests/gateway/real-provider-check.sh --with-restrictions
```

缺少必要变量时脚本返回 `[BLOCKED]` 和退出码 2，不打印变量值。因尚无真实供应商证据，OpenSpec 任务 8.2、8.3、8.4、8.6 保持未完成，阶段文档状态不更新。
