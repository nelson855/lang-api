## 1. New API 候选与制品身份

- [ ] 1.1 从 New API 官方 Release、tag、镜像仓库和冻结版本源码核对 `v0.13.2` 候选的完整 commit、支持架构、数据库要求、环境变量、迁移提示、`LICENSE`、`NOTICE` 和第三方声明
- [ ] 1.2 拉取稳定候选的官方容器镜像，记录 OCI manifest digest、本机平台 digest 和可重复核验命令，并证明 tag、commit 与镜像版本输出一致
- [ ] 1.3 创建 `docs/new-api/基线版本与许可证.md`，记录候选身份、来源、是否修改、许可证与 NOTICE 义务、商用部署待确认项及 2026-09-12 的官方资料依据
- [ ] 1.4 定义稳定候选准入清单；若其缺少第一阶段不可替代能力，记录阻塞证据并暂停冻结，向用户提交 v1 RC 候选及“不建议生产使用”风险，获得明确选择后再继续

## 2. 密钥模板与 Compose 骨架

- [ ] 2.1 先编写 Compose 静态校验脚本，覆盖五个服务、禁止 `container_name`、外部镜像 digest、必填变量、端口发布、网络成员和命名卷约束，并确认校验在骨架缺失时失败
- [ ] 2.2 创建 `deploy/.env.example`，为端口、PostgreSQL、Redis 和 New API Session/Crypto 变量提供中文说明与空值；首次管理员凭证只在受控初始化时输入，不进入模板
- [ ] 2.3 更新 `.gitignore`，明确忽略 `deploy/.env`、原始探测响应、数据库备份和本地临时证据，同时保留 `.env.example` 与脱敏样例
- [ ] 2.4 创建 `deploy/compose.yml`，定义 `edge-nginx`、`lang-api`、`new-api`、`postgres`、`redis`，固定所有外部镜像 digest，并通过 `${VAR:?message}` 在缺少必填密钥时立即失败
- [ ] 2.5 让 `lang-api` 复用根 Dockerfile 构建并显式激活 `dev` Profile；验证本阶段不新增 YAML 配置、不修改四个现有 `.properties` 文件，也不添加尚未被代码消费的 New API 配置键
- [ ] 2.6 运行静态校验和 `docker compose config --quiet`，修正 Compose 结构并确认输出日志不包含完整渲染后的密钥环境

## 3. 网络边界与 Nginx

- [ ] 3.1 在 Compose 中建立 `web`、`control`、`relay`、`data`、`egress` 网络，按设计配置服务成员关系，并将 control、relay、data 标记为内部网络
- [ ] 3.2 配置 PostgreSQL 和 Redis 只加入 data 网络，New API 加入 control、relay、data、egress，Lang API 加入 web、control，edge-nginx 加入 web、relay
- [ ] 3.3 先编写网关失败场景测试，覆盖 New API 默认页面、`/api/*`、`/setup/*`、`/v1/*` 和 `/v1beta/*` 不可通过 edge 到达
- [ ] 3.4 创建 `gateway/nginx.conf`，只代理 Lang API 页面与 `/portal/api/*`，提供 `/healthz`，对管理和 Relay 路径返回自有 404，使网关测试通过
- [ ] 3.5 创建 `deploy/compose.ops.yml`，仅在显式叠加时将 New API 3000 端口绑定到 `127.0.0.1`，不为 PostgreSQL 或 Redis增加任何端口
- [ ] 3.6 验证加载运维覆盖和恢复主配置时 New API 端口分别出现与消失，并验证局域网地址不能访问该回环绑定

## 4. 健康、启动依赖与持久化

- [ ] 4.1 为 PostgreSQL、Redis、New API、Lang API 和 edge-nginx 配置实际健康检查及合理的 interval、timeout、retries、start_period
- [ ] 4.2 使用 `service_healthy` 建立 PostgreSQL/Redis → New API、Lang API → edge-nginx 的启动依赖，并保留 New API 首次待初始化状态的明确诊断
- [ ] 4.3 为 PostgreSQL、Redis AOF、New API 运行数据和日志配置命名卷，确认 PostgreSQL 是业务数据恢复权威
- [ ] 4.4 创建 `integration-tests/new-api/smoke.sh`，验证五服务状态、唯一 edge 端口、内部 DNS/连通性、数据网络隔离、Lang API 首页和所有关闭路径
- [ ] 4.5 从干净 Compose 项目运行启动与冒烟测试，确认重复执行同一启动命令能收敛且所有失败可定位到具体服务

## 5. New API 首次初始化与数据保留

- [ ] 5.1 通过运维覆盖启动稳定候选，实测状态和 setup 接口/页面在初始化前后的行为，并将健康与初始化完成分开判断
- [ ] 5.2 使用本地生成且不入库的管理员凭证完成首次初始化，记录可重复步骤后撤销运维端口
- [ ] 5.3 创建无真实个人信息的抽样数据并记录稳定标识，执行不删除卷的停止和容器重建，再验证初始化状态和抽样数据仍存在
- [ ] 5.4 在 `docs/new-api/初始化与升级手册.md` 中区分日常 stop/down 与彻底 `down -v`，将卷销毁放在独立章节并添加不可恢复警告
- [ ] 5.5 验证默认主 Compose 下 New API 管理页面、管理 API、PostgreSQL 和 Redis 均不能从宿主机或 edge 直接访问

