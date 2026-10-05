# レコード ID 列の指定 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#66](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/66) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 候補の型判定は **`FieldTypeSuggester` に関数を足して再利用**する | `suggestRecordIdType` が既に対応表を持ち、対応外で `error()` を投げる。型の集合を 2 箇所に書かない（C-1） | ビューやルートに型リストを書く案 → 対応表が二重化し、片方だけ直す事故が起きる |
| D-2 | step4 が **`recordIdColumn` を任意で受け取る** | ステップ構成を変えずに済む（C-3）。無ければ従来どおり `findPrimaryKey` を使うので、主キーのあるテーブルは手順が増えない（AC-7） | step3.5 を新設する案 → 画面が増え、戻る導線も複雑になる |
| D-3 | 選択 UI は **#64 のエラー表示の経路に載せる** | `NoPrimaryKey` で step3 に戻す流れが既にある。そこに選択 UI を足すだけで済む（C-5） | 専用画面を作る案 |
| D-4 | 保存形式は**変えない** | `PrimaryKeyConfig.jdbcColumn` に入れるだけ。主キー由来か利用者選択かを区別して保存しない（C-2）。実行時の扱いは同じで、区別する利用者がいない | 由来を保存する案 → 使わない情報を増やす |
| D-5 | **一意性は検証しない** | `COUNT(*)` と `COUNT(DISTINCT col)` の比較は大きなテーブルで重く、設定操作が待たされる。代わりに**責任の所在を画面で明示**する（AC-3） | 常に検証する案 → 設定のたびに全件走査 |
| D-6 | 候補が 0 件なら**従来の「連携できない」表示**にする | 選ばせる列が無い状態で選択 UI を出しても意味がない（AC-6） | 常に選択 UI を出す案 |
| D-7 | `Step4Result.NoPrimaryKey` の `candidates` を**絞ってから詰める** | ビューが型判定を知らなくて済む。#64 で候補列を持たせた意図どおり | ビュー側で絞る案 |

### 1.2 フロー

```
step3 でカラムを選んで送信
  → POST /syncs/new/step4（recordIdColumn なし）
      findPrimaryKey あり → Ready → step4 を描画（従来どおり、AC-7）
      findPrimaryKey なし → NoPrimaryKey(tableName, 候補列)
          ├─ 候補あり → step3 に戻し、レコード ID 列の選択 UI + 注意書きを出す
          └─ 候補なし → 従来の「連携できない」表示（AC-6）

レコード ID 列を選んで再送信
  → POST /syncs/new/step4（recordIdColumn あり）
      → 指定列で Ready → step4 を描画（AC-1）

保存
  → PrimaryKeyConfig.jdbcColumn = 指定列（AC-4）
  → RecordIdType は指定列の JDBC 型から決まる（AC-5）
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `metadata/FieldTypeSuggester.kt` | 変更 | `canBeRecordId(jdbcType)` を追加し、`suggestRecordIdType` と対応表を共有 |
| `web/routes/TableWizardRoutes.kt` | 変更 | `recordIdColumn` を受け取る。候補を絞って `NoPrimaryKey` に詰める |
| `web/views/TableWizardView.kt` | 変更 | 候補列の選択 UI と注意書き |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `metadata/FieldTypeSuggesterTest.kt` | 変更 | `canBeRecordId` の判定（対応型／非対応型） |
| `web/views/RecordIdSelectionTest.kt` | 新規 | 選択 UI の描画（候補の有無・注意書き） |

---

## 3. 実装

### 3.1 型判定の共有

```kotlin
/** レコード番号に使える JDBC 型か。候補列の絞り込みに使う。 */
fun canBeRecordId(jdbcType: Int): Boolean = recordIdTypeOrNull(jdbcType) != null

/**
 * 主キーカラムの JDBC 型から RecordIdType を推奨する。
 * 数値型でも文字列型でもない型の場合はエラー。
 */
fun suggestRecordIdType(jdbcType: Int): RecordIdType =
    recordIdTypeOrNull(jdbcType) ?: error("主キーとして対応できない JDBC 型: $jdbcType")

