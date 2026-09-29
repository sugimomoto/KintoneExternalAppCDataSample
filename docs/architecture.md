# 技術仕様書（Architecture Document）

| 項目 | 内容 |
|---|---|
| プロダクト名 | kintone External App CData Adapter Sample |
| バージョン | 1.0（フェーズ1） |
| 作成日 | 2026-05-15 |
| ステータス | ドラフト |

---

## 1. テクノロジースタック

### 1.1 ランタイム

| 項目 | バージョン | 備考 |
|---|---|---|
| JVM | **Java 21 LTS** | 最低 Java 17、推奨 Java 21 |
| Kotlin | **1.9.x 以上** | Kotlin 2.0 にも追従可 |

### 1.2 サーバ／RPC

| 項目 | バージョン | 用途 |
|---|---|---|
| **Ktor** | 2.3 以上 | HTTP/2 サーバ実装 |
| **connect-kotlin** | 0.5 以上 | Connect プロトコル ハンドラ |
| `protobuf-kotlin` | 3.25 以上 | protobuf ランタイム |
| `kotlinx-coroutines-core` | 1.7 以上 | 非同期処理 |

### 1.3 データソース層

| 項目 | バージョン | 用途 |
|---|---|---|
| **CData JDBC Driver** | 2025J 以上 | データソース接続（Salesforce 等） |
| **HikariCP** | 5.x | JDBC 接続プール |
| `java.sql` API | JDK 標準 | JDBC 抽象化 |

### 1.4 設定・CLI

| 項目 | バージョン | 用途 |
|---|---|---|
| **Clikt** | 4.x | CLI サブコマンド処理 |
| **kotlinx-serialization** | 1.6 以上 | データクラス ⇄ JSON 変換（SQLite の JSON カラム） |

### 1.5 ロギング・テスト

| 項目 | バージョン | 用途 |
|---|---|---|
| **SLF4J + Logback** | 2.x | ロギング |
| **JUnit 5** | 5.10 以上 | ユニットテスト |
| **MockK** | 1.13 以上 | モックライブラリ |
| **Testcontainers** | 1.19 以上 | 統合テスト（PostgreSQL 等） |

### 1.6 ビルド

| 項目 | バージョン | 用途 |
|---|---|---|
| **Gradle** | 8.x | ビルドツール |
| **Kotlin DSL** | - | ビルド定義（`build.gradle.kts`） |
| **Shadow Plugin** | 8.x | Fat JAR 生成 |
| **buf Gradle Plugin** | 0.10 以上 | protobuf → Kotlin コード生成 |
| **ktlint Gradle Plugin** | 12.x | 静的解析 |
| **detekt Gradle Plugin** | 1.23 以上 | 静的解析 |

---

## 2. 開発ツールと手法

### 2.1 開発環境

| 項目 | 推奨 |
|---|---|
| IDE | IntelliJ IDEA Community / Ultimate（Kotlin 対応最良） |
| JDK 管理 | SDKMAN! または mise |
| バージョン管理 | Git |
| protobuf スキーマ管理 | buf CLI（`brew install bufbuild/buf/buf`） |
| API テスト | curl, grpcurl |

### 2.2 開発フロー（TDD ベース）

**本プロジェクトは TDD（テスト駆動開発）で進める。** Red → Green → Refactor サイクルに従い、テストを先に書く。

#### 標準的な実装サイクル

1. **🔴 Red**: 次の振る舞いに対する失敗するテストを書く（`./gradlew test --tests <クラス>` で Red を確認）
2. **🟢 Green**: テストを通す最小限の実装を書く（`./gradlew test` で Green を確認）
3. **🔵 Refactor**: 構造を改善（命名・抽象化・重複排除）。テストは緑のまま
4. コミット境界：`test: ...` → `feat: ...` → `refactor: ...` を意識

#### 開発時の主要コマンド

```bash
# Red → Green の反復
./gradlew test --tests "com.cdata.kintone.adapter.filter.FilterTranslatorTest"

# 全テスト + 静的解析
./gradlew test ktlintCheck detekt

# カバレッジレポート
./gradlew jacocoTestReport

# Adapter 起動（手動 E2E 確認）
./gradlew run --args="serve"
# または: java -jar build/libs/adapter-all.jar serve

# curl で動作確認
./scripts/test-adapter.sh GetCapability

# Docker ビルド
docker build -t cdata-kintone-adapter .
```

#### TDD で推奨する実装順序

純粋関数 → 副作用ありの順。詳細は [対応方針 §11.1](../.steering/20260515-initial-implementation/approach-draft.md)：

