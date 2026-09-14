## 1. 契约证据与共享基础

- [ ] 1.1 补充冻结版 New API v0.13.2 的脱敏 fixture 和兼容性证据，确认 `/api/log/self`、`/api/log/self/stat`、`/api/data/self`、`/api/user/self` 的认证、参数、时间边界与响应形状
- [x] 1.2 先为默认 24 小时、最大 30 天、秒级 `[startTime,endTime)`、成对参数和固定 `Clock` 编写单元测试，再实现共享时间范围值对象
- [x] 1.3 先为非负 quota、非法配置、精度和舍入编写测试，再从 P1-08 价格映射提取统一 quota→USD `BigDecimal` 转换器
- [x] 1.4 在 `application.properties` 增加 usage 默认范围、最大范围、默认页大小和最大页大小的共享配置，并保留既有 `lang.portal.catalog.quota-per-usd`
- [x] 1.5 在 `application-dev.properties`、`application-test.properties`、`application-prod.properties` 分别显式配置 usage 参数，补充绑定与非法启动配置测试，保持环境配置分离

## 2. New API 日志适配

- [x] 2.1 先编写个人日志客户端契约测试，覆盖固定 `/api/log/self`、`session` Cookie、`New-Api-User` Header、页码页大小及 `type=2/type=5`
- [x] 2.2 先编写查询编码测试，覆盖 Key 精确筛选、模型普通文本包含、LIKE 通配/转义字符、长度限制、未知参数和 `[start,end)` 到右端包含秒的转换
- [x] 2.3 定义个人日志最小上游 DTO、查询命令和分页结果，实现只允许 `SUCCESS/ERROR` 的语义化客户端操作
- [x] 2.4 先编写日志映射与隐私测试，覆盖成功/错误、可空字段、秒转毫秒、数值溢出，以及 `content/other/IP/user/token/channel/group` 无法离开适配边界
- [x] 2.5 实现整页严格校验与安全错误映射，确保非法条目使整页返回 `UPSTREAM_ERROR` 且异常和日志不含上游正文

## 3. New API 用量与余额适配

- [x] 3.1 先编写摘要客户端契约测试，再实现固定 `/api/log/self/stat` 的最小 quota/rpm/tpm 只读操作
- [x] 3.2 先编写小时用量客户端契约测试，再实现固定 `/api/data/self` 的最小桶时间、请求数、Token、quota 只读操作
- [x] 3.3 先编写同小时多模型行、排序、空结果、范围外数据、非整点及负数测试，再实现严格小时聚合器且不补零、不插值
- [x] 3.4 先编写当前余额契约测试，再复用或扩展固定 `/api/user/self` 的认证操作，只向账户边界返回合法 quota
- [x] 3.5 覆盖四个操作的超时、非 2xx、失败 envelope、缺字段、错误类型、未知新增字段与脱敏日志，确认不调用管理员或数据库接口

## 4. Portal 请求日志 API

- [x] 4.1 先编写 `/portal/api/request-logs` Controller 契约测试，覆盖统一分页包装、默认 `SUCCESS`、默认分页/时间、组合筛选、空页和 `UNAUTHENTICATED`
- [x] 4.2 补充非法 page/pageSize/result、单边时间、无时区/亚秒、反向或超 30 天范围，以及 userId/username/channel/group/tokenId/未知 query 的 400 测试
- [x] 4.3 实现 request/response DTO、查询服务和 Controller，返回稳定字段、十进制 quota/USD、可空协议/首字延迟并复用会话身份
- [x] 4.4 补充用户隔离、安全响应和访问日志测试，确认浏览器参数不能覆盖用户身份且敏感上游字段不出现在响应、错误或日志

## 5. Portal 摘要、趋势与余额 API

