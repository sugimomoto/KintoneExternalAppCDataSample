#!/bin/bash
# Adapter と Agent の安全な再起動スクリプト
#
# 起動・停止順序:
#   停止: Agent (受信を止める) → Adapter
#   起動: Adapter (準備完了) → Agent (受信開始)
#
# Adapter ビルド時に Agent が古い Adapter にリクエストを流して
# エラーログでまる、kintone セッションが混乱する、Docker daemon が
# OOM 圧迫を受ける、等を避けるのが目的。
#
# 使い方:
#   ./scripts/restart-stack.sh          # ビルドなしで再起動
#   ./scripts/restart-stack.sh --build  # gradle shadowJar してから再起動
#   ./scripts/restart-stack.sh stop     # 停止のみ
#   ./scripts/restart-stack.sh start    # 起動のみ

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

ADAPTER_PORT="${ADAPTER_PORT:-8083}"
ADAPTER_JAR="${ADAPTER_JAR:-build/libs/adapter-0.1.0-SNAPSHOT-all.jar}"
ADAPTER_LOG="${ADAPTER_LOG:-/tmp/adapter.log}"
JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@21"
COMPOSE_FILE="agent/docker-compose.yml"

color_info() { printf "\033[1;34m[INFO]\033[0m %s\n" "$*"; }
color_ok() { printf "\033[1;32m[OK]\033[0m %s\n" "$*"; }
color_warn() { printf "\033[1;33m[WARN]\033[0m %s\n" "$*"; }

ensure_java() {
  if [ -z "${JAVA_HOME:-}" ] || ! "${JAVA_HOME}/bin/java" -version &>/dev/null; then
    if [ -x "$JAVA_HOME_DEFAULT/bin/java" ]; then
      export JAVA_HOME="$JAVA_HOME_DEFAULT"
      export PATH="$JAVA_HOME/bin:$PATH"
    else
      color_warn "Java 21 が見つかりません。JAVA_HOME を設定してください"
      exit 1
    fi
  fi
}

stop_agent() {
  color_info "Agent コンテナ停止中..."
  if docker compose -f "$COMPOSE_FILE" ps --status running --quiet 2>/dev/null | grep -q .; then
    docker compose -f "$COMPOSE_FILE" stop 2>&1 | sed 's/^/  /'
    color_ok "Agent 停止完了"
  else
    color_info "Agent は既に停止中"
  fi
}

stop_adapter() {
  color_info "Adapter プロセス停止中..."
  local pids
  pids=$(lsof -ti:"$ADAPTER_PORT" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill 2>/dev/null || true
    # 最大 10 秒待ってグレースフルにシャットダウン、それでも残ったら強制
    for _ in {1..10}; do
      if ! lsof -ti:"$ADAPTER_PORT" >/dev/null 2>&1; then break; fi
      sleep 1
    done
    if lsof -ti:"$ADAPTER_PORT" >/dev/null 2>&1; then
      color_warn "graceful 停止失敗、強制終了"
      lsof -ti:"$ADAPTER_PORT" | xargs kill -9 2>/dev/null || true
    fi
    color_ok "Adapter 停止完了"
  else
    color_info "Adapter は既に停止中"
  fi
}

build_adapter() {
  color_info "Gradle ビルド中..."
  ensure_java
  ./gradlew shadowJar -x test 2>&1 | tail -5 | sed 's/^/  /'
  color_ok "ビルド完了: $ADAPTER_JAR"
}

start_adapter() {
  ensure_java
  if [ ! -f "$ADAPTER_JAR" ]; then
    color_warn "JAR が見つかりません: $ADAPTER_JAR （--build オプションでビルドできます）"
    exit 1
  fi
  color_info "Adapter 起動中..."
  nohup java -jar "$ADAPTER_JAR" serve > "$ADAPTER_LOG" 2>&1 &
  local pid=$!
  # ヘルスチェックで起動完了確認（最大 30 秒）
  for _ in {1..30}; do
    if grpcurl -plaintext -d '{}' "localhost:$ADAPTER_PORT" grpc.health.v1.Health/Check 2>/dev/null | grep -q SERVING; then
      color_ok "Adapter 起動完了 (PID=$pid, port=$ADAPTER_PORT)"
      return 0
    fi
    sleep 1
  done
  color_warn "Adapter のヘルスチェックが 30 秒以内に通りませんでした。ログを確認: $ADAPTER_LOG"
  tail -20 "$ADAPTER_LOG"
  exit 1
}

start_agent() {
  color_info "Agent コンテナ起動中..."
  docker compose -f "$COMPOSE_FILE" up -d 2>&1 | sed 's/^/  /'
  # kintone 接続待ち
  for _ in {1..15}; do
    if docker logs kintone-agent 2>&1 | tail -5 | grep -q "successfully connected to kintone"; then
      color_ok "Agent 起動完了（kintone 接続済み）"
      return 0
    fi
    sleep 1
  done
  color_warn "Agent の kintone 接続が確認できませんでした。ログ末尾:"
  docker logs kintone-agent --tail 5 2>&1
}

cmd_stop() {
  stop_agent
  stop_adapter
}

cmd_start() {
  start_adapter
  start_agent
}

cmd_restart() {
  cmd_stop
  cmd_start
}

cmd_restart_build() {
  stop_agent
  stop_adapter
  build_adapter
  start_adapter
  start_agent
}

case "${1:-restart}" in
  stop) cmd_stop ;;
  start) cmd_start ;;
  restart) cmd_restart ;;
  --build|-b|build) cmd_restart_build ;;
  *) echo "Usage: $0 [stop|start|restart|--build]" >&2; exit 1 ;;
esac
