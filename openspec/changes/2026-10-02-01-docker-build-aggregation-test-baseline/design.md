## Context

见 `proposal.md`。根 Maven 构建与 Dockerfile 都有意执行完整测试，不能用跳过测试来规避失败。当前失败都位于测试代码或 Docker 构建时的测试输入可见性：生产 API、运行镜像和 New API 数据不应改变。

## Goals / Non-Goals

**Goals:**

- 让架构规则只审查生产类，仍保留对生产 Web 层上游 DTO/日志类型泄露的禁止。
- 让聚合范围测试在构造合法上下文后，验证目标服务的超范围拒绝路径。
- 让证据夹具测试以显式、只读且与 Maven/Docker 工作目录无关的方式发现仓库根目录。
- 使本地 Maven 与 Docker 构建执行相同的完整测试语义并成功。

**Non-Goals:**

- 不跳过、降级或删除任何前后端测试，不在 Dockerfile 中加入测试绕过开关。
- 不改聚合 API、数据基线、目录接口、Docker 运行阶段镜像或 Compose 网络和卷。
- 不把 P2-07 的运行态验证伪装成 Docker 构建成功；P2-07 仍单独记录真实扣费与目录接口证据。

## Decisions

### 1. 架构导入排除测试类

`PortalArchitectureTests` 只导入生产输出目录中的 `com.lang.portal` 类，避免测试桩的辅助返回类型触发生产 Web 层规则。

备选方案是在每个测试桩中改用 Portal 自有 DTO。该方案把架构测试的实现细节渗入夹具，扩大无关修改，且不能解决将来其他测试类被导入的问题。

### 2. 范围测试使用可构造的上下文

范围纵向测试使用 DAY 粒度构造 8 天的合法 `AggregationQueryContext`（默认允许 30 天），再进入快照服务，验证默认 7 天 `maxLiveLogRange` 在访问上游前拒绝。无需修改生产校验或配置。

备选方案是放宽 `AggregationQueryContext` 的构造校验。该方案会削弱生产侧的早期参数拒绝，不采用。

### 3. 夹具根目录采用显式锚点和受限回退

测试统一从 `user.dir` 向上查找 `pom.xml` 与 `docs/new-api/` 的组合锚点；不依赖容器中不存在的 `.git` 或 `openspec/`。Dockerfile 只复制 `docs/new-api/aggregation-baseline.json` 与 `docs/new-api/samples/aggregation/`，`.dockerignore` 仅为这些路径设置例外。定位失败必须继续报错，不能以空夹具、跳过或外部网络替代。

备选方案是依赖 `user.dir` 固定等于仓库根目录。该假设在模块化 Maven 与 Docker `/build/portal-api` 工作目录下不成立。

### 4. 完整构建保持为发布前门禁

修复后验证使用模块全量测试、根 Maven `verify` 和 Compose `build lang-api`（Docker 内部继续 `clean package`）；不引入 `-DskipTests`、Surefire 排除或 Docker 专用的弱测试路径。

### 5. 前端余额断言必须限定语义区域

前端问题属于相同 Docker 构建基线，不另建业务 change。原始 Docker、宿主全量与根 Maven 前端均曾 100/582 通过，但修复后首次 Docker 重建在 `DashboardPage.test.tsx` 取得真实失败：整页 `findByText(/1\.0/)` 同时匹配 `US$1.00` 余额与 `2026-10-02T09:53:01.000Z` 时间戳。每分钟秒数末位为 1 时存在碰撞，属于墙上时钟引起的非确定性测试，并非 Docker 专有依赖或 Canvas 问题。

先用 `vi.setSystemTime` 固定 `2026-10-02T09:53:01.000Z`，保留原断言确认宿主也稳定失败；继续使用真实异步计时器。再以 `within(await screen.findByRole('region', {name: '当前余额'}))` 限定余额断言，保留金额、指标、错误恢复与旧接口禁止断言。测试结束恢复时钟。生产组件、依赖、超时和测试数量保持不变；Canvas / getComputedStyle stderr 继续作为非致命诊断。

## Risks / Trade-offs

- [架构导入过滤过宽] → 断言过滤仅针对测试输出路径，并为生产违规保留正反例测试。
- [根目录定位掩盖缺失夹具] → 要求全部基线、manifest 与 fixture 存在；任一缺失仍失败。
- [Docker 上下文扩大] → 只复制证据测试所需的版本控制文件，不把密钥、`node_modules`、测试报告带入最终运行镜像。
- [历史测试修复影响 P2-07 排期] → 作为独立变更推进；P2-07 只消费通过后的正常运行态验证结果。

## Migration Plan

1. 先在本机执行受影响测试，证明失败可复现。
2. 最小化修复每一种失败并分别验证。
3. 运行根 Maven 完整构建，再运行 Docker Compose 构建。
4. 构建通过后，以正常 Compose 启动当前源码，并执行 P2-07 三个只读目录接口的运行态核对。
5. 若 Docker 构建失败，保留当前运行容器和卷，修复测试后重新构建；不执行 `down -v`。
