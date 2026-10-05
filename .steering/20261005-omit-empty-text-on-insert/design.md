# Insert で空文字のテキスト値を送らない — 設計

| 項目 | 内容 |
|---|---|
| 要求定義 | [requirements.md](requirements.md) |
| Issue | [#76](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/76) |

---

## 1. 方針

**Insert 専用の変換メソッドを `RowMapper` に追加する。** 既定引数で切り替えない（C-3）。

```
Insert:  rowMapper.recordToInsertValues(record)   ← 新規。主キーと空文字テキストを省く
Update:  rowMapper.recordToColumnValues(record)   ← 変更なし
```

フィールド型を見られるのは `RowMapper` だけなので、判定はここに置く（C-2）。
サービス層は「Insert 用の変換を呼ぶ」だけになる。

既定引数（`recordToColumnValues(record, omitEmptyText = true)`）にしない理由は、
呼び出し側で挙動の違いが読み取れなくなるため。Insert と Update で意味が変わる
変換を、引数 1 つの差に埋めると事故る。

## 2. 主キーの除外をメソッド側に移す

現状、主キーの除外はサービス層でやっている。

```kotlin
// AdapterServiceImpl.insert
val values = rowMapper.recordToColumnValues(record)
    .filterKeys { it != config.table.primaryKey.kintoneFieldId }
```

空文字を省いた結果 0 列になるケース（F-3）の判定は、**主キーを除いた後**でないと
正しくできない。`{id: ..., name: ""}` は空文字を省くと `{id: ...}` で 1 件あるが、
主キーを除くと 0 件になる。

そのため主キーの除外も `recordToInsertValues` の中に移す。Insert が主キーを
書き込まないのは仕様なので、メソッドの責務として自然（AC-6）。

## 3. 実装

> **実機確認を受けた変更**: 当初はテキストの空文字だけを対象にしていたが、
> `ModifiedDate`（`NOT NULL`・既定値 `getdate()`）が NULL で失敗したため、
> **未入力の数値・日時・選択も省く**ことにした。詳細は requirements.md §8。


```kotlin
/**
 * Insert 用に Record を `kintone field_id → 値` のマップへ変換する。
 *
 * 主キーと、**空文字のテキスト値**を含めない。自動生成列をマッピングしている
 * 連携で、空文字が `uniqueidentifier` 等の列に渡って失敗するのを防ぐ (Issue #76)。
 *
 * 空文字を省けるのは Insert だけ。`TextField.value` は proto3 の presence を
 * 持たないため「空文字を送った」と「送っていない」を区別できず、Update で省くと
 * テキスト項目を空にする操作ができなくなる。Insert には「既存の値を空にする」
 * という操作が無いので副作用がない。
 */
fun recordToInsertValues(record: Record): Map<String, Any?> {
    val withoutId = record.fieldsMap.filterKeys { it != table.primaryKey.kintoneFieldId }
    val kept = withoutId.filterValues { !isAbsent(it) }
    // すべて空だった場合は省かない。INSERT 対象が 0 列になり組み立てに失敗する。
    return kept.ifEmpty { withoutId }.mapValues { (_, field) -> fieldToValue(field) }
}

/** 値が入っていないフィールドか。テキストだけ presence が無く、空文字で判定する。 */
private fun isAbsent(field: Field): Boolean = when (field.fieldCase) {
    Field.FieldCase.TEXT_FIELD -> field.textField.value.isEmpty()
    Field.FieldCase.NUMBER_FIELD -> !field.numberField.hasValue()
    Field.FieldCase.DATETIME_FIELD -> !field.datetimeField.hasValue()
    Field.FieldCase.SELECTION_FIELD -> !field.selectionField.hasValue()
    else -> false
}
```

`fieldToValue` は共有する。空文字の判定だけが Insert 固有。

### サービス層

```kotlin
// 変更前
val values = rowMapper.recordToColumnValues(record)
    .filterKeys { it != config.table.primaryKey.kintoneFieldId }
// 変更後
val values = rowMapper.recordToInsertValues(record)
```

## 4. 0 列になるケースを省略しない理由（F-3）

マッピングが全てテキストで、kintone 側で全項目を空のまま登録した場合、
省略すると Insert 対象が 0 列になり `buildInsert` の
`require(columnValues.isNotEmpty())` に落ちる。

`INSERT INTO t DEFAULT VALUES` は移植性が無く、CData ドライバーで通る保証もない。
この場合は**従来どおり空文字を送る**（挙動を変えない）のが最も安全。空文字しか
無いレコードを登録しようとしている状況自体が稀で、そこを救うために SQL の
組み立て方を増やす価値はない。

## 5. 影響範囲

| ファイル | 変更 |
|---|---|
| `jdbc/RowMapper.kt` | `recordToInsertValues` / `isEmptyText` を追加 |
| `service/AdapterServiceImpl.kt` | Insert の 1 行を差し替え |
| `jdbc/QueryBuilder.kt` | 変更なし |
| Update の経路 | 変更なし（C-1） |

## 6. テスト

| テスト | 内容 |
|---|---|
| `RowMapperTest` | Insert: 空文字のテキストがマップに入らない（AC-1） |
| | Insert: 値のあるテキストは入る（AC-2） |
| | Insert: 空白だけのテキストは入る（意図した値の可能性がある） |
| | Insert: 主キーが入らない（AC-6） |
| | Insert: 未入力の NUMBER / DATETIME / SELECTION が入らない |
| | Insert: 値のある NUMBER / DATETIME は入る |
| | Insert: 全フィールドが未入力なら省かず全件返す（AC-5） |
| | Insert: 主キー以外が全て未入力でも空のマップにならない（AC-5） |
| | Update: 空文字のテキストがマップに入る（AC-3） |
| | Update: 未入力の NUMBER / DATETIME が `null` として入る（AC-4） |
| | Update: 主キーが入る |
| `QueryBuilderTest` | 変更なし（組み立て側は手を入れない） |

## 7. 実機確認（AC-9）

`rowguid` をマッピングした連携を作り、kintone から Insert する。
手元では kintone を介さず gRPC で `Insert` を直接叩いて確認する。

```
grpcurl -plaintext -d '{"payload":{"records":[...]}}' localhost:<port> \
  cybozu.data_connector.adapter.v1.AdapterService/Insert
```

確認後は投入した行を削除して AdventureWorksLT を元に戻す。
