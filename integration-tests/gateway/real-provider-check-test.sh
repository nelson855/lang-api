#!/usr/bin/env bash
# 真实供应商验收脚本的行为测试；只使用本地 fixture 与假 Key。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="$ROOT/integration-tests/gateway/real-provider-check.sh"
FIXTURE="$ROOT/integration-tests/gateway/fixture/server.py"
TMPD="$(mktemp -d)"
FIX_PID=""

cleanup() {
  if [[ -n "$FIX_PID" ]]; then
    kill "$FIX_PID" 2>/dev/null || true
    wait "$FIX_PID" 2>/dev/null || true
  fi
  rm -rf "$TMPD"
}
trap cleanup EXIT

fail() { echo "[FAIL] $1"; exit 1; }
pass() { echo "[OK] $1"; }

[[ -f "$CHECK" ]] || fail "缺少真实供应商验收脚本"

set +e
env -u MODEL_API_BASE_URL -u MODEL_API_KEY -u MODEL_NAME \
  bash "$CHECK" --success-only >"$TMPD/missing.out" 2>&1
missing_code=$?
set -e
[[ "$missing_code" -eq 2 ]] || fail "缺少变量时应以 2 退出，实际 $missing_code"
grep -q "\[BLOCKED\]" "$TMPD/missing.out" || fail "缺少变量时应明确报告 BLOCKED"
pass "缺少外部条件时保持受阻"

FAKE_KEY="sk-real-check-test-secret"
FIXTURE_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')"
FIXTURE_PORT="$FIXTURE_PORT" python3 "$FIXTURE" >"$TMPD/fixture.log" 2>&1 &
FIX_PID=$!
for _ in $(seq 1 30); do
  curl -s --max-time 1 "http://127.0.0.1:$FIXTURE_PORT/__control/health" >/dev/null && break
  sleep 0.1
done
curl -s --max-time 1 "http://127.0.0.1:$FIXTURE_PORT/__control/health" >/dev/null \
  || fail "本地 fixture 未就绪"

MODEL_API_BASE_URL="http://127.0.0.1:$FIXTURE_PORT/v1" \
MODEL_API_KEY="$FAKE_KEY" \
MODEL_NAME="fixture-model" \
  bash "$CHECK" --success-only >"$TMPD/success.out" 2>&1 \
  || { sed -n '1,120p' "$TMPD/success.out"; fail "成功模式未通过"; }

for marker in "models" "non-stream" "SSE" "requestId"; do
  grep -q "$marker" "$TMPD/success.out" || fail "成功输出缺少标记: $marker"
done
if grep -q "$FAKE_KEY\|fixture reply\|chunk-0" "$TMPD/success.out"; then
  fail "验收输出泄露 Key 或响应正文"
fi
pass "成功模式覆盖三类调用且输出脱敏"

MODEL_API_BASE_URL="http://127.0.0.1:$FIXTURE_PORT/v1" \
MODEL_API_KEY="$FAKE_KEY" \
MODEL_NAME="fixture-model" \
MODEL_API_DISABLED_KEY="sk-test-disabled" \
MODEL_API_EXPIRED_KEY="sk-test-expired" \
MODEL_API_EXHAUSTED_KEY="sk-test-exhausted" \
MODEL_API_RESTRICTED_KEY="sk-test-restricted" \
  bash "$CHECK" --with-restrictions >"$TMPD/restrictions.out" 2>&1
for marker in "disabled restriction" "expired restriction" "exhausted restriction" "restricted restriction"; do
  grep -q "$marker" "$TMPD/restrictions.out" || fail "限制输出缺少标记: $marker"
done
if grep -q 'sk-test-disabled\|sk-test-expired\|sk-test-exhausted\|sk-test-restricted' "$TMPD/restrictions.out"; then
  fail "限制验收输出泄露测试 Key"
fi
pass "完整模式覆盖四类限制且输出脱敏"

echo "真实供应商验收脚本自检：通过"
