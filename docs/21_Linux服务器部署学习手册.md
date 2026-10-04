# Linux 服务器部署学习手册

本文以当前 Lang API 仓库为基础，按“理解配置 → 修改项目 → 在服务器运行 → 正式访问 → 维护”的顺序实践。你已经会连接 Linux，因此本文从项目和运行环境开始。

编写日期：2026-10-04。本文中的生产配置是本次给出的部署方案，不是仓库已经实现并在真实服务器验收过的配置。本文只创建学习文档；下面标注“新建”的部署文件，需要你实践时再创建。我们讨论后可继续调整同一份文档。

## 阅读与学习记录

不用一次读完。建议先读第 1 章，每次讨论一个小节；确认理解后再操作。所有命令都标明执行位置。服务器命令默认从项目根目录 `/opt/lang-api` 执行；操作系统安装命令不要求进入该目录。

| 部分 | 理解状态 | 操作状态 | 讨论记录 |
|---|---|---|---|
| 1.1～1.3 服务、镜像与配置关系 | 未讨论 | 未操作 | — |
| 1.4 Compose 字段 | 未讨论 | 未操作 | — |
| 1.5～1.6 环境与命令 | 未讨论 | 未操作 | — |
| 2 部署方案与准备信息 | 未讨论 | 未操作 | — |
| 3 项目文件修改 | 未讨论 | 未操作 | — |
| 4 服务器准备与首次启动 | 未讨论 | 未操作 | — |
| 5 管理初始化 | 未讨论 | 未操作 | — |
| 6 HTTPS 与真实业务验证 | 未讨论 | 未操作 | — |
| 7 安全与长期运行 | 未讨论 | 未操作 | — |
| 8 更新、备份和排障 | 未讨论 | 未操作 | — |

理解状态使用“未讨论 / 已讨论 / 已理解”；操作状态使用“未操作 / 已操作 / 已验证 / 有阻塞”。讨论过不自动等于验证过。后续每次讨论，在对应行记录日期、问题和结论；有需要再拆成更细的行。

## 目录

