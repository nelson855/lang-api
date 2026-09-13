## MODIFIED Requirements

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

## ADDED Requirements

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
