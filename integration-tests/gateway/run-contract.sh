#!/usr/bin/env bash
# 模型网关运行时契约验证（LANG-P1-07 任务 7.1-7.6）。
# 使用本机 fixture（假 Key、假数据）+ 临时 edge 容器验证真实模板行为，
# 不启动项目 Compose，不连接真实供应商，全程用完即清。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIXTURE_DIR="$ROOT/integration-tests/gateway/fixture"
DOCKERFILE="$ROOT/gateway/Dockerfile"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

FAKE_KEY="sk-test-fake-0001"
FIXTURE_PORT=3000

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

command -v docker >/dev/null || { fail "缺少 docker"; exit 1; }
command -v python3 >/dev/null || { fail "缺少 python3"; exit 1; }
command -v curl >/dev/null || { fail "缺少 curl"; exit 1; }

if curl -s --max-time 2 "http://127.0.0.1:$FIXTURE_PORT/__control/health" | grep -q ok; then
  fail "本机 $FIXTURE_PORT 端口被占用，请先释放再跑契约验证"
  exit 1
fi

NGINX_IMAGE="$(grep -oE "FROM[^@]*@sha256:[0-9a-f]{64}" "$DOCKERFILE" | head -n1 | sed -E 's/^FROM //')"
[[ -n "$NGINX_IMAGE" ]] || { fail "无法从 Dockerfile 解析镜像"; exit 1; }
HOST_GATEWAY_IPV4="$(docker run --rm --add-host=new-api:host-gateway --entrypoint sh "$NGINX_IMAGE" \
  -c 'cat /etc/hosts' | awk '$2 == "new-api" && $1 ~ /^[0-9]+\./ {print $1; exit}')"
[[ -n "$HOST_GATEWAY_IPV4" ]] || { fail "无法解析 Docker host-gateway IPv4"; exit 1; }

TMPD="$(mktemp -d)"
CONTAINER="langapi-gwtest-$$"
FIX_PID=""
CLIENT_PIDS=()

cleanup() {
  for pid in ${CLIENT_PIDS[@]+"${CLIENT_PIDS[@]}"}; do
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
  if [[ -n "$FIX_PID" ]]; then
    kill "$FIX_PID" 2>/dev/null || true
    wait "$FIX_PID" 2>/dev/null || true
  fi
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$TMPD"
}
trap cleanup EXIT

render_template() {
  # 按官方模板规则只替换允许变量；其余 $变量保持原样。
  # $2 的显式覆盖必须先于 Dockerfile 默认值生效，否则占位符已被默认值吃掉。
  local extra="${1:-}"
  SED_ARGS=(-e 's/\${PORTAL_SERVER_NAME}/portal.test/g' -e 's/\${MODEL_API_SERVER_NAME}/model.test/g')
  if [[ -n "$extra" ]]; then
    local k="${extra%%=*}"
    local v="${extra#*=}"
    SED_ARGS+=(-e "s|\${$k}|$v|g")
  fi
  while IFS='=' read -r key value; do
    [[ -n "$key" && -n "$value" ]] && SED_ARGS+=(-e "s|\${$key}|$value|g")
  done < <(grep -oE 'GATEWAY_[A-Z_]+=[^ \]+' "$DOCKERFILE")
  sed "${SED_ARGS[@]}" "$NGINX" > "$TMPD/default.conf"
}

start_edge() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker run --rm -d --name "$CONTAINER" \
    -p 127.0.0.1::80 \
    --add-host="new-api:$HOST_GATEWAY_IPV4" \
    --add-host="lang-api:127.0.0.1" \
    -v "$TMPD/default.conf:/etc/nginx/conf.d/default.conf:ro" \
    "$NGINX_IMAGE" >/dev/null
  GW_PORT="$(docker port "$CONTAINER" 80 | sed -E 's/.*://')"
  for _ in $(seq 1 30); do
    if curl -s --max-time 2 -H "Host: portal.test" "http://127.0.0.1:$GW_PORT/healthz" | grep -q ok; then
      return 0
    fi
    sleep 1
  done
  fail "edge 容器未就绪"
  return 1
}

