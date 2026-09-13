#!/usr/bin/env bash
# 错误、响应 Header 与日志检查（LANG-P1-07 任务 5.1-5.6）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$NGINX" ]] || { fail "缺失文件: $NGINX"; exit 1; }

# 5.1 自有错误走 error_page 命名位置，统一 {requestId,error:{code,message,type}} JSON
# 401/404 的自有分支用带正文的直接返回（天然绕过 error_page），
# error_page 同码目标只承接上游拦截；405 自有分支同样直接返回，上游 405 透传。
for code in "401" "404" "413" "429"; do
  grep -q "error_page.*$code" "$NGINX" \
    && pass "错误 $code 走 error_page" \
    || fail "缺少 error_page $code 映射"
done
grep -q "return 405" "$NGINX" \
  && pass "自有 405 直接返回" \
  || fail "缺少自有 405 返回"
grep -q "proxy_intercept_errors on" "$NGINX" \
  && pass "拦截上游错误正文" \
  || fail "缺少 proxy_intercept_errors on"
for code in " 400" " 401" " 403" " 429" " 502" " 503" " 504"; do
  grep -q "error_page.*$code" "$NGINX" \
    && pass "拦截上游 $code" \
    || fail "缺少上游 $code 拦截映射"
done
grep -q 'type":"gateway_error' "$NGINX" \
  && pass "错误 type 为 gateway_error" \
  || fail "错误正文缺少 type gateway_error"
grep -q '"requestId":"$request_id"' "$NGINX" \
  && pass "错误携带网关 requestId" \
  || fail "错误正文缺少网关 requestId"
grep -q "default_type application/json" "$NGINX" \
  && pass "错误默认 JSON 内容类型" \
  || fail "缺少 default_type application/json"
grep -q "charset utf-8" "$NGINX" \
  && pass "错误使用 UTF-8" \
  || fail "缺少 charset utf-8"

# 5.2 丢弃上游原始正文与默认 HTML，不含品牌与内部信息
if grep -qi "new_api_error\|one_api\|New-Api-Channel\|new-api:3000.*message" "$NGINX"; then
  fail "错误模板不得包含上游品牌或私网地址"
else
  pass "错误模板无品牌无私网地址"
fi
if awk '/location @/ {inside=1} inside && /proxy_pass/ {bad=1} inside && /^  \}/ {inside=0} END {exit bad+0}' "$NGINX"; then
  pass "错误命名位置不转发上游"
else
  fail "错误命名位置不得再转发上游"
fi

# 5.3 已开始的流不改写：不得引入正文改写模块
for mod in "sub_filter" "njs_" "lua_"; do
  if grep -q "$mod" "$NGINX"; then
    fail "不得引入正文改写能力: $mod"
  fi
done
pass "无 SSE 改写能力"

# 5.4 响应 Header：覆盖 X-Request-Id 与 no-store，移除已知标识，不开 CORS
grep -q 'add_header X-Request-Id \$request_id always' "$NGINX" \
  && pass "覆盖响应 X-Request-Id" \
  || fail "缺少 add_header X-Request-Id"
grep -q 'add_header Cache-Control "no-store" always' "$NGINX" \
  && pass "响应不缓存" \
  || fail "缺少 Cache-Control no-store"
for h in "Server" "ETag" "Age" "Via" "X-Cache" "X-Request-Id" "New-Api-User"; do
  grep -q "proxy_hide_header $h" "$NGINX" \
    && pass "隐藏上游 Header: $h" \
    || fail "缺少 proxy_hide_header $h"
done
grep -q "server_tokens off" "$NGINX" \
  && pass "隐藏版本号" \
  || fail "缺少 server_tokens off"
if grep -q "Access-Control-Allow-Origin" "$NGINX"; then
  fail "不得启用浏览器 CORS"
else
  pass "无 CORS 头"
fi

# 5.5 模型数据面 JSON 访问日志：只记录受控字段
grep -q "log_format model_json" "$NGINX" \
  && pass "模型日志格式存在" \
  || fail "缺少 log_format model_json"
for v in '$time_iso8601' '$request_id' '$request_method' '$model_route' '$status' '$request_time' '$upstream_header_time' '$upstream_response_time' '$gateway_result'; do
  grep -q "log_format model_json" "$NGINX" \
    && awk "/log_format model_json/,/;/" "$NGINX" | grep -qF "$v" \
    && pass "日志字段: $v" \
    || fail "日志缺少字段: $v"
done
grep -q 'set \$model_route' "$NGINX" \
  && pass "规范化路由变量存在" \
  || fail "缺少 set \$model_route"
grep -q 'access_log .* model_json' "$NGINX" \
  && pass "模型日志已挂载" \
  || fail "缺少 access_log model_json 挂载"
grep -q "gateway_result" "$NGINX" \
  && pass "安全结果类别映射存在" \
  || fail "缺少 gateway_result 映射"

# 5.6 日志不记录敏感信息；error log 保持 warning 级
FORMAT="$(awk "/log_format model_json/,/;/" "$NGINX")"
for bad in '\$request_uri' '\$http_authorization' '\$http_cookie' '\$remote_addr' '\$upstream_addr' '\$http_x_request_id'; do
  if echo "$FORMAT" | grep -q "$bad"; then
    fail "日志格式不得记录: $bad"
  fi
done
# 先剔除允许的 request_* 变量，再检查是否残留原始请求行与查询串
CLEAN="$(echo "$FORMAT" | sed -E 's/\$request_(method|time|id)//g')"
if echo "$CLEAN" | grep -qE '\$request([^_a-zA-Z]|$)'; then
  fail "日志格式不得记录原始 \$request"
else
  pass "日志无原始请求行"
fi
if echo "$FORMAT" | grep -qE '\$args([^_a-zA-Z]|$)'; then
  fail "日志格式不得记录查询串"
else
  pass "日志无查询串"
fi
pass "日志无凭证与地址"
grep -qE "error_log .* warn" "$NGINX" \
  && pass "error log 为 warning 级" \
  || fail "error_log 应保持 warning 级"

if [[ "$FAIL" -ne 0 ]]; then
  echo "错误响应与日志检查：未通过"
  exit 1
fi
echo "错误响应与日志检查：通过"
