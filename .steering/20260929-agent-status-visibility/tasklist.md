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

- [x] **T-10** 🔴 `AgentContainerManagerTest` に `restarting` → `RESTARTING` のテストを追加
- [x] **T-11** 🟢 `State.RESTARTING` を追加し、`status()` を `status` 文字列ベースに変更
- [x] **T-12** 🔴🟢 `exited` / `paused` / `dead` を `STOPPED` と判定すること
- [x] **T-13** 🔴🟢 `status` が取れないときに `running` 真偽へフォールバックすること
- [x] **T-14** 🔴🟢 `restartCount` を `ContainerInfo` に載せること
- [x] **T-15** `listAll()` の状態判定を共通の `parseState` に統一
- [x] **T-16** `SyncConnectionService` の `when` に `RESTARTING` を追加（restart 扱い）

## Phase 2. 連携名でのルックアップ

- [x] **T-20** 🔴 `statusesBySyncName` のテスト（接頭辞を剥がす / 失敗時は空マップ）
- [x] **T-21** 🟢 `statusesBySyncName` を実装

## Phase 3. 画面

- [x] **T-30** 一覧に Agent 列を追加（Docker 不可なら列を出さない）
- [x] **T-31** `agentStatusBadge` を追加
- [x] **T-32** `app.css` に `.status-badge.failing` を追加
- [x] **T-33** 詳細画面に Agent の状態と再起動回数を表示
- [x] **T-34** `TablesRoutes` が `status()` の戻り値を使っていないか確認

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-42** 一覧で停止中・稼働中の連携が区別できることを確認
- [x] **T-43** 再起動ループの表示を `AUTO_STOP_AUTH_FAILED_AGENTS=false` で確認

## Phase 5. 仕上げ

- [x] **T-50** `docs/` への影響確認
- [x] **T-51** コミット
- [x] **T-52** PR 作成・マージ、Issue #20 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 再起動ループを RESTARTING と判定 | ✅ | `AgentContainerManagerTest` + 実機（`failing` バッジ） |
| AC-2 既存の 3 状態がデグレしない | ✅ | 同上 |
| AC-3 paused / dead を RUNNING と誤判定しない | ✅ | `AgentContainerManagerTest` |
| AC-4 再起動回数が取れる | ✅ | 同上 |
| AC-5 一覧に Agent 列が出る | ✅ | 実機で列を確認 |
| AC-6 再起動ループが異常と分かる | ✅ | `failing`（危険色）で表示 |
| AC-7 詳細に再起動回数が出る | ✅ | 実機「再起動中 7 回再起動」 |
| AC-8 Adapter 表示がデグレしない | ✅ | 実機で全連携の Adapter 列を確認 |
| AC-9 Docker 不可で壊れない | ✅ | `agentContainerManager == null` のとき列を出さない |
| AC-10 N+1 にしない | ✅ | `statusesBySyncName()` を 1 回だけ呼ぶ |
| AC-11 取得失敗でも一覧が出る | ✅ | `AgentContainerManagerTest`（空マップを返す） |
| AC-12 ユニットテスト | ✅ | `AgentContainerManagerTest` に 8 件追加 |
| AC-13 テストと lint | ✅ | `test` 成功。detekt は 84 件で変更前後とも同数 |

### 実機での確認

一覧（#19 で自動停止された連携と正常な連携が区別できている）:

```
連携                          Adapter            Agent
AccountHistory                稼働中 (18001)      稼働中
Categories                    稼働中 (18005)      稼働中
Product                       稼働中 (18007)      停止中      ← 従来は両方「稼働中」で区別不能
ProductPlant                  稼働中 (18008)      停止中
bcartorders                   稼働中 (18009)      停止中
GoogleAccount                 停止中              未作成      ← 停止中と未作成も区別できる
```

再起動ループの表示は、停止済みコンテナを手動で `docker start` して再現:

```
Product   稼働中 (18007)   再起動中(failing)
詳細:     再起動中  7 回再起動
```

確認後にコンテナは停止済み。

### 実装で変わった判断

- 一覧のテーブル描画を `syncTable` に切り出した。Agent 列の追加で
  `tablesListView` が `LongMethod` の閾値 (60) を超えるため
- `listAll()` の状態判定も `parseState` に統一した。設計では `status()` だけの
  変更を想定していたが、一覧 API 側も `"running"` 以外を全て `STOPPED` に
  畳んでいたため、`restarting` を取りこぼす同じ問題があった