start_fixture() {
  # & 必须在括号外，$! 才是服务进程；exec 让子 shell 直接变成 server，kill 才打得准。
  (cd "$FIXTURE_DIR" && exec python3 server.py >/tmp/gwtest-fixture.log 2>&1) &
  FIX_PID=$!
  for _ in $(seq 1 20); do
    if curl -s --max-time 2 "http://127.0.0.1:$FIXTURE_PORT/__control/health" | grep -q ok; then
      return 0
    fi
    sleep 0.5
  done
  fail "fixture 未就绪"
  return 1
}

stop_fixture() {
  if [[ -n "$FIX_PID" ]]; then
    kill "$FIX_PID" 2>/dev/null || true
    wait "$FIX_PID" 2>/dev/null || true
  fi
  FIX_PID=""
  sleep 1
}

# gw <host> <path> [curl args...]：把状态码写入 $CODE，正文 $BODY，响应头 $HDRS
gw() {
  local host="$1"; local path="$2"; shift 2
  CODE="$(curl -s --max-time 30 -o "$TMPD/body" -D "$TMPD/hdrs" -w "%{http_code}" \
    -H "Host: $host" "http://127.0.0.1:$GW_PORT$path" "$@")"
  BODY="$(cat "$TMPD/body")"
}

resp_header() {
  grep -i "^$1:" "$TMPD/hdrs" | head -n1 | tr -d '\r' | sed -E "s/^[^:]+:[[:space:]]*//"
}

expect_code() {
  local want="$1"; local what="$2"
  if [[ "$CODE" == "$want" ]]; then
    pass "$what -> $CODE"
  else
    fail "${what} 期望 ${want}，实际 ${CODE}（正文: $(head -c 200 "$TMPD/body")）"
  fi
}

expect_body_code() {
  local want="$1"; local what="$2"
  if echo "$BODY" | grep -q "\"code\":\"$want\""; then
    pass "$what 错误码 $want"
  else
    fail "${what} 错误码期望 ${want}，实际: $(head -c 200 "$TMPD/body")"
  fi
}

count_for() {
  local route="$1"
  python3 -c 'import json,sys; print(json.load(sys.stdin)["request_count"].get(sys.argv[1], 0))' "$route"
}

echo "== 启动 fixture 与 edge（默认超时） =="
start_fixture
render_template
start_edge

echo "== 7.1 精确路径正常调用与拒绝 =="
gw model.test /v1/models -H "Authorization: Bearer $FAKE_KEY"
expect_code 200 "GET /v1/models"
echo "$BODY" | grep -q fixture-model && pass "模型列表正文透传" || fail "模型列表正文未透传"

gw model.test /v1/chat/completions -X POST -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" -d '{"model":"fixture-model","stream":false}'
expect_code 200 "POST /v1/chat/completions 非流式"
echo "$BODY" | grep -q fixture && pass "聊天正文透传" || fail "聊天正文未透传"

gw model.test /v1/chat/completions -H "Authorization: Bearer $FAKE_KEY"
expect_code 405 "GET 聊天路径"
expect_body_code METHOD_NOT_ALLOWED "GET 聊天路径"

gw model.test /v1/models -X POST -H "Authorization: Bearer $FAKE_KEY"
expect_code 405 "POST 模型列表"
expect_body_code METHOD_NOT_ALLOWED "POST 模型列表"

gw model.test /v1/chat/completions -X OPTIONS -H "Authorization: Bearer $FAKE_KEY"
expect_code 405 "OPTIONS 预检拒绝"
echo "$BODY" | grep -q "Access-Control-Allow-Origin" \
  && fail "OPTIONS 不得返回通配 CORS" || pass "OPTIONS 无 CORS"

