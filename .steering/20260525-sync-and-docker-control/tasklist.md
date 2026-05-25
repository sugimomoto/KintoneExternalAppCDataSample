# Phase 2-C: タスクリスト

| 項目 | 内容 |
|---|---|
| フェーズ | 2-C |
| 作成日 | 2026-05-25 |
| 関連 | [requirements.md](./requirements.md), [design.md](./design.md) |
| 概算工数 | 約 10 日 |

## 凡例

- ⬜ 未着手 / ✅ 完了
- 🔴 RED / 🟢 GREEN / 🔵 REFACTOR
- ⚙️ 設定・スキャフォールド / 📝 ドキュメント / ✅ 動作確認
- [S] 〜1h / [M] 〜4h / [L] 〜1日

---

# M1: 用語統一 (Tables → Syncs)

## M1-A: URL リダイレクトとファイルリネーム

- ⬜ **T-M1-A-01** ⚙️ [S] `routes/TablesRoutes.kt` → `routes/SyncsRoutes.kt` リネーム
- ⬜ **T-M1-A-02** ⚙️ [S] `views/TablesView.kt` → `views/SyncsView.kt` リネーム
- ⬜ **T-M1-A-03** ⚙️ [S] `routes/TableWizardRoutes.kt` → `routes/SyncWizardRoutes.kt`
- ⬜ **T-M1-A-04** ⚙️ [S] `views/TableWizardView.kt` → `views/SyncWizardView.kt`
- ⬜ **T-M1-A-05** 🟢 [S] 旧 URL からの 301 リダイレクトハンドラ追加 (`/tables/*` → `/syncs/*`)
- ⬜ **T-M1-A-06** 🔴🟢 [S] `301` リダイレクトのテスト (TestApplication)

## M1-B: UI 文言の翻訳

- ⬜ **T-M1-B-01** 🟢 [M] Layout のナビゲーション `Tables` → `Syncs (連携)`
- ⬜ **T-M1-B-02** 🟢 [M] 一覧ページの見出し・ボタンを翻訳 (`+ New Table` → `+ 新しい連携`)
- ⬜ **T-M1-B-03** 🟢 [S] ダッシュボードの "Active Adapters" → "稼働中の連携"
- ⬜ **T-M1-B-04** 🟢 [S] 詳細ページの "Server" / "JDBC" / "Table" / "Capability" を日本語化

- **M1 完了基準**: ブラウザで `/tables` → `/syncs` リダイレクト、UI 文言が日本語化

---

# M2: Docker SDK 統合 + AgentContainerManager

## M2-A: 依存追加とクライアント初期化

- ⬜ **T-M2-A-01** ⚙️ [S] `build.gradle.kts` に `docker-java-core` 3.4.0, `docker-java-transport-httpclient5`
- ⬜ **T-M2-A-02** 🟢 [S] `DockerClientFactory` で `/var/run/docker.sock` 接続
- ⬜ **T-M2-A-03** 🟢 [S] socket が無い環境のフォールバック (`AgentControlMode.available = false`)

## M2-B: AgentContainerManager 実装

- ⬜ **T-M2-B-01** 🔴🟢 [M] `ensureCreated(syncName)`: 既存があれば idempotent、無ければ create
- ⬜ **T-M2-B-02** 🔴🟢 [S] `start(syncName)`: コンテナ起動
- ⬜ **T-M2-B-03** 🔴🟢 [S] `stop(syncName, timeout)`: コンテナ停止
- ⬜ **T-M2-B-04** 🔴🟢 [S] `remove(syncName, force)`: 停止 + 削除
- ⬜ **T-M2-B-05** 🔴🟢 [S] `status(syncName)`: 稼働状態取得
- ⬜ **T-M2-B-06** 🔴🟢 [S] `listAll()`: ラベル `com.cdata.adapter.managed=true` のコンテナ列挙
- ⬜ **T-M2-B-07** 🔵 [S] エラーハンドリング (NotFound / Conflict 等)

## M2-C: テスト

- ⬜ **T-M2-C-01** 🔴🟢 [M] AgentContainerManagerTest (モック DockerClient 利用)
- ⬜ **T-M2-C-02** ✅ [M] 実 Docker で create → start → stop → remove のサイクル動作確認

- **M2 完了基準**: AgentContainerManager で kintone-agent-* コンテナを完全制御可能

---

# M3: Sync 詳細画面の刷新

