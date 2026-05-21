# リポジトリ構造定義書（Repository Structure）

| 項目 | 内容 |
|---|---|
| プロダクト名 | kintone External App CData Adapter Sample |
| バージョン | 1.0（フェーズ1） |
| 作成日 | 2026-05-15 |
| ステータス | ドラフト |

---

## 1. 全体構成

```
KintoneExternalAppCDataSample/
├ CLAUDE.md                        # Claude Code 向けプロジェクトメモリ
├ README.md                        # ユーザー向け説明・クイックスタート
├ LICENSE                          # Apache License 2.0
├ .gitignore
├ .editorconfig                    # ktlint 用エディタ設定
├ build.gradle.kts                 # Gradle ビルド定義（Kotlin DSL）
├ settings.gradle.kts
├ gradle.properties
├ gradle/
│  └ wrapper/                      # Gradle Wrapper
├ buf.yaml                         # Buf モジュール定義
├ buf.gen.yaml                     # コード生成設定
├ Dockerfile                       # Docker イメージビルド
├ docker-compose.yml               # ローカル動作確認用
│
├ docs/                            # 永続的ドキュメント
│  ├ product-requirements.md
│  ├ functional-design.md
│  ├ architecture.md
│  ├ repository-structure.md       # 本ファイル
│  ├ development-guidelines.md
│  └ glossary.md
│
├ .steering/                       # 作業単位ドキュメント
│  └ 20260515-initial-implementation/
│     ├ approach-draft.md
│     ├ requirements.md
│     ├ design.md
│     └ tasklist.md
│
├ .claude/                         # Claude Code 向けナレッジ
│  ├ settings.local.json           # （任意）ローカル設定
│  └ skills/
│     └ kintone-external-app-spec/ # kintone Adapter 仕様参照スキル
│        ├ SKILL.md
│        └ reference/
│           ├ 01-architecture.md
│           ├ 02-api-surface.md
│           ├ 03-field-types.md
│           ├ 04-filter-conditions.md
│           ├ 05-constraints.md
│           ├ 06-sample-adapter-anatomy.md
│           ├ 07-sdk-options.md
│           ├ 08-jdbc-mapping.md
│           └ 09-curl-test-snippets.md
│
├ config/                          # 設定ファイル群（4ファイル分割）
│  ├ server.yaml                   # サーバ設定
│  ├ jdbc.yaml.example             # JDBC接続設定の雛形（実体は gitignore）
│  ├ table.yaml.example            # Salesforce 用テーブル定義例
│  └ capability.yaml               # サポート機能宣言
│
├ lib/                             # CData JDBC Driver 配置
│  ├ cdata.jdbc.salesforce.jar     # ※ gitignore
│  ├ cdata.jdbc.salesforce.lic     # ※ gitignore
│  └ help/                         # ヘルプドキュメント（参考用、gitignore）
│
├ reference/                       # 開発時の参考資料（gitignore 推奨）
│  ├ db-connector-sample/          # サイボウズ提供のサンプル Adapter
│  ├ kintone-data-connector/       # protobuf スキーマ（ローカルコピー）
│  ├ kintone-data-connector-agent_v0.9.2_*/  # Agent バイナリ
│  └ *.pdf                         # 公式マニュアル類
│
├ src/
│  ├ main/
│  │  ├ kotlin/
│  │  │  └ com/cdata/kintone/adapter/
│  │  │     ├ Application.kt            # エントリポイント・サブコマンドディスパッチ
│  │  │     ├ cli/
│  │  │     │  ├ ServeCommand.kt        # serve サブコマンド
│  │  │     │  ├ InitTableCommand.kt    # init-table 対話式 CLI
│  │  │     │  ├ ListTablesCommand.kt
│  │  │     │  └ TestConnectionCommand.kt
│  │  │     ├ config/
│  │  │     │  ├ AdapterConfig.kt       # 統合設定モデル
│  │  │     │  ├ ServerConfig.kt
│  │  │     │  ├ JdbcConfig.kt
│  │  │     │  ├ TableConfig.kt
│  │  │     │  ├ CapabilityConfig.kt
│  │  │     │  ├ ConfigLoader.kt        # 4ファイル YAML 読込・統合
│  │  │     │  └ TableConfigWriter.kt   # init-table の出力先
│  │  │     ├ service/
│  │  │     │  └ AdapterServiceImpl.kt  # 9 RPC 実装
│  │  │     ├ jdbc/
│  │  │     │  ├ JdbcConnectionProvider.kt   # HikariCP ラッパ
│  │  │     │  ├ QueryBuilder.kt             # SQL 動的組立
│  │  │     │  ├ RowMapper.kt                # ResultSet ⇄ Record
│  │  │     │  └ TypeMapper.kt               # JDBC型 ⇄ kintone型
│  │  │     ├ filter/
│  │  │     │  ├ FilterTranslator.kt    # 39 FilterCondition → WHERE 句
│  │  │     │  └ WhereClause.kt
│  │  │     ├ metadata/
│  │  │     │  ├ JdbcMetadataInspector.kt    # DatabaseMetaData ラッパ
│  │  │     │  └ FieldTypeSuggester.kt       # JDBC型→kintone型推奨ロジック
│  │  │     └ (proto/)                  # buf generate 出力先（build時生成、git管理外）
│  │  └ resources/
│  │     └ logback.xml              # ロギング設定
│  └ test/
│     └ kotlin/
│        └ com/cdata/kintone/adapter/
│           ├ filter/
│           │  └ FilterTranslatorTest.kt    # 39 case のテスト
│           ├ jdbc/
│           │  ├ QueryBuilderTest.kt
│           │  └ RowMapperTest.kt
│           ├ service/
│           │  └ AdapterServiceImplIntegrationTest.kt  # Testcontainers 利用
│           └ metadata/
│              └ JdbcMetadataInspectorTest.kt
│
└ scripts/                         # 開発支援スクリプト
   ├ test-adapter.sh              # curl で各 RPC をテスト
   ├ start-agent.sh               # Agent をローカル起動
   └ generate-keypair.sh          # 認証用鍵ペア生成
```

