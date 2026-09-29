# 連携一覧画面への削除ボタン追加 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | syncs-list-delete-button |
| 対応 Issue | [#8](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/8) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. 確認ダイアログの生成

- [x] **T-10** 🔴 `DeleteConfirmTest` を作成（文言と JS エスケープ）
- [x] **T-11** 🟢 `web/views/DeleteConfirm.kt` を実装
- [x] **T-12** 🔴🟢 エスケープの異常系（`\` / 改行 / 順序）

## Phase 2. 画面

- [x] **T-20** `tableActions()` に削除ボタンを追加（稼働状態で出し分けない）
- [x] **T-21** 詳細画面の独立した削除フォームを削除（重複排除）
- [x] **T-22** `app.css` の確認 → **既に定義済みのため変更なし**（別作業 `dff272b` で実装済み）

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-31** `SyncsListE2ETest` に 3 件追加（コンパイルは通るが実行環境の制約で未実行。下記参照）
- [x] **T-32** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-33** 一覧・詳細の表示を実機確認（ボタン数・色・横並び）
- [x] **T-34** 実機で 1 件削除して一覧から消えることを確認

## Phase 4. 仕上げ

- [x] **T-40** `docs/` への影響確認（UI 追加のみで基本設計に影響なし）
- [x] **T-41** コミット
- [x] **T-42** PR 作成・マージ、Issue #8 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 一覧の操作列に削除ボタン | ✅ | 実機: 11 行すべてに `削除(danger)` |
| AC-2 稼働中・停止中の両方に表示 | ✅ | 実機: 「⏸ 停止 + 削除」と「▶ 開始 + 削除」の両方を確認 |
| AC-3 確認ダイアログの内容 | ✅ | `DeleteConfirmTest` + 実機の `onsubmit` 属性 |
| AC-4 キャンセルで削除されない | ⚠️ | ブラウザ標準の `confirm` に委ねる。E2E は書いたが未実行（下記） |
| AC-5 OK で削除され一覧から消える | ✅ | 隔離環境で削除 → 302 → 11 件が 10 件に |
| AC-6 Adapter / Agent も停止・削除 | ✅ | 既存ハンドラを変更していない（C-1） |
| AC-7 danger スタイル | ✅ | 実機で `class="danger"`。CSS は既に定義済み |
| AC-8 0 件のときは空状態 | ✅ | `empty-state` は `syncTable` の外側にあり変更していない |
| AC-9 詳細画面の回帰なし | ✅ | 実機: 連携削除フォームは 1 つだけ |
| AC-10 detekt とテスト | ✅ | `test` 成功。detekt は origin/main と同じ 84 件 |

### 実機での確認

```
連携                              操作列
AccountFeed                       ⏸ 停止(secondary) | 削除(danger)
GoogleAccount                     ▶ 開始(secondary) | 削除(danger)

削除ボタン数: 11 / 行数: 11

confirm: return confirm('連携 "AccountFeed" を削除しますか？稼働中の Adapter と
         Agent コンテナを停止・削除し、agent.json も削除します。この操作は取り消せません。')
```

削除の実行は、**本番の設定を複製した隔離環境**（別ポート・Docker socket 非マウント）で確認した。
実 Agent コンテナに触らないためであり、本番側の連携数は 11 件のまま無傷。

```
隔離環境: POST /syncs/gs-opportunity/delete → 302 → 連携数 11 → 10、gs-opportunity が消えた
本番側:   連携数 11 のまま、gs-opportunity あり
```

### 実装で変わった判断

**1. 詳細画面の独立した削除フォームを削除した（要求定義に無い変更）**

一覧と詳細は同じ `tableActions()` を使っているため、ここに削除ボタンを追加すると
**詳細画面にボタンが 2 つ並ぶ**。詳細画面側（`TablesView.kt:146-149`）を削除して一本化した。
結果として確認文言も 1 箇所になった。

**2. 確認ダイアログの生成を `DeleteConfirm` に切り出した**

現行の実装は連携名を素の文字列補間で JS に埋め込んでおり、**エスケープしていなかった**。
`'` を含む連携名では `confirm(' ... ' ... ')` となり JS 構文エラーで削除ボタンが動かない。
要求定義の C-4 が指摘していた懸念そのもの。純粋関数に切り出してテストで固定した。

**3. `app.css` は変更しなかった**

要求定義では「`button.danger` は定義済みだが未使用」「hover 状態を追加する」としていたが、
確認したところ `:hover` / `:active` とも別作業（`dff272b` Web UI の表示統一）で
実装済みだった。要求定義を書いた時点より実装が進んでいた。

### E2E テストについて

`SyncsListE2ETest` に 3 件追加した（削除ボタンの表示 / キャンセル挙動 / 詳細画面のボタン数）。
コンパイルは通るが**実行できていない**。Playwright のブラウザ実行に必要な共有ライブラリが
`eclipse-temurin:21-jdk-jammy` に無く、`validateHostRequirementsForExecutable` で失敗する。

```
SyncsListE2ETest > initializationError FAILED
    com.microsoft.playwright.PlaywrightException
```

E2E を書く過程で**セレクタの誤りを 1 つ見つけて直した**。`button.danger:has-text("削除")`
は部分一致のため、詳細画面の別機能「agent.json を削除」ボタンも拾ってしまう。
フォームの `action` で特定する形に変更した。
