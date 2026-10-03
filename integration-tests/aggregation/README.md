# 第二阶段聚合验收

本入口只运行检查，不部署、发布、提交代码，不修改 New API 配置或清空数据卷。当前基线保持 `p2-2026-09-22-a`。

## 执行

从仓库根目录运行，使用 Python 3 标准库、Java 21、指定 Maven 3.8.4/settings.xml 及项目固定 Node。先执行根工程 `mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml clean verify` 生成集成 JAR 和 frontend/dist。

```bash
python3 integration-tests/aggregation/acceptance.py contract
python3 integration-tests/aggregation/acceptance.py local --container lang-api-lang-api-1
python3 integration-tests/aggregation/acceptance.py performance --samples 1
python3 integration-tests/aggregation/acceptance.py all
python3 integration-tests/aggregation/acceptance.py all --plan-only
python3 -m unittest discover -s integration-tests/aggregation -p 'test_*.py'
```

`--base-url` 默认本地 `http://localhost:8081`；`--upstream-container` 默认 lang-api-new-api-1；`--jar` 可指定待验收集成 JAR。容器的 /app/app.jar 必须与该 JAR 完全相同，JAR 静态资源须逐文件与 frontend/dist 相同。镜像标签与 Git revision 不能代替字节摘要。外部配置、挂载、未知 profile 或缺失 New API digest 导致 BLOCKED；资源不一致 FAIL。默认入口不会重新构建或重启容器；契约后端使用test，保持待验收JAR不变。增量构建可能保留旧chunk，正式本轮集成产物应使用clean verify。

性能入口可通过进程环境变量 `P210_COOKIE` 提供专用测试用户门户 Cookie，不打印、不持久化。结束时间由 `--end` 固定（带时区），UTC 范围统一为 `[start,end)`，每个范围的冷热请求保持同一完整查询身份。`--samples` 是每范围冷热各自次数，默认各1次冒烟；冷请求先等待有效 cache-ttl 加1秒，随后立即热请求。等待可能较长；不要在繁忙共享环境运行。30d 仅验证拒绝。不得把单次/总超时预算作为正式性能目标。

当前 HTTP 测量仅能证明客户端延迟及响应分类；缓存命中、真实上游调用数、页数和记录规模若没有受控观测来源，保存 null/unknown，不能推断或发布典型规模基线。`performance.py` 只对有明确冷热观测、非负规模计数、代表性数据、部署资源确认且各范围冷热≥20有效样本计算最近秩 p50/p95（排序后第 ceil(n×p) 项）。小样本保留明细，基线及目标保持 BLOCKED。完整请求数/成功率、金额、趋势、TOPUP/REFUND、上游真实时区和增强元数据不因本地样本存在而解除阻塞。

真实双身份`identity`已接通；浏览器、固定范围对账仍需适用证据。不接受任意外部PASS JSON覆盖结果。身份未提供时不尝试管理员账号或读取历史Cookie。

身份检查通过`P210_IDENTITY_FILE`读取权限600的专用输入JSON，结构为两条记录，每条提供`username`、`password`、`sample_key_name`；最后一项是各自已存在真实成功日志的Key名称，不能用两个空账号替代。凭据文件必须位于受保护忽略目录。通过`P210_IDENTITY_START`与`--end`指定带时区、秒精度、不超过1小时且包含两条样本的同一窗口。`local`只有当前构建核对PASS后才运行身份检查；使用独立临时实例，通过该实例日志的真实log-self调用事件核验冷热缓存。同一固定窗口重复运行前应等待缓存TTL到期，或使用新隔离实例；不清空共享缓存。不新增供应商调用、额度分配或账号注册。

适配独立登录，创建临时零额度Key，核对Key/成功日志/聚合和流水归属，检查伪造输入及退出旧Cookie重放；finally删除登记Key并清除客户端Cookie，保留账号和真实日志。清除Cookie与服务端撤销分别记录：旧Cookie仍有效时返回FAIL。`identity.json`只保存断言、次数、范围和恢复状态，扫描通过后才落盘；缺前提为BLOCKED，实际错误或清理失败为FAIL。受控`fail_after_keys=True`演练只证明Key清理和客户端Cookie清除，不能替代会话撤销。

## 报告与退出码

默认报告位于忽略目录 `portal-api/target/p210-report/report.json` 和 `report.md`。`--output` 可指定安全报告目录。每项记录预期、实测、证据类型、状态、原因、位置；构建清单保存非敏感配置和摘要。现行契约结论针对所选集合，阶段上线条件按矩阵单独计算。FAIL 优先返回1；无FAIL但有BLOCKED返回2；全部适用项PASS/CANCELLED返回0。仅P2-09协议和首Token延迟可引用用户取消依据。

模拟传输/固定时钟契约验证标记 controlled-contract，真实本地少量请求标记 real-small-sample；两者均不能替代典型规模真实数据。Playwright 默认使用 route 合成接口，不是本地真实 New API 验收。

`ScanEvidence.java` 复用既有 AggregationSensitiveScanner，读取标准输入，禁止输出原始载荷及 snippet；报告扫描成功后才能写入两个文件。性能采样只保存 `evidence.py` 白名单，不保存用户、Cookie、Token、原始请求响应。禁止字段即使隐藏在未知字段下也先拒绝。Java scanner 依赖仅取指定 Maven 本地仓库。命令诊断及容器复制的 JAR 位于 `portal-api/target/probe-raw/p210/`，不把完整日志作为持久化报告。不要复制该目录到版本化文档。

## 失败恢复与升级回归

1. 检查报告失败/阻塞位置及构建摘要，先区分缺少前提与契约不符。所有原始诊断留在忽略目录；对外只使用通过扫描的投影。
2. 独立本地测试进程不清理共享业务缓存；等待TTL或退出该隔离进程即可失效其进程内缓存。临时配置先记录原值，正常及异常退出均恢复；新建测试Key必须在 finally 中删除；测试身份按预先约定保留或删除。不要删除真实日志。
3. New API 升级前保留当前版本和镜像digest，以及分页、时间键/过滤、错误记录开关/完整性、时区、pricing版本、quota_per_unit及实扣证据；升级后重新运行来源契约、真实抽样和页面检查。旧type=5样本不能证明新版本持续完整。
4. 应用回退先核对旧镜像ID与JAR摘要、对应前端资源及配置档案，按运维流程恢复旧镜像和配置，保留数据库、Redis与所有命名卷。本文不是执行发布的授权。
5. 恢复后重新核对运行摘要、等待健康，验证登录刷新、Dashboard范围/不可用状态、钱包覆盖、模型价格与详情、日志筛选和匿名401，再运行受支持范围冷热冒烟及敏感扫描。禁止 `down -v`。统计基线升级或启用受阻能力必须另行设计。

`observability`已接通实际执行：运行`AggregationObservabilityChainTests`的16项双聚合调用链场景，检查拒绝、失败HTTP尝试、已接纳页/记录、缓存、共享加载、耗时与有限标签。只在本次测试通过并有注册表证据时返回PASS；断言失败为FAIL，工具或本次证据缺失为BLOCKED。安全扫描后的`observability.json`与报告保存在同一输出目录，原始诊断仍在忽略目录。计数定义见[逐项方案B1](../../docs/20_LANG-P2-10阻塞项与逐项推进方案.md)：HTTP尝试覆盖所有成功/错误log-self调用，逻辑读取只由聚合来源收口，二者不可互换。注册表是本地受控HTTP测试进程，不是运行容器或真实New API指标；不新增公开监控接口。

原阶段差距、配置差异与测试复用见 [matrix.md](matrix.md)。本轮结果见 [results.md](results.md)。
