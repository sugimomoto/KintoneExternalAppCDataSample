# テーブル選択のページネーション + 検索 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | table-list-pagination-search |
| 作成日 | 2026-09-29 |
| 関連 | [requirements.md](./requirements.md) |
| ステータス | ドラフト |

---

## 1. 設計方針

### 1.1 主要な設計判断

| ID | 判断 | 根拠 |
|---|---|---|
| D-01 | **絞り込み・ページングはアプリ側の純粋関数で行う** (`TableListPaginator`) | `getTables` のパターン指定の挙動がドライバごとに異なる。純粋関数なら JDBC 接続なしでユニットテストできる |
| D-02 | **テーブル一覧は Connection 単位でキャッシュする** (`TableMetadataCache`) | 検索 1 打鍵ごとに `getTables` 全件取得が走るのを防ぐ (AC-7) |
| D-03 | **htmx フラグメント用エンドポイントを新設** (`GET /syncs/new/step2/list`) | `ConnectionsRoutes.kt:51` の `/connections/properties` と同じ既存パターンを踏襲 |
| D-04 | **一覧は `schema` → `name` の順でソートしてから分割する** | `getTables` の返却順はドライバ依存。順序が不安定だとページ分割が壊れる |
| D-05 | **デフォルトページサイズは 50、25/50/100/200 から選択可** | `.scrollable-list` は `max-height: 360px` のスクロール領域。50 件でスクロール量が過大にならない |
| D-06 | **検索はインクリメンタル (`keyup changed delay:300ms`)** | `ConnectionsView.kt:290` の URL プレビューと同じトリガ。既存 UX と揃える |
| D-07 | **`JdbcMetadataInspector.listTables()` のシグネチャは変更しない** | `cli/InitTableCommand.kt` からも利用されている (requirements 6章の制約) |
| D-08 | **ページ移動・検索でラジオの選択はリセットされる** | 「検索 → 選択 → Config name 入力 → Next」が動線で、選択後にページ移動する導線がない。選択の持ち越しは複雑さに見合わない |

### 1.2 リクエストフロー

```
[初回表示] GET /syncs/new/step2?connection=sf
   │
   ├─ TableMetadataCache.get("sf")  ← miss なら listTables() + sort してキャッシュ
   ├─ TableListPaginator.paginate(all, query=null, page=1, pageSize=50)
   └─ wizardStep2View(...)          ← ページ全体を返す

[検索 / ページ移動] GET /syncs/new/step2/list?connection=sf&q=oppo&page=2&pageSize=50
   │                                  ▲ htmx (hx-get, hx-target="#table-list-container")
   ├─ TableMetadataCache.get("sf")  ← hit (JDBC アクセスなし)
   ├─ TableListPaginator.paginate(all, query="oppo", page=2, pageSize=50)
   └─ createHTML().div { tableListContent(...) }  ← リスト部分のみ返す

[再取得] GET /syncs/new/step2/list?connection=sf&refresh=true
   └─ TableMetadataCache.invalidate("sf") してから上と同じ
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `metadata/TableListPaginator.kt` | **新規** | 絞り込み + ページ分割の純粋関数と `TablePage` データクラス |
| `metadata/TableMetadataCache.kt` | **新規** | Connection 名をキーにした `List<TableInfo>` のインメモリキャッシュ |
| `web/AppContext.kt` | 変更 | `tableMetadataCache` を DI プロパティとして追加 |
| `web/WebUiServer.kt` (AppContext 生成箇所) | 変更 | `TableMetadataCache` を生成して `AppContext` に渡す |
| `web/routes/TableWizardRoutes.kt` | 変更 | `step2` をキャッシュ経由に変更 + `step2/list` フラグメント追加 |
| `web/views/TableWizardView.kt` | 変更 | `wizardStep2View` を分解し `tableListContent` を抽出 |
| `resources/static/app.css` | 変更 | `.pagination-bar` / `.search-box` / `.empty-result` を追加 |
| `metadata/TableListPaginatorTest.kt` | **新規** | 絞り込み・境界値・クランプのユニットテスト |
| `metadata/TableMetadataCacheTest.kt` | **新規** | キャッシュヒット / TTL 失効 / invalidate のユニットテスト |
| `e2e/NewSyncFlowE2ETest.kt` | 変更 | 検索ボックス経由でテーブルを選ぶステップを追加 |

> `WebUiServer.kt` は `AppContext` を実際に生成している箇所を指す。ファイル名は実装時に確認する。

---

## 3. データ構造

### 3.1 `TablePage`

```kotlin
/** 絞り込み + ページ分割の結果。View はこれだけを見て描画する。 */
data class TablePage(
    /** 現在ページに表示する要素。 */
    val items: List<TableInfo>,
    /** 1 origin のページ番号 (クランプ済み)。 */
    val page: Int,
    val pageSize: Int,
    /** 絞り込み前の総件数。 */
    val totalCount: Int,
    /** 絞り込み後の件数。検索なしなら totalCount と同じ。 */
    val filteredCount: Int,
    /** 適用された検索語。未指定なら空文字。 */
    val query: String,
) {
    val totalPages: Int get() = if (filteredCount == 0) 1 else (filteredCount + pageSize - 1) / pageSize
    /** 表示範囲の開始 (1 origin)。0 件なら 0。 */
    val fromIndex: Int get() = if (filteredCount == 0) 0 else (page - 1) * pageSize + 1
    val toIndex: Int get() = minOf(page * pageSize, filteredCount)
    val hasPrev: Boolean get() = page > 1
    val hasNext: Boolean get() = page < totalPages
    val isFiltered: Boolean get() = query.isNotEmpty()
    val isEmpty: Boolean get() = items.isEmpty()
}
```

### 3.2 `TableListPaginator`

```kotlin
object TableListPaginator {
    const val DEFAULT_PAGE_SIZE = 50
    val PAGE_SIZE_OPTIONS = listOf(25, 50, 100, 200)

