## 1. 冻结契约、配置与公共错误

- [ ] 1.1 基于 New API v0.13.2 为 `/api/token/search` 增加脱敏成功、空结果、分页、名称 wildcard 转义、401、HTTP 200 `success=false` 和非法响应 fixture，并在本地 Compose 可用时抽查其与冻结源码一致
- [ ] 1.2 先编写配置失败测试，再在 `application.properties` 增加 `lang.api-key.*` 共享配置，覆盖 Portal 默认/最大 pageSize、状态聚合请求预算及其他非敏感安全上限，不新增 YAML
- [ ] 1.3 在 `application-dev.properties` 显式声明开发环境 API Key 查询与预算策略，保持现有同源会话、CSRF 和本地 Compose 行为
- [ ] 1.4 在 `application-test.properties` 显式声明更短且确定性的 API Key 聚合/敏感值测试参数，保证超时和清除测试稳定
- [ ] 1.5 在 `application-prod.properties` 显式声明生产 API Key 查询边界，确认配置不能关闭 CSRF、Origin、`no-store` 或明文清除约束
- [ ] 1.6 扩展配置绑定与启动校验测试，拒绝非正 pageSize/预算、默认值大于最大值及可能造成无界聚合的组合
- [ ] 1.7 先扩展统一错误契约测试，再增加 `RESOURCE_CONFLICT`/409、`API_KEY_LIMIT_REACHED`/409 和 `OPERATION_RESULT_UNKNOWN`/502，确认错误正文不包含上游消息、限制正文或完整 Key

## 2. New API Token 适配器

- [ ] 2.1 先为列表、名称搜索和单项读取编写契约失败测试，固定 v0.13.2 方法、路径、分页参数、`session` Cookie、`New-Api-User`、掩码字段、未知字段裁剪、401/404 和上游故障行为
- [ ] 2.2 增加 Token 专用上游 request/response DTO、分页结果和语义化操作，确保 `/api/token/*` 路径、数字状态及逗号/换行字段只存在于 `upstream.newapi` 边界
- [ ] 2.3 实现普通分页与名称搜索适配，对用户名称转义 `%`、`_`、`!` 后构造包含匹配，永不使用上游 `token` 搜索参数，并验证上游页进度与 total
- [ ] 2.4 先为创建编写契约测试，再实现名称、quota、`-1`/Unix 秒、模型逗号串和 IP 换行串转换；确认最多请求一次且不假设成功响应含 id 或明文
- [ ] 2.5 先为完整编辑编写保留字段测试，再实现按当前用户读取、合并允许字段和一次 PUT，保留 key、owner、status、used quota、group、cross-group retry 与未公开字段
- [ ] 2.6 先为启停编写契约测试，再实现 `status_only=true` 专用更新，只允许 enabled/disabled 目标并安全映射过期或额度耗尽冲突
- [ ] 2.7 先为删除和单项 reveal 编写契约测试，再实现当前用户范围的 DELETE 与 `POST /api/token/{id}/key`，reveal 只提取 key 且使用不可默认字符串化的敏感值类型
- [ ] 2.8 完成 Token 状态、时间、quota、模型、IP、掩码与 `sk-` 前缀的双向转换测试，覆盖未知状态、非法字段、重复前缀和缺失字段
- [ ] 2.9 完成 Token 错误翻译与架构测试，覆盖数量上限、无效额度、expired/exhausted 启用、跨用户/不存在、读超时和写结果未知，确认适配边界外无上游 DTO 或路径

## 3. API Key 领域校验与查询服务

- [ ] 3.1 先为公开字段模型编写测试，再实现 `ApiKey`、quota、status、modelRestrictions 和 allowedIps 的 Lang API DTO，列表/详情不包含 `accessed_time`、group、owner、完整 Key 或上游字段
- [ ] 3.2 先编写创建/编辑参数测试，再实现名称 1～50 字符、非负安全整数 quota、未来 expiresAt、模型去空去重及 IPv4/IPv6 字面量规范化，拒绝 CIDR、域名、端口、分隔符和控制字符注入
- [ ] 3.3 先编写默认列表测试，再实现无条件请求的上游分页直通，保证从 1 开始的 page、最大 pageSize、创建时间倒序、掩码和真实 total
- [ ] 3.4 先编写名称搜索测试，再实现 search 分页编排，确认空查询回到普通列表、名称条件不进入日志且取消/超时能释放后续上游请求
- [ ] 3.5 先编写组合状态筛选测试，再实现跨上游页去重聚合、状态映射后过滤和 Portal 分页，覆盖 total 漂移、空页、请求预算耗尽及“不能只过滤当前页”
- [ ] 3.6 先编写详情服务测试，再实现按当前已校验用户读取脱敏详情，统一隐藏不存在与跨用户资源的差异

