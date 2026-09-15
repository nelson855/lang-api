# frontend-application-shell Specification Delta

## MODIFIED Requirements

### Requirement: 第一批路由表面
前端 SHALL 声明 `/`、`/models`、`/docs`、`/terms`、`/privacy`、`/regions`、`/login`、`/register` 和 `/dashboard` 路由，并为其他路径提供前端 404。路由切换 MUST 使用客户端导航且直接刷新任一路由仍由一体化应用恢复；每个页面 MUST 设置与当前语言及运行时站点名称一致的文档标题。

#### Scenario: 公开路由导航
- **WHEN** 用户通过站内导航访问首页、模型广场、开发文档、法律内容或服务地区
- **THEN** 页面不整页刷新即可切换，主标题和文档标题与目标页面一致

#### Scenario: 直接刷新深层路由
- **WHEN** 用户直接刷新任一声明的公开、认证或控制台路由
- **THEN** Spring Boot 返回 SPA 入口且前端恢复对应路由或按鉴权守卫重定向

#### Scenario: 未知前端路由
- **WHEN** 用户访问不属于服务端 API 或静态资源的未知页面路径
- **THEN** 前端显示带返回首页和支持入口的自有 404 页面，而不是浏览器或 Spring Boot 默认错误页

### Requirement: 注册页面服从运行时策略
`/register` SHALL 以认证选项为唯一展示依据：公开注册开启时显示用户名、密码和确认密码表单；关闭时根据 `registrationDisabledReason` 展示管理员关闭、预览模式或法律正文未就绪的准确说明且不显示可提交表单。页面 MUST 始终提供用户协议与隐私政策入口；公开布局的注册入口 MUST 同步隐藏或改为不可注册说明，且页面不得展示未启用的邮箱、验证码或邀请码字段。

#### Scenario: 注册开放
- **WHEN** 认证选项返回 `registrationEnabled=true`
- **THEN** 注册页显示可提交的用户名密码表单和两份法律入口，成功后引导到登录页

#### Scenario: 注册关闭
- **WHEN** 认证选项返回 `registrationEnabled=false`
- **THEN** 注册页显示与关闭原因一致的说明和法律入口，不发送注册请求

#### Scenario: 注册策略在加载中
- **WHEN** 认证选项尚未返回
- **THEN** 页面显示加载状态和法律入口，不短暂展示注册表单

## ADDED Requirements

### Requirement: 完整公开站点导航与内容闭环
公开布局 SHALL 在桌面和移动端提供首页、模型、文档、服务地区及适用的登录/注册目的地；页脚 SHALL 提供用户协议、隐私政策、服务地区和运行时支持入口。首页 MUST 使用真实站点名、当前开放协议与可到达的主要操作解释产品价值，不得包含虚构客户、指标、价格、能力或无行为按钮。

#### Scenario: 公开站点主要目的地
- **WHEN** 匿名用户从首页使用桌面导航、移动导航或页脚
- **THEN** 所有展示的内部链接都到达声明路由，支持入口使用 public-config 的受校验值

#### Scenario: 能力尚未开放
- **WHEN** public-config 没有公开模型协议或部署为 `PREVIEW`
- **THEN** 首页准确显示准备状态，不把注册或调用入口描述为已正式可用

#### Scenario: 移动导航访问新增页面
- **WHEN** 窄屏用户打开导航并选择服务地区或其他公开目的地
- **THEN** 导航按既有焦点规则关闭并进入目标页面，不产生背景误操作

### Requirement: 服务地区页面来源真实
`/regions` SHALL 只根据 public-config 的 `supportedRegions` 展示服务地区，并使用本地受控地区名称映射；空列表时显示尚未公布状态。页面 MUST 说明地区列表属于当前服务可用范围，不推导数据驻留、付款方式、税务、合规认证或未声明的服务承诺。

#### Scenario: 展示已配置地区
- **WHEN** public-config 返回一个或多个受支持地区代码
- **THEN** 页面以当前界面语言展示对应名称，且保留代码用于无歧义表达

#### Scenario: 地区尚未公布
- **WHEN** public-config 返回空地区数组
- **THEN** 页面显示未发布状态和支持入口，不使用推测地区填充

### Requirement: 品牌资产与公开 Meta 一致
前端 SHALL 复用自有 `L/` 几何标识作为 MVP Logo，并提供同源 favicon、文本替代和可集中替换的资产入口。公开路由 SHALL 基于运行时站点名和当前语言设置 title、description、canonical 与 robots；`PREVIEW`、404、错误或未发布法律页面 MUST 为 `noindex`，只有 `PUBLIC` 中有效的公开页面可索引。源码、构建产物、公开响应和用户可见错误不得残留 QinghuaAPI、New API 私网地址或上游默认品牌。

#### Scenario: 正式公开页面 Meta
- **WHEN** `PUBLIC` 模式下用户访问有效公开页面
- **THEN** 页面生成以 `siteUrl` 为基准的同源 canonical、对应语言标题和描述，并允许索引

#### Scenario: 预览或错误页面 Meta
- **WHEN** 部署为 `PREVIEW` 或当前页面为 404、错误、未发布法律状态
- **THEN** 页面声明 `noindex`，且不生成指向错误地址的 canonical

#### Scenario: 品牌残留检查
- **WHEN** 自动化检查前端源码、构建产物、公开接口夹具和用户可见文案
- **THEN** 不出现被禁止的上游品牌、私网域名或默认站点资产