1. `FieldTypeSuggester`（JDBC型 → kintone型推奨）
2. `FilterTranslator`（37 種 FilterCondition → SQL）
3. `QueryBuilder`（動的 SQL 組立）
4. `RowMapper`（ResultSet ⇄ Record）
5. `JdbcMetadataInspector`（メタデータラッパ）
6. `AdapterServiceImpl`（9 RPC 実装）
7. `InitTableCommand` 他 CLI

#### TDD 上の補足

- CData JDBC Driver のヘルプ参照は `search-cdata-help` スキル経由で随時実施
- 統合テストは Testcontainers の PostgreSQL を基本、実 Salesforce 検証は別途スクリプト
- TDD の詳細ガイドラインは [development-guidelines.md §4](development-guidelines.md) 参照

### 2.3 protobuf コード生成

```bash
./gradlew bufGenerate
# または
buf generate
```

生成物は `src/main/kotlin/com/cdata/kintone/adapter/proto/` 配下（buildディレクトリ管理）。Gitには **含めない**。

### 2.4 コード品質ツール

| ツール | 用途 | 設定ファイル |
|---|---|---|
| ktlint | コードスタイル統一 | `.editorconfig` |
| detekt | 静的解析・複雑度 | `detekt.yml` |
| JUnit 5 | テスト実行 | - |
| Gradle | ビルド・依存解決 | `build.gradle.kts` |

---

## 3. 技術的制約と要件

### 3.1 プロトコル制約

| 制約 | 内容 |
|---|---|
| Connect RPC バージョン | v1 |
| HTTP バージョン | HTTP/2 必須 |
| メッセージサイズ上限 | 4 MB（Connect デフォルト、必要なら設定変更） |
| ペイロード形式 | protobuf バイナリ + JSON（両対応） |
| protobuf スキーマ互換性 | `cybozu.data_connector.adapter.v1` に厳密準拠 |

### 3.2 データソース制約

| 制約 | 内容 |
|---|---|
| 対応 JDBC Driver | CData JDBC Driver 製品群のみ |
| 非対応 | ネイティブ JDBC（Oracle JDBC, MySQL Connector/J 等） |
| 主キー | 1カラムのみ。複合主キーは未対応 |
| 主キー型 | BIGINT/INTEGER（NUMBER） または VARCHAR/CHAR（TEXT）のみ |
| 対応列数 | 制限なし（kintone 側の制限に従う） |

### 3.3 ライセンス制約

| 項目 | 内容 |
|---|---|
| プロジェクト本体 | Apache License 2.0 |
| CData JDBC Driver | 別途ライセンス購入が必要（トライアル可） |
| 依存OSS | 各ライセンスに従う（互換性は detekt の `licensee` プラグイン等で検査） |

### 3.4 セキュリティ制約

| 項目 | 内容 |
|---|---|
| TLS | Adapter ↔ Agent 間は plaintext モード対応（Agent 仕様）。本番では TLS 推奨 |
| 認証情報 | 接続文字列は `${VAR}` による環境変数展開を推奨。`config.db` は gitignore 必須 |
| SQL インジェクション | PreparedStatement 必須（全 SQL 動的組立箇所） |
| プロセス分離 | Adapter は外部 DB 接続用のネットワークアクセスのみ。kintone 側への通信は不要 |
| ログ | 接続文字列・APIトークン等の機密情報はマスキング |

### 3.5 デプロイ制約

| 項目 | 内容 |
|---|---|
| 動作プラットフォーム | Linux / macOS / Windows（Java 17 以上） |
| Docker サポート | linux/amd64, linux/arm64 |
| 単一テーブル制約 | 1 Adapter プロセス = 1 テーブル（kintone 仕様） |
| マルチテーブル | 別ポートで別プロセス起動（フェーズ1 ではユーザー自身で管理） |
| メモリ | 最小 512MB、推奨 1GB（接続プール・JVM ヒープ含む） |

---

## 4. パフォーマンス要件

### 4.1 応答時間目標

| 操作 | 目標値 | 条件 |
|---|---|---|
| GetCapability | < 100ms | 静的応答 |
| GetSchema | < 200ms | 静的応答 |
| Select (1件取得) | < 500ms | Salesforce 標準オブジェクト |
| Select (500件) | < 3秒 | Salesforce 標準オブジェクト |
| Insert (1件) | < 1秒 | Salesforce 標準オブジェクト |
| Update (1件) | < 1秒 | Salesforce 標準オブジェクト |
| Delete (10件) | < 2秒 | Salesforce 標準オブジェクト |
| Count (ACTUAL) | < 5秒 | データソース依存。重い場合は `ALWAYS_ZERO` |

### 4.2 スループット目標

