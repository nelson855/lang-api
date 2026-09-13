# frontend-api-integration Specification

## Purpose

为所有前端业务建立唯一的 Portal API 访问、服务端状态和鉴权导航边界，使页面只依赖 Lang API 自有契约，并能一致处理加载、取消、失败和运行时公开配置。

## Requirements

### Requirement: 统一 Portal API 客户端
所有浏览器业务请求 SHALL 通过统一客户端访问相对 `/portal/api/*` 路径。客户端 MUST 拒绝绝对 URL、协议相对 URL、路径穿越和非 Portal API 路径，使用同源凭证策略，发送合法 `X-Request-Id`，并支持由页面或数据层取消尚未完成的请求。前端源码和构建产物 MUST NOT 包含 New API 私网主机、原始 `/api/*` 管理路径或上游 DTO。

#### Scenario: 正常 Portal API 请求
- **WHEN** 页面通过统一客户端请求一个声明的相对 Portal API 路径
- **THEN** 请求发送到当前站点的 `/portal/api/*`，携带合法请求标识并按调用方取消信号终止

#### Scenario: 拒绝越界 URL
- **WHEN** 调用方尝试传入绝对 URL、`//host/path`、`..` 路径或非 `/portal/api/*` 路径
- **THEN** 客户端在发起网络请求前拒绝调用并返回安全的前端配置错误

#### Scenario: 构建产物边界扫描
- **WHEN** 检查前端源码和生产构建产物
- **THEN** 不存在 New API 私网地址、默认品牌、原始管理路径或响应字段依赖

### Requirement: Portal 响应运行时校验
客户端 SHALL 在运行时校验 Portal API 成功与失败包装，只将已声明 DTO 交给页面。非 2xx、统一错误包装、网络失败、取消、非 JSON 和结构不匹配 MUST 转换为可区分的前端错误；已提供的服务端 `requestId` MUST 保留用于错误页面和支持排查，原始异常或响应正文不得直接显示给用户。

#### Scenario: 统一成功响应
- **WHEN** 服务端返回符合 `{requestId,data}` 的成功响应且业务 DTO 有效
- **THEN** 客户端返回经过校验的业务数据和 requestId，不把响应包装细节扩散到页面

#### Scenario: 统一业务失败
- **WHEN** 服务端返回符合 `{requestId, error:{code,message}}` 的失败响应
- **THEN** 客户端产生包含安全 code、message、HTTP 状态和 requestId 的统一前端错误

#### Scenario: 非法服务端响应
- **WHEN** 服务端返回 HTML、无效 JSON 或不符合声明 DTO 的内容
- **THEN** 客户端返回通用不可解析响应错误，不渲染原始内容，并在响应 Header 有合法 requestId 时保留它

#### Scenario: 用户取消请求
- **WHEN** 路由离开或调用方取消一个尚未完成的请求
- **THEN** 客户端终止请求，取消不会作为需要通知用户的服务故障显示

### Requirement: 服务端状态统一管理
前端 SHALL 使用统一的服务端状态 Provider 管理请求缓存、加载、失败和手动重试。默认 MUST NOT 自动重试任何 mutation；公开配置失败也不得进入无限重试。页面卸载或查询失效时 MUST 将取消信号传给统一 API 客户端。

#### Scenario: mutation 失败
- **WHEN** 后续业务 mutation 因网络中断或超时失败
- **THEN** 数据层不自动重放请求，由具体业务决定是否允许用户重试

#### Scenario: 查询手动重试
- **WHEN** 一个查询失败且页面显示可重试错误状态
- **THEN** 只有用户触发重试后才再次请求，并清除旧的瞬时错误展示

### Requirement: 运行时公开配置启动门禁
应用启动 SHALL 请求 `/portal/api/public-config` 并校验 `siteName` 与 `apiBaseUrls`，再以该数据渲染品牌名称和公开 Base URL。加载期间 SHALL 显示明确启动状态；失败时 SHALL 显示可重试错误且不得伪造公开地址；空地址数组 MUST 被视为合法的“尚未开放协议”状态。

#### Scenario: 公开配置加载成功
- **WHEN** public-config 返回合法站点名称和一个或多个地址
- **THEN** 导航、页面标题及相关页面使用运行时站点名称，并仅展示响应中的协议地址

#### Scenario: 公开地址尚未开放
- **WHEN** public-config 返回空 `apiBaseUrls`
- **THEN** 应用正常进入页面，并在需要地址的位置显示尚未开放状态而不是示例域名

#### Scenario: 公开配置加载失败
- **WHEN** public-config 网络失败或结构非法
- **THEN** 应用显示启动错误、可用 requestId 和手动重试操作，不进入无限加载或使用构建期虚假配置

### Requirement: 鉴权路由守卫协议
前端 SHALL 提供可复用的鉴权路由守卫，并以 `GET /portal/api/profile` 的真实结果驱动 `checking`、`authenticated`、`anonymous` 和 `forbidden` 状态：检查中显示加载状态，有效 profile 渲染受保护内容，`UNAUTHENTICATED` 跳转登录，无权限显示 403。登录返回地址 MUST 只接受站内相对路径，防止开放重定向；前端 MUST NOT 根据本地存储或伪造用户恢复 authenticated 状态。