---

## 2. ディレクトリの役割

| ディレクトリ | 役割 | Git管理 |
|---|---|---|
| `docs/` | 永続的ドキュメント。基本設計が変わらない限り更新しない | ◯ |
| `.steering/` | 作業単位ドキュメント。作業ごとにディレクトリを切る | ◯ |
| `.claude/skills/` | Claude Code 向け仕様参照スキル | ◯ |
| `config/` | 4ファイル分割の設定ファイル群 | 一部（例ファイルのみ） |
| `lib/` | CData JDBC Driver 関連（ライセンス含む） | × |
| `reference/` | 開発時の参考資料・サンプル・公式マニュアル | × |
| `src/main/kotlin/` | プロダクションコード | ◯ |
| `src/test/kotlin/` | テストコード | ◯ |
| `scripts/` | 開発・運用支援シェルスクリプト | ◯ |

---

## 3. ファイル配置ルール

### 3.1 Kotlin パッケージ命名

- ルートパッケージ：`com.cdata.kintone.adapter`
- 層ごとにサブパッケージ：`cli`, `config`, `service`, `jdbc`, `filter`, `metadata`, `proto`
- protobuf 生成コードは `com.cdata.kintone.adapter.proto` だが、実際の protobuf パッケージ名（`cybozu.data_connector.adapter.v1`）に従う独立階層になる

### 3.2 設定ファイル

- すべて `config/` 配下
- ファイル名は **固定**：`server.yaml` / `jdbc.yaml` / `table.yaml` / `capability.yaml`
- `*.example` で雛形を提供（実ファイルは gitignore）

### 3.3 リソースファイル

- `src/main/resources/`：実行時に classpath から読まれるリソース（`logback.xml` 等）
- 設定ファイル（`config/*.yaml`）は外部ファイルとして起動時に読み込み

### 3.4 テスト

- ユニットテスト：`src/test/kotlin/com/cdata/kintone/adapter/<層名>/` に対応コードと同じ階層
- 統合テスト：`src/test/kotlin/com/cdata/kintone/adapter/integration/` 配下、Testcontainers を利用
- テストファイル名規約：`<対象クラス名>Test.kt`

### 3.5 ドキュメント

- 永続的ドキュメントは `docs/` に固定ファイル名で配置
- 作業単位ドキュメントは `.steering/[YYYYMMDD]-[タイトル]/` 配下
- ドキュメント内で他ドキュメントを参照する際は相対パス Markdown リンクを使用

---

## 4. .gitignore 方針

以下を git 管理外とする：

```gitignore
# Gradle / Kotlin
.gradle/
build/
*.iml
.idea/

# CData JDBC Driver（ライセンス・JARファイル）
lib/*.jar
lib/*.lic
lib/help/

# 設定ファイル（機密含むもの）
config/jdbc.yaml
config/table.yaml
# 例ファイルは管理対象
!config/*.example

# 参考資料
reference/

# OS
.DS_Store

# Logs
*.log
```

---

## 5. Docker 関連ファイル配置

### 5.1 Dockerfile

ルート直下。マルチステージビルド：

```dockerfile
# ステージ1: Gradle ビルド
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle shadowJar

# ステージ2: 実行
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/adapter-all.jar /app/adapter.jar
COPY lib/cdata.jdbc.salesforce.jar /app/lib/
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/adapter.jar", "serve"]
```

### 5.2 docker-compose.yml

ローカル動作確認用。Adapter のみ起動（Agent 等は別途）：

```yaml
services:
  adapter:
    build: .
    ports:
      - "8083:8083"
    volumes:
      - ./config:/app/config
      - ./lib:/app/lib:ro
    env_file:
      - .env
```

---

## 6. 命名規則

| 種別 | 規則 | 例 |
|---|---|---|
| ファイル名（Kotlin） | UpperCamelCase | `FilterTranslator.kt` |
| パッケージ名 | lowercase | `com.cdata.kintone.adapter.filter` |
| クラス名 | UpperCamelCase | `JdbcMetadataInspector` |
| 関数名・プロパティ名 | lowerCamelCase | `getFilterableFields()` |
| 定数 | UPPER_SNAKE_CASE | `DEFAULT_POOL_SIZE` |
| YAML キー | kebab-case | `kintone-field-id`, `count-strategy` |
| サブコマンド | kebab-case | `init-table`, `test-connection` |
| 環境変数 | UPPER_SNAKE_CASE | `ADAPTER_CONFIG_DIR`, `SF_USER` |

---

## 7. 関連ドキュメント

- プロダクト要求：[product-requirements.md](product-requirements.md)
- 機能設計：[functional-design.md](functional-design.md)
- 技術仕様：[architecture.md](architecture.md)
- 開発ガイドライン：[development-guidelines.md](development-guidelines.md)
- ユビキタス言語：[glossary.md](glossary.md)
