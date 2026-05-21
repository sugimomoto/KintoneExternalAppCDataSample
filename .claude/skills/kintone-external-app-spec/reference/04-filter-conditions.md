# 04. FilterCondition 全39種

`FilterCondition` は `oneof condition` で **39種類** の case を持つ。`MatchOperator` (ALL=AND / ANY=OR) と組み合わせて使用。

## 一覧（タグ番号 = oneof case 番号）

| # | oneof case | 値型 | SQL 等価 | サンプル実装 |
|---|---|---|---|---|
| 1 | `all_records` | (なし) | （無条件） | ○ |
| 2 | `record_id_equal` | int64 + RecordId | `field = ?` | ○ |
| 3 | `record_id_not_equal` | int64 + RecordId | `field != ?` | ○ |
| 4 | `record_id_greater_than_or_equal` | int64 | `field >= ?` | ○ |
| 5 | `record_id_less_than_or_equal` | int64 | `field <= ?` | ○ |
| 6 | `text_equal` | string | `field = ?` | ○ |
| 7 | `text_not_equal` | string | `field != ?` | ○ |
| 8 | `text_in` | repeated string | `field IN (?, ?...)` | ○ |
| 9 | `text_contains` | string | `field LIKE '%?%'` | ○ |
| 10 | `text_not_contains` | string | `field NOT LIKE '%?%'` | ○ |
| 11 | `datetime_equal` | Timestamp | `field = ?` | ○ |
| 12 | `datetime_not_equal` | Timestamp | `field != ?` | ○ |
| 13 | `datetime_greater_than` | Timestamp | `field > ?` | ○ |
| 14 | `datetime_greater_than_or_equal` | Timestamp | `field >= ?` | ○ |
| 15 | `datetime_less_than` | Timestamp | `field < ?` | ○ |
| 16 | `datetime_less_than_or_equal` | Timestamp | `field <= ?` | ○ |
| 17 | `number_equal` | optional double | `field = ?` または `field IS NULL` | ○ |
| 18 | `number_not_equal` | optional double | `field != ?` または `field IS NOT NULL` | ○ |
| 19 | `number_greater_than_or_equal` | optional double | `field >= ?` | ○ |
| 20 | `number_less_than_or_equal` | optional double | `field <= ?` | ○ |
| 21 | `selection_in` | repeated NullableOption | `field IN (?, ?...)` または `field IS NULL` | ○ |
| 22 | `selection_not_in` | repeated NullableOption | `field NOT IN (?, ?...)` | ○ |
| 23 | `record_id_greater_than` | int64 | `field > ?` | ○ |
| 24 | `record_id_less_than` | int64 | `field < ?` | ○ |
| 25 | `record_id_in` | repeated int64 + RecordId | `field IN (?...)` | ○ |
| 26 | `record_id_not_in` | repeated int64 + RecordId | `field NOT IN (?...)` | ○ |
| 27 | `text_not_in` | repeated string | `field NOT IN (?...)` | ○ |
| 28 | `datetime_in_range` | start + end Timestamp | `field >= ? AND field < ?` | ○ |
| 29 | `datetime_not_in_range` | start + end Timestamp | `NOT (field >= ? AND field < ?)` | ○ |
| 30 | `number_greater_than` | optional double | `field > ?` | ○ |
| 31 | `number_less_than` | optional double | `field < ?` | ○ |
| 32 | `number_in` | repeated double | `field IN (?...)` | ○ |
| 33 | `number_not_in` | repeated double | `field NOT IN (?...)` | ○ |
| 34 | `multiple_selection_in` | repeated NullableOption | 複数選択フィールドの和集合検索 | × |
| 35 | `multiple_selection_not_in` | repeated NullableOption | 複数選択フィールドの否定検索 | × |
| 36 | `text_is` | TextFieldValueState (EMPTY) | `field = ''` または `field IS NULL` | × |
| 37 | `text_is_not` | TextFieldValueState (EMPTY) | `field != ''` または `field IS NOT NULL` | × |
| 38 | `record_id_contains` | string (英数+_-, 100文字以内) | `field LIKE '%?%'`（TEXT ID用） | × |
| 39 | `record_id_not_contains` | string (英数+_-, 100文字以内) | `field NOT LIKE '%?%'`（TEXT ID用） | × |

