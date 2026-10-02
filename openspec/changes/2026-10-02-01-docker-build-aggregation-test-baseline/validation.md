# Docker 构建基线验证记录（2026-10-02）

## 根因与先失败验证

- 原始 Compose build 使用 Dockerfile 的根 Maven `clean package`。前端 `npm ci` 后 100 个文件、582 项测试通过；后端 747 项测试出现 1 个失败、6 个错误、1 个既有 opt-in 跳过。
- 原始 Docker、宿主 `npm run test -- --run` 与首轮根 Maven 的前端均曾 100/582 通过。首次修复后 Docker 重建前端出现 1 失败/581 通过：`DashboardPage.test.tsx` 整页余额正则 `/1\.0/` 同时命中 `US$1.00` 与时钟生成的 `09:53:01.000Z`，是时间相关的多匹配断言错误。Canvas/getComputedStyle stderr 出现在通过的运行中，并非根因。
- 前端先失败：固定 `2026-10-02T09:53:01.000Z` 后，宿主受影响文件稳定复现相同多匹配错误（1 失败/6 通过）；随后仅限定余额查询到“当前余额”region，等待余额区域异步出现并在测试结束恢复时钟后，受影响文件 7 项全部通过；未修改业务组件、依赖、超时或跳过测试。
- 架构导入包含 `target/test-classes`，4 个 upstream 返回类型违规全部来自测试桩。宿主原测试失败，新增加的生产输出导入守卫也先失败。采用 ArchUnit 标准 `DO_NOT_INCLUDE_TESTS` 后 6 项架构测试通过；独立导入违规样例仍触发同一返回类型规则。
- 原范围测试构造 HOUR 粒度 40 天上下文，超过默认 7 天，尚未到达服务入口即失败。现使用合法的 DAY 粒度 8 天查询（允许 30 天），由实时日志读取的 7 天上限拒绝，仍断言 `INVALID_ARGUMENT` 与上游请求数 0；纵向测试 7 项通过。
- Docker 的 `.dockerignore` 排除了证据目录，原 5 项证据读取在 `/build/portal-api` 明确失败。构建阶段仅复制冻结基线 JSON 与脱敏 aggregation 样例；最终 JRE 阶段仍仅复制 JAR。
- 统一只读定位用 `pom.xml` 与 `docs/new-api` 锚点。新增定位测试的子目录干扰情形先失败，修复后覆盖模块/根工作目录、无 Git/OpenSpec 的容器式目录、缺失明确报错。现有缺失样例引用、manifest 与敏感内容检查继续执行。

## 已执行结果

- 受影响后端与配置回归：39 项，失败 0、错误 0、跳过 0。
- `portal-api` 全量：752 项，失败 0、错误 0、跳过 1。唯一跳过为既有 `AggregationFixtureMaterializationTests`，要求显式真实探测 opt-in；本变更未新增或修改跳过条件，所有只读证据校验均实际运行。
- Maven 使用 `/Users/nelson/software/apache-maven-3.8.4/bin/mvn -s /Users/nelson/software/apache-maven-3.8.4/conf/settings.xml`。
- 首轮根工程 `verify`（前端修复前）：三个 Maven 模块全部成功；前端 Lint、100/582 测试及生产构建成功；后端 752 项（0 失败、0 错误、1 个既有 opt-in 跳过），其中 catalog 19 个测试类、67 项全部通过。
- 前端修复后的最终根工程 `verify`：三个模块全部成功；前端 100/582、后端 752 项（0 失败、0 错误、1 个既有 opt-in 跳过），包含全部 67 项 catalog 测试。
- 最终主 Compose `build --progress=plain lang-api` 成功：前端 100/582、后端 752 项（0 失败、0 错误、1 个既有 opt-in 跳过）；全部真实仓库 fixture 校验在 `/build/portal-api` 实际通过。
- 主文件 + ops 文件 `build --progress=plain lang-api edge-nginx` 成功，复用通过后的构建层。
- 主 Compose `up -d --build` 返回成功；edge-nginx、lang-api、new-api、postgres、redis 全部 running/healthy。未执行数据卷删除，启动前后四个 `lang-api_` 数据卷名称完全一致。
- 运行时匿名列表、`deepseek-v4-flash` 详情及 providers 全部 200，三者定价版本一致；列表/详情 `pricing` 完全一致（TOKEN/USD/PER_MILLION_TOKENS，input/output 75.0，request null），均 no-store。
- 结合既有脱敏真实调用证据复核 `(108 + 252) × 75 / 1,000,000 = 0.027` 与扣费 0.027000 一致；本轮未重新发起付费模型调用。增强元数据与四项能力全部保持 null。
- 构建基线任务 12/12；P2-07 任务 31/31。P2 阶段文档仍按原约束记录独立 quotaPerUsd 与增强来源外部缺口。

完整构建日志保留在本机 `/tmp/lang-docker-baseline/`；原始日志仅作为本机诊断输入，不复制凭据或原始请求标识到本记录。

## 本轮修改文件

构建与测试（未修改生产业务源码）：

- `Dockerfile`、`.dockerignore`
- `frontend/src/pages/DashboardPage.test.tsx`
- `portal-api/src/test/java/com/lang/portal/PortalArchitectureTests.java`
- `portal-api/src/test/java/com/lang/portal/base/aggregation/AggregationEvidenceRecomputeTests.java`
- `portal-api/src/test/java/com/lang/portal/infrastructure/aggregation/AccountAggregationVerticalTests.java`
- `portal-api/src/test/java/com/lang/portal/upstream/newapi/log/AggregationLogTokenIdTests.java`
- `portal-api/src/test/java/com/lang/portal/upstream/newapi/probe/AggregationBaselineJsonContractTests.java`
- 新增 `portal-api/src/test/java/com/lang/portal/testsupport/AggregationEvidenceRoot.java`
- 新增 `portal-api/src/test/java/com/lang/portal/testsupport/AggregationEvidenceRootTests.java`

计划与结果文档：

- 本 change 的 `proposal.md`、`design.md`、`tasks.md` 及新增本记录 `validation.md`
- P2-07 change 的 `proposal.md`、`design.md`、`tasks.md`
- `docs/11_第二阶段聚合能力开发与子需求拆分.md`
- `docs/16_LANG-P2-07-模型详情与供应商筛选接口说明.md`

两个 change 的 OpenSpec 严格校验及 `git diff --check` 均通过。既有 catalog 生产实现、测试与其他未提交改动均保留，未执行提交、推送、PR、tag 或对外发布。完整 `git status --short` 快照在 `/tmp/lang-docker-baseline/git-status-short.txt`。
