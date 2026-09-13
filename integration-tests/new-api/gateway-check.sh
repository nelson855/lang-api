#!/usr/bin/env bash
# 网关静态校验（LANG-P1-07 任务 2.3/2.4/3.1：P1-02 的“Relay 保持关闭”已由精确白名单替代）
# 检查 gateway/nginx.conf：用户站点只代理 Lang API；模型站点仅两条精确路径转发 New API Relay。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$NGINX" ]] || { fail "缺失文件: $NGINX"; exit 1; }
pass "存在文件: $NGINX"

# 必须代理 lang-api 页面与 /portal/api/
grep -q "location /portal/api/" "$NGINX" || fail "缺少 /portal/api/ 声明"
grep -q "lang-api:8080" "$NGINX" || fail "缺少到 lang-api:8080 的转发"
grep -q "location = /healthz" "$NGINX" || grep -q "location /healthz" "$NGINX" || fail "缺少 /healthz 健康端点"

# 用户站点与模型站点的管理/未开放前缀必须返回自有 404
for p in "/v1beta/" "/api/" "/setup/"; do
  if grep -A3 "location $p" "$NGINX" | grep -q "return 404"; then
    pass "关闭路径 $p 返回自有 404"
  else
    fail "路径 $p 应显式 return 404"
  fi
done

# 指向 Relay 的转发必须恰好两条（两条精确路径经单一 upstream），且 upstream 只有一个 new-api 节点
COUNT=$(grep -v "^[[:space:]]*#" "$NGINX" | grep -c "proxy_pass http://new_api_relay" || true)
if [[ "$COUNT" -eq 2 ]]; then
  pass "恰好两条 Relay 转发（两条精确路径）"
else
  fail "Relay 转发应恰好两条，实际=$COUNT"
fi
if grep -q "upstream new_api_relay" "$NGINX" \
  && grep -E "server[[:space:]]+new-api:3000" "$NGINX" | grep -vq "backup"; then
  pass "单一 Relay upstream 直达 new-api:3000"
else
  fail "应声明直达 new-api:3000 的单一 upstream（无备用节点）"
fi
for loc in "chat/completions" "models"; do
  pat="location = [/]v1[/]$loc"
  if awk -v pat="$pat" '$0 ~ pat,0 { if ($0 ~ pat) inside=1; if (inside && /proxy_pass http:\/\/new_api_relay/) found=1; if (inside && /^  \}/) exit } END { exit !found }' "$NGINX"; then
    pass "精确路径 /v1/$loc 转发到 Relay"
  else
    fail "精确路径 /v1/$loc 应转发到 Relay"
  fi
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "网关静态校验：未通过"
  exit 1
fi
echo "网关静态校验：通过"
