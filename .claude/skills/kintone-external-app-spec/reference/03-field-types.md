# 03. フィールド型（Field / FieldDefinition）

## Field（レコードに含まれる値）

`Record` は `map<string, Field>` で、キーが field_id、値が型付き値。

```protobuf
message Record {
  map<string, Field> fields = 1;
}

message Field {
  oneof field {
    RecordIdField record_id_field = 1;
    TextField text_field = 2;
    DatetimeField datetime_field = 3;
    NumberField number_field = 4;
    SelectionField selection_field = 5;
    MultipleSelectionField multiple_selection_field = 6;
  }
}
```

### 6つのフィールド型

| Field 型 | 値型 | kintone 側フィールド | 備考 |
|---|---|---|---|
| `RecordIdField` | `int64 value` または `RecordId record_id_value` | レコード番号 | NUMBER または TEXT |
| `TextField` | `string value` | 文字列(一行)/文字列(複数行) | |
| `DatetimeField` | `google.protobuf.Timestamp value` | 日時 | UTC基準 |
| `NumberField` | `optional double value` | 数値 | double（精度損失に注意） |
| `SelectionField` | `Option value` | ドロップダウン | 単一選択 |
| `MultipleSelectionField` | `repeated Option value` | 複数選択 | サンプル未実装 |

```protobuf
message RecordIdField {
  string field_id = 1;
  int64 value = 2;              // RecordIdType=NUMBER の場合
  RecordId record_id_value = 3; // RecordIdType=TEXT の場合
}

message TextField {
  string field_id = 1;
  string value = 2;
}

message DatetimeField {
  string field_id = 1;
  google.protobuf.Timestamp value = 2;  // null = 未設定
}

message NumberField {
  string field_id = 1;
  optional double value = 2;  // 明示的なnullableマーキング
}

message SelectionField {
  string field_id = 1;
  Option value = 2;
}

message MultipleSelectionField {
  string field_id = 1;
  repeated Option value = 2;
}

message Option {
  string value = 1;  // 1〜128文字
}
```

### RecordId（NUMBER と TEXT の両対応）

```protobuf
message RecordId {
  oneof value {
    int64 value_number = 1;
    string value_text = 2;  // ^[a-zA-Z0-9_-]{0,100}$
  }
}
```

**CData連携での重要ポイント**:
- Salesforce ID（18文字英数）→ `value_text` でそのまま渡せる
- SAP の DocEntry → `value_number`
- DB の auto-increment id → `value_number`
- 複合キー → 文字列連結して `value_text`（区切り文字に `-` か `_` を使う）

`GetCapability` response の `record_id_type` でどちらを採用するか宣言する。**1つの Adapter ではどちらか一方しか使えない**（混在不可と推測）。

---

## FieldDefinition（スキーマ定義）

`GetSchema` のレスポンスで使用。「このフィールドは何型か」を定義する。

```protobuf
message FieldDefinition {
  oneof definition {
    RecordIdFieldDefinition record_id_field_definition = 1;
    TextFieldDefinition text_field_definition = 2;
    DatetimeFieldDefinition datetime_field_definition = 3;
    NumberFieldDefinition number_field_definition = 4;
    SelectionFieldDefinition selection_field_definition = 5;
    MultipleSelectionFieldDefinition multiple_selection_field_definition = 6;
  }
}

message RecordIdFieldDefinition  { string field_id = 1; }
message TextFieldDefinition      { string field_id = 1; }
message DatetimeFieldDefinition  { string field_id = 1; }
message NumberFieldDefinition    { string field_id = 1; }

message SelectionFieldDefinition {
  string field_id = 1;
  repeated Option options = 2;  // 選択肢一覧（必須）
}

message MultipleSelectionFieldDefinition {
  string field_id = 1;
  repeated Option options = 2;  // 選択肢一覧（必須）
}
```

選択系のみ `options` が必須。スキーマ取得時点で全選択肢を返す必要がある（後から動的に増減はできない設計）。

---

## CData JDBC ↔ protobuf 型マッピング指針

| JDBC 型 (java.sql.Types) | protobuf Field | 備考 |
|---|---|---|
| `BIGINT` / `INTEGER` （主キー） | `RecordIdField` (NUMBER) | レコード番号として |
| `VARCHAR` / `CHAR` （主キー） | `RecordIdField` (TEXT) | Salesforce ID 等 |
| `VARCHAR` / `CHAR` / `LONGVARCHAR` | `TextField` | 通常文字列 |
| `BIGINT` / `INTEGER` / `SMALLINT` / `DECIMAL` / `NUMERIC` / `FLOAT` / `DOUBLE` / `REAL` | `NumberField` | 全て `double` に変換。`BIGINT` は精度損失リスクあり |
| `TIMESTAMP` / `DATE` / `TIME` | `DatetimeField` | UTC Timestamp に変換 |
| `BOOLEAN` | `TextField` ("true"/"false") or `SelectionField` | 直接対応なし。実装方針要決定 |
| `BLOB` / `CLOB` | 非対応 | kintone 側に対応フィールド型なし |

**注意点**:
- kintone 側のフィールド型制約（[05-constraints.md](05-constraints.md) 参照）で「リッチエディタ」「計算」「複数選択（kintone組込みのチェックボックス等）」「ファイル添付」は使えない
- `NumberField` の `double` は IEEE 754 倍精度。`DECIMAL(38,10)` などは精度損失する → 必要なら `TextField` でフォールバック
- 日付のみ（`java.sql.Date`）と時刻のみは `DatetimeField` に変換するしかないが、UTC 0:00 等の規約決めが必要

---

## サンプル adapter での型登録方法

サンプルでは `src/FieldIds.ts` で各フィールド型に分類された field_id の配列を定義：

```typescript
export const columnsRecordIdFieldId = "id";
export const columnsTextFieldIds = ["code", "name", "category"] as const;
export const columnsNumberFieldIds = ["price", "price_decimal"] as const;
export const columnsDatetimeFieldIds = ["created_at", "start_date"] as const;
```

`src/selectionFields.ts` で SelectionField を定義：

```typescript
export const selectionFieldOptions = {
  valid: ["on", "off"] as const,
  code_status: ["active", "inactive", "pending"] as const,
} as const;
```

CData JDBC 版では、これを `config/capability.yaml`（RecordIdType・filterable_fields 等）と `config/table.yaml`（カラム定義・SELECTION 選択肢等）から読み込む形にする（4ファイル分割設定の詳細は [08-jdbc-mapping.md](08-jdbc-mapping.md) 参照）。
