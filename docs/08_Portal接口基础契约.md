# Portal API 基础契约（LANG-P1-03）

## 响应包装

- 成功：`{requestId, data}`，分页 `data` 为 `{items, page, pageSize, total}`，`page` 从 1 开始，`pageSize` 正整数，`total` 非负。
- 失败：`{requestId, error:{code, message}}`，不含 `success`、`message` 原始上游包装、堆栈、私网地址、渠道 ID、节点名、数据库错误。
- 所有 `/portal/api/**` 响应头均回传 `X-Request-Id`，与响应体 `requestId` 一致。

## requestId 规则

- 客户端可经 `X-Request-Id` 提供，仅当长度 8–64 且仅含 ASCII 字母、数字、`.`、`_`、`:`、`-` 时采用。
- 缺失或非法时生成 `req_` + 无连字符 UUID。
- 最终标识写入请求属性与日志 `requestId`，透传给实际发生的上游调用，不记录原非法值。

## 错误码与 HTTP 映射

| 错误码 | HTTP | 说明 |
|---|---|---|
| INVALID_ARGUMENT | 400 | 参数、路径、JSON 校验失败，只提示公开字段 |
| UNAUTHENTICATED | 401 | 需认证接口未登录，JSON 返回，不重定向 HTML |
| FORBIDDEN | 403 | 已登录但无权限 |
| NOT_FOUND | 404 | 未知 Portal 路径，不回退 `index.html`，不请求 New API |
| METHOD_NOT_ALLOWED | 405 | 已知路径错误方法，不请求 New API |
| PAYLOAD_TOO_LARGE | 413 | 正文超 1 MiB，有无 `Content-Length` 均生效，仅作用 `/portal/api/**` |
| UPSTREAM_ERROR | 502 | 上游非 2xx、`success=false`、解析失败等，未明确映射时默认 |
| UPSTREAM_UNAVAILABLE | 503 | 建连失败、连接池获取超时，不暴露目标地址 |
| UPSTREAM_TIMEOUT | 504 | 读写超时，不暴露目标地址 |
| INTERNAL_ERROR | 500 | 未知异常，不返回异常消息原文 |

面向客户端的消息由错误码或显式安全消息产生，不使用 `exception.getMessage()`。

## public-config 示例

`GET /portal/api/public-config` 匿名可访问，`Cache-Control: no-store`。

```json
{
  "requestId": "req_xxx",
  "data": {
    "siteName": "Lang API",
    "apiBaseUrls": [
      {"protocol": "OPENAI", "url": "https://api.example.com/v1"}
    ]
  }
}
```

- `protocol` 固定大写，顺序 `OPENAI`、`ANTHROPIC`、`GEMINI`，只返回已启用且合法的项，未启用时为空数组。
- 只来源于 Lang API 自有 `.properties`，不请求 New API `/api/status`，不返回版本、内部地址、系统名、OAuth、倍率、第三方模板等字段。

## 公开字段

- 仅 `siteName`、`apiBaseUrls[{protocol,url}]` 公开，其余 New API 字段默认不公开。
- 校验失败只返回首个公开字段的安全提示，不返回约束类名、Java 类型、堆栈。

## 后续适配接入约束

- 所有 New API 调用、路径、DTO 只在 `upstream.newapi` 内，业务经语义化操作调用，不拼接路径、不引用上游 DTO、不接受任意绝对 URL。
- 请求 Header 从空集合重建，仅 `Accept`、`Content-Type`、`X-Request-Id`，认证操作另加声明的会话 Cookie 与 `New-Api-User`；不批量透传浏览器 Header。
- 响应 Header 默认丢弃，会话 Cookie 移除 Domain、Path 固定 `/portal`、`HttpOnly`、`SameSite=Lax`，`Secure` 按环境（prod 强制 true）。
- 传输禁用自动重试，写操作最多一次上游请求；有界池默认 50 连接/100 pending，获取 1 秒、建连 2 秒、读写 5 秒。
- 新增操作必须复用 `NewApiExchange` 与契约基座，断言方法、路径、Header、字段裁剪、错误和请求次数。
- 非公开 Controller 必须声明统一保护注解，公开 Controller 仅白名单。
