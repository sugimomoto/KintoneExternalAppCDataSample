# 改修ガイド（Extending Guide）

本サンプルを**引き継いで自社案件に適用する開発者**向けのガイドです。
「動かす」までは [README.md](../README.md)、「何をどう作ったか」は
[functional-design.md](functional-design.md) を参照してください。
本書はその中間、**「やりたいことに対して、どのファイルを触ればよいか」** を扱います。

---

## 1. コードの歩き方

### 1.1 パッケージと責務

すべて `src/main/kotlin/com/cdata/kintone/adapter/` 配下です。
上から順に読むと、外側（プロトコル）から内側（SQL）へ降りていきます。

| パッケージ | 責務 | 主要クラス |
|---|---|---|
| `service` | **Connect RPC の入口**。9 つの RPC を実装 | `AdapterServiceImpl` |
| `filter` | kintone の絞り込み条件 → SQL の WHERE 句 | `FilterTranslator` / `WhereClause` |
| `jdbc` | SQL 組み立て・実行・結果の変換 | `QueryBuilder` / `RowMapper` / `JdbcConnectionProvider` |
| `config` | 設定の読み書き（SQLite） | `ConfigSource` / `ConfigStore` / `Config` |
| `metadata` | JDBC メタデータから型を推測 | `JdbcMetadataInspector` / `FieldTypeSuggester` |
| `runtime` | 複数 Adapter の起動・停止・ポート採番 | `MultiAdapterRunner` / `TableAdapterServer` |
| `agent` | Agent コンテナと鍵・トークンの制御 | `AgentContainerManager` / `KeyPairGeneratorService` |
| `web` | 管理 Web UI（Ktor） | `WebUiServer` / `routes/` / `views/` |
| `cli` | CLI サブコマンド（Clikt） | `WebUiCommand` / `ServeCommand` ほか |
| `error` | 例外 → kintone 向けエラーメッセージ | `ErrorMessageTranslator` |
| `log` | Web UI へログを流す Appender | `WebLogAppender` |

> **最初に読む 3 ファイル**
> 1. `service/AdapterServiceImpl.kt` — Adapter が何を求められているかが全部ここにある
> 2. `filter/FilterTranslator.kt` — 実装量が一番多く、一番よく直す場所
> 3. `config/Config.kt` — 設定ファイルのデータモデル

### 1.2 Select リクエストが通る道

kintone の一覧画面で絞り込みをかけたとき、何が起きるか。

```
kintone Connector
   ↓ gRPC
Agent（サイボウズ提供バイナリ）
   ↓ Connect RPC / HTTP2
AdapterServiceImpl.select()                     service/AdapterServiceImpl.kt
   ├─ FilterTranslator.translate()              filter/FilterTranslator.kt
   │     FilterCondition（37 種）→ WhereClause（SQL 断片 + バインド値）
   ├─ QueryBuilder.buildSelect()                jdbc/QueryBuilder.kt
   │     SELECT 列 FROM テーブル WHERE … ORDER BY … LIMIT … OFFSET …
   ├─ ConnectionProvider.getConnection()        jdbc/JdbcConnectionProvider.kt
   │     HikariCP プールから JDBC 接続を借りる
   ├─ PreparedStatement 実行
   └─ RowMapper.resultSetToRecord()             jdbc/RowMapper.kt
         ResultSet → protobuf Record（6 フィールド型へ変換）
```

Insert / Update / Delete / Count も同じ並びです。**この 4 クラスが本体の心臓部**で、
それ以外（web / agent / cli）は運用のための外周です。

### 1.3 起動が通る道

```
Application.kt（Clikt のルート）
   └─ WebUiCommand              cli/WebUiCommand.kt
        └─ WebUiServer          web/WebUiServer.kt        … Ktor で 8080 を待ち受け
             └─ AppContext      web/AppContext.kt         … 依存の組み立て場所
                  └─ MultiAdapterRunner  runtime/MultiAdapterRunner.kt
                       └─ TableAdapterServer × N          … 連携ごとに gRPC サーバ
                            └─ AdapterServiceImpl
```

`AppContext` が実質の DI コンテナです。**差し替えたい実装があればここを見る**のが早道です。

---

<a id="config"></a>

