#!/usr/bin/env bash
# 网关镜像与虚拟主机检查（LANG-P1-07 任务 2.1-2.5）
# 校验：Dockerfile digest 锁定 + OCI label；nginx.conf 为受限模板且声明两个互斥虚拟主机；
# 用户站点迁移既有行为；模型站点默认拒绝；nginx -t 与 envsubst/label 自动化检查。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOCKERFILE="$ROOT/gateway/Dockerfile"
NGINX="$ROOT/gateway/nginx.conf"
FAIL=0

fail() { echo "[FAIL] $1"; FAIL=1; }
pass() { echo "[OK] $1"; }

# 2.1 Dockerfile
[[ -f "$DOCKERFILE" ]] || { fail "缺失文件: $DOCKERFILE"; exit 1; }
pass "存在文件: $DOCKERFILE"
grep -q "@sha256:" "$DOCKERFILE" || fail "Dockerfile 基础镜像必须 digest 锁定"
grep -Eq "FROM.*:latest" "$DOCKERFILE" && fail "Dockerfile 不允许 latest" || pass "Dockerfile 无 latest 引用"
grep -q "org.opencontainers.image.version" "$DOCKERFILE" || fail "缺少 OCI version label"
grep -q "org.opencontainers.image.revision" "$DOCKERFILE" || fail "缺少 OCI revision label"
grep -qi "templates/default.conf.template" "$DOCKERFILE" || fail "Dockerfile 应把配置烘焙到 templates/default.conf.template"
pass "Dockerfile 镜像烘焙与 label 声明存在"

# 2.2 模板变量：两个互斥 server_name，且不得把宿主机挂载覆盖写进 Dockerfile 以外的运行配置
grep -q "PORTAL_SERVER_NAME" "$NGINX" || fail "nginx.conf 缺少 PORTAL_SERVER_NAME"
grep -q "MODEL_API_SERVER_NAME" "$NGINX" || fail "nginx.conf 缺少 MODEL_API_SERVER_NAME"
pass "模板声明两个虚拟主机变量"
# 模板中不得再出现 server_name _ 通配默认站点
if grep -q "server_name _;" "$NGINX"; then
  fail "模板不得保留 server_name _ 通配默认站点，应拆分为两个互斥虚拟主机"
else
  pass "无通配默认站点"
fi
# 受限替换痕迹：Dockerfile 应声明 NGINX_ENVSUBST_FILTER
grep -q "NGINX_ENVSUBST_FILTER" "$DOCKERFILE" || fail "Dockerfile 应声明受限 NGINX_ENVSUBST_FILTER"
pass "受限 envsubst 声明存在"

# 2.3 用户站点迁移既有行为
grep -q "location /portal/api/" "$NGINX" || fail "缺少 /portal/api/ 转发"
grep -q "location = /healthz" "$NGINX" || fail "缺少 /healthz"
grep -q "lang-api:8080" "$NGINX" || fail "缺少到 lang-api:8080 的转发"
pass "用户站点既有行为已迁移"

# 2.4 模型站点默认拒绝页面、Portal API、管理面与兜底
for p in "/api/" "/setup/" "/portal/api/"; do
  if grep -q "location $p" "$NGINX"; then
    pass "模型边界声明: $p"
  else
    fail "缺少模型边界声明: $p"
  fi
done
# 模型站点必须有兜底拒绝（location / 返回自有 404）
if grep -q 'location / ' "$NGINX" || grep -q 'location /{' "$NGINX"; then
  pass "存在兜底 location 检查点"
else
  fail "缺少兜底 location / 拒绝"
fi

# 2.5 nginx -t 语法检查（使用 digest 锁定的官方镜像，不启动项目应用）
if command -v docker >/dev/null 2>&1; then
  NGINX_IMAGE="$(grep -oE "FROM[^@]*@sha256:[0-9a-f]{64}" "$DOCKERFILE" | grep -oE "nginx[^@]*@sha256:[0-9a-f]{64}" | head -n1 || true)"
  if [[ -z "$NGINX_IMAGE" ]]; then
    fail "无法从 Dockerfile 解析 digest 锁定的 nginx 镜像"
  else
    # 用 envsubst 还原模板变量后做 nginx -t（仅静态校验）。
    # --add-host 让 new-api / lang-api 在校验容器内可解析（只验证配置，不断言连通）。
    TMPDIR_TEST="$(mktemp -d)"
    trap 'rm -rf "$TMPDIR_TEST"' EXIT
    # 只还原模板占位符 ${...}；Nginx 自身变量（$request_id 等）保持原样，
    # 它们在 nginx -t 下本来就是合法的，替换反而会造出非法条件。
    # 网关变量默认值以 Dockerfile 的 ENV 为准（单一来源），主机名用测试值。
    SED_ARGS=(-e 's/\${PORTAL_SERVER_NAME}/localhost/g' -e 's/\${MODEL_API_SERVER_NAME}/api.localhost/g')
    while IFS='=' read -r key value; do
      [[ -n "$key" && -n "$value" ]] && SED_ARGS+=(-e "s|\${$key}|$value|g")
    done < <(grep -oE 'GATEWAY_[A-Z_]+=[^ \]+' "$DOCKERFILE")
    sed "${SED_ARGS[@]}" "$NGINX" > "$TMPDIR_TEST/default.conf"
    if docker run --rm \
      --add-host=new-api:127.0.0.1 --add-host=lang-api:127.0.0.1 \
      -v "$TMPDIR_TEST/default.conf:/etc/nginx/conf.d/default.conf:ro" \
      "$NGINX_IMAGE" nginx -t 2>&1; then
      pass "nginx -t 语法通过"
    else
      fail "nginx -t 语法未通过"
    fi
    rm -rf "$TMPDIR_TEST"
    trap - EXIT
  fi
else
  fail "缺少 docker，无法执行 nginx -t"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "网关镜像与虚拟主机检查：未通过"
  exit 1
fi
echo "网关镜像与虚拟主机检查：通过"
