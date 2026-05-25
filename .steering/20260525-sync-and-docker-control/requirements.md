# Phase 2-C: Sync 概念導入 + Docker API 制御 — 要求定義

| 項目 | 内容 |
|---|---|
| フェーズ | 2-C |
| 作成日 | 2026-05-25 |
| 前提 | Phase 2-B (v0.3.0-web-ui + Agent token 編集) 完了済み |
| 目的 | エンドユーザーが Adapter/Agent/Port を意識せずに使えるよう、UX を抜本的に作り直す |

---

## 1. 背景 (Phase 2-B のフィードバック)

Phase 2-B 検証中、ユーザーから以下の指摘:
- 「Adapter / Agent / port の概念をエンドユーザーに理解させたくない」
- 「テーブル登録後、token を紐付ける場所がない」（Phase 2-B で前倒し対応済）
- 「Web UI で連携 1 件作るのに 5+ ステップ × 3 つの場所（Web UI / kintone / docker-compose 手動編集）を行き来する」

現状の用語と内部実装が密結合で、技術理解のない運用者には負担が大きい。

## 2. 目的

「**Sync (連携)**」を一級概念にし、Adapter プロセス / Agent コンテナ / port / agent.json /
docker-compose の操作をすべて Web UI 内に隠蔽する。

エンドユーザーが意識すべきこと:
- データソース接続（どこに何があるか）
- どのテーブルを kintone と連携するか
- kintone との接続キー (token)

エンドユーザーが意識しなくてよくなること:
- ❌ Adapter プロセス / Agent コンテナ / port 番号
- ❌ agent.json / docker-compose.yml の編集
- ❌ host.docker.internal や Docker Compose の概念

## 3. スコープ

### 3.1 Phase 2-C で実装する機能

| 大分類 | ID | 内容 | 状態 |
|---|---|---|---|
| **概念統一** | UX-01 | "Tables" → "Syncs" のリネーム（URL / ナビゲーション / 用語） | **2-C** |
| **概念統一** | UX-02 | 詳細表示の "Advanced" 折りたたみ（Adapter / Agent / Port を隠蔽） | **2-C** |
| **概念統一** | UX-03 | エラー文言のユーザー向け翻訳辞書（内部用語 → ビジネス用語） | **2-C** |
| **kintone 統合** | KC-01 | 連携詳細画面に公開鍵セクション（コピー + ダウンロード） | **2-C** |
| **kintone 統合** | KC-02 | kintone 管理画面への直リンク（環境ホスト名入力） | **2-C** |
| **kintone 統合** | KC-03 | 新規ウィザード Step 4 に「kintone とつなぐ」セクション統合（token 入力 + 1 ボタン全自動） | **2-C** |
| **Docker 制御** | DK-01 | Docker SDK for Java を導入し、Agent コンテナを動的 create/start/stop/remove | **2-C** |
| **Docker 制御** | DK-02 | docker-compose.multi.yml の自動編集をやめて Docker Engine API 直接呼び出し | **2-C** |
| **Docker 制御** | DK-03 | Web UI に「Sync 開始 / 停止 / 再起動」ボタン（Adapter + Agent を 1 操作で） | **2-C** |
| **Docker 制御** | DK-04 | Agent コンテナのヘルス状態を Web UI に表示（SSE で更新） | **2-C** |
| **ライブログ** | LG-01 | Adapter プロセスログをブラウザにストリーミング表示（既存ロガーから） | **2-C** |
| **ライブログ** | LG-02 | Agent コンテナのログを Docker API 経由でストリーミング表示 | **2-C** |
| **ライブログ** | LG-03 | ログ画面の検索・フィルタ（INFO/WARN/ERROR） | **2-C** |
| **ウィザード強化** | WZ-01 | ウィザードでの jdbc-ref 選択分岐（既存共通 JDBC を参照、新規 inline JDBC、を選択可） | **2-C** |
| **ウィザード強化** | WZ-02 | Sync 作成後、即座に kintone 接続ステップへ自動遷移 | **2-C** |
| **配布** | DP-01 | adapter-console を Docker イメージ化（Dockerfile + マルチステージビルド） | **2-C** |
| **配布** | DP-02 | プロジェクトルートに `docker-compose.yml` 完成版（adapter-console コンテナ起動） | **2-C** |

合計 17 機能。

### 3.2 Phase 2-C スコープ外（次フェーズ送り）