    /**
     * [all] を [query] で絞り込み、[page] / [pageSize] で切り出す。
     * - [query] は trim し、空なら絞り込みなし
     * - 照合対象は `schema.name` の表示ラベルとテーブル名の両方、大文字小文字は無視
     * - [page] は 1..totalPages にクランプ、[pageSize] は PAGE_SIZE_OPTIONS 外なら DEFAULT に丸める
     */
    fun paginate(
        all: List<TableInfo>,
        query: String?,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): TablePage
}

/** `schema.name` 形式の表示ラベル。View / 絞り込み / form value で共通利用する。 */
fun TableInfo.label(): String = if (schema != null) "$schema.$name" else name
```

`TableInfo.label()` は現在 `TableWizardView.kt:98` にインラインで書かれている
`(if (t.schema != null) "${t.schema}." else "") + t.name` を関数として切り出したもの。
form の `value` と絞り込み対象を同じ関数で生成し、ズレを防ぐ。

### 3.3 `TableMetadataCache`

```kotlin
/**
 * Connection 名をキーにテーブル一覧をキャッシュする。
 * ウィザード Step 2 の検索・ページ移動で JDBC メタデータの再取得が走らないようにする。
 */
class TableMetadataCache(
    private val ttl: Duration = Duration.ofMinutes(10),
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Entry(val tables: List<TableInfo>, val loadedAt: Instant)
    private val cache = ConcurrentHashMap<String, Entry>()

    /** キャッシュがあれば返し、なければ [loader] で取得して schema→name 順にソートして保存する。 */
    fun get(connectionName: String, loader: () -> List<TableInfo>): List<TableInfo>

    /** 指定 Connection のキャッシュを破棄する (Refresh ボタン / Connection 更新時)。 */
    fun invalidate(connectionName: String)
}
```

- TTL は 10 分。期限切れエントリは `get` 時に破棄して再取得する。
- ソートは `compareBy({ it.schema ?: "" }, { it.name })` を `get` 内で一度だけ適用する (D-04)。
- `ConcurrentHashMap` で保持。Web UI は単一プロセスなので分散キャッシュは不要。
- **Connection の更新・削除時に `invalidate` を呼ぶ。** 対象は `ConnectionsRoutes.kt` の
  `POST /connections/{name}` (更新) と削除ルート。古い接続情報のテーブル一覧が残るのを防ぐ。

---

## 4. UI 設計

### 4.1 Step 2 の画面構成

```
┌──────────────────────────────────────────────────────────┐
│ New Table — Step 2 of 4: Select Table                    │
│ Step 2 of 4: Table                                       │
│ Via connection: sf                                       │
│ ┌── form (action=/syncs/new/step3, method=get) ────────┐ │
│ │ [hidden] connection = sf                             │ │
│ │                                                      │ │
│ │ 🔍 [ Search tables...        ]  [50 ▾]  [↻ Refresh] │ │ ← 差し替え領域の外
│ │ ┌── div#table-list-container ──────────────────────┐ │ │
│ │ │ 全 1043 件中 51〜100 件を表示                     │ │ │ ← ここだけ htmx で差し替え
│ │ │ ┌ .scrollable-list ────────────────────────────┐ │ │ │
│ │ │ │ ○ Salesforce.Account                         │ │ │ │
│ │ │ │ ○ Salesforce.AccountFeed                     │ │ │ │
│ │ │ │ ...                                          │ │ │ │
│ │ │ └──────────────────────────────────────────────┘ │ │ │
│ │ │ .pagination-bar                                  │ │ │
│ │ │   [← Prev]  Page 2 / 21  [Next →]                │ │ │
│ │ └──────────────────────────────────────────────────┘ │ │
│ │                                                      │ │
│ │ Config name (短い識別子、英数小文字): [__________]   │ │ ← 差し替え領域の外 → 保持される (AC-5)
│ │ [← Back]                              [Next →]      │ │
│ └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