## 6. 接口探测与脱敏基线

- [ ] 6.1 创建 `integration-tests/new-api/probe.sh` 的请求、Cookie jar、HTTP 状态和断言框架，所有原始响应只写入被忽略的临时目录
- [ ] 6.2 创建 `integration-tests/new-api/sanitize.sh`，先用包含密码、Cookie、Token、API Key、邮箱、手机号和数据库凭证的测试夹具证明脱敏与敏感信息扫描有效
- [ ] 6.3 创建 `docs/new-api/第一阶段接口兼容性矩阵.md`，固定字段：能力、方法、路径、认证、请求、成功响应、失败响应、前置条件、日期、状态和证据链接
- [ ] 6.4 实测 status/setup、公开配置、注册开关、登录前置、登录、刷新、退出和当前用户，保存脱敏成功/失败样例并更新矩阵
- [ ] 6.5 实测 API Key 列表、创建、读取、编辑、启停、删除以及额度、过期、模型和 IP 限制字段，保存脱敏样例并更新矩阵
- [ ] 6.6 实测模型目录、价格与厂商信息接口，记录数据口径、权限、空数据和错误行为并更新矩阵
- [ ] 6.7 实测用户日志、分页、筛选、用量和余额接口，记录时间、额度/金额单位及调用前置条件并更新矩阵
- [ ] 6.8 实测充值配置/记录、资料与密码修改、用户协议、隐私政策和其他公开配置；缺少支付或邮件外部条件时标为 `条件可用`，不得伪造已验证
- [ ] 6.9 在内部 New API 入口探测计划支持的 OpenAI、Anthropic、Gemini Relay 路径、认证和基础流式行为；缺少真实供应商时记录已验证边界，同时确认 edge 仍未开放这些路径
- [ ] 6.10 运行矩阵完整性检查，确保每项只使用 `已验证`、`存在差异`、`条件可用`、`不可用`，且所有 `已验证` 项均链接真实脱敏证据

## 7. 冻结最终基线

- [ ] 7.1 对照第一阶段后续子需求判断稳定候选是否存在不可替代缺口，并将所有差异映射到 P1-03、P1-05～P1-11 的设计输入
- [ ] 7.2 若稳定候选通过准入，更新基线文档与 Compose 为最终 Release、commit、manifest digest 和平台 digest；若改选预发布版本，则先完成用户风险接受和全部补充验证
- [ ] 7.3 自动比对 Compose 的 New API digest 与基线文档记录，并扫描所有 Compose/脚本/文档，确认不存在 New API 浮动镜像引用
- [ ] 7.4 对最终 tag/commit 再次核对许可证、NOTICE、源码提供、署名和商标边界，记录明确结论或将不明确项标为生产验证阻塞项

## 8. 备份、升级与恢复演练

- [ ] 8.1 在手册中记录 `pg_dump` 备份、校验、保管和恢复命令，确保备份文件进入被忽略目录且命令不回显密码
- [ ] 8.2 从当前基线生成备份并恢复到独立 Compose 项目/数据库卷，核对管理员初始化状态和抽样业务数据
- [ ] 8.3 在恢复副本上启动最终候选镜像，让 New API 自身执行 schema 初始化/迁移，重跑关键接口探测和数据核对
- [ ] 8.4 模拟迁移或兼容性验收失败，验证可以使用旧镜像和未迁移备份恢复，不让旧镜像直接连接已迁移的唯一数据库
- [ ] 8.5 将“备份—副本迁移—复验—切换—失败恢复”固化为后续 New API 升级门禁，并禁止仅修改 tag/digest 即宣布升级完成

## 9. 文档与最终验收

- [ ] 9.1 更新 `docs/00_文档索引.md` 的当前 New API 基线入口，并在全部验收通过后将 `docs/06_第一阶段MVP开发与子需求拆分.md` 中 LANG-P1-02 状态改为已完成
- [ ] 9.2 执行项目正式 Maven `verify`，确认 P1-01 前后端构建与测试未被 Compose 变更破坏
- [ ] 9.3 从清理后的 Compose 状态执行单命令启动、健康、默认端口、内部访问、管理入口、Relay 关闭、持久化和重复启动全部验收
- [ ] 9.4 扫描受版本控制文件，确认没有真实密码、Session/Crypto Secret、Cookie、Token、完整 API Key、供应商 Key、支付签名、原始响应或数据库备份
- [ ] 9.5 对照两个 capability 的全部 Requirement/Scenario 汇总实际命令、通过结果、条件项和阻塞项，执行 `git diff --check` 并报告最终 `git status`
