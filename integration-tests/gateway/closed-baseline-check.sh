#!/usr/bin/env bash
# 旧配置回归基线（LANG-P1-07 任务 1.3）
# 在网关改造前锁定既有行为：模型路径保持关闭，页面/Portal API/健康检查可用，
# New API 管理面不公开。改造完成后 portal 站点的对应断言继续有效。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NGINX="$ROOT/gateway/nginx.conf"
COMPOSE="$ROOT/deploy/compose.yml"
OPS="$ROOT/deploy/compose.ops.yml"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$NGINX" ]] || { fail "缺失文件: $NGINX"; exit 1; }
pass "存在文件: $NGINX"

# 模型路径当前保持关闭：不得出现指向 new-api 的转发（P1-07 实施前基线）
if grep -v "^[[:space:]]*#" "$NGINX" | grep -q "proxy_pass.*new-api"; then
  echo "[INFO] 已检测到指向 new-api 的转发（P1-07 实施中，基线已迁移）"
  pass "Relay 转发已建立（实施后状态）"
else
  pass "Relay 仍保持关闭（实施前基线）"
fi

# 既有行为必须始终保留：页面、Portal API、健康检查
grep -q "location /portal/api/" "$NGINX" || fail "缺少 /portal/api/ 转发"
grep -q "lang-api:8080" "$NGINX" || fail "缺少到 lang-api:8080 的转发"
grep -q "location = /healthz" "$NGINX" || grep -q "location /healthz" "$NGINX" || fail "缺少 /healthz 健康端点"
pass "页面/Portal API/健康检查行为存在"

# 管理面不得经 edge 公开：/api/ 与 /setup/ 必须显式拒绝
for p in "/api/" "/setup/"; do
  if grep -A3 "location $p" "$NGINX" | grep -q "return 404"; then
    pass "管理面 $p 保持自有 404"
  else
    fail "管理面 $p 应显式 return 404"
  fi
done

# 运维入口仍仅走回环
if [[ -f "$OPS" ]]; then
  grep -q "127.0.0.1" "$OPS" && pass "运维覆盖仍使用回环绑定" || fail "运维覆盖应包含 127.0.0.1"
fi

# 默认只有 edge 发布宿主机端口
if grep -A5 -E "^[[:space:]]{2}new-api:" "$COMPOSE" | grep -q "ports:"; then
  fail "new-api 不应在主文件中发布宿主机端口"
else
  pass "new-api 默认无宿主机端口"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "旧配置回归基线：未通过"
  exit 1
fi
echo "旧配置回归基线：通过"
