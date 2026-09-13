#!/usr/bin/env bash
# 精确路由与请求边界检查（LANG-P1-07 任务 3.1-3.5）
# 校验：两条 exact location 直达 new-api:3000；方法门禁自有 405；严格 Bearer 401；
# 关闭默认转发后逐项重建；敏感/伪造 Header 不透传。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$NGINX" ]] || { fail "缺失文件: $NGINX"; exit 1; }

# 3.1 两条 exact location 经单一 upstream 直达 new-api:3000
for loc in "location = /v1/chat/completions" "location = /v1/models"; do
  if grep -q "$loc" "$NGINX"; then
    pass "存在精确路径: $loc"
  else
    fail "缺少精确路径: $loc"
  fi
done
if grep -q "upstream new_api_relay" "$NGINX" \
  && grep -E "server[[:space:]]+new-api:3000" "$NGINX" | grep -vq "backup" \
  && grep -q "proxy_pass http://new_api_relay" "$NGINX"; then
  pass "精确路径经单一 upstream 直达 new-api:3000"
else
  fail "精确路径应经单一 upstream 直达 new-api:3000（无备用节点）"
fi
# 不得使用宽泛正则聚合 /v1/
if grep -qE "location ~.*v1/\(chat" "$NGINX"; then
  fail "不得使用正则聚合白名单路径"
else
  pass "无正则聚合白名单"
fi

# 3.2 方法门禁：白名单路径错误方法返回自有 405，且 OPTIONS 同样拒绝
if grep -q "return 405" "$NGINX"; then
  pass "存在自有 405 方法门禁"
else
  fail "缺少 return 405 方法门禁"
fi
# 聊天路径仅 POST，模型列表仅 GET（通过方法条件体现）
grep -q 'request_method' "$NGINX" || fail '缺少基于 $request_method 的方法门禁'
pass "方法门禁基于 request_method"

# 3.3 严格 Bearer 检查：缺失/空/ malformed 在 edge 直接 401
if grep -q "return 401" "$NGINX"; then
  pass "存在自有 401 鉴权门禁"
else
  fail "缺少 return 401 鉴权门禁"
fi
if grep -q "http_authorization" "$NGINX" && grep -q "Bearer" "$NGINX"; then
  pass "Bearer 格式检查存在"
else
  fail "缺少基于 Authorization 的 Bearer 格式检查"
fi

# 3.4 关闭默认转发后逐项重建
grep -q "proxy_pass_request_headers off" "$NGINX" || fail "必须设置 proxy_pass_request_headers off"
pass "已关闭默认请求 Header 转发"
for h in "Authorization" "Content-Type" "Accept" "X-Request-Id"; do
  if grep -q "$h" "$NGINX"; then
    pass "重建 Header: $h"
  else
    fail "缺少重建 Header: $h"
  fi
done
# requestId 必须由网关生成（$request_id），不得透传客户端值
if grep -q 'proxy_set_header X-Request-Id \$request_id' "$NGINX"; then
  pass "X-Request-Id 由网关生成"
else
  fail 'X-Request-Id 必须使用 $request_id 生成'
fi
# Host 重建为内部 Relay host，不得透传客户端 Host
if grep -q "proxy_set_header Host" "$NGINX"; then
  pass "Host 已重建"
else
  fail "缺少 Host 重建"
fi

# 3.5 敏感/伪造 Header 不得透传：模板中不得出现整包转发或放行清单外的扩展 Header
if grep -q "proxy_pass_request_headers on" "$NGINX"; then
  fail "不得开启整包 Header 转发"
else
  pass "无整包 Header 转发"
fi
# 不得透传 Cookie / X-Forwarded-* / Forwarded / New-Api-User 作为上游身份
for bad in 'proxy_set_header Cookie' 'proxy_set_header X-Forwarded-For $http_x_forwarded_for' 'proxy_set_header Forwarded' 'proxy_set_header New-Api-User'; do
  if grep -q "$bad" "$NGINX"; then
    fail "不得透传敏感/伪造 Header: $bad"
  fi
done
pass "敏感/伪造 Header 未透传"

if [[ "$FAIL" -ne 0 ]]; then
  echo "精确路由与请求边界检查：未通过"
  exit 1
fi
echo "精确路由与请求边界检查：通过"
