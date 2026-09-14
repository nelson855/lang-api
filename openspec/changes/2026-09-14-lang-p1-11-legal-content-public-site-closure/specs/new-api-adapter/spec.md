# new-api-adapter Specification Delta

## ADDED Requirements

### Requirement: 冻结版法律正文只读适配
New API 适配器 SHALL 为 v0.13.2 提供用户协议和隐私政策两个匿名只读操作，分别固定调用 `GET /api/user-agreement` 与 `GET /api/privacy-policy`。操作 MUST 不发送浏览器 Cookie、`Authorization`、`New-Api-User` 或任意透传 Header，只允许公共操作白名单中的 `Accept` 和统一 `X-Request-Id`。

#### Scenario: 查询用户协议
- **WHEN** 法律内容服务请求用户协议
- **THEN** 适配器只调用 `GET /api/user-agreement` 并提取成功包装中的字符串 `data`

#### Scenario: 查询隐私政策
- **WHEN** 法律内容服务请求隐私政策
- **THEN** 适配器只调用 `GET /api/privacy-policy` 并提取成功包装中的字符串 `data`

#### Scenario: 浏览器携带认证信息
- **WHEN** 匿名法律请求携带 Cookie、Authorization 或代理 Header
- **THEN** 上游法律操作不收到这些值，且调用行为与匿名请求一致

### Requirement: 法律正文响应最小转换
法律正文适配 SHALL 只接受成功包装中的字符串 `data`，忽略未知字段，并把业务失败、非 JSON、非字符串或缺失字段转换为稳定上游错误。适配器不得解释、净化、缓存或记录正文；原始上游 `message`、品牌、Header 和响应包装不得离开适配边界。

#### Scenario: 响应包含未知字段
- **WHEN** 上游成功响应在字符串正文之外增加版本、站点名或其他字段
- **THEN** 适配器仅返回正文字符串，新增字段不会进入业务层

#### Scenario: 上游返回非法正文类型
- **WHEN** `data` 缺失、为对象或其他非字符串类型
- **THEN** 适配器返回安全 `UPSTREAM_ERROR`，不把原始响应交给调用方

#### Scenario: 上游业务失败
- **WHEN** 上游以 HTTP 200 和 `success=false` 返回消息
- **THEN** 适配器转换为稳定错误且不透传原始消息
