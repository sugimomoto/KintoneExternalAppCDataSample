# CData Kintone Adapter Console

kintone「外部システムのアプリ化」機能における **Adapter のリファレンス実装**。
**CData JDBC Driver** を介して、Salesforce / Google Sheets / Snowflake など
250+ 種類のデータソースを kintone アプリから直接参照・編集できます。

Web UI から連携 (Sync) を追加するだけで、Adapter (gRPC) と Agent コンテナが
自動的に立ち上がり、kintone と接続されます。

| 項目 | 内容 |
|---|---|
| 実装言語 | Kotlin 2.2.x (JVM 21) |
| サーバ | Ktor 3.x (Web UI / SSE) + gRPC (Netty) |
| データ層 | CData JDBC Driver + HikariCP |
| 設定ストア | YAML or SQLite (切替可) |
| 配布形態 | Docker (推奨) / fat jar |
| ライセンス | Apache License 2.0 |

---

## アーキテクチャ

```
┌─────────────────────────────────────────────────────────────────┐
│  Host (Linux / macOS / Windows + Docker Desktop)                │
│                                                                 │
│   ┌──────────────────────┐                                     │
│   │  adapter-console     │  ← Web UI (8080) + Adapter (gRPC)   │
│   │  (このサービス本体)   │     ports 18000-18099              │
│   └──────────┬───────────┘                                     │
│              │ docker.sock                                      │
│              ▼                                                  │
│   ┌──────────────────────┐  ┌──────────────────────┐           │
│   │ kintone-agent-{sync} │  │ kintone-agent-{sync} │  ...      │
│   │ (連携ごとに動的生成)  │  │                       │           │
│   └──────────┬───────────┘  └──────────┬───────────┘           │
└──────────────┼─────────────────────────┼───────────────────────┘
               │ HTTPS                   │ HTTPS
               ▼                         ▼
            kintone (cybozu.com)
```

- **adapter-console**: Web UI + Adapter (gRPC) を同居させた本体コンテナ
- **kintone-agent-{sync}**: 連携ごとに adapter-console が動的に作成・起動する
  サイドカーコンテナ。kintone との通信を担当
- Docker socket (`/var/run/docker.sock`) を adapter-console にマウントして
  Agent コンテナの制御を行う

---

## クイックスタート (Docker / 推奨)

### 必要なもの

- Docker Engine 20.10+ または Docker Desktop 4.20+
- **サイボウズ社から受領した `kintone-data-connector-agent` バイナリ**
  (Linux amd64 / arm64 用。公開レジストリでは配布されていません)
