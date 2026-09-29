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

- [x] **T-10** 🔴 `AgentConnectionStatusStoreTest` を作成（記録して読み出す）
- [x] **T-11** 🟢 `agent/AgentConnectionStatus.kt` / `agent/AgentConnectionStatusStore.kt` を実装
- [x] **T-12** 🔴🟢 複数連携・上書き・`clear`
- [x] **T-13** 🔴🟢 ファイル不在・壊れたファイルで空を返すこと
- [x] **T-14** `.gitignore` に `run/` が含まれるか確認

## Phase 2. 検知時の記録

- [x] **T-20** `Result.AuthRejected` を追加
- [x] **T-21** 🔴 `SyncConnectionServiceTest`: 認証拒否で `AuthRejected` を返す（既存テストの期待値も更新）
- [x] **T-22** 🟢 `SyncConnectionService` を変更
- [x] **T-23** 🔴🟢 認証拒否で記録される / 成功で記録が消える / タイムアウトでは記録しない
- [x] **T-24** 🔴🟢 `AgentAuthFailureSweeper` が停止時に記録する

## Phase 3. 画面

- [x] **T-30** `AppContext` にストアを追加
- [x] **T-31** `ConnectKintoneRoutes` に `AuthRejected` の分岐を追加
- [x] **T-32** `connectKintoneView` に kintone 側の手順を追加
- [x] **T-33** 一覧バッジを「キー拒否」に出し分け
- [x] **T-34** 連携詳細に警告バナーと導線を追加

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-42** 一覧・詳細・接続画面の表示を確認
- [x] **T-43** 記録が console 再起動後も残ることを確認

## Phase 5. 仕上げ

- [x] **T-50** `docs/` / README への影響確認
- [x] **T-51** コミット
- [x] **T-52** PR 作成・マージ、Issue #21 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 記録が再起動後も残る | ✅ | `run/agent-connection-status.json` に 3 件。console 再起動後も表示される |
| AC-2 2 経路で記録される | ✅ | `SyncConnectionServiceTest` / `AgentAuthFailureSweeperTest` + 実機（棚卸し経由で記録） |
| AC-3 成功で記録が消える | ✅ | `SyncConnectionServiceTest` |
| AC-4 記録に接続キーを含めない | ✅ | `AgentConnectionStatusStoreTest` + 実ファイルを目視 |
| AC-5 一覧で「キー拒否」と分かる | ✅ | 実機: Product / ProductPlant / bcartorders が `キー拒否(failing)` |
| AC-6 詳細に理由と検知時刻 | ✅ | 実機: 「検知: 2026-09-29 06:32 UTC」 |
| AC-7 1 クリックで接続画面へ | ✅ | 実機: `/syncs/Product/connect` へのボタン |
| AC-8 kintone 側の手順が出る | ✅ | 実機: 4 ステップ + ヘルプへのリンク |
| AC-9 接続キーを再入力できる | ✅ | 実機: Step 3 の入力欄が残る |
| AC-10 復旧で表示が戻る | ✅ | 成功時に `clear` するユニットテスト（実機は有効な接続キーが無いため未確認） |
| AC-11 認証拒否と他の失敗を出し分け | ✅ | `Result.AuthRejected` を追加 |
| AC-12 ユニットテスト | ✅ | `AgentConnectionStatusStoreTest` 10 件 + 既存 2 ファイルに 4 件追加 |
| AC-13 壊れたファイルで落ちない | ✅ | `AgentConnectionStatusStoreTest` |
| AC-14 テストと lint | ✅ | `test` 成功。detekt は origin/main と同じ 84 件 |

### 実機での確認

```
連携                          Adapter            Agent
AccountHistory                稼働中 (18001)      稼働中
Categories                    稼働中 (18005)      稼働中
Product                       稼働中 (18007)      キー拒否      ← 停止中と区別できる
ProductPlant                  稼働中 (18008)      キー拒否
bcartorders                   稼働中 (18009)      キー拒否
AccountFeed                   稼働中 (18000)      停止中        ← 手動停止はこちら
GoogleAccount                 停止中              未作成
```

### 実装で変わった判断

- **検知時刻にタイムゾーンを表示する**ことにした。設計では `yyyy-MM-dd HH:mm` の
  つもりだったが、コンテナの既定タイムゾーンが UTC のため実機で 9 時間ずれて見えた。
  `z` を付けて `2026-09-29 06:32 UTC` と出すようにした。
  `DashboardView` も同じ形式（タイムゾーンなし）なので、そちらは未修整のまま
- `connectKintoneView` の通知系引数を `ConnectNotice` にまとめた。`authRejected` を
  足すと `LongParameterList` の閾値 (6) を超えるため。あわせて `ConnectNotice` は
  `MatchingDeclarationName` を避けるため独立ファイルにした
- 接続結果から通知への変換を `noticeFor` に切り出した。分岐が 1 つ増えると
  `connectKintoneRoutes` が `LongMethod` の閾値を超えるため

### 検証時の事故

接続画面の表示確認で、`POST /syncs/Product/connect` にダミーの接続キーを送った。
`connect` は最初に agent.json を保存するため、**`agent/tables/Product/agent.json` の
接続キーがダミー値で上書きされた**（`.gitignore` 対象で復元不可）。

この接続キーは元々 kintone に拒否されていたもので、いずれ再発行が必要だったため
実害は小さいが、以後この種の確認は使い捨ての `--agent-root` で行うべきだった。
他の連携の agent.json は無傷（いずれも 395 桁のまま）。