**検索ボックスと Config name 入力を差し替え領域 (`#table-list-container`) の外に置くことで、
htmx の swap があっても入力値が失われない (AC-5)。**

### 4.2 View 関数の分割

```kotlin
// ページ全体 (初回表示)。現行の wizardStep2View を置き換える。
fun HTML.wizardStep2View(
    ctx: AppContext,
    connectionName: String,
    pageResult: TablePage,
)

// 検索ボックス + ページサイズ選択 + Refresh。差し替え領域の外。
private fun FlowContent.tableSearchBar(connectionName: String, pageResult: TablePage)

// 差し替え対象。フラグメントエンドポイントからも同じものを呼ぶ。
fun FlowContent.tableListContent(pageResult: TablePage, connectionName: String)
```

`tableListContent` を `internal` ではなく `public` にし、`TableWizardRoutes` の
フラグメント応答から `createHTML().div { tableListContent(...) }` で再利用する。

### 4.3 htmx 属性

検索ボックス:
```kotlin
input(type = InputType.search, name = "q") {
    placeholder = "テーブル名で検索..."
    attributes["hx-get"] = "/syncs/new/step2/list"
    attributes["hx-trigger"] = "keyup changed delay:300ms, search"
    attributes["hx-target"] = "#table-list-container"
    attributes["hx-include"] = "#table-search-controls"   // connection / pageSize も送る
}
```

ページ送りボタン (`button type=button` にして form の submit を防ぐ):
```kotlin
button(type = ButtonType.button) {
    attributes["hx-get"] = "/syncs/new/step2/list?page=${pageResult.page + 1}"
    attributes["hx-target"] = "#table-list-container"
    attributes["hx-include"] = "#table-search-controls"
    +"Next →"
}
```

Refresh ボタンは同じ形で `?refresh=true&page=1` を付ける。

### 4.4 件数・空結果の表示

| 状態 | 表示 |
|---|---|
| 検索なし | `全 1043 件中 1〜50 件を表示` |
| 検索あり | `"oppo" に一致: 12 件中 1〜12 件を表示 (全 1043 件)` |
| 0 件 | `.empty-result` で `"zzz" に一致するテーブルはありません。検索条件を変えてください。` |

現行の `"Found ${tables.size} tables (showing first 100)"` は削除する。

---

## 5. ルーティング設計

### 5.1 `GET /syncs/new/step2` (変更)

```kotlin
get("/syncs/new/step2") {
    val connectionName = call.request.queryParameters["connection"]
        ?: return@get call.respondRedirect("/syncs/new")
    val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
        ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)

    val all = ctx.tableMetadataCache.get(connectionName) { loadTables(jdbc) }
    val pageResult = TableListPaginator.paginate(all, query = null, page = 1)
    call.respondHtml { wizardStep2View(ctx, connectionName, pageResult) }
}
```

### 5.2 `GET /syncs/new/step2/list` (新規)

| クエリパラメータ | 型 | 既定値 | 内容 |
|---|---|---|---|
| `connection` | String | — | 必須。未指定なら 400 |
| `q` | String? | `""` | 検索語 |
| `page` | Int | `1` | 1 origin。範囲外は `paginate` 側でクランプ |
| `pageSize` | Int | `50` | 選択肢外は 50 に丸める |
| `refresh` | Boolean | `false` | `true` ならキャッシュを破棄してから取得 |

```kotlin
get("/syncs/new/step2/list") {
    val connectionName = call.request.queryParameters["connection"]
        ?: return@get call.respondText("missing connection", status = HttpStatusCode.BadRequest)
    val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
        ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)

    if (call.request.queryParameters["refresh"] == "true") {
        ctx.tableMetadataCache.invalidate(connectionName)
    }
    val all = ctx.tableMetadataCache.get(connectionName) { loadTables(jdbc) }
    val pageResult = TableListPaginator.paginate(
        all,
        query = call.request.queryParameters["q"],
        page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
        pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: TableListPaginator.DEFAULT_PAGE_SIZE,
    )
    val html = createHTML().div { tableListContent(pageResult, connectionName) }
    call.respondText(html, ContentType.Text.Html)
}
```