## サンプル未実装の case（CData版で対応必須/任意）

サンプル (`src/query.ts`) は **default branch で例外を投げる** ため、未実装 case を kintone が送ってきたら 400 エラーになる。
CData 版では以下を対応する必要がある：

- `multiple_selection_in` / `multiple_selection_not_in` ← MultipleSelectionField を扱う場合
- `text_is` / `text_is_not` ← 「未入力」フィルター UI に対応
- `record_id_contains` / `record_id_not_contains` ← TEXT ID 採用時のみ

「対応しない」と決めたフィルターは、`GetCapability` の `filterable_fields` から該当フィールドを除外すれば、kintone 側の UI で選択肢として出ないはず（要検証）。

---

## protobuf 詳細（主要なもの）

### FilterCondition の構造

```protobuf
message FilterCondition {
  oneof condition {
    FilterConditionAllRecords all_records = 1;
    FilterConditionRecordIdEqual record_id_equal = 2;
    // ... (全39 case)
  }
}

enum MatchOperator {
  MATCH_OPERATOR_UNSPECIFIED = 0;
  MATCH_OPERATOR_ALL = 1;  // AND
  MATCH_OPERATOR_ANY = 2;  // OR
}
```

### `NullableOption`（選択系で使用）

```protobuf
message NullableOption {
  optional Option option = 1;  // null = 未選択を表す
}
```

`selection_in` で `values` の中に NULL が混在する場合、SQL 上は `(field IN (...)) OR (field IS NULL)` の形にする必要がある（サンプル query.ts の `convertNullableOptionsToInClause` 関数参照）。

### `TextFieldValueState`

```protobuf
enum TextFieldValueState {
  TEXT_FIELD_VALUE_STATE_UNSPECIFIED = 0;
  TEXT_FIELD_VALUE_STATE_EMPTY = 1;
}
```

`text_is` / `text_is_not` で「未入力」かどうかをチェックする。

### `datetime_in_range` の半開区間

```protobuf
message FilterConditionDatetimeInRange {
  string field_id = 1;
  google.protobuf.Timestamp start = 2;  // 含む
  google.protobuf.Timestamp end = 3;    // 含まない
}
```

SQL に翻訳するときは `field >= start AND field < end` （半開区間 `[start, end)`）。

---

## サンプル (`src/query.ts`) の Prisma 翻訳パターン

```typescript
case "textContains": {
  return {
    [filter.condition.value.fieldId]: {
      contains: filter.condition.value.value,
    },
  };
}

case "datetimeInRange": {
  return {
    [filter.condition.value.fieldId]: {
      not: null,
      gte: convertTimestampToDate(filter.condition.value.start),
      lt: convertTimestampToDate(filter.condition.value.end),
    },
  };
}

case "selectionIn": {
  return convertNullableOptionsToInClause(
    filter.condition.value.fieldId,
    filter.condition.value.values,
  );
}
```

最終的にすべての case を結合して `where: { AND: [...] }` または `where: { OR: [...] }` の形で Prisma に渡す。

CData JDBC 版は **PreparedStatement のパラメータバインド** + SQL 文字列を組み立てる形になる。詳細は [08-jdbc-mapping.md](08-jdbc-mapping.md) 参照。

---

## NULL の扱い（重要）

`number_equal` / `selection_in` などで NULL 値が含まれうる場合：
- SQL の `IN (1, 2, NULL)` は NULL を含まない結果になる（SQL 仕様）
- そのため、サンプルでは「NULL を分離して `OR field IS NULL` 句として追加」している
- CData JDBC 実装でも同様のロジックが必要

```sql
-- ダメ: NULL がマッチしない
WHERE status IN ('a', 'b', NULL)

-- 正解: NULL を分離
WHERE status IN ('a', 'b') OR status IS NULL
```
