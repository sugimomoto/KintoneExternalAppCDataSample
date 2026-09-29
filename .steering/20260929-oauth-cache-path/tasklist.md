# OAuth キャッシュパスの一本化 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | oauth-cache-path |
| 対応 Issue | [#11](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/11) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. キャッシュパスの決定

- [ ] **T-10** 🔴 `JdbcUrlEnhancerTest` を `withOAuthCache` 名に更新し、異常系を追加
- [ ] **T-11** 🟢 `JdbcUrlEnhancer` を改名・引数名変更

## Phase 2. 参照名の取得

- [ ] **T-20** 🔴 `SqliteConfigSourceTest` に `sharedJdbcRefOf` のテストを追加
- [ ] **T-21** 🟢 `ConfigSource` に既定実装つきで追加し、`SqliteConfigSource` で実装

## Phase 3. プロバイダでの一律付与

- [ ] **T-30** `JdbcConnectionProvider` に `oauthCacheKey` を必須追加し、`init` で付与
- [ ] **T-31** コンパイルエラーになる 7 箇所を確認（付与忘れ防止が効いていること）
- [ ] **T-32** `TableAdapterServer` から URL 付与を削除し、キーを受け取る形に変更
- [ ] **T-33** `MultiAdapterRunner` でキーを解決して渡す
- [ ] **T-34** Web UI の接続テスト・ウィザード 3 経路を修正
- [ ] **T-35** CLI (`test-connection` / `list-tables`) を修正
- [ ] **T-36** テストの Fake ファクトリ 3 箇所を 2 引数に修正

## Phase 4. 検証

- [ ] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [ ] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [ ] **T-42** 接続テスト・ウィザード・実行時のログで同じキャッシュパスが出ることを確認

## Phase 5. 仕上げ

- [ ] **T-50** README に既存キャッシュの扱い（一度の再認可が必要）を明記
- [ ] **T-51** `docs/extending.md` の R-10 を更新
- [ ] **T-52** コミット
- [ ] **T-53** PR 作成・マージ、Issue #11 に結果を記録
