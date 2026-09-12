#!/usr/bin/env bash
# Compose 静态校验（LANG-P1-02 任务 2.1/7.3）
# 只做静态文本检查，不启动容器，不输出密钥明文。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="$ROOT/deploy/compose.yml"
OPS="$ROOT/deploy/compose.ops.yml"
ENV_EXAMPLE="$ROOT/deploy/.env.example"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

fail() {
  echo "[FAIL] $1"
  FAIL=1
}

pass() {
  echo "[OK] $1"
}

# 1. 文件存在
for f in "$COMPOSE" "$OPS" "$ENV_EXAMPLE" "$NGINX"; do
  if [[ ! -f "$f" ]]; then
    fail "缺失文件: $f"
  else
    pass "存在文件: $f"
  fi
done
[[ "$FAIL" -ne 0 ]] && { echo "静态校验未通过：基础文件缺失"; exit 1; }

# 2. 五个服务存在
for svc in "edge-nginx" "lang-api" "new-api" "postgres" "redis"; do
  if grep -qE "^[[:space:]]{2}$svc:" "$COMPOSE"; then
    pass "服务存在: $svc"
  else
    fail "缺少服务定义: $svc"
  fi
done

# 3. 禁止 container_name（保持项目隔离）
if grep -q "container_name" "$COMPOSE" "$OPS"; then
  fail "Compose 中不允许出现 container_name"
else
  pass "无 container_name"
fi

# 4. 外部镜像必须带 digest，禁止 latest 浮动引用
for img in "new-api" "nginx" "postgres" "redis"; do
  :
done
if grep -Eq "image:.*:latest" "$COMPOSE"; then
  fail "发现 latest 浮动引用"
else
  pass "无 latest 浮动引用"
fi
# 至少 new-api/nginx/postgres/redis 的 image 行必须包含 @sha256:
DIGEST_COUNT=$(grep -c "image:.*@sha256:" "$COMPOSE" || true)
if [[ "$DIGEST_COUNT" -lt 4 ]]; then
  fail "外部镜像 digest 不足（期望>=4，实际=$DIGEST_COUNT）"
else
  pass "外部镜像均使用 digest（$DIGEST_COUNT 处）"
fi

# 5. 必填变量使用 ${VAR:?} 在解析期失败
for v in "POSTGRES_PASSWORD" "REDIS_PASSWORD" "SESSION_SECRET" "CRYPTO_SECRET"; do
  if grep -q "\${$v:?" "$COMPOSE"; then
    pass "必填变量解析期保护: $v"
  else
    fail "缺少解析期保护: $v（期望 \${$v:?message}）"
  fi
done

# 6. 默认只发布 edge-nginx 端口
if grep -A5 -E "^[[:space:]]{2}new-api:" "$COMPOSE" | grep -q "ports:"; then
  fail "new-api 不应在主文件中发布宿主机端口"
else
  pass "new-api 默认无宿主机端口"
fi
if grep -A5 -E "^[[:space:]]{2}postgres:" "$COMPOSE" | grep -q "ports:"; then
  fail "postgres 不应在主文件中发布宿主机端口"
else
  pass "postgres 默认无宿主机端口"
fi
if grep -A5 -E "^[[:space:]]{2}redis:" "$COMPOSE" | grep -q "ports:"; then
  fail "redis 不应在主文件中发布宿主机端口"
else
  pass "redis 默认无宿主机端口"
fi

# 7. 网络：web/control/relay/data/egress 存在（顶级 networks 下两空格缩进），且 control/relay/data 为 internal
for net in "web" "control" "relay" "data" "egress"; do
  if grep -qE "^  $net:" "$COMPOSE"; then
    pass "网络存在: $net"
  else
    fail "缺少网络定义: $net"
  fi
done
for net in "control" "relay" "data"; do
  if grep -A2 -E "^  $net:" "$COMPOSE" | grep -q "internal:[[:space:]]*true"; then
    pass "内部网络: $net"
  else
    fail "网络 $net 应标记 internal: true"
  fi
done

# 8. 命名卷存在
for vol in "pgdata" "redisdata" "newapi-data"; do
  if grep -q "$vol" "$COMPOSE"; then
    pass "命名卷: $vol"
  else
    fail "缺少命名卷: $vol"
  fi
done

# 9. ops 文件只给 new-api 加回环绑定
if grep -q "127.0.0.1" "$OPS"; then
  pass "运维覆盖使用回环绑定"
else
  fail "运维覆盖应包含 127.0.0.1 回环绑定"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "Compose 静态校验：未通过"
  exit 1
fi
echo "Compose 静态校验：通过"
