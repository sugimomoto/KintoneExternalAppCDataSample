# CData Kintone Adapter Console

kintone「外部システムのアプリ化」機能における **Adapter のリファレンス実装**。
**CData JDBC Driver** を介して、Salesforce / Google Sheets / Snowflake など
300+ 種類のデータソースを kintone アプリから直接参照・編集できます。

Web UI から連携 (Sync) を追加するだけで、Adapter (gRPC) と Agent コンテナが
自動的に立ち上がり、kintone と接続されます。

| 項目 | 内容 |
|---|---|
| 実装言語 | Kotlin 2.2.x (JVM 21) |
| サーバ | Ktor 3.x (Web UI / SSE) + gRPC (Netty) |
| データ層 | CData JDBC Driver + HikariCP |
| 設定ストア | SQLite (`config/config.db`) |
| 配布形態 | Docker (推奨) / fat jar |
| ライセンス | Apache License 2.0 |

---

## パートナー各位へ — 本リポジトリの位置づけ

本リポジトリは、パートナー SI が**自社の製品・案件に組み込んで提供する**ことを
想定した**サンプルコード**です。引き継ぐにあたって、以下を前提としてください。

### 提供するもの / しないもの

| | CData | パートナー |
|---|---|---|
| Adapter のソースコード一式 | **提供（Apache 2.0 / AS-IS）** | 受領・改変・自社製品への組込 |
| サンプルの動作保証 | **なし（AS-IS）** | — |
| 案件ごとのカスタマイズ実装 | — | **実施** |
| エンドユーザーへの提供・契約 | — | **実施** |
| 構築・導入・運用・保守 | — | **実施** |
| エンドユーザーからの一次サポート | — | **窓口** |
| CData JDBC Driver の製品サポート | **実施**（ライセンス契約に基づく） | 二次エスカレーション |
| Adapter 実装に関する技術的な質問 | **技術サポート役務として対応**（範囲は別途合意） | 起票 |
| kintone / Agent 本体の不具合 | — | サイボウズへエスカレーション |

- 改変した部分は完全にパートナー側の資産です。CData への還元義務はありません（Apache 2.0）。
- **CData は SI を行いません。** 最終的な実装・顧客提供はパートナー側でお願いします。

### 引き継ぎ時に最初に読む順番

1. **本 README の「クイックスタート」** — まず動かす（30 分程度）
2. **[docs/extending.md](docs/extending.md)** — コードの歩き方と「やりたいこと別」改修ガイド
3. **[docs/feedback-to-cybozu.md](docs/feedback-to-cybozu.md)** — 仕様の癖と、その回避方法の記録
4. **[docs/architecture.md](docs/architecture.md)** — 技術仕様・制約・性能要件

> ⚠️ 本リポジトリは kintone「外部システムのアプリ化」機能に関する
> **サイボウズ社の未公開情報を含みます**。社外開示・二次配布にはご注意ください。

---

## アーキテクチャ

```
┌─ kintone 環境（サイボウズ） ─┐        ┌─ 顧客／パートナー環境 ─────────────┐   ┌─ データソース ─┐
│                              │        │                                    │   │                │
│   ┌────────────────┐         │        │   ┌────────────────┐               │   │  Salesforce    │
│   │ kintone アプリ │         │        │   │     Agent      │               │   │  SAP           │
│   └───────┬────────┘         │        │   │ (サイボウズ提供)│               │   │  Snowflake     │
│           │                  │        │   └───────┬────────┘               │   │  Google Sheets │
│   ┌───────┴────────┐         │  gRPC  │           │ Connect RPC (HTTP/2)   │   │  … 300+        │
│   │   Connector    │◄────────┼────────┼──►┌───────┴────────┐               │   │                │
│   └────────────────┘  HTTPS  │  RSA + │   │    Adapter     │               │   │                │
│                              │   JWT  │   │  (本リポジトリ) │               │   │                │
└──────────────────────────────┘        │   └───────┬────────┘               │   │                │
                                        │           │ in-process             │   │                │
                                        │   ┌───────┴──────────────────┐     │   │                │
                                        │   │ CData JDBC Driver        │◄────┼───┼──► API / HTTPS │
                                        │   │  + HikariCP 接続プール    │     │   │                │
                                        │   └──────────────────────────┘     │   │                │
                                        └────────────────────────────────────┘   └────────────────┘
```