## M3-A: PublicKeyManager

- ⬜ **T-M3-A-01** 🔴🟢 [S] `agent/public-key.pem` を読み込む
- ⬜ **T-M3-A-02** 🔴🟢 [S] SHA-256 fingerprint 生成
- ⬜ **T-M3-A-03** 🔴🟢 [S] `GET /syncs/{name}/public-key.pem` で `Content-Disposition: attachment` 配信

## M3-B: 新 Sync 詳細ビュー

- ⬜ **T-M3-B-01** 🟢 [M] データソース section (driver + db table)
- ⬜ **T-M3-B-02** 🟢 [M] kintone 接続 section (公開鍵 + コピーボタン + ダウンロード + token ステータス)
- ⬜ **T-M3-B-03** 🟢 [M] 同期フィールド section (マッピング表)
- ⬜ **T-M3-B-04** 🟢 [S] アクティビティ section ([ ログを見る ] リンク)
- ⬜ **T-M3-B-05** 🟢 [S] アクションバー (開始 / 停止 / 再起動 / 削除) を Sync ステータスで切替表示
- ⬜ **T-M3-B-06** 🟢 [S] Advanced 折りたたみ (port / container / file path)

## M3-C: アクションのバックエンド統合

- ⬜ **T-M3-C-01** 🔴🟢 [S] `POST /syncs/{name}/start` で Adapter + Agent コンテナ両方起動
- ⬜ **T-M3-C-02** 🔴🟢 [S] `POST /syncs/{name}/stop` で Adapter + Agent コンテナ両方停止
- ⬜ **T-M3-C-03** 🔴🟢 [S] `POST /syncs/{name}/restart` で順次再起動
- ⬜ **T-M3-C-04** 🔴🟢 [S] `POST /syncs/{name}/delete` で Adapter 停止 + Agent コンテナ削除 + 設定削除

- **M3 完了基準**: Sync 詳細画面が新 UI、上級者向け詳細は折りたたみ表示

---

# M4: 「kintone とつなぐ」1 ボタン

## M4-A: SyncConnectionService

- ⬜ **T-M4-A-01** 🔴🟢 [M] `connectKintone(syncName, token, kintoneDomain?)`: agent.json 保存 + Adapter 起動 + Agent 作成・起動 + 接続待機
- ⬜ **T-M4-A-02** 🔴🟢 [S] 接続待機 (10秒タイムアウト、Agent ログから `successfully connected` 検出)
- ⬜ **T-M4-A-03** 🔴🟢 [S] エラー時の rollback (Agent コンテナ削除等)

## M4-B: KintonePreferences

- ⬜ **T-M4-B-01** 🔴🟢 [S] kintone ドメイン等の永続化 (sqlite or yaml)
- ⬜ **T-M4-B-02** 🟢 [S] 初回アクセス時にドメイン入力フォーム表示、以降は値保持

## M4-C: 接続画面 (`/syncs/{name}/connect`)

- ⬜ **T-M4-C-01** 🟢 [M] Step1: 公開鍵セクション (コピー / ダウンロード)
- ⬜ **T-M4-C-02** 🟢 [S] Step2: kintone ドメイン入力 + 管理画面リンク
- ⬜ **T-M4-C-03** 🟢 [M] Step3: token textarea + [接続して開始] ボタン
- ⬜ **T-M4-C-04** 🟢 [S] 接続中のローディング表示 (HTMX with hx-indicator)
- ⬜ **T-M4-C-05** 🟢 [S] 接続成功時の遷移 (Sync 詳細 + 完了メッセージ)
- ⬜ **T-M4-C-06** 🟢 [S] 接続失敗時のエラー表示 (ErrorMessageTranslator 経由)

- **M4 完了基準**: 1 ボタンで Adapter + Agent + kintone 接続が全自動

---

# M5: ライブログ表示

## M5-A: WebLogAppender

- ⬜ **T-M5-A-01** 🔴🟢 [M] Logback Appender + RingBuffer (capacity 1000)
- ⬜ **T-M5-A-02** 🔴🟢 [S] MDC に `sync=<syncName>` を入れる仕組み (AdapterServiceImpl 内)
- ⬜ **T-M5-A-03** 🔴🟢 [S] `snapshot(syncName)` でフィルタ
- ⬜ **T-M5-A-04** 🔴🟢 [S] `subscribe(callback)` でリアルタイム通知
- ⬜ **T-M5-A-05** ⚙️ [S] `logback.xml` に追加