1. [认识本项目的 Docker Compose](#1-认识本项目的-docker-compose)
2. [确定 Linux 部署方案](#2-确定-linux-部署方案)
3. [项目中具体要修改哪些文件](#3-项目中具体要修改哪些文件)
4. [准备服务器并完成第一次启动](#4-准备服务器并完成第一次启动)
5. [初始化 New API 并准备业务](#5-初始化-new-api-并准备业务)
6. [配置正式访问并验证真实业务](#6-配置正式访问并验证真实业务)
7. [安全与长期运行](#7-安全与长期运行)
8. [更新、备份、恢复和故障处理](#8-更新备份恢复和故障处理)

## 1. 认识本项目的 Docker Compose

### 1.1 先知道我们到底要运行什么

这个项目不是只启动一个 Java 程序。现有 `deploy/compose.yml` 组合了五个服务：

| Compose 服务名 | 运行的内容 | 在项目中的作用 |
|---|---|---|
| `edge-nginx` | Nginx | 唯一业务入口，区分网站和模型 API 请求 |
| `lang-api` | 前端页面 + Java 后端 | 提供 Lang API 页面、登录、用户控制台等接口 |
| `new-api` | New API | 管理用户、模型渠道、令牌、用量与计费，向供应商发起模型请求 |
| `postgres` | PostgreSQL | 保存 New API 的业务数据 |
| `redis` | Redis | 保存运行数据和 Lang API 的服务端登录会话 |

网站请求经过 `edge-nginx` 到 `lang-api`；后者按需要访问 `new-api` 和 Redis。模型调用经过 `edge-nginx` 直接到 `new-api`，不经过 Java 后端。

前端构建后被放进 Java 的 JAR 包，因此没有单独的前端容器。服务器不需要另开 Vite 开发服务器。

### 1.2 镜像、容器和 Compose 分别负责什么

**镜像**是程序和运行环境的安装包；**容器**是使用这个安装包启动的运行实例。我们改完源码后，要重新构建镜像，再用新镜像重建容器，修改才会进入运行中的程序。

**Docker Compose**读取 YAML 配置，统一管理多个容器、网络和数据卷。YAML 就是这里使用的配置文件格式，靠缩进表示层级，不能用 Tab 混排。

在本项目中，一次 `docker compose up` 会根据配置拉取或构建镜像、准备网络和卷、启动服务。它不会替你购买服务器、配置 DNS、初始化管理员或填写供应商密钥。

### 1.3 文件怎样配合

| 文件 | 读取它的程序 | 作用 |
|---|---|---|
| 根目录 `Dockerfile` | Docker 构建器 | 在构建容器中安装 Java/Maven，构建前后端，再生成运行镜像 |
| `gateway/Dockerfile` | Docker 构建器 | 把项目网关配置放入 Nginx 镜像 |
| `gateway/nginx.conf` | 容器中的 Nginx | 决定网站和模型请求转发到哪里 |
| `deploy/compose.yml` | Compose | 描述本地五服务及其连接关系 |
| `deploy/compose.ops.yml` | Compose | 在主配置上增加 New API 本机管理端口 |
| `deploy/.env` | Compose | 为 `${变量名}` 提供具体值 |
| `application-prod.properties` | Java 后端 | 定义后端生产默认值，并从环境变量取得站点参数 |

注意区别：`.env` 中写了一个变量，**不代表它就进入 Java 容器**。Compose 需要在 `environment` 中引用并传入它，Java 配置或代码再读取这个变量。

例如下面是完整传递链：

```text
环境文件：PORTAL_SITE_NAME=我的 API 站点
Compose environment：PORTAL_SITE_NAME: ${PORTAL_SITE_NAME}
application-prod.properties：lang.portal.site-name=${PORTAL_SITE_NAME:}
Java 后端：使用站点名称
```

### 1.4 看懂 Compose 的主要字段

以下片段用于解释，不要拿片段直接替换完整文件。

**`build` 与 `image`：服务从哪里来**

```yaml
services:
  lang-api:
    build:
      context: ..
      dockerfile: Dockerfile
  postgres:
    image: postgres:16-alpine@sha256:实际指纹
```

`build` 表示用我们的代码构建镜像；`image` 表示使用指定镜像。`context: ..` 以 Compose 文件所在目录为基准，指向仓库根目录。`sha256` 是镜像内容指纹，当前项目用它固定外部组件；首次部署沿用，不顺便升级。

当一个服务同时有 `build` 与 `image`，可以把构建结果命名为 `image` 指定的名称，方便保存旧版本。

**`environment`：传给容器内程序的参数**

```yaml
environment:
  SPRING_PROFILES_ACTIVE: prod
  REDIS_PASSWORD: ${REDIS_PASSWORD:?请设置 REDIS_PASSWORD}
```

第一项传固定值；第二项从环境文件取值。`${变量:?错误消息}` 表示不能为空，缺少时 Compose 在启动前报错。`${变量:-默认值}` 表示没提供时使用默认值。

**`ports`：服务器外怎样进入容器**

```yaml
ports:
  - "127.0.0.1:13000:3000"
```

从左到右是“服务器监听地址 : 服务器端口 : 容器端口”。这里服务器本机的 `13000` 转到 New API 容器的 `3000`。`127.0.0.1` 限制为本机访问；远程电脑不能直接打开它，需要 SSH 隧道。

不写服务器监听地址，通常会监听所有地址。数据库和 Redis 不需要发布端口：同一 Docker 网络中的服务已经能访问它们。

**`networks`：容器之间谁能连接谁**

现有配置分出 `web`、`control`、`relay`、`data`、`egress` 网络。服务只加入自己需要的网络；`internal: true` 的网络用于内部通信。New API 通过 `egress` 访问外部供应商。

在网络内，`new-api`、`redis`、`postgres` 等服务名就是可用的主机名。Java 容器里的 `localhost` 指向 Java 容器自己，所以它访问上游时用 `http://new-api:3000`，不能填 `http://localhost:3000`。

**`volumes`：数据放在哪里**

```yaml
volumes:
  - pgdata:/var/lib/postgresql/data
```

`pgdata` 是命名数据卷——Docker 管理的持久存储；右边是容器内数据库目录。更换应用容器不会自动清空命名卷。普通 `down` 保留卷，`down -v` 会删除卷及其数据。

`/etc/letsencrypt:/etc/letsencrypt:ro` 则是把服务器目录挂进容器；`ro` 表示只读。证书续期在服务器上完成，容器只读取证书。

**`depends_on` 与 `healthcheck`：什么时候启动**

当前配置要求数据库、Redis 健康后启动 New API，New API 和 Redis 健康后启动 Java，最后启动网关。健康检查是周期执行的一条探测命令，不是完整业务验收。

`depends_on` 主要约束启动，不负责自动修好数据库，也不能保证已经初始化管理员。`unhealthy` 不会因为设置了自动重启就必然重启容器。

**`restart` 与 `logging`：长期运行**

本文生产配置使用 `restart: unless-stopped`：进程退出或 Docker 恢复时自动拉起，主动停止的服务不自动恢复。Docker 日志配置限制每个容器的标准输出日志大小；应用自己写入卷中的日志需要另管。参见 [Docker 服务字段](https://docs.docker.com/reference/compose-file/services/) 和 [日志轮转配置](https://docs.docker.com/engine/logging/drivers/json-file/)。

### 1.5 怎样区分环境

这里有三层，不能混为一谈：

| 层次 | 控制什么 | 本文做法 |
|---|---|---|
| Compose 配置 | 服务、网络、端口、卷、启动参数 | 本地用 `compose.yml`，服务器用独立 `compose.prod.yml` |
| 环境文件 | 域名、密码、版本等具体取值 | 本地用 `deploy/.env`，服务器用 `/etc/lang-api/production.env` |
| Spring Profile | Java 加载哪套应用配置 | 生产 Compose 显式设置 `SPRING_PROFILES_ACTIVE=prod` |

根 `Dockerfile` 默认是 `prod`，但现有本地 Compose 显式写了 `dev`，运行时会覆盖镜像默认值。所以把现有 Compose 搬到服务器，并不会自动变成生产环境。

`prod` 控制后端生产运行规则；`PORTAL_PUBLICATION_MODE=PUBLIC` 控制站点公开发布状态。先使用 `prod + PREVIEW + 关闭注册` 邀请试用；不需要为跑通项目先开放公众注册。

**不同环境还需要隔离数据。** Compose 项目名会用于默认容器、网络和命名卷名称。当前本地名为 `lang-api`；本文生产名为 `lang-api-prod`。环境文件不同，但项目名和卷相同，仍可能共用数据。后续测试环境还需独立项目名、端口、域名与网段。

多个 `-f` 会从左到右合并：标量、映射和列表的合并规则不同，不能简单理解为“后面整个替换前面”。尤其端口列表可能追加，错误叠加可能同时留下本地和公网入口。本文生产文件是完整文件，**不要与本地 `compose.yml` 一起使用**；只按需叠加 `compose.ops.yml`。参见 [Compose 合并规则](https://docs.docker.com/compose/how-tos/multiple-compose-files/merge/)。

### 1.6 先认识常用命令

| 命令 | 用途 | 修改源码后是否足够 |
|---|---|---|
| `config --quiet` | 检查配置能否解析 | 不更新程序 |
| `build` | 构建镜像 | 还需重建容器 |
| `up -d --build` | 构建并按当前配置启动或重建 | 通常用于代码更新 |
| `up -d` | 按当前配置启动或重建 | 会应用环境变化，但不会自动构建源码 |
| `ps` | 查看状态 | 不修改服务 |
| `logs` | 查看输出日志 | 不修改服务 |
| `exec` | 在运行容器里执行命令 | 适用于探测或数据库备份 |
| `restart` | 重启原容器 | 不加载修改后的 Compose 环境变量 |
| `stop` | 停止容器 | 保留容器和数据 |
| `down` | 删除本项目容器和网络 | 保留命名卷；不要加 `-v` |

## 2. 确定 Linux 部署方案

### 2.1 本文采用的路线

使用一台服务器、Docker Engine 和 Compose，在服务器上构建镜像。沿用五服务结构，由 `edge-nginx` 直接处理正式域名和证书，不额外装一层宿主机 Nginx。

先在服务器回环地址启动 HTTP 入口，只检查服务、页面与管理初始化；随后把同一个网关改为 HTTPS 并开放正式端口。容器和数据库继续使用同一个生产项目，避免“试运行后又换一套空数据库”。生产登录 Cookie 要求 HTTPS，网页登录和用户模型调用验收放在第 6 章。

本文安装命令以 **Ubuntu Server 24.04 LTS、具有 sudo 权限的普通账号**为具体示例。服务器系统尚未确认；如果实际是 Debian、Rocky、Alibaba Cloud Linux 等，第 4 章安装命令需要先替换，不能直接照抄。

### 2.2 需要准备的具体值

| 项目 | 文中示例 | 实践时填写 |
|---|---|---|
| 网站域名 | `portal.example.com` | 待填写 |
| 模型 API 域名 | `api.example.com` | 待填写 |
| 服务器公网地址 | `SERVER_IP` | 待填写 |
| SSH 账号 | `deployuser` | 待填写 |
| 项目目录 | `/opt/lang-api` | 默认沿用 |
| 外部环境文件 | `/etc/lang-api/production.env` | 默认沿用 |
| 网关固定内部地址 | `172.30.10.10` | 检查网段无冲突后沿用 |
| 初次部署版本名 | `first-20261004` | 按实际日期/版本填写 |
| 支持邮箱、运营地区 | 不生成真实值 | 对公众开放前填写 |

`example.com` 是示例域名，不能申请你的项目证书。本文不假设服务器已有数据；第一次部署默认创建空数据库。已有数据需先安排迁移和备份，不能混用首次初始化步骤。

### 2.3 目录与端口

代码在 `/opt/lang-api`；密码和密钥在 `/etc/lang-api/production.env`；证书在 `/etc/letsencrypt`；证书验证文件在 `/var/www/letsencrypt`；数据库在 Docker 命名卷。备份另放 `/var/backups/lang-api`，再复制到服务器之外。

初次内部试运行：网关 `127.0.0.1:8081`，管理入口 `127.0.0.1:13000`。正式访问：网关发布 `80`、`443`；管理入口仍只发布到本机，数据库、Redis 和 Java 不发布公网端口。

## 3. 项目中具体要修改哪些文件

### 3.1 修改清单

| 文件 | 操作 | 用途 |
|---|---|---|
| `deploy/compose.prod.yml` | 新建 | 完整生产服务配置，不叠加本地主文件 |
| `gateway/Dockerfile.prod` | 新建 | 构建独立生产网关，沿用现有镜像基线 |
| `gateway/nginx.prod.conf` | 新建并分两步编辑 | 第一阶段内部 HTTP；第二阶段正式 HTTPS |
| `/etc/lang-api/production.env` | 服务器新建 | 保存实际域名、密码、密钥、版本 |
| 根 `Dockerfile` | 首次部署保持 | 已能构建前后端一体化镜像 |
| `application-prod.properties` | 首次部署保持 | 已支持本文需要的生产环境变量 |
| `deploy/maven-settings.docker.xml` | 保持 | 容器构建使用的 Maven 配置 |
| `deploy/compose.ops.yml` | 保持，按需叠加 | 管理端口只监听本机 |
| 本地 `compose.yml`、`deploy/.env` | 保持 | 继续用于你的 Mac 本地开发 |

本章代码块是需要创建的文件内容；这次编写文档没有替你创建它们。项目文件可先在 Mac 编辑后上传；服务器环境文件只在服务器创建。

### 3.2 新建生产 Compose 文件

新建 `deploy/compose.prod.yml`，填入下列完整内容。

`x-runtime` 和 `&runtime` 是 YAML 复用配置的写法；每个服务的 `<<: *runtime` 引入相同的自动重启和日志设置。第一次学习时把它理解为“把这段公共配置复制到各服务”。

```yaml
name: lang-api-prod

x-runtime: &runtime
  restart: unless-stopped
  logging:
    driver: json-file
    options:
      max-size: "10m"
      max-file: "3"

services:
  edge-nginx:
    <<: *runtime
    image: lang-api-edge:${DEPLOY_VERSION:?请设置 DEPLOY_VERSION}
    build:
      context: ../gateway
      dockerfile: Dockerfile.prod
      args:
        PROJECT_VERSION: ${DEPLOY_VERSION:?请设置 DEPLOY_VERSION}
        GIT_REVISION: ${GIT_REVISION:?请设置 GIT_REVISION}
    environment:
      PORTAL_SERVER_NAME: ${PORTAL_SERVER_NAME:?请在 production.env 设置 PORTAL_SERVER_NAME}
      MODEL_API_SERVER_NAME: ${MODEL_API_SERVER_NAME:?请在 production.env 设置 MODEL_API_SERVER_NAME}
      GATEWAY_CLIENT_MAX_BODY: ${GATEWAY_CLIENT_MAX_BODY:-10m}
      GATEWAY_CONNECT_TIMEOUT: ${GATEWAY_CONNECT_TIMEOUT:-3s}
      GATEWAY_SEND_TIMEOUT: ${GATEWAY_SEND_TIMEOUT:-60s}
      GATEWAY_READ_TIMEOUT: ${GATEWAY_READ_TIMEOUT:-600s}
      GATEWAY_RATE_LIMIT: ${GATEWAY_RATE_LIMIT:-10r/s}
      GATEWAY_RATE_BURST: ${GATEWAY_RATE_BURST:-20}
      GATEWAY_CONN_LIMIT: ${GATEWAY_CONN_LIMIT:-20}
    depends_on:
      lang-api:
        condition: service_healthy
    ports:
      - "${EDGE_BIND_ADDRESS:-127.0.0.1}:${EDGE_HTTP_PORT:-8081}:80"
      - "${EDGE_BIND_ADDRESS:-127.0.0.1}:${EDGE_HTTPS_PORT:-8443}:443"
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /var/www/letsencrypt:/var/www/letsencrypt:ro
    networks:
      web:
        ipv4_address: 172.30.10.10
      relay: {}
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://127.0.0.1/healthz || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 20s

  lang-api:
    <<: *runtime
    image: lang-api-app:${DEPLOY_VERSION:?请设置 DEPLOY_VERSION}
    build:
      context: ..
      dockerfile: Dockerfile
      secrets:
        - maven_settings
    environment:
      SPRING_PROFILES_ACTIVE: prod
      NEW_API_BASE_URL: http://new-api:3000
      PORTAL_SITE_NAME: ${PORTAL_SITE_NAME:?请设置 PORTAL_SITE_NAME}
      PORTAL_ALLOWED_ORIGINS: ${PORTAL_ALLOWED_ORIGINS:?请设置 PORTAL_ALLOWED_ORIGINS}
      PORTAL_TRUSTED_PROXY_CIDRS: 172.30.10.10/32
      PORTAL_REGISTRATION_ENABLED: ${PORTAL_REGISTRATION_ENABLED:-false}
      PORTAL_PUBLICATION_MODE: ${PORTAL_PUBLICATION_MODE:-PREVIEW}
      PORTAL_SITE_URL: ${PORTAL_SITE_URL:?请设置 PORTAL_SITE_URL}
      PORTAL_SUPPORT_URL: ${PORTAL_SUPPORT_URL:-}
      PORTAL_SUPPORTED_REGIONS: ${PORTAL_SUPPORTED_REGIONS:-}
      PORTAL_ENABLED_LOCALES: zh-CN
      PORTAL_LEGAL_SOURCE_LOCALE: zh-CN
      PORTAL_CATALOG_QUOTA_PER_USD: ${PORTAL_CATALOG_QUOTA_PER_USD:-500000}
      PORTAL_ENABLED_PROTOCOLS: ${PORTAL_ENABLED_PROTOCOLS:-OPENAI}
      PORTAL_OPENAI_URL: ${PORTAL_OPENAI_URL:?请设置 PORTAL_OPENAI_URL}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD:?请在 production.env 设置 REDIS_PASSWORD}
      SPRING_DATA_REDIS_DATABASE: 1
    depends_on:
      new-api:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - web
      - control
      - data
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 90s

  new-api:
    <<: *runtime
    image: calciumion/new-api:v0.13.2@sha256:0c6aa7afce4747f0fc4fab9c7934d7c2e4b69fda6844065acca6a5e1bf258506
    environment:
      SQL_DSN: "postgres://${POSTGRES_USER:?请在 production.env 设置 POSTGRES_USER}:${POSTGRES_PASSWORD:?请在 production.env 设置 POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB:?请在 production.env 设置 POSTGRES_DB}?sslmode=disable"
      REDIS_CONN_STRING: "redis://:${REDIS_PASSWORD:?请在 production.env 设置 REDIS_PASSWORD}@redis:6379/0"
      SESSION_SECRET: "${SESSION_SECRET:?请在 production.env 设置 SESSION_SECRET}"
      CRYPTO_SECRET: "${CRYPTO_SECRET:?请在 production.env 设置 CRYPTO_SECRET}"
      TZ: Asia/Shanghai
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    volumes:
      - newapi-data:/data
      - newapi-logs:/log
    networks:
      - control
      - relay
      - data
      - egress
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:3000/api/status || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 120s

  postgres:
    <<: *runtime
    image: postgres:16-alpine@sha256:cf78e76683b9ca8c5733cbbdce6c9262b45b6767934dd0a95e671f9a0fc20685
    environment:
      POSTGRES_DB: "${POSTGRES_DB:?请在 production.env 设置 POSTGRES_DB}"
      POSTGRES_USER: "${POSTGRES_USER:?请在 production.env 设置 POSTGRES_USER}"
      POSTGRES_PASSWORD: "${POSTGRES_PASSWORD:?请在 production.env 设置 POSTGRES_PASSWORD}"
      TZ: Asia/Shanghai
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$${POSTGRES_USER}\" -d \"$${POSTGRES_DB}\" || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  redis:
    <<: *runtime
    image: redis:7-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf
    environment:
      REDIS_PASSWORD: "${REDIS_PASSWORD:?请在 production.env 设置 REDIS_PASSWORD}"
    command: ["sh", "-c", "redis-server --requirepass \"$${REDIS_PASSWORD}\" --appendonly yes"]
    volumes:
      - redisdata:/data
    networks:
      - data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"$${REDIS_PASSWORD}\" ping | grep -q PONG || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s

networks:
  web:
    ipam:
      config:
        - subnet: 172.30.10.0/24
  control:
    internal: true
  relay:
    internal: true
  data:
    internal: true
  egress:

volumes:
  pgdata:
  redisdata:
  newapi-data:
  newapi-logs:

secrets:
  maven_settings:
    file: ./maven-settings.docker.xml
```

这里有几处和本地配置不同：

1. 项目名改为 `lang-api-prod`，生产卷与本地隔离。
2. Java 显式使用 `prod`，明确内部上游地址和生产站点参数。
3. `web` 网段固定，网关固定为 `172.30.10.10`；后端只信任这个代理地址。`/32` 表示只接受这一个 IPv4 地址。
4. 网关先只监听服务器回环地址，后面才开放 80/443。
5. 自有镜像有版本名称，旧镜像可保留；外部镜像沿用仓库的固定指纹。
6. 增加只读证书目录与验证文件目录挂载。

创建前检查服务器路由和已有 Docker 网络：

```bash
# 服务器终端
ip route
sudo docker network ls
sudo docker network inspect $(sudo docker network ls -q) --format '{{.Name}} {{range .IPAM.Config}}{{.Subnet}} {{end}}'
```

若 `172.30.10.0/24` 与已有网络或服务器内网冲突，不启动。选择不冲突网段后，同时修改 Compose 的 `subnet`、网关 `ipv4_address` 和后端 `PORTAL_TRUSTED_PROXY_CIDRS`，三处必须一致。

### 3.3 新建生产网关 Dockerfile

新建 `gateway/Dockerfile.prod`。它沿用当前网关基础镜像与变量替换白名单，只改为读取生产模板。

```dockerfile
FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10

ARG PROJECT_VERSION
ARG GIT_REVISION
LABEL org.opencontainers.image.title="lang-api edge gateway" \
      org.opencontainers.image.version="${PROJECT_VERSION}" \
      org.opencontainers.image.revision="${GIT_REVISION}"

ENV GATEWAY_CLIENT_MAX_BODY=10m \
    GATEWAY_CONNECT_TIMEOUT=3s \
    GATEWAY_SEND_TIMEOUT=60s \
    GATEWAY_READ_TIMEOUT=600s \
    GATEWAY_RATE_LIMIT=10r/s \
    GATEWAY_RATE_BURST=20 \
    GATEWAY_CONN_LIMIT=20

ENV NGINX_ENVSUBST_FILTER="PORTAL_SERVER_NAME|MODEL_API_SERVER_NAME|GATEWAY_CLIENT_MAX_BODY|GATEWAY_CONNECT_TIMEOUT|GATEWAY_SEND_TIMEOUT|GATEWAY_READ_TIMEOUT|GATEWAY_RATE_LIMIT|GATEWAY_RATE_BURST|GATEWAY_CONN_LIMIT"
COPY nginx.prod.conf /etc/nginx/templates/default.conf.template
```

容器启动时，Nginx 官方入口根据环境变量生成最终配置。白名单只允许替换项目变量，保留 `$host`、`$remote_addr` 等 Nginx 自己的变量。不要去容器内临时改生成文件：下次重建会丢失，也无法追踪版本。

### 3.4 创建第一阶段生产网关模板

在项目根目录执行一次：

```bash
# Mac 项目终端，或者服务器上的项目根目录；文件不存在时才复制
cp gateway/nginx.conf gateway/nginx.prod.conf
```

编辑 `gateway/nginx.prod.conf`，找到用户站点的两个位置：`location /` 和 `location /portal/api/`。把两处：

```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

改成：

```nginx
proxy_set_header X-Forwarded-For $remote_addr;
```

本文网关直接面对客户端，不需要保留客户端自己提交的转发地址。重建该请求头后，后端和网关的按 IP 限流才能基于可信来源。保留 `X-Forwarded-Proto $scheme`，切为 HTTPS 后它会自然变成 `https`。

暂时保留两个站点的 `listen 80`，用于内部试运行。其余路由、资源限制、流式响应配置和模型路径白名单沿用现有文件；不要重写成一个无差别转发所有路径的简化网关。

当前模型网关只开放 `GET /v1/models` 和 `POST /v1/chat/completions`；部署不会自动增加 `/v1/responses`、Anthropic 或 Gemini 支持。

### 3.5 服务器环境文件

密码与密钥保存在仓库外，避免上传、提交或进入 Docker 构建上下文。在服务器执行：

```bash
sudo install -d -m 700 /etc/lang-api
sudo install -m 600 /dev/null /etc/lang-api/production.env
sudo nano /etc/lang-api/production.env
```

以上创建命令仅用于第一次；已有文件不要重新用 `/dev/null` 覆盖。`nano` 保存用 `Ctrl+O`、回车，退出用 `Ctrl+X`。

填入以下内容，替换域名和所有 `REPLACE_` 值：

```dotenv
# 第一阶段只允许服务器本机访问；第 6 章再改正式入口
EDGE_BIND_ADDRESS=127.0.0.1
EDGE_HTTP_PORT=8081
EDGE_HTTPS_PORT=8443
NEW_API_ADMIN_PORT=13000

POSTGRES_DB=langapi
POSTGRES_USER=langapi
POSTGRES_PASSWORD=REPLACE_POSTGRES_PASSWORD
REDIS_PASSWORD=REPLACE_REDIS_PASSWORD
SESSION_SECRET=REPLACE_SESSION_SECRET
CRYPTO_SECRET=REPLACE_CRYPTO_SECRET

PORTAL_SERVER_NAME=portal.example.com
MODEL_API_SERVER_NAME=api.example.com
PORTAL_OPENAI_URL=https://api.example.com/v1
PORTAL_ENABLED_PROTOCOLS=OPENAI
PORTAL_SITE_NAME=Lang API
PORTAL_ALLOWED_ORIGINS=https://portal.example.com
PORTAL_SITE_URL=https://portal.example.com

# 先邀请试用，不开启公众注册
PORTAL_PUBLICATION_MODE=PREVIEW
PORTAL_REGISTRATION_ENABLED=false
PORTAL_SUPPORT_URL=
PORTAL_SUPPORTED_REGIONS=
PORTAL_CATALOG_QUOTA_PER_USD=500000

GATEWAY_CLIENT_MAX_BODY=10m
GATEWAY_CONNECT_TIMEOUT=3s
GATEWAY_SEND_TIMEOUT=60s
GATEWAY_READ_TIMEOUT=600s
GATEWAY_RATE_LIMIT=10r/s
GATEWAY_RATE_BURST=20
GATEWAY_CONN_LIMIT=20

DEPLOY_VERSION=first-20261004
GIT_REVISION=REPLACE_SOURCE_REVISION
```

分别执行两次 `openssl rand -hex 24`，填入数据库和 Redis 密码；再分别执行两次 `openssl rand -hex 48`，填入两个 New API 密钥。四个值独立生成，不共用。使用十六进制字符，避免密码拼入连接地址时需要额外编码。

`GIT_REVISION` 可从 Mac 的 `git rev-parse HEAD` 获取；若上传包含未提交变更，记录为“该提交号加 local 标识”，并另存这次上传源码包，不能把提交号当成未提交代码的完整证明。

环境文件由 Compose 读取；无需执行 `source`。完整 `docker compose config` 会展开真实密钥，本文用 `config --quiet` 只检查是否能解析。不要把环境文件和展开配置贴到聊天里。

### 3.6 为什么不改 Java 生产配置与根 Dockerfile

`application-prod.properties` 已读取 `NEW_API_BASE_URL`、`PORTAL_ALLOWED_ORIGINS`、站点名称等环境变量，本文只需在生产 Compose 中补齐传递链。真实域名和密码不硬编码进 Java 文件。

根 `Dockerfile` 已在构建阶段安装 Maven 3.8.4 和 Java 21，并通过根 Maven 构建生成包含前端的 JAR。服务器只需 Docker，不需要在宿主机另装 Java、Maven、Node。

Compose 构建使用仓库的 `deploy/maven-settings.docker.xml`，通过构建 secret 传入，避免写进镜像层。不要把 Mac 的 `/Users/nelson/...` 路径复制成 Linux 启动命令，也不要上传个人 Maven 配置替换项目配置。

## 4. 准备服务器并完成第一次启动

### 4.1 确认系统与资源

```bash
# 服务器终端
cat /etc/os-release
uname -m
free -h
df -h
```

只有确认是 Ubuntu 并适用于 Docker 官方安装方式后，才执行下节。构建前后端需要额外内存和磁盘；实际容量需结合服务器配置观察，本文不把某个资源规格当成已验证的最低要求。

### 4.2 安装 Docker 与 Compose

先检查 `sudo docker version`、`sudo docker compose version`。如果已安装并可用，跳过安装。已有 Docker、containerd 或其他业务容器时，先核对现状，不盲目卸载已有组件。

以下用于新装 Ubuntu 服务器，按 [Docker 官方 Ubuntu 安装说明](https://docs.docker.com/engine/install/ubuntu/) 设置软件源并安装：

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker compose version
```

成功判断：测试容器输出确认消息，Compose 能显示版本。本文后续使用 `sudo docker`，不要求修改 Docker 用户组。

### 4.3 把代码传到服务器

服务器上创建目录，`deployuser` 替换为实际账号：

```bash
sudo install -d -o deployuser -g deployuser /opt/lang-api
```

Mac 项目根目录执行以下命令，包含你新建且尚未提交的生产配置，排除本地密钥和构建产物：

```bash
tar --exclude='.git' --exclude='.idea' --exclude='.agents' \
  --exclude='.claude' --exclude='.DS_Store' \
  --exclude='deploy/.env' --exclude='target' --exclude='node_modules' \
  --exclude='frontend/node' --exclude='frontend/dist' \
  --exclude='backups' --exclude='*.log' --exclude='*.pem' \
  --exclude='*.key' --exclude='*.sql' --exclude='*.dump' \
  -czf /tmp/lang-api-source.tar.gz .
scp /tmp/lang-api-source.tar.gz deployuser@SERVER_IP:/tmp/
```

第一次在服务器解压：

```bash
tar -xzf /tmp/lang-api-source.tar.gz -C /opt/lang-api
cd /opt/lang-api
ls -a
ls deploy/compose.prod.yml gateway/Dockerfile.prod gateway/nginx.prod.conf
sudo install -d -m 755 /etc/letsencrypt /var/www/letsencrypt
```

必须包含根 `.dockerignore`、`pom.xml`、前后端源码、锁文件、`deploy/maven-settings.docker.xml`，以及 Dockerfile 使用的 `docs/new-api/` 脱敏测试证据。不要只上传 JAR；本文选择的是在服务器构建完整项目。

这条传输命令不是任意私人文件的安全过滤器，打包前确认项目没有其他位置的真实密钥。先不要把新包直接覆盖一份已运行的生产代码，更新方法在第 8 章。

### 4.4 定义生产操作快捷命令

为了避免每次写错文件组合，在服务器项目根目录当前 shell 定义：

```bash
cd /opt/lang-api
pc() {
  sudo docker compose --env-file /etc/lang-api/production.env \
    -f /opt/lang-api/deploy/compose.prod.yml "$@"
}
po() {
  sudo docker compose --env-file /etc/lang-api/production.env \
    -f /opt/lang-api/deploy/compose.prod.yml \
    -f /opt/lang-api/deploy/compose.ops.yml "$@"
}
```

`pc` 是生产业务配置，`po` 是生产配置加管理入口。`"$@"` 把你输入的参数原样传给 Compose。两个函数都固定路径，因此不会误读 Mac 环境文件。重新登录后函数会消失，需重新定义；确认常用后可以放进服务器账号的 `~/.bashrc`。

本文下面的 `pc`、`po` 命令都依赖这两个函数。它们不等于项目已经自带的 CLI。

### 4.5 检查、构建、启动

```bash
pc config --quiet
po config --quiet
pc config --services
pc up -d --build
pc ps
```

预期服务列表有五个服务。第一次会拉镜像和依赖；`--build` 构建两套自有镜像，不会构建 PostgreSQL、Redis、New API 源码。

配置检查报错先补参数；构建失败先看日志，不跳过测试强行启动。项目文档中记录过完整构建失败，历史成功或文档示例不能保证当前源码一定能通过；服务器这次构建输出才是依据。

`ps` 最终应显示五个服务运行且 `healthy`。`starting` 可稍后再看；`Exited`、`Restarting`、`unhealthy` 都不算完成。

```bash
pc logs --tail=100 lang-api new-api edge-nginx
curl --fail --silent --show-error http://127.0.0.1:8081/healthz
curl --fail --silent --show-error -H 'Host: portal.example.com' http://127.0.0.1:8081/ -o /tmp/lang-api-home.html
```

健康入口预期为 `ok`；首页保存为 HTML。这一阶段**不验证 HTTP 网页登录**，生产 Cookie 需要 HTTPS；后续不通过关闭 Secure 来绕过。

**本章完成条件：** 两种配置可解析，构建成功，五个服务健康，内部首页和健康入口可响应。

## 5. 初始化 New API 并准备业务

### 5.1 临时打开管理入口

服务器终端：

```bash
po up -d --no-deps new-api
po port new-api 3000
curl --fail --silent --show-error http://127.0.0.1:13000/api/status
```

端口应显示 `127.0.0.1:13000`。管理入口不需要加入公网安全组。

Mac 另开终端建立隧道，替换账号和服务器地址：

```bash
ssh -N -L 13000:127.0.0.1:13000 deployuser@SERVER_IP
```

保持该终端运行，在 Mac 浏览器打开 `http://127.0.0.1:13000`。这里的浏览器访问会通过 SSH 转到服务器本机端口；不是让公网直接访问 New API。

若 Mac 已有本地项目占用 13000，可用 `-L 13001:127.0.0.1:13000`，浏览器改打开本机 13001。

### 5.2 创建管理员与业务试用账号

按初始化向导创建自己的管理员，保存独立强密码；不要复用数据库密码。初始化后再打开页面，确认不再出现首次安装向导。

然后按 [管理员操作手册](用户手册/02_管理员操作手册.md) 的渠道、定价、用户开通步骤准备：

1. 添加真实供应商渠道，填写供应商地址、上游密钥和可用模型。
2. 初次用户、渠道和 API Key 分组保持一致，例如 `default`。
3. 配置模型价格，确认 quota 换算与本文 `PORTAL_CATALOG_QUOTA_PER_USD` 一致。
4. 创建普通试用用户并分配少量额度，检查密码登录能力已启用。

供应商渠道密钥、用户 API Key 和网页登录密码是三个不同凭证。真实渠道测试可能产生供应商费用，使用一条小请求，不批量测试所有渠道。

### 5.3 关闭管理入口

```bash
pc up -d --no-deps new-api
pc ps
pc port new-api 3000
sudo ss -lntp | grep ':13000'
```

最后一条没有匹配、`port` 不再显示映射是预期。`ss` 是辅助检查，还要用 Docker 的实际端口映射确认；有的 Docker 端口不显示为普通宿主机监听进程。Mac 隧道用 `Ctrl+C` 结束。

完成后可以暂时停止并重新启动 New API，确认管理员和试用账号仍在。数据库卷存在只是形式，重新登录并找到账号才是数据保留的证据。

**本章完成条件：** 管理员初始化、渠道和试用账号已准备；管理入口已关闭。网页登录和实际模型调用在第 6 章验证。

## 6. 配置正式访问并验证真实业务

### 6.1 配置域名与证书验证入口

在域名管理平台，把网站和模型 API 两个域名的 A 记录指向服务器公网 IPv4。如果没有正确配置 IPv6，不保留指向错误地址的 AAAA 记录。初次配置使用直接解析到服务器的方式，暂不加 CDN 或其他代理层。

在 Mac 或服务器检查：

```bash
getent ahostsv4 portal.example.com  # Linux
getent ahostsv4 api.example.com
```

Mac 可用 `dig +short portal.example.com` 和 `dig +short api.example.com`。预期解析到目标服务器。

云安全组为网关放行入站 TCP 80、443。SSH 保留现有受控访问；不要放行 3000、5432、6379、8080、13000。

在 `gateway/nginx.prod.conf` 两个现有站点的 `server` 内，各添加下面的位置块：

```nginx
location ^~ /.well-known/acme-challenge/ {
  root /var/www/letsencrypt;
  default_type text/plain;
}
```

它让证书服务访问指定验证文件，与普通用户页面和模型 API 分开。然后编辑 `/etc/lang-api/production.env`：

```dotenv
EDGE_BIND_ADDRESS=0.0.0.0
EDGE_HTTP_PORT=80
EDGE_HTTPS_PORT=443
```

此时暂时开放 HTTP，**只进行证书申请，不让用户在 HTTP 上登录或提交密钥**。执行：

```bash
pc up -d --build edge-nginx
sudo install -d -m 755 /var/www/letsencrypt/.well-known/acme-challenge
printf 'acme-check
' | sudo tee /var/www/letsencrypt/.well-known/acme-challenge/check > /dev/null
curl --fail http://portal.example.com/.well-known/acme-challenge/check
curl --fail http://api.example.com/.well-known/acme-challenge/check
```

也要从 Mac 执行两个 curl，确认公网链路返回 `acme-check`。只有服务器自己访问成功不能证明外部验证服务可达。

### 6.2 安装 Certbot 并申请证书

Certbot 负责申请和续期证书。本文使用宿主机 Certbot 的 **webroot** 方式：在挂载目录写验证文件，由容器网关对外提供，因此续期不需要停止网关。

新装 Ubuntu 示例，已有 Certbot 时先核对，不重复混装。安装参照 [Certbot 官方说明](https://certbot.eff.org/instructions?os=snap&ws=nginx)：

```bash
sudo apt install -y snapd
sudo snap install --classic certbot
/snap/bin/certbot --version
```

申请一张同时覆盖两个域名的证书，替换真实邮箱：

```bash
sudo /snap/bin/certbot certonly --webroot \
  -w /var/www/letsencrypt \
  --cert-name lang-api \
  -d portal.example.com -d api.example.com \
  --email YOUR_REAL_EMAIL --agree-tos
```

预期证书路径为 `/etc/letsencrypt/live/lang-api/fullchain.pem` 与 `privkey.pem`。使用 `--cert-name lang-api` 固定目录名，和下面配置对应。申请失败先修复域名与 80 端口连通，不继续填写不存在的证书路径。[Certbot webroot 用法](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot)。

### 6.3 把网关切换为 HTTPS

编辑 `gateway/nginx.prod.conf`。这里给出具体替换位置，不让你重写原来的模型转发规则。

**第一步：** 把用户站点的：

```nginx
listen 80 default_server;
```

改成：

```nginx
listen 443 ssl default_server;
ssl_certificate /etc/letsencrypt/live/lang-api/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/lang-api/privkey.pem;
ssl_protocols TLSv1.2 TLSv1.3;
```

把模型 API 站点的 `listen 80;` 改成 `listen 443 ssl;`，同样加上这三条 `ssl_` 配置。第 6.1 节添加的验证位置块可以从这两个 HTTPS 站点移除，交由下一步独立 HTTP 站点处理。

**第二步：** 在同一文件末尾增加一个专门负责 HTTP 的站点：

```nginx
server {
  listen 80 default_server;
  server_name ${PORTAL_SERVER_NAME} ${MODEL_API_SERVER_NAME};

  location = /healthz {
    access_log off;
    return 200 'ok';
  }

  location ^~ /.well-known/acme-challenge/ {
    root /var/www/letsencrypt;
    default_type text/plain;
  }

  location / {
    return 301 https://$host$request_uri;
  }
}
```

不要把全站跳转写成服务器级别的无条件 `return`，否则证书验证和当前 HTTP 健康检查也会被跳走。`/healthz` 只证明网关可响应，仍需同时看其他服务健康状态。

生产模型网关原有 `/v1/chat/completions` 的 `proxy_buffering off`、长读取超时和禁用自动重试继续保留，防止破坏流式输出或重复产生调用费用。

**第三步：** 构建并启动，然后检查真实生成的 Nginx 配置：

```bash
pc up -d --build edge-nginx
pc exec -T edge-nginx nginx -t
pc ps
pc logs --tail=100 edge-nginx
curl --fail https://portal.example.com/ -o /tmp/lang-api-home-https.html
curl -I http://portal.example.com/
curl --fail http://portal.example.com/healthz
```

预期 Nginx 配置通过；HTTPS 首页可访问；HTTP 首页跳到 HTTPS；健康入口返回 `ok`。不要通过 `curl -k` 忽略证书错误来算验收成功。

Compose 挂载整个 `/etc/letsencrypt`，因为 `live` 中证书是指向 `archive` 的链接。只挂载 `live` 可能让链接在容器内失效。证书只读，不复制进镜像，也不提交仓库。

### 6.4 验证页面、登录和实际模型调用

浏览器打开正式网站，用普通试用账号登录，检查刷新、退出、再次登录。检查开发者工具中的登录 Cookie 是否带 `Secure` 和 `HttpOnly`，以及写操作是否返回来源校验错误。

允许来源应精确为 `https://portal.example.com`，不加 `/`、不加页面路径。它与 `PORTAL_OPENAI_URL` 不同：前者控制浏览器写操作来源，后者是给用户/SDK 调模型的地址。

验证公开配置：

```bash
curl --fail --silent --show-error https://portal.example.com/portal/api/public-config
```

检查其中的模型地址指向 `https://api.example.com/v1`，没有 `new-api:3000` 或 13000 管理端口。

在 Lang API 为普通用户创建 API Key，执行一条模型列表请求和一条很短的聊天请求。以下在服务器 Bash 终端操作；把真实模型 ID 填入 `YOUR_MODEL_ID`：

```bash
read -r -s -p '输入试用用户 API Key: ' LANG_TEST_API_KEY
printf '
'
LANG_TEST_HEADER_FILE=$(mktemp)
chmod 600 "$LANG_TEST_HEADER_FILE"
printf 'Authorization: Bearer %s
' "$LANG_TEST_API_KEY" > "$LANG_TEST_HEADER_FILE"
unset LANG_TEST_API_KEY

curl --fail-with-body --silent --show-error \
  -H @"$LANG_TEST_HEADER_FILE" https://api.example.com/v1/models
curl --fail-with-body --silent --show-error \
  -H @"$LANG_TEST_HEADER_FILE" -H 'Content-Type: application/json' \
  https://api.example.com/v1/chat/completions \
  --data '{"model":"YOUR_MODEL_ID","messages":[{"role":"user","content":"回复 OK"}],"max_tokens":16}'

rm -f "$LANG_TEST_HEADER_FILE"
unset LANG_TEST_HEADER_FILE
```

密钥通过受限临时文件传递，不写成带真实值的 shell 命令。结束时删文件；若操作被中断也要清理。再用相同方法测试 `"stream":true` 并加 `curl -N`，确认持续输出而不是攒到最后一次返回。

成功后对照 Lang API 请求日志、余额和 New API 使用日志，确认请求属于这个用户、命中预期模型并正常扣费。不能用管理员渠道测试代替普通用户验收。详细点击步骤见 [用户使用手册](用户手册/01_用户使用手册.md)。

### 6.5 公众注册是后续独立设置

邀请试用可以一直保持 `PREVIEW` 和关闭注册。决定开放公众注册时，先在 New API 配好真实用户协议、隐私正文及兼容的登录注册选项，再填写实际支持信息和地区：

```dotenv
PORTAL_PUBLICATION_MODE=PUBLIC
PORTAL_REGISTRATION_ENABLED=true
PORTAL_SUPPORT_URL=mailto:YOUR_REAL_SUPPORT_EMAIL
PORTAL_SUPPORTED_REGIONS=CN
```

`CN` 只是格式示例，运营地区要按真实范围填写。生产已将语言明确为 `zh-CN`，当前 PUBLIC 校验只允许法律源语言，不能照搬 dev 的多语言配置。

然后 `pc up -d lang-api`，验证 `/terms`、`/privacy`、`/regions` 和新用户注册、登录。环境开关、上游注册开关、法律正文可用性都满足才能注册；不要只看到按钮就认为已完成。

### 6.6 上线判断

部署跑通不等于所有产品上线条件已经满足。仓库的 [阻塞项与逐项推进方案](20_LANG-P2-10阻塞项与逐项推进方案.md) 记录了未完成的验收和暂缓问题；部署文档不替这些事项宣布通过。

**本章完成条件：** 域名与证书有效，网站登录正常，一次普通用户模型调用与流式调用正常，日志及扣费能对应，管理端口没有公开。公众注册仅在实际启用并验证后另标完成。

## 7. 安全与长期运行

### 7.1 最终检查暴露范围

```bash
pc ps
sudo ss -lntp
```

公网业务入口为 80/443。Java、New API、数据库、Redis 不应发布公网端口。Docker 发布端口可能绕过部分 UFW 规则，因此不能只说“防火墙已经挡住”；首先确保 Compose 没发布这些端口，再配云安全组。参见 [Docker 官方防火墙说明](https://docs.docker.com/engine/install/ubuntu/#firewall-limitations)。

### 7.2 密钥与证书续期

`production.env` 维持 600 权限；证书私钥不改成全员可读。两个 New API 密钥保存到受保护的服务器外位置，数据库备份也需要匹配的加密密钥。不要随机重生成 `CRYPTO_SECRET` 当作日常更新，它可能导致已有加密数据无法读取。

检查 Certbot 自动任务：

```bash
systemctl list-timers --all | grep -i certbot
sudo /snap/bin/certbot renew --dry-run
```

webroot 验证成功还不够，续期后 Nginx 需要重新加载证书。新建服务器文件 `/etc/letsencrypt/renewal-hooks/deploy/lang-api-reload`：

```bash
#!/bin/sh
set -eu
/usr/bin/docker compose --env-file /etc/lang-api/production.env \
  -f /opt/lang-api/deploy/compose.prod.yml \
  exec -T edge-nginx nginx -t
/usr/bin/docker compose --env-file /etc/lang-api/production.env \
  -f /opt/lang-api/deploy/compose.prod.yml \
  exec -T edge-nginx nginx -s reload
```

设置权限并手动执行一次，确认配置检查和 reload 成功：

```bash
sudo chmod 700 /etc/letsencrypt/renewal-hooks/deploy/lang-api-reload
sudo /etc/letsencrypt/renewal-hooks/deploy/lang-api-reload
sudo /snap/bin/certbot renew --dry-run --run-deploy-hooks
```

脚本假设 `command -v docker` 是 `/usr/bin/docker`，不一致时先改路径。测试必须看到证书验证及钩子运行正常。[Certbot 自动续期与钩子](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates)。

### 7.3 重启恢复与日志

确认 `sudo systemctl is-enabled docker`。在没有真实用户请求的维护时间执行一次服务器重启，重连后定义 `pc`，检查 `pc ps`、HTTPS 首页、登录和模型调用；这一步实际完成后才标“重启恢复已验证”。

`restart: unless-stopped` 不负责健康失败自愈，也不保证数据库断线后的业务立即恢复，按真实结果处理。主动 `pc stop` 之后，不要期待重启机器自动重新开放业务。

```bash
pc logs --tail=100 lang-api
sudo docker system df
df -h
```

Compose 的 `max-size/max-file` 只管理容器标准输出日志。当前模型访问日志在 `/var/log/nginx/model-access.log`，New API 还挂载了 `/log` 卷；需要单独观察大小、配置轮转或后续改成统一输出。不能把 Docker 日志上限当成全项目日志上限。

不要把 `docker system prune --volumes` 当作例行清理。先辨认占用来自镜像、构建缓存、日志还是数据卷，再处理。

## 8. 更新、备份、恢复和故障处理

### 8.1 改了文件后，怎样生效

| 改动内容 | 正常应用方式 |
|---|---|
| Java / 前端源码 / 根 Dockerfile | `pc up -d --build lang-api` |
| 生产 Nginx 模板 / 网关 Dockerfile | `pc up -d --build edge-nginx`，然后 `nginx -t` |
| 环境文件中的后端站点参数 | `pc up -d lang-api` |
| 环境文件中的网关域名、端口或限制 | `pc up -d edge-nginx` |
| Compose 网络、卷或服务设置 | 先 `pc config --quiet`，再按变更范围 `pc up -d` |
| 续期证书 | 在网关执行 `nginx -t` 后 `nginx -s reload` |
| New API 管理页面配置 | 按界面保存并实际验证，一般无需重建 Java 镜像 |

数据库用户名、密码和库名不是普通参数更新：已有 PostgreSQL 数据卷不会因为改环境变量就重设账号。需要数据库内修改与连接配置同步，不能改 `.env` 后认定密码已更新。

### 8.2 备份数据库和必要文件

服务器执行，确保当前定义的是生产 `pc`：

```bash
sudo install -d -m 700 /var/backups/lang-api
LANG_BACKUP_PATH="/var/backups/lang-api/newapi-$(date +%Y%m%d-%H%M%S).dump"
set -o pipefail
pc exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"' \
  | sudo tee "$LANG_BACKUP_PATH" > /dev/null
sudo chmod 600 "$LANG_BACKUP_PATH"
sudo test -s "$LANG_BACKUP_PATH"
sudo sha256sum "$LANG_BACKUP_PATH"
```

内部命令的单引号保留 `$POSTGRES_PASSWORD`，让它在数据库容器内读取，而不是要求宿主机 `source` 密钥文件。`pipefail` 让数据库导出失败能反映到整条命令，不能仅凭创建了文件就说备份成功。

同时保管：环境文件、生产配置、源码版本、两套自有镜像、New API 镜像指纹；按实际使用情况备份 New API `/data` 中的文件。数据库备份不自动包含 Redis、文件卷或日志。Redis 会话丢失通常需要重新登录，但需确认没有其他必须保留的数据。

备份复制到另一台受保护的机器或存储，保存权限和校验和。仅放本机硬盘不能应对整机损坏。

### 8.3 验证数据库备份能恢复

恢复验证必须在隔离环境，第一次不要往生产库灌入备份。下面用现有 PostgreSQL 镜像启动一个不发布端口的临时容器，在维护时有足够资源再执行：

```bash
LANG_RESTORE_CONTAINER="lang-api-restore-$(date +%Y%m%d%H%M%S)"
LANG_RESTORE_PASSWORD=$(openssl rand -hex 24)
export LANG_RESTORE_PASSWORD
sudo docker run -d \
  --name "$LANG_RESTORE_CONTAINER" --network none \
  -e POSTGRES_DB=restorecheck -e POSTGRES_USER=restorecheck \
  -e POSTGRES_PASSWORD="$LANG_RESTORE_PASSWORD" \
  postgres:16-alpine@sha256:cf78e76683b9ca8c5733cbbdce6c9262b45b6767934dd0a95e671f9a0fc20685
unset LANG_RESTORE_PASSWORD
```

临时数据库密码不是生产密钥；等待下面检查成功，不成功就查看临时容器日志，不继续恢复：

```bash
sudo docker exec "$LANG_RESTORE_CONTAINER" pg_isready -U restorecheck -d restorecheck
sudo cat "$LANG_BACKUP_PATH" \
  | sudo docker exec -i "$LANG_RESTORE_CONTAINER" \
      pg_restore --exit-on-error --no-owner --no-privileges -U restorecheck -d restorecheck
sudo docker exec "$LANG_RESTORE_CONTAINER" psql -U restorecheck -d restorecheck -c '\dt'
```

还要对照备份时记录的实际用户、渠道、令牌数量与抽样记录。只看到表名不算完整恢复；要证明业务可用，还需隔离 New API 使用同版镜像、匹配加密密钥连接该副本，再验证管理员登录及抽样数据。此时可按当前服务器信息补一份恢复演练配置，禁止直接让试验上游连接唯一生产库。

演练结束，确认临时容器名无误后删除该临时容器及其匿名卷：

```bash
sudo docker rm -f -v "$LANG_RESTORE_CONTAINER"
unset LANG_RESTORE_CONTAINER LANG_BACKUP_PATH
```

这里 `-v` 仅针对刚创建的临时容器，不用于生产 Compose。正式灾难恢复应保留损坏现场，把备份恢复到新卷，使用匹配版本和密钥验证后再切换入口；不要无备份覆盖唯一数据库。

### 8.4 更新项目与回退

第一次部署不引入自动发布。每次手动更新按以下顺序：

1. 备份数据库、环境文件，记录当前镜像和版本。
2. 在独立目录解压新源码，检查配置差异；保留旧源码包，不直接混盖导致旧文件残留。
3. 选择新 `DEPLOY_VERSION` 和 `GIT_REVISION`，构建自有镜像，确认构建通过。
4. 在维护窗口将 `/opt/lang-api` 指向或替换为新版本目录，保留生产配置和环境文件；确保续期脚本的固定路径仍有效。
5. 执行 `pc up -d --build`，健康后核对页面、登录及一次小额真实调用。

生产名和卷名保持，配置中的相对路径以新的 Compose 所在目录解析。更新期间不要改变项目名或卷名来“修复”启动问题，否则可能连接到新的空数据库。

更新前保存旧镜像到受保护目录：

```bash
# OLD_VERSION 替换为当前环境文件中的实际 DEPLOY_VERSION
sudo docker image save lang-api-app:OLD_VERSION lang-api-edge:OLD_VERSION \
  | sudo tee /var/backups/lang-api/images-OLD_VERSION.tar > /dev/null
```

应用回退时恢复匹配旧源码和配置，将 `DEPLOY_VERSION` 改回旧值；若本机旧镜像不在，先 `docker image load` 导入。运行 `pc up -d --no-build --pull never lang-api edge-nginx` 使用旧镜像，不重新构建覆盖旧版本。单实例更新可能短暂停机，本文不承诺无中断。

只回退应用镜像不会回退数据库。首次路线不升级 New API 或 PostgreSQL；上游升级另按 [初始化与升级手册](new-api/初始化与升级手册.md) 做副本迁移与恢复验证。

### 8.5 常见现象，从哪里查

| 现象 | 先检查 |
|---|---|
| `config` 提示缺参数 | 环境文件路径、变量拼写、是否还是 `REPLACE_` |
| Maven/npm 或镜像下载失败 | 服务器出网、DNS、具体下载地址，项目 Maven 配置；不盲用未知镜像源 |
| 构建被杀或退出 137 | `free -h`、磁盘和系统 OOM 日志，确认是不是资源不足 |
| `lang-api` 启动循环 | `pc logs --tail=100 lang-api`，查生产参数校验、Redis 和上游连接 |
| 五服务健康但公网打不开 | 域名解析、网关实际端口、云安全组、宿主机占用端口 |
| 证书申请失败 | 两个域名解析、80 公网可达、验证文件能从 Mac 读取 |
| 网关提示找不到证书 | 申请是否成功、证书名 `lang-api`、整个目录挂载及链接 |
| 登录后立即退出 | 是否 HTTPS、Cookie 标志和路径、Redis 会话存储、浏览器响应 |
| 写操作被拒绝 | `PORTAL_ALLOWED_ORIGINS` 是否等于真实网站来源 |
| 模型调用 401 | 是否使用用户 API Key、是否正确 Bearer、Key 状态 |
| 模型调用失败或无可用渠道 | New API 渠道、模型 ID、分组、用户额度、供应商返回 |
| `/v1/responses` 返回 404 | 当前网关未开放该路径，不是证书或端口问题 |
| 出现新安装向导或账号消失 | 是否改了 Compose 项目名、卷名、数据库配置，先保留旧卷 |
| 磁盘持续增长 | Docker 日志、模型日志、New API 文件日志、镜像和构建缓存 |

定位时先记录现象、发生时间、实际命令和必要日志。不要用清空数据库、删除卷、重新生成密钥作为通用修复动作。

## 后续讨论记录

| 日期 | 小节 | 问题 | 已确认结论 | 待验证事项 |
|---|---|---|---|---|
| 2026-10-04 | 文档方案 | 从哪里开始学 | 跳过连接 Linux，单列 Compose 学习，操作与文件修改一起讲 | 服务器系统、域名与资源尚未确认 |

### 本次文档核验记录

- 生产业务配置、叠加管理入口配置、正式端口配置：均通过 Compose 解析检查。
- 31 个 Shell 代码块：通过 Bash 语法检查；相对文件链接与代码围栏配对检查通过。
- 内部 HTTP、证书申请 HTTP、正式 HTTPS：按文档步骤生成配置，在隔离临时容器中通过 `nginx -t`。HTTPS 检查使用临时测试证书，没有申请真实证书或接入供应商。
- 文档变更通过 `git diff --check`；没有修改实际部署文件或运行中的服务。

以上核验不替代实际服务器验收。服务器安装、证书签发、重启恢复和真实业务仍需在实际服务器执行后记录结果。前面的进度表保持“未操作”，不会把文档示例当成已部署成功。
