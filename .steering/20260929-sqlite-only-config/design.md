# 設定ストアの SQLite 一本化 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | sqlite-only-config |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 実装アプローチ

`ConfigSource` インターフェースはそのまま残し、実装を `SqliteConfigSource` 1 つにする。
インターフェース自体は将来の外部 DB 実装（スコープ外）の拡張点として意味があるため削除しない。

```
変更前                                変更後
─────────────────────               ─────────────────────
ConfigSource (interface)            ConfigSource (interface)
 ├─ YamlConfigSource      ←削除       └─ SqliteConfigSource
 └─ SqliteConfigSource

ConfigSourceFactory                 ConfigStore (object)
 ├─ Mode.YAML / Mode.SQLITE ←削除     └─ open(configDir, sqlitePath?) : ConfigSource
 ├─ detectMode()            ←削除
 └─ create()
```

### 1.1 主要な設計判断

| # | 判断 | 理由 |
|---|---|---|
| 1 | `ConfigSource` インターフェースは残す | 外部 DB 実装の拡張点。`docs/extending.md` R-9 の説明もこれを前提にしている |
| 2 | `ConfigSourceFactory` → `ConfigStore` に置き換え | Mode 分岐がなくなり「ファクトリ」ではなくなるため。パス解決だけを担う |
| 3 | `expandEnvVars` を `EnvVarExpander` に独立 | `SqliteConfigSource` が `YamlConfigSource` の companion に依存しているため。YAML 固有の処理ではない |
| 4 | `init-table` を廃止 | YAML 4 ファイル生成が唯一の役割。Web UI ウィザードと重複し、残すと `kaml` 依存も残る |
| 5 | `test-connection` / `list-tables` は `--jdbc-name` に変更 | SQLite の `shared_jdbcs` を名前で引く。ファイルパス指定の概念がなくなるため |

---

## 2. 変更するコンポーネント

### 2.1 新規作成

#### `config/EnvVarExpander.kt`

`YamlConfigSource.Companion.expandEnvVars` を移設する。

```kotlin
/** `${VAR}` 形式のプレースホルダを環境変数で置換する。 */
object EnvVarExpander {
    private val ENV_VAR_REGEX = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")

    fun expand(text: String, envResolver: (String) -> String?): String =
        ENV_VAR_REGEX.replace(text) { match ->
            envResolver(match.groupValues[1]) ?: match.value
        }
}
```

#### `config/ConfigStore.kt`

`ConfigSourceFactory` の後継。Mode 判定を持たず、パス解決のみ。

```kotlin
object ConfigStore {
    const val DB_FILE_NAME = "config.db"

    fun resolveDbPath(configDir: Path, sqlitePath: Path? = null): Path =
        sqlitePath ?: configDir.resolve(DB_FILE_NAME)

    fun open(
        configDir: Path,
        sqlitePath: Path? = null,
        envResolver: (String) -> String? = System::getenv,
    ): ConfigSource = SqliteConfigSource(resolveDbPath(configDir, sqlitePath), envResolver)
}
```

`SqliteConfigSource` の `init` が `CREATE TABLE IF NOT EXISTS` を実行するため、
**`config.db` が無い場合は空スキーマが自動生成される**（FR-03 を満たす）。

### 2.2 変更

| ファイル | 変更内容 |
|---|---|
| `config/SqliteConfigSource.kt:209` | `YamlConfigSource.expandEnvVars(...)` → `EnvVarExpander.expand(...)` |
| `config/ConfigSource.kt:9` | KDoc の「フェーズ2-A では YamlConfigSource が唯一の実装」を更新 |
| `cli/ServeCommand.kt` | `YamlConfigSource(configDir)` → `ConfigStore.open(configDir)`。`--sqlite-path` を追加。引数なし既定の `DEFAULT_TABLE_NAME` 起動は廃止し、`--table` / `--tables` 必須にする |
| `cli/ServeAllCommand.kt` | 同上（`--sqlite-path` 追加） |
| `cli/TestConnectionCommand.kt` | `--jdbc-config <path>` → `--jdbc-name <name>` + `--config-dir`。`ConfigSource.loadSharedJdbcConfig(name)` で取得 |
| `cli/ListTablesCommand.kt` | 同上 |
| `web/AppContext.kt` | `configSourceMode` フィールドと `ConfigSourceFactory` 参照を削除。`ConfigStore.open()` を使う |
| `web/views/Layout.kt` | 引数 `mode` を削除。フッタの `ConfigSource: xxx` 表記を削除 |
| `web/views/*.kt` | `layout(...)` 呼び出しから `mode` 引数を除去 |
| `cli/WebUiCommand.kt` | `--sqlite-path` を追加（任意） |
| `Application.kt` | 廃止コマンドの `subcommands(...)` 登録と import を削除 |
| `build.gradle.kts` | `kaml` 依存を削除 |
| `docker-compose.yml` / `Dockerfile` | `CONFIG_SOURCE` 環境変数を削除 |
| `.env.example` | `CONFIG_SOURCE` の記述を削除 |

### 2.3 削除