/** 対応表はここだけに置く。[canBeRecordId] と [suggestRecordIdType] で共有する。 */
private fun recordIdTypeOrNull(jdbcType: Int): RecordIdType? = when (jdbcType) { ... }
```

### 3.2 ルート

```kotlin
val recordIdColumn = form["recordIdColumn"]?.takeIf { it.isNotBlank() }
...
// 利用者が選んだ列があればそれを使う。無ければ主キーを探す (Issue #66)。
val idColumn = recordIdColumn?.let { name -> allColumns.firstOrNull { it.name == name } }
    ?: inspector.findPrimaryKey(tableName, schema)?.let { pk -> allColumns.firstOrNull { it.name == pk.column } }
    ?: return@use Step4Result.NoPrimaryKey(tableName, allColumns.filter { FieldTypeSuggester.canBeRecordId(it.jdbcType) })
```

`NoPrimaryKey` に詰める時点で候補を絞る（D-7）。

### 3.3 ビュー

`Step3Content` に候補列を足し、あれば選択 UI を描画する。

```kotlin
data class Step3Content(
    val columns: List<ColumnInfo>,
    val error: String? = null,
    /** レコード ID 列の候補。空でなければ選択 UI を出す (Issue #66)。 */
    val recordIdCandidates: List<ColumnInfo> = emptyList(),
)
```

注意書きは**一意性の責任**を明示する。

```
選んだ列の値が一意であることは利用者の責任です。重複がある列を選ぶと、
更新・削除が意図しない行に及びます。
```

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 主キーの無いテーブル／ビュー | レコード ID 列を選んで連携できる | AC-1 |
| 候補列 | kintone の制約に合う型だけ | AC-2 |
| 主キーのあるテーブル | **変更なし**。選択 UI は出ない | AC-7 |
| 候補が 0 件のテーブル | 従来の「連携できない」表示 | AC-6 |
| 保存形式 | **変更なし** | C-2, D-4 |
| 一意性 | 検証しない。責任を画面で明示 | D-5, AC-3 |
| `docs/` | 影響なし | — |

### 受け入れるリスク

一意でない列を選べてしまう。**更新・削除が複数行に及ぶ可能性がある。**
検証コストとのトレードオフで、画面での明示に留める（D-5）。
将来、任意の検証機能を足す余地は残す。

---

## 5. テスト設計

### 5.1 `FieldTypeSuggesterTest`（追加）

- `BIGINT` / `INTEGER` / `SMALLINT` / `TINYINT` が候補になる
- `VARCHAR` / `CHAR` / `NVARCHAR` 等が候補になる
- `TIMESTAMP` / `DATE` / `BOOLEAN` / `BLOB` / `DECIMAL` / `DOUBLE` が候補にならない
- `canBeRecordId` が true の型で `suggestRecordIdType` が例外にならない（対応表の整合）

### 5.2 `RecordIdSelectionTest`（描画）

- 候補があれば選択 UI が描画される
- 候補の列名が選択肢に出る
- 一意性が利用者の責任である旨が表示される
- 候補が空なら選択 UI を描画しない
- 候補が空でエラーがあれば従来のエラー表示になる

### 5.3 実機確認

1. 主キーの無いビューで候補列の選択 UI が出る（AC-1, AC-2）
2. 列を選んで step4 に進み、`RecordIdType` が型どおりに決まる（AC-5）
3. 保存した `table_json` の `primary-key.jdbc-column` が選んだ列になる（AC-4）
4. `SalesLT.Customer`（主キーあり）は選択 UI が出ず従来どおり（AC-7）
5. 注意書きが出る（AC-3）
6. 検証データを片付け、`config.db` をバックアップと突き合わせる

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-2, D-3, §3.2 | §5.3-1, §5.3-2 |
| AC-2 | D-1, D-7, §3.1 | §5.1, §5.3-1 |
| AC-3 | D-5, §3.3 | §5.2, §5.3-5 |
| AC-4 | D-4 | §5.3-3 |
| AC-5 | §3.1 | §5.3-2 |
| AC-6 | D-6 | §5.2 |
| AC-7 | D-2 | §5.3-4 |
| AC-8 | §5.1, §5.2 | — |
| AC-9 | — | `./gradlew test detekt` |
