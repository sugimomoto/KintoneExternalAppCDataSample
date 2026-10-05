# スキーマの選択と保持 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#63](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/63) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | スキーマを**独立した値として引き回す**（ラベルから切り出さない） | `substringAfter(".")` はスキーマ名やテーブル名にドットが含まれると壊れる。そもそも 2 つの値を 1 つの文字列に詰めたのが誤り | ラベルを `schema.table` のまま持ち回る案 → 分解が必要で同じ問題が残る |
| D-2 | `TableConfig` に **省略可能な `schema`** を追加する | 既存 `table_json`（スキーマ無し）をそのまま読める（C-1）。`encodeDefaults = false` なので保存内容も増えない | テーブル名にドットを含める案 → `quote` が 1 識別子として扱うため機能しない（C-3） |
| D-3 | 修飾名の組み立ては **`SqlIdentifier.qualified`** に切り出す | `quote` は「識別子 1 つをクォートする」責務のまま（C-2）。修飾は別の関心事 | `quote` にドット分割を入れる案 → 正規のテーブル名にドットが含まれる場合に壊れる |
| D-4 | `JdbcMetadataInspector` のスキーマ引数は **既定値 null** | 既存の呼び出し（テーブル一覧以外）を壊さずに段階導入できる | 全呼び出しを必須引数にする案 |
| D-5 | step2 のスキーマ選択は **クエリパラメータで絞り込む** | htmx や JS を増やさずに済む。step2 は既に DB へ 1 往復しているので追加コストが小さい | 全件を描画して JS で絞る案 → 100 件制限と相性が悪い |
| D-6 | スキーマが **0 または 1 種類なら選択 UI を出さない** | SaaS コネクタの手順を増やさない（AC-3） | 常に出す案 |
| D-7 | 既存連携への**スキーマ自動補完はしない** | どのスキーマだったか推測できない。現状どおり動くことを優先（スコープ外） | 起動時に補完する案 |

### 1.2 フロー

```
step2  GET /syncs/new/step2?connection=X[&schema=S]
         listTables() → TableInfo(schema, name) の一覧
         スキーマが 2 種類以上 → 選択 UI を出し、選択されていればそれで絞り込む
         テーブル選択の radio は value に「テーブル名のみ」を持ち、
         スキーマは hidden で別に送る

step3  GET /syncs/new/step3?connection=X&schema=S&table=T&configName=N
         listColumns(T, schema = S)

step4  POST /syncs/new/step4  (schema, table, configName, selectedColumns)
         findPrimaryKey(T, schema = S)   ← ここで主キーが見つかるようになる
         listColumns(T, schema = S)

保存   POST /syncs → TableConfig(name = T, schema = S, ...)

実行時 QueryBuilder → SELECT ... FROM [S].[T]
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/SqlIdentifier.kt` | 変更 | `qualified(schema, name)` を追加 |
| `config/Config.kt` | 変更 | `TableConfig.schema`（省略可能）と `qualifiedName()` |
| `jdbc/QueryBuilder.kt` | 変更 | テーブル参照を `table.qualifiedName()` に |
| `metadata/JdbcMetadataInspector.kt` | 変更 | `listColumns` / `findPrimaryKey` / `distinctValues` にスキーマ引数 |
| `web/routes/TableWizardRoutes.kt` | 変更 | スキーマを引き回す。`substringAfter(".")` を削除 |
| `web/views/TableWizardView.kt` | 変更 | step2 にスキーマ選択。step3 / step4 で hidden 保持 |
| `docs/functional-design.md` | 変更 | データモデルに `TableConfig.schema` を追記 |

---

## 3. 実装

### 3.1 修飾名

```kotlin
/**
 * スキーマで修飾した識別子を返す。[schema] が null / 空なら修飾しない。
 *
 * `[schema].[name]` の形にする。**[quote] にドット分割を任せない。**
 * `quote` は識別子 1 つをクォートする責務で、正規のテーブル名にドットが
 * 含まれる場合に壊れるため (Issue #63)。
 */
fun qualified(schema: String?, name: String): String =
    if (schema.isNullOrBlank()) quote(name) else "${quote(schema)}.${quote(name)}"
```

### 3.2 `TableConfig`

```kotlin
data class TableConfig(
    val name: String,
    /**
     * テーブルのスキーマ。スキーマを持たないデータソースでは null。
     *
     * 省略可能にしているのは、この項目が無い既存設定をそのまま読むため。
     * 名前にドットを含めて修飾する方式は採れない（[SqlIdentifier.quote] が
     * 1 つの識別子として扱う）ため、独立した項目として持つ (Issue #63)。
     */
    val schema: String? = null,
    ...
) {
    /** 実行時クエリで使う修飾済みテーブル名。 */
    fun qualifiedName(): String = SqlIdentifier.qualified(schema, name)
}
```