## 2. 設定のデータ構造

設定はすべて **SQLite (`config/config.db`)** に保存されます。テーブルは 3 つだけです
（`config/SqliteSchema.kt`）。

| テーブル | 内容 |
|---|---|
| `shared_jdbcs` | 共有 JDBC 接続。複数の連携から参照される |
| `tables` | 連携 (Sync) ごとの設定。列定義などは JSON カラムに格納 |
| `schema_meta` | スキーマバージョン |

`config.db` が無い場合は `SqliteConfigSource` の初期化でスキーマが自動生成されるため、
空の状態からそのまま起動できます（WAL モード / `foreign_keys=ON`）。

### 2.1 ドメインモデル

SQLite の行は以下の Kotlin データクラス（`config/Config.kt`）にマッピングされます。
JSON カラムの中身は `@Serializable` なクラスをそのままシリアライズしたものです。

| クラス | 内容 |
|---|---|
| `ServerConfig` | 待ち受けポート（`0` または `"auto"` で自動採番）・bind アドレス・plaintext |
| `TableConfig` | テーブル名・主キー (`PrimaryKeyConfig`)・列定義 (`ColumnConfig`) |
| `CapabilityConfig` | 対応 RPC の宣言・Count 戦略・主キー型・絞り込み/並び替え可能な列 |
| `JdbcConfig` | ドライバクラス・jar パス・接続文字列・接続プール (`PoolConfig`) |

この 4 つをまとめたものが `TableConfigSet`（= `AdapterConfig` の typealias）で、
`ConfigSource.loadTableSet(name)` が返す単位です。

```kotlin
// 列定義の例
ColumnConfig(
    kintoneFieldId = "name",   // kintone 側の field_id
    jdbcColumn = "Name",       // データソース側の列名
    type = ColumnType.TEXT,    // TEXT / NUMBER / DATETIME / SELECTION
    options = null,            // SELECTION のときだけ選択肢を持つ
)
```

### 2.2 機能宣言 (`CapabilityConfig`)

| フィールド | 意味 |
|---|---|
| `selectSupported` ほか | 各 RPC を kintone に対して「対応している」と宣言するか |
| `countStrategy` | `ACTUAL`（`SELECT COUNT(*)`）または `ALWAYS_ZERO`（件数を返さない） |
| `recordIdType` | `TEXT`（Salesforce の 18 桁 ID 等）または `NUMBER` |
| `filterableFields` / `sortableFields` | kintone UI で絞り込み・並び替えの選択肢に出す field_id |

### 2.3 環境変数の展開

接続文字列には `${VAR}` 形式で環境変数を埋め込めます
（`config/EnvVarExpander.kt`）。解決できない変数はプレースホルダのまま残るため、
設定ミスが握りつぶされることはありません。

> **`config.db` は接続文字列＝認証情報を含みます。** `.gitignore` 済みですが、
> バックアップやコンテナイメージへの混入に注意してください。

---

## 3. レシピ集 — やりたいこと別

<a id="r-1"></a>

### R-1. 別のデータソースに繋ぎたい

**コード変更は不要です。** Web UI（`/drivers` → `/connections` → `/syncs/new`）で完結します。

1. `/drivers` で CData JDBC Driver の jar をアップロード → トライアルをアクティベート
2. `/connections` で接続文字列を登録（`sys_connection_props` から動的にフォームが作られる）
3. `/syncs/new` のウィザードでテーブルを選び、列と型のマッピングを確認して保存

CLI から接続だけ確認したい場合:

```bash
# 共有 JDBC 接続の登録が 1 件だけなら --jdbc-name は省略できる
java -jar build/libs/adapter-*-all.jar test-connection --jdbc-name snowflake
java -jar build/libs/adapter-*-all.jar list-tables     --jdbc-name snowflake
```

列と型の初期提案は JDBC メタデータから行われます
（`metadata/JdbcMetadataInspector.kt` → `metadata/FieldTypeSuggester.kt`）。
提案が気に入らなければウィザードの Step 3 / Step 4 で変更できます。

---

<a id="r-2"></a>

### R-2. JDBC 型 → kintone フィールド型のマッピングを変えたい

**触る場所**： `metadata/FieldTypeSuggester.kt:25`