`loadTables(jdbc)` は両ルートで共通の private ヘルパー:
```kotlin
private fun loadTables(jdbc: JdbcConfig): List<TableInfo> =
    JdbcConnectionProvider(jdbc).use { provider ->
        provider.connection().use { conn -> JdbcMetadataInspector(conn).listTables() }
    }
```

---

## 6. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| `GET /syncs/new/step3` 以降 | **なし**。ラジオの `name="table"` / value 形式 (`schema.name`) は不変 | — |
| `cli/InitTableCommand.kt` | **なし**。`listTables()` を直接呼んでおりキャッシュを経由しない | — |
| `JdbcMetadataInspector` | **なし**。既存メソッドは変更しない (D-07) | — |
| `NewSyncFlowE2ETest.kt:67` | ラジオは残るが、対象テーブルが 1 ページ目にない可能性がある | 検索ボックスに入力してから選択する手順に変更 |
| `ConnectionsRoutes` 更新・削除 | キャッシュが古くなる | 更新・削除時に `invalidate(name)` を呼ぶ |
| `AppContext` のコンストラクタ | 引数が 1 つ増える | 生成箇所とテストのヘルパーを更新 |
| メモリ使用量 | Connection ごとに `List<TableInfo>` を保持。1000 件でも数百 KB 程度 | TTL 10 分で自然に解放。上限管理は不要と判断 |

---

## 7. テスト設計

### 7.1 `TableListPaginatorTest` (ユニット)

| ケース | 期待 |
|---|---|
| 150 件 / page=1 / size=50 | `items.size == 50`、`fromIndex=1`、`toIndex=50`、`totalPages=3`、`hasPrev=false`、`hasNext=true` |
| 150 件 / page=3 | `hasNext == false`、`toIndex == 150` |
| 150 件 / page=99 (範囲外) | `page` が 3 にクランプされる |
| 150 件 / page=0 or -1 | `page` が 1 にクランプされる |
| `q="acc"` で大文字小文字混在 | `Account` / `ACCOUNTS` / `myaccount` すべてヒット |
| `q="sf."` (スキーマ名) | `schema="sf"` のテーブルがヒット |
| `q="  "` (空白のみ) | 絞り込みなし扱い、`isFiltered == false` |
| `q="zzz"` (0 件) | `items` 空、`filteredCount=0`、`totalPages=1`、`fromIndex=0`、`isEmpty=true` |
| `pageSize=7` (選択肢外) | 50 に丸められる |
| 絞り込み後のページング | `filteredCount` ベースで `totalPages` が計算される |

### 7.2 `TableMetadataCacheTest` (ユニット)

| ケース | 期待 |
|---|---|
| 初回 get | loader が 1 回呼ばれる |
| 2 回目 get (TTL 内) | loader が呼ばれない (呼び出し回数が 1 のまま) |
| TTL 経過後 (`Clock.fixed` を進める) | loader が再度呼ばれる |
| `invalidate` 後 | loader が再度呼ばれる |
| 別の connection 名 | それぞれ独立して loader が呼ばれる |
| ソート | loader が順不同で返しても `schema` → `name` 順で返る |

### 7.3 E2E (`NewSyncFlowE2ETest`)

Step 2 で以下を追加検証する:
1. 検索ボックスにテーブル名の一部を入力 → `#table-list-container` が差し替わる
2. 絞り込み後のリストから対象テーブルのラジオを選択
3. Config name を入力して Next → Step 3 に到達する (検索操作で Config name が消えていないこと)

### 7.4 手動確認

- テーブル数 100 超のデータソース (Salesforce) で 101 件目以降を選択して Sync を作成できること (AC-1)

---

## 8. 受け入れ条件との対応

| AC | 対応する設計 |
|---|---|
| AC-1 | D-01 ページング + 5.2 フラグメント (`take(100)` の削除) |
| AC-2 | 3.2 `paginate` の絞り込み仕様 + 7.1 |
| AC-3 | 4.3 ページ送りボタンが `hx-include` で `q` を同送 |
| AC-4 | 4.4 件数表示 |
| AC-5 | 4.1 Config name を差し替え領域の外に配置 |
| AC-6 | 4.4 `.empty-result` |
| AC-7 | D-02 `TableMetadataCache` + 7.2 |
| AC-8 | 7.3 E2E 更新 |
| AC-9 | tasklist の最終タスクで `./gradlew detekt test` を実行 |
