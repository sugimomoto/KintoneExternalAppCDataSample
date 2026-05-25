# Phase 2-C: Sync 概念導入 + Docker API 制御 — 設計

| 項目 | 内容 |
|---|---|
| フェーズ | 2-C |
| 作成日 | 2026-05-25 |
| 関連 | [requirements.md](./requirements.md) |
| ステータス | ドラフト |

---

## 1. 全体アーキテクチャ

### 1.1 プロセスモデル変更

#### Phase 2-B (現状)

```
ホスト OS
├ java -jar adapter web-ui  (Web UI + MultiAdapterRunner)
│   └ Adapter A, B, C (port 18001-18003)
└ Docker
    ├ kintone-agent-account     ← docker-compose.multi.yml 手動編集
    ├ kintone-agent-contact
    └ kintone-agent-gs-opportunity
```

#### Phase 2-C (目標)

```
ホスト OS
├ Docker (Compose)
│   ├ adapter-console コンテナ
│   │   ├ Web UI (port 8080)
│   │   ├ MultiAdapterRunner
│   │   ├ Adapter A, B, C (port 18001-18003)
│   │   └ AgentContainerManager ←→ /var/run/docker.sock
│   ├ kintone-agent-account     ← Web UI が動的に create/start
│   ├ kintone-agent-contact     ← compose ファイルへの記載不要
│   └ kintone-agent-gs-opportunity
```

ユーザー操作:
```bash
docker compose up -d adapter-console  # 1 度だけ
# → ブラウザで http://localhost:8080
# → Sync 作成や Agent 起動はすべて Web UI から
```

### 1.2 主要な設計判断

| ID | 判断 | 根拠 |
|---|---|---|
| D-01 | **Sync 一級概念**: `TableConfigSet` + `Adapter` + `AgentContainer` を統合 | UX の核心。技術詳細を隠蔽 |
| D-02 | **Docker SDK for Java** で Agent コンテナ制御 | `docker-java` 公式 SDK 利用、`docker-compose.yml` の手動編集を完全廃止 |
| D-03 | **docker-compose.multi.yml は段階的廃止** | Phase 2-C: 後方互換で残すが Web UI からは使わない、Phase 2-D で削除 |
| D-04 | **adapter-console を Docker 化** | 配布形態をシンプルに（ホスト Java 不要） |
| D-05 | **Adapter は引き続き 1 JVM 内 (MultiAdapterRunner)** | Phase 2-A の延長、起動高速 + リソース効率 |
| D-06 | **Agent コンテナのライフサイクルは Sync と同期** | Sync 作成 → Agent コンテナ create、Sync 削除 → コンテナ remove |
| D-07 | **既存設定 (yaml/sqlite) は無変更** | 後方互換、Phase 2-B 検証済資産の維持 |
| D-08 | **用語統一は URL も対象** | `/syncs` を正式 URL、`/tables` は 301 リダイレクト |

---

## 2. Docker SDK 統合 (DK-01 〜 DK-04)

### 2.1 依存追加

```kotlin
// build.gradle.kts
implementation("com.github.docker-java:docker-java-core:3.4.0")
implementation("com.github.docker-java:docker-java-transport-httpclient5:3.4.0")
```

### 2.2 AgentContainerManager 設計

```kotlin
class AgentContainerManager(
    private val dockerClient: DockerClient,
    private val agentImage: String = "kintone-data-connector-agent:0.9.2",
    private val hostAgentRoot: Path = Path.of("/app/agent"),  // コンテナ内パス
) {
    /** Sync 用 Agent コンテナの状態。 */
    data class ContainerInfo(
        val name: String,         // kintone-agent-<syncName>
        val state: State,         // RUNNING / STOPPED / NOT_FOUND
        val image: String,
        val createdAt: Instant?,
    )

    enum class State { RUNNING, STOPPED, NOT_FOUND }

    /** コンテナを作成 (まだ無ければ)。既存があれば idempotent。 */
    fun ensureCreated(syncName: String): ContainerInfo

    /** コンテナを起動。 */
    fun start(syncName: String): ContainerInfo

    /** コンテナを停止。 */
    fun stop(syncName: String, timeoutSec: Int = 10): ContainerInfo

    /** コンテナを削除 (停止 → 削除)。 */
    fun remove(syncName: String, force: Boolean = false): Boolean

    /** 状態取得 (ヘルス含む)。 */
    fun status(syncName: String): ContainerInfo

    /** ログ取得 (Phase 2-C LG-02 で使う)。 */
    fun streamLogs(syncName: String, since: Long, tail: Int = 100): Flow<String>

    /** 全 Sync 用コンテナを列挙 (kintone-agent-* プレフィックス)。 */
    fun listAll(): List<ContainerInfo>
}
```

