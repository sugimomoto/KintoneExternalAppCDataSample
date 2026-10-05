# DATETIME の束縛を文字列に統一する — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#71](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/71) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **DATETIME を書式付き文字列で束縛する** | `setObject` / `setTimestamp` / `setObject(_, Types.TIMESTAMP)` のいずれも SQL Server で失敗し、文字列なら通る（実測）。CData の SQL エンジンが日時リテラルとして受ける形式に揃える | 束縛方法を変える案 → 3 通りすべて失敗 |
| D-2 | 変換を **`SqlDateTime` に集約**する | `RowMapper`（書き込み）と `FilterTranslator`（フィルタ）の両方が同じ問題を踏む。2 箇所に書くと片方だけ直す事故が起きる（C-1） | 各所で変換する案 |
| D-3 | **タイムゾーンを引数で受け取る**（既定 `ZoneId.systemDefault()`） | 従来 `java.sql.Timestamp` が JVM 既定タイムゾーンで描画されていた。既存連携の値がずれないよう揃える（C-3）。引数化はテストを実行環境に依存させないため（C-2） | 固定で UTC にする案 → 既存連携の値が 9 時間ずれる |
| D-4 | ミリ秒が 0 なら**小数部を付けない** | SQL の見た目を従来に近く保つ（C-4）。ログの比較もしやすい | 常に `.SSS` を付ける案 |
| D-5 | ミリ秒未満は**切り捨てる** | SQL Server の `datetime` は約 3.33ms 精度で、ナノ秒は保持できない | ナノ秒まで出す案 → ドライバーが受けない可能性 |
| D-6 | `bindParams` は**変更しない** | 文字列に統一すれば `setObject(String)` で足りる | 型別束縛を入れる案 → 本件の原因ではない |

### 1.2 実測の根拠

| 経路 | `Timestamp` 束縛 | `String` 束縛 |
|---|---|---|
| `UPDATE SET [ModifiedDate] = ?` | ❌ エラー 241 | ✅ 1 行 |
| `SELECT ... WHERE [ModifiedDate] > ?` | ❌ エラー 241 | ✅ 847 行 |

`QueryPassthrough` の設定には依存しない（`True` / `False` 両方で失敗）。

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/SqlDateTime.kt` | 新規 | protobuf `Timestamp` → SQL 用文字列 |
| `jdbc/RowMapper.kt` | 変更 | DATETIME の値を文字列にする |
| `filter/FilterTranslator.kt` | 変更 | 日時フィルタのパラメータを文字列にする |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/SqlDateTimeTest.kt` | 新規 | 書式・タイムゾーン・ミリ秒 |
| `jdbc/RowMapperTest.kt` | 変更 | 期待値を文字列に |
| `filter/FilterTranslatorTest.kt` | 変更 | 期待値を文字列に |

---

## 3. 実装

```kotlin
object SqlDateTime {
    private val SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun format(ts: Timestamp, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()).atZone(zone)
        return (if (at.nano / NANOS_PER_MILLI == 0) SECONDS else MILLIS).format(at)
    }
}
```

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| SQL Server の Update / Insert | 成功するようになる | AC-1, AC-3 |
| SQL Server の日時フィルタ | 成功するようになる | AC-2 |
| SaaS コネクタの日時フィルタ | **パラメータの型が変わる（未検証）** | §5 のリスク参照 |
| タイムゾーンの解釈 | **変更なし**（JVM 既定） | AC-6 |
| `bindParams` | **変更なし** | D-6 |

### 受け入れたリスク

**SaaS コネクタでの日時の書き込み・フィルタは検証できなかった。** 現在 `Timestamp` 束縛で
動いているものを文字列に変えるため、回帰の可能性が残る。検証できない理由は
requirements §1.4 のとおり（Google Sheets はテーブル名の別原因、BCart は未認証、
Salesforce は DATETIME 列が読み取り専用）。

利用者の判断でこの方針を選択した。回帰が出た場合はドライバーごとの分岐が必要になるが、
300+ データソースの分岐は破綻するため、別の設計（ドライバーが申告する日時リテラル形式を
使う等）を検討することになる。

---

## 5. テスト設計

### 5.1 `SqlDateTimeTest`

- エポックを UTC で描画する
- タイムゾーンに応じて描画が変わる（既存挙動の維持）
- 秒まで描画する
- ミリ秒を持つ値で精度を落とさない
- ミリ秒が 0 なら小数部を付けない
- ミリ秒未満は切り捨てる
- エポック以前の値も描画できる
- 既定のタイムゾーンが JVM 既定である

### 5.2 実機確認

1. `modified_date` を含む Update が成功し、値が書き込まれる（AC-1）
2. 書き込まれた値のタイムゾーンが従来どおり（AC-6）
3. 日時フィルタを含む Select が成功する（AC-2）
4. Insert で DATETIME が通る（AC-3）
5. 文字列フィールドの更新が従来どおり（AC-4）
6. 検証データを片付け、元の値に復元する

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-3 | D-1, D-2 | §5.2-1, §5.2-4 |
| AC-2 | D-2（`FilterTranslator` も通す） | §5.2-3 |
| AC-4 | D-6（他の型は変えない） | §5.2-5 |
| AC-5 | D-4, D-5 | §5.1 |
| AC-6 | D-3 | §5.2-2 |
| AC-7 | D-3（引数化）, §5.1 | — |
| AC-8 | — | `./gradlew test detekt` |
