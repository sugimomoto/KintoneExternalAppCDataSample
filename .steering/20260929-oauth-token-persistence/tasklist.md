# OAuth トークンの保存 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#34](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/34) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 純粋関数

- [ ] **T-10** 🔴🟢 `JdbcUrlEnhancer.withProperty`（追加・置換・区切り・大文字小文字）
- [ ] **T-11** `withInitiateOAuthOff` を `withProperty` で書き直す（既存テストは緑のまま）
- [ ] **T-12** 🔴🟢 `OAuthTokens.from`（列名の解釈）

## Phase 2. 取得と保存

- [ ] **T-20** `fetchAccessToken` が `OAuthTokens` を返すように変更
- [ ] **T-21** ルート側で接続設定を更新（`InitiateOAuth=REFRESH` + `OAuthRefreshToken`）
- [ ] **T-22** リフレッシュトークンが取れない場合を失敗として扱う

## Phase 3. 検証

- [ ] **T-30** `./gradlew test` / detekt
- [ ] **T-31** `docker compose build && up -d --force-recreate`（CLAUDE.md 手順 7）
- [ ] **T-32** 再認可してトークンが保存されることを確認
- [ ] **T-33** 接続テストが成功することを確認
- [ ] **T-34** 一覧・プレビューでマスクされていることを確認

## Phase 4. 仕上げ

- [ ] **T-40** #12 の設計文書に C-5 撤回の経緯を追記
- [ ] **T-41** コミット
- [ ] **T-42** PR 作成・マージ、Issue #34 に結果を記録