| 項目 | 目標値 |
|---|---|
| 同時接続数 | 10 並列リクエストで安定動作 |
| 1分あたりのリクエスト処理数 | 60 RPC/分以上 |
| メモリ使用量（アイドル時） | 512MB 以下 |
| メモリ使用量（負荷時） | 1GB 以下 |

### 4.3 リソース上限

| リソース | 上限 |
|---|---|
| HikariCP 接続プールサイズ | デフォルト 10、設定可能 |
| 接続待機タイムアウト | デフォルト 30秒、設定可能 |
| Select の limit | protobuf により最大 501（kintone 側仕様） |
| Search の limit | protobuf により最大 100 |
| Aggregate の limit | protobuf により最大 10001 |

---

## 5. 信頼性・可用性

### 5.1 障害分類と対応

| 障害種別 | 対応 |
|---|---|
| 設定ファイル誤り | 起動時に detect し、明確なメッセージで停止 |
| JDBC Driver ロード失敗 | 起動時に detect し、停止 |
| データソース接続失敗（一時的） | HikariCP のリトライに任せる。連続失敗時は `UNAVAILABLE` を返す |
| SQL 例外 | `INTERNAL` を返す、トランザクションは Rollback |
| OOM | 制御不能。Docker の OOMKiller に任せる |

### 5.2 監視

フェーズ1 では最小限：

- 標準出力ログ（JSON 形式推奨、後段ツールで集約可）
- ヘルスチェックエンドポイント `/health`（HTTP 200 を返すのみ）

将来：Prometheus メトリクス、OpenTelemetry トレース等を検討。

---

## 6. 拡張性

### 6.1 想定される拡張ポイント

| 拡張対象 | アプローチ |
|---|---|
| 別の CData JDBC Driver | 設定ファイル変更のみで可能 |
| 別の RPC 実装言語 | proto ファイル経由で他言語に移植可能（buf generate） |
| 認証強化 | Ktor のミドルウェア層で追加 |
| メトリクス計測 | Connect の Interceptor で実装 |
| ホットリロード | フェーズ2 で検討 |

### 6.2 フェーズ2-A で導入したプロセスモデル（A: 1 JVM マルチサーバ）

```
            ┌──────────── 1 JVM Process ────────────┐
            │                                       │
            │   MultiAdapterRunner                  │
            │   ├─ TableAdapterServer "account"     │  ← gRPC port: 18001
            │   ├─ TableAdapterServer "contact"     │  ← gRPC port: 18002
            │   └─ TableAdapterServer "orders"      │  ← gRPC port: 18003
            │                                       │
            │   状態は ./run/active-adapters.json   │
            └───────────────────────────────────────┘
                   ↑              ↑              ↑
              Agent(acc)     Agent(con)     Agent(ord)   ← Docker コンテナ×N
                   ↑              ↑              ↑
                            kintone Connector × N
```

- 各 `TableAdapterServer` は独立した `HealthStatusManager` と JDBC プールを持つ
- 共有 JDBC 設定は SQLite の `shared_jdbcs` テーブルに置き、連携から名前で参照する
- 異なるドライバー（Salesforce + Google Sheets）の同居は URLClassLoader 隔離で実現

### 6.3 フェーズ2-B 以降で見据える拡張

- 複数テーブル管理 UI（Web UI）
- ~~SQLite ベースの `ConfigSource` 実装~~（実装済み。YAML は廃止して SQLite に一本化）
- マルチプロセス制御（Adapter / Agent をまとめて起動・停止）
- TLS 終端
- CData Connect AI 連携（gRPC プロキシ経由）

---

## 7. 依存ライブラリ一覧（暫定）

`build.gradle.kts` の `dependencies` ブロックに記載される想定：

```kotlin
dependencies {
    // Connect RPC
    implementation("com.connectrpc:connect-kotlin:0.5.0")
    implementation("com.connectrpc:connect-kotlin-google-java-ext:0.5.0")
    implementation("com.google.protobuf:protobuf-kotlin:3.25.1")

    // Ktor server
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")

    // CData JDBC（ローカル参照）
    implementation(files("lib/cdata.jdbc.salesforce.jar"))

    // 接続プール
    implementation("com.zaxxer:HikariCP:5.1.0")

    // 設定のシリアライズ（SQLite の JSON カラム）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")

    // CLI
    implementation("com.github.ajalt.clikt:clikt:4.2.1")

    // ロギング
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // テスト
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}
```

---

## 8. 関連ドキュメント

- プロダクト要求：[product-requirements.md](product-requirements.md)
- 機能設計：[functional-design.md](functional-design.md)
- リポジトリ構造：[repository-structure.md](repository-structure.md)
- 開発ガイドライン：[development-guidelines.md](development-guidelines.md)
- ユビキタス言語：[glossary.md](glossary.md)
