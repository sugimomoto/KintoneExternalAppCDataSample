# Multi-stage build for cdata-kintone-adapter
# Phase 2-C: Web UI 中心。adapter-console として起動し、
# Agent コンテナは Docker socket 経由で動的制御。

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon --version

COPY src src
COPY detekt.yml .editorconfig ./
RUN ./gradlew --no-daemon shadowJar -x test -x ktlintCheck -x detekt

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /app/build/libs/adapter-*-all.jar /app/adapter.jar

# Web UI (8080) + Sync 用 Adapter ポート範囲 (18000-18099)
EXPOSE 8080 18000-18099

# マウント対象:
# - /app/lib    : CData JDBC Driver の jar + .lic
# - /app/config : YAML 設定 or SQLite (config.db)
# - /app/agent  : 公開鍵 + agent.json (Agent コンテナとボリューム共有)
# - /app/run    : 稼働状態 (active-adapters.json)
# - /var/run/docker.sock : Agent コンテナ制御 (compose で別途マウント)
VOLUME ["/app/lib", "/app/config", "/app/agent", "/app/run"]

ENV LOG_LEVEL=INFO
# Agent コンテナ作成時の bind マウント解決用。
# docker-compose 側でホスト上の絶対パスを指定する。
ENV HOST_AGENT_ROOT=/app/agent

ENTRYPOINT ["java", "-jar", "/app/adapter.jar"]
CMD ["web-ui", "--port", "8080", "--bind-address", "0.0.0.0", "--config-dir", "/app/config", "--lib-dir", "/app/lib"]