for p in "/v1/responses" "/v1/chat/completions/" "/v1/chat/completions/extra" "/v1beta/models" "/api/status" "/setup/" "/"; do
  gw model.test "$p" -X POST -H "Authorization: Bearer $FAKE_KEY"
  [[ "$CODE" == "404" ]] && pass "拒绝 $p" || fail "路径 $p 期望 404，实际 $CODE"
done

gw portal.test /v1/chat/completions -X POST -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" -d '{}'
expect_code 404 "用户站点 Host 隔离"

gw model.test /v1/models
expect_code 401 "缺失 Authorization"
expect_body_code AUTHENTICATION_REQUIRED "缺失 Authorization"

gw model.test /v1/models -H "Authorization: Bearer"
expect_code 401 "空凭证 401"
gw model.test /v1/models -H "Authorization: Basic abc"
expect_code 401 "错误 scheme 401"

echo "== 7.2 Header 转发与伪造清除 =="
BEFORE_COUNT="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
BEFORE_MODELS="$(echo "$BEFORE_COUNT" | count_for models)"
gw model.test /v1/models -H "Authorization: Bearer $FAKE_KEY" \
  -H "Accept: application/json" \
  -H "Cookie: LANG_SESSION=forged" \
  -H "X-Forwarded-For: 9.9.9.9" \
  -H "Forwarded: for=9.9.9.9" \
  -H "New-Api-User: 1" \
  -H "X-Request-Id: client-forged-id" \
  -H "X-Custom-Evil: 1"
expect_code 200 "带伪造头的正常调用"
LAST="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/last")"
echo "$LAST" | grep -q '"has_authorization": true' && pass "Authorization 已转发" || fail "Authorization 未转发"
echo "$LAST" | grep -q '"auth_scheme": "Bearer"' && pass "Bearer 原值转发" || fail "Bearer 未原值转发"
echo "$LAST" | grep -q '"has_cookie": false' && pass "Cookie 未进入上游" || fail "Cookie 透传到上游"
echo "$LAST" | grep -q '"forbidden_headers_present": \[\]' && pass "伪造转发头已清除" || fail "伪造头未清除: $LAST"
GW_RID="$(resp_header X-Request-Id)"
UP_RID="$(echo "$LAST" | python3 -c "import sys,json;print(json.load(sys.stdin).get('x_request_id',''))")"
if [[ -n "$GW_RID" && "$GW_RID" == "$UP_RID" && "$GW_RID" != "client-forged-id" ]]; then
  pass "网关 requestId 一致且覆盖客户端值"
else
  fail "requestId 不一致（响应:$GW_RID 上游:$UP_RID）"
fi
AFTER_COUNT="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
AFTER_MODELS="$(echo "$AFTER_COUNT" | count_for models)"
[[ "$AFTER_MODELS" -eq $((BEFORE_MODELS + 1)) ]] \
  && pass "一次请求只触发一次上游" \
  || fail "单次请求的上游计数应增加 1（之前 ${BEFORE_MODELS}，之后 ${AFTER_MODELS}）"

echo "== 7.3 非流式透传与去标识 =="
gw model.test /v1/chat/completions -X POST -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" -d '{"model":"fixture-model","stream":false}'
[[ "$(resp_header Content-Type)" == application/json* ]] \
  && pass "非流式 Content-Type 保持 JSON" || fail "非流式 Content-Type 异常"
[[ "$(resp_header Cache-Control)" == "no-store" ]] && pass "Cache-Control: no-store" || fail "缺少 no-store"
[[ -n "$(resp_header X-Request-Id)" ]] && pass "响应 X-Request-Id" || fail "缺少响应 X-Request-Id"
grep -qi "^etag:" "$TMPD/hdrs" && fail "上游 ETag 未移除" || pass "ETag 已移除"
grep -qi "^x-cache:" "$TMPD/hdrs" && fail "上游 X-Cache 未移除" || pass "X-Cache 已移除"
grep -qi "RelayFixture" "$TMPD/hdrs" && fail "上游 Server 标识未移除" || pass "Server 标识已隐藏"
C1="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
gw model.test /v1/chat/completions -X POST -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" -d '{"model":"fixture-model","stream":false}'
C2="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
[[ "$C1" != "$C2" ]] && pass "重复请求不缓存（每次触达上游）" || fail "响应疑似被缓存"

