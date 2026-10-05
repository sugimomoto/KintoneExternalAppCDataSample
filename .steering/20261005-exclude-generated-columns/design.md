# 自動生成列の除外 — 設計

| 項目 | 内容 |
|---|---|
| 要求定義 | [requirements.md](requirements.md) |
| Issue | [#75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75) |

---

## 1. 方針

**JDBC が申告する情報だけで判定し、判定ロジックを純粋関数に閉じる。**

`DatabaseMetaData.getColumns` は JDBC 仕様として既定値・自動採番・計算列を返す。
ドライバーごとの分岐を書かずに済む（C-1）。

```
JdbcMetadataInspector.listColumns
  → ColumnInfo(..., defaultValue, autoIncrement, generated)   ← F-1
        ↓
GeneratedColumns.partition(columns)                            ← F-2（純粋関数）
  → Partition(selectable, excluded: List<ExcludedColumn>)
        ↓
Step3Content(columns = selectable, excludedColumns = excluded) ← F-3
```

主キー検出（`findPrimaryKey`）とレコード ID 列の候補はこの関数を通さない（C-2 / F-4 / F-5）。

## 2. 判定ルール

| 条件 | 理由コード | 画面表示 |
|---|---|---|
| `IS_AUTOINCREMENT = YES` | `AUTO_INCREMENT` | 自動採番列 |
| `IS_GENERATEDCOLUMN = YES` | `GENERATED` | 計算列 |
| `COLUMN_DEF` が非 null・非空 | `DEFAULT_VALUE` | 既定値あり（式を併記） |

優先順位は上から。自動採番列は既定値も持ち得るため、より具体的な理由を出す。

### 既定値を持つだけの列も除外する理由

一見「既定値があるだけで自動生成ではない」列（`NameStyle` の `((0))` 等）も除外する。
**`NOT NULL` かつ既定値を持つ列は、kintone から空で送られると必ず失敗する。**

| 送る内容 | 結果 |
|---|---|
| 空文字（テキスト型） | `Conversion failed when converting ... to uniqueidentifier` |
| `NULL`（数値・日時型、`hasValue()` が false） | `SQL Error Number is 515`（NULL を挿入できない） |
| **列自体を送らない** | ✅ 既定値が効く |

既定値を持つ列は**省いても必ず DB 側が埋める**ため、省くことが常に安全。
式の形（関数呼び出しかリテラルか）で切り分ける案は採らない。`(newid())` と `((0))` を
文字列解析で区別するのはドライバー依存の脆い判定になる割に、得られるのは
「リテラル既定値の列を kintone から設定できる」という小さな利益しかない。

### 除外の代償を見えるようにする

既定値を持つ列は kintone から**設定できなくなる**。黙って消すと「列が足りない」と
誤解されるため、除外した列を別表に出して理由と既定値の式を示す（F-3 / AC-3）。
SaaS 系ドライバーが想定外に既定値を申告した場合も、この表で気づける（AC-7 の担保）。

実測では Salesforce ドライバーが**書き込めない項目**（`Id` / `CreatedDate` / `OwnerId` 等）を
`IS_GENERATEDCOLUMN = YES` で申告する。これらは元々マッピングしても登録・更新で
失敗していた列なので、除外は改善になる。詳細は requirements.md §8。

## 3. 変更内容

### 3.1 `metadata/JdbcMetadataInspector.kt`

`ColumnInfo` に 3 つのフィールドを**既定値付きで**追加する（C-4）。
既存の呼び出しは名前付き引数なのでそのまま通る。

```kotlin
data class ColumnInfo(
    val name: String,
    val jdbcType: Int,
    val typeName: String,
    val nullable: Boolean,
    /** 既定値の式。`(newid())` 等。無ければ null。 */
    val defaultValue: String? = null,
    /** 自動採番列か（`IS_AUTOINCREMENT`）。 */
    val autoIncrement: Boolean = false,
    /** 計算列か（`IS_GENERATEDCOLUMN`）。 */
    val generated: Boolean = false,
)
```

`listColumns` の読み取りは**存在する列だけ**を読む。`IS_AUTOINCREMENT` /
`IS_GENERATEDCOLUMN` は JDBC 4.0 / 4.1 で追加されたもので、返さないドライバーがある。
`rs.metaData` から 1 度だけラベル集合を作り、無い列は既定値のままにする。

```kotlin
val labels = (1..rs.metaData.columnCount).map { rs.metaData.getColumnLabel(it).uppercase() }.toSet()
...
defaultValue = if ("COLUMN_DEF" in labels) rs.getString("COLUMN_DEF") else null,
autoIncrement = "YES".equals(labels.yesOrNull(rs, "IS_AUTOINCREMENT"), ignoreCase = true),
```

### 3.2 `metadata/GeneratedColumns.kt`（新規）

判定を純粋関数として切り出す（C-3）。DB 無しでテストできる。

```kotlin
/** 除外の理由。 */
enum class ExclusionReason(val label: String) {
    AUTO_INCREMENT("自動採番列"),
    GENERATED("計算列"),
    DEFAULT_VALUE("既定値あり"),
}

/** 除外した列と理由。 */
data class ExcludedColumn(val column: ColumnInfo, val reason: ExclusionReason)

/** 選択できる列と除外した列。 */
data class ColumnPartition(
    val selectable: List<ColumnInfo>,
    val excluded: List<ExcludedColumn>,
)

object GeneratedColumns {
    /** 自動生成列なら理由を返す。通常の列なら null。 */
    fun reasonFor(column: ColumnInfo): ExclusionReason? = when {
        column.autoIncrement -> ExclusionReason.AUTO_INCREMENT
        column.generated -> ExclusionReason.GENERATED
        !column.defaultValue.isNullOrBlank() -> ExclusionReason.DEFAULT_VALUE
        else -> null
    }

    /** 選択候補と除外列に分ける。入力の順序は保つ。 */
    fun partition(columns: List<ColumnInfo>): ColumnPartition
}
```

### 3.3 `web/views/TableWizardView.kt`

`Step3Content` に除外列を追加する。

```kotlin
data class Step3Content(
    val columns: List<ColumnInfo>,
    val error: String? = null,
    val recordIdCandidates: List<ColumnInfo> = emptyList(),
    /** 自動生成列として除外した列。空なら何も描画しない (Issue #75)。 */
    val excludedColumns: List<ExcludedColumn> = emptyList(),
)
```

除外列の表示を `excludedColumnsNotice` として切り出す。`wizardStep3View` は既に
長いため、インラインで足すと detekt の `LongMethod` に触れる（過去に同じ指摘を受けた）。

```kotlin
fun FlowContent.excludedColumnsNotice(excluded: List<ExcludedColumn>) {
    if (excluded.isEmpty()) return
    article(classes = "warning-banner") { ... 表 ... }
}
```

表示内容：

```
以下の 4 列は DB 側で自動的に値が決まるため、マッピング対象から外しました。
kintone の入力項目として表示すると、空のまま登録しようとして失敗します。

| 列          | 型               | 理由            |
|-------------|------------------|-----------------|
| CustomerID  | int identity     | 自動採番列      |
| rowguid     | uniqueidentifier | 既定値あり (newid()) |
| ModifiedDate| TIMESTAMP        | 既定値あり (getdate()) |
| NameStyle   | NameStyle        | 既定値あり ((0)) |
```

### 3.4 `web/routes/TableWizardRoutes.kt`

step3 の表示と、step4 でのマッピング生成の両方に除外を適用する。

| 箇所 | 変更 |
|---|---|
| `get("/syncs/new/step3")` | `GeneratedColumns.partition` の結果を `Step3Content` に渡す |
| `computeStep4` の `maps` | 選択候補に残った列だけをマッピングする |
| `computeStep4` の `idColumn` 解決 | **変更しない**（`allColumns` のまま。F-4 / F-5） |
| `Step4Result.NoPrimaryKey` の候補 | **変更しない**（`allColumns` から型で絞るまま） |

step4 側でも除外するのは、step3 の画面を経由せず直接 POST された場合に
自動生成列が混ざるのを防ぐため。画面の表示だけを変えても保存内容は守れない。

## 4. 影響範囲

| ファイル | 変更 |
|---|---|
| `metadata/JdbcMetadataInspector.kt` | `ColumnInfo` に 3 フィールド追加、`listColumns` で読む |
| `metadata/GeneratedColumns.kt` | 新規 |
| `web/views/TableWizardView.kt` | `Step3Content.excludedColumns`、`excludedColumnsNotice` |
| `web/routes/TableWizardRoutes.kt` | step3 / step4 で `partition` を適用 |
| `config/`・`service/`・`jdbc/` | 変更なし |
| `docs/` | 変更なし（ウィザードの候補絞り込みは実装詳細） |

## 5. テスト

| テスト | 内容 |
|---|---|
| `GeneratedColumnsTest` | 自動採番 → `AUTO_INCREMENT` |
| | 計算列 → `GENERATED` |
| | 既定値あり → `DEFAULT_VALUE` |
| | 自動採番かつ既定値 → `AUTO_INCREMENT`（優先順位） |
| | 既定値が空文字 → 除外しない |
| | 通常の列 → `null` |
| | `partition` が入力順を保つ |
| | `partition` が全列通常なら `excluded` が空（AC-7） |
| `JdbcMetadataInspectorTest` | H2 で `GENERATED BY DEFAULT AS IDENTITY` 列が `autoIncrement = true` |
| | H2 で `DEFAULT 0` 列の `defaultValue` が非 null |
| | 既存の主キー検出テストが通る（AC-4） |
| `Step3ExcludedColumnsTest` | 除外列があると列名・理由が HTML に出る |
| | 除外列が空なら何も出ない |
