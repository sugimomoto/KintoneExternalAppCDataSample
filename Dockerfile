# Multi-stage build for cdata-kintone-adapter

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Gradle wrapper と関連ファイルを先にコピーして依存キャッシュを効かせる
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon --version

# ソースコピー＋ビルド
COPY src src
COPY detekt.yml .editorconfig ./
RUN ./gradlew --no-daemon shadowJar -x test -x ktlintCheck -x detekt

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /app/build/libs/adapter-*-all.jar /app/adapter.jar

# Adapter の待ち受けポート
EXPOSE 8083

# CData JDBC Driver の JAR と設定はホスト側からマウントする
# - /app/lib/  : CData JDBC Driver の jar (cdata.jdbc.salesforce.jar 等) と .lic ファイル
# - /app/config: server.yaml / jdbc.yaml / table.yaml / capability.yaml
VOLUME ["/app/lib", "/app/config"]

ENV LOG_LEVEL=INFO

ENTRYPOINT ["java", "-jar", "/app/adapter.jar"]
CMD ["serve", "--config-dir", "/app/config"]
