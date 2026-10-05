# QueryPassthrough の注意書き — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#69](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/69) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **値を自動で書き換えない** | 設定値の変更は利用者の運用に委ねる（利用者の明示的な判断）。接続設定を勝手に変えると、意図して `True` を選んだ場合に壊す | `QueryPassthrough=False` を自動付与する案 |
| D-2 | 判定を **`QueryPassthroughAdvice` の純粋関数**にする | DB もドライバーも要らずテストできる（C-3）。接続画面と将来の他経路で同じ判定を使える | ビューにインラインで書く案 |
| D-3 | 判定は **`propertyName` で行う** | `Name` 列は表示名（`Query Passthrough`、スペース入り）。実装は `propertyName = rs.getString("PropertyName")` を使っている（C-1） | `displayName` で引く案 → 一致せず検出できない |
| D-4 | 接続文字列に**明示設定があれば出さない** | 対処済みの警告を出し続けると他の警告を見落とす。`True` を選んだ人にも不要（AC-4） | 既定値だけ見る案 |
| D-5 | 既定値が `true` の場合に限る | `QueryPassthrough` を持たないコネクタ（SaaS 系）や既定が `false` のコネクタではノイズ（AC-5, AC-6） | 常に出す案 |
| D-6 | 注意書きは**放置した結果まで書く** | 「設定してください」だけでは重要度が伝わらない。接続テストもウィザードも通り、**kintone からレコードを読んだ時点で初めて失敗する**ため | 設定方法だけ書く案 |
| D-7 | 接続文字列の読み取りに **`JdbcUrlEnhancer.propertyOf`** を追加する | `withProperty` が持つ正規表現と同じ概念。区切りが `;` だけでなく `:` も来る（CData の接続文字列は `jdbc:<product>:<最初のプロパティ>=...`） | 独自に正規表現を書く案 → 3 箇所目の重複になる |

### 1.1 実測の根拠

```
Name=Query Passthrough   PropertyName=QueryPassthrough   Default=true
```

| `QueryPassthrough` | `LIMIT ? OFFSET ?` |
|---|---|
| 既定（`true`） | ❌ `Incorrect syntax near 'LIMIT'` |
| `False` | ✅（OFFSET も正しく効く） |

`QueryPassthrough=False` で **kintone からデータを参照できることを実機で確認済み**。

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/JdbcUrlEnhancer.kt` | 変更 | `propertyOf(jdbcUrl, name)` を追加 |
| `jdbc/QueryPassthroughAdvice.kt` | 新規 | 注意書きの必要性判定とメッセージ |
| `web/views/ConnectionsView.kt` | 変更 | 接続フォームに注意書きを描画 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/QueryPassthroughAdviceTest.kt` | 既存（退避済み） | 判定（既定値・明示設定・プロパティ無し・`displayName`） |
| `jdbc/JdbcUrlEnhancerTest.kt` | 変更 | `propertyOf` の読み取り |

---

## 3. 実装

### 3.1 接続文字列の読み取り

```kotlin
/**
 * 接続文字列からプロパティ値を取り出す。明示されていなければ null。
 *
 * 区切りは `;` だけでなく `:` も見る。CData の接続文字列は
 * `jdbc:<product>:<最初のプロパティ>=...` の形。
 */
fun propertyOf(jdbcUrl: String, name: String): String? =
    Regex("""(?i)(?:^|[;:])\s*${Regex.escape(name)}\s*=\s*([^;]*)""")
        .find(jdbcUrl)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
```

### 3.2 判定

```kotlin
object QueryPassthroughAdvice {

    const val PROPERTY = "QueryPassthrough"

    val MESSAGE = "このデータソースは $PROPERTY が既定で有効です。" +
        "クエリがそのまま渡されるため、レコードの参照時に " +
        "'Incorrect syntax near 'LIMIT'' のようなエラーになります。" +
        "接続テストやウィザードは成功するため、kintone からレコードを読んだ時点で" +
        "初めて失敗します。$PROPERTY を False に設定してください。"

    /**
     * 注意書きが必要か。
     *
     * 既定値が `true` で、かつ接続文字列に明示設定が無い場合だけ true。
     * 判定は `propertyName` で行う（`displayName` は `Query Passthrough`）。
     */
    fun isNeeded(properties: List<ConnectionProperty>, jdbcUrl: String): Boolean {
        val defaultsToTrue = properties
            .firstOrNull { it.propertyName.equals(PROPERTY, ignoreCase = true) }
            ?.defaultValue
            ?.equals("true", ignoreCase = true) == true
        if (!defaultsToTrue) return false
        return JdbcUrlEnhancer.propertyOf(jdbcUrl, PROPERTY) == null
    }
}
```

### 3.3 ビュー

接続フォームのプロパティ欄の前に `warning-banner` で出す。`propertiesFormContent` は
接続文字列を知らないため、`connectionFormView` 側で判定して描画する。

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| SQL Server 等の接続画面 | 注意書きが出る | AC-1, AC-2, AC-3 |
| 明示設定済みの接続 | 出ない | AC-4 |
| SaaS コネクタ | 出ない（プロパティを持たない） | AC-5 |
| 接続文字列 | **書き換えない** | D-1, C-2 |
| `QueryBuilder` | **変更なし** | — |
| `docs/` | 影響なし | — |

---

## 5. テスト設計

### 5.1 `QueryPassthroughAdviceTest`（退避済み・9 件）

既定が true で未設定 / `False` 設定済み / `True` 設定済み / 大文字小文字 /
最初のプロパティ / プロパティ無し / 既定が false / 既定値なし / `displayName` で判定しない /
メッセージの内容

### 5.2 `JdbcUrlEnhancerTest`（追加）

- プロパティ値を読める
- 無ければ null
- 区切りが `:`（最初のプロパティ）でも読める
- 大文字小文字を無視する
- 空値は null

### 5.3 実機確認

1. SQL Server の接続編集画面に注意書きが出ないこと（現在 `QueryPassthrough=False` 設定済みのため）
2. 明示設定を外した接続で注意書きが出ること
3. Google Sheets では出ないこと

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 / AC-3 | D-5, D-6, §3.2 | §5.1, §5.3-2 |
| AC-4 | D-4, D-7 | §5.1, §5.3-1 |
| AC-5 / AC-6 | D-5 | §5.1, §5.3-3 |
| AC-7 | D-2, §5.1 | — |
| AC-8 | — | `./gradlew test detekt` |
