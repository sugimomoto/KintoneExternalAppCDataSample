# kintone ドメイン入力の削除 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#51](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/51) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 削除と置き換え

- [x] **T-10** Step 2 を案内文 + 管理画面パスに置き換える
- [x] **T-11** `readKintoneDomain()` を削除する
- [x] **T-12** `POST /syncs/{name}/connect/save-domain` を削除する
- [x] **T-13** `authRejectedGuide()` の「(下の Step 2 のリンク)」を修正する
- [x] **T-14** 不要になった import を整理する

## Phase 2. 検証

- [x] **T-20** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-21** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-22** 入力欄と「保存して開く」が無いことを確認（AC-1）
- [x] **T-23** `POST .../save-domain` が 404 であることを確認（AC-2）
- [x] **T-24** Step 2 に管理画面のパスが出ることを確認（AC-3）
- [x] **T-25** 接続キー拒否時の案内がリンクを指していないことを確認（AC-4）
- [x] **T-26** `KINTONE_DOMAIN` の参照が消えたことを確認（AC-5）
- [x] **T-27** Step 1 / Step 3 が従来どおりであることを確認（AC-6）

## Phase 3. 仕上げ

- [x] **T-30** コミット
- [x] **T-31** PR 作成・マージ、Issue #51 に結果を記録

---

## 実機検証の結果 (2026-09-29)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | `name="domain"` / `保存して開く` / `save-domain` / `kintone 管理画面を開く` / `example.cybozu.com` すべて HTML に無し |
| AC-2 | ✅ | `POST /syncs/AccountFeed/connect/save-domain` → **HTTP 404** |
| AC-3 | ✅ | Step 2 に「管理画面のパス: `https://<kintone ドメイン>/k/admin/system/admin/dataConnector.html`」が出る |
| AC-4 | ✅ | 「kintone の管理画面で「外部システムのアプリ化」を開く (パスは下の Step 2)」に修正済み |
| AC-5 | ⚠ | 機能的な参照は消えた。残っているのは**削除理由を記録したコメント 1 行のみ**（再追加を防ぐため意図的に残す） |
| AC-6 | ✅ | Step 1 / Step 2 / Step 3 の見出しが揃っており、Step 3 の `name="token"` 入力欄も従来どおり |
| AC-7 | ✅ | `./gradlew test` パス、detekt はベースライン 84 件のまま |

未使用になった import (`kotlinx.html.InputType` / `kotlinx.html.input`) も削除した。
Step 3 の接続キー入力は `textArea` なので影響しない。