### 2.3 コンテナ作成パラメータ

```kotlin
private fun createContainerCommand(syncName: String): CreateContainerCmd {
    return dockerClient.createContainerCmd(agentImage)
        .withName("kintone-agent-$syncName")
        .withHostConfig(
            HostConfig.newHostConfig()
                .withBinds(
                    Bind("$hostAgentRoot/tables/$syncName/agent.json", Volume("/opt/agent/agent.json"), AccessMode.ro),
                    Bind("$hostAgentRoot/private-key.pem", Volume("/opt/agent/private-key.pem"), AccessMode.ro),
                )
                .withExtraHosts("host.docker.internal:host-gateway")
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart()),
        )
        .withLabels(mapOf(
            "com.cdata.adapter.sync" to syncName,    // 識別用ラベル
            "com.cdata.adapter.managed" to "true",   // listAll で絞り込み
        ))
}
```

### 2.4 Docker socket 接続

```kotlin
// 起動時
val dockerClient: DockerClient = DockerClientBuilder.getInstance(
    DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("unix:///var/run/docker.sock")
        .build()
).withDockerHttpClient(
    ApacheDockerHttpClient.Builder()
        .dockerHost(URI("unix:///var/run/docker.sock"))
        .build()
).build()
```

### 2.5 Web UI コンテナ判定

Web UI が `/var/run/docker.sock` を見つけたら Docker モード、なければ「Docker 制御無効」モード（既存の compose ファイル + 手動操作）にフォールバック。

```kotlin
class AgentControlMode(socketPath: Path = Path.of("/var/run/docker.sock")) {
    val available: Boolean = socketPath.exists()
}
```

---

## 3. Sync 概念の実装 (UX-01 + UX-02)

### 3.1 用語マッピングと内部実装

| エンドユーザー表示 | URL | コード上の名前 | データ実体 |
|---|---|---|---|
| Syncs | `/syncs` | `TableConfigSet` | `config/tables/<name>/` |
| データソース接続 | `/connections` | `JdbcConfig` | `config/jdbc/<name>.yaml` |
| ドライバー | `/drivers` | `JdbcDriverInfo` | `lib/*.jar` |

### 3.2 URL リダイレクト

```kotlin
// Phase 2-B URL → Phase 2-C URL (HTTP 301)
"/tables" → "/syncs"
"/tables/{name}" → "/syncs/{name}"
"/tables/{name}/edit" → "/syncs/{name}/edit"
"/tables/{name}/start" → "/syncs/{name}/start"
"/tables/{name}/stop" → "/syncs/{name}/stop"
"/tables/new" → "/syncs/new"
"/tables/new/step{N}" → "/syncs/new/step{N}"
```

ファイル名: `routes/TablesRoutes.kt` → `routes/SyncsRoutes.kt` (リネーム + 内部用ハンドラは旧 path もサポート 1 フェーズ)

### 3.3 ナビゲーション変更

```
旧: [ Dashboard ] [ Tables ] [ Connections ] [ Drivers ]
新: [ Dashboard ] [ Syncs ] [ Connections ] [ Drivers ]
```

ボタンラベル、画面タイトル、説明文を全て翻訳:
- "Tables" → "Syncs (連携)"
- "+ New Table" → "+ 新しい連携"
- "Start" → "開始" / "起動"
- "Stop" → "停止"
- "Edit" → "編集"

### 3.4 Sync 詳細画面 (UX-02 + KC-01 + KC-02 + DK-04)