| ID | 内容 | フェーズ |
|---|---|---|
| AT-01 | Basic 認証（Web UI のログイン） | 2-D |
| AT-02 | OIDC / SAML | 2-D |
| AT-03 | ロールベース権限 | 2-D |
| MN-01 | RPC 呼び出し統計（件数・レイテンシ） | 2-D |
| MN-02 | Adapter プロセスのリソース使用量 | 2-D |
| MN-03 | エラー監視・通知 | 2-D |
| SC-01 | Docker socket proxy で権限隔離 | 2-D |
| SC-02 | rootless Docker / Sysbox 対応 | 2-D |
| AU-01 | 監査ログ | 2-D |
| AU-02 | 多人数同時編集ロック | 2-D |
| HC-01 | Helm Chart / Kubernetes Manifest | Phase 3 |
| ML-01 | マルチホスト分散運用 | Phase 3 |
| LC-01 | CData JDBC ライセンスのコンテナ対応（machine ID 課題） | 別途 CData 相談 |

## 4. 用語マッピング (UX-01 詳細)

| 内部用語 (現状) | エンドユーザー向け (Phase 2-C) | コード上の名前 (内部維持) |
|---|---|---|
| Table / TableConfig | **Sync** | `TableConfigSet` |
| Tables 画面 / `/tables` | **Syncs** / `/syncs` | (URL リダイレクト併用) |
| Adapter プロセス | **Sync の稼働状態** | `TableAdapterServer` |
| Agent コンテナ | **kintone との接続** | `AgentContainer` (新概念) |
| Adapter port | (隠蔽、Advanced のみ) | `port` |
| JDBC Connection | **データソース接続** | `JdbcConfig` |
| Driver | **データソースの種類** | `JdbcDriverInfo` |
| Connector token | **kintone 接続キー** | `AgentConfig.token` |
| agent.json | (隠蔽、内部ファイル) | `agent.json` |
| docker-compose.yml | (隠蔽、内部) | (廃止予定) |

## 5. 機能要件詳細

### 5.1 Sync 詳細画面の刷新 (UX-02 + KC-01 + KC-02 + DK-04)

```
┌─ Sync: GoogleAccount ───────────────────────────────────────┐
│  ●動作中  (Salesforce → kintone)                            │
│  [ 一時停止 ] [ 再起動 ] [ 削除 ]                            │
│                                                             │
│  ── データソース ──                                         │
│  Google Sheets / CRM Data sugimotok_Account                 │
│  [ データソース接続 を見る ]                                │
│                                                             │
│  ── kintone 接続 ──                                         │
│  ●接続中  (last check: 2 秒前)                              │
│  公開鍵: [ コピー ] [ ダウンロード ]                        │
│  接続キー: ●設定済み  [ 更新 ]                              │
│  kintone 管理画面: [ 開く ↗ ]                               │
│                                                             │
│  ── 同期フィールド (6 個) ──                                │
│  id ↔ Id (Primary) / name ↔ Name (TEXT) / ...               │
│  [ マッピングを編集 ]                                       │
│                                                             │
│  ── アクティビティ ──                                       │
│  [ ログを見る ]                                             │
│                                                             │
│  ▶ 詳細 (上級者向け)                                        │
│      - Adapter port: 18004                                  │
│      - Agent container: kintone-agent-GoogleAccount         │
│      - agent.json: ./agent/tables/GoogleAccount/agent.json  │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 「kintone とつなぐ」ワンクリック (KC-03)

```
┌─ kintone と接続する ────────────────────────────────────────┐
│                                                             │
│  Step 1: 公開鍵を kintone に登録                            │
│     [ 公開鍵をコピー ]   または  [ ダウンロード ]           │
│     [ kintone 管理画面を開く ↗ ]                            │
│                                                             │
│  Step 2: 発行された接続キーを入力                           │
│     [______________________________________________]       │
│                                                             │
│  Step 3: 接続を確立                                         │
│     [ 接続して開始 ]                                        │
│                                                             │
│  ⓘ 「接続して開始」を押すと、内部で以下が自動実行されます: │
│     - Sync 用のサーバ起動                                   │
│     - kintone 接続キーの保存                                │
│     - Agent コンテナの作成・起動                            │
│     - kintone との疎通確認                                  │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Docker SDK 統合 (DK-01 〜 DK-04)

