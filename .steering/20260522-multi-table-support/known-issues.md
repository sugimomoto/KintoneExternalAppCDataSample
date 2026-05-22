# 既知の課題（フェーズ2-A 検証で発見、次フェーズ送り）

フェーズ2-A の実装スコープを越える、または別タスクで扱うのが妥当な課題を記録する。
各課題は次フェーズ着手時にチケット化して優先度判定する。

---

## ISSUE-001: QueryBuilder がテーブル名・カラム名を識別子クォートしていない ✅ 解決済 (2026-05-22)

| 項目 | 内容 |
|---|---|
| 発見日 | 2026-05-22 |
| 解決日 | 2026-05-22 |
| 解決コミット | tag `v0.2.1-quoted-identifiers` |
| 重要度 | 🟡 中（特定データソースで顕在化） |
| 関連ファイル | [src/main/kotlin/com/cdata/kintone/adapter/jdbc/QueryBuilder.kt](../../src/main/kotlin/com/cdata/kintone/adapter/jdbc/QueryBuilder.kt), [SqlIdentifier.kt](../../src/main/kotlin/com/cdata/kintone/adapter/jdbc/SqlIdentifier.kt) |

### 事象

CData Google Sheets Driver で、シート名にスペースを含むテーブル（例: `CRM Data sugimotok_Opportunity`）を Select すると次のエラーが返る：

```
SQL 不正な形式のSQLステートメント：予想外のトークンが見つかりました：[sugimotok_Opportunity]。
ステートメント：SELECT Id, Id1, Name, ... FROM CRM Data sugimotok_Opportunity WHERE 1=1 LIMIT ? OFFSET ?
```

QueryBuilder が `FROM ${tableName}` のように識別子を裸で埋め込んでいるため、空白で SQL パーサが切れる。

### 解決方法

[SqlIdentifier.kt](../../src/main/kotlin/com/cdata/kintone/adapter/jdbc/SqlIdentifier.kt) を新規追加。
QueryBuilder / FilterTranslator が出力するすべてのテーブル名・カラム名を `[name]` で
自動クォートするよう変更した。冪等性 (`[foo]` を二重クォートしない) を持たせたため、
ユーザが手動でブラケットを書いていた既存 yaml ともそのまま互換。

旧暫定回避 (`name: "[CRM Data sugimotok_Opportunity]"`) は不要になり、シンプルに
`name: "CRM Data sugimotok_Opportunity"` で動作することを実環境で確認済み。

### 実装内容（参考）

- `SqlIdentifier.quote(name)`: 識別子を `[name]` でラップ。`]` は `]]` でエスケープ。既に `[...]` 形式なら素通し（冪等）
- QueryBuilder: SELECT/INSERT/UPDATE/DELETE/COUNT 全てでテーブル名・カラム名をクォート
- FilterTranslator: 24 箇所の `table.toJdbcColumn(...)` 呼び出しをヘルパ `col()` 経由でクォート
- AdapterServiceImplTest: H2 を `MODE=MSSQLServer` で起動し、DDL もブラケットでクォートして互換性確保

### テスト追加件数

- QueryBuilderTest: 既存 12 → 20 件（+8 ISSUE-001 用）
- 全体: 181 → 189 件（フェーズ2-A 完了時 181 + ISSUE-001 +8）
- すべて緑

### 実環境動作確認

- Salesforce default テーブル (`config/server.yaml` 直下 / port 8083): Select 動作 OK
- Google Sheets `gs-opportunity` テーブル (`config/tables/gs-opportunity` / port 18003): Select 動作 OK
- スペース入りシート名 `CRM Data sugimotok_Opportunity` を `name:` にそのまま書いて動作

---

## ISSUE-002: CData の `Spreadsheet` vs `SpreadsheetId` パラメータの違い

| 項目 | 内容 |
|---|---|
| 発見日 | 2026-05-22 |
| 発見契機 | Google Sheets 初回接続時 |
| 重要度 | 🟢 低（ドキュメント整備で解消） |

### 事象

`config/jdbc/googlesheets.yaml.example` を当初 `Spreadsheet=${GS_SPREADSHEET_ID}` で記述していたが、CData ドライバは `Spreadsheet` を「**名前**」として扱うため、ID を渡しても該当シートが見つからず実シートが取得できなかった。

正しくは `SpreadsheetId=...` を使う必要がある。

### 対応済み

`config/jdbc/googlesheets.yaml.example` を `SpreadsheetId` 使用に修正。

### 推奨対応（恒久）

- CData 個別ドライバの「ID 指定パラメータ」一覧をドキュメント化（例: Salesforce は接続URL内に ID 不要、Google Sheets は `SpreadsheetId`、SharePoint は `URL` 等）
- `init-table` で `--jdbc-config` を読み込み時に、URL内のキー（`Spreadsheet` を含む等）を検証し警告を出す案