| モジュール | 提供元 | 責務 |
|---|---|---|
| kintone アプリ / Connector | サイボウズ | エンドユーザー操作、イベント送出 |
| **Agent** | サイボウズ（**バイナリを個別受領**） | Connector の gRPC を Connect RPC に変換 |
| **Adapter** | **本リポジトリ** | Connect RPC を受け、JDBC でデータソースを操作 |
| CData JDBC Driver | CData（**要ライセンス**） | SaaS API ⇄ SQL の変換 |

**Adapter は kintone に対して通信を開始しません。** 顧客環境から外へ出るのは
Agent の HTTPS のみで、Adapter は社内ネットワークに閉じられます。

実行時は 1 つの `adapter-console` コンテナが常駐し、連携ごとの Agent コンテナを
Docker socket 経由で動的に生成します。

```
                                                    ┌──────────────────────┐
                                                    │ kintone (cybozu.com) │
                                                    └───────────▲──────────┘
┌─ Host（Docker Desktop / Linux）──────────────────────────────┼──────────┐
│                                                              │ HTTPS    │
│   ┌────────────────────────────┐      ┌──────────────────────┴───────┐  │
│   │  adapter-console           │      │  kintone-agent-A             │  │
│   │   ・Web UI       : 8080    │─────►│  kintone-agent-B             │  │
│   │   ・Adapter gRPC : 18000-  │ dock │  kintone-agent-C             │  │
│   │                    18099   │ .sock│  （連携ごとに 1 コンテナ）    │  │
│   └────────────────────────────┘      └──────────────────────────────┘  │
│    常駐するのはこの 1 つだけ            adapter-console が動的に生成      │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## クイックスタート (Docker / 推奨)

### 必要なもの

- Docker Engine 20.10+ または Docker Desktop 4.20+
- **サイボウズ社から受領した `kintone-data-connector-agent` バイナリ**
  (Linux amd64 / arm64 用。公開レジストリでは配布されていません)
- kintone ワイドコース環境

> 利用する CData JDBC Driver jar は起動後に Web UI からアップロードできるため、
> 事前準備は不要です。トライアル取得は <https://www.cdata.com/jp/jdbc/> から。

### 1. リポジトリ取得と基本ディレクトリ作成

```bash
git clone <repo-url> cdata-kintone-adapter
cd cdata-kintone-adapter

mkdir -p lib config agent run
```

### 2. Agent バイナリの受領と配置

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

### 3. Agent コンテナイメージのビルド

adapter-console は Agent コンテナを `kintone-data-connector-agent:0.9.2`
という名前のイメージから起動します。これは **管理者が同梱の Dockerfile で
ローカルビルド** します (`docker pull` ではありません)。

```bash
docker compose -f agent/docker-compose.yml build
# → kintone-data-connector-agent:0.9.2 が docker images に登録される
```

### 4. 鍵ペアの生成

**鍵ペアは起動後に Web UI から 1 ボタンで生成できる**ため、事前準備は不要です。
公開鍵はそのまま画面でコピー / ダウンロードして kintone に登録します。

> 自前で OpenSSL を使って生成したい場合は次のコマンドも利用できます:
> ```bash
> cd agent
> openssl genrsa 2048 > private-key.pem
> openssl rsa -pubout -in private-key.pem -out public-key.pem
> chmod 600 private-key.pem
> ```

### 5. adapter-console の起動

```bash
cp .env.example .env       # 必要に応じて編集

docker compose build adapter-console
docker compose up -d adapter-console
docker compose logs -f adapter-console
```

ブラウザで <http://localhost:8080> を開き、ダッシュボードが表示されれば成功です。

### 6. Web UI から連携を作成

1. `/drivers` で JDBC Driver jar をアップロード → アクティベート
2. `/connections` でデータソースの JDBC 接続文字列を保存
3. `/syncs/new` で新しい連携を追加 (ウィザード)
4. 連携詳細画面の「kintone と接続」で **「🔑 鍵ペアを生成する」** ボタンを押す
   → `agent/public-key.pem` / `private-key.pem` が生成されます (秘密鍵は画面に出ません)
5. 画面に表示された公開鍵を kintone 管理画面のコネクター登録に貼り付け、トークンを取得
6. 取得したトークンを画面に入力 → Adapter + Agent コンテナが自動起動
7. kintone 側で外部 App としてアプリ作成

詳細は Web UI の `/help` ページ、または [docs/DOCKER-SETUP.md](docs/DOCKER-SETUP.md) を参照。

---

## コードの歩き方

動かせたら、次はコードです。**詳細は [docs/extending.md](docs/extending.md)** にありますが、
ここでは地図だけ示します。

### 本体の心臓部は 4 クラス

kintone の一覧画面で絞り込みをかけたとき、何が起きるか。

```
AdapterServiceImpl.select()          service/AdapterServiceImpl.kt   … 9 RPC の入口
   ├─ FilterTranslator.translate()   filter/FilterTranslator.kt      … 絞り込み → WHERE 句
   ├─ QueryBuilder.buildSelect()     jdbc/QueryBuilder.kt            … SQL 組み立て
   └─ RowMapper.resultSetToRecord()  jdbc/RowMapper.kt               … ResultSet → protobuf
