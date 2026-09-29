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

- [x] **T-00** ブランチ `fix/19-stop-auth-failed-agents` を作成
- [x] **T-01** 基準値を記録: Product / ProductPlant / bcartorders / GoogleSheetOpportunitySample が 295 回再起動、Categories / BCartCustomers / AccountHistory が 0 回

## Phase 1. 判定マーカーの集約

- [x] **T-10** 🔴 `AgentLogMarkersTest` を作成
- [x] **T-11** 🟢 `agent/AgentLogMarkers.kt` を実装
- [x] **T-12** `SyncConnectionService` のマーカー定義を `AgentLogMarkers` に委譲（既存テストは緑のまま）

## Phase 2. 接続操作時の停止

- [x] **T-20** 🔴 `SyncConnectionServiceTest` に「`AUTH_FAILED` で `stop` が呼ばれる」を追加
- [x] **T-21** 🟢 `AUTH_FAILED` の分岐で `stop` を呼ぶ
- [x] **T-22** 🔴🟢 `CONNECTED` / `TIMEOUT` では `stop` が呼ばれないこと
- [x] **T-23** 🔴🟢 停止が例外を投げても `Failure` を返すこと

## Phase 3. 起動時の棚卸し

- [x] **T-30** 🔴 `AgentAuthFailureSweeperTest` を作成（失敗しているものだけ停止）
- [x] **T-31** 🟢 `agent/AgentAuthFailureSweeper.kt` を実装
- [x] **T-32** 🔴🟢 正常稼働中・ログが空のコンテナを停止しないこと
- [x] **T-33** 🔴🟢 ログ取得・停止が例外を投げても他のコンテナの処理を続けること
- [x] **T-34** 🔴🟢 時間窓を `fetchLogs` に渡すこと
- [x] **T-35** `AppContext` から起動時に呼ぶ（`AUTO_STOP_AUTH_FAILED_AGENTS`、Docker 不可なら呼ばない）

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-42** 実環境でループ中の 4 コンテナが停止し、正常な 3 コンテナが残ることを確認

## Phase 5. 仕上げ

- [x] **T-50** `docs/` への影響確認
- [x] **T-51** コミット
- [x] **T-52** PR 作成・マージ、Issue #19 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 認証拒否で停止する | ✅ | `SyncConnectionServiceTest` + 実環境 |
| AC-2 停止後に自動復活しない | ✅ | 実環境で停止 100 秒後も `exited` のまま |
| AC-3 起動時の棚卸し | ✅ | console 再起動でループ中の 4 件を停止 |
| AC-4 タイムアウトでは停止しない | ✅ | `SyncConnectionServiceTest` |
| AC-5 接続成功時は停止しない | ✅ | 同上 |
| AC-6 復旧済みのコンテナを止めない | ✅ | 正常な 3 件は `running` のまま。直近ログの時間窓で判定 |
| AC-7 Docker socket 不可でも起動する | ✅ | `containerMgr == null` のとき呼ばない |
| AC-8 停止理由がログに残る | ✅ | 連携名つきの WARN を出力 |
| AC-9 判定ルールが 1 箇所 | ✅ | `AgentLogMarkers` に集約 |
| AC-10 テストと lint | ✅ | `test` 成功。detekt は変更ファイルに新規指摘なし |

### 実環境での検証

console 再起動時のログ:

```
WARN AgentAuthFailureSweeper - 接続キーが kintone に拒否されているため Agent コンテナを停止します: ProductPlant
INFO AgentContainerManager   - Stopped container: kintone-agent-ProductPlant
...
WARN AppContext - 接続キーが拒否されている Agent コンテナを停止しました (4 件): ProductPlant, Product, GoogleSheetOpportunitySample, bcartorders
```

100 秒後の状態:

| 連携 | 再起動回数 | status |
|---|---|---|
| ProductPlant / Product / GoogleSheetOpportunitySample / bcartorders | 297 で停止 | `exited` |
| Categories / BCartCustomers / AccountHistory | 0 | `running` |

### 実装で変わった判断

`AppContext.create` の起動時フラグを `StartupOptions` データクラスにまとめた。
引数として追加すると `LongParameterList` の閾値 (6) を超えて detekt の指摘が増えるため。
既存の 2 つも同じクラスに移している。
