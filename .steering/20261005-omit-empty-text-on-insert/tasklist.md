# Insert で空文字のテキスト値を送らない — タスクリスト

| 項目 | 内容 |
|---|---|
| 設計 | [design.md](design.md) |
| Issue | [#76](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/76) |
| ブランチ | `fix/76-omit-empty-text-on-insert` |

---

## T-1 変換ロジック（TDD）

- [x] T-1-1 `RowMapperTest` に Insert 用のテストを追加（赤を確認）
- [x] T-1-2 `recordToInsertValues` / `isEmptyText` を実装
- [x] T-1-3 テストが通ることを確認
- [x] T-1-4 Update の既存テストが通ることを確認（AC-3）
- [x] T-1-5 実機確認を受けて未入力の数値・日時・選択も省くよう拡張（requirements.md §8）

## T-2 サービス層の差し替え

- [x] T-2-1 `AdapterServiceImpl.insert` の呼び出しを `recordToInsertValues` に変更
- [x] T-2-2 主キーの `filterKeys` を削除（メソッド側に移動済み）

## T-3 品質チェック

- [x] T-3-1 `./gradlew test`（`JAVA_HOME=/opt/homebrew/opt/openjdk@21`）
- [x] T-3-2 `./gradlew detekt`（78 件以下）
- [x] T-3-3 `ktlintFormat` は実行しない

## T-4 コンテナ最新化と実機確認

- [x] T-4-1 `docker compose build adapter-console && docker compose up -d --force-recreate adapter-console`
- [x] T-4-2 イメージ ID の一致を確認
- [x] T-4-3 `rowguid` をマッピングした連携を作り、gRPC で Insert が成功することを確認（AC-9）
- [x] T-4-4 `rowguid` に既定値が入っていることを確認
- [x] T-4-5 投入した行を削除し、AdventureWorksLT を元に戻す

## T-5 PR とマージ

- [ ] T-5-1 コミット
- [ ] T-5-2 PR 作成
- [ ] T-5-3 マージ、Issue #76 に結果を記録

## 完了条件

requirements.md の AC-1 〜 AC-9 をすべて満たす。