#### Scenario: 页面刷新恢复有效会话
- **WHEN** 应用启动时 profile 返回有效用户
- **THEN** 守卫从 checking 进入 authenticated，并渲染目标受保护页面

#### Scenario: 匿名访问受保护路由
- **WHEN** profile 返回 `UNAUTHENTICATED` 且用户访问受保护路径
- **THEN** 守卫进入 anonymous，跳转 `/login` 并保存经过校验的站内返回路径

#### Scenario: 会话检查中
- **WHEN** profile 请求尚未完成
- **THEN** 守卫显示加载状态且不短暂渲染受保护内容

#### Scenario: 阻止外部返回地址
- **WHEN** 登录返回参数包含绝对地址、协议相对地址或其他站点目标
- **THEN** 守卫丢弃该值并使用安全的站内默认路径

### Requirement: 认证请求与缓存一致性
前端 SHALL 通过统一 Portal API 客户端执行认证选项、CSRF 引导、注册、登录、refresh、profile 和 logout 请求。认证 mutation MUST 不自动重试；登录或 refresh 成功 SHALL 立即更新唯一 profile 缓存，logout、profile 401 或 refresh 401 SHALL 清除所有用户作用域缓存并进入 anonymous 状态。

#### Scenario: 登录成功进入返回页
- **WHEN** 登录成功且存在合法站内 returnTo
- **THEN** 前端写入返回的 profile 缓存并导航到该地址，无需再次显示匿名状态

#### Scenario: 会话在使用中失效
- **WHEN** 任一受保护请求返回 `UNAUTHENTICATED`
- **THEN** 前端只执行一次会话失效处理，清除用户作用域缓存并引导登录

#### Scenario: 退出请求失败
- **WHEN** logout 因上游撤销未确认而返回错误但服务器已清除 Cookie
- **THEN** 前端仍清除本地用户缓存并进入 anonymous，同时显示安全的退出结果提示

#### Scenario: 认证 mutation 不重放
- **WHEN** register、login、refresh 或 logout 遇到网络失败
- **THEN** 数据层不自动重试该请求，由用户决定是否再次操作

### Requirement: CSRF 凭证自动附加
统一 API 客户端 SHALL 在认证写操作前确保已取得 CSRF 凭证，并将匹配 Token 放入约定 Header；CSRF Token 仅保存在运行时或专用非身份 Cookie 中，不得与登录会话混淆。收到 `CSRF_REJECTED` 时 MUST 不自动重放原始认证请求。

#### Scenario: 首次提交登录
- **WHEN** 当前页面尚无 CSRF 凭证并提交登录
- **THEN** 客户端先取得 Token，再携带它执行一次登录 mutation

#### Scenario: CSRF 失效
- **WHEN** 服务端返回 `CSRF_REJECTED`
- **THEN** 前端显示可重试安全提示，不自动重新提交密码或注册数据

### Requirement: API Key 查询与 mutation 一致性
前端 SHALL 通过统一 Portal API 客户端和运行时 DTO 处理 API Key 列表、详情、创建、编辑、启停、删除和 reveal。查询键 MUST 包含当前用户作用域及规范化分页、名称和状态条件；所有 mutation MUST 禁止自动重试，成功后按资源粒度更新或失效列表/详情，`UNAUTHENTICATED` 时复用统一会话失效流程清除全部用户作用域缓存。

#### Scenario: 搜索条件变化
- **WHEN** 用户修改名称或状态条件
- **THEN** 前端将页码重置为 1、取消过期请求并只展示最新条件对应的响应

#### Scenario: 编辑成功
- **WHEN** API Key 编辑 mutation 成功
- **THEN** 前端使对应详情与受影响列表失效，不继续显示旧限制或错误状态

#### Scenario: 写操作网络失败
- **WHEN** 创建、编辑、启停或删除遇到网络失败、超时或结果未确认错误
- **THEN** 客户端不自动重放请求，页面保留安全上下文并提供刷新核对或用户主动重试

### Requirement: API Key secret 不进入查询缓存
reveal 响应 SHALL 使用独立的非缓存请求路径，完整 `secret` MUST NOT 写入服务端状态查询缓存、持久化缓存、开发工具持久化、URL、错误对象、通知文本或遥测。组件 SHALL 在复制成功、对话框关闭、路由离开、设定短时窗口结束或会话失效时清除内存中的 secret；剪贴板写入失败时不得降级为持久存储。

#### Scenario: reveal 成功
- **WHEN** 用户确认 reveal 且服务端返回完整 secret
- **THEN** secret 只进入当前打开的敏感值组件状态，不出现在任何 API Key 查询缓存快照中

#### Scenario: 复制后清除
- **WHEN** 浏览器成功写入剪贴板
- **THEN** 页面显示不含 secret 的成功提示并立即从组件状态清除完整值

#### Scenario: 页面或会话结束
- **WHEN** 用户关闭对话框、切换路由或会话失效
- **THEN** 前端清除 secret，后续返回页面必须重新确认并请求 reveal
