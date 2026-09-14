# LANG-P1-07 edge 网关镜像（任务 2.1/4.1-4.3）：把 gateway/nginx.conf 烘焙为受限模板，
# 避免用宿主机只读挂载覆盖线上网关行为。基础镜像与生产 Compose 保持同一 digest。
FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10

ARG PROJECT_VERSION=development
ARG GIT_REVISION=development

LABEL org.opencontainers.image.title="lang-api edge gateway" \
      org.opencontainers.image.version="${PROJECT_VERSION}" \
      org.opencontainers.image.revision="${GIT_REVISION}"

# 网关资源边界默认值（任务 4.1-4.3）：部署时由 Compose 环境变量显式覆盖，
# 模板只替换 NGINX_ENVSUBST_FILTER 允许的变量。
ENV GATEWAY_CLIENT_MAX_BODY=10m \
    GATEWAY_CONNECT_TIMEOUT=3s \
    GATEWAY_SEND_TIMEOUT=60s \
    GATEWAY_READ_TIMEOUT=600s \
    GATEWAY_RATE_LIMIT=10r/s \
    GATEWAY_RATE_BURST=20 \
    GATEWAY_CONN_LIMIT=20

# 官方入口把这里当正则表达式逐个匹配变量名（不是空格分隔的名单），
# 所以多个变量用竖线隔开；Nginx 自身的 $request_id 等变量保持原样。
ENV NGINX_ENVSUBST_FILTER="PORTAL_SERVER_NAME|MODEL_API_SERVER_NAME|GATEWAY_CLIENT_MAX_BODY|GATEWAY_CONNECT_TIMEOUT|GATEWAY_SEND_TIMEOUT|GATEWAY_READ_TIMEOUT|GATEWAY_RATE_LIMIT|GATEWAY_RATE_BURST|GATEWAY_CONN_LIMIT"

COPY nginx.conf /etc/nginx/templates/default.conf.template