[requirements.md §5.1](./requirements.md) のモックアップ参照。実装は `views/SyncDetailView.kt`:

```kotlin
fun HTML.syncDetailView(ctx: AppContext, name: String, set: TableConfigSet) {
    val active = ctx.runner.listActive().firstOrNull { it.tableName == name }
    val agentConfig = ctx.agentConfigManager.load(name)
    val agentContainer = ctx.agentContainerManager.status(name)
    val publicKey = ctx.publicKeyManager.read()

    layout(...) {
        h2 { /* Sync name + status badge */ }
        actionBar { /* 開始 / 停止 / 再起動 / 削除 */ }

        section { dataSourceSection(set) }
        section { kintoneConnectionSection(name, publicKey, agentConfig, agentContainer) }
        section { mappingSection(set) }
        section { activitySection(name) }

        details { summary { +"詳細 (上級者向け)" }
            advancedSection(active, agentContainer)
        }
    }
}
```

### 3.5 Advanced 折りたたみ

`<details>` タグで折りたたみ表示:
- Adapter port (set.server.port)
- Agent container name (`kintone-agent-<syncName>`)
- agent.json のパス
- config ファイルのパス
- 内部ログファイル

---

## 4. kintone 接続の 1 ボタン化 (KC-03)

### 4.1 ワンクリック処理フロー

「[ 接続して開始 ]」ボタンを押すと、サーバ側で以下を順次実行:

```kotlin
suspend fun connectKintone(syncName: String, token: String, kintoneHost: String?): Result {
    // 1. 既存 agent.json を読むか新規作成 (token + adapter_addr)
    val port = ctx.configSource.loadTableSet(syncName).server.port
    val adapterAddr = "host.docker.internal:$port"  // Adapter モード次第
    ctx.agentConfigManager.save(syncName, AgentConfig(token, adapterAddr, ...))

    // 2. Adapter を起動 (まだなら)
    if (ctx.runner.get(syncName) == null) {
        ctx.runner.startOne(syncName)
    }

    // 3. Agent コンテナを作成 + 起動
    ctx.agentContainerManager.ensureCreated(syncName)
    ctx.agentContainerManager.start(syncName)

    // 4. kintone 接続確認 (最大 10 秒待機、Agent ログから "successfully connected" 検出)
    val connected = waitForConnection(syncName, timeoutSec = 10)
    return if (connected) Result.Success else Result.Pending
}
```

### 4.2 公開鍵管理 (KC-01)

```kotlin
class PublicKeyManager(private val keyPath: Path = Path.of("./agent/public-key.pem")) {
    fun exists(): Boolean = keyPath.exists()
    fun read(): String? = if (exists()) Files.readString(keyPath) else null
    fun fingerprint(): String? = read()?.let { sha256(it) }
}
```

UI:
```kotlin
section {
    h3 { +"公開鍵" }
    code(classes = "public-key-display") { +publicKey.take(80) + "..." }
    button(onclick = "navigator.clipboard.writeText('...')") { +"📋 コピー" }
    a(href = "/syncs/$name/public-key.pem", download = "public-key.pem") { +"⬇ ダウンロード" }
}
```

### 4.3 kintone 管理画面リンク (KC-02)

```kotlin
section {
    label {
        +"kintone ドメイン (初回のみ): "
        input(type = InputType.text, name = "kintone_domain") {
            placeholder = "your-tenant.cybozu.com"
            value = ctx.preferences.get("kintone.domain") ?: ""
        }
    }
    a(href = "https://${domain}/k/admin/system/admin/dataConnector.html", target = "_blank") {
        +"kintone 管理画面で公開鍵を登録 ↗"
    }
}
```

ドメイン設定は SQLite or YAML の preferences テーブルに永続化 (新規追加)。

---

## 5. ライブログ表示 (LG-01 〜 LG-03)

### 5.1 Adapter ログ (LG-01)

Logback の `ListAppender` を独自実装し、メモリリングバッファを保持:

```kotlin
class WebLogAppender : AppenderBase<ILoggingEvent>() {
    private val buffer = RingBuffer<LogEntry>(capacity = 1000)
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    override fun append(event: ILoggingEvent) {
        val entry = LogEntry(
            timestamp = event.timeStamp,
            level = event.level.toString(),
            logger = event.loggerName,
            message = event.formattedMessage,
            mdc = event.mdcPropertyMap,
        )
        buffer.add(entry)
        listeners.forEach { it(entry) }
    }

    fun snapshot(syncName: String? = null): List<LogEntry> =
        buffer.toList().filter { syncName == null || it.mdc["sync"] == syncName }

    fun subscribe(callback: (LogEntry) -> Unit): Disposable { ... }
}
```

MDC (Mapped Diagnostic Context) に `sync=<syncName>` を入れて、Sync 別フィルタを実現。

### 5.2 Agent ログ (LG-02)

```kotlin
fun streamAgentLogs(syncName: String): Flow<String> = flow {
    val cmd = dockerClient.logContainerCmd("kintone-agent-$syncName")
        .withFollowStream(true)
        .withStdOut(true)
        .withStdErr(true)
        .withTimestamps(true)
        .withTail(100)
    cmd.exec(object : ResultCallback.Adapter<Frame>() {
        override fun onNext(frame: Frame) {
            trySend(String(frame.payload, Charsets.UTF_8))
        }
    }).awaitCompletion()
}
```

### 5.3 SSE エンドポイント

```
GET /syncs/{name}/logs/adapter/sse   → Adapter ログ
GET /syncs/{name}/logs/agent/sse     → Agent ログ
```

クライアント JS で 2 つの EventSource を開き、左右ペイン or タブで表示。

### 5.4 フィルタ (LG-03)

クライアントサイドのみで実装:
- レベル: INFO / WARN / ERROR チェックボックス
- 検索: 部分一致 (case insensitive)
- 上限: 1000 行（リングバッファ）

---

## 6. ウィザード強化 (WZ-01 + WZ-02)

### 6.1 Step 1 の選択肢追加

```
Step 1: データソース接続を選ぶ
   ⚪ 既存接続を使う (一覧から選択)        ← jdbc-ref
       [ salesforce  ▼ ]
   ⚪ + 新しい接続を作成
       → /connections/new に遷移、戻り URL 付き
```

### 6.2 jdbc-ref vs inline 分岐 (WZ-01)

SqliteConfigSource は `saveTableSetWithRef()` を既に持つ。YamlConfigSource にも追加:

```kotlin
fun YamlConfigSource.saveTableSetWithRef(tableName: String, set: TableConfigSet, jdbcRef: String) {
    val tableDir = configDir.resolve("tables").resolve(tableName)
    Files.createDirectories(tableDir)
    writeYaml(tableDir.resolve("server.yaml"), set.server, serializer<ServerConfig>())
    writeYaml(tableDir.resolve("jdbc-ref.yaml"), JdbcRef(jdbcRef), serializer<JdbcRef>())
    writeYaml(tableDir.resolve("table.yaml"), set.table, serializer<TableConfig>())
    writeYaml(tableDir.resolve("capability.yaml"), set.capability, serializer<CapabilityConfig>())
}
```

`ConfigSource` インターフェースに昇格させる。

### 6.3 Step 4 完了後の自動遷移 (WZ-02)

```kotlin
post("/syncs") {
    // ... 保存処理 ...
    val redirectTo = if (form["andConnect"] == "true") {
        "/syncs/$syncName/connect"   // kintone 接続画面
    } else {
        "/syncs/$syncName"
    }
    call.respondRedirect(redirectTo)
}
```

ボタンを「Save」「Save & Connect」の 2 種類に。

---

## 7. エラー文言翻訳 (UX-03)

### 7.1 ErrorMessageTranslator