### 3.3 `QueryBuilder`

`quote(table.name)` を `table.qualifiedName()` に置き換える（5 箇所: select / insert / update / delete / count）。

### 3.4 `JdbcMetadataInspector`

```kotlin
fun listColumns(tableName: String, schema: String? = null): List<ColumnInfo> =
    connection.metaData.getColumns(null, schema, tableName, "%").use { ... }

fun findPrimaryKey(tableName: String, schema: String? = null): PrimaryKeyInfo? {
    connection.metaData.getPrimaryKeys(null, schema, tableName).use { ... }
    // カラム情報の解決にも同じスキーマを渡す
    val columnInfo = listColumns(tableName, schema).firstOrNull { ... }
}
```

`getColumns` / `getPrimaryKeys` は `schema = null` を「すべてのスキーマ」として扱うため、
既定値 null で従来の挙動を保つ。

### 3.5 step2 のスキーマ選択

```kotlin
val schemas = tables.mapNotNull { it.schema }.distinct().sorted()
if (schemas.size >= 2) {
    // スキーマが 1 種類以下なら出さない。SaaS コネクタの手順を増やさない。
    form(action = "/syncs/new/step2", method = FormMethod.get) {
        input(type = InputType.hidden, name = "connection") { value = connectionName }
        label {
            +"スキーマ: "
            select {
                name = "schema"
                option { value = ""; +"（すべて）" }
                schemas.forEach { s -> option { value = s; selected = (s == selectedSchema); +s } }
            }
        }
        button(type = ButtonType.submit, classes = "secondary outline") { +"絞り込む" }
    }
}
```

テーブル選択の radio は **テーブル名のみ**を value に持ち、スキーマは hidden で送る。
同名テーブルが複数スキーマにある場合に区別できるよう、絞り込み前は
`schema.name` を**表示**に使う（value とは分ける）。

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| SQL Server での連携作成 | step4 まで進み主キーが検出される | AC-1 |
| step2 | スキーマが 2 種類以上なら選択 UI が出る | AC-2, AC-3 |
| 保存される `table_json` | `schema` が入る（スキーマがある場合のみ） | AC-4 |
| 実行時クエリ | `[schema].[table]` に修飾される | AC-5 |
| スキーマ無しの既存連携 | `schema` が無い → `qualified` が修飾しない → 従来と同じ SQL | AC-7 |
| `agent.json` | **影響なし**（Agent はテーブル名を知らない） | — |
| `docs/functional-design.md` | データモデルの変更に該当するため追記 | CLAUDE.md の方針 |

---

## 5. テスト設計

### 5.1 `SqlIdentifierTest`（追加）

- スキーマがあれば `[schema].[name]` になる
- スキーマが null なら `[name]`（修飾しない）
- スキーマが空文字・空白なら修飾しない
- スキーマ名に `]` が含まれる場合もエスケープされる
- 既にクォート済みの名前を二重クォートしない

### 5.2 `QueryBuilderTest`（追加）

- `schema` を持つ `TableConfig` で `FROM [S].[T]` になる
- `schema` が無い `TableConfig` で従来どおり `FROM [T]`（後方互換）

### 5.3 `TableConfig` のデシリアライズ（追加）

- `schema` を持たない JSON が読める（後方互換、AC-7）
- `schema` を持つ JSON が読める

### 5.4 実機確認

1. SQL Server で step2 にスキーマ選択が出る（AC-2）
2. `SalesLT` を選んで `Customer` を選択し、step4 まで進んで `CustomerID` が検出される（AC-1, AC-4）
3. 保存した連携の `table_json` に `schema` が入る
4. Google Sheets など単一スキーマの接続で選択 UI が出ない（AC-3）
5. 既存のスキーマ無し連携が従来どおり表示・起動できる（AC-7）
6. 検証データを片付け、`config.db` をバックアップと突き合わせる

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1, §3.4 | §5.4-2 |
| AC-2 / AC-3 | D-5, D-6, §3.5 | §5.4-1, §5.4-4 |
| AC-4 | D-1, §1.2 | §5.4-2, §5.4-3 |
| AC-5 | D-2, D-3, §3.3 | §5.2 |
| AC-6 | D-1（スキーマを別に送る） | §5.4-2 |
| AC-7 | D-2（省略可能）, D-3 | §5.2, §5.3, §5.4-5 |
| AC-8 | §5.1, §5.2 | — |
| AC-9 | — | `./gradlew test detekt` |