echo "== 7.4 SSE 首块与逐块 =="
TIMES="$(curl -s -N --max-time 30 -o "$TMPD/sse.body" -w "%{time_starttransfer} %{time_total}" \
  -H "Host: model.test" -H "Authorization: Bearer $FAKE_KEY" -H "Content-Type: application/json" \
  -X POST --data-binary '{"model":"fixture-model","stream":true}' \
  "http://127.0.0.1:$GW_PORT/v1/chat/completions?chunks=4&chunk_delay=0.5")"
T_FIRST="$(echo "$TIMES" | awk '{print $1}')"
T_TOTAL="$(echo "$TIMES" | awk '{print $2}')"
python3 -c "exit(0 if float('$T_FIRST') < 1.0 and float('$T_TOTAL') > 1.5 else 1)" \
  && pass "首块先到（首字节 ${T_FIRST}s，总 ${T_TOTAL}s）" \
  || fail "SSE 时序异常（首字节 ${T_FIRST}s，总 ${T_TOTAL}s）"
python3 - "$TMPD/sse.body" <<'PY' \
  && pass "数据块顺序完整且 [DONE] 未改写" \
  || fail "SSE 数据块顺序、数量或 [DONE] 异常"
import json
import sys

events = [line.removeprefix("data: ") for line in open(sys.argv[1], encoding="utf-8")
          if line.startswith("data: ")]
indexes = [json.loads(event)["index"] for event in events[:-1]]
raise SystemExit(0 if indexes == [0, 1, 2, 3] and events[-1:] == ["[DONE]\n"] else 1)
PY

echo "== 7.5 客户端取消 =="
curl -s --max-time 10 -X POST "http://127.0.0.1:$FIXTURE_PORT/__control/reset" -d '' >/dev/null
curl -s -N --max-time 30 -H "Host: model.test" -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" -X POST --data-binary '{"model":"fixture-model","stream":true}' \
  "http://127.0.0.1:$GW_PORT/v1/chat/completions?chunks=50&chunk_delay=0.2" > "$TMPD/cancel.body" 2>/dev/null &
CURL_PID=$!
sleep 1.5
kill "$CURL_PID" 2>/dev/null || true
wait "$CURL_PID" 2>/dev/null || true
CANCELLED=""
for _ in $(seq 1 20); do
  CANCELLED="$(curl -s --max-time 5 "http://127.0.0.1:$FIXTURE_PORT/__control/cancelled")"
  echo "$CANCELLED" | grep -q "chat/completions" && break
  sleep 0.5
done
echo "$CANCELLED" | grep -q "chat/completions" \
  && pass "上游在时限内观察到取消" \
  || fail "上游未观察到取消: $CANCELLED"
COUNTS="$(curl -s --max-time 5 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
echo "$COUNTS" | grep -q '"chat/completions": 1' \
  && pass "取消请求只触发一次上游" \
  || fail "上游计数异常: $COUNTS"
CANCEL_LOG=""
for _ in $(seq 1 20); do
  CANCEL_LOG="$(docker exec "$CONTAINER" sh -c 'tail -n 20 /var/log/nginx/model-access.log' 2>/dev/null || true)"
  echo "$CANCEL_LOG" | grep -q '"status":[0-9][0-9]*.*"result":"client_cancelled"' && break
  sleep 0.2
done
echo "$CANCEL_LOG" | grep -q '"status":[0-9][0-9]*.*"result":"client_cancelled"' \
  && pass "取消日志记录状态码/client_cancelled" \
  || { fail "取消日志缺少状态码/client_cancelled"; echo "$CANCEL_LOG"; }

