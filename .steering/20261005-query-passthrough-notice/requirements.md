# QueryPassthrough の注意書き — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | query-passthrough-notice |
| 作成日 | 2026-10-05 |
| Issue | [#69](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/69) |
| 目的 | `QueryPassthrough` が既定で有効なコネクタで、レコード参照が失敗する前に気づけるようにする |

---

## 1. 背景

### 1.1 起きたこと

SQL Server 接続で kintone からレコードを読むと Adapter がエラーを返した。

```
DATA_SOURCE The SQL Error Number is 102, State is 1, Severity is 15,
Error is 'Incorrect syntax near 'LIMIT'.', Server name is sqlserver-sample
```

### 1.2 原因

**`QueryPassthrough` の既定値が `true`。**

> This option passes the query to the SQL Server server as is.
> **Default Value: true**

素通しされると T-SQL の文法が適用され、`QueryBuilder` が生成する `LIMIT ? OFFSET ?` が
通らない。実測結果。

| `QueryPassthrough` | `LIMIT ? OFFSET ?` | `OFFSET/FETCH` |
|---|---|---|
| 既定（`true`） | ❌ `Incorrect syntax near 'LIMIT'` | ✅ |
| **`False`** | **✅**（OFFSET も正しく効く: `[1 2]`） | ✅ |

`QueryPassthrough=False` の設定で **kintone からデータを参照できることを実機で確認済み**。

### 1.3 SQL 生成側の変更は不要

`QueryBuilder` の `LIMIT ? OFFSET ?` は CData 共通方言として正しい。
`docs/extending.md` の記述も正しかった。

> CData JDBC Driver は SQL-92 に寄せた共通の方言を受け付けるため、通常は変更不要です。

### 1.4 課題

**利用者がこのプロパティに気づく手がかりが無い。** 接続テストは成功し、ウィザードも通り、
連携も作成できる。**kintone からレコードを読んだ時点で初めて失敗する**ため、原因から
遠い場所でエラーに遭遇する。

`QueryPassthrough` は 100 近いプロパティのうちの 1 つで、カテゴリー分類の中に埋もれている。

### 1.5 調査中の誤り（記録）

当初 `sys_connection_props` を `Name = 'QueryPassthrough'` で照会して「プロパティなし」と
誤判定した。`Name` 列は**表示名（`Query Passthrough`、スペース入り）**で、camelCase の
識別子は `PropertyName` 列にある。

```
Name=Query Passthrough   PropertyName=QueryPassthrough   Default=true
```

実装では `propertyName = rs.getString("PropertyName")` を使っているため、
**判定は `propertyName` で行う。**

## 2. 目的

`QueryPassthrough` が既定で有効なコネクタで、接続画面に注意書きを出して事前に気づける
ようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | `QueryPassthrough` の既定値が `true` かを `sys_connection_props` から判定する |
| F-2 | 接続文字列に明示設定が無い場合に限り注意書きを出す |
| F-3 | 注意書きに「`QueryPassthrough=False` にする」旨を含める |
| F-4 | 注意書きに「放置するとレコード参照時に失敗する」旨を含める |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| `QueryPassthrough=False` の自動付与 | **設定値の変更は利用者の運用に委ねる方針**（利用者の明示的な判断） |
| `QueryBuilder` の方言対応 | `QueryPassthrough=False` なら共通方言が通る。SQL 生成側の変更は不要 |
| 接続テストでの事前検出 | `LIMIT` を含むクエリを試せば検出できるが、接続テストの意味を変える。別途検討 |
| `distinctValues` の `LIMIT` | 同じ前提に依存するが、`QueryPassthrough=False` なら通る |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、レコード参照が失敗する前に設定の必要性を知りたい。接続テストもウィザードも通るため、連携を作り終えてから失敗すると手戻りが大きいため |
| US-2 | 検証を進める担当者として、何をどう設定すればよいか具体的に知りたい。100 近いプロパティから該当項目を探すのは手間なため |
| US-3 | 検証を進める担当者として、既に設定済みなら注意書きを出さないでほしい。対処済みの警告が出続けると他の警告を見落とすため |
| US-4 | 検証を進める担当者として、SaaS コネクタでは出さないでほしい。関係ない注意書きはノイズになるため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | `QueryPassthrough` の既定値が `true` のコネクタで接続画面に注意書きが出る |
| AC-2 | 注意書きに `QueryPassthrough=False` にする旨が含まれる |
| AC-3 | 注意書きに、放置するとレコード参照時に失敗する旨が含まれる |
| AC-4 | 接続文字列に `QueryPassthrough` が明示設定されている場合は出ない（値が `True` でも `False` でも） |
| AC-5 | `QueryPassthrough` を持たないコネクタ（SaaS 系）では出ない |
| AC-6 | 既定値が `false` のコネクタでは出ない |
| AC-7 | 判定ロジックに単体テストがある |
| AC-8 | `./gradlew test` が通り、detekt が 82 件以下である |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | 判定は `ConnectionProperty.propertyName` で行う。`displayName`（`Query Passthrough`）ではない |
| C-2 | 接続文字列の値を書き換えない。注意書きを出すだけ |
| C-3 | 判定は純粋関数として切り出し、DB もドライバーも要らずテストできること |
| C-4 | 既存の `warning-banner` を使う。新しいスタイルは追加しない |
| C-5 | 接続文字列を画面に出す場合は `ConnectionStringMasker` を通す（既存方針） |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `jdbc/QueryPassthroughAdvice.kt` | 新規。注意書きが必要かの判定 |
| `web/views/ConnectionsView.kt` | 接続フォームに注意書きを追加 |
| `jdbc/JdbcUrlEnhancer.kt` | 接続文字列からプロパティ値を読む関数を追加 |
| `jdbc/QueryBuilder.kt` | **変更なし** |
| `docs/` | 永続的ドキュメントへの影響なし |
