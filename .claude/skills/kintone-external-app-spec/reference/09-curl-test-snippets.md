# 09. curl / grpcurl テストスニペット

Connect プロトコルは **JSON over HTTP/2** をサポートしているため、curl で各 RPC を叩いて Adapter の動作確認ができる。Agent 不要。

---

## 前提

- Adapter が `http://localhost:8083` で起動している（ポートは ADAPTER_PORT で変更可）
- TLS なし（`adapter_plaintext: true` 相当）
- エンドポイント URL 形式：
  ```
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/{MethodName}
  ```

## curl の必須フラグ

```bash
curl -s \
  --http2-prior-knowledge \                      # ← HTTP/2 で接続
  --header 'Content-Type: application/json' \    # ← Connect JSON 形式
  --data '{...}' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/{MethodName} \
  | jq .
```

`--http2-prior-knowledge` は **TLS なしで HTTP/2 通信** するために必須。

---

## 1. GetCapability

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{}' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/GetCapability \
  | jq .
```

期待レスポンス：
```json
{
  "payload": {
    "selectOperationSupported": true,
    "insertOperationSupported": true,
    "updateOperationSupported": true,
    "deleteOperationSupported": true,
    "countOperationSupported": true,
    "searchOperationSupported": false,
    "recordIdType": "RECORD_ID_TYPE_NUMBER",
    "filterableFields": ["id", "name", "status"],
    "sortableFields": ["id", "created_at"]
  }
}
```

---

## 2. GetSchema

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{}' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/GetSchema \
  | jq .
```

期待レスポンス：
```json
{
  "payload": {
    "schema": {
      "id": { "recordIdFieldDefinition": { "fieldId": "id" } },
      "name": { "textFieldDefinition": { "fieldId": "name" } },
      "revenue": { "numberFieldDefinition": { "fieldId": "revenue" } },
      "created_at": { "datetimeFieldDefinition": { "fieldId": "created_at" } },
      "status": {
        "selectionFieldDefinition": {
          "fieldId": "status",
          "options": [{"value":"active"}, {"value":"inactive"}]
        }
      }
    }
  }
}
```

---

## 3. Select（基本）

全件取得：
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "filterConditions": [{"allRecords":{}}],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [{"fieldId":"id","sortDirection":"SORT_DIRECTION_ASC"}],
      "offset": "0",
      "limit": "10"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

**注意**：`offset` と `limit` は **int64 のため文字列で渡す**（JSON で64bit整数を扱う Connect 規約）。

特定カラムのみ取得 + フィルター：
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": ["id", "name"],
      "filterConditions": [
        {"textContains": {"fieldId": "name", "value": "Acme"}}
      ],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "100"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

複数フィルター（AND）：
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "filterConditions": [
        {"textContains": {"fieldId":"name","value":"Acme"}},
        {"numberGreaterThanOrEqual": {"fieldId":"revenue","value":1000000}}
      ],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "50"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

日時範囲フィルター：
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "filterConditions": [{
        "datetimeInRange": {
          "fieldId": "created_at",
          "start": "2026-01-01T00:00:00Z",
          "end": "2026-02-01T00:00:00Z"
        }
      }],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "100"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

選択肢 IN（NULL 含む）：
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "filterConditions": [{
        "selectionIn": {
          "fieldId": "status",
          "values": [
            {"option": {"value": "active"}},
            {"option": {"value": "pending"}},
            {}
          ]
        }
      }],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "100"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

---

## 4. Insert

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "records": [
        {
          "fields": {
            "name": {"textField": {"fieldId":"name","value":"Acme Corp"}},
            "revenue": {"numberField": {"fieldId":"revenue","value":5000000}},
            "status": {"selectionField": {"fieldId":"status","value":{"value":"active"}}}
          }
        }
      ]
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Insert \
  | jq .
```

期待レスポンス：
```json
{
  "payload": {
    "ids": ["123"]
  }
}
```

TEXT ID の場合：
```json
{
  "payload": {
    "recordIds": [{"valueText": "001xx000003DIloAAG"}]
  }
}
```

---

## 5. Update

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "records": [
        {
          "fields": {
            "id": {"recordIdField": {"fieldId":"id","value":"123"}},
            "name": {"textField": {"fieldId":"name","value":"Acme Corp Updated"}}
          }
        }
      ]
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Update \
  | jq .
```

期待レスポンス：
```json
{
  "payload": {
    "records": [{"id": "123"}]
  }
}
```

