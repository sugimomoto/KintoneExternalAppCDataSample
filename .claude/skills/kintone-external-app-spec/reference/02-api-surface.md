# 02. API サーフェス（9 RPC メソッド）

`cybozu.data_connector.adapter.v1.AdapterService` が提供する全 RPC。
すべて Unary RPC（双方向ストリーミングではない単純なリクエスト/レスポンス）。

## 全 RPC 一覧

| # | RPC 名 | 呼出タイミング | 必須/任意 | サンプル実装 |
|---|---|---|---|---|
| 1 | `GetCapability` | コネクター管理画面で接続ボタン押下時 | **必須** | あり |
| 2 | `GetSchema` | 外部連携アプリの設定画面を開いた時 | **必須** | あり |
| 3 | `Select` | レコード一覧表示・詳細表示時 | 任意 | あり |
| 4 | `Insert` | レコード追加時 | 任意 | あり |
| 5 | `Update` | レコード更新時 | 任意 | あり |
| 6 | `Delete` | レコード削除時 | 任意 | あり |
| 7 | `Count` | レコード一覧表示時 | 任意 | あり |
| 8 | `Search` | 検索AI機能利用時 | 任意 | コメントアウト |
| 9 | `Aggregate` | グラフ画面を開いた時 | 任意 | なし |

GetCapability/GetSchema 以外を「未対応」とする場合は、GetCapability で `*_operation_supported = false` を返す。

## 共通：Context

全 Request の field 1 に `Context`：

```protobuf
message Context {
  string operation_id = 1;  // UUID形式
  string app_id = 2;
  UserInfo user_info = 3;
}
message UserInfo {
  string id = 1;
  string code = 2;
}
```

ログ出力・監査・ユーザー固有処理に使える。

---

## 1. GetCapability（必須）

**用途**: Adapter が提供する機能を kintone 側に宣言する。

### Request
```protobuf
message GetCapabilityRequestPayload {}  // 空
```

### Response
```protobuf
message GetCapabilityResponsePayload {
  bool select_operation_supported = 1;
  bool insert_operation_supported = 2;
  bool update_operation_supported = 3;
  bool delete_operation_supported = 4;
  bool count_operation_supported = 5;
  repeated string filterable_fields = 6;   // 絞り込み対象にできるfield_id
  repeated string sortable_fields = 7;     // ソート対象にできるfield_id
  bool search_operation_supported = 8;
  RecordIdType record_id_type = 9;         // NUMBER または TEXT
}

enum RecordIdType {
  RECORD_ID_TYPE_UNSPECIFIED = 0;
  RECORD_ID_TYPE_NUMBER = 1;  // 従来のBigInt ID
  RECORD_ID_TYPE_TEXT = 2;    // 英数+_-で最大100文字（Salesforce等向け）
}
```

**重要**: `filterable_fields` / `sortable_fields` を空にすると、kintone 側で絞り込み・ソート UI が無効化される（と推測される）。Aggregate サポートに対応するフラグはこのメッセージには無く、暗黙的に対応されるか実装ガイド側で別管理になる可能性あり（要検証）。

---

## 2. GetSchema（必須）

**用途**: 外部レコードのフィールド定義（field_id とフィールド型）を返す。

### Request
```protobuf
message GetSchemaRequestPayload {}  // 空
```

### Response
```protobuf
message GetSchemaResponsePayload {
  map<string, FieldDefinition> schema = 1;
}
```

`map` のキーが field_id（カラム名）、値が型定義。FieldDefinition の詳細は [03-field-types.md](03-field-types.md)。

---

## 3. Select（任意）

**用途**: 条件指定でレコード一覧を取得。

### Request
```protobuf
message SelectRequestPayload {
  repeated string fields = 1;                    // 取得するfield_id（空配列なら全カラム）
  repeated FilterCondition filter_conditions = 2;
  MatchOperator match_operator = 3;              // ALL=AND, ANY=OR
  repeated SortCondition sort_conditions = 4;
  int64 offset = 5;                              // 0以上
  int64 limit = 6;                               // 1〜501
}
```

### Response
```protobuf
message SelectResponsePayload {
  repeated Record records = 1;
}
```

`Record` 詳細は [03-field-types.md](03-field-types.md)。

---

## 4. Insert（任意）

**用途**: 新規レコードを追加し、発行された ID を返す。

### Request
```protobuf
message InsertRequestPayload {
  repeated Record records = 1;
}
```

### Response
```protobuf
message InsertResponsePayload {
  repeated int64 ids = 1;             // RecordIdType=NUMBER の場合
  repeated RecordId record_ids = 2;   // RecordIdType=TEXT の場合
}
```

サンプルは `ids` のみ使用。CData JDBC で Salesforce 等 TEXT ID のデータソースに対応する場合は `record_ids` を返すべき。

---

## 5. Update（任意）

**用途**: 既存レコードを部分更新。

### Request
```protobuf
message UpdateRequestPayload {
  repeated Record records = 1;
}
```

各 Record は ID フィールド + 更新したいフィールドを含む。サンプルでは ID 必須チェックあり。

