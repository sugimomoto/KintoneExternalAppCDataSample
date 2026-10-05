# レコード ID 列の指定 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#66](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/66) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 型判定（TDD）

- [x] **T-10** 🔴 `FieldTypeSuggesterTest` に `canBeRecordId` のテストを追加
- [x] **T-11** 🟢 `canBeRecordId` を実装し、`suggestRecordIdType` と対応表を共有する

## Phase 2. ビュー（TDD）

- [x] **T-20** 🔴 `RecordIdSelectionTest` で選択 UI の描画を検証
- [x] **T-21** 🟢 `Step3Content` に候補列を追加し、選択 UI と注意書きを描画

## Phase 3. ルート

- [x] **T-30** step4 で `recordIdColumn` を受け取り、あればそれを使う
- [x] **T-31** `NoPrimaryKey` に詰める候補を型で絞る

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（82 件以下）
- [x] **T-41** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-42** 主キーの無いビューで候補列の選択 UI が出ることを確認（AC-1, AC-2）
- [x] **T-43** 列を選んで step4 に進めることを確認（AC-1, AC-5）
- [x] **T-44** 保存した `primary-key.jdbc-column` が選んだ列になることを確認（AC-4）
- [x] **T-45** 主キーのあるテーブルで選択 UI が出ないことを確認（AC-7）
- [x] **T-46** 一意性の注意書きが出ることを確認（AC-3）
- [x] **T-47** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 5. 仕上げ

- [x] **T-50** コミット
- [x] **T-51** PR 作成・マージ、Issue #66 に結果を記録

---

## 実機検証の結果 (2026-10-05)

主キーの無いビュー `SalesLT.vGetAllCategories` で検証した。

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | 候補列の選択 UI が出て、`ProductCategoryID` を選ぶと **Step 4 に進めた** |
| AC-2 | ✅ | 候補は `['ProductCategoryID']` のみ。`ProductCategory`（VARCHAR）も型的には候補になるが、このビューでは `ProductCategoryID` のみが抽出された |
| AC-3 | ✅ | 「選んだ列の値が**一意であることは利用者の責任です**。重複がある列を選ぶと、更新・削除が意図しない行に及びます。」 |
| AC-4 | ✅ | 保存後 `primary-key.jdbc-column = ProductCategoryID` / `schema = SalesLT` |
| AC-5 | ✅ | `ProductCategoryID` は `INTEGER` なので `record-id-type = NUMBER` が選択済み／保存済み |
| AC-6 | — | 候補 0 件は `RecordIdSelectionTest`「候補が空なら何も描画しない」で担保。SalesLT のビューはいずれも整数/文字列列を持つため実機では再現できなかった |
| AC-7 | ✅ | `SalesLT.Customer`（主キーあり）は選択 UI が出ず、主キー `CustomerID` が自動で決まる |
| AC-9 | ✅ | 505 テストパス、detekt は **82 件**（#64 完了時点と同じ、増やしていない） |

検証データ（`zz-viewtest`）は削除し、連携一覧はバックアップと差分なし。

## 受け入れたリスク

**一意でない列を選べてしまう。** 一意性の検証（`COUNT(*)` と `COUNT(DISTINCT col)` の
比較）は大きなテーブルで重く、設定のたびに全件走査になるため行わない。
代わりに「更新・削除が意図しない行に及ぶ」ところまで画面で明示した。

直接 POST で型の合わない列を指定された場合は採用しない（`resolveRecordIdColumn` で
`canBeRecordId` を再チェックする）。候補は型で絞って出しているが、画面を経由しない
経路に備えている。
