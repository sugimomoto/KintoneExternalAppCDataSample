# DATETIME の束縛を文字列に統一する — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | datetime-binding |
| 作成日 | 2026-10-05 |
| Issue | [#71](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/71) |
| 目的 | SQL Server で DATETIME の更新と日時フィルタが失敗する問題を解消する |

---

## 1. 背景

### 1.1 課題

kintone からレコードを更新すると失敗する。

```
DATA_SOURCE The SQL Error Number is 241, State is 1, Severity is 16,
Error is 'Conversion failed when converting date and/or time from character string.'
```

### 1.2 原因

**CData の SQL Server ドライバーは `java.sql.Timestamp` のパラメータを
SQL Server が解釈できない文字列に変換する。** 実測結果。

| 経路 | `Timestamp` 束縛 | `String` 束縛 |
|---|---|---|
| `UPDATE SET [ModifiedDate] = ?` | ❌ エラー 241 | ✅ 1 行 |
| `SELECT ... WHERE [ModifiedDate] > ?` | ❌ エラー 241 | ✅ 847 行 |
| SQL にリテラル `'2026-10-05 12:00:00'` | — | ✅ |

`setObject(Timestamp)` / `setTimestamp` / `setObject(Timestamp, Types.TIMESTAMP)` の
いずれも失敗する。`QueryPassthrough` の設定とは無関係（`True` / `False` 両方で失敗）。

### 1.3 影響は更新だけではない

`java.sql.Timestamp` を作っている箇所は 2 つある。

| 箇所 | 用途 | SQL Server での現状 |
|---|---|---|
| `RowMapper.fieldToValue` | Insert / Update の値 | 失敗 |
| `FilterTranslator.timestampToSql` | Select / Count の日時フィルタ | 失敗 |

**kintone 側で日時の絞り込みを使っても同じエラーになる。**

### 1.4 SaaS コネクタでの検証ができなかった

現在 SaaS の日時フィルタは `Timestamp` 束縛で動いている。文字列に変えると
動いているものに手を入れることになるが、この環境では検証できなかった。

| コネクタ | 検証できない理由 |
|---|---|
| Google Sheets | テーブル名（`CRM Data sugimotok_Opportunity`）のレンジ解析で別原因の失敗。3 通りとも同じ結果で判別不能 |
| BCart | コンテナ内で未認証 |
| Salesforce | DATETIME 列（`CreatedDate` / `LastModifiedDate` / `SystemModstamp`）がすべて読み取り専用 |

**利用者の判断で A（全データソースで文字列束縛に統一）を選択した。** CData の
SQL エンジンは日時リテラルを `'yyyy-MM-dd HH:mm:ss'` 形式で受けるのが本来の形で、
文字列束縛はその形に揃えることになる。

## 2. 目的

DATETIME の束縛を文字列に統一し、SQL Server で更新と日時フィルタが動くようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | protobuf `Timestamp` → SQL 用文字列の変換を共有の関数にする |
| F-2 | `RowMapper.fieldToValue`（Insert / Update）が文字列を返す |
| F-3 | `FilterTranslator.timestampToSql`（Select / Count）が文字列を返す |
| F-4 | ミリ秒を持つ値で精度を黙って落とさない |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| `bindParams` の型別束縛 | 文字列に統一すれば `setObject(String)` で足りる。null 束縛は型指定が望ましいが別の関心事 |
| Google Sheets のテーブル名レンジ解析エラー | 本件とは別原因。空白を含むテーブル名の扱いの問題 |
| ドライバーごとの分岐 | 300+ データソースの分岐は破綻する（#60 / #63 と同じ判断） |
| タイムゾーンの扱いの見直し | 現状の挙動（JVM 既定タイムゾーンで描画）を維持する。変えると既存連携の値がずれる |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、kintone からレコードを更新したい。日時列を含む更新が失敗すると、ほぼすべての更新が通らないため |
| US-2 | 検証を進める担当者として、日時で絞り込みたい。kintone の一覧で日時フィルタを使うと失敗するため |
| US-3 | このサンプルを引き継ぐ開発者として、既存の SaaS 連携の日時の値がずれないでほしい。タイムゾーンの解釈が変わると過去のデータと不整合になるため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | `modified_date`（DATETIME）を含む Update が成功する |
| AC-2 | SQL Server で日時フィルタを含む Select が成功する |
| AC-3 | Insert でも DATETIME が正しく束縛される |
| AC-4 | 文字列・数値フィールドの更新が従来どおり成功する |
| AC-5 | ミリ秒を持つ値で精度が落ちない |
| AC-6 | タイムゾーンの解釈が従来と同じ（JVM 既定タイムゾーン） |
| AC-7 | 変換ロジックに単体テストがある（タイムゾーンに依存しない形で） |
| AC-8 | `./gradlew test` が通り、detekt が 78 件以下である |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | 変換は 1 箇所に集約する。`RowMapper` と `FilterTranslator` で別々に書かない |
| C-2 | タイムゾーンは引数で受け取り、既定を `ZoneId.systemDefault()` にする。テストが実行環境に依存しないこと |
| C-3 | 既存の日時の解釈（JVM 既定タイムゾーンでの描画）を変えない |
| C-4 | ミリ秒が 0 の場合に不要な `.000` を付けない（SQL の見た目を従来に近く保つ） |
| C-5 | `FilterTranslator` の `WhereClause` の構造は変えない |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `jdbc/SqlDateTime.kt` | 新規。protobuf `Timestamp` → SQL 用文字列 |
| `jdbc/RowMapper.kt` | DATETIME の値が文字列になる |
| `filter/FilterTranslator.kt` | 日時フィルタのパラメータが文字列になる |
| `service/AdapterServiceImpl.kt` | **変更なし**（`setObject(String)` で足りる） |
| `docs/` | 永続的ドキュメントへの影響なし |
