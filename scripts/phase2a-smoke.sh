#!/bin/bash
# フェーズ2-A スモークテスト。実 kintone / 実 Salesforce 接続なしで、
# シェル経由で確認できる範囲を点検する。
#
# 使い方:
#   ./scripts/phase2a-smoke.sh

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@21"
JAR="${JAR:-$PROJECT_DIR/build/libs/adapter-0.1.0-SNAPSHOT-all.jar}"

color_info() { printf "\033[1;34m[INFO]\033[0m %s\n" "$*"; }
color_ok() { printf "\033[1;32m[OK]\033[0m %s\n" "$*"; }
color_fail() { printf "\033[1;31m[FAIL]\033[0m %s\n" "$*"; exit 1; }

ensure_java() {
  if [ -z "${JAVA_HOME:-}" ] || ! "${JAVA_HOME}/bin/java" -version &>/dev/null; then
    if [ -x "$JAVA_HOME_DEFAULT/bin/java" ]; then
      export JAVA_HOME="$JAVA_HOME_DEFAULT"
      export PATH="$JAVA_HOME/bin:$PATH"
    fi
  fi
}

ensure_jar() {
  if [ ! -f "$JAR" ]; then
    color_info "JAR が見つかりません。shadowJar を実行: $JAR"
    ./gradlew shadowJar -x test
  fi
}

check_help() {
  color_info "(1) CLI ヘルプを確認"
  local out
  out=$(java -jar "$JAR" --help 2>&1)
  for cmd in serve serve-all list-active init-table list-tables test-connection migrate-config; do
    if ! echo "$out" | grep -q "  $cmd"; then
      color_fail "サブコマンドが見つかりません: $cmd"
    fi
  done
  color_ok "7 サブコマンドすべて表示"
}

check_migrate() {
  color_info "(2) migrate-config 動作確認"
  local tmpdir
  tmpdir=$(mktemp -d)

  cp "$PROJECT_DIR/config/server.yaml.example" "$tmpdir/server.yaml"
  cp "$PROJECT_DIR/config/jdbc.yaml.example" "$tmpdir/jdbc.yaml"
  cp "$PROJECT_DIR/config/table.yaml.example" "$tmpdir/table.yaml"
  cp "$PROJECT_DIR/config/capability.yaml.example" "$tmpdir/capability.yaml"

  java -jar "$JAR" migrate-config --config-dir "$tmpdir" >/dev/null

  for f in server.yaml jdbc.yaml table.yaml capability.yaml; do
    [ -f "$tmpdir/tables/default/$f" ] || { rm -rf "$tmpdir"; color_fail "移行後ファイルが見つかりません: $f"; }
  done
  color_ok "4 ファイルが tables/default/ に移行された"

  # 二回目: ALREADY_MIGRATED
  local out
  out=$(java -jar "$JAR" migrate-config --config-dir "$tmpdir" 2>&1)
  if ! echo "$out" | grep -q "既に新構成"; then
    rm -rf "$tmpdir"
    color_fail "2回目で ALREADY_MIGRATED を検出できず"
  fi
  color_ok "2回目実行で ALREADY_MIGRATED 検出"
  rm -rf "$tmpdir"
}

check_list_active_empty() {
  color_info "(3) list-active が空状態でメッセージを出す"
  local tmpfile
  tmpfile=$(mktemp)
  rm -f "$tmpfile"
  local out
  out=$(java -jar "$JAR" list-active --state-file "$tmpfile" 2>&1)
  echo "$out" | grep -q "稼働中の Adapter はありません" || color_fail "想定メッセージなし: $out"
  color_ok "空状態を正しく検出"
}

check_examples_present() {
  color_info "(4) フェーズ2-A example ファイルが揃っている"
  local required=(
    "config/jdbc/salesforce.yaml.example"
    "config/jdbc/googlesheets.yaml.example"
    "config/tables/account/server.yaml.example"
    "config/tables/account/jdbc-ref.yaml.example"
    "config/tables/account/table.yaml.example"
    "config/tables/account/capability.yaml.example"
    "config/tables/contact/table.yaml.example"
    "config/tables/googlesheets-orders/table.yaml.example"
    "agent/tables/account/agent.json.example"
    "agent/tables/contact/agent.json.example"
    "agent/docker-compose.multi.yml"
  )
  for f in "${required[@]}"; do
    [ -f "$PROJECT_DIR/$f" ] || color_fail "example ファイルが見つかりません: $f"
  done
  color_ok "全 example ファイル存在"
}

check_gitignore() {
  color_info "(5) .gitignore が新階層を保護している"
  for pat in "config/jdbc/\*.yaml" "config/tables/\*/\*.yaml" "agent/tables/\*/agent.json" "run/"; do
    grep -q "$pat" "$PROJECT_DIR/.gitignore" || color_fail ".gitignore に欠落: $pat"
  done
  color_ok "新階層保護ルール OK"
}

main() {
  ensure_java
  ensure_jar
  check_help
  check_migrate
  check_list_active_empty
  check_examples_present
  check_gitignore
  echo
  color_ok "フェーズ2-A スモークテスト全件 OK"
}

main "$@"
