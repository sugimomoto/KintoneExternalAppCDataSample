# 自動生成列の除外 — タスクリスト

| 項目 | 内容 |
|---|---|
| 設計 | [design.md](design.md) |
| Issue | [#75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75) |
| ブランチ | `fix/75-exclude-generated-columns` |

---

## T-1 判定ロジック（TDD）

- [x] T-1-1 `GeneratedColumnsTest` を書く（赤を確認）
- [x] T-1-2 `metadata/GeneratedColumns.kt` を実装（`ExclusionReason` / `ExcludedColumn` / `ColumnPartition` / `reasonFor` / `partition`）
- [x] T-1-3 テストが通ることを確認

## T-2 メタデータの取得（F-1）

- [x] T-2-1 `ColumnInfo` に `defaultValue` / `autoIncrement` / `generated` を既定値付きで追加
- [x] T-2-2 `listColumns` で `COLUMN_DEF` / `IS_AUTOINCREMENT` / `IS_GENERATEDCOLUMN` を読む（存在する列だけ）
- [x] T-2-3 `JdbcMetadataInspectorTest` に H2 での確認を追加（identity 列・DEFAULT 付き列）
- [x] T-2-4 既存の主キー検出テストが通ることを確認（AC-4）

## T-3 画面表示（F-2 / F-3）

- [x] T-3-1 `Step3Content` に `excludedColumns` を追加
- [x] T-3-2 `excludedColumnsNotice` を実装（列・型・理由・既定値の式）
- [x] T-3-3 `wizardStep3View` から呼ぶ
- [x] T-3-4 `Step3ExcludedColumnsTest` を書く

## T-4 ルート側の適用（F-4 / F-5）

- [x] T-4-1 `get("/syncs/new/step3")` で `partition` を適用
- [x] T-4-2 `computeStep4` のマッピング生成で `partition` を適用
- [x] T-4-3 レコード ID 列の解決・`NoPrimaryKey` の候補は `allColumns` のままであることを確認

## T-5 品質チェック

- [x] T-5-1 `./gradlew test`（`JAVA_HOME=/opt/homebrew/opt/openjdk@21`）
- [x] T-5-2 `./gradlew detekt`（78 件以下）
- [x] T-5-3 `ktlintFormat` は実行していない（既存 895 件を巻き込む）

## T-6 コンテナ最新化と実機確認

- [x] T-6-1 `docker compose build adapter-console && docker compose up -d --force-recreate adapter-console`
- [x] T-6-2 イメージ ID の一致を確認
- [x] T-6-3 `SalesLT.Customer` で step3 を開き、4 列が除外表に出ることを確認
- [x] T-6-4 step4 に進み、`CustomerID` が主キーとして決まることを確認（AC-4）
- [x] T-6-5 Salesforce / B-Cart でも確認（requirements.md §8）

## T-7 PR とマージ

- [ ] T-7-1 コミット
- [ ] T-7-2 PR 作成
- [ ] T-7-3 マージ、Issue #75 に結果を記録

## 完了条件

requirements.md の AC-1 〜 AC-9 をすべて満たす。