### Response
```protobuf
message UpdateResponsePayload {
  repeated UpdatedRecordInfo records = 1;
}
message UpdatedRecordInfo {
  int64 id = 1;
  RecordId record_id = 2;
}
```

---

## 6. Delete（任意）

**用途**: ID 指定で複数レコード削除。

### Request
```protobuf
message DeleteRequestPayload {
  repeated int64 ids = 1;              // NUMBER ID
  repeated RecordId record_ids = 2;    // TEXT ID
}
```

### Response
```protobuf
message DeleteResponsePayload {}  // 空
```

---

## 7. Count（任意）

**用途**: 絞り込み条件でレコード件数を返す（一覧表示時のページング計算用と思われる）。

### Request
```protobuf
message CountRequestPayload {
  repeated FilterCondition filter_conditions = 1;
  MatchOperator match_operator = 2;
}
```

### Response
```protobuf
message CountResponsePayload {
  int64 count = 1;  // 0以上
}
```

---

## 8. Search（任意・サンプル未実装）

**用途**: 全文検索（kintone 検索 AI 機能）。

### Request
```protobuf
message SearchRequestPayload {
  repeated string fields = 1;
  repeated string keywords = 2;
  SearchOperator search_operator = 3;  // ALL=全キーワードAND, ANY=OR
  int64 offset = 4;
  int64 limit = 5;                     // 1〜100
}

enum SearchOperator {
  SEARCH_OPERATOR_UNSPECIFIED = 0;
  SEARCH_OPERATOR_ALL = 1;
  SEARCH_OPERATOR_ANY = 2;
}
```

### Response
```protobuf
message SearchResponsePayload {
  repeated SearchResult search_results = 1;
}
message SearchResult {
  Record record = 1;
}
```

サンプルでは MySQL FULLTEXT インデックスで実装する例がコメントアウトされている。CData JDBC では `CONTAINS()` / `LIKE` ベースの実装か、データソース固有検索クエリへの変換が必要。

---

## 9. Aggregate（任意・サンプル未実装）

**用途**: グラフ用の集計（GROUP BY + 集計関数）。

### Request
```protobuf
message AggregateRequestPayload {
  repeated GroupingSpec grouping_specs = 1;
  repeated AggregationSpec aggregation_specs = 2;
  repeated FilterCondition filter_conditions = 3;
  MatchOperator match_operator = 4;
  repeated AggregateSortCondition sort_conditions = 5;
  int64 limit = 6;                                  // 1〜10001
}

message GroupingSpec {
  string field = 1;
  GroupingMethod grouping_method = 2;
}

enum GroupingMethod {
  GROUPING_METHOD_UNSPECIFIED = 0;
  GROUPING_METHOD_DATETIME_YEAR = 1;
  GROUPING_METHOD_DATETIME_QUARTER = 2;  // 未対応
  GROUPING_METHOD_DATETIME_MONTH = 3;
  GROUPING_METHOD_DATETIME_WEEK = 4;     // 未対応
  GROUPING_METHOD_DATETIME_DAY = 5;
  GROUPING_METHOD_DATETIME_HOUR = 6;
  GROUPING_METHOD_DATETIME_MINUTE = 7;
}

message AggregationSpec {
  optional string field = 1;
  AggregateFunction function = 2;
}

enum AggregateFunction {
  AGGREGATE_FUNCTION_UNSPECIFIED = 0;
  AGGREGATE_FUNCTION_COUNT = 1;
  AGGREGATE_FUNCTION_SUM = 2;
  AGGREGATE_FUNCTION_AVERAGE = 3;
  AGGREGATE_FUNCTION_MAXIMUM = 4;
  AGGREGATE_FUNCTION_MINIMUM = 5;
}
```

### Response
```protobuf
message AggregateResponsePayload {
  repeated AggregateRow rows = 1;
}
message AggregateRow {
  repeated Group groups = 1;
  repeated AggregateResult results = 2;
}
message Group {
  string field = 1;
  GroupingMethod grouping_method = 2;
  oneof value {
    string string_value = 3;
    double double_value = 4;
    google.protobuf.Timestamp datetime_value = 5;
  }
}
message AggregateResult {
  oneof result {
    CountResult count = 1;
    SumResult sum = 2;
    AverageResult average = 3;
    MaximumResult maximum = 4;
    MinimumResult minimum = 5;
  }
}
```

CData JDBC では `SELECT field, COUNT(*) FROM table WHERE ... GROUP BY field` に翻訳。`DATETIME_*` 系の集計には `EXTRACT(YEAR FROM ...)` 等のSQL関数を使う。

---

## バリデーション制約まとめ

| 項目 | 制約 |
|---|---|
| `field_id` 文字列長 | 1〜128文字 |
| `RecordId.value_text` パターン | `^[a-zA-Z0-9_-]{0,100}$` |
| Select `offset` | 0以上 |
| Select `limit` | 1〜501 |
| Search `offset` | 0以上 |
| Search `limit` | 1〜100 |
| Aggregate `limit` | 1〜10001 |
| Context `operation_id` | UUID形式 |

これらに違反した場合は `Code.InvalidArgument` で `ConnectError` を返す（サンプル準拠）。