---

## 6. Delete

```bash
# NUMBER ID
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "ids": ["123", "124"]
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Delete \
  | jq .

# TEXT ID
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "recordIds": [
        {"valueText": "001xx000003DIloAAG"},
        {"valueText": "001xx000003DIlpAAG"}
      ]
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Delete \
  | jq .
```

---

## 7. Count

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "filterConditions": [{"allRecords":{}}],
      "matchOperator": "MATCH_OPERATOR_ALL"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Count \
  | jq .
```

期待レスポンス：
```json
{"payload": {"count": "1234"}}
```

---

## 8. Search

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "keywords": ["customer", "tokyo"],
      "searchOperator": "SEARCH_OPERATOR_ALL",
      "offset": "0",
      "limit": "20"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Search \
  | jq .
```

---

## 9. Aggregate

```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "groupingSpecs": [
        {"field": "status"}
      ],
      "aggregationSpecs": [
        {"function": "AGGREGATE_FUNCTION_COUNT"},
        {"field": "revenue", "function": "AGGREGATE_FUNCTION_SUM"}
      ],
      "filterConditions": [{"allRecords":{}}],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [
        {"target":"AGGREGATE_SORT_TARGET_AGGREGATION","index":"0","sortDirection":"AGGREGATE_SORT_DIRECTION_DESC"}
      ],
      "limit": "100"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Aggregate \
  | jq .
```

---

## grpcurl での代替

gRPC バイナリでテストしたい場合：

```bash
brew install grpcurl

# .proto ファイルを指定する形
grpcurl -plaintext \
  -proto reference/kintone-data-connector/adapter_service.proto \
  -import-path reference/kintone-data-connector \
  -d '{}' \
  localhost:8083 \
  cybozu.data_connector.adapter.v1.AdapterService/GetCapability
```

`-plaintext` は TLS なし。本番テストでは外す。

reflection が Adapter 側で有効なら：
```bash
grpcurl -plaintext localhost:8083 list
grpcurl -plaintext localhost:8083 describe cybozu.data_connector.adapter.v1.AdapterService
```

---

## エラーケースの確認

### 不正な FilterCondition
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "filterConditions": [{"textContains": {"fieldId":"nonexistent_field","value":"x"}}],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "10"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

期待される失敗レスポンス：
```json
{
  "code": "invalid_argument",
  "message": "Unsupported Field ID: nonexistent_field"
}
```

### limit 上限超え
```bash
curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data '{
    "payload": {
      "fields": [],
      "filterConditions": [{"allRecords":{}}],
      "matchOperator": "MATCH_OPERATOR_ALL",
      "sortConditions": [],
      "offset": "0",
      "limit": "999"
    }
  }' \
  http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/Select \
  | jq .
```

protovalidate で `limit` が 1〜501 範囲外として拒否される（実装による）。

---

## デバッグ Tips

### Verbose モード
```bash
curl -v --http2-prior-knowledge ...
```
HTTP/2 ヘッダー、ステータス、レスポンスサイズ等の詳細が見える。

### バイナリレスポンスを確認
Connect ライブラリはデフォルトで `Content-Type: application/json` で応答する。バイナリ確認したい場合：
```bash
curl --http2-prior-knowledge \
  -H 'Content-Type: application/proto' \
  -H 'Accept: application/proto' \
  --data-binary @request.bin \
  http://localhost:8083/... \
  -o response.bin
```

### 不可解な500エラーの原因切り分け
- Adapter のログを見る
- `curl -v` でレスポンスヘッダ確認（`grpc-status` ヘッダにエラーコード）
- protovalidate のバリデーション失敗は `400 Bad Request` 系

---

## bash ヘルパースクリプト案

`scripts/test-adapter.sh`:
```bash
#!/bin/bash
ADAPTER_URL="${ADAPTER_URL:-http://localhost:8083}"
METHOD="$1"
BODY="${2:-{}}"

curl -s --http2-prior-knowledge \
  --header 'Content-Type: application/json' \
  --data "$BODY" \
  "$ADAPTER_URL/cybozu.data_connector.adapter.v1.AdapterService/$METHOD" \
  | jq .
```

使用例：
```bash
./scripts/test-adapter.sh GetCapability
./scripts/test-adapter.sh Count '{"payload":{"filterConditions":[{"allRecords":{}}],"matchOperator":"MATCH_OPERATOR_ALL"}}'
```
