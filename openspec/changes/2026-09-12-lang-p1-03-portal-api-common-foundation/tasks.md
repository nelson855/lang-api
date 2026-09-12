## 1. 依赖与类型安全配置

- [x] 1.1 更新 `portal-api/pom.xml`，引入 Bean Validation、Spring Security、Reactor Netty transport，以及测试范围的 MockWebServer、Spring Security Test 和 ArchUnit；确认所有版本由现有 Spring Boot BOM 或显式固定版本管理
- [x] 1.2 先编写配置绑定与非法配置测试，覆盖 New API Base URL、连接池、连接池获取/建连/读取/写入超时、正文上限、Cookie 策略、站点名、已启用协议及公开 URL 的边界
- [x] 1.3 在 `application.properties` 增加共享 `lang.*` 配置：1 MiB 正文上限、requestId 规则、连接池和超时默认值、Cookie/安全 Header 策略、站点名及公开协议配置键
- [x] 1.4 更新 `application-dev.properties`，配置可被环境变量覆盖的 `http://new-api:3000`、开发 Cookie 策略和默认空的公开协议列表，不写入真实域名或凭证
- [x] 1.5 更新 `application-test.properties`，配置不可达回环默认地址、最小连接池和短超时，并允许测试通过动态属性注入 MockWebServer URL
- [x] 1.6 更新 `application-prod.properties`，将 New API Base URL 设为外部必填配置、强制安全 Cookie、启用 JSON 结构化日志，并让站点名、启用协议及公开 URL 可由外部配置提供
- [x] 1.7 实现类型安全配置对象及启动期校验，验证四个 `.properties` Profile 隔离、启用协议缺少合法 URL 时失败，且仓库不存在 `application.yml` 或 `application.yaml`

## 2. Portal API 统一契约

- [x] 2.1 先编写响应对象测试，固定成功、失败、分页 JSON 字段和分页参数不变量，再实现 `base.response` 下的不可变 Lang API DTO 与响应工厂
- [x] 2.2 先编写 requestId Filter 测试，覆盖合法值复用、缺失/非法/超长值替换、响应 Header/Body 一致和 MDC 清理，再实现最前置 `RequestIdFilter`
- [x] 2.3 先编写全局异常映射测试，覆盖校验、JSON 解析、未认证、禁止访问、404、405、413、上游错误/不可用/超时及未知异常，再实现稳定错误目录、应用异常和统一异常处理器
- [x] 2.4 先编写有 `Content-Length` 和 chunked/未知长度两类超限测试，再实现仅作用于 `/portal/api/**` 的有界请求流包装和正文大小 Filter
- [x] 2.5 将 `PortalApiNotFoundHandler` 改为统一 `NOT_FOUND` 响应，并补充未知 API、已知路径错误方法、未知 Actuator 与 SPA 路由不回退的回归测试
- [x] 2.6 增加测试专用校验端点或等价 MVC fixture，证明 request/path/body 参数失败只返回公开字段的安全提示，不返回约束类名、Java 类型或堆栈

## 3. Security 与安全响应头

- [x] 3.1 先编写 Security MVC 测试，覆盖匿名访问公开配置和页面、测试用受保护端点 401/403、未公开 Actuator 拒绝、无 HTML 登录重定向及无宽泛 CORS
- [x] 3.2 实现 Security Filter Chain、方法安全和统一受保护端点注解，关闭 form login/HTTP Basic，允许未知 Portal 路径进入统一 404，并保留 CSRF 基线
- [x] 3.3 先编写页面、成功 API、4xx/5xx 与 prod HTTPS 场景的响应头断言，再配置 CSP、Frame、nosniff、Referrer、Permissions Policy 和条件 HSTS
- [x] 3.4 增加受信代理边界测试，确认未经配置的外部 `X-Forwarded-Proto` 不能单独触发 HSTS 或改变安全判断
- [x] 3.5 增加自动化规则，确保新增非公开 Portal Controller 必须声明统一保护注解，公开 Controller 只能位于显式白名单

## 4. 结构化日志与敏感信息保护

- [x] 4.1 先编写日志捕获测试，固定单条完成态事件的时间、requestId、method、route、status、durationMs 字段，并确认查询串和未匹配原始路径不进入日志
- [x] 4.2 实现 `PortalAccessLogFilter`，使用 MVC 匹配模板或固定未匹配标识记录 SLF4J key-value 事件，确保异常分支也只产生一条完成态访问日志
- [x] 4.3 先用包含不同大小写 password、Cookie、Set-Cookie、Authorization、Access Token、完整 API Key、支付签名和私网地址的 fixture 编写失败测试，再实现集中脱敏器
- [x] 4.4 验证普通日志、访问日志和异常日志默认不采集 Header、Cookie、query、请求/响应正文或上游 URL，并确认 prod Profile 输出可解析的结构化 JSON

