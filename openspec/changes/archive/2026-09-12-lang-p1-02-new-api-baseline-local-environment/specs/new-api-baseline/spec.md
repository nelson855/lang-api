## Purpose

为 Lang API 固定一个可追溯、可重复获取且经过真实运行验证的 New API 上游基线，并以结构化兼容性证据约束后续 Portal API、模型网关和升级工作。

## ADDED Requirements

### Requirement: New API 基线身份不可漂移
项目 SHALL 记录选定 New API 的 Release、完整 Git commit、镜像仓库、镜像 tag、OCI manifest digest、当前验证平台的镜像 digest、核验日期和支持的平台架构。运行配置 MUST 使用 digest 引用，且 MUST NOT 使用 `latest`、`alpha`、`main` 或其他可变引用。

#### Scenario: 根据记录复现镜像
- **WHEN** 开发人员在受支持平台根据基线记录拉取 New API 镜像
- **THEN** 拉取结果的 manifest digest 和平台镜像 digest 与记录一致，并能映射到同一 Release 和 Git commit

#### Scenario: 上游可变 tag 发生移动
- **WHEN** 上游同名 tag 后续指向不同镜像
- **THEN** 项目 Compose 仍解析到已冻结的原镜像内容

#### Scenario: 检查部署引用
- **WHEN** 检查 New API 的 Compose 镜像配置
- **THEN** 配置中不存在浮动 tag 或未带 digest 的 New API 镜像引用

### Requirement: 候选版本经过准入判断
项目 SHALL 优先验证满足第一阶段能力的非预发布 Release。若最终选择预发布版本，基线记录 MUST 说明稳定版缺少的阻塞能力、上游风险提示、已执行的补充测试和明确的风险接受决定；没有这些证据时 MUST NOT 将预发布版本冻结为生产验证基线。

#### Scenario: 稳定候选满足必需能力
- **WHEN** 最新合格的非预发布候选通过全部必需接口、数据库和运行验证
- **THEN** 项目冻结该稳定候选，不因存在更新的预发布版本而自动切换

#### Scenario: 预发布候选被考虑
- **WHEN** 稳定候选缺少第一阶段不可替代的必需能力
- **THEN** 兼容性记录明确失败项，并在预发布候选完成补充验证和风险接受后才允许冻结

### Requirement: 第一阶段接口兼容性矩阵完整
项目 SHALL 对初始化与状态、注册与登录、会话刷新与退出、当前用户与资料修改、API Key 生命周期、模型与价格、用户日志与用量、余额与充值、法律内容以及计划开放的 Relay 协议建立兼容性矩阵。每一项 MUST 记录路由、HTTP 方法、认证与 Cookie/Header 要求、请求字段、成功响应、主要失败响应、前置配置、实测日期和结论状态。

结论状态只允许使用 `已验证`、`存在差异`、`条件可用`、`不可用`。没有真实运行证据的项目 MUST NOT 标记为 `已验证`。

#### Scenario: 查询后续认证适配依据
- **WHEN** LANG-P1-05 需要设计注册、登录、刷新、当前用户和退出适配
- **THEN** 矩阵中存在对应实测条目及脱敏成功/失败样例，且认证状态、Cookie/Header 和前置开关清晰可判定

#### Scenario: 查询后续 Key 与用量适配依据
- **WHEN** LANG-P1-06、LANG-P1-08、LANG-P1-09 或 LANG-P1-10 查询 Key、价格、日志、余额或充值能力
- **THEN** 每项均有明确状态，差异、条件或不可用原因不会被误写成已支持

#### Scenario: 能力未验证
- **WHEN** 某接口因缺少供应商账号、支付渠道或外部服务而无法完成真实验证
- **THEN** 该项标记为 `条件可用` 或 `不可用`，并记录已验证边界和缺失条件

### Requirement: 实测证据安全且可重复
接口实测 SHALL 在隔离的本地环境对冻结镜像执行，并保存可重复的探测步骤、HTTP 状态、关键响应结构和脱敏样例。证据 MUST NOT 包含真实密码、Session、Cookie、Access Token、完整 API Key、供应商密钥、数据库凭据或支付签名。

#### Scenario: 保存认证成功样例
- **WHEN** 探测登录或其他会返回凭证的接口
- **THEN** 兼容性证据保留字段结构和 Cookie 属性，但所有凭证值均被稳定占位符替换

#### Scenario: 重跑接口探测
- **WHEN** 使用相同冻结镜像和已记录前置数据重新执行探测
- **THEN** 能判断接口仍符合记录的路由、方法、认证方式和关键响应结构

### Requirement: 许可证与来源义务可追溯
项目 SHALL 针对实际冻结的 New API tag/commit 保存许可证名称、许可证文件与 NOTICE 来源、是否修改上游、容器分发方式及适用于当前部署方式的署名、源码提供和商标边界说明。许可证结论 MUST 基于冻结版本本身的文件，而不是仅依据 `main` 分支。

#### Scenario: 冻结未修改的官方镜像
- **WHEN** 项目选择直接运行官方 New API 镜像且不修改其源码
- **THEN** 基线记录明确官方来源、未修改状态、随镜像或服务需要保留的许可证/NOTICE 义务及运维后台的访问边界

#### Scenario: 许可证信息不明确
- **WHEN** 冻结版本的许可证或附加条款无法确认是否满足商用部署要求
- **THEN** 该候选 MUST NOT 被标记为可进入生产验证，直至获得明确结论

### Requirement: New API 升级受兼容性门禁控制
冻结后的 New API 升级 SHALL 作为独立变更处理。升级前 MUST 记录目标版本、迁移说明、备份与恢复点，并对兼容性矩阵和数据持久化场景重新验证；不得仅修改 tag 或 digest 后直接视为升级完成。

#### Scenario: 提议升级冻结版本
- **WHEN** 需要从当前基线升级到新 Release
- **THEN** 在变更生效前完成数据库备份、迁移风险记录、接口回归和失败恢复验证

#### Scenario: 升级验证失败
- **WHEN** 必需接口、数据库迁移或数据一致性验证失败
- **THEN** 保持当前冻结基线不变，并记录失败证据和恢复结果