- 依存追加: `com.github.docker-java:docker-java-core` + `docker-java-transport-httpclient5`
- `/var/run/docker.sock` 経由で Docker Engine API を呼ぶ
- `AgentContainerManager` クラスで CRUD:
  - `create(syncName, token, adapterAddr)`: コンテナ作成
  - `start(syncName)` / `stop(syncName)` / `remove(syncName)`: ライフサイクル
  - `health(syncName)`: 稼働状態
  - `logs(syncName, since, limit)`: ログ取得 (LG-02 用)
- docker-compose.multi.yml の手動編集を廃止し、すべて API 経由

### 5.4 ライブログ (LG-01 〜 LG-03)

- Web UI 内で `/syncs/<name>/logs` を開くとライブテール表示
- Adapter ログ: Logback の `Appender` をプログラム的に登録し、SSE で配信
- Agent ログ: Docker API `containerLogs(since, follow)` を SSE で中継
- レベルフィルタ + 検索（クライアントサイド JavaScript）

### 5.5 ウィザード強化 (WZ-01 + WZ-02)

- Step 1 (Connection) で「既存共通 JDBC を使う」と「個別 JDBC URL を直接書く」を選択
- 既存共通 JDBC を選んだ場合は `jdbc-ref` で保存される（現状は常に inline JDBC）
- Step 4 終了後、Sync は作成されるが「未接続」状態 → 自動的に「kintone とつなぐ」画面に遷移

### 5.6 Docker 配布 (DP-01 + DP-02)

```yaml
# プロジェクトルートの docker-compose.yml (完成版)
services:
  adapter-console:
    build:
      context: .
      dockerfile: Dockerfile
    image: cdata-kintone-adapter:0.4.0
    container_name: adapter-console
    ports:
      - "8080:8080"
      - "18000-18099:18000-18099"   # Sync 用ポート範囲
    volumes:
      - ./config:/app/config
      - ./lib:/app/lib
      - ./agent:/app/agent
      - ./run:/app/run
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      CONFIG_SOURCE: ${CONFIG_SOURCE:-yaml}
    restart: unless-stopped
```

ユーザー操作:
```bash
docker compose up -d adapter-console
# → http://localhost:8080 で Web UI
```

Agent コンテナは Web UI から動的に追加されるので compose ファイルに記述不要。

## 6. 非機能要件

| ID | 種別 | 内容 |
|---|---|---|
| NFR-01 | 性能 | Web UI 主要画面 < 200ms (LAN) |
| NFR-02 | 性能 | Docker API 操作 (start/stop) < 2 秒 |
| NFR-03 | 信頼性 | adapter-console コンテナ停止時に Agent コンテナはそのまま残す（再起動時に Web UI が再認識） |
| NFR-04 | 互換 | Phase 2-B の既存 yaml/sqlite 設定はそのまま動く |
| NFR-05 | テスト | 223 件全件緑のまま維持 |
| NFR-06 | セキュリティ | Docker socket マウントは Phase 2-C ではそのまま、注意書きを README に追記 |
| NFR-07 | 配布 | `docker compose up -d` でゼロから起動可能 |

## 7. 受け入れシナリオ

| ID | シナリオ |
|---|---|
| S-01 | エンドユーザーが新規 Sync 作成 → 「kintone とつなぐ」で token 貼り付け → 1 ボタンで Adapter + Agent 起動 → kintone でアプリ作成 |
| S-02 | Sync 一覧で「停止」ボタン → Adapter + Agent コンテナ両方停止 |
| S-03 | Sync 詳細でログ画面を開く → Adapter / Agent ログがライブテール |
| S-04 | Sync 削除 → Adapter 停止 + Agent コンテナ削除 + 設定削除 |
| S-05 | `docker compose up -d adapter-console` でゼロから起動、既存 Sync が自動復元 |
| S-06 | エラー時に「kintone との接続が切れています [再接続]」が表示（ユーザー向け文言） |
| S-07 | 「上級者向け詳細」を展開すると port / container 名が見える |

## 8. 関連

- 前フェーズ要求: [../20260522-web-ui-and-sqlite/requirements.md](../20260522-web-ui-and-sqlite/requirements.md)
- 前フェーズ設計: [../20260522-web-ui-and-sqlite/design.md](../20260522-web-ui-and-sqlite/design.md)
- Phase 2-A: [../20260522-multi-table-support/](../20260522-multi-table-support/)
