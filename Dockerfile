# syntax=docker/dockerfile:1

# ============ 构建阶段：复用根 Maven 正式构建入口 ============
FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build

ENV MAVEN_VERSION=3.8.4 \
    MAVEN_HOME=/opt/apache-maven-3.8.4 \
    LANG=C.UTF-8

# 安装与本机一致的 Maven 3.8.4（校验压缩包指纹，失败即停）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    && curl -fsSL -o /tmp/maven.tar.gz.sha512 "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz.sha512" \
    && echo "$(cat /tmp/maven.tar.gz.sha512 | awk '{print $1}')  /tmp/maven.tar.gz" | sha512sum -c - \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && rm /tmp/maven.tar.gz /tmp/maven.tar.gz.sha512 \
    && ln -s "${MAVEN_HOME}/bin/mvn" /usr/local/bin/mvn

WORKDIR /build
COPY pom.xml ./
COPY frontend/pom.xml frontend/pom.xml
COPY frontend/package.json frontend/package.json
COPY frontend/package-lock.json frontend/package-lock.json
COPY portal-api/pom.xml portal-api/pom.xml
COPY frontend/ frontend/
COPY portal-api/ portal-api/

# 与本机正式构建同一入口；Maven 私服凭据只经 BuildKit secret 注入，不进镜像层
RUN --mount=type=secret,id=maven_settings \
    mvn -s /run/secrets/maven_settings clean package

# ============ 运行阶段：精简 JRE + 非 root ============
FROM eclipse-temurin:21.0.8_9-jre-jammy

ENV LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r -g 10001 langapi \
    && useradd -r -u 10001 -g langapi -s /usr/sbin/nologin langapi \
    && mkdir -p /app \
    && chown langapi:langapi /app

COPY --from=build --chown=langapi:langapi /build/portal-api/target/portal-api-*.jar /app/app.jar

USER langapi
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