```kotlin
fun suggest(jdbcType: Int): ColumnType = when (jdbcType) { ... }
fun suggestRecordIdType(jdbcType: Int): RecordIdType = when (jdbcType) { ... }
```

`java.sql.Types` の定数から `ColumnType`（`TEXT` / `NUMBER` / `DATETIME` / `SELECTION`）を返す
純粋関数です。ここは新規連携ウィザードの**初期提案**にしか使われないので、
既存の連携には影響しません。

実際の値変換は別の場所です：

| やりたいこと | 触る場所 |
|---|---|
| ResultSet の値 → protobuf Field | `jdbc/RowMapper.kt:106` `readField()` |
| protobuf Field → SQL バインド値 | `jdbc/RowMapper.kt:141` `fieldToValue()` |
| GetSchema が返す定義 | `service/AdapterServiceImpl.kt` `getSchema()` |

**列型を増やす場合**は `ColumnType` enum に加えて、上の 3 箇所すべての `when` を
埋める必要があります（Kotlin の網羅性チェックがコンパイルエラーで教えてくれます）。

---

<a id="r-3"></a>

### R-3. 絞り込み条件の変換を直したい／足したい

**触る場所**： `filter/FilterTranslator.kt:85` `translateOne()`

kintone の `FilterCondition`（oneof で 39 種）を受け、`WhereClause`
（SQL 断片 + バインドパラメータ）へ変換する巨大な `when` です。

```kotlin
private fun translateOne(c: FilterCondition): WhereClause = when (c.conditionCase) {
    TEXT_EQUAL -> textEqual(c.textEqual)
    TEXT_CONTAINS -> textContains(c.textContains, negate = false)
    // …
}
```

- **値は必ず `WhereClause` のパラメータとして返す**こと。SQL 文字列に値を埋め込むと
  インジェクションになります。列名だけは `jdbc/SqlIdentifier.kt` の `quote()` を通します。
- 未対応は `multiple_selection_in` / `multiple_selection_not_in` の 2 件のみです。
- 日時系（今日・今月・過去 N 日 など）は相対日付の解決が必要なので、
  データソースの方言に合わせて直すことが多い箇所です。

**テストが揃っています。** `src/test/kotlin/com/cdata/kintone/adapter/filter/FilterTranslatorTest.kt` に条件ごとの
ケースがあるので、**先にテストを足してから実装する**のが安全です。

---

<a id="r-4"></a>

### R-4. SQL の方言に合わせたい（LIMIT / OFFSET / 引用符など）

**触る場所**： `jdbc/QueryBuilder.kt` と `jdbc/SqlIdentifier.kt`

```kotlin
fun buildSelect(...): PreparedQuery   // :29
fun buildInsert(...)                  // :61
fun buildUpdate(...)                  // :76
fun buildDelete(...)                  // :89
fun buildCount(...)                   // :99
```

CData JDBC Driver は SQL-92 に寄せた共通の方言を受け付けるため、
**通常は変更不要**です。ネイティブ JDBC ドライバに差し替える場合や、
ページングの方式が違うデータソースに当てる場合だけここを触ります。

> 複合主キーには未対応です（`PrimaryKeyConfig` が 1 カラム固定）。
> 対応するなら `TableConfig` / `QueryBuilder` / `RowMapper.extractRecordId()` の
> 3 箇所を同時に変える必要があり、影響は小さくありません。

---

<a id="r-5"></a>

### R-5. 件数取得（Count）を軽くしたい

**設定だけで切り替わります。** 連携の `count-strategy`（Web UI の連携編集画面）:

| 値 | 挙動 |
|---|---|
| `ACTUAL` | `SELECT COUNT(*)` を実行（既定） |
| `ALWAYS_ZERO` | クエリを投げず常に 0 を返す |

SaaS では `COUNT` が全件走査になり数十秒かかることがあります。
一覧の総件数表示を諦めてでも体感速度を優先したい場合に `ALWAYS_ZERO` を使います。
実装は `service/AdapterServiceImpl.kt` の `count()` と `config/Config.kt:87` の `CountStrategy`。

---

<a id="r-6"></a>

### R-6. Search / Aggregate を作り込みたい

