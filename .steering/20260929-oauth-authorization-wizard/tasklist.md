# OAuth 認可ウィザード — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 純粋関数（TDD の主戦場）

- [ ] **T-10** 🔴🟢 `OAuthCapability.isOAuthAuthScheme`
- [ ] **T-11** 🔴🟢 `OAuthUrlMasker.maskQuery`
- [ ] **T-12** 🔴🟢 `JdbcUrlEnhancer.withInitiateOAuthOff`
- [ ] **T-13** 🔴🟢 `ErrorMessageTranslator` の OAuth ルール

## Phase 2. プロシージャ実行

- [ ] **T-20** `OAuthAuthorizer` を実装（`Statement.execute()` + `Url` 列の取得）
- [ ] **T-21** SQL リテラルのエスケープ（verifier は外部由来）
- [ ] **T-22** `OAuthCapability.hasAuthorizationProcedure`

## Phase 3. 画面とルーティング

- [ ] **T-30** `ConnectionOAuthView` を実装（Step 1 / Step 2 / 完了 / 失敗）
- [ ] **T-31** `ConnectionOAuthRoutes` を実装
- [ ] **T-32** `WebUiServer` にルート登録
- [ ] **T-33** 編集画面に導線を追加（`AuthScheme` が OAuth 系のときのみ）

## Phase 4. 検証

- [ ] **T-40** `./gradlew test` / detekt
- [ ] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`
- [ ] **T-42** Salesforce 接続で認可 URL が生成されることを実機確認
- [ ] **T-43** SAP Gateway で導線が出ないことを実機確認
- [ ] **T-44** ログに認可 URL のクエリ / verifier が出ないことを確認

## Phase 5. ドキュメント

- [ ] **T-50** `docs/architecture.md` §3.2 に OAuth の対応状況を明記
- [ ] **T-51** README の制約表・トラブルシューティングを更新
- [ ] **T-52** `docs/extending.md` の R-10 を更新
- [ ] **T-53** `HelpView` の「初回ブラウザ認可が必要」を実手順に差し替え

## Phase 6. 仕上げ

- [ ] **T-60** コミット
- [ ] **T-61** PR 作成・マージ、Issue #12 に結果を記録（検証の限界を明記）
