# 認証失敗した Agent コンテナの停止 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | stop-auth-failed-agents |
| 対応 Issue | [#19](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/19) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 0. 準備

- [ ] **T-00** ブランチ `fix/19-stop-auth-failed-agents` を作成
- [ ] **T-01** 現在ループしているコンテナと再起動回数を記録（検証の基準値）

## Phase 1. 判定マーカーの集約

- [ ] **T-10** 🔴 `AgentLogMarkersTest` を作成
- [ ] **T-11** 🟢 `agent/AgentLogMarkers.kt` を実装
- [ ] **T-12** `SyncConnectionService` のマーカー定義を `AgentLogMarkers` に委譲（既存テストは緑のまま）

## Phase 2. 接続操作時の停止

- [ ] **T-20** 🔴 `SyncConnectionServiceTest` に「`AUTH_FAILED` で `stop` が呼ばれる」を追加
- [ ] **T-21** 🟢 `AUTH_FAILED` の分岐で `stop` を呼ぶ
- [ ] **T-22** 🔴🟢 `CONNECTED` / `TIMEOUT` では `stop` が呼ばれないこと
- [ ] **T-23** 🔴🟢 停止が例外を投げても `Failure` を返すこと

## Phase 3. 起動時の棚卸し

- [ ] **T-30** 🔴 `AgentAuthFailureSweeperTest` を作成（失敗しているものだけ停止）
- [ ] **T-31** 🟢 `agent/AgentAuthFailureSweeper.kt` を実装
- [ ] **T-32** 🔴🟢 正常稼働中・ログが空のコンテナを停止しないこと
- [ ] **T-33** 🔴🟢 ログ取得・停止が例外を投げても他のコンテナの処理を続けること
- [ ] **T-34** 🔴🟢 時間窓を `fetchLogs` に渡すこと
- [ ] **T-35** `AppContext` から起動時に呼ぶ（`AUTO_STOP_AUTH_FAILED_AGENTS`、Docker 不可なら呼ばない）

## Phase 4. 検証

- [ ] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [ ] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [ ] **T-42** 実環境でループ中の 4 コンテナが停止し、正常な 3 コンテナが残ることを確認

## Phase 5. 仕上げ

- [ ] **T-50** `docs/` への影響確認
- [ ] **T-51** コミット
- [ ] **T-52** PR 作成・マージ、Issue #19 に結果を記録