## M5-B: Adapter ログ SSE エンドポイント

- ⬜ **T-M5-B-01** 🔴🟢 [S] `GET /syncs/{name}/logs/adapter/sse` で snapshot + subscribe
- ⬜ **T-M5-B-02** 🟢 [S] フォーマット: timestamp / level / message を JSON 化

## M5-C: Agent ログ Docker API 中継

- ⬜ **T-M5-C-01** 🔴🟢 [M] `AgentContainerManager.streamLogs(syncName)`: Docker API logContainer を Flow に変換
- ⬜ **T-M5-C-02** 🔴🟢 [S] `GET /syncs/{name}/logs/agent/sse` で配信
- ⬜ **T-M5-C-03** 🟢 [S] backpressure / 切断ハンドリング

## M5-D: ログ画面 (`/syncs/{name}/logs`)

- ⬜ **T-M5-D-01** 🟢 [M] 左右ペイン (Adapter / Agent) の HTML レイアウト
- ⬜ **T-M5-D-02** 🟢 [S] 2 つの EventSource 接続
- ⬜ **T-M5-D-03** 🟢 [S] レベルフィルタ (INFO/WARN/ERROR チェックボックス) クライアントサイド
- ⬜ **T-M5-D-04** 🟢 [S] 検索 (case insensitive 部分一致) クライアントサイド
- ⬜ **T-M5-D-05** 🟢 [S] 自動スクロール ON/OFF、表示上限 1000 行

- **M5 完了基準**: Sync 詳細から「ログを見る」で Adapter/Agent ログがライブテール表示

---

# M6: ウィザード強化

## M6-A: jdbc-ref 選択分岐 (WZ-01)

- ⬜ **T-M6-A-01** 🔴🟢 [S] `ConfigSource.saveTableSetWithRef(name, set, ref)` を interface に昇格
- ⬜ **T-M6-A-02** 🟢 [S] YamlConfigSource に実装追加 (jdbc-ref.yaml 生成)
- ⬜ **T-M6-A-03** 🟢 [M] Step1 で「既存接続を使う」「個別 URL を直接入力」のラジオボタン
- ⬜ **T-M6-A-04** 🔴🟢 [S] Step4 の `POST /syncs` で jdbc-ref か inline かを判定して保存

## M6-B: 自動遷移 (WZ-02)

- ⬜ **T-M6-B-01** 🟢 [S] Step4 のボタンを「保存」「保存 & 接続」の 2 種類に
- ⬜ **T-M6-B-02** 🟢 [S] 「保存 & 接続」を選んだら `/syncs/{name}/connect` に自動遷移

- **M6 完了基準**: 既存接続を選んでウィザード完了 → kintone 接続画面に自動遷移

---

# M7: UX 改善 (エラー文言 + 上級者表示)

## M7-A: ErrorMessageTranslator

- ⬜ **T-M7-A-01** 🔴🟢 [M] `TranslationRule` データクラス + ルールテーブル
- ⬜ **T-M7-A-02** 🔴🟢 [S] 主要エラー 10 パターン以上のルール追加 (RPC / Connection / Driver 等)
- ⬜ **T-M7-A-03** 🔴🟢 [S] `translate(rawMessage)` のテーブル駆動テスト

## M7-B: エラー UI 表示

- ⬜ **T-M7-B-01** 🟢 [S] エラーカード コンポーネント (UserMessage を受け取って表示)
- ⬜ **T-M7-B-02** 🟢 [S] action ボタン (RECONNECT / GO_TO_DRIVERS 等) を表示
- ⬜ **T-M7-B-03** 🟢 [S] 既存の例外メッセージ生表示箇所を ErrorMessageTranslator 経由に置換

## M7-C: Advanced 折りたたみ統一

- ⬜ **T-M7-C-01** 🟢 [S] 共通コンポーネント `advancedDetails { ... }` を views/Components.kt に追加
- ⬜ **T-M7-C-02** 🟢 [S] Connections 詳細, Drivers 詳細にも Advanced 適用

- **M7 完了基準**: エラー時に日本語のユーザー向け文言 + アクションボタン表示

---

# M8: Docker 配布

## M8-A: Dockerfile (DP-01)

