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
#   ./scripts/restart-stack.sh          # ビルドなしで再起動（フェーズ1 互換: serve + 単一 Agent）
#   ./scripts/restart-stack.sh --build  # gradle shadowJar してから再起動
#   ./scripts/restart-stack.sh stop     # 停止のみ
#   ./scripts/restart-stack.sh start    # 起動のみ
#
# フェーズ2-A モード:
#   MULTI=1 ./scripts/restart-stack.sh           # serve-all + docker-compose.multi.yml
#   MULTI=1 ./scripts/restart-stack.sh --build

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

MULTI="${MULTI:-0}"
ADAPTER_PORT="${ADAPTER_PORT:-8083}"
ADAPTER_JAR="${ADAPTER_JAR:-build/libs/adapter-0.1.0-SNAPSHOT-all.jar}"
ADAPTER_LOG="${ADAPTER_LOG:-/tmp/adapter.log}"
JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@21"
if [ "$MULTI" = "1" ]; then
  COMPOSE_FILE="agent/docker-compose.multi.yml"
  ADAPTER_CMD="serve-all"
else
  COMPOSE_FILE="agent/docker-compose.yml"
  ADAPTER_CMD="serve"
fi

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
  local pids=""
  if [ "$MULTI" = "1" ]; then
    # serve-all モード: java プロセス名 + JAR 名でマッチ
    pids=$(pgrep -f "java -jar.*$(basename "$ADAPTER_JAR") serve-all" 2>/dev/null || true)
  else
    pids=$(lsof -ti:"$ADAPTER_PORT" 2>/dev/null || true)
  fi
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill 2>/dev/null || true
    # 最大 10 秒待ってグレースフルにシャットダウン、それでも残ったら強制
    for _ in {1..10}; do
      local remaining=""
      if [ "$MULTI" = "1" ]; then
        remaining=$(pgrep -f "java -jar.*$(basename "$ADAPTER_JAR") serve-all" 2>/dev/null || true)
      else
        remaining=$(lsof -ti:"$ADAPTER_PORT" 2>/dev/null || true)
      fi
      [ -z "$remaining" ] && break
      sleep 1
    done
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
  color_info "Adapter 起動中 (cmd=$ADAPTER_CMD)..."
  nohup java -jar "$ADAPTER_JAR" "$ADAPTER_CMD" > "$ADAPTER_LOG" 2>&1 &
  local pid=$!
  if [ "$MULTI" = "1" ]; then
    # serve-all モードでは複数ポート。状態ファイル経由で起動完了確認
    for _ in {1..30}; do
      if [ -f "./run/active-adapters.json" ] && grep -q '"port"' ./run/active-adapters.json 2>/dev/null; then
        color_ok "Adapter 起動完了 (PID=$pid)"
        echo "  起動中のテーブル:"
        java -jar "$ADAPTER_JAR" list-active 2>/dev/null | sed 's/^/    /'
        return 0
      fi
      sleep 1
    done
  else
    for _ in {1..30}; do
      if grpcurl -plaintext -d '{}' "localhost:$ADAPTER_PORT" grpc.health.v1.Health/Check 2>/dev/null | grep -q SERVING; then
        color_ok "Adapter 起動完了 (PID=$pid, port=$ADAPTER_PORT)"
        return 0
      fi
      sleep 1
    done
  fi
  color_warn "Adapter のヘルスチェックが 30 秒以内に通りませんでした。ログを確認: $ADAPTER_LOG"
  tail -20 "$ADAPTER_LOG"
  exit 1
}

start_agent() {
  color_info "Agent コンテナ起動中 (compose=$COMPOSE_FILE)..."
  docker compose -f "$COMPOSE_FILE" up -d 2>&1 | sed 's/^/  /'
  # kintone 接続待ち
  local containers
  containers=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null || true)
  if [ -z "$containers" ]; then
    color_warn "起動中の Agent コンテナがありません"
    return
  fi
  for _ in {1..20}; do
    local all_connected=1
    for c in $containers; do
      if ! docker logs "$c" 2>&1 | tail -10 | grep -q "successfully connected to kintone"; then
        all_connected=0
        break
      fi
    done
    if [ "$all_connected" = "1" ]; then
      color_ok "Agent 起動完了（全コンテナで kintone 接続済み）"
      return 0
    fi
    sleep 1
  done
  color_warn "一部 Agent の kintone 接続が確認できませんでした。各コンテナの最終ログ:"
  for c in $containers; do
    echo "--- $c ---"
    docker logs "$c" --tail 5 2>&1 | sed 's/^/  /'
  done
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