echo "== 7.6 错误映射与资源边界 =="
for st in "400:400:UPSTREAM_REJECTED" "401:401:AUTHENTICATION_FAILED" "403:403:FORBIDDEN" \
          "404:404:UPSTREAM_REJECTED" "409:409:UPSTREAM_REJECTED" "422:422:UPSTREAM_REJECTED" \
          "429:429:RATE_LIMITED" "500:502:GATEWAY_UNAVAILABLE"; do
  UP="${st%%:*}"; rest="${st#*:}"; HTTP="${rest%%:*}"; WANT="${rest##*:}"
  gw model.test "/v1/models?status=$UP" -H "Authorization: Bearer $FAKE_KEY"
  if [[ "$CODE" == "$HTTP" ]]; then
    pass "上游 $UP -> $HTTP"
  else
    fail "上游 ${UP} 期望 ${HTTP}，实际 ${CODE}"
  fi
  expect_body_code "$WANT" "上游 $UP"
  echo "$BODY" | grep -q "fixture injected" \
    && fail "上游 $UP 原始正文未丢弃" || pass "上游 $UP 原始正文已丢弃"
done

echo "--- 已开始 SSE 的上游中断 ---"
curl -s --max-time 10 -X POST "http://127.0.0.1:$FIXTURE_PORT/__control/reset" -d '' >/dev/null
curl -s -N --max-time 10 -o "$TMPD/mid-disconnect.body" \
  -H "Host: model.test" -H "Authorization: Bearer $FAKE_KEY" -H "Content-Type: application/json" \
  -X POST --data-binary '{"model":"fixture-model","stream":true}' \
  "http://127.0.0.1:$GW_PORT/v1/chat/completions?chunks=5&chunk_delay=0.1&disconnect=mid" || true
grep -q "chunk-0" "$TMPD/mid-disconnect.body" \
  && pass "上游中断前已透传首块" || fail "上游中断样例缺少首块"
grep -q "\[DONE\]" "$TMPD/mid-disconnect.body" \
  && fail "上游中断后不得伪造 [DONE]" || pass "上游中断后未伪造 [DONE]"
MID_COUNTS="$(curl -s --max-time 5 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
[[ "$(echo "$MID_COUNTS" | count_for chat/completions)" -eq 1 ]] \
  && pass "上游中断未触发重试" || fail "上游中断触发次数异常: $MID_COUNTS"

echo "--- 413 正文上限 ---"
C_BEFORE="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
head -c 11534336 /dev/zero | tr '\0' 'a' > "$TMPD/big.json"
gw model.test /v1/chat/completions -X POST -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" --data-binary "@$TMPD/big.json"
expect_code 413 "11MB 正文"
expect_body_code PAYLOAD_TOO_LARGE "11MB 正文"
C_AFTER="$(curl -s --max-time 10 "http://127.0.0.1:$FIXTURE_PORT/__control/counts")"
[[ "$C_BEFORE" == "$C_AFTER" ]] && pass "超限请求未触达 Relay" || fail "超限请求触达了 Relay"

echo "--- 429 边缘限流 ---"
HIT429=0
for _ in $(seq 1 40); do
  c="$(curl -s --max-time 10 -o /dev/null -w "%{http_code}" -H "Host: model.test" \
    -H "Authorization: Bearer $FAKE_KEY" "http://127.0.0.1:$GW_PORT/v1/models")"
  [[ "$c" == "429" ]] && HIT429=1 && break
done
[[ "$HIT429" == "1" ]] && pass "触发 429 限流" || fail "40 连击未触发 429"
gw model.test /v1/models -H "Authorization: Bearer $FAKE_KEY"
if [[ "$CODE" == "429" ]]; then
  [[ -n "$(resp_header Retry-After)" ]] && pass "429 带 Retry-After" || fail "429 缺少 Retry-After"
