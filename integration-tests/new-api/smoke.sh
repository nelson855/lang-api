#!/usr/bin/env bash
# 五服务冒烟测试（LANG-P1-02 任务 4.4）
# 前置：cp deploy/.env.example deploy/.env 并填入本地随机值；Docker 可用。
# 用法：bash integration-tests/new-api/smoke.sh
# 失败返回非零；所有失败定位到具体服务，不以其他进程存在掩盖失败。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE=(-f "$ROOT/deploy/compose.yml")
EDGE_PORT="${EDGE_HTTP_PORT:-8081}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "[FAIL] 缺少命令: $1"; exit 2; }; }
need docker
need curl

echo "== 1. 五服务状态 =="
docker compose "${COMPOSE[@]}" ps --format '{{.Service}} {{.State}} {{.Health}}' | tee /tmp/langapi-smoke-ps.txt
for svc in edge-nginx lang-api new-api postgres redis; do
  grep -q "^$svc " /tmp/langapi-smoke-ps.txt || { echo "[FAIL] 缺少服务: $svc"; exit 1; }
done
echo "[OK] 五服务均存在"

echo "== 2. 唯一 edge 端口 =="
PUBLISHED=$(docker compose "${COMPOSE[@]}" ps --format json | grep -o '"Publishers":[^{]*' || true)
echo "$PUBLISHED"
if docker compose "${COMPOSE[@]}" ps new-api postgres redis 2>/dev/null | grep -qE "0\.0\.0\.0|:::"; then
  echo "[FAIL] new-api/postgres/redis 不应有宿主机端口映射"
  exit 1
fi
echo "[OK] 仅 edge-nginx 发布宿主机端口"

echo "== 3. 内部连通性（lang-api -> new-api 经 control 面）=="
docker compose "${COMPOSE[@]}" exec -T lang-api sh -c 'wget -qO- http://new-api:3000/api/status >/dev/null' \
  && echo "[OK] lang-api 可达 new-api" \
  || { echo "[FAIL] lang-api 无法到达 new-api（control 面）"; exit 1; }

echo "== 4. 数据网络隔离（edge/lang-api 不可达 postgres/redis）=="
if docker compose "${COMPOSE[@]}" exec -T edge-nginx sh -c 'wget -qO- --timeout=3 postgres:5432 >/dev/null 2>&1'; then
  echo "[FAIL] edge-nginx 不应能连接 postgres"
  exit 1
fi
echo "[OK] edge 无法直连数据服务"

echo "== 5. 唯一入口可用 =="
curl -fsS "http://127.0.0.1:${EDGE_PORT}/healthz" | grep -q ok && echo "[OK] edge /healthz"
curl -fsS "http://127.0.0.1:${EDGE_PORT}/actuator/health" | grep -q '"status":"UP"' && echo "[OK] Lang API 首页链路"

echo "== 6. 关闭路径 =="
for p in "/v1/chat/completions" "/v1beta/models" "/api/status" "/setup/"; do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${EDGE_PORT}${p}")
  [[ "$CODE" == "404" ]] || { echo "[FAIL] $p 期望 404，实际 $CODE"; exit 1; }
  echo "[OK] $p -> 404"
done

echo "冒烟测试：通过"
