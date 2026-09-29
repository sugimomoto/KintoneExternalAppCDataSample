# 接続キー拒否からの復旧導線 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | auth-rejected-recovery |
| 対応 Issue | [#21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. 記録の永続化

- [ ] **T-10** 🔴 `AgentConnectionStatusStoreTest` を作成（記録して読み出す）
- [ ] **T-11** 🟢 `agent/AgentConnectionStatus.kt` / `agent/AgentConnectionStatusStore.kt` を実装
- [ ] **T-12** 🔴🟢 複数連携・上書き・`clear`
- [ ] **T-13** 🔴🟢 ファイル不在・壊れたファイルで空を返すこと
- [ ] **T-14** `.gitignore` に `run/` が含まれるか確認

## Phase 2. 検知時の記録

- [ ] **T-20** `Result.AuthRejected` を追加
- [ ] **T-21** 🔴 `SyncConnectionServiceTest`: 認証拒否で `AuthRejected` を返す（既存テストの期待値も更新）
- [ ] **T-22** 🟢 `SyncConnectionService` を変更
- [ ] **T-23** 🔴🟢 認証拒否で記録される / 成功で記録が消える / タイムアウトでは記録しない
- [ ] **T-24** 🔴🟢 `AgentAuthFailureSweeper` が停止時に記録する

## Phase 3. 画面

- [ ] **T-30** `AppContext` にストアを追加
- [ ] **T-31** `ConnectKintoneRoutes` に `AuthRejected` の分岐を追加
- [ ] **T-32** `connectKintoneView` に kintone 側の手順を追加
- [ ] **T-33** 一覧バッジを「キー拒否」に出し分け
- [ ] **T-34** 連携詳細に警告バナーと導線を追加

## Phase 4. 検証

- [ ] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [ ] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [ ] **T-42** 一覧・詳細・接続画面の表示を確認
- [ ] **T-43** 記録が console 再起動後も残ることを確認

## Phase 5. 仕上げ

- [ ] **T-50** `docs/` / README への影響確認
- [ ] **T-51** コミット
- [ ] **T-52** PR 作成・マージ、Issue #21 に結果を記録
