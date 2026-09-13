#!/usr/bin/env bash
# LANG-P1-07 部署预检（任务 6.8）：在发布 OPENAI Base URL 前，先证明网关契约通过、
# 两个 server name 非空且不同、公开 URL 与模型主机一致并以 /v1 结束、
# 未引用 New API 私网地址与运维端口。任一门禁失败即非零退出，阻止发布。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${1:-$ROOT/deploy/.env}"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$ENV_FILE" ]] || { fail "缺少环境文件: $ENV_FILE（可从 deploy/.env.example 复制）"; exit 1; }
pass "环境文件: $ENV_FILE"

# 只读取需要的变量（不输出任何值，避免密钥泄露）
PORTAL_SERVER_NAME="$(grep -E "^PORTAL_SERVER_NAME=" "$ENV_FILE" | cut -d= -f2- || true)"
MODEL_API_SERVER_NAME="$(grep -E "^MODEL_API_SERVER_NAME=" "$ENV_FILE" | cut -d= -f2- || true)"
PORTAL_ENABLED_PROTOCOLS="$(grep -E "^PORTAL_ENABLED_PROTOCOLS=" "$ENV_FILE" | cut -d= -f2- || true)"
PORTAL_OPENAI_URL="$(grep -E "^PORTAL_OPENAI_URL=" "$ENV_FILE" | cut -d= -f2- || true)"
EDGE_HTTP_PORT="$(grep -E "^EDGE_HTTP_PORT=" "$ENV_FILE" | cut -d= -f2- || true)"

# 两个主机非空且不同
if [[ -z "$PORTAL_SERVER_NAME" || -z "$MODEL_API_SERVER_NAME" ]]; then
  fail "PORTAL_SERVER_NAME 与 MODEL_API_SERVER_NAME 均不能为空"
else
  pass "两个 server name 非空"
fi
if [[ "$PORTAL_SERVER_NAME" == "$MODEL_API_SERVER_NAME" ]]; then
  fail "两个 server name 不得相同"
else
  pass "两个 server name 不同"
fi

# 启用协议当前只能是 OPENAI（或空，即不发布）
if [[ -n "$PORTAL_ENABLED_PROTOCOLS" && "$PORTAL_ENABLED_PROTOCOLS" != "OPENAI" ]]; then
  fail "PORTAL_ENABLED_PROTOCOLS 当前只能是 OPENAI（P1-07 仅验证该协议）"
else
  pass "启用协议合法"
fi

if [[ -n "$PORTAL_ENABLED_PROTOCOLS" ]]; then
  # URL 必须以 /v1 结束
  if [[ "$PORTAL_OPENAI_URL" != */v1 ]]; then
    fail "PORTAL_OPENAI_URL 必须以 /v1 结束"
  else
    pass "公开 URL 以 /v1 结束"
  fi
  # host 必须与模型主机一致
  URL_HOST="$(echo "$PORTAL_OPENAI_URL" | sed -E 's|^[a-zA-Z]+://([^/:]+).*|\1|')"
  if [[ "$URL_HOST" != "$MODEL_API_SERVER_NAME" ]]; then
    fail "公开 URL host（$URL_HOST）必须与模型主机一致"
  else
    pass "公开 URL host 与模型主机一致"
  fi
  # 不得引用 New API 私网地址与运维端口
  if echo "$PORTAL_OPENAI_URL" | grep -qE "new-api|127\.0\.0\.1:13000|:${NEW_API_ADMIN_PORT:-13000}"; then
    fail "公开 URL 不得引用 New API 私网地址或运维端口"
  else
    pass "公开 URL 无私网引用"
  fi
  # 非本地域名必须 https（本地联调用 http 即可）
  URL_SCHEME="$(echo "$PORTAL_OPENAI_URL" | sed -E 's|^([a-zA-Z]+)://.*|\1|')"
  if [[ "$URL_SCHEME" != "https" ]] && [[ "$URL_HOST" != "localhost" && "$URL_HOST" != api.localhost && "$URL_HOST" != "127.0.0.1" ]]; then
    fail "非本地域名的公开 URL 必须使用 https"
  else
    pass "公开 URL scheme 合法"
  fi
else
  pass "未启用公开协议，跳过 URL 门禁（网关仍需通过契约检查）"
fi

# 网关契约未通过时阻止发布
for check in "integration-tests/gateway/fixture-check.sh" \
             "integration-tests/gateway/route-header-check.sh" \
             "integration-tests/gateway/image-vhost-check.sh" \
             "integration-tests/gateway/resource-check.sh" \
             "integration-tests/gateway/error-log-check.sh" \
             "integration-tests/gateway/compose-env-check.sh" \
             "integration-tests/gateway/closed-baseline-check.sh" \
             "integration-tests/new-api/gateway-check.sh" \
             "integration-tests/new-api/compose-check.sh"; do
  if bash "$ROOT/$check" >/dev/null 2>&1; then
    pass "契约通过: $(basename "$check")"
  else
    fail "契约未通过: $check"
  fi
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "部署预检：未通过，阻止发布 OPENAI"
  exit 1
fi
echo "部署预检：通过"