- 利用したい CData JDBC Driver の jar
  (トライアル取得: <https://www.cdata.com/jp/jdbc/>)
- kintone ワイドコース環境

### 1. リポジトリ取得と基本ディレクトリ作成

```bash
git clone <repo-url> cdata-kintone-adapter
cd cdata-kintone-adapter

mkdir -p lib config agent run
```

### 2. JDBC Driver の配置

利用するデータソースの JDBC Driver jar を `lib/` に置きます。

```bash
cp /path/to/cdata.jdbc.salesforce.jar lib/
# トライアルライセンスがあれば lib/*.lic / *.txt も同梱
```

ライセンス (トライアル含む) のアクティベーションは起動後に Web UI の
`/drivers` 画面から実行できます。

### 3. Agent バイナリの受領と配置

`kintone-data-connector-agent` は **サイボウズ社から個別に受領するプログラム本体**
です。受領した tar.gz を展開し、対応アーキのバイナリを配置してください。

```bash
mkdir -p agent/bin/linux_amd64 agent/bin/linux_arm64

tar -xzf kintone-data-connector-agent_v0.9.2_linux_amd64.tar.gz \
    -C agent/bin/linux_amd64 --strip-components=1
tar -xzf kintone-data-connector-agent_v0.9.2_linux_arm64.tar.gz \
    -C agent/bin/linux_arm64 --strip-components=1

chmod +x agent/bin/linux_*/kintone-data-connector-agent
```

### 4. Agent コンテナイメージのビルド

adapter-console は Agent コンテナを `kintone-data-connector-agent:0.9.2`
という名前のイメージから起動します。これは **管理者が同梱の Dockerfile で
ローカルビルド** します (`docker pull` ではありません)。

```bash
docker compose -f agent/docker-compose.yml build
# → kintone-data-connector-agent:0.9.2 が docker images に登録される
```

### 5. 鍵ペア生成と kintone 登録

```bash
cd agent
openssl genrsa 2048 > private-key.pem
openssl rsa -pubout -in private-key.pem -out public-key.pem
chmod 600 private-key.pem
cd ..
```

`public-key.pem` の内容を kintone 管理画面の「外部システムコネクター管理」
に登録し、発行された JWT トークンを後で Web UI から保存します。

### 6. adapter-console の起動

```bash
cp .env.example .env       # 必要に応じて編集

docker compose build adapter-console
docker compose up -d adapter-console
docker compose logs -f adapter-console
```

ブラウザで <http://localhost:8080> を開き、ダッシュボードが表示されれば成功です。

### 7. Web UI から連携を作成

1. `/drivers` でドライバーをアクティベート
2. `/connections` でデータソースの JDBC 接続文字列を保存
3. `/syncs/new` で新しい連携を追加 (ウィザード)
4. 連携詳細画面の「kintone と接続」で公開鍵 + トークンを設定 → Adapter + Agent が自動起動
5. kintone 側で外部 App としてアプリ作成

詳細は Web UI の `/help` ページ、または [docs/DOCKER-SETUP.md](docs/DOCKER-SETUP.md) を参照。

---

## 開発者向け: ホスト Java で起動 (Docker なし)

Docker を使わず JVM 上で直接起動する場合。

### 必要なもの

- Java 21 LTS (`brew install openjdk@21`)
- Gradle (リポジトリ同梱の wrapper を使う)

### ビルドと起動

```bash
# JDK 21 を指定 (Gradle 8.x が Java 25 未対応のため)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew shadowJar
# → build/libs/adapter-0.1.0-SNAPSHOT-all.jar

java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar web-ui --port 8080
```

> このモードでは Docker socket が無いため Agent コンテナの動的制御は無効化され、
> `agent/docker-compose.multi.yml` を手動で扱う必要があります。

---

## CLI サブコマンド一覧

主に開発・運用補助用。通常運用は Web UI から完結します。

| サブコマンド | 説明 |
|---|---|
| `web-ui` | ブラウザ管理コンソールを起動 (本体) |
| `serve` | 単一テーブルの gRPC サーバを起動 |
| `serve-all` | `config/tables/` 配下の全テーブルを 1 JVM で並行起動 |
| `list-active` | 稼働中の Adapter 一覧を `./run/active-adapters.json` から表示 |
| `init-table` | 対話式 `table.yaml` 生成 (`--non-interactive` で自動化) |
| `list-tables` | 接続先データソースのテーブル一覧表示 |
| `test-connection` | JDBC 接続テスト |
| `migrate-to-sqlite` | YAML 設定を SQLite (`config/config.db`) に一括移行 |
| `export-yaml` | SQLite データベースを YAML に書き出し (バックアップ) |
| `migrate-config` / `migrate-to-multi-table` | レガシー単一テーブル → マルチテーブル構成への移行 |

```bash
java -jar build/libs/adapter-*-all.jar --help
java -jar build/libs/adapter-*-all.jar <subcommand> --help
```

---

## 主な画面 (Web UI)

| 画面 | パス | 内容 |
|---|---|---|
| ダッシュボード | `/` | 稼働状況の KPI と稼働中連携の一覧 (SSE リアルタイム更新) |
| 連携 (Syncs) | `/syncs` | 連携の CRUD・起動/停止・ログ表示 |
| 新規連携ウィザード | `/syncs/new` | 4 ステップで連携を作成 |
| kintone 接続 | `/syncs/{name}/connect` | 公開鍵 + トークン入力 → 1 ボタン全自動接続 |
| ライブログ | `/syncs/{name}/logs` | Adapter + Agent ログを並列表示・SSE ストリーミング |
| データソース | `/connections` | 共有 JDBC 接続文字列 (`sys_connection_props` 動的フォーム) |
| ドライバー | `/drivers` | JDBC jar のアップロード + トライアルアクティベーション |
| ヘルプ | `/help` | エンドユーザー向け操作ガイド |

---

## 設定ストアの切替 (YAML / SQLite)

デフォルトは YAML。多数の連携を扱う場合は SQLite に移行可能。

```bash
# YAML → SQLite に一括移行
java -jar build/libs/adapter-*-all.jar migrate-to-sqlite

# 起動時に CONFIG_SOURCE=sqlite を指定 (または config/config.db が存在すれば自動検出)
CONFIG_SOURCE=sqlite java -jar build/libs/adapter-*-all.jar web-ui
```

`config.db` を YAML に書き戻すバックアップ:

```bash
java -jar build/libs/adapter-*-all.jar export-yaml --out-dir ./config-backup
```

---

## ディレクトリ構成

```
.
├── src/                       # Kotlin ソース
│   ├── main/                  # 本体 (Web UI / gRPC / Agent 制御 / CLI)
│   ├── test/                  # 単体テスト (231 件)
│   └── browserTest/           # Playwright E2E (23 件)
├── lib/                       # JDBC Driver jar + ライセンス (gitignore)
├── config/                    # 連携設定 (YAML or config.db)
│   ├── jdbc/                  # 共有 JDBC 設定
│   └── tables/                # 連携 (Sync) ごとの設定
├── agent/                     # Agent コンテナ用ファイル群
│   ├── bin/linux_<arch>/      # サイボウズ受領のバイナリ本体 (gitignore)
│   ├── Dockerfile             # Agent コンテナイメージのビルド定義
│   ├── docker-compose.yml     # 単体 Agent 起動 (開発用)
│   ├── public-key.pem         # 公開鍵 (kintone へ登録)
│   ├── private-key.pem        # 秘密鍵 (gitignore)
│   └── tables/{name}/agent.json  # 連携ごとのトークン (gitignore)
├── run/                       # 稼働状態 (active-adapters.json 等)
├── docker-compose.yml         # 本体起動
├── Dockerfile                 # adapter-console イメージ
└── docs/                      # ドキュメント
```

---

## トラブルシューティング

### 連携を開始すると 500 エラー

`docker logs adapter-console` で次を確認:

- Agent コンテナイメージ (`kintone-data-connector-agent:0.9.2`) が
  `docker images` にローカルビルド済みか
  → 無ければ `docker compose -f agent/docker-compose.yml build`
- `agent/bin/linux_<arch>/kintone-data-connector-agent` が存在し実行権限ありか
- `HOST_AGENT_ROOT` 環境変数がホスト側の絶対パスを指しているか

### `What went wrong: 25.0.2` などの Java バージョンエラー

Gradle 8.x が Java 25 未対応。JDK 21 を使う:

```bash
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

### CData JDBC Driver のライセンスエラー

```bash
cd lib && java -jar cdata.jdbc.salesforce.jar -license
# 名前 → メールアドレス → "TRIAL" の順で入力
```

### `OAUTH [50001] タイムアウト`

OAuth 初回認証で 60 秒以内にブラウザでログインしなかった場合。
`test-connection` をターミナルから直接実行し、ブラウザで認証してください。

### ドライバーアップロード時に画面が真っ白

過去のバグ。最新版で修正済みなので `./gradlew shadowJar` で再ビルドしてください。

そのほか詳細は Web UI の [/help](http://localhost:8080/help) を参照。

---

## テスト

```bash
# 単体テスト (231 件、数秒で完了)
./gradlew test
# JaCoCo: build/reports/jacoco/test/html/index.html

# Playwright E2E (23 件、初回は Chromium ~150MB を自動 DL)
./gradlew browserTest

# ヘッドフル (画面を見ながらデバッグ)
PLAYWRIGHT_HEADLESS=false ./gradlew browserTest

# 静的解析
./gradlew ktlintCheck detekt
```

---

## ドキュメント

| ドキュメント | 内容 |
|---|---|
| [docs/DOCKER-SETUP.md](docs/DOCKER-SETUP.md) | **管理者向け Docker セットアップガイド** |
| [docs/architecture.md](docs/architecture.md) | アーキテクチャ全体像 |
| [docs/functional-design.md](docs/functional-design.md) | 機能設計 |
| [docs/product-requirements.md](docs/product-requirements.md) | プロダクト要件 |
| [docs/development-guidelines.md](docs/development-guidelines.md) | 開発ガイドライン (TDD ベース) |
| [docs/repository-structure.md](docs/repository-structure.md) | リポジトリ構成 |
| [docs/glossary.md](docs/glossary.md) | 用語集 |
| [docs/E2E-SETUP.md](docs/E2E-SETUP.md) | 実 kintone との結合テスト手順 |
| [agent/README.md](agent/README.md) | Agent コンテナ単体起動 (開発用) |
| Web UI `/help` | エンドユーザー向け操作ガイド |

---

## ライセンス

[Apache License 2.0](LICENSE)

## 注意事項

- 本リポジトリは kintone「外部システムのアプリ化」機能のサンプル実装です。
  **サイボウズの未公開情報を含む**ため、社外開示には注意してください
- CData JDBC Driver は**別途ライセンス購入が必要**です (トライアルあり)
- `kintone-data-connector-agent` は**サイボウズから個別に受領する必要があります**
  (本リポジトリには同梱されていません)
- 本サンプルは AS-IS で提供され、動作保証はありません