## 5. New API 适配底座

- [x] 5.1 建立 `upstream.newapi` 的 `config`、`transport`、`policy`、`dto`、`operation` 技术分层，并先用 ArchUnit 测试禁止 Controller/Service 依赖上游 DTO 或底层 transport
- [x] 5.2 先编写 MockWebServer 基座，提供录制请求及模拟成功、HTTP 200 + `success=false`、非 2xx、未知字段、非 JSON、延迟、断连和慢速读写的公共 fixture
- [x] 5.3 先编写传输契约测试，覆盖固定 Base URL/相对路径、最大 50 连接/100 pending 的有界池、连接池获取/建连/读取/写入超时、关闭资源和失败后连接可复用
- [x] 5.4 实现专用 Reactor Netty `ConnectionProvider`、连接/读/写超时、`disableRetry(true)` 和 Spring `RestClient` request factory，并将传输失败映射为稳定内部异常
- [x] 5.5 先编写写操作断连与超时测试，断言 MockWebServer 只收到一次请求，再实现只接受内部操作描述且不接受任意绝对 URL 的 `NewApiExchange`
- [x] 5.6 先编写请求 Header 白名单测试，再实现从空集合重建 `Accept`、`Content-Type`、`X-Request-Id` 及认证操作专属 Cookie/`New-Api-User` 的集中策略
- [x] 5.7 先编写上游响应 Header 和 Cookie 转换测试，再实现默认丢弃响应 Header、移除 Cookie Domain、限制 Path、设置 HttpOnly/SameSite/按环境 Secure 及登出过期处理的策略组件
- [x] 5.8 先基于 P1-02 脱敏样例编写响应裁剪与错误翻译测试，再实现未知字段忽略、HTTP 状态/`success=false`/解析失败的统一翻译，确认原始 message 和私网地址不进入客户端响应
- [x] 5.9 实现上游调用结构化事件，记录固定 operation、outcome、durationMs 和当前 requestId，并用日志测试确认不记录 Base URL、Header、Cookie 或正文
- [x] 5.10 将 MockWebServer 支撑代码整理为后续认证、Key、日志、余额、模型和法律适配可复用的契约测试基类，并写明新增操作必须断言的方法、路径、Header、字段裁剪、错误和请求次数

## 6. 公开配置接口

- [x] 6.1 先编写 `PublicPortalProperties` 与 service 单元测试，覆盖固定协议顺序、只返回已启用且合法的地址、全部未启用时返回空数组及非法 URL 启动失败
- [x] 6.2 定义独立的 `PublicConfigResponse` 与 `{protocol,url}` response DTO，实现具体 `PublicConfigService`，确保不引用 `upstream.newapi` DTO 或请求 `/api/status`
- [x] 6.3 先编写 `GET /portal/api/public-config` MVC 契约测试，固定顶层包装、唯一公开字段、匿名访问、`Cache-Control: no-store`、错误方法 405 和 requestId 一致性
- [x] 6.4 实现 `PublicConfigController` 并验证同一构建制品使用 dev/test/prod 不同外部 `.properties` 值时返回对应环境配置，无需前端重新构建
- [x] 6.5 添加泄露回归测试，将 P1-02 `/api/status` 样例中的版本、内部地址、系统名、OAuth、倍率和第三方模板作为禁止字段，确认公开接口响应均不包含它们

## 7. 文档与验收

- [x] 7.1 新增 Portal API 基础契约文档，记录响应包装、完整错误码/HTTP 映射、requestId 规则、`public-config` 示例、公开字段和后续适配接入约束
- [x] 7.2 使用项目指定的 Maven 3.8.4 与 `settings.xml` 运行针对性测试和根工程 `verify`，确认后端、前端、一体化 JAR、SPA 回退及既有 P1-01/P1-02 测试无回归
- [ ] 7.3 在 P1-02 Compose 中启动新制品，验证 edge 只能访问声明的 `public-config`、未知 Portal API 为统一 JSON 404、New API/数据库仍不可由公网直达，且 public-config 在 New API 不可用时仍可返回
- [x] 7.4 执行自动化敏感信息扫描和日志样例检查，确认代码、配置、测试输出及受版本控制文件不包含真实密码、Cookie、Token、完整 Key、支付签名、私网地址泄露或 New API 原始错误
- [ ] 7.5 对照 5 个 delta spec 逐项记录验收命令与实际结果；全部通过后更新 `docs/06_第一阶段MVP开发与子需求拆分.md` 中 LANG-P1-03 状态及必要文档索引
- [x] 7.6 运行 `git diff --check` 并报告最终 `git status`、已执行测试、未验证外部条件和遗留风险，不执行 Git 提交或发布
