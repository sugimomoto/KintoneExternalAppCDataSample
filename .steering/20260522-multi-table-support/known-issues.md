# 既知の課題（フェーズ2-A 検証で発見、次フェーズ送り）

フェーズ2-A の実装スコープを越える、または別タスクで扱うのが妥当な課題を記録する。
各課題は次フェーズ着手時にチケット化して優先度判定する。

---

## ISSUE-001: QueryBuilder がテーブル名・カラム名を識別子クォートしていない

| 項目 | 内容 |
|---|---|
| 発見日 | 2026-05-22 |
| 発見契機 | フェーズ2-A E2E、Google Sheets 統合中 |
| 重要度 | 🟡 中（特定データソースで顕在化） |
| 関連ファイル | [src/main/kotlin/com/cdata/kintone/adapter/jdbc/QueryBuilder.kt](../../src/main/kotlin/com/cdata/kintone/adapter/jdbc/QueryBuilder.kt) |

### 事象

CData Google Sheets Driver で、シート名にスペースを含むテーブル（例: `CRM Data sugimotok_Opportunity`）を Select すると次のエラーが返る：

```
SQL 不正な形式のSQLステートメント：予想外のトークンが見つかりました：[sugimotok_Opportunity]。
ステートメント：SELECT Id, Id1, Name, ... FROM CRM Data sugimotok_Opportunity WHERE 1=1 LIMIT ? OFFSET ?
```

QueryBuilder が `FROM ${tableName}` のように識別子を裸で埋め込んでいるため、空白で SQL パーサが切れる。

### 暫定回避

`config/tables/<name>/table.yaml` の `name:` フィールドに、ユーザがブラケット込みで書く：

```yaml
name: "[CRM Data sugimotok_Opportunity]"
```

これは Adapter 設定の責務外なので根本対策ではない。

### 推奨対応

QueryBuilder（および関連する SQL 生成箇所）で、識別子を **常に CData 標準のブラケット（`[...]`） or ANSI 標準のダブルクォート（`"..."`）でクォート** する。

- カラム名: `SELECT [Id], [Name] FROM ...`
- テーブル名: `FROM [CRM Data sugimotok_Opportunity]`
- WHERE 句: `WHERE [Name] = ?`

注意点:
- CData 全ドライバが共通で `[...]` 形式を受け付けるか要検証（Salesforce/Snowflake等）
- 既存 Salesforce 動作を壊さないよう、回帰テスト必須
- バッククォート (\`) を使うエンジンとの互換性確認

### TDD タスク案

1. 🔴 空白入りテーブル名でも Select が成功するテスト（モック JDBC 経由）
2. 🟢 QueryBuilder に `quoteIdentifier(name: String): String` 関数を追加し、すべての SELECT/INSERT/UPDATE/DELETE で使用
3. 🔴 識別子内ブラケット (`]`) のエスケープテスト（CData は `]]` で escape）
4. ✅ Salesforce 既存テスト 121 件が全緑のまま維持

### 関連参考

- CData 公式: ["SQL Reference" 内 識別子クォート](https://cdn.cdata.com/help/UAJ/jdbc/pg_keywords.htm)
- フェーズ2-A 検証ログ: gs-opportunity Select エラー再現手順は [e2e-checklist.md §9](./e2e-checklist.md)

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
