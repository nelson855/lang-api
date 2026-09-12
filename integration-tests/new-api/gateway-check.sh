#!/usr/bin/env bash
# 网关失败场景静态校验（LANG-P1-02 任务 3.3/3.4）
# 检查 gateway/nginx.conf：只代理 Lang API，不转发 New API 管理与 Relay 路径。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$NGINX" ]] || { fail "缺失文件: $NGINX"; exit 1; }
pass "存在文件: $NGINX"

# 必须代理 lang-api 页面与 /portal/api/
grep -q "location /portal/api/" "$NGINX" || fail "缺少 /portal/api/ 转发"
grep -q "lang-api:8080" "$NGINX" || fail "缺少到 lang-api:8080 的转发"
grep -q "location = /healthz" "$NGINX" || grep -q "location /healthz" "$NGINX" || fail "缺少 /healthz 健康端点"

# 管理与 Relay 路径必须返回自有 404，不得 proxy_pass 到 new-api
for p in "/v1/" "/v1beta/" "/api/" "/setup/"; do
  if grep -A3 "location $p" "$NGINX" | grep -q "return 404"; then
    pass "关闭路径 $p 返回自有 404"
  else
    fail "路径 $p 应显式 return 404"
  fi
done

# 整个文件除注释外，不允许出现指向 new-api 的 proxy_pass
if grep -v "^[[:space:]]*#" "$NGINX" | grep -q "proxy_pass.*new-api"; then
  fail "P1-02 阶段不得将任何路径 proxy_pass 到 new-api"
else
  pass "无指向 new-api 的转发"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "网关静态校验：未通过"
  exit 1
fi
echo "网关静态校验：通过"
