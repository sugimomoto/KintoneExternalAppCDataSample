# kintone-data-connector-agent を Linux コンテナで起動するための Dockerfile
# Mac (arm64) でも amd64 Linux でも動くようマルチアーキ対応

# Debian slim をベースに最小限の環境を構築
FROM debian:bookworm-slim

ARG TARGETARCH

# 必須ツール（証明書、SSL/TLS 用）
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# バイナリは /usr/local/bin/ に配置（volume mount 先と分離するため）
# - TARGETARCH=amd64 → bin/linux_amd64/
# - TARGETARCH=arm64 → bin/linux_arm64/
COPY bin/linux_${TARGETARCH}/kintone-data-connector-agent /usr/local/bin/kintone-data-connector-agent
RUN chmod +x /usr/local/bin/kintone-data-connector-agent

# 設定ファイル・秘密鍵は /opt/agent/ に volume mount する想定
WORKDIR /opt/agent

# Agent はアウトバウンド HTTPS のみで kintone と通信するためポート EXPOSE 不要

ENTRYPOINT ["/usr/local/bin/kintone-data-connector-agent"]
CMD ["--config", "/opt/agent/agent.json"]
