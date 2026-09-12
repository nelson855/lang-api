#!/usr/bin/env bash
# 脱敏有效性自检（LANG-P1-02 任务 6.2）
# 用含敏感信息的夹具验证 sanitize.sh 能替换且扫描能发现残留。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SANITIZE="$ROOT/integration-tests/new-api/sanitize.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

[[ -x "$SANITIZE" ]] || { echo "[FAIL] 缺失可执行文件: $SANITIZE"; exit 1; }

cat > "$TMP/fixture.json" <<'EOF'
{
  "password": "SuperSecret123!",
  "authorization": "Bearer sk-test-abc123456789",
  "cookie": "session=abc123; token=xyz789",
  "api_key": "sk-test-full-key-123456",
  "email": "user@example.com",
  "phone": "13800138000",
  "dsn": "postgres://user:dbpass123@postgres:5432/newapi"
}
EOF

"$SANITIZE" "$TMP/fixture.json" > "$TMP/clean.json"

FAIL=0
for secret in "SuperSecret123" "sk-test-abc123456789" "session=abc123" "sk-test-full-key" "user@example.com" "13800138000" "dbpass123"; do
  if grep -q "$secret" "$TMP/clean.json"; then
    echo "[FAIL] 残留敏感信息: $secret"
    FAIL=1
  fi
done

for placeholder in "REDACTED" "__REDACTED__" "***"; do
  if grep -q "$placeholder" "$TMP/clean.json"; then
    echo "[OK] 发现占位符: $placeholder"
    break
  fi
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "脱敏自检：未通过"
  exit 1
fi
echo "脱敏自检：通过"
