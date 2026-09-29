# kintone 管理画面パスの修正 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#53](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/53) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 修正

- [x] **T-10** `ADMIN_CONNECTOR_PATH` の値を修正
- [x] **T-11** KDoc に実環境で確認済みである旨と再発防止の注意を残す

## Phase 2. 検証

- [x] **T-20** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-21** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-22** Step 2 の案内が正しいパスを示すことを確認（AC-1）
- [x] **T-23** `dataConnector` が `src/` に残っていないことを確認（AC-2）
- [x] **T-24** Step 1 / Step 3 が従来どおりであることを確認（AC-3）

## Phase 3. 仕上げ

- [x] **T-30** コミット
- [x] **T-31** PR 作成・マージ、Issue #53 に結果を記録

---

## 実機検証の結果 (2026-09-30)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | Step 2 に「管理画面のパス: `https://<kintone ドメイン>/k/admin/system/externalapp/`」が出る |
| AC-2 | ✅ | `src/` に残る `dataConnector` は**旧値を記録した KDoc 1 行のみ**（再発防止のため意図的に残す）。定数の値からは消えた |
| AC-3 | ✅ | Step 1 / Step 2 / Step 3 の見出しが揃い、Step 3 の `name="token"` 入力欄も従来どおり |
| AC-4 | ✅ | `./gradlew test` パス、detekt はベースライン 84 件のまま |

## 振り返り

#51 の設計で制約 C-3 に「管理画面のパスは現行の値を維持する」と書いたのが誤りの直接原因。
**表示されていなかった値を表示するようにした時点で、値の検証が必要だった。**
「現行の値を維持する」という制約は、その値が正しいことを確認して初めて妥当になる。