- ⬜ **T-M8-A-01** ⚙️ [S] マルチステージ Dockerfile (build + runtime)
- ⬜ **T-M8-A-02** ⚙️ [S] `.dockerignore` で build/, gradle/, .git/ を除外
- ⬜ **T-M8-A-03** ⚙️ [S] EXPOSE 8080 + 18000-18099
- ⬜ **T-M8-A-04** ⚙️ [S] healthcheck (gRPC health check or HTTP)
- ⬜ **T-M8-A-05** ✅ [S] `docker build .` 成功 + イメージサイズ確認

## M8-B: docker-compose.yml (DP-02)

- ⬜ **T-M8-B-01** ⚙️ [S] ルートに `docker-compose.yml` 作成 (adapter-console サービスのみ)
- ⬜ **T-M8-B-02** ⚙️ [S] ボリュームマウント設定 (config, lib, agent, run, docker.sock)
- ⬜ **T-M8-B-03** ⚙️ [S] ポート範囲マッピング (8080 + 18000-18099)
- ⬜ **T-M8-B-04** ✅ [M] `docker compose up -d adapter-console` で起動 + ブラウザアクセス確認

## M8-C: 旧 compose ファイルの扱い

- ⬜ **T-M8-C-01** 📝 [S] `agent/docker-compose.multi.yml` 冒頭に「Phase 2-C 以降は Web UI 推奨、本ファイルは Phase 2-D で削除」コメント追加
- ⬜ **T-M8-C-02** 📝 [S] README で従来手順との互換性を案内

- **M8 完了基準**: `docker compose up -d adapter-console` 1 コマンドで全部起動

---

# M9: ドキュメント + リリース

## M9-A: README

- ⬜ **T-M9-A-01** 📝 [S] クイックスタート: `docker compose up -d adapter-console` ベースに書き直し
- ⬜ **T-M9-A-02** 📝 [S] 用語: Sync / データソース接続 / ドライバー の解説
- ⬜ **T-M9-A-03** 📝 [S] Docker socket マウントのセキュリティ注意書き
- ⬜ **T-M9-A-04** 📝 [S] CLI 一覧の更新

## M9-B: 永続的ドキュメント更新

- ⬜ **T-M9-B-01** 📝 [S] `docs/architecture.md` に Docker SDK + Sync 概念追加
- ⬜ **T-M9-B-02** 📝 [S] `docs/glossary.md` に「Sync」「公開鍵」「kintone 接続キー」追加
- ⬜ **T-M9-B-03** 📝 [S] `docs/repository-structure.md` に `web/` 配下の新規ファイル反映

## M9-C: E2E チェックリスト

- ⬜ **T-M9-C-01** 📝 [M] `e2e-checklist.md` (Phase 2-C 用) 作成

## M9-D: タグ・リリース

- ⬜ **T-M9-D-01** ⚙️ [S] `v0.4.0-sync-docker` タグ作成
- ⬜ **T-M9-D-02** ⚙️ [S] origin/main に push

- **M9 完了基準**: README + 永続的ドキュメント全て更新、タグ付け済み

---

## タスク総数と工数集計

| マイルストーン | タスク数 | 概算工数 |
|---|---|---|
| M1: 用語統一 | 10 | 0.5日 |
| M2: Docker SDK | 12 | 1.5日 |
| M3: Sync 詳細 | 14 | 1.5日 |
| M4: kintone 接続 | 11 | 1日 |
| M5: ライブログ | 11 | 1.5日 |
| M6: ウィザード強化 | 6 | 1日 |
| M7: UX 改善 | 8 | 1日 |
| M8: Docker 配布 | 9 | 1日 |
| M9: ドキュメント | 9 | 1日 |
| **合計** | **90** | **約10日** |

---

## 進捗ルール

1. **TDD 厳守**: 🔴 → 🟢 → 🔵
2. **マイルストーンごとに全テスト緑を維持** (223 件以上)
3. **マイルストーン単位 or 機能単位のコミット**
4. **既知の問題は known-issues.md に記録**
5. **CLAUDE.md の段階承認フローを守る** (このフェーズは設計まで承認済)

---

## 関連ドキュメント

- 要求定義: [requirements.md](./requirements.md)
- 設計書: [design.md](./design.md)
- Phase 2-B: [../20260522-web-ui-and-sqlite/](../20260522-web-ui-and-sqlite/)
- Phase 2-A: [../20260522-multi-table-support/](../20260522-multi-table-support/)
