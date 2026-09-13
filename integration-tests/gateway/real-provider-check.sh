#!/usr/bin/env bash
# LANG-P1-07 真实供应商验收。只输出脱敏状态、时序与 requestId。
set +x
set -euo pipefail

MODE="${1:---with-restrictions}"
case "$MODE" in
  --success-only|--with-restrictions) ;;
  *) echo "用法: $0 [--success-only|--with-restrictions]"; exit 64 ;;
esac

block_missing() {
  local missing=""
  local name
  for name in "$@"; do
    if [[ -z "${!name:-}" ]]; then
      missing="${missing}${missing:+、}${name}"
    fi
  done
  if [[ -n "$missing" ]]; then
    echo "[BLOCKED] 缺少真实验收环境变量: $missing"
    exit 2
  fi
}

fail() { echo "[FAIL] $1"; exit 1; }
pass() { echo "[OK] $1"; }

command -v curl >/dev/null 2>&1 || fail "缺少 curl"
command -v python3 >/dev/null 2>&1 || fail "缺少 python3"
block_missing MODEL_API_BASE_URL MODEL_API_KEY MODEL_NAME

BASE_URL="${MODEL_API_BASE_URL%/}"
[[ "$BASE_URL" =~ ^https?://[^/?#[:space:]@]+(:[0-9]+)?/v1$ ]] \
  || fail "MODEL_API_BASE_URL 必须是无用户信息、查询和片段并以 /v1 结束的 HTTP(S) 地址"
[[ "$MODEL_API_KEY" =~ ^[A-Za-z0-9._-]+$ ]] \
  || fail "MODEL_API_KEY 格式包含不允许的字符"

TMPD="$(mktemp -d)"
cleanup() { rm -rf "$TMPD"; }
trap cleanup EXIT

MODEL_NAME="$MODEL_NAME" python3 -c '
import json, os
name = os.environ["MODEL_NAME"]
print(json.dumps({"model": name, "messages": [{"role": "user", "content": "Reply with OK."}], "stream": False}))
' > "$TMPD/non-stream-request.json"
MODEL_NAME="$MODEL_NAME" python3 -c '
import json, os
name = os.environ["MODEL_NAME"]
print(json.dumps({"model": name, "messages": [{"role": "user", "content": "Reply with OK."}], "stream": True}))
' > "$TMPD/stream-request.json"

curl_with_key() {
  local key="$1"
  shift
  [[ "$key" =~ ^[A-Za-z0-9._-]+$ ]] || fail "验收 Key 格式包含不允许的字符"
  printf 'header = "Authorization: Bearer %s"\nheader = "X-Request-Id: acceptance-probe"\n' "$key" \
    | curl --config - --silent --show-error --connect-timeout 10 --max-time 180 "$@"
}

request_id() {
  awk 'BEGIN {IGNORECASE=1} /^X-Request-Id:/ {sub(/^[^:]+:[[:space:]]*/, ""); sub(/\r$/, ""); value=$0} END {print value}' "$1"
}

assert_no_brand() {
  if grep -Eqi 'new[ -]?api|one[ -]?api|new-api|one-api' "$@"; then
    fail "响应暴露 New API/One API 品牌"
  fi
}

echo "真实供应商验收开始（输出不含 URL、Key、模型名或响应正文）"

metrics="$(curl_with_key "$MODEL_API_KEY" -D "$TMPD/models.headers" -o "$TMPD/models.body" \
  -w '%{http_code} %{time_total}' "$BASE_URL/models")"
read -r status duration <<< "$metrics"
[[ "$status" == "200" ]] || fail "models 调用失败，HTTP $status"
python3 -c 'import json,sys; data=json.load(open(sys.argv[1])); raise SystemExit(0 if isinstance(data.get("data"), list) else 1)' \
  "$TMPD/models.body" || fail "models 响应不是 OpenAI 兼容模型列表"
rid="$(request_id "$TMPD/models.headers")"
[[ -n "$rid" ]] || fail "models 响应缺少 X-Request-Id"
assert_no_brand "$TMPD/models.headers" "$TMPD/models.body"
pass "models status=200 duration=${duration}s requestId=$rid"

metrics="$(curl_with_key "$MODEL_API_KEY" -D "$TMPD/non-stream.headers" -o "$TMPD/non-stream.body" \
  -H 'Content-Type: application/json' --data-binary "@$TMPD/non-stream-request.json" \
  -w '%{http_code} %{time_total}' "$BASE_URL/chat/completions")"
read -r status duration <<< "$metrics"
[[ "$status" == "200" ]] || fail "non-stream 调用失败，HTTP $status"
python3 -c 'import json,sys; data=json.load(open(sys.argv[1])); raise SystemExit(0 if isinstance(data.get("choices"), list) and data["choices"] else 1)' \
  "$TMPD/non-stream.body" || fail "non-stream 响应缺少 choices"
rid="$(request_id "$TMPD/non-stream.headers")"
[[ -n "$rid" ]] || fail "non-stream 响应缺少 X-Request-Id"
assert_no_brand "$TMPD/non-stream.headers" "$TMPD/non-stream.body"
pass "non-stream status=200 duration=${duration}s requestId=$rid"

metrics="$(curl_with_key "$MODEL_API_KEY" -N -D "$TMPD/stream.headers" -o "$TMPD/stream.body" \
  -H 'Content-Type: application/json' --data-binary "@$TMPD/stream-request.json" \
  -w '%{http_code} %{time_starttransfer} %{time_total}' "$BASE_URL/chat/completions")"
read -r status first_byte duration <<< "$metrics"
[[ "$status" == "200" ]] || fail "SSE 调用失败，HTTP $status"
grep -Eqi '^Content-Type:.*text/event-stream' "$TMPD/stream.headers" \
  || fail "SSE 响应 Content-Type 不正确"
[[ "$(grep -c '^data:' "$TMPD/stream.body")" -ge 2 ]] || fail "SSE 响应没有逐块数据"
grep -q '^data: \[DONE\]' "$TMPD/stream.body" || fail "SSE 响应缺少 [DONE]"
rid="$(request_id "$TMPD/stream.headers")"
[[ -n "$rid" ]] || fail "SSE 响应缺少 X-Request-Id"
assert_no_brand "$TMPD/stream.headers" "$TMPD/stream.body"
pass "SSE status=200 firstByte=${first_byte}s duration=${duration}s requestId=$rid"

if [[ "$MODE" == "--with-restrictions" ]]; then
  block_missing MODEL_API_DISABLED_KEY MODEL_API_EXPIRED_KEY MODEL_API_EXHAUSTED_KEY MODEL_API_RESTRICTED_KEY
  for scenario in disabled expired exhausted restricted; do
    case "$scenario" in
      disabled) restricted_key="$MODEL_API_DISABLED_KEY" ;;
      expired) restricted_key="$MODEL_API_EXPIRED_KEY" ;;
      exhausted) restricted_key="$MODEL_API_EXHAUSTED_KEY" ;;
      restricted) restricted_key="$MODEL_API_RESTRICTED_KEY" ;;
    esac
    metrics="$(curl_with_key "$restricted_key" -D "$TMPD/${scenario}.headers" -o "$TMPD/${scenario}.body" \
      -H 'Content-Type: application/json' --data-binary "@$TMPD/non-stream-request.json" \
      -w '%{http_code} %{time_total}' "$BASE_URL/chat/completions")"
    read -r status duration <<< "$metrics"
    [[ "$status" =~ ^4[0-9][0-9]$ ]] || fail "${scenario} Key 应被拒绝，实际 HTTP $status"
    python3 -c 'import json,sys; data=json.load(open(sys.argv[1])); err=data.get("error", {}); raise SystemExit(0 if err.get("type") == "gateway_error" and err.get("code") else 1)' \
      "$TMPD/${scenario}.body" || fail "${scenario} Key 未返回稳定网关错误"
    rid="$(request_id "$TMPD/${scenario}.headers")"
    [[ -n "$rid" ]] || fail "${scenario} 响应缺少 X-Request-Id"
    assert_no_brand "$TMPD/${scenario}.headers" "$TMPD/${scenario}.body"
    pass "${scenario} restriction status=${status} duration=${duration}s requestId=$rid"
  done
fi

echo "真实供应商验收：通过"
