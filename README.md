# kintone External App CData Adapter Sample

kintone「外部システムのアプリ化」機能における **Adapter のリファレンス実装**。
**CData JDBC Driver** を介して、Salesforce 等の SaaS データを kintone アプリから直接参照・編集できます。

| 項目 | 内容 |
|---|---|
| 実装言語 | Kotlin 2.2.x |
| サーバ | gRPC (Netty) on JVM 21 |
| データ層 | CData JDBC Driver + HikariCP |
| ライセンス | Apache License 2.0 |

## クイックスタート（Salesforce で 30 分以内）

### 必要なもの

- Java 21 LTS（`brew install openjdk@21`）
- CData JDBC Driver for Salesforce（[CData サイト](https://www.cdata.com/jp/jdbc/) からトライアル取得可能）
- kintone ワイドコース環境（「外部システムのアプリ化」機能の利用権限）
- Salesforce 環境（Developer Edition で OK）
- サイボウズ提供の Agent バイナリ（`kintone-data-connector-agent`）
- grpcurl（任意、動作確認用）：`brew install grpcurl`

### 1. ビルド

```bash
# JAVA_HOME を Java 21 に向ける（Gradle 8.x が Java 25 未対応のため）
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

# Fat JAR をビルド
./gradlew shadowJar
# → build/libs/adapter-0.1.0-SNAPSHOT-all.jar
```

### 2. CData JDBC Driver の配置

```bash
mkdir -p lib
# CData JDBC Driver for Salesforce をダウンロード・解凍し、以下に配置：
cp /path/to/cdata.jdbc.salesforce.jar lib/
cp /path/to/cdata.jdbc.salesforce.lic lib/  # ライセンスキー
```

> ⚠️ **重要**: `lib/*.jar` は fat jar に同梱されず、実行時に動的ロードされます（`compileOnly` 依存）。
> これは CData JDBC Driver のライセンスファイル（`.lic`）が JAR と同じディレクトリに存在することを要求するためです。

### 3. 設定ファイル準備

```bash
mkdir -p config
cp config/server.yaml.example config/server.yaml
cp config/jdbc.yaml.example config/jdbc.yaml
cp config/capability.yaml.example config/capability.yaml
```

`config/jdbc.yaml` を編集し、**Salesforce の OAuth 接続文字列** を設定します：

```yaml
driver-class: cdata.jdbc.salesforce.SalesforceDriver
driver-jar: ./lib/cdata.jdbc.salesforce.jar
url: "jdbc:salesforce:AuthScheme=OAuth;InitiateOAuth=GETANDREFRESH;LoginURL=https://YOUR_ORG.my.salesforce.com/;OAuthSettingsLocation=./lib/cdata-oauth-salesforce.txt;"
```

**接続文字列の重要パラメータ**：

| パラメータ | 説明 |
|---|---|
| `AuthScheme=OAuth` | OAuth 認証を選択（推奨。User/Password 認証は `AuthScheme=Basic`） |
| `InitiateOAuth=GETANDREFRESH` | 初回ブラウザ認証 → トークン自動更新 |
| `LoginURL` | Salesforce の My Domain URL（Developer Edition なら `https://orgfarm-xxx.develop.my.salesforce.com/`）|
| `OAuthSettingsLocation` | リフレッシュトークン保存先（gitignore済み） |

> 💡 **環境変数展開**: `${VAR_NAME}` を YAML 内に書けば実行時の環境変数で展開されます。

### 4. table.yaml の自動生成（init-table）

```bash
# 対話式生成（多数のテーブルから選択）
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar init-table

# 特定テーブルを直接指定（Salesforce 等のように 1,000+ テーブルがある環境向け）
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar init-table --table Account --non-interactive

# → config/table.yaml が生成される
```

**初回 OAuth 認証**: `init-table` または `test-connection` 実行時にブラウザが自動で開き、Salesforce にログインして「許可」ボタンを押すとトークンが `lib/cdata-oauth-salesforce.txt` にキャッシュされます。以降は自動再認証。

### 5. 接続テストと起動

```bash
# JDBC 接続確認
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar test-connection

# テーブル一覧確認
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar list-tables

# Adapter サーバ起動
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar serve
# → port 8083 で待ち受け
```

### 6. grpcurl で動作確認

gRPC server reflection を有効化済みなので、**proto ファイル不要** で grpcurl から叩けます。

```bash
brew install grpcurl

# サービス一覧
grpcurl -plaintext localhost:8083 list

# ヘルスチェック
grpcurl -plaintext -d '{}' localhost:8083 grpc.health.v1.Health/Check
# → {"status": "SERVING"}

# Capability 確認
grpcurl -plaintext -d '{}' localhost:8083 cybozu.data_connector.adapter.v1.AdapterService/GetCapability

# Select (Salesforce Account 5件取得)
grpcurl -plaintext -d '{
  "payload": {
    "fields": ["id", "name"],
    "filterConditions": [{"allRecords": {}}],
    "matchOperator": "MATCH_OPERATOR_ALL",
    "sortConditions": [],
    "offset": "0",
    "limit": "5"
  }
}' localhost:8083 cybozu.data_connector.adapter.v1.AdapterService/Select
```

### 7. kintone Agent と接続

詳細は [docs/E2E-SETUP.md](docs/E2E-SETUP.md) 参照。要点：

1. `openssl genrsa 2048 > private-key.pem` で鍵生成
2. `openssl rsa -pubout -in private-key.pem -out public-key.pem`
3. kintone 管理画面の「外部システムコネクター管理」で公開鍵を登録、トークン取得
4. `agent.json` に トークン + `"adapter_addr": "localhost:8083"` + `"adapter_plaintext": true` を設定
5. Agent バイナリを起動
6. kintone 側でコネクター接続 → アプリ作成

macOS では Agent バイナリが提供されていないため、Docker Linux コンテナで動かす方法を [docs/AGENT-DOCKER.md](docs/AGENT-DOCKER.md) に記載。

## Docker での Adapter 起動

```bash
# .env を作成して環境変数を設定（OAuth 利用なら主に未設定でOK、Basic 利用時のみ必要）
cat > .env <<EOF
SF_USER=your_user
SF_PASSWORD=your_password
SF_SECURITY_TOKEN=your_token
EOF

# 起動
docker compose up -d

# ログ確認
docker compose logs -f adapter
```

## CLI サブコマンド一覧

| サブコマンド | 説明 | 主なオプション |
|---|---|---|
| `serve` | 個別テーブルの gRPC サーバを起動 | `--config-dir <dir>`, `--table <name>`, `--tables a,b` |
| `serve-all` | `config/tables/` 配下の全テーブルを 1 JVM で並行起動（フェーズ2-A） | `--config-dir <dir>` |
| `list-active` | 稼働中の Adapter 一覧を `./run/active-adapters.json` から表示 | `--state-file <file>` |
| `migrate-config` | フェーズ1 構成（config 直下の YAML）を `config/tables/default/` に移行 | `--config-dir <dir>`, `--target <name>` |
| `init-table` | 対話式 `table.yaml` 生成（`--name` でフェーズ2-A 構成、`--jdbc-ref` で共通 jdbc 参照） | `--jdbc-config`, `--name`, `--jdbc-ref`, `--non-interactive`, `--table <name>` |
| `list-tables` | 接続先データソースのテーブル一覧表示 | `--jdbc-config <file>` |
| `test-connection` | JDBC 接続テスト | `--jdbc-config <file>` |

`--help` で詳細表示可：

```bash
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar --help
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar init-table --help
```

## 設定ファイル

### フェーズ1（シングルテーブル、既存）

4つの YAML ファイルを `config/` 直下に置く。後方互換のため引き続き動作する。

| ファイル | 内容 | Git 管理推奨 |
|---|---|---|
| `config/server.yaml` | サーバの待ち受けポート等 | ✅ |
| `config/jdbc.yaml` | JDBC 接続情報（機密含む） | ❌ `.gitignore` |
| `config/table.yaml` | テーブル名・カラム定義（`init-table` で自動生成可） | △（環境依存） |
| `config/capability.yaml` | サポート機能宣言・record-id-type | ✅ |

### フェーズ2-A（マルチテーブル、推奨）

```
config/
├── jdbc/                          # 複数テーブルで共有する JDBC 設定
│   ├── salesforce.yaml            # 機密: .gitignore
│   ├── salesforce.yaml.example    # コミット可
│   └── googlesheets.yaml.example
└── tables/
    ├── account/                   # テーブル単位のサブディレクトリ
    │   ├── server.yaml            # port: 0 (auto) 推奨
    │   ├── jdbc-ref.yaml          # name: salesforce (共通設定参照)
    │   ├── table.yaml             # init-table で生成
    │   └── capability.yaml
    ├── contact/
    │   └── …
    └── googlesheets-orders/
        ├── jdbc-ref.yaml          # name: googlesheets
        └── …
```

ポイント:
- 各テーブルは独立した gRPC サーバとして起動（1 テーブル ↔ 1 Connector ↔ 1 Agent ↔ 1 kintone アプリ）
- 共通 JDBC 設定は `config/jdbc/<name>.yaml` に置き、各テーブルから `jdbc-ref.yaml` で参照
- ドライバーが異なる場合は `jdbc-ref` ではなくテーブル内に個別 `jdbc.yaml` を置く（同一 JVM 内で別 URLClassLoader 経由でロード）

#### フェーズ1 → フェーズ2-A への移行

```bash
java -jar build/libs/adapter-*-all.jar migrate-config --config-dir ./config
# → config/server.yaml 等が config/tables/default/ に移動
```

### 他データソースへの切替・混在運用

CData JDBC Driver の jar を `lib/` に追加し、対応する `config/jdbc/<name>.yaml` を作成すれば、1 JVM 内で 250+ 種類のデータソースを混在運用できます（Google Sheets / SAP / Snowflake / Oracle SaaS 等）。OAuth キャッシュは `./run/oauth/<tableName>.txt` にテーブル別で自動分離されます。

例: Salesforce 3 テーブル + Google Sheets 1 シート を 1 JVM で稼働させたい場合：

```bash
# 1. 共通設定を作成（example をコピー）
cp config/jdbc/salesforce.yaml.example config/jdbc/salesforce.yaml
cp config/jdbc/googlesheets.yaml.example config/jdbc/googlesheets.yaml

# 2. テーブル設定を init-table で生成
java -jar build/libs/adapter-*-all.jar init-table \
  --jdbc-config config/jdbc/salesforce.yaml \
  --name account --jdbc-ref salesforce --table Account --non-interactive

# 3. serve-all で並行起動
java -jar build/libs/adapter-*-all.jar serve-all
```

## ヘルスチェック

gRPC 標準の `grpc.health.v1.Health` サービスを実装。Kubernetes liveness/readiness probe 等に利用可：

```bash
grpcurl -plaintext -d '{}' localhost:8083 grpc.health.v1.Health/Check
# → {"status": "SERVING"}

# AdapterService 単体のヘルス
grpcurl -plaintext -d '{"service": "cybozu.data_connector.adapter.v1.AdapterService"}' \
  localhost:8083 grpc.health.v1.Health/Check
```

## トラブルシュート

### `What went wrong: 25.0.2` などの Java バージョンエラー

Gradle 8.x が Java 25 に未対応。Java 21 LTS を使ってください：

```bash
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

### CData JDBC Driver のライセンスエラー

```bash
cd lib
java -jar cdata.jdbc.salesforce.jar -l
# 名前 → メール → "TRIAL" の順で入力（順番注意）
```

### `OAUTH [50001] タイムアウトしました`

OAuth 初回認証で 60 秒以内にブラウザでログイン・許可しなかった場合に発生。
**ターミナルから直接実行**してください（バックグラウンド実行ではブラウザ起動が確認できないため）：

```bash
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar test-connection
# → ブラウザが開く → ログイン → 「許可」クリック
```

### `OAUTH [30004] Login failed: OAuthAccessToken is required`

`AuthScheme` が指定されていないか OAuth キャッシュ未取得。`AuthScheme=OAuth;InitiateOAuth=GETANDREFRESH;` を URL に追加。

### `INVALID_LOGIN` (User/Password 認証時)

- セキュリティトークンが古い → Salesforce で再発行（パスワード変更時もリセット）
- IP 制限・ロックアウト
- Sandbox の場合は `LoginURL=https://test.salesforce.com;` を追加

### `port already in use`

`config/server.yaml` の `port` を変更するか、既存プロセスを停止：

```bash
lsof -ti:8083 | xargs kill
```

### kintone Agent から接続できない

- Agent の `agent.json` で `"adapter_plaintext": true` になっているか確認
- Adapter が `0.0.0.0:8083` で待ち受けているか確認（`config/server.yaml` の `bind-address`）
- Agent と Adapter が同じネットワーク上にあるか確認

## アーキテクチャ

```
┌─────────────┐    gRPC    ┌─────────┐  gRPC over HTTP/2  ┌──────────┐  JDBC  ┌──────────┐
│   kintone   │ ────────► │  Agent  │ ─────────────────► │ Adapter  │ ─────► │  CData   │
│  Connector  │           │ (cybozu  │                    │ (本実装) │        │   JDBC   │
└─────────────┘           │ provided)│                    └──────────┘        │  Driver  │
                          └─────────┘                                          └─────┬────┘
                                                                                     │
                                                                                ┌────▼─────┐
                                                                                │ データソース │
                                                                                │(Salesforce│
                                                                                │  etc.)   │
                                                                                └──────────┘
```

詳細は [docs/](docs/) 配下を参照。

## 動作確認済み機能（実 Salesforce）

| 機能 | RPC | 確認内容 |
|---|---|---|
| 認証 | - | OAuth (`AuthScheme=OAuth`) + LoginURL |
| ケイパビリティ | GetCapability | record-id-type=TEXT、filterable/sortable fields |
| スキーマ | GetSchema | Account 68カラムの定義返却 |
| 件数取得 | Count | ACTUAL 戦略・フィルター付き両方 |
| 一覧取得 | Select | フィールド絞込・filter（textContains）・sort・pagination |
| 追加 | Insert | 単発・一括、TEXT ID 返却 |
| 更新 | Update | 部分更新、recordIdEqual で WHERE |
| 削除 | Delete | 単発・一括 |
| ヘルスチェック | grpc.health.v1.Health | SERVING 状態返却 |

## 開発

- 開発ガイドライン（**TDD ベース**）: [docs/development-guidelines.md](docs/development-guidelines.md)
- 機能設計: [docs/functional-design.md](docs/functional-design.md)
- 技術仕様: [docs/architecture.md](docs/architecture.md)
- リポジトリ構成: [docs/repository-structure.md](docs/repository-structure.md)
- 用語集: [docs/glossary.md](docs/glossary.md)

### テスト実行

```bash
./gradlew test
# JaCoCo カバレッジレポート: build/reports/jacoco/test/html/index.html
```

### 静的解析

```bash
./gradlew ktlintCheck detekt
```

## ライセンス

[Apache License 2.0](LICENSE)

## 注意事項

- 本リポジトリは kintone の「外部システムのアプリ化」機能のサンプル実装です。**サイボウズの未公開情報を含む**ため、社外開示には注意してください
- CData JDBC Driver は**別途ライセンス購入**が必要です（トライアルあり）
- 本サンプルは AS-IS で提供され、動作保証はありません