## 4. API Key 写操作与 Portal 接口

- [ ] 4.1 先编写创建应用服务/Controller 测试，覆盖有限/无限 quota、永不过期、模型/IP 限制、数量上限、重复提交边界、上游失败和成功响应不含 id/secret
- [ ] 4.2 实现 `POST /portal/api/api-keys` 及自有 request/response，只编排一次上游创建并返回统一成功包装
- [ ] 4.3 先编写编辑测试，覆盖单字段修改、完整公开字段修改、非法限制、保留内部字段、跨用户/不存在及写结果未知
- [ ] 4.4 实现 `PUT /portal/api/api-keys/{id}`，由服务端安全上下文确定用户并通过适配器 read-modify-write，不接受 owner、key、used quota 或上游状态字段
- [ ] 4.5 先编写启停测试，覆盖 enabled→disabled、disabled→enabled、expired/exhausted 冲突、重复点击和结果未知
- [ ] 4.6 实现 `PUT /portal/api/api-keys/{id}/status`，只接受 `{enabled:boolean}` 并映射为一次上游 `status_only` 更新
- [ ] 4.7 先编写删除测试，覆盖确认后的成功删除、重复/不存在、跨用户、上游超时与连接中断，确认不自动重试
- [ ] 4.8 实现 `DELETE /portal/api/api-keys/{id}`，成功后返回稳定自有结果，失败时区分安全 NOT_FOUND 与 `OPERATION_RESULT_UNKNOWN`
- [ ] 4.9 先编写 reveal 测试，覆盖 owner 校验、合法/非法 id、缺失 key、带/不带 `sk-`、no-store/no-cache Header 和所有失败响应不含明文
- [ ] 4.10 实现 `POST /portal/api/api-keys/{id}/reveal`，只返回 `{secret}`，禁止 ETag/共享缓存且不把敏感结果保存到服务端字段、缓存或异常
- [ ] 4.11 实现 `GET /portal/api/api-keys` 与 `GET /portal/api/api-keys/{id}`，严格拒绝未知 query、非法 page/pageSize/status/name 和未声明方法，并补统一分页/404/405 契约测试

## 5. 会话安全、CSRF、权限与审计

- [ ] 5.1 先扩展 Spring Security 与会话过滤测试，确认全部 `/portal/api/api-keys/**` 只允许有效 Portal 会话，忽略伪造 owner/role/`New-Api-User` 且不授予管理权限
- [ ] 5.2 先扩展 CSRF/Origin 测试，将创建、编辑、启停、删除和 reveal 纳入现有双提交防护，确认失败时在任何 Token 上游请求前返回 `CSRF_REJECTED`
- [ ] 5.3 为详情、编辑、启停、删除和 reveal 增加双用户集成测试，统一将他人资源表现为 `NOT_FOUND`，且不调用管理员接口确认所有者
- [ ] 5.4 先编写 API Key 安全事件测试，再实现 create/update/status/delete/reveal 完成态事件，只记录 requestId、操作、不可逆主体标识、资源 id、结果类别和原因码
- [ ] 5.5 扩展集中脱敏与访问日志测试，覆盖 request/response、异常、Header、URL、指标和追踪中的完整/掩码 Key、名称、额度、模型/IP 限制及上游正文，确认规范化路由不记录查询串
- [ ] 5.6 增加 reveal 生命周期测试，确认服务端不缓存 secret、响应写出后不保留引用，成功与失败都带安全响应头且不会进入异常 `toString`

## 6. 前端 API Key 数据层