```

これ以外（`web` / `agent` / `cli`）は運用のための外周です。

### パッケージと責務

| パッケージ | 責務 |
|---|---|
| `service` | Connect RPC の入口。9 つの RPC を実装 |
| `filter` | kintone の絞り込み条件（37/39 種）→ SQL の WHERE 句 |
| `jdbc` | SQL 組み立て・実行・結果の変換・接続プール・ドライバ管理 |
| `config` | 設定の読み書き（SQLite） |
| `metadata` | JDBC メタデータから kintone フィールド型を推測 |
| `runtime` | 複数 Adapter の起動・停止・ポート採番 |
| `agent` | Agent コンテナと鍵・トークンの制御 |
| `web` | 管理 Web UI（Ktor + kotlinx.html） |
| `cli` | CLI サブコマンド（Clikt） |

### やりたいこと → 触るファイル

| やりたいこと | 触る場所 | 詳細 |
|---|---|---|
| 別のデータソースに繋ぐ | **コード変更不要**（Web UI から登録） | [R-1](docs/extending.md#r-1) |
| 型マッピングを変える | `metadata/FieldTypeSuggester.kt` `jdbc/RowMapper.kt` | [R-2](docs/extending.md#r-2) |
| 絞り込み条件の変換を直す | `filter/FilterTranslator.kt` | [R-3](docs/extending.md#r-3) |
| SQL 方言に対応する | `jdbc/QueryBuilder.kt` | [R-4](docs/extending.md#r-4) |
| Count を軽くする | 連携設定の `count-strategy`（Web UI） | [R-5](docs/extending.md#r-5) |
| Search / Aggregate を作り込む | `service/AdapterServiceImpl.kt` | [R-6](docs/extending.md#r-6) |
| Web UI に画面を足す | `web/routes/` `web/views/` | [R-8](docs/extending.md#r-8) |
| 設定ストアを差し替える | `config/ConfigSource.kt` の実装を追加 | [R-9](docs/extending.md#r-9) |
| 接続・OAuth まわり | `jdbc/JdbcConnectionProvider.kt` ほか | [R-10](docs/extending.md#r-10) |
| Agent の起動方法を変える | `agent/AgentContainerManager.kt` | [R-11](docs/extending.md#r-11) |

---

## 開発環境

### ホスト Java で起動 (Docker なし)

Docker を使わず JVM 上で直接起動する場合。Web UI の画面確認やデバッグ向けです。

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

### protobuf スキーマについて

**ローカルでコード生成はしていません。** サイボウズが Buf Schema Registry に公開している
生成済みアーティファクトを Maven 依存として取得しています。スキーマを更新する場合は
`build.gradle.kts` 先頭の `bsrCommit` とバージョン定数を差し替えてください
（[docs/extending.md §5](docs/extending.md#proto)）。

リポジトリ直下の `.proto/` は**参照用のコピー**で、ビルドには使われません。

### テスト

```bash
./gradlew test                                   # ユニット（約 235 件、数秒で完了）
./gradlew browserTest                            # Playwright E2E（約 25 件、初回は Chromium を自動DL）
PLAYWRIGHT_HEADLESS=false ./gradlew browserTest   # ヘッドフル（画面を見ながらデバッグ）
./gradlew ktlintCheck detekt                     # 静的解析
./gradlew jacocoTestReport                       # カバレッジ → build/reports/jacoco/test/html/index.html
```

本プロジェクトは TDD ベースで書かれています。改修時は**先にテストを足してから実装**
してください。規約は [docs/development-guidelines.md](docs/development-guidelines.md) §4 を参照。

---

## 設定

設定はすべて **SQLite (`config/config.db`)** に保存されます。設定ファイルを手で編集する
運用はありません。連携の作成・編集は Web UI から行います。

`config.db` が存在しない場合は起動時にスキーマが自動生成され、連携 0 件の状態で立ち上がります。

| 保存されるもの | 内容 |
|---|---|
| 連携 (Sync) | 待ち受けポート・テーブル名・主キー・列と型のマッピング |
| 機能宣言 | 対応 RPC・Count 戦略・主キー型・絞り込み/並び替え可能な列 |
| 共有 JDBC 接続 | ドライバクラス・jar パス・接続文字列・接続プール設定 |

- 共有 JDBC 接続は**複数の連携から参照**されます（`/connections` で管理）
- 接続文字列には `${VAR}` 形式で環境変数を埋め込めます
- **`config.db` は接続文字列＝認証情報を含みます。** `.gitignore` 済みですが、
  バックアップの取り扱いに注意してください

データモデルの詳細は [docs/extending.md §2](docs/extending.md#config) を参照。

> **配置場所を変える場合**は `--sqlite-path` で明示できます。
> 省略時は `<config-dir>/config.db` です。

---

## CLI サブコマンド一覧

主に開発・運用補助用です。**連携の作成・編集は Web UI から行います。**

| サブコマンド | 説明 |
|---|---|
| `web-ui` | ブラウザ管理コンソールを起動 (本体) |
| `serve` | 指定した連携の gRPC サーバを起動（`--table` / `--tables` のいずれか必須） |
| `serve-all` | 登録済みの全連携を 1 JVM で並行起動 |
| `list-active` | 稼働中の Adapter 一覧を `./run/active-adapters.json` から表示 |
| `list-tables` | 接続先データソースのテーブル一覧表示 |
| `test-connection` | JDBC 接続テスト |

共通オプション: `--config-dir`（既定 `./config`）、`--sqlite-path`（既定 `<config-dir>/config.db`）

```bash
java -jar build/libs/adapter-*-all.jar --help
java -jar build/libs/adapter-*-all.jar <subcommand> --help

