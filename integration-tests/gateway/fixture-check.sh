#!/usr/bin/env bash
# 网关测试基线检查（LANG-P1-07 任务 1.1/1.2）
# 校验：Relay fixture 存在且版本固定、支持普通 JSON/分段 SSE/状态延时断连/计数/取消观测/Header 回显；
# 测试 overlay 接入 relay 网络且不改变生产拓扑、不发布宿主机端口。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIXTURE_DIR="$ROOT/integration-tests/gateway/fixture"
FIXTURE_SERVER="$FIXTURE_DIR/server.py"
FIXTURE_DOCKERFILE="$FIXTURE_DIR/Dockerfile"
OVERLAY="$ROOT/integration-tests/gateway/compose.relay-test.yml"
PROD_COMPOSE="$ROOT/deploy/compose.yml"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

# 1.1 fixture 文件存在
for f in "$FIXTURE_SERVER" "$FIXTURE_DOCKERFILE"; do
  if [[ -f "$f" ]]; then
    pass "存在文件: $f"
  else
    fail "缺失文件: $f"
  fi
done

# fixture 服务端能力关键字（最小实现必须包含这些分支）
if [[ -f "$FIXTURE_SERVER" ]]; then
  for kw in "chat/completions" "v1/models" "text/event-stream" "request_count" "cancelled" "X-Request-Id" "Authorization"; do
    if grep -q "$kw" "$FIXTURE_SERVER"; then
      pass "fixture 支持能力: $kw"
    else
      fail "fixture 缺少能力: $kw"
    fi
  done
  # 状态/延时/断连控制参数
  for kw in "status" "delay" "disconnect"; do
    if grep -q "$kw" "$FIXTURE_SERVER"; then
      pass "fixture 支持控制参数: $kw"
    else
      fail "fixture 缺少控制参数: $kw"
    fi
  done
fi

# fixture Dockerfile 必须 digest 锁定且不使用 latest
if [[ -f "$FIXTURE_DOCKERFILE" ]]; then
  if grep -Eq "image:.*:latest|FROM.*:latest" "$FIXTURE_DOCKERFILE"; then
    fail "fixture Dockerfile 不允许使用 latest 浮动引用"
  else
    pass "fixture 无 latest 引用"
  fi
  if grep -q "@sha256:" "$FIXTURE_DOCKERFILE"; then
    pass "fixture 基础镜像已 digest 锁定"
  else
    fail "fixture 基础镜像必须包含 @sha256 digest 锁定"
  fi
fi

# 1.2 overlay 存在且接入 relay 网络
if [[ -f "$OVERLAY" ]]; then
  pass "存在测试 overlay: $OVERLAY"
  if grep -q "relay" "$OVERLAY"; then
    pass "overlay 接入 relay 网络"
  else
    fail "overlay 必须接入 relay 网络"
  fi
  # overlay 不得发布宿主机端口（ports:）
  if grep -qE "^[[:space:]]*ports:" "$OVERLAY"; then
    fail "测试 overlay 不得发布宿主机端口"
  else
    pass "overlay 未发布宿主机端口"
  fi
  # overlay 不得修改生产五服务定义（不得重定义 edge/lang-api/postgres/redis 的 image/build）
  for svc in "edge-nginx" "lang-api" "postgres" "redis"; do
    if grep -qE "^[[:space:]]{2}$svc:" "$OVERLAY"; then
      fail "overlay 不得重定义生产服务: $svc"
    fi
  done
  pass "overlay 未污染生产服务定义"
else
  fail "缺失测试 overlay: $OVERLAY"
fi

# 生产拓扑不受影响：主 compose 仍只有 edge 发布端口
if [[ -f "$PROD_COMPOSE" ]]; then
  if grep -A5 -E "^[[:space:]]{2}new-api:" "$PROD_COMPOSE" | grep -q "ports:"; then
    fail "生产 compose 的 new-api 不应发布宿主机端口"
  else
    pass "生产 new-api 仍无宿主机端口"
  fi
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "网关 fixture 基线检查：未通过"
  exit 1
fi
echo "网关 fixture 基线检查：通过"
