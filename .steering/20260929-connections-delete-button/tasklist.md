# データソース一覧への削除ボタン追加 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#36](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/36) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 参照チェック（純粋関数）

- [x] **T-10** 🔴 `JdbcReferenceIndexTest` を書く（参照あり / 複数 / なし / null / 大文字小文字 / 空）
- [x] **T-11** 🟢 `JdbcReferenceIndex.tablesReferencing` を実装
- [x] **T-12** 🔴🟢 `DeleteConfirm.connectionDeleteOnSubmit` とそのテスト

## Phase 2. 画面とハンドラ

- [x] **T-20** `connectionsListView` の操作列に削除ボタンを追加
- [x] **T-21** `BlockedDelete` と削除拒否時の表示を追加
- [x] **T-22** 削除ハンドラに参照チェックを追加

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-31** `docker compose build && up -d --force-recreate`（CLAUDE.md 手順 7）+ イメージ ID 一致確認
- [x] **T-32** 一覧に削除ボタンが出ることを確認（AC-1, AC-9）
- [x] **T-33** 参照の無い接続が削除できることを確認（AC-4）
- [x] **T-34** `jdbc_ref` を仕込み、参照中は削除されず参照元が出ることを確認（AC-5〜AC-7）

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成（#35 の上に積む）、Issue #36 に結果を記録

---

## 実機検証の結果 (2026-09-29)

使い捨ての接続 `zz-ref-test` と、それを参照する連携 `zz-sync-a` / `zz-sync-b` を
仕込んで検証した（検証後に削除。`config.db` はバックアップと差分なしに復帰）。

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 / AC-9 | ✅ | 全行の操作列に `<button type="submit" class="danger">削除</button>` が出る |
| AC-2 | ✅ | `onsubmit="return confirm('データソース接続 "Salesforce1" を削除しますか？…')"` |
| AC-4 | ✅ | 参照なしの接続を削除 → HTTP 302、`shared_jdbcs` から消えた |
| AC-5 | ✅ | 参照中は削除されず設定が残った |
| AC-6 / AC-7 | ✅ | 「zz-ref-test は次の連携が使用しているため削除できません」＋ `zz-sync-a` `zz-sync-b` の 2 件が出た |
| — | ✅ | `zz-sync-b` の `jdbc_ref` は `ZZ-REF-TEST`（大文字）。大文字小文字の無視が実機でも効いている |

AC-3（キャンセルで削除されない）は JS `confirm` の標準挙動で、#8 と同様に
ブラウザ操作が必要なため未検証。

### 検証中に気づいたこと（今回は対応せず）

`connectionsListView` は行ごとに `loadSharedJdbcConfig` を呼ぶため、
`config_json` が壊れている行が 1 つあると一覧画面全体が 500 になる。
検証用に手で入れた JSON がキー名を間違えていて踏んだ。既存の挙動で本件とは独立。