| 対象 | 種別 |
|---|---|
| `config/YamlConfigSource.kt` | 実装 |
| `config/ConfigLoader.kt` | 実装（`YamlConfigSource` の薄いラッパ） |
| `config/ConfigMigrator.kt` | 実装 |
| `config/ConfigSourceFactory.kt` | 実装（`ConfigStore` に置換） |
| `cli/MigrateConfigCommand.kt` | CLI |
| `cli/MigrateToMultiTableCommand.kt` | CLI |
| `cli/MigrateToSqliteCommand.kt` | CLI |
| `cli/ExportYamlCommand.kt` | CLI |
| `cli/InitTableCommand.kt` | CLI |
| `config/YamlConfigSourceTest.kt` | テスト |
| `config/ConfigLoaderTest.kt` | テスト |
| `config/ConfigMigratorTest.kt` | テスト |
| `config/ConfigSourceFactoryTest.kt` | テスト |
| `config/YamlSqliteMigrationTest.kt` | テスト |
| `config/*.yaml.example`, `config/tables/*/*.yaml.example`, `config/jdbc/*.yaml.example` | 雛形 |

> `config/tables/` と `config/jdbc/` の実体 YAML は作業前に `config/config.db` へ取り込み済み。
> 削除して差し支えない。

---

## 3. データ構造の変更

**なし。** `SqliteSchema`（`shared_jdbcs` / `tables` / `schema_meta`）は変更しない。
`AdapterConfig` / `TableConfig` / `JdbcConfig` などのドメインモデルも変更しない。

`@Serializable` アノテーションは JSON シリアライズ（SQLite のカラム）で引き続き使うため残す。
`kaml` だけが不要になる。

---

## 4. CLI インターフェースの変更

### 4.1 変更後のサブコマンド一覧

| コマンド | 変更 |
|---|---|
| `web-ui` | `--sqlite-path` 追加 |
| `serve` | `--table` / `--tables` のいずれか必須に。`--sqlite-path` 追加 |
| `serve-all` | `--sqlite-path` 追加 |
| `list-active` | 変更なし |
| `test-connection` | `--jdbc-config <path>` → `--jdbc-name <name>` |
| `list-tables` | `--jdbc-config <path>` → `--jdbc-name <name>` |

### 4.2 使用例（変更後）

```bash
# 共有 JDBC 設定名の確認は Web UI の /connections で行う
java -jar adapter-all.jar test-connection --jdbc-name salesforce
java -jar adapter-all.jar list-tables     --jdbc-name salesforce

java -jar adapter-all.jar serve --table account
java -jar adapter-all.jar serve-all
java -jar adapter-all.jar web-ui --port 8080
```

### 4.3 `--jdbc-name` 省略時の挙動

共有 JDBC 設定が **1 件だけならそれを使う**。複数ある場合は一覧を表示して
終了コード 1 で終わる（例外を投げない）。0 件なら Web UI での登録を案内する。

これは調査結果 2（`NoSuchFileException` で落ちる）の再発防止を兼ねる。

---

## 5. 影響範囲の分析

### 5.1 テスト

| テスト | 対応 |
|---|---|
| `YamlConfigSourceTest` ほか 5 件 | 削除 |
| `SqliteConfigSourceTest` | 変更なし |
| `ServerConfigTest` | 変更なし（YAML 非依存） |
| `runtime/*Test` | `FakeConnectionProvider` 利用。変更なし |
| E2E 4 件（`ConnectKintoneE2ETest` / `NewSyncFlowE2ETest` / `SyncLogsE2ETest` / `SyncsListE2ETest`） | `YamlConfigSource` でフィクスチャを作っている。`SqliteConfigSource` に置換 |

新規テスト:

| 対象 | 内容 |
|---|---|
| `EnvVarExpanderTest` | `${VAR}` の展開・未定義時のフォールバック |
| `ConfigStoreTest` | `config.db` 不在時のスキーマ自動生成、`--sqlite-path` 指定の解決 |

### 5.2 ドキュメント

| ファイル | 変更箇所 |
|---|---|
| `README.md` | 設定ファイル節、CLI 一覧、設定ストア切替節、ディレクトリ構成 |
| `docs/extending.md` | §2 設定ファイルの構造、R-1、R-9、§6 落とし穴 |
| `docs/DOCKER-SETUP.md` | `CONFIG_SOURCE` 記述、ボリューム表 |
| `docs/architecture.md` | 設定・CLI 節、依存ライブラリ一覧（kaml） |
| `docs/functional-design.md` | §5.1 設定ファイル構造 |
| `docs/repository-structure.md` | `config/` 配下の構成 |
| `docs/glossary.md` | ConfigSource 関連の用語 |
| `web/views/HelpView.kt` | Web UI のヘルプ本文 |

### 5.3 後方互換性

**破壊的変更。** 既存の YAML 設定は読み込めなくなる。
移行機能は提供しないため、旧環境は Web UI から設定を作り直す。

---

## 6. 実装順序

依存関係の都合で以下の順に進める。

1. `EnvVarExpander` を作り、`SqliteConfigSource` の依存を切り替える（`YamlConfigSource` を消せる状態にする）
2. `ConfigStore` を作る
3. `ServeCommand` / `ServeAllCommand` / `WebUiCommand` / `AppContext` を `ConfigStore` 経由に変更
4. `test-connection` / `list-tables` を `--jdbc-name` に変更
5. `Application.kt` から廃止コマンドを外し、CLI ファイルを削除
6. `YamlConfigSource` / `ConfigLoader` / `ConfigMigrator` / `ConfigSourceFactory` を削除
7. `Layout.kt` と各 View から `mode` を除去
8. テストの削除・追加・修正
9. `build.gradle.kts` から `kaml` を削除
10. Docker / `.env.example` の `CONFIG_SOURCE` を削除
11. YAML 雛形ファイルの削除
12. ドキュメント更新
13. `./gradlew test browserTest ktlintCheck detekt`