# 接続テストとテーブル一覧（共有 JDBC 接続を名前で指定）
java -jar build/libs/adapter-*-all.jar test-connection --jdbc-name salesforce
java -jar build/libs/adapter-*-all.jar list-tables     --jdbc-name salesforce

# 連携の起動
java -jar build/libs/adapter-*-all.jar serve --table account
java -jar build/libs/adapter-*-all.jar serve-all
```

`--jdbc-name` は、共有 JDBC 接続の登録が **1 件だけなら省略できます**。
複数ある場合や名前が違う場合は、登録済みの一覧を表示して終了します。

---

## 主な画面 (Web UI)

| 画面 | パス | 内容 |
|---|---|---|
| ダッシュボード | `/` | 稼働状況の KPI と稼働中連携の一覧 (SSE リアルタイム更新) |
| 連携 (Syncs) | `/syncs` | 連携の CRUD・起動/停止・ログ表示。Adapter と Agent の状態を別々に表示 |
| 新規連携ウィザード | `/syncs/new` | 4 ステップで連携を作成 |
| kintone 接続 | `/syncs/{name}/connect` | 公開鍵 + トークン入力 → 1 ボタン全自動接続 |
| ライブログ | `/syncs/{name}/logs` | Adapter + Agent ログを並列表示・SSE ストリーミング |
| データソース | `/connections` | 共有 JDBC 接続文字列 (`sys_connection_props` 動的フォーム) |
| ドライバー | `/drivers` | JDBC jar のアップロード + トライアルアクティベーション |
| ヘルプ | `/help` | エンドユーザー向け操作ガイド |

---

## ディレクトリ構成

```
.
├── src/                       # Kotlin ソース
│   ├── main/                  # 本体 (Web UI / gRPC / Agent 制御 / CLI)
│   ├── test/                  # 単体テスト
│   └── browserTest/           # Playwright E2E
├── .proto/                    # protobuf 定義の参照用コピー（ビルドには不使用）
├── lib/                       # JDBC Driver jar + ライセンス (gitignore)
├── config/                    # 設定ストア
│   └── config.db              # SQLite。連携・共有 JDBC 接続 (gitignore)
├── agent/                     # Agent コンテナ用ファイル群
│   ├── bin/linux_<arch>/      # サイボウズ受領のバイナリ本体 (gitignore)
│   ├── Dockerfile             # Agent コンテナイメージのビルド定義
│   ├── docker-compose.yml     # 単体 Agent 起動 (開発用)
│   ├── public-key.pem         # 公開鍵 (kintone へ登録)
│   ├── private-key.pem        # 秘密鍵 (gitignore)
│   └── tables/{name}/agent.json  # 連携ごとのトークン (gitignore)
├── run/                       # 稼働状態 (active-adapters.json, oauth キャッシュ)
├── docker-compose.yml         # 本体起動
├── Dockerfile                 # adapter-console イメージ
└── docs/                      # ドキュメント（images/ に構成図の SVG）
```

---

## 制約と落とし穴

引き継ぐ前に必ず目を通してください。詳細は
[docs/extending.md §6](docs/extending.md#pitfalls)。

| # | 内容 |
|---|---|
| 1 | **1 テーブル = 1 Adapter = 1 Agent = 1 Connector**（サイボウズ仕様）。テーブルが増えるとセット数も増える |
| 2 | Agent の停止・再起動直後に kintone 側で「セッションが見つかりません」が出る（改善要望提出済み） |
| 3 | 複数連携で **OAuth キャッシュが衝突**する。`JdbcUrlEnhancer` の分離処理を外さないこと |
| 4 | **複合主キー未対応**（主キーは 1 カラムのみ） |
| 5 | Connect RPC のメッセージ上限 **4MB**。大量件数の一括取得には不向き |
| 6 | `GetCapability` で `true` を宣言した RPC は kintone が呼び始める。**実装より先に宣言しない** |
| 7 | **macOS 版 Agent は未提供**。開発時も Docker (Linux コンテナ) 前提 |
| 8 | Gradle 8.x は Java 25 未対応。**JDK 21** を使う |
| 9 | Docker socket のマウントは実質 root 権限。本番では socket proxy 等の隔離を検討 |
| 10 | `Search` / `Aggregate` は基本実装のみ。TLS 終端は未実装（リバースプロキシ前提） |

---

## トラブルシューティング

### 連携を開始すると 500 エラー

`docker logs adapter-console` で次を確認:

- Agent コンテナイメージ (`kintone-data-connector-agent:0.9.2`) が
  `docker images` にローカルビルド済みか
  → 無ければ `docker compose -f agent/docker-compose.yml build`
- `agent/bin/linux_<arch>/kintone-data-connector-agent` が存在し実行権限ありか
- 既存の `kintone-agent-<連携名>` が `Exited` で残っていないか
  → `docker rm kintone-agent-<連携名>`
- `HOST_AGENT_ROOT` 環境変数がホスト側の絶対パスを指しているか

古い Agent コンテナをまとめて掃除する場合:

```bash
docker ps -aq --filter label=com.cdata.adapter.managed=true | xargs -r docker rm -f
```

### kintone で「Adapterが利用できません」(GAIA_AU01)

Agent は kintone に接続できているが、**Agent → Adapter の gRPC が通っていない**状態。
`docker logs kintone-agent-<連携名>` に次が出る。

```
"msg":"failed to call GetCapability","err":"... dial tcp ...:43343: connect: connection refused"
"msg":"failed to handle operation request","err":"adapter is unavailable ..."
```

確認する順番:

1. **Adapter のポートが公開範囲内か**
   連携詳細画面のポートが **18000-18099** の範囲にあるか。
   `0`（auto）になっていると publish 範囲外の ephemeral port を掴むため必ず到達不可。
   → adapter-console を再起動すると `PortMigrator` が自動で範囲内へ移行する
2. **Adapter が起動しているか**
   `docker logs adapter-console` の起動サマリ `Adapter 起動: N 件` を確認
3. **agent.json が最新のポートを指しているか**
   `agent/tables/<連携名>/agent.json` の `adapter_addr` と連携のポートが一致しているか
   → 一致していない場合は Web UI から「接続して開始」をやり直す

### Agent コンテナが停止している / `Restarting` を繰り返す

`docker logs kintone-agent-<連携名>` に `Unauthenticated desc = invalid token` /
`token has been revoked` が出ている場合、接続キーが kintone に拒否されている。
kintone 側で接続キーを再発行し、Web UI の「接続して開始」で入力し直す。

**この状態の Agent は adapter-console が自動で停止する** (Issue #19)。
接続キーの拒否は再試行では直らないため、`restart: unless-stopped` のまま
放置すると 1 分おきに kintone へ失敗リクエストを投げ続けるため。

| タイミング | 動作 |
|---|---|
| 「接続して開始」で拒否されたとき | その場で該当コンテナを停止する |
| adapter-console の起動時 | 直近 3 分のログに認証失敗が出ているコンテナを停止する |

連携一覧 (`/syncs`) の **Agent 列**で状態を確認できる (Issue #20)。

| 表示 | 意味 |
|---|---|
| 稼働中 | Agent が正常に動いている |
| 再起動中（赤） | 起動に失敗し続けている。接続キーの拒否が主な原因 |
| キー拒否（赤） | 接続キーを拒否されて停止した。**接続キーの再発行が必要** |
| 停止中 | コンテナはあるが止まっている（手動停止など） |
| 未作成 | コンテナがまだ作られていない |

連携詳細 (`/syncs/{name}`) では再起動回数も表示する。

「キー拒否」の連携は、詳細画面の警告バナーから「接続キーを再入力する」で
接続画面へ移動できる (Issue #21)。接続画面には kintone 側の操作手順も表示される。
拒否の記録は `run/agent-connection-status.json` に残るため、adapter-console を
再起動しても理由が分かる（接続が成立すると記録は消える）。

停止されるのは**認証失敗の場合だけ**で、接続確認のタイムアウト（Adapter の
起動待ちなど一過性の要因）では停止しない。`AUTO_STOP_AUTH_FAILED_AGENTS=false`
で自動停止を無効化できる。

接続キーの有効期限切れとは限らない点に注意。接続キーは kintone 側の接続インスタンス
に紐づくため、**kintone で接続を作り直すと、署名も有効期限も正常なまま拒否される**。

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

一度認可すれば、その**データソース接続**を使う連携すべてでトークンが共有されます
(Issue #11)。キャッシュは `run/oauth/<データソース接続名>.txt` に保存されます。

以前は接続テストと実行時で保存先が食い違っており、テストで認可しても連携開始時に
再認可が走っていました。修正後は接続テスト・ウィザード・CLI・実行時のすべてが
同じ場所を使います。

> ⚠️ この修正を適用した直後は、**一度だけ再認可が必要**です。旧バージョンの
> キャッシュは `run/oauth/<連携名>.txt` に保存されており、連携名と接続名の対応は
> 一意に決まらないため自動移行していません。

### 画面やログの時刻がずれている

コンテナの既定タイムゾーンは UTC です。`docker-compose.yml` は
`TZ: ${TZ:-Asia/Tokyo}` を渡すため通常は日本時間になりますが、別のタイムゾーンで
運用する場合は環境変数で上書きしてください (Issue #25)。

```bash
TZ=America/New_York docker compose up -d adapter-console
```

- 画面・CLI の時刻表示にはタイムゾーンが併記されます（例: `2026-09-29 15:45:55 JST`）
- **既存の Agent コンテナには反映されません。** Agent コンテナは作成時に `TZ` を
  受け取るため、作り直すまで従来のタイムゾーンのままです
- ライブログ (`/syncs/{name}/logs`) の行頭のタイムスタンプは Docker Engine が
  付けるもので、常に UTC です

### Web UI に変更が反映されない

`docker compose restart` だけでは古い jar のままです。
`docker compose build adapter-console` でイメージを再生成してから `up -d` してください。

そのほか詳細は Web UI の [/help](http://localhost:8080/help) を参照。

---

## ドキュメント

| ドキュメント | 内容 |
|---|---|
| [docs/extending.md](docs/extending.md) | **改修ガイド — 引き継ぐ開発者はここから** |
| [docs/DOCKER-SETUP.md](docs/DOCKER-SETUP.md) | 管理者向け Docker セットアップガイド |
| [docs/architecture.md](docs/architecture.md) | 技術仕様・制約・性能要件 |
| [docs/functional-design.md](docs/functional-design.md) | 機能設計・シーケンス図・クラス図 |
| [docs/product-requirements.md](docs/product-requirements.md) | プロダクト要件・ユーザーストーリー |
| [docs/repository-structure.md](docs/repository-structure.md) | ディレクトリとファイル配置のルール |
| [docs/development-guidelines.md](docs/development-guidelines.md) | コーディング規約・TDD・Git 規約 |
| [docs/glossary.md](docs/glossary.md) | 用語集 (kintone / CData 双方の用語) |
| [docs/E2E-SETUP.md](docs/E2E-SETUP.md) | 実 kintone との結合テスト手順 |
| [docs/feedback-to-cybozu.md](docs/feedback-to-cybozu.md) | サイボウズ社への改善要望と検証知見 |
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
- 本サンプルは **AS-IS** で提供され、動作保証はありません