現状は基本実装のみです（`Search` は `LIKE` ベース、`Aggregate` は
COUNT/SUM/AVG/MAX/MIN と日時グループ化）。案件で本格的に使うなら
`service/AdapterServiceImpl.kt` の該当メソッドを拡張します。

- 有効化は連携設定の `search-supported` / `aggregate-supported` を `true` に
- `GetCapability` で `true` を返すと kintone 側が呼び始めるので、**実装より先に宣言しない**こと
- 集計を SQL に落とすなら `QueryBuilder` に `buildAggregate()` を足す形が素直です

---

<a id="r-7"></a>

### R-7. 対応機能の宣言を変えたい

Web UI の連携編集画面で変更し、Adapter を再起動するだけです。
`filterable-fields` / `sortable-fields` に挙げた kintone field_id だけが
kintone の UI で絞り込み・並び替えの選択肢に出ます。

> ここに挙げた列は、データソース側でインデックスが効くか確認しておくこと。
> 効かない列を絞り込み可能にすると、一覧を開くたびに全件走査が走ります。

---

<a id="r-8"></a>

### R-8. 管理 Web UI に画面を足したい

Ktor のサーバサイドレンダリングです。ルーティングとビューが 1:1 で並んでいます。

```
web/WebUiServer.kt          ルーティング登録の入口
web/AppContext.kt           依存の組み立て
web/routes/*.kt             HTTP ハンドラ
web/views/*.kt              HTML 生成（kotlinx.html）
web/views/Layout.kt         共通レイアウト・ナビゲーション
src/main/resources/static/  CSS・JS
```

画面を 1 つ足す手順は、`routes/` にハンドラを追加 → `views/` にビューを追加 →
`WebUiServer.kt` に登録 → `Layout.kt` のナビに導線を追加、の 4 ステップです。
既存の `HelpRoutes.kt` / `HelpView.kt` が最小の例です。

E2E テスト（Playwright）は `src/browserTest/kotlin/com/cdata/kintone/adapter/e2e/` にあります。

---

<a id="r-9"></a>

### R-9. 設定ストアを差し替えたい（RDB・S3 など）

**触る場所**： `config/ConfigSource.kt:13`

```kotlin
interface ConfigSource {
    fun listTables(): List<String>
    fun loadTableSet(tableName: String): TableConfigSet
    fun saveTableSet(tableName: String, set: TableConfigSet)
    fun deleteTable(tableName: String)
    fun loadSharedJdbcConfig(name: String): JdbcConfig?
    fun listSharedJdbcConfigs(): List<String>
    fun saveSharedJdbcConfig(name: String, config: JdbcConfig)
    fun deleteSharedJdbcConfig(name: String)
}
```

現在の実装は `SqliteConfigSource` の 1 つだけです。
PostgreSQL や MySQL を設定ストアにしたい場合は、このインターフェースを実装して
`config/ConfigStore.kt` の `open()` が返すようにすれば差し替わります。

```kotlin
// config/ConfigStore.kt
object ConfigStore {
    fun open(configDir: Path, sqlitePath: Path? = null, ...): ConfigSource =
        SqliteConfigSource(resolveDbPath(configDir, sqlitePath), envResolver)  // ← ここ
}
```

上位のコード（`web/AppContext.kt` / `cli/*Command.kt` / `runtime/MultiAdapterRunner.kt`）は
`ConfigSource` しか知らないので、影響範囲は閉じています。
外部 DB 実装は現時点では**スコープ外**です
（`.steering/20260522-web-ui-and-sqlite/requirements.md` で次フェーズ送り）。

---

<a id="r-10"></a>

### R-10. 接続・OAuth まわりを触りたい

| やりたいこと | 触る場所 |
|---|---|
| 接続プールの設定 | `jdbc/JdbcConnectionProvider.kt`（HikariCP） |
| ドライバ jar の動的ロード | `jdbc/JdbcDriverManager.kt`（URLClassLoader による隔離） |
| トライアルライセンス有効化 | `jdbc/DriverActivator.kt` |
| OAuth キャッシュの分離 | `jdbc/JdbcUrlEnhancer.kt:15` |
| 接続文字列のプロパティ検出 | `jdbc/JdbcConnectionPropertyInspector.kt` |
| プロパティ検出用の接続文字列（プローブ）の組み立て | `jdbc/ConnectionPropertyProbe.kt` |
| プロパティ検出のフォールバック（`getPropertyInfo` 由来の縮退） | `jdbc/DegradedPropertyMapper.kt` |
| 接続文字列のマスキング | `jdbc/ConnectionStringMasker.kt` |

