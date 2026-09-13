# 仅供测试使用的 Relay fixture 镜像（LANG-P1-07 任务 1.1）。
# digest 于 2026-09-13 经可达镜像站的 registry API 核验（官方仓库直连超时，
# 内容寻址保证指纹与官方一致）：python:3.11-alpine。
# 该镜像不加入生产拓扑，只被 integration-tests/gateway/compose.relay-test.yml 引用。
FROM python:3.11-alpine@sha256:0d55920083f1ce1e38ac292e2772f924b4f8bb4188d336c79bf66963039e6146

WORKDIR /srv
COPY server.py .
EXPOSE 3000
CMD ["python3", "server.py"]
