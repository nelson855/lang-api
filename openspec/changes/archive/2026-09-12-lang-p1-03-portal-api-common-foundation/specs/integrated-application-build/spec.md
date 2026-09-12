## MODIFIED Requirements

### Requirement: 单页应用路由回退边界
应用 SHALL 对非文件型前端页面路径返回 `index.html` 以支持客户端路由刷新；该回退 MUST 排除 `/portal/api/*`、`/actuator/*` 和静态资源请求。不存在的 Portal API MUST 返回带当前 `requestId` 的统一 JSON `NOT_FOUND`/404，已声明 Portal API 的不支持方法 MUST 返回统一 JSON `METHOD_NOT_ALLOWED`/405，两者均不得回退页面或使用 Spring Boot 默认错误页面。

#### Scenario: 根页面可访问
- **WHEN** 客户端请求 `/`
- **THEN** 应用返回 React 应用入口页面

#### Scenario: 刷新前端路由
- **WHEN** 客户端直接请求一个不存在服务端映射的前端页面路径，例如 `/dashboard`
- **THEN** 应用返回 React 应用入口页面而不是服务器 404

#### Scenario: 未知 Portal API 不回退页面
- **WHEN** 客户端请求不存在的 `/portal/api/test`
- **THEN** 应用返回带 `requestId` 的统一 JSON `NOT_FOUND`/404，响应体 MUST NOT 是 `index.html` 或默认错误页面

#### Scenario: Portal API 方法不匹配
- **WHEN** 客户端以未声明的方法请求一个已存在的 `/portal/api/*` 路径
- **THEN** 应用返回带 `requestId` 的统一 JSON `METHOD_NOT_ALLOWED`/405，响应体 MUST NOT 是 `index.html` 或默认错误页面

#### Scenario: 未知 Actuator 路径不回退页面
- **WHEN** 客户端请求不存在的 `/actuator/not-found`
- **THEN** 应用返回非页面回退响应，响应体 MUST NOT 是 `index.html`
