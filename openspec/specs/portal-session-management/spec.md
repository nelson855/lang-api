# portal-session-management Specification

## Purpose

定义由 Lang API 承担的浏览器会话边界、当前用户恢复与退出语义，在沿用 New API 会话权威的同时防止上游凭证、管理员能力和私网信息暴露给前端。

## Requirements

### Requirement: 自有浏览器会话 Cookie
登录成功后系统 SHALL 将上游会话封装为 `LANG_SESSION` 与 `LANG_UID` 两个 Host-only、`HttpOnly` Cookie；二者 SHALL 使用 `/portal` Path、配置的 `SameSite` 和环境对应的 `Secure`，且 MUST NOT 设置 New API 私网 Domain。浏览器脚本 MUST NOT 能读取这两个 Cookie，任何长期认证凭证 MUST NOT 写入 `localStorage` 或 `sessionStorage`。

#### Scenario: 生产环境设置会话
- **WHEN** 用户在 `prod` 通过 HTTPS 登录成功
- **THEN** 两个会话 Cookie 均为 Host-only、`HttpOnly`、`Secure`，Path 为 `/portal`，且不包含上游域名

#### Scenario: 前端存储检查
- **WHEN** 登录、刷新页面和会话恢复完成
- **THEN** 浏览器存储中不存在会话值、New API Access Token 或其他长期认证凭证

#### Scenario: 会话 Cookie 不完整
- **WHEN** 请求只携带两个会话 Cookie 中的一个，或用户标识格式非法
- **THEN** 系统将其视为无效会话、清除两个 Cookie 并返回 `UNAUTHENTICATED`

### Requirement: 当前用户与会话恢复
`GET /portal/api/profile` SHALL 是受保护接口，并以 New API 当前会话状态为权威校验浏览器会话。成功响应只允许包含普通用户前端所需的 `id`、`username`、`displayName` 和可选 `email`，MUST NOT 返回角色、用户组、管理员权限、额度或上游状态字段。会话过期、用户不存在或用户被禁用时 SHALL 清除本地会话并返回 `UNAUTHENTICATED`。

#### Scenario: 页面刷新恢复会话
- **WHEN** 浏览器保留有效 Cookie 并在应用启动后请求当前用户
- **THEN** 系统校验上游会话并返回裁剪后的 profile，前端恢复 authenticated 状态

#### Scenario: 会话已经过期
- **WHEN** 上游不再接受浏览器携带的会话
- **THEN** 系统清除会话 Cookie 并返回 HTTP 401 与 `UNAUTHENTICATED`

#### Scenario: 不暴露管理员能力
- **WHEN** 上游当前用户响应包含 role、group、quota 或管理字段
- **THEN** Portal profile 响应不包含这些字段，普通前端也不据此提供管理入口

### Requirement: 会话重新校验
`POST /portal/api/auth/refresh` SHALL 重新校验现有 New API 会话并返回与 profile 相同的裁剪用户资料。由于冻结版 New API v0.13.2 不提供独立刷新令牌接口，此操作 MUST NOT 获取或暴露长期 Access Token，也 MUST NOT 承诺滚动延长上游会话有效期。

#### Scenario: 有效会话刷新
- **WHEN** 客户端以有效会话调用 refresh
- **THEN** 系统返回最新裁剪用户资料且不向浏览器返回 Access Token

#### Scenario: 失效会话刷新
- **WHEN** 客户端以过期或已撤销会话调用 refresh
- **THEN** 系统清除会话 Cookie并返回 `UNAUTHENTICATED`

### Requirement: 幂等退出与凭证撤销
`POST /portal/api/auth/logout` SHALL 尝试调用 New API 退出以撤销有效上游会话，并无条件清除浏览器端两个会话 Cookie。没有会话或会话已失效时 SHALL 返回成功；上游无法确认撤销时 SHALL 返回稳定上游错误，但仍须清除浏览器 Cookie，使前端进入 anonymous 状态。

#### Scenario: 正常退出
- **WHEN** 已登录用户调用退出且上游确认成功
- **THEN** 系统清除会话 Cookie，后续旧会话不能访问受保护接口

#### Scenario: 重复退出
- **WHEN** 客户端没有会话或在成功退出后再次调用退出
- **THEN** 系统返回成功且保持 Cookie 已清除

#### Scenario: 上游退出不可用
- **WHEN** 上游退出调用超时或不可达
- **THEN** 系统返回对应稳定上游错误并仍清除浏览器 Cookie，响应不泄露原始会话
