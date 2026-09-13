#!/usr/bin/env bash
# 流式传输与资源保护检查（LANG-P1-07 任务 4.1-4.5）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NGINX="$ROOT/gateway/nginx.conf"
DOCKERFILE="$ROOT/gateway/Dockerfile"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$NGINX" ]] || { fail "缺失文件: $NGINX"; exit 1; }
[[ -f "$DOCKERFILE" ]] || { fail "缺失文件: $DOCKERFILE"; exit 1; }

# 4.1 10 MiB 请求正文上限 + 请求缓冲（超限在到达 Relay 前 413）
grep -q 'client_max_body_size ${GATEWAY_CLIENT_MAX_BODY}' "$NGINX" \
  && pass "请求正文上限走模板变量" \
  || fail "缺少 client_max_body_size \${GATEWAY_CLIENT_MAX_BODY}"
grep -q "GATEWAY_CLIENT_MAX_BODY=10m" "$DOCKERFILE" \
  && pass "正文上限默认 10m" \
  || fail "Dockerfile 缺少 GATEWAY_CLIENT_MAX_BODY=10m 默认值"
grep -q "proxy_request_buffering on" "$NGINX" \
  && pass "请求缓冲开启" \
  || fail "缺少 proxy_request_buffering on"

# 4.2 建连 3s / 发送 60s / 读取 600s，数值走模板变量
grep -q 'proxy_connect_timeout ${GATEWAY_CONNECT_TIMEOUT}' "$NGINX" \
  && pass "建连超时走模板变量" \
  || fail "缺少 proxy_connect_timeout 模板变量"
grep -q 'proxy_send_timeout ${GATEWAY_SEND_TIMEOUT}' "$NGINX" \
  && pass "发送超时走模板变量" \
  || fail "缺少 proxy_send_timeout 模板变量"
grep -q 'proxy_read_timeout ${GATEWAY_READ_TIMEOUT}' "$NGINX" \
  && pass "读取超时走模板变量" \
  || fail "缺少 proxy_read_timeout 模板变量"
for d in "GATEWAY_CONNECT_TIMEOUT=3s" "GATEWAY_SEND_TIMEOUT=60s" "GATEWAY_READ_TIMEOUT=600s"; do
  grep -q "$d" "$DOCKERFILE" && pass "默认值 $d" || fail "Dockerfile 缺少默认值 $d"
done

# 4.3 单地址 10r/s burst 20 + 并发 20，超限自有 429
grep -q 'limit_req_zone .* rate=${GATEWAY_RATE_LIMIT}' "$NGINX" \
  && pass "速率 zone 走模板变量" \
  || fail "缺少 limit_req_zone 模板变量"
grep -q 'limit_req zone=.* burst=${GATEWAY_RATE_BURST} nodelay' "$NGINX" \
  && pass "burst 走模板变量" \
  || fail "缺少 limit_req burst 模板变量"
grep -q 'limit_conn .* ${GATEWAY_CONN_LIMIT}' "$NGINX" \
  && pass "并发上限走模板变量" \
  || fail "缺少 limit_conn 模板变量"
grep -q "limit_req_status 429" "$NGINX" \
  && pass "超速率返回 429" \
  || fail "缺少 limit_req_status 429"
grep -q "limit_conn_status 429" "$NGINX" \
  && pass "超并发返回 429" \
  || fail "缺少 limit_conn_status 429"
for d in "GATEWAY_RATE_LIMIT=10r/s" "GATEWAY_RATE_BURST=20" "GATEWAY_CONN_LIMIT=20"; do
  grep -q "$d" "$DOCKERFILE" && pass "默认值 $d" || fail "Dockerfile 缺少默认值 $d"
done

# 4.4 禁用代理缓冲、缓存、压缩；HTTP/1.1 上游连接
grep -q "proxy_buffering off" "$NGINX" \
  && pass "禁用代理缓冲" \
  || fail "缺少 proxy_buffering off"
grep -q "proxy_cache off" "$NGINX" \
  && pass "禁用代理缓存" \
  || fail "缺少 proxy_cache off"
grep -q "gzip off" "$NGINX" \
  && pass "禁用压缩" \
  || fail "缺少 gzip off"
grep -q "proxy_http_version 1.1" "$NGINX" \
  && pass "HTTP/1.1 上游连接" \
  || fail "缺少 proxy_http_version 1.1"

# 4.5 取消向上传播、零重试、单一 upstream、无备用节点
grep -q "proxy_ignore_client_abort off" "$NGINX" \
  && pass "客户端取消向上传播" \
  || fail "缺少 proxy_ignore_client_abort off"
grep -q "proxy_next_upstream off" "$NGINX" \
  && pass "禁用上游重试" \
  || fail "缺少 proxy_next_upstream off"
grep -q "upstream new_api_relay" "$NGINX" \
  && pass "单一 Relay upstream" \
  || fail "缺少 upstream new_api_relay"
if grep -E "server[[:space:]]+new-api" "$NGINX" | grep -q "backup"; then
  fail "Relay upstream 不得配置备用节点"
else
  pass "无备用节点"
fi
grep -q "proxy_pass http://new_api_relay" "$NGINX" \
  && pass "精确路径经单一 upstream 转发" \
  || fail "精确路径应 proxy_pass 到 http://new_api_relay"

if [[ "$FAIL" -ne 0 ]]; then
  echo "流式传输与资源保护检查：未通过"
  exit 1
fi
echo "流式传输与资源保护检查：通过"
