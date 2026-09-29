# タスクリスト — Agent 接続フローの不具合修正

## T1. #1 Docker 304 ハンドリング
- [x] T1-1 `AgentContainerManager.start()` を `NotModifiedException` の型捕捉に変更
- [x] T1-2 `AgentContainerManager.stop()` を同様に変更
- [x] T1-3 `AgentContainerManagerTest` を新規作成（mockk、304 で例外が漏れないこと）

**完了条件**: 起動済みコンテナへの start / 停止済みコンテナへの stop が例外を投げない

## T2. #2 設定変更を反映する再起動
- [x] T2-1 `AgentContainerManager.restart()` を追加（NotFound 時は create + start にフォールバック）
- [x] T2-2 `SyncConnectionService` のコンテナ起動ステップを状態別分岐に変更
- [x] T2-3 再起動したことをログ・UI メッセージに反映
- [x] T2-4 テスト: RUNNING なら restart、STOPPED なら start が呼ばれること

**完了条件**: running な Sync への再接続でコンテナが再起動され、新しい agent.json が読み込まれる

## T3. #3 ポート採番
- [x] T3-1 `PortAllocator` の既定レンジを 18000-18099 に是正し `isPublished()` を追加
- [x] T3-2 `SyncPortAllocator` を新規作成（ConfigSource + runner から使用中ポートを収集）
- [x] T3-3 `TableWizardView` の port 入力欄の初期値を採番結果に変更
- [x] T3-4 `TableWizardRoutes` で `port=0` 受信時に採番して保存
- [x] T3-5 `PortMigrator` を新規作成（`port: 0` の既存 Sync を移行、agent.json も更新）
- [x] T3-6 `docker-compose.yml` のポート範囲にコード側定数への参照コメントを追記
- [x] T3-7 テスト: 採番の衝突回避 / 範囲枯渇 / 移行結果

**完了条件**: 新規作成・既存移行ともに公開範囲内のポートになる

## T4. #4 接続判定の是正
- [x] T4-1 `waitForKintoneConnection` を `sinceSeconds` ベースに変更
- [x] T4-2 認証失敗マーカーの検知で即時 Failure を返す
- [x] T4-3 テスト: 古いログ / 認証失敗 / 成功の 3 ケース

**完了条件**: 古いログで誤って成功と判定されない、トークン失効が即座に分かる

## T5. #6 起動時の Adapter 復元
- [x] T5-1 `AppContext.create()` に移行 + `startAll()` を追加（環境変数でスイッチ）
- [x] T5-2 `WebUiCommand` の起動サマリに起動件数を表示
- [~] T5-3 テスト: 自動起動フラグの ON/OFF
      → 自動テストは見送り。`AppContext.create()` は Docker socket / 実 JDBC に依存し、
        ハーネスを組むコストに見合わないため、起動ロジックは既存の `MultiAdapterRunnerTest`
        (`startAll` の失敗継続) + 実環境確認 (T8-2) でカバーする。

**完了条件**: `docker restart adapter-console` 後に全 Sync の Adapter が復元される

## T6. #7 Agent→Adapter 到達性チェック
- [x] T6-1 `AdapterReachabilityChecker` を新規作成
- [x] T6-2 `SyncConnectionService.connect()` に組み込み、失敗時は原因つきメッセージを返す
- [x] T6-3 テスト: 到達可 / 到達不可 / 範囲外ポートのメッセージ

**完了条件**: 到達不可の状態で「接続が確立されました」と表示されない

## T7. 品質チェック
- [x] T7-1 `./gradlew detekt` でリント
- [x] T7-2 `./gradlew test` で全テスト
- [x] T7-3 `./gradlew build` でビルド確認

## T8. 実環境での動作確認
- [x] T8-1 Docker イメージを再ビルドして `adapter-console` を再起動
- [x] T8-2 起動ログで `port: 0` の 8 件が移行され、Adapter が復元されることを確認
- [x] T8-3 Categories を「接続して開始」で再接続し、エラーが出ないことを確認
- [ ] T8-4 kintone から Categories にアクセスしてレコードが表示されることを確認 **(ユーザー確認待ち)**

## T9. ドキュメント更新
- [-] T9-1 `docs/architecture.md` にポート採番・公開範囲の記述を追加
      → **見送り**。design.md とコード内 KDoc (`PortAllocator` / `PortMigrator`) に同内容があり
        三重管理になるため、ユーザー判断で revert。公開範囲の乖離防止は
        `docker-compose.yml` のコメント (T3-6) と README のトラブルシュート (T9-2) でカバーする。
- [x] T9-2 `README.md` のトラブルシュートに `Adapterが利用できません` を追記
- [ ] T9-3 Issue #1-#4, #6, #7 をクローズ (T8-4 の確認後)

---

## 実施結果 (2026-09-29)

### 品質チェック
- `./gradlew test` … **262 件すべて成功**（新規 23 件を追加）
- `./gradlew detekt` … 97 件の指摘で失敗。**変更前 (HEAD) の 100 件から減少**しており、
  本作業で新たに増やした指摘は無い。既存の指摘（`FilterTranslator` の `UseRequire`、
  `AdapterServiceImpl` の `MaxLineLength` 等）は本作業のスコープ外。
- `./gradlew shadowJar` / `docker compose build` … 成功

### 実環境での確認
- `port: 0` だった 8 件が 18000-18009 へ移行（既存の固定ポート 18003 / 18004 とは衝突なし）
- Adapter 7 件が起動時に自動復元。`GoogleSheetOpportunitySample` のみ Google Sheets の
  認証エラーで起動失敗したが、**他の Sync と Web UI は正常に継続**（AC-5 を確認）
- Categories の「接続して開始」が **`Status 304:` エラーを出さずに「接続が確立されました」** を返す
- Categories の Agent が接続操作ごとに 1 回だけ再起動し、`successfully connected to kintone` を出力

### 判明した副作用
ポート移行に伴い稼働中の Agent コンテナを再起動した結果、
AccountFeed / AccountHistory / BCartCustomers / Product / ProductPlant / bcartorders の
6 件が `Unauthenticated desc = invalid token` で再起動ループに入った。

これらは接続キーが既に失効していたもので、いずれも `port: 0` のため
**元から kintone から利用できない状態**だった（再起動によって顕在化しただけ）。
利用するには kintone 側で接続キーを再発行し、Web UI から接続し直す必要がある。
