#!/bin/bash
# Adapter の各 RPC を curl で叩いて動作確認するスクリプト
# 前提: Adapter が gRPC + HTTP/2 で待ち受け中 (default: localhost:8083)
#
# 使い方:
#   ./scripts/test-adapter.sh GetCapability
#   ./scripts/test-adapter.sh Count
#   ./scripts/test-adapter.sh Select
#
# 注意: gRPC バイナリプロトコルで通信するため grpcurl を使用する
#   brew install grpcurl

set -euo pipefail

ADAPTER_HOST="${ADAPTER_HOST:-localhost:8083}"
PROTO_DIR="${PROTO_DIR:-reference/kintone-data-connector}"
SERVICE="cybozu.data_connector.adapter.v1.AdapterService"

method="${1:-GetCapability}"

case "$method" in
GetCapability)
  data='{}'
  ;;
GetSchema)
  data='{}'
  ;;
Select)
  data='{
    "payload": {
      "fields": [],
      "filterConditions": [{"allRecords": {}}],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "10"
    }
  }'
  ;;
Count)
  data='{
    "payload": {
      "filterConditions": [{"allRecords": {}}],
      "matchOperator": "MATCH_OPERATOR_ALL"
    }
  }'
  ;;
*)
  if [ -n "${2:-}" ]; then
    data="$2"
  else
    data='{}'
  fi
  ;;
esac

echo "==> $SERVICE/$method"
echo "$data" | jq . || echo "$data"
echo "---"

grpcurl \
  -plaintext \
  -proto "$PROTO_DIR/adapter_service.proto" \
  -import-path "$PROTO_DIR" \
  -import-path "$(brew --prefix 2>/dev/null)/include" \
  -d "$data" \
  "$ADAPTER_HOST" \
  "$SERVICE/$method"
