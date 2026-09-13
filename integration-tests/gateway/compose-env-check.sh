#!/usr/bin/env bash
# Compose 与环境配置检查（LANG-P1-07 任务 6.1-6.8）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="$ROOT/deploy/compose.yml"
ENV_EXAMPLE="$ROOT/deploy/.env.example"
PREFLIGHT="$ROOT/deploy/preflight.sh"
APP="$ROOT/portal-api/src/main/resources/application.properties"
DEV="$ROOT/portal-api/src/main/resources/application-dev.properties"
TEST_PROPS="$ROOT/portal-api/src/main/resources/application-test.properties"
PROD="$ROOT/portal-api/src/main/resources/application-prod.properties"
VALIDATOR="$ROOT/portal-api/src/main/java/com/lang/portal/config/PortalPropertiesValidator.java"
VALIDATOR_TEST="$ROOT/portal-api/src/test/java/com/lang/portal/config/PortalPropertiesValidatorTests.java"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

# 6.1 edge 使用自有镜像构建，只发布 edge 端口，edge 经 relay 访问 New API
grep -q "context: ../gateway" "$COMPOSE" \
  && pass "edge 由 gateway 目录构建" \
  || fail "edge 应从 ../gateway 构建自有镜像"
grep -A25 "edge-nginx:" "$COMPOSE" | grep -q "PORTAL_SERVER_NAME" \
  && pass "edge 注入 PORTAL_SERVER_NAME" \
  || fail "edge 缺少 PORTAL_SERVER_NAME 模板变量"
grep -A25 "edge-nginx:" "$COMPOSE" | grep -q "MODEL_API_SERVER_NAME" \
  && pass "edge 注入 MODEL_API_SERVER_NAME" \
  || fail "edge 缺少 MODEL_API_SERVER_NAME 模板变量"
if grep -q "gateway/nginx.conf" "$COMPOSE"; then
  fail "Compose 不得再挂载宿主机网关配置覆盖镜像行为"
else
  pass "无宿主机网关配置挂载"
fi

# 6.2 环境模板声明非敏感网关变量
for v in "PORTAL_SERVER_NAME" "MODEL_API_SERVER_NAME" "PORTAL_OPENAI_URL" "GATEWAY_CLIENT_MAX_BODY" "GATEWAY_CONNECT_TIMEOUT" "GATEWAY_SEND_TIMEOUT" "GATEWAY_READ_TIMEOUT" "GATEWAY_RATE_LIMIT" "GATEWAY_RATE_BURST" "GATEWAY_CONN_LIMIT"; do
  grep -q "$v" "$ENV_EXAMPLE" \
    && pass "模板声明 $v" \
    || fail "模板缺少 $v"
done

# 6.3 共享入口保留协议配置键
grep -q "lang.portal.enabled-protocols" "$APP" \
  && pass "共享入口保留协议键" \
  || fail "application.properties 缺少 lang.portal.enabled-protocols"

# 6.4 dev 允许注入本地 OPENAI 与 api.localhost 地址
grep -q "PORTAL_ENABLED_PROTOCOLS" "$DEV" \
  && pass "dev 可注入协议" \
  || fail "dev 缺少 PORTAL_ENABLED_PROTOCOLS 注入"
grep -q "api.localhost" "$DEV" \
  && pass "dev 默认本地测试域名" \
  || fail "dev 缺少 api.localhost 默认地址"

# 6.5 test 默认不发布真实地址
if grep -qE "^lang.portal.enabled-protocols=$" "$TEST_PROPS" \
  && grep -qE "^lang.portal.public-urls.openai=$" "$TEST_PROPS"; then
  pass "test 默认不发布地址"
else
  fail "test 应默认保持协议关闭且地址为空"
fi

# 6.6 prod 默认关闭，由部署显式提供
if grep -q "PORTAL_ENABLED_PROTOCOLS" "$PROD" && grep -q "PORTAL_OPENAI_URL" "$PROD"; then
  pass "prod 由部署显式提供"
else
  fail "prod 缺少部署变量注入"
fi

# 6.7 只接受 OPENAI 且 URL 以 /v1 结束
grep -q 'Set.of("OPENAI")' "$VALIDATOR" \
  && pass "校验只接受 OPENAI" \
  || fail "校验器应只接受 OPENAI"
grep -q '/v1' "$VALIDATOR" \
  && pass "校验要求 /v1 后缀" \
  || fail "校验器应要求 URL 以 /v1 结束"
grep -q "onlyOpenaiProtocolAcceptedInP107" "$VALIDATOR_TEST" \
  && grep -q "openaiUrlMustEndWithV1" "$VALIDATOR_TEST" \
  && pass "P1-07 校验测试存在" \
  || fail "缺少 P1-07 校验测试"

# 6.8 部署预检存在且覆盖关键门禁
[[ -x "$PREFLIGHT" ]] \
  && pass "预检脚本可执行" \
  || fail "缺少可执行的 deploy/preflight.sh"
for kw in "PORTAL_SERVER_NAME" "MODEL_API_SERVER_NAME" "PORTAL_OPENAI_URL" "/v1" "new-api"; do
  grep -q "$kw" "$PREFLIGHT" \
    && pass "预检覆盖: $kw" \
    || fail "预检缺少门禁: $kw"
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "Compose 与环境配置检查：未通过"
  exit 1
fi
echo "Compose 与环境配置检查：通过"