else
  for _ in $(seq 1 30); do
    gw model.test /v1/models -H "Authorization: Bearer $FAKE_KEY"
    [[ "$CODE" == "429" ]] && break
  done
  [[ "$CODE" == "429" && -n "$(resp_header Retry-After)" ]] \
    && pass "429 带 Retry-After" || fail "未能复现带 Retry-After 的 429（最后状态 ${CODE}）"
fi
sleep 3

echo "--- 429 并发上限 ---"
render_template "GATEWAY_CONN_LIMIT=2"
start_edge
for idx in 1 2; do
  curl -s -N --max-time 20 -H "Host: model.test" -H "Authorization: Bearer $FAKE_KEY" \
    -H "Content-Type: application/json" -X POST --data-binary '{"model":"fixture-model","stream":true}' \
    "http://127.0.0.1:$GW_PORT/v1/chat/completions?chunks=100&chunk_delay=0.2" \
    > "$TMPD/concurrent-${idx}.body" 2>/dev/null &
  CLIENT_PIDS+=("$!")
done
for _ in $(seq 1 30); do
  grep -q "chunk-0" "$TMPD/concurrent-1.body" 2>/dev/null \
    && grep -q "chunk-0" "$TMPD/concurrent-2.body" 2>/dev/null \
    && break
  sleep 0.2
done
gw model.test /v1/models -H "Authorization: Bearer $FAKE_KEY"
expect_code 429 "超过单地址并发上限"
expect_body_code RATE_LIMITED "超过单地址并发上限"
[[ -n "$(resp_header Retry-After)" ]] \
  && pass "并发 429 带 Retry-After" || fail "并发 429 缺少 Retry-After"
for pid in ${CLIENT_PIDS[@]+"${CLIENT_PIDS[@]}"}; do
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
done
CLIENT_PIDS=()

echo "--- 上游不可用 ---"
stop_fixture
gw model.test /v1/models -H "Authorization: Bearer $FAKE_KEY"
expect_code 502 "上游断连"
expect_body_code GATEWAY_UNAVAILABLE "上游断连"
echo "$BODY" | grep -qi "new-api" && fail "不可用错误泄露内部地址" || pass "不可用错误无品牌"
start_fixture

echo "== 日志隐私扫描 =="
SECRET="sk-test-fake-SECRET999"
curl -s --max-time 10 -o /dev/null -H "Host: model.test" -H "Authorization: Bearer $FAKE_KEY" \
  "http://127.0.0.1:$GW_PORT/v1/models?api_key=$SECRET" || true
curl -s --max-time 10 -o /dev/null -H "Host: model.test" -H "Authorization: Bearer $FAKE_KEY" \
  -H "Content-Type: application/json" -X POST --data-binary "{\"model\":\"m\",\"prompt\":\"$SECRET\"}" \
  "http://127.0.0.1:$GW_PORT/v1/chat/completions" || true
sleep 1
LOG="$(docker exec "$CONTAINER" cat /var/log/nginx/model-access.log 2>/dev/null || true)"
if echo "$LOG" | grep -q "$SECRET"; then
  fail "访问日志泄露敏感值"
else
  pass "访问日志无敏感值"
fi
echo "$LOG" | grep -q '"route":"models"' && pass "日志记录规范化路由" || fail "日志缺少规范化路由"

echo "== 短超时复验 504（任务 4.2/7.6） =="
render_template "GATEWAY_READ_TIMEOUT=3s"
start_edge
gw model.test "/v1/models?delay=6" -H "Authorization: Bearer $FAKE_KEY"
expect_code 504 "上游 6s 无数据（3s 读取超时）"
expect_body_code GATEWAY_TIMEOUT "读取超时"

if [[ "$FAIL" -ne 0 ]]; then
  echo "网关运行时契约：未通过"
  exit 1
fi
echo "网关运行时契约：通过"
