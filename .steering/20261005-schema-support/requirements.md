# スキーマの選択と保持 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | schema-support |
| 作成日 | 2026-10-05 |
| Issue | [#63](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/63) |
| 目的 | スキーマを選択・保持できるようにし、スキーマを持つデータソースで連携を作れるようにする |

---

## 1. 背景

### 1.1 課題

SQL Server の `Customer` テーブルで連携を追加すると step4 で 500 になり画面が真っ白になる。

```
java.lang.IllegalStateException: 主キーが定義されていません: Customer
```

`Customer` には主キー `CustomerID` が定義されている。スキーマを渡していないために
見つかっていない。

```
getPrimaryKeys(null, null,     "Customer")          → ❌ 0 件    ← 現在の実装
getPrimaryKeys(null, "SalesLT","Customer")          → ✅ CustomerID
getPrimaryKeys(null, null,     "SalesLT.Customer")  → ❌ 0 件
```

テーブルの実体は `AdventureWorksLT.SalesLT.Customer`。

### 1.2 原因

`listTables()` はスキーマを `TableInfo(schema, name)` に保持しているが、
**ウィザードが 3 箇所でスキーマを捨てている**。

```kotlin
// web/routes/TableWizardRoutes.kt
val tableName = tableLabel.substringAfter(".")   // step3 (58 行)
val tableName = tableLabel.substringAfter(".")   // step4 (76 行)
val dbTableName = tableLabel.substringAfter(".") // POST /syncs (149 行) ← 保存される値
```

メタデータ取得側もスキーマを受け取れない。

```kotlin
fun listColumns(tableName: String) = connection.metaData.getColumns(null, null, tableName, "%")
fun findPrimaryKey(tableName: String) = connection.metaData.getPrimaryKeys(null, null, tableName)
fun distinctValues(tableName: String, ...)
```

さらに**保存も実行時クエリもスキーマ無し**。`TableConfig` にスキーマを持つ場所がなく、
`QueryBuilder` は `quote(table.name)` で `SELECT ... FROM [Customer]` を発行する。

### 1.3 step4 の 500 に留まらない

- 同名テーブルが複数スキーマにある場合、**意図しないテーブルを参照する**危険がある
- 仮に step4 を通過できても、実行時クエリがスキーマ無しのため失敗しうる
- `SqlIdentifier.quote` は `SalesLT.Customer` を `[SalesLT.Customer]` と
  **1 つの識別子として**クォートするため、保存名にドットを含める方式は機能しない

### 1.4 SaaS コネクタでも起こり得る

スキーマが複数あるのは DB 系に限らない。SaaS コネクタでも複数スキーマを公開する
ものがあるため、「DB 系だけの問題」として扱わない。

## 2. 目的

スキーマを選択・保持できるようにし、スキーマを持つデータソースで連携を作成・実行できる
ようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | テーブル一覧（step2）にスキーマの選択／絞り込みを追加する |
| F-2 | step2 → step3 → step4 → `POST /syncs` でスキーマを引き回す（`substringAfter(".")` をやめる） |
| F-3 | `listColumns` / `findPrimaryKey` / `distinctValues` がスキーマを受け取り JDBC API に渡す |
| F-4 | `TableConfig` にスキーマを持たせる（省略可能） |
| F-5 | 実行時クエリで `[schema].[table]` に修飾する |
| F-6 | スキーマが無い／1 つだけのデータソースでは従来どおり動く |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| カタログ（データベース）の選択 | 接続文字列の `Database` で決まる。スキーマとは別の軸 |
| 主キーの無いテーブル／ビューへの対応 | kintone 側がレコード ID を要求するため成立しない |
| 失敗時に画面が真っ白になる問題 | [#64](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/64) で別途対応 |
| `agent.json` への影響 | Agent はテーブル名を知らない。影響なし |
| 既存連携へのスキーマ自動補完 | 推測で書き換えない。現状どおり動くことを優先する |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、SQL Server のテーブルで連携を作りたい。今は step4 で真っ白になり作成できないため |
| US-2 | 検証を進める担当者として、どのスキーマのテーブルかを選びたい。同名テーブルが複数スキーマにあると区別できないため |
| US-3 | 検証を進める担当者として、スキーマが 1 つしかないデータソースでは余計な選択をしたくない。SaaS コネクタの手順が増えるのは困るため |
| US-4 | このサンプルを引き継ぐ開発者として、既存の連携設定が壊れないでほしい。スキーマ無しで保存済みの連携が動かなくなるのは困るため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | SQL Server の `SalesLT.Customer` でウィザードが step4 まで進み、主キー `CustomerID` が検出される |
| AC-2 | テーブル一覧画面でスキーマを選択・絞り込みできる |
| AC-3 | スキーマが 1 つだけ、または無いデータソースでは選択 UI が出ない（または邪魔にならない） |
| AC-4 | 選択したスキーマが step3 / step4 / 保存まで引き継がれる |
| AC-5 | 保存された連携の実行時クエリがスキーマで修飾される |
| AC-6 | 同名テーブルが複数スキーマにある場合、選択したスキーマのテーブルが使われる |
| AC-7 | スキーマ無しの既存設定が従来どおり動く（後方互換） |
| AC-8 | スキーマ修飾の組み立てに単体テストがある |
| AC-9 | `./gradlew test` が通り、detekt がベースライン 84 件のままである |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | `TableConfig` へのフィールド追加は**省略可能**にする。`ignoreUnknownKeys` / `encodeDefaults = false` により既存 `table_json` がそのまま読めること |
| C-2 | `SqlIdentifier.quote` の挙動は変えない。識別子 1 つをクォートする責務のまま。修飾は別の関数で組む |
| C-3 | テーブル名にドットを含めて保存する方式は採らない（`quote` が 1 識別子として扱うため） |
| C-4 | スキーマは JDBC の `getTables` が返す `TABLE_SCHEM` を使う。接続文字列からの推測はしない |
| C-5 | ウィザードのステップ構成（step1〜step4）は変えない |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `metadata/JdbcMetadataInspector.kt` | `listColumns` / `findPrimaryKey` / `distinctValues` がスキーマを受け取る |
| `web/routes/TableWizardRoutes.kt` | スキーマを引き回す。`substringAfter(".")` を削除 |
| `web/views/TableWizardView.kt` | step2 にスキーマ選択。step3 / step4 で hidden として保持 |
| `config/Config.kt` | `TableConfig` にスキーマを追加（省略可能） |
| `jdbc/QueryBuilder.kt` | テーブル参照を修飾名にする |
| `jdbc/SqlIdentifier.kt` | 修飾名を組む関数を追加（`quote` 自体は変えない） |
| `docs/functional-design.md` | データモデルに `TableConfig.schema` を追記（基本設計の変更に該当） |