```kotlin
object ErrorMessageTranslator {
    private val rules = listOf(
        TranslationRule(
            pattern = Regex("rpc error: code = Unavailable.*keepalive ping failed"),
            userMessage = "kintone との接続が切断されています。再接続をお試しください。",
            severity = Severity.WARN,
            actions = listOf(UserAction.RECONNECT),
        ),
        TranslationRule(
            pattern = Regex("Connection refused"),
            userMessage = "データソースへの接続に失敗しました。接続設定を確認してください。",
            severity = Severity.ERROR,
        ),
        TranslationRule(
            pattern = Regex("JAR not found"),
            userMessage = "データソース用のドライバーが見つかりません。Drivers 画面で追加してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_DRIVERS),
        ),
        // ... 多数
    )

    fun translate(rawMessage: String): UserMessage {
        return rules.firstOrNull { it.pattern.containsMatchIn(rawMessage) }
            ?.let { UserMessage(it.userMessage, it.severity, it.actions) }
            ?: UserMessage(rawMessage, Severity.ERROR, emptyList())
    }
}

data class UserMessage(val text: String, val severity: Severity, val actions: List<UserAction>)
enum class Severity { INFO, WARN, ERROR }
enum class UserAction { RECONNECT, GO_TO_DRIVERS, GO_TO_CONNECTIONS, RETRY }
```

エラー表示時にこれを通して、ユーザー向け文言 + アクションボタンを返す。

---

## 8. Docker 配布 (DP-01 + DP-02)

### 8.1 Dockerfile (DP-01)

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY . .
RUN ./gradlew shadowJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/build/libs/adapter-*-all.jar /app/adapter.jar
COPY src/main/resources/static /app/static
EXPOSE 8080 18000-18099
ENTRYPOINT ["java", "-jar", "/app/adapter.jar", "web-ui", \
            "--port", "8080", "--bind-address", "0.0.0.0", \
            "--config-dir", "/app/config", "--lib-dir", "/app/lib"]
```

### 8.2 docker-compose.yml (DP-02)

```yaml
# プロジェクトルートの docker-compose.yml
services:
  adapter-console:
    build:
      context: .
      dockerfile: Dockerfile
    image: cdata-kintone-adapter:0.4.0
    container_name: adapter-console
    ports:
      - "8080:8080"                       # Web UI
      - "18000-18099:18000-18099"         # Adapter ポート範囲
    volumes:
      - ./config:/app/config
      - ./lib:/app/lib
      - ./agent:/app/agent
      - ./run:/app/run
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      CONFIG_SOURCE: ${CONFIG_SOURCE:-yaml}
    restart: unless-stopped