- [ ] 6.1 先为列表、详情、创建、编辑、启停、删除和 reveal 编写运行时 DTO/统一包装测试，拒绝上游字段、非法枚举、非 ISO 时间、无单位 quota 和非法 secret 响应
- [ ] 6.2 实现 `api/apiKeys.ts` 相对 Portal 请求，所有 mutation/reveal 复用 CSRF 引导、同源凭证和 AbortSignal，且 mutation 默认重试次数为 0
- [ ] 6.3 先编写查询键与竞态测试，再实现按用户 id、page、pageSize、规范化 name/status 隔离的查询；条件变化重置第一页并取消旧请求
- [ ] 6.4 实现 create/update/status/delete hooks 的资源级缓存更新/失效，覆盖成功、`UNAUTHENTICATED`、`OPERATION_RESULT_UNKNOWN` 和列表页删除最后一项后的页码回退
- [ ] 6.5 先编写 secret 安全守卫测试，再实现绕过 TanStack Query/全局 Store 的一次性 reveal 请求，确认 secret 不进入 URL、localStorage、sessionStorage、错误、Toast、查询缓存或遥测

## 7. API Key 页面与交互

- [ ] 7.1 先为 `/api-keys` 路由和控制台导航编写测试，覆盖 authenticated 访问、anonymous 安全 returnTo、页面标题、桌面/移动导航和当前项状态
- [ ] 7.2 新增 `ApiKeysPage`，接入名称搜索、状态筛选、分页、加载、首次无 Key、无匹配、可重试错误和结果未知状态，桌面与窄屏均无横向不可用操作
- [ ] 7.3 先为共享创建/编辑表单编写测试，覆盖名称、有限/无限 quota、永不过期、未来期限、模型去重、IPv4/IPv6 校验、高级区域和重复提交阻止
- [ ] 7.4 实现创建/编辑对话框，常用字段默认可见、模型/IP 放高级区域；创建成功清除筛选并回第一页，刷新列表后只显示一次“显示并复制”引导
- [ ] 7.5 先为启停和删除确认编写测试，覆盖风险文案、Key 名称、焦点管理、并发点击、成功缓存失效、NOT_FOUND 与结果未知刷新引导
- [ ] 7.6 实现启停和删除交互，状态使用图标加文字表达，不对 expired/exhausted 执行虚假启用，也不做乐观最终状态
- [ ] 7.7 先为 reveal/复制组件编写测试，覆盖确认前无请求、成功显示、剪贴板成功/失败、60 秒自动清除、关闭、路由卸载和会话失效清除
- [ ] 7.8 实现敏感值确认对话框与复制交互，secret 仅存局部 state；复制成功立即清除，失败时只在当前短时窗口允许手工复制
- [ ] 7.9 补齐 API Key 页面全部中英文资源与完整性测试，确保字段、quota 单位、四种状态、验证、风险确认、空状态、通知和 requestId 支持文案无缺失键或上游品牌
- [ ] 7.10 完成键盘、焦点、屏幕阅读器、axe、减少动效和移动端布局测试，确认操作菜单不依赖 hover、状态不只依赖颜色且主要操作可触达

## 8. 集成验证与交付记录

- [ ] 8.1 增加后端集成流程，串联登录、创建、列表/搜索/状态筛选、详情、编辑、启停、reveal、删除，并断言每步只影响当前用户及分页 total 正确
- [ ] 8.2 增加前端浏览器流程，覆盖首次空状态、创建后复制提示、编辑高级限制、停用/启用、删除、搜索筛选分页、会话过期及窄屏操作
- [ ] 8.3 使用两个测试用户验证读取、编辑、启停、删除和 reveal 均不可越权，错误表现一致且浏览器网络中不存在 New API 私网地址或原始 `/api/token/*`
- [ ] 8.4 扫描源码、测试输出、日志样例和生产构建产物，确认不存在完整 Key、可拼接掩码片段、Cookie、CSRF Token、上游用户 Header、私网地址、原始 Token DTO 或 New API 品牌
- [ ] 8.5 使用项目指定 Maven `settings.xml` 运行完整 `mvn verify`，并执行适用的前端单测、Lint、构建和 Playwright 流程，记录命令、用例数及实际结果
- [ ] 8.6 在本地 Compose 可用时完成一次冻结版真实管理链路抽查：创建受限 Key、列表脱敏、编辑、启停、reveal 和删除；测试 Key 与证据必须脱敏且测试结束后明确清理
- [ ] 8.7 更新 `docs/new-api/第一阶段接口兼容性矩阵.md` 的名称搜索证据和 LANG-P1-06 交付说明，并在全部任务和适用验证完成后同步 `docs/06_第一阶段MVP开发与子需求拆分.md` 状态
