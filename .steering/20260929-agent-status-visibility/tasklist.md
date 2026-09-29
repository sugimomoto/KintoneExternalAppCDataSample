# Agent コンテナの状態表示 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | agent-status-visibility |
| 対応 Issue | [#20](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/20) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. 状態判定

- [ ] **T-10** 🔴 `AgentContainerManagerTest` に `restarting` → `RESTARTING` のテストを追加
- [ ] **T-11** 🟢 `State.RESTARTING` を追加し、`status()` を `status` 文字列ベースに変更
- [ ] **T-12** 🔴🟢 `exited` / `paused` / `dead` を `STOPPED` と判定すること
- [ ] **T-13** 🔴🟢 `status` が取れないときに `running` 真偽へフォールバックすること
- [ ] **T-14** 🔴🟢 `restartCount` を `ContainerInfo` に載せること
- [ ] **T-15** `listAll()` の状態判定を共通の `parseState` に統一
- [ ] **T-16** `SyncConnectionService` の `when` に `RESTARTING` を追加（restart 扱い）

## Phase 2. 連携名でのルックアップ

- [ ] **T-20** 🔴 `statusesBySyncName` のテスト（接頭辞を剥がす / 失敗時は空マップ）
- [ ] **T-21** 🟢 `statusesBySyncName` を実装

## Phase 3. 画面

- [ ] **T-30** 一覧に Agent 列を追加（Docker 不可なら列を出さない）
- [ ] **T-31** `agentStatusBadge` を追加
- [ ] **T-32** `app.css` に `.status-badge.failing` を追加
- [ ] **T-33** 詳細画面に Agent の状態と再起動回数を表示
- [ ] **T-34** `TablesRoutes` が `status()` の戻り値を使っていないか確認

## Phase 4. 検証

- [ ] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [ ] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [ ] **T-42** 一覧で停止中・稼働中の連携が区別できることを確認
- [ ] **T-43** 再起動ループの表示を `AUTO_STOP_AUTH_FAILED_AGENTS=false` で確認

## Phase 5. 仕上げ

- [ ] **T-50** `docs/` への影響確認
- [ ] **T-51** コミット
- [ ] **T-52** PR 作成・マージ、Issue #20 に結果を記録