- [x] 5.1 先编写 `/portal/api/usage/summary` 契约测试，覆盖同一区间消费、`rateWindowSeconds=60`、零值、非法上游值与时间参数错误
- [x] 5.2 实现摘要 DTO、查询服务和 Controller，集中换算 USD，不承诺输入/输出 Token 汇总、成功率或平均延迟
- [x] 5.3 先编写 `/portal/api/usage/timeseries` 契约测试，覆盖 `granularity=HOUR`、合并、升序、空点、金额、时间边界和整体失败
- [x] 5.4 实现趋势 DTO、查询服务和 Controller，直接使用权威小时数据且不从日志补算
- [x] 5.5 先编写 `/portal/api/account/balance` 契约测试，覆盖当前 quota/USD、零余额、非法 quota、匿名访问和 `Cache-Control: no-store`
- [x] 5.6 实现余额 DTO、查询服务和 Controller，每次读取上游且不返回 profile、充值或累计用量字段
- [x] 5.7 扩展 Portal Security 与方法/未知 API 测试，确认四个只读资源只允许有效会话且保持 JSON 401/404/405 契约

## 6. Dashboard 基础用量概览

- [x] 6.1 先为 balance、usage summary 和 timeseries 的前端 schema/API/query 编写测试，覆盖十进制字符串、时间序列校验和统一错误处理
- [x] 6.2 先编写 Dashboard 组件测试，覆盖三个并行查询、同一时间范围、独立 loading/empty/error/retry、趋势延迟提示和局部失败保留
- [x] 6.3 替换 `DashboardPage` 占位内容，实现当前余额、区间消费、60 秒 RPM/TPM、小时请求量与消费趋势，并标清单位和口径
- [x] 6.4 实现默认最近 24 小时及最大 30 天的时间选择，使摘要与趋势共享规范化边界而余额不受历史范围影响
- [x] 6.5 增加用户标识到认证查询 key，并在登出/会话失效时清理余额、用量和日志缓存，覆盖账户切换不显示旧数据的测试
- [x] 6.6 补充 Dashboard 窄屏、键盘操作、中英文文案、页面标题与图表空状态测试

## 7. 请求日志页面

- [x] 7.1 先为 request logs 前端 schema/API/query 编写测试，覆盖默认值、服务端 total、可空字段、组合筛选和参数序列化
- [x] 7.2 先编写 `/dashboard/request-logs` 路由和导航测试，覆盖已登录访问、匿名安全 returnTo、桌面/移动导航选中状态，以及顶层 `/request-logs`、`/usage` 仍为未知路由
- [x] 7.3 先编写页面交互测试，覆盖筛选提交回第一页、翻页保留筛选、刷新、加载、空结果、普通错误和会话失效
- [x] 7.4 实现请求日志页面和控制台导航，展示时间、Key、模型、Token、耗时、结果、费用、requestId 及缺失协议/首字延迟的“暂无数据”
- [x] 7.5 实现窄屏可操作布局并验证无整页横向溢出、筛选和分页可通过键盘与触摸完成

## 8. 集成验证与交付记录

- [x] 8.1 运行 Portal API 单元、契约与安全测试，并使用项目指定 Maven `settings.xml` 完成根工程 `verify`
- [ ] 8.2 运行前端 lint、类型检查、Vitest 与相关 Playwright 流程，验证 Dashboard、请求日志、刷新、匿名跳转和移动端导航
- [ ] 8.3 在一体化 JAR/Compose 中验证 Portal API、SPA 回退、未知 API JSON 404，确认浏览器不直接访问 New API
- [ ] 8.4 在具备真实 New API 日志/数据导出、可用 Key、渠道和额度时执行一次 P1-07 调用，记录日志出现、摘要/趋势时效和余额扣减的一致性证据
- [x] 8.5 更新 `docs/06_第一阶段MVP开发与子需求拆分.md` 的 LANG-P1-09 实际状态及必要接口说明，明确真实环境未满足项，不提前宣称完成
- [x] 8.6 运行 OpenSpec 严格校验、`git diff --check` 和 `git status`，确认所有实际完成且已验证的任务已即时勾选并报告未提交改动