> ⚠️ **`sys_connection_props` は接続を確立しないと読めません。**
> 26.x 系のドライバーは空の接続文字列 (`jdbc:sapgateway:`) を検証で弾くため、
> `ConnectionPropertyProbe` が「安全プロパティのみ → 素の接続文字列 →
> 必須プロパティのダミー値付き」の順に候補を作り、最初に成功したものを使います。
> ドライバーごとの分岐は入れないでください（300+ データソースに対して維持できません）。
> 取得できたかどうかは `ConnectionPropertiesResult.source` で判別します。

> ⚠️ **接続文字列をログ・画面に出すときは、必ず `ConnectionStringMasker.mask()` を通してください。**
> プロパティ名に `password` / `token` / `secret` / `key` 等を含む値をマスクします。
> 自前の正規表現でマスクすると、データソースごとに異なる認証プロパティ名
> （`PersonalAccessToken` / `APIKey` / `AWSSecretKey` ...）を取りこぼします。
> 実際にこれで JWT がログに平文で出ていました
> （[Issue #10](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/10)）。

**OAuth キャッシュの分離も重要です。** 同じドライバを使う複数の連携が
1 つのキャッシュファイルを取り合うと再認証が頻発します。`JdbcUrlEnhancer` が
`OAuthSettingsLocation` を `run/oauth/<連携名>.txt` に自動で振り分けています。
接続文字列を組み立て直す改修をする場合、**この処理を外さないこと**。

---

<a id="r-11"></a>

### R-11. Agent コンテナの起動方法を変えたい

**触る場所**： `agent/AgentContainerManager.kt`

Docker socket（`/var/run/docker.sock`）を叩いて `kintone-agent-<連携名>` を
作成・起動・停止します。Kubernetes や systemd に載せ替えるなら、このクラスを
差し替えるのが境界になります。

関連：

| クラス | 役割 |
|---|---|
| `agent/AgentConfigManager.kt` | `agent/tables/<連携名>/agent.json` の生成 |
| `agent/KeyPairGeneratorService.kt` | RSA 2048 鍵ペアの生成（Web UI のボタン） |
| `agent/PublicKeyManager.kt` | 公開鍵の読み出し |
| `agent/SyncConnectionService.kt` | 鍵・トークン・起動をまとめた 1 ボタン接続 |

> Agent バイナリ本体はサイボウズ社から個別に受領するもので、
> リポジトリには含まれません。`agent/bin/linux_<arch>/` に配置します。

---

## 4. テストの足し方

本プロジェクトは TDD ベースで書かれています（[development-guidelines.md](development-guidelines.md) §4）。

| 種別 | 置き場所 | 実行 |
|---|---|---|
| ユニットテスト | `src/test/kotlin/.../<パッケージ>/` | `./gradlew test` |
| ブラウザ E2E | `src/browserTest/kotlin/.../e2e/` | `./gradlew browserTest` |

- テストクラス名は対象クラス + `Test`（例：`FilterTranslatorTest`）
- JDBC を伴う処理は `src/test/kotlin/com/cdata/kintone/adapter/runtime/FakeConnectionProvider.kt` を使うと
  実データソースなしで書けます
- E2E は `BrowserTestBase.kt` を継承すると Web UI の起動・終了が自動になります

```bash
./gradlew test                                  # ユニット（数秒）
./gradlew browserTest                           # E2E（初回は Chromium を自動DL）
PLAYWRIGHT_HEADLESS=false ./gradlew browserTest  # 画面を見ながらデバッグ
./gradlew ktlintCheck detekt                    # 静的解析
./gradlew jacocoTestReport                      # カバレッジ
```

---

<a id="proto"></a>

## 5. protobuf スキーマを更新するとき

**ローカルでコード生成はしていません。** サイボウズが Buf Schema Registry (BSR) に
公開している**生成済みアーティファクト**を Maven 依存として取得しています。

```kotlin
// build.gradle.kts
val bsrCommit = "86857a1d93f8"  // ← スキーマ更新時はここを差し替える
val protoKotlinGenVersion = "34.1.0.2.20260309044725.$bsrCommit"
val protoJavaGenVersion   = "34.1.0.1.20260309044725.$bsrCommit"
val grpcJavaGenVersion    = "1.81.0.1.20260309044725.$bsrCommit"
val grpcKotlinGenVersion  = "1.5.0.3.20260309044725.$bsrCommit"
```

更新手順は、BSR で新しいコミットハッシュを確認して上記 5 つの定数を差し替え、
`./gradlew build` でコンパイルを通すだけです。`repositories` に
`https://buf.build/gen/maven` が登録済みなので追加設定は要りません。

> リポジトリ直下の `.proto/` は**参照用のコピー**です。ビルドには使われないので、
> ここを編集しても生成コードは変わりません。

**スキーマは `cybozu.data_connector.adapter.v1` に厳密準拠が必要**で、
独自のフィールド追加はできません。

> `protovalidate` の transitive 依存は、cybozu 側が参照する版が BSR から
> 削除されているため `resolutionStrategy.force` で上書きしています。
> スキーマ更新時にここが原因で解決に失敗したら、`protovalidate*Version` も
> 併せて最新に上げてください。

---

<a id="pitfalls"></a>

## 6. 引き継ぎ時に必ず読む落とし穴

| # | 落とし穴 | 対処 |
|---|---|---|
| 1 | 1 テーブル = 1 Adapter = 1 Agent = 1 Connector（サイボウズ仕様） | テーブルが増えるとセット数も増える。`MultiAdapterRunner` が 1 JVM に集約している |
| 2 | Agent の停止・再起動直後に kintone 側で「セッションが見つかりません」 | サイボウズへ改善要望提出済み。デモ直前に Agent を触らない |
| 3 | 複数連携で OAuth キャッシュが衝突する | `JdbcUrlEnhancer` の分離処理を外さない（R-10） |
| 3b | 接続文字列をそのままログ・画面に出すと認証情報が漏れる | 必ず `ConnectionStringMasker.mask()` を通す（R-10） |
| 4 | 複合主キー未対応 | 対象テーブルの選定で回避するのが現実的 |
| 5 | Connect RPC のメッセージ上限 4MB | 大量件数の一括取得には向かない。ページングを使う |
| 6 | `GetCapability` で `true` を宣言した RPC は kintone が呼び始める | 実装より先に宣言しない |
| 7 | macOS 版 Agent は未提供 | 開発時も Docker（Linux コンテナ）前提 |
| 8 | Gradle 8.x は Java 25 未対応 | JDK 21 を使う（`.tool-versions` 参照） |
| 9 | Docker socket のマウントは実質 root 権限 | 本番では socket proxy 等の隔離を検討 |
| 10 | `lib/*.lic` `.env` `agent/*.pem` は機密 | `.gitignore` 済みだが誤コミットに注意 |

サイボウズ社へ提出した改善要望の全文は [feedback-to-cybozu.md](feedback-to-cybozu.md) にあります。
**仕様の癖と、それをどう回避したかの記録**なので、引き継ぎ時に目を通す価値があります。

---

## 7. 関連ドキュメント

| ドキュメント | 内容 |
|---|---|
| [README.md](../README.md) | 起動手順・全体像 |
| [architecture.md](architecture.md) | 技術仕様・制約・性能要件 |
| [functional-design.md](functional-design.md) | 機能設計・シーケンス図・クラス図 |
| [repository-structure.md](repository-structure.md) | ディレクトリとファイル配置のルール |
| [development-guidelines.md](development-guidelines.md) | コーディング規約・TDD・Git 規約 |
| [glossary.md](glossary.md) | 用語集（kintone / CData 双方の用語） |
| [DOCKER-SETUP.md](DOCKER-SETUP.md) | 管理者向けセットアップ |
| [E2E-SETUP.md](E2E-SETUP.md) | 実 kintone との結合テスト手順 |
| [feedback-to-cybozu.md](feedback-to-cybozu.md) | サイボウズ社への改善要望と検証知見 |
