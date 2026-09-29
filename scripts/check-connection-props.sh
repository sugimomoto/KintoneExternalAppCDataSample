#!/usr/bin/env bash
# lib/ に置かれた実ドライバーから接続プロパティを取得できるか確認する。
#
# 26.x 系のドライバーは空の接続文字列を検証で弾くため、段階的プローブで
# 取得できていることを実 JAR で確認する必要がある (Issue #15)。
# 実 JAR とライセンスが要るので CI では動かさない。
#
# 期待値: .steering/20260929-connection-props-fetch-fallback/design.md §8.4
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew --quiet test \
  --tests "com.cdata.kintone.adapter.jdbc.RealDriverPropertyFetchTest" \
  -DrealDrivers=true \
  --rerun-tasks -i 2>&1 | grep -E "source=|完全取得できなかった|BUILD"
