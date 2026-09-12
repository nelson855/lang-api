#!/usr/bin/env bash
# New API 接口探测框架（LANG-P1-02 任务 6.1）
# 探测期间临时启用运维覆盖，宿主机经 127.0.0.1 访问：
#   docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.ops.yml up -d
# 所有原始响应只写入被忽略的临时目录（integration-tests/new-api/tmp/），
# 落盘到 docs/new-api/samples/ 前必须经过 sanitize.sh 脱敏与人工抽查。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SANITIZE="$ROOT/integration-tests/new-api/sanitize.sh"
TMPDIR="$ROOT/integration-tests/new-api/tmp"
ADMIN_BASE="${NEW_API_ADMIN_BASE:-http://127.0.0.1:13000}"
JAR="$TMPDIR/cookies.txt"

mkdir -p "$TMPDIR"

api() {
  # 用法：api <METHOD> <PATH> [BODY_FILE] [OUT_FILE]
  local method="$1" path="$2" body="${3:-}" out="${4:-$TMPDIR/last.raw.json}"
  if [[ -n "$body" ]]; then
    curl -sS -c "$JAR" -b "$JAR" -X "$method" "$ADMIN_BASE$path" \
      -H 'Content-Type: application/json' --data-binary "@$body" \
      -D "$TMPDIR/last.headers" -o "$out" -w '%{http_code}\n'
  else
    curl -sS -c "$JAR" -b "$JAR" -X "$method" "$ADMIN_BASE$path" \
      -D "$TMPDIR/last.headers" -o "$out" -w '%{http_code}\n'
  fi
}

assert_status() {
  # 用法：assert_status <实际> <期望> <说明>
  if [[ "$1" != "$2" ]]; then
    echo "[FAIL] $3：期望 HTTP $2，实际 $1"
    return 1
  fi
  echo "[OK] $3 -> $1"
}

save_sample() {
  # 用法：save_sample <原始文件> <样例目录/文件名>（自动脱敏后写入 docs 样例区）
  local src="$1" dest="$ROOT/docs/new-api/samples/$2"
  mkdir -p "$(dirname "$dest")"
  "$SANITIZE" "$src" > "$dest"
  echo "[OK] 脱敏样例：docs/new-api/samples/$2"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "探测框架已加载。示例："
  echo "  source integration-tests/new-api/probe.sh"
  echo "  api GET /api/status"
  echo "原始响应目录（已忽略）：$TMPDIR"
fi
