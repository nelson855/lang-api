#!/usr/bin/env bash
# 基线一致性检查（LANG-P1-02 任务 7.3）
# 比对 deploy/compose.yml 的 New API digest 与 docs/new-api/基线版本与许可证.md 记录，
# 并扫描 Compose/脚本/文档中的 New API 浮动镜像引用。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="$ROOT/deploy/compose.yml"
BASELINE="$ROOT/docs/new-api/基线版本与许可证.md"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

[[ -f "$COMPOSE" ]] || { fail "缺失 $COMPOSE"; exit 1; }
[[ -f "$BASELINE" ]] || { fail "缺失 $BASELINE"; exit 1; }

COMPOSE_DIGEST=$(grep -oE "calciumion/new-api:[^@[:space:]]+@sha256:[0-9a-f]{64}" "$COMPOSE" | head -n1 || true)
[[ -n "$COMPOSE_DIGEST" ]] || { fail "Compose 中未找到 digest 锁定的 New API 镜像"; exit 1; }
pass "Compose New API 引用: $COMPOSE_DIGEST"

COMPOSE_SHA=$(echo "$COMPOSE_DIGEST" | grep -oE "sha256:[0-9a-f]{64}")
if grep -q "$COMPOSE_SHA" "$BASELINE"; then
  pass "基线文档包含相同 digest"
else
  fail "基线文档与 Compose 的 New API digest 不一致"
fi

# 扫描浮动引用：new-api 镜像出现 :latest 或无 digest 的 tag 引用（排除检查脚本自身）
if grep -rn --exclude=baseline-check.sh "calciumion/new-api:latest" "$ROOT/deploy" "$ROOT/integration-tests" "$ROOT/docs/new-api" 2>/dev/null; then
  fail "发现 New API floating 引用（latest）"
else
  pass "无 New API latest 引用"
fi

if grep -rnE "image:[[:space:]]*calciumion/new-api:[^@[:space:]'\"]+[\"']?$" "$ROOT/deploy" 2>/dev/null; then
  fail "发现未带 digest 的 New API 镜像引用"
else
  pass "New API 引用均带 digest"
fi

# 占位提醒：全零 digest 表示尚未冻结，不视为失败，但必须明确提示
if echo "$COMPOSE_SHA" | grep -q "sha256:0\{64\}"; then
  echo "[WARN] 当前 digest 为待核验占位（全零），基线尚未冻结，禁止视为已验证"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "基线一致性检查：未通过"
  exit 1
fi
echo "基线一致性检查：通过"