# Agent コンテナは Web UI から動的に作成されるため定義不要
# (旧 agent/docker-compose.multi.yml は段階的廃止)
```

### 8.3 既存 Phase 2-A 用 compose ファイル

`agent/docker-compose.multi.yml` は当面残す。ただし README で「廃止予定、Web UI 経由を推奨」と明記。

---

## 9. データ・インターフェース変更点

### 9.1 既存に対する変更

| 既存 | 変更内容 |
|---|---|
| `TablesRoutes` | `SyncsRoutes` にリネーム + 旧 URL リダイレクト追加 |
| `TablesView` | `SyncsView` にリネーム + UI 文言翻訳 |
| `TableWizardRoutes` | `SyncWizardRoutes` にリネーム + Step 1 で connection 既存/新規分岐 |
| `AppContext` | `agentContainerManager`, `publicKeyManager`, `webLogAppender` 追加 |
| `ConfigSource` | `saveTableSetWithRef()` を interface に昇格 |
| `YamlConfigSource` | `saveTableSetWithRef()` 実装追加 |

### 9.2 新規追加

| クラス / モジュール | 役割 |
|---|---|
| `AgentContainerManager` | Docker SDK 経由で Agent コンテナを制御 |
| `PublicKeyManager` | `agent/public-key.pem` の読み出し + フィンガープリント |
| `ErrorMessageTranslator` | 内部エラー → ユーザー向け文言の辞書翻訳 |
| `WebLogAppender` | Logback Appender + メモリリングバッファ |
| `LogStreamRoutes` | SSE で Adapter/Agent ログを配信 |
| `SyncConnectionService` | KC-03 の 1 ボタン処理を取りまとめ |
| `KintonePreferences` | kintone ドメイン等のユーザー設定永続化 |
| `Dockerfile` | adapter-console のイメージビルド |
| `docker-compose.yml` (root) | 完成版配布 compose |

---

## 10. 影響範囲と互換性

| 影響領域 | 変更内容 | 後方互換 |
|---|---|---|
| URL | `/tables/*` → `/syncs/*` に 301 リダイレクト | ✅ 旧 URL は 1 フェーズ動作 |
| 設定ファイル | yaml/sqlite 構造は変更なし | ✅ |
| CLI | `serve` / `serve-all` / `web-ui` 等は維持 | ✅ |
| docker-compose.multi.yml | 段階的廃止 (Phase 2-D で削除予定) | ✅ Phase 2-C は残す |
| Agent コンテナの名前 | `kintone-agent-<syncName>` で統一 | ✅ |
| agent.json の場所 | `agent/tables/<syncName>/agent.json` 不変 | ✅ |
| Docker socket マウント | adapter-console コンテナ起動時に必須 | ⚠ ホスト OS の docker socket が必要 |

---

## 11. テスト戦略

### 11.1 ユニットテスト

- `AgentContainerManager`: docker-java の TestContainers 利用 or モック
- `PublicKeyManager`: `@TempDir` で agent ディレクトリ操作
- `ErrorMessageTranslator`: パターンマッチング多数のテーブル駆動テスト
- `WebLogAppender`: Logback の `ListAppender` ベースで動作確認

### 11.2 統合テスト

- Ktor TestApplication: 各ルート (リダイレクト含む) で 200 / 301 確認
- Sync 詳細ページが新 UI 要素を含むこと
- `/syncs/{name}/connect` POST で agent.json 保存 + Adapter 起動が連動

### 11.3 E2E (実機)

- Docker Desktop 必須
- `docker compose up -d adapter-console` で起動
- ブラウザで新規 Sync 作成 → 「kintone とつなぐ」→ kintone でアプリ作成

---

## 12. リスクと緩和

| リスク | 影響 | 緩和策 |
|---|---|---|
| Docker socket マウントのセキュリティ | コンテナがホスト Docker 全権限 | README で明示、Phase 2-D で proxy 対応予定 |
| docker-java API のバージョン互換 | Docker Engine API バージョン差で動かないケース | API バージョン明示、起動時にバージョン確認 |
| CData JDBC ライセンスのコンテナ対応 | adapter-console コンテナで machine ID 変動 | CData にエスカレ、当面は Trial license で運用 |
| ホスト Java 単体起動の後方互換 | Docker を使わないユーザー | `adapter web-ui` CLI は維持、Docker socket 検出時のみ機能拡張 |
| Logback Appender 注入の競合 | 既存ロガー設定との干渉 | logback.xml に明示記述、テストで確認 |

---

## 13. 段階的マイルストーン

| M | 内容 | 主要成果物 | 概算 |
|---|---|---|---|
| M1 | 用語統一 (Tables → Syncs) | URL リダイレクト + UI 文言 | 0.5 日 |
| M2 | Docker SDK 統合 + AgentContainerManager | 単体テスト緑 | 1.5 日 |
| M3 | Sync 詳細画面の刷新 + 公開鍵セクション | KC-01 + UX-02 + DK-04 | 1.5 日 |
| M4 | "kintone とつなぐ" 1 ボタン | KC-03 + SyncConnectionService | 1 日 |
| M5 | ライブログ表示 | LG-01〜LG-03 | 1.5 日 |
| M6 | ウィザード強化 | WZ-01 + WZ-02 | 1 日 |
| M7 | エラー文言翻訳辞書 + Advanced 折りたたみ | UX-03 | 1 日 |
| M8 | Docker 配布 | Dockerfile + docker-compose.yml | 1 日 |
| M9 | ドキュメント + リリース | README / known-issues + v0.4.0 タグ | 1 日 |

合計約 **10 日**。タスクリスト詳細は [tasklist.md](./tasklist.md)。

---

## 14. 関連

- 要求定義: [requirements.md](./requirements.md)
- タスク詳細: [tasklist.md](./tasklist.md) (次に作成)
- Phase 2-B 設計: [../20260522-web-ui-and-sqlite/design.md](../20260522-web-ui-and-sqlite/design.md)
- Phase 2-A 設計: [../20260522-multi-table-support/design.md](../20260522-multi-table-support/design.md)
