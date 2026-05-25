# Phase 2-B: Web UI + SqliteConfigSource + Salesforce 新階層移行 — 設計

| 項目 | 内容 |
|---|---|
| フェーズ | 2-B |
| 作成日 | 2026-05-22 |
| 関連 | [requirements.md](./requirements.md) |
| ステータス | ドラフト |

---

## 1. 全体アーキテクチャ

### 1.1 プロセスモデル

Phase 2-A の `serve-all` ライクな**1 JVM で複数テーブル分の Adapter を並行起動**するモデルを継承し、Web UI も同一 JVM に組み込む（最小依存・シンプル運用）。

```
┌──────────────────────────────────────────────────────────┐
│  adapter web-ui (1 JVM Process, ホスト)                  │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Ktor HTTP Server (port 8080, 127.0.0.1)            │ │
│  │  routes:                                           │ │
│  │   /                  ダッシュボード                │ │
│  │   /tables, /tables/{name}                          │ │
│  │   /connections, /connections/{name}                │ │
│  │   /drivers, /drivers/{filename}                    │ │
│  │   /runtime/sse        Server-Sent Events           │ │
│  └────────────────────────────────────────────────────┘ │
│                          ↕                               │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Application Service Layer                          │ │
│  │  - ConfigSource (Yaml / Sqlite 切替可)             │ │
│  │  - MultiAdapterRunner (Phase 2-A の既存資産)       │ │
│  │  - JdbcDriverManager (新規, lib/ 操作)             │ │
│  │  - JdbcMetadataInspector (既存)                    │ │
│  └────────────────────────────────────────────────────┘ │
│                          ↕                               │
│  ┌────────────────────────────────────────────────────┐ │
│  │ TableAdapterServer × N (Phase 2-A 既存)            │ │
│  │  各テーブルが独立した gRPC ポート (18001, 18002…)  │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
       ↑              ↓                       ↓
    browser   lib/, config/, run/    各 Agent コンテナ
                config.db (SQLite)
```

### 1.2 主要な設計判断

| ID | 判断 | 根拠 |
|---|---|---|
| D-01 | **Web UI と Adapter は同一 JVM** | 開発・運用の単純化。Web UI から MultiAdapterRunner を直接呼べる。Phase 2-A の `serve-all` を機能拡張する形 |
| D-02 | **ConfigSource は Yaml/Sqlite 切替可、デフォルト Yaml** | 既存ユーザの後方互換維持。新規ユーザは Sqlite モードを推奨 |
| D-03 | **Web UI はサーバサイドレンダリング + HTMX** | SPA 不要。Kotlin DSL でテンプレ + 部分更新で動的体験 |
| D-04 | **JDBC Driver は lib/ で管理継続** | 既存運用と一致。Web UI から lib/ に upload する |
| D-05 | **アクティベーションは外部プロセス + expect/PTY** | CData driver の `-l` プロンプトを自動入力。Phase 2-A 検証で確立済みの手法 |
| D-06 | **SQLite は JSON カラム中心の軽量スキーマ** | 4 テーブル分の YAML 構造をそのまま JSON 化。export-yaml が容易 |
| D-07 | **Web UI のリスナーは `127.0.0.1` のみデフォルト** | セキュリティ。要件 G-01 |

---

## 2. ConfigSource: Yaml/Sqlite 切替

### 2.1 ConfigSourceFactory

```kotlin
object ConfigSourceFactory {
    enum class Mode { YAML, SQLITE }

    fun create(
        mode: Mode = detectMode(),
        configDir: Path = Path.of("./config"),
        sqlitePath: Path = Path.of("./config/config.db"),
    ): ConfigSource = when (mode) {
        Mode.YAML -> YamlConfigSource(configDir)
        Mode.SQLITE -> SqliteConfigSource(sqlitePath)
    }

    private fun detectMode(): Mode {
        // 1. 明示指定: 環境変数 CONFIG_SOURCE
        System.getenv("CONFIG_SOURCE")?.let {
            return Mode.valueOf(it.uppercase())
        }
        // 2. 自動判定: config.db があれば SQLITE
        if (Files.exists(Path.of("./config/config.db"))) return Mode.SQLITE
        // 3. デフォルト: YAML
        return Mode.YAML
    }
}
```

CLI コマンド（既存 `serve`, `serve-all`, `web-ui` など）から `--config-source yaml|sqlite` で上書き可能。

### 2.2 SqliteConfigSource スキーマ

シンプルさ重視で**2 テーブル + JSON カラム**構造。

```sql
-- 共通 JDBC 設定 (config/jdbc/<name>.yaml 相当)
CREATE TABLE IF NOT EXISTS shared_jdbcs (
    name TEXT PRIMARY KEY NOT NULL,
    driver_class TEXT NOT NULL,
    url_template TEXT NOT NULL,    -- ${VAR} 展開前の URL (検索・表示用)
    config_json TEXT NOT NULL,     -- JdbcConfig 全体 (JSON)
    updated_at INTEGER NOT NULL    -- epoch millis
);

CREATE INDEX IF NOT EXISTS idx_shared_jdbcs_updated_at
    ON shared_jdbcs(updated_at);

-- テーブル定義 (config/tables/<name>/*.yaml 4 ファイル相当)
CREATE TABLE IF NOT EXISTS tables (
    name TEXT PRIMARY KEY NOT NULL,           -- ConfigSource 上の識別名 (例: account)
    db_table_name TEXT NOT NULL,              -- TableConfig.name (例: Account)
    jdbc_ref TEXT,                             -- shared_jdbcs.name (NULL なら個別 jdbc)
    server_json TEXT NOT NULL,                 -- ServerConfig (JSON)
    inline_jdbc_json TEXT,                     -- jdbc_ref が NULL の場合の JdbcConfig
    table_json TEXT NOT NULL,                  -- TableConfig (JSON)
    capability_json TEXT NOT NULL,             -- CapabilityConfig (JSON)
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (jdbc_ref) REFERENCES shared_jdbcs(name) ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tables_jdbc_ref ON tables(jdbc_ref);

-- スキーマバージョン管理
CREATE TABLE IF NOT EXISTS schema_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT OR IGNORE INTO schema_meta(key, value) VALUES ('version', '1');
```

WAL モード有効化: 接続時 `PRAGMA journal_mode=WAL;`。

### 2.3 マイグレーションコマンド

```
adapter migrate-to-sqlite [--config-dir DIR] [--sqlite-path FILE]
adapter export-yaml [--sqlite-path FILE] [--out-dir DIR]
```

- `migrate-to-sqlite`: `YamlConfigSource.listTables()` → 各テーブル `loadTableSet()` → `SqliteConfigSource.saveTableSet()`
- `export-yaml`: 逆方向。バックアップや人間レビュー用

---

## 3. Web UI: Ktor + kotlinx.html + HTMX

### 3.1 技術スタック

| レイヤ | 選定 | バージョン目安 |
|---|---|---|
| HTTP サーバ | Ktor Netty | 3.0.x |
| テンプレ | `kotlinx.html` DSL | 0.11.x |
| 部分更新 | HTMX | 1.9.x (CDN 配信) |
| CSS | [Pico.css](https://picocss.com/) | 2.0.x (CDN) |
| JSON | kotlinx-serialization-json (既存) | 1.7.x |
| Form parsing | Ktor 標準 | - |
| Multipart | Ktor 標準 | - |
| SSE | Ktor 標準 | - |

依存追加: `io.ktor:ktor-server-netty`, `ktor-server-html-builder`, `ktor-server-sse`, `ktor-server-call-logging`, `org.xerial:sqlite-jdbc`

### 3.2 ルーティング全体図

```
GET    /                              ダッシュボード (テーブル一覧 + 稼働状況)
GET    /tables                        テーブル一覧 (HTMX で同上をパーシャル)
GET    /tables/new                    新規ウィザード step 1 (コネクション選択)
GET    /tables/new/step2/{conn}       step 2 (テーブル選択)
GET    /tables/new/step3/{conn}/{tbl} step 3 (カラム選択)
GET    /tables/new/step4              step 4 (マッピング + capability)
POST   /tables                        作成完了
GET    /tables/{name}                 詳細
GET    /tables/{name}/edit            編集
PUT    /tables/{name}                 保存
DELETE /tables/{name}                 削除
POST   /tables/{name}/start           Adapter 起動
POST   /tables/{name}/stop            Adapter 停止
POST   /tables/{name}/restart         再起動

GET    /connections                   JDBC 接続一覧
GET    /connections/new               新規
POST   /connections                   作成
GET    /connections/{name}            詳細
GET    /connections/{name}/edit       編集
PUT    /connections/{name}            保存
DELETE /connections/{name}            削除
POST   /connections/{name}/test       接続テスト (HTMX で結果フラグメント返却)

GET    /drivers                       ドライバ一覧
POST   /drivers/upload                JAR アップロード (multipart)
POST   /drivers/{filename}/activate   トライアルアクティベーション
DELETE /drivers/{filename}            削除

GET    /runtime/sse                   Server-Sent Events で active-adapters 配信
GET    /static/*                      htmx.min.js, pico.min.css 等の静的アセット
```

### 3.3 共通レイアウト

```
┌─ Header ─────────────────────────────────────────────────────────┐
│  ⚙ Adapter Console v0.3.0                                        │
│  [ Dashboard ] [ Tables ] [ Connections ] [ Drivers ]            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  (各ページコンテンツ)                                            │
│                                                                  │
├─ Footer ─────────────────────────────────────────────────────────┤
│  ConfigSource: yaml  |  Adapters: 2 active  |  © 2026 CData      │
└──────────────────────────────────────────────────────────────────┘
```

### 3.4 画面別モックアップ

#### S-01: ダッシュボード (`/`)

```
┌─ Adapter Console — Dashboard ────────────────────────────────────┐
│                                                                  │
│  Active Adapters (2)                          [ Refresh ] [ ⏹ ]  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Name             Port    Started           Status   Action │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ account         18001   2026-05-22 16:30  ●SERVING  [stop] │ │
│  │ gs-opportunity  18003   2026-05-22 16:32  ●SERVING  [stop] │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Quick Actions                                                   │
│  [ + New Table ]  [ Upload Driver ]  [ Start All ]               │
│                                                                  │
│  ⚠ Phase 1 構成を検出: config/ 直下に server.yaml 等あり          │
│     [ Migrate to multi-table ]                                   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-02: テーブル一覧 (`/tables`)

```
┌─ Tables ─────────────────────────────────────────────────────────┐
│                                                                  │
│  Tables (3)                              [ + New Table ]         │
│  Filter: [____________]  Status: [All ▼]                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Name          DB Table        JDBC          Status  Action │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ account       Account         salesforce   ●Running [stop] │ │
│  │               (ref)                                  [edit]│ │
│  │ contact       Contact         salesforce   ○Stopped [start]│ │
│  │               (ref)                                  [edit]│ │
│  │ gs-opportunity CRM Data       googlesheets ●Running [stop] │ │
│  │               sugimotok…       (ref)                 [edit]│ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-03: テーブル詳細 (`/tables/{name}`)

```
┌─ Tables › account ───────────────────────────────────────────────┐
│                                                                  │
│  account  ●Running on port 18001                                 │
│  [ Stop ] [ Restart ] [ Edit ] [ Delete ]                        │
│                                                                  │
│  ── Server ────────────────────────────────                      │
│    Port: 18001                                                   │
│    Bind: 0.0.0.0       Plaintext: true                           │
│                                                                  │
│  ── JDBC ──────────────────────────────────                      │
│    Reference: salesforce  →  [ View connection ]                 │
│                                                                  │
│  ── Table ─────────────────────────────────                      │
│    DB Table: Account                                             │
│    Primary Key: id (kintone) ↔ Id (JDBC)                         │
│    Columns (6):                                                  │
│      name           ↔ Name           TEXT                        │
│      revenue        ↔ AnnualRevenue  NUMBER                      │
│      industry       ↔ Industry       SELECTION (7 options)       │
│      created_at     ↔ CreatedDate    DATETIME                    │
│      phone          ↔ Phone          TEXT                        │
│      website        ↔ Website        TEXT                        │
│                                                                  │
│  ── Capability ────────────────────────                          │
│    Record ID Type: TEXT                                          │
│    CRUD: ☑Select ☑Insert ☑Update ☑Delete ☑Count                  │
│    Count Strategy: ACTUAL                                        │
│    Filterable: id, name, industry, created_at                    │
│    Sortable: id, name, revenue, created_at                       │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-04: テーブル編集 (`/tables/{name}/edit`)

```
┌─ Tables › account › Edit ────────────────────────────────────────┐
│                                                                  │
│  Edit table: account                                             │
│  [ Server ] [ JDBC ] [ Columns ] [ Capability ]                  │
│  ─────────                                                       │
│                                                                  │
│  ── Server ────────────────────────────────                      │
│    Port:           [ 18001         ]   (0 or "auto" for OS)      │
│    Bind Address:   [ 0.0.0.0       ]                             │
│    Plaintext:      [✓]                                           │
│                                                                  │
│  ── JDBC ──────────────────────────────────                      │
│    ⚪ Use shared JDBC reference                                  │
│       Reference:   [ salesforce  ▼ ]                             │
│    ⚪ Use inline JDBC (override)                                 │
│       Driver:      [ cdata.jdbc.sales… ▼ ]                       │
│       URL:         [ jdbc:salesforce:…  ]                        │
│                                                                  │
│  (Columns / Capability も同様にセクション形式)                   │
│                                                                  │
│  ─────────────────────────────────────────                       │
│  [ Cancel ]  [ Save ]  [ Save & Restart ]                        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-05: 新規ウィザード Step 1 — Connection 選択

```
┌─ Tables › New › Step 1 of 4 ─────────────────────────────────────┐
│                                                                  │
│  ●─────○─────○─────○                                             │
│  Conn   Table  Cols  Map                                         │
│                                                                  │
│  ── Select JDBC Connection ────────────────                      │
│                                                                  │
│    ⚪ salesforce                                                  │
│       cdata.jdbc.salesforce.SalesforceDriver                     │
│       jdbc:salesforce:AuthScheme=OAuth;…                         │
│                                                                  │
│    ⚪ googlesheets                                                │
│       cdata.jdbc.googlesheets.GoogleSheetsDriver                 │
│       jdbc:googlesheets:SpreadsheetId=…                          │
│                                                                  │
│    ⚪ + Create new connection                                     │
│                                                                  │
│  ─────────────────────────────────────────                       │
│  [ Cancel ]                                       [ Next → ]     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-06: 新規ウィザード Step 2 — テーブル選択

```
┌─ Tables › New › Step 2 of 4 ─────────────────────────────────────┐
│                                                                  │
│  ●─────●─────○─────○                                             │
│  Conn   Table  Cols  Map                                         │
│  via salesforce                                                  │
│                                                                  │
│  ── Select DB Table ───────────────────────                      │
│    Filter: [_______________]                                     │
│                                                                  │
│    ⚪ Account                                                     │
│    ⚪ Contact                                                     │
│    ⚪ Opportunity                                                 │
│    ⚪ Lead                                                        │
│    ⚪ Campaign                                                    │
│    (1500+ tables)                                                │
│                                                                  │
│    Config name (短い識別子): [ contact          ]                │
│                                                                  │
│  ─────────────────────────────────────────                       │
│  [ ← Back ] [ Cancel ]                            [ Next → ]     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-07: 新規ウィザード Step 3 — カラム選択

```
┌─ Tables › New › Step 3 of 4 ─────────────────────────────────────┐
│                                                                  │
│  ●─────●─────●─────○                                             │
│  Conn   Table  Cols  Map                                         │
│  via salesforce / Contact                                        │
│                                                                  │
│  ── Select Columns to Map ─────────────────                      │
│    [ Select all ] [ Deselect all ]    Found 45 columns           │
│                                                                  │
│    ☑ Id              VARCHAR(18)  (Primary Key)                  │
│    ☑ FirstName       VARCHAR(40)                                 │
│    ☑ LastName        VARCHAR(80)                                 │
│    ☑ Email           VARCHAR(80)                                 │
│    ☑ Phone           VARCHAR(40)                                 │
│    ☐ Fax             VARCHAR(40)                                 │
│    ☐ MailingStreet   VARCHAR(255)                                │
│    ☑ CreatedDate     TIMESTAMP                                   │
│    (scroll for more...)                                          │
│                                                                  │
│  ─────────────────────────────────────────                       │
│  [ ← Back ] [ Cancel ]                            [ Next → ]     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-08: 新規ウィザード Step 4 — マッピング & Capability

```
┌─ Tables › New › Step 4 of 4 ─────────────────────────────────────┐
│                                                                  │
│  ●─────●─────●─────●                                             │
│  Conn   Table  Cols  Map                                         │
│                                                                  │
│  ── Auto-generated Mapping ────────────────                      │
│    Primary Key: id ↔ Id  (TEXT — auto detected)                  │
│                                                                  │
│    kintone field_id    JDBC column       Type                    │
│    [ first_name    ]  [ FirstName    ]  [ TEXT     ▼ ]   [×]     │
│    [ last_name     ]  [ LastName     ]  [ TEXT     ▼ ]   [×]     │
│    [ email         ]  [ Email        ]  [ TEXT     ▼ ]   [×]     │
│    [ phone         ]  [ Phone        ]  [ TEXT     ▼ ]   [×]     │
│    [ created_at    ]  [ CreatedDate  ]  [ DATETIME ▼ ]   [×]     │
│                                                                  │
│  ── Capability ────────────────────────                          │
│    Record ID Type: ⚪TEXT  ⚫NUMBER                                │
│    Filterable: [ id ☑ ] [ email ☑ ] [ phone ☐ ]                  │
│    Sortable:   [ id ☑ ] [ last_name ☑ ] [ created_at ☑ ]         │
│    Server Port: [ 18002         ]  (or auto)                     │
│                                                                  │
│  ─────────────────────────────────────────                       │
│  [ ← Back ] [ Cancel ]                  [ Save & Start ]         │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-09: Connections 一覧 (`/connections`)

```
┌─ Connections ────────────────────────────────────────────────────┐
│                                                                  │
│  JDBC Connections (2)                  [ + New Connection ]      │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Name          Driver                       URL    Action   │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ salesforce    cdata.jdbc.salesforce…  …Basic   [test][edit]│ │
│  │ googlesheets  cdata.jdbc.googleshe…   …Spread  [test][edit]│ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-10: Connection 新規 (`/connections/new`) — 1 画面動的フォーム

ドライバ選択 → HTMX で `sys_connection_props` 取得 → 同一画面の下部にフォームを差し替え。
URL プレビューは画面下部 sticky 表示で常に見える。

```
┌─ Connections › New ─────────────────────────────────────────────────────┐
│                                                                         │
│  Name:         [ salesforce_prod  ]   (識別子、英数小文字)              │
│                                                                         │
│  JDBC Driver:  [ cdata.jdbc.salesforce.SalesforceDriver ▼ ] ⟳           │
│                (lib/ から自動検出 / Drivers 画面で追加)                  │
│                                                                         │
│  ┌─── ↓ ドライバ選択でこの下が動的に差し替わる (HTMX hx-trigger) ───┐ │
│  │                                                                    │ │
│  │  Configure: SalesforceDriver  (sys_connection_props: 268 props)    │ │
│  │  Filter: [ ☑ Required ] [ ☐ Show all ]  Search: [_________]        │ │
│  │                                                                    │ │
│  │  ━━ Authentication ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │ │
│  │  AuthScheme *               [ Basic           ▼ ]                  │ │
│  │    認証スキーム                                                    │ │
│  │  User * 🔐                  [ ${SF_USER}                ]          │ │
│  │    Salesforce アカウントユーザ名                                   │ │
│  │  Password * 🔐PASSWORD      [ ${SF_PASSWORD}            ] (masked) │ │
│  │    認証に使用するパスワード                                        │ │
│  │  SecurityToken * 🔐         [ ${SF_SECURITY_TOKEN}      ]          │ │
│  │    Trial 環境ではセキュリティトークンが必須                        │ │
│  │  UseSandbox *               [ ☐ ] (false)                          │ │
│  │    サンドボックス組織に接続                                        │ │
│  │                                                                    │ │
│  │  ━━ Connection (▶ click to expand 22 props) ━━━━━━━━━━━━━━━━━━     │ │
│  │  ━━ OAuth (▶ 31 props) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━     │ │
│  │  ━━ Schema (▶ 17 props) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━     │ │
│  │  ━━ Caching / Firewall / Proxy / SSL / Logging (▶) ━━━━━━━━━━━     │ │
│  │                                                                    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ── Pool Settings ──────────────────                                    │
│    Pool size:        [ 10  ]                                            │
│    Connection timeout (ms): [ 30000 ]                                   │
│                                                                         │
│  [ Test connection ]                                                    │
│    ●Connected — CData 2025J 25.0.9540                                   │
│                                                                         │
│  ─────────────────────────────────────────                              │
│  [ Cancel ]                                              [ Save ]       │
│                                                                         │
├═════════════════════════════════════════════════════════════════════════┤
│ Sticky bottom: JDBC URL Preview                              [ ✎ edit ] │
│ jdbc:salesforce:AuthScheme=Basic;User=${SF_USER};Password=${SF_PASSWORD}│
│ ;SecurityToken=${SF_SECURITY_TOKEN};                                    │
├═════════════════════════════════════════════════════════════════════════┤
└─────────────────────────────────────────────────────────────────────────┘
```

**初期表示ルール**
- カテゴリ `Authentication` だけ展開（Required はここに集中）
- それ以外のカテゴリは折りたたみ（クリックで展開）
- フィルター「Required」がデフォルト ON で、各カテゴリ内も Required + Visible のみ表示
- 「Show all」に切り替えると全 268 プロパティ表示

**Hierarchy（依存関係）の扱い**
- `Hierarchy = "AuthScheme=OAuth,OAuthClient"` のような条件は、Phase 2-B では**ヒント表示のみ**:
  ```
  OAuthClientId 🔐  [ _______ ]
    ⓘ AuthScheme が OAuthClient / AzureAD / OAuthPKCE の場合に有効
  ```
- 動的な enable/disable は Phase 2-C 送り（実装複雑度を抑える）

**Drivers が一つも追加されてない場合**
- 「Drivers 画面で JAR をアップロードしてください」リンクを表示

#### S-10 設計詳細: 動的プロパティフォーム実装

**1. ConnectionProperty データクラス（実機検証結果に基づく）**

```kotlin
data class ConnectionProperty(
    val propertyName: String,      // JDBC URL キー (例: "User", "Password")
    val displayName: String,       // 表示用ラベル (例: "User", "Password")
    val shortDescription: String,  // 日本語の説明
    val type: PropertyType,        // STRING / INT / BOOLEAN
    val defaultValue: String?,
    val allowedValues: List<String>, // Values カンマ区切りをパース。空なら自由入力
    val category: String,          // "Authentication" / "Connection" / "OAuth" / ... / "" (未分類)
    val required: Boolean,
    val sensitivity: Sensitivity,  // NONE / SENSITIVE / PASSWORD
    val visible: Boolean,          // false なら UI 非表示
    val hierarchy: String,         // "AuthScheme=Basic,OAuthPassword" 等の表示条件ヒント
    val ordinal: Int,              // カテゴリ内表示順
    val categoryOrdinal: Int,
)

enum class PropertyType {
    STRING,    // sys_connection_props.Type = "String"
    INT,       // = "int"
    BOOLEAN,   // = "boolean"
    ;
    fun isEnum(allowedValues: List<String>) = allowedValues.isNotEmpty()
}

enum class Sensitivity {
    NONE,       // sys_connection_props.Sensitivity = ""
    SENSITIVE,  // = "SENSITIVE" (トークン類、入力欄は password 風)
    PASSWORD,   // = "PASSWORD" (本物のパスワード)
}
```

**2. プロパティ取得 (JdbcConnectionPropertyInspector)**

```kotlin
class JdbcConnectionPropertyInspector(
    private val driverClass: String,
    private val driverJar: Path,
) {
    fun listProperties(): List<ConnectionProperty> {
        JdbcConnectionProvider.loadDriver(driverJar.toString(), driverClass)
        val jdbcPrefix = jdbcPrefixOf(driverClass)  // "jdbc:salesforce:" 等
        return DriverManager.getConnection("$jdbcPrefix:").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(QUERY).use { rs -> rs.parseRows() }
            }
        }
    }

    /** ドライバクラス名から JDBC URL プレフィックスを推定 */
    private fun jdbcPrefixOf(driverClass: String): String {
        // "cdata.jdbc.salesforce.SalesforceDriver" → "jdbc:salesforce"
        val parts = driverClass.split('.')
        require(parts.size >= 3 && parts[0] == "cdata" && parts[1] == "jdbc") {
            "Not a CData JDBC driver class: $driverClass"
        }
        return "jdbc:${parts[2]}"
    }

    companion object {
        // Visible=true のみ取り出して、Ordinal でソート
        private const val QUERY = """
            SELECT PropertyName, Name, ShortDescription, Type, Values, Default,
                   Category, Required, Sensitivity, Visible, Hierarchy,
                   Ordinal, CatOrdinal
            FROM sys_connection_props
            ORDER BY CatOrdinal, Ordinal
        """
    }
}
```

**3. UI レンダリング規約**

| Property type | input 要素 |
|---|---|
| `STRING` + `allowedValues.isEmpty()` | `<input type="text">` |
| `STRING` + `allowedValues.isNotEmpty()` | `<select>` (enum dropdown) |
| `BOOLEAN` | `<input type="checkbox">` |
| `INT` | `<input type="number">` |
| `Sensitivity.PASSWORD` | `<input type="password">` |
| `Sensitivity.SENSITIVE` | `<input type="password">` + 表示切替ボタン |

各プロパティは以下の DSL コンポーネント:
```kotlin
fun FlowContent.propertyRow(prop: ConnectionProperty, value: String?) {
    div("property-row") {
        label {
            +prop.displayName
            if (prop.required) span("required") { +" *" }
            sensitivityBadge(prop.sensitivity)  // 🔐 / 🔐PASSWORD
        }
        renderInput(prop, value)
        small("description") { +prop.shortDescription }
        if (prop.hierarchy.isNotEmpty()) {
            small("hierarchy-hint") { +"ⓘ ${humanizeHierarchy(prop.hierarchy)}" }
        }
    }
}
```

**4. URL 自動生成**

入力中に HTMX `hx-trigger="keyup changed delay:200ms"` でフォーム全体を POST し、サーバ側で URL 生成 → URL プレビュー部分を返す:

```kotlin
fun generateJdbcUrl(jdbcPrefix: String, props: Map<String, String>, defaults: Map<String, String>): String {
    val pairs = props
        .filterValues { it.isNotBlank() }
        .filter { (k, v) -> v != defaults[k] }  // デフォルト値は URL から省略
        .map { (k, v) -> "$k=$v" }
        .joinToString(";")
    return "$jdbcPrefix:$pairs;"
}
```

**5. URL プレビュー (下部 sticky)**

```html
<div class="url-preview-sticky" hx-swap-oob="true">
  <code>jdbc:salesforce:User=${SF_USER};...</code>
  <button onclick="copyUrl()">📋 Copy</button>
  <button onclick="toggleEdit()">✎ Edit</button>
</div>
```

CSS:
```css
.url-preview-sticky {
  position: sticky;
  bottom: 0;
  background: var(--card-bg);
  border-top: 1px solid var(--border);
  padding: 1rem;
  font-family: monospace;
  word-break: break-all;
}
```

**6. 手動編集モード**

「✎ Edit」で textarea に切り替え、編集後は **手動編集モード優先** で保存。フォームには戻れる（「← Form に戻す」ボタン）が、textarea の編集内容は失われる旨を警告。

**7. キャッシュ**

`(driverClass, driverJar.lastModified())` をキーにメモリキャッシュ。Drivers 画面での upload/delete 時に無効化。

**8. フォールバック**

非 CData ドライバや `sys_connection_props` が取れないケースでは、従来の URL textarea + Driver class 入力に戻す（feature flag で切り替え）。

**9. Phase 2-B では未対応・将来検討**

- Hierarchy 条件評価による動的 enable/disable (Phase 2-C)
- 「全 268 プロパティ」表示時の仮想スクロール
- カテゴリ別の高度な検索（プロパティ間関連グラフ等）

#### S-11: Drivers 一覧 (`/drivers`)

```
┌─ Drivers ────────────────────────────────────────────────────────┐
│                                                                  │
│  JDBC Drivers in lib/ (2)              [ ⬆ Upload JAR ]          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Filename                       Driver Class    License     │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ cdata.jdbc.salesforce.jar     cdata.jdbc.s… ●Activated     │ │
│  │ (8.2 MB)                                       [delete]    │ │
│  │ cdata.jdbc.googlesheets.jar   cdata.jdbc.g… ●Activated     │ │
│  │ (7.9 MB)                                       [delete]    │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Drop a .jar file here, or:                                      │
│  ┌──────────────────────────────────────┐                        │
│  │ [ Choose File... ]  No file chosen   │  [ Upload ]            │
│  └──────────────────────────────────────┘                        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### S-12: Driver アクティベーション (`/drivers/{filename}/activate`)

```
┌─ Drivers › cdata.jdbc.googlesheets.jar › Activate ───────────────┐
│                                                                  │
│  ── Trial License Activation ──────────────                      │
│                                                                  │
│    アップロードされたドライバは未アクティベーションです。        │
│    CData のトライアルライセンスを取得します（無料・30 日）。     │
│                                                                  │
│    Name:           [ kazuya sugimoto      ]                      │
│    Email:          [ sugimotok@cdata.com  ]                      │
│    Product Key:    [ TRIAL                ]  (固定)              │
│                                                                  │
│    ⚠ 入力された情報は CData ライセンスサーバに送信されます      │
│                                                                  │
│  ─────────────────────────────────────────                       │
│  [ Cancel ]                              [ Activate ]            │
│                                                                  │
│  (アクティベーション中はステータスバーで進捗表示)                │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.4 部分更新方針（HTMX）

| 操作 | リクエスト | 返却 |
|---|---|---|
| 起動ボタンクリック | `POST /tables/{name}/start` (hx-post) | 起動状況フラグメント (`<tr>` 1 行) |
| 接続テスト | `POST /connections/{name}/test` (hx-post) | 成功/失敗バッジ |
| ウィザード Step 進行 | `GET /tables/new/stepN/...` (hx-get) | ステップフォームフラグメント |
| 稼働状況リフレッシュ | SSE event `active-adapters` | テーブル一覧 status セル群を JS で更新 |

### 3.5 サーバサイド構造

```
src/main/kotlin/com/cdata/kintone/adapter/web/
├ WebUiCommand.kt              # CLI: adapter web-ui --port 8080
├ WebUiServer.kt               # Ktor Application セットアップ
├ Services.kt                  # AppContext (ConfigSource + Runner + DriverManager 集約)
├ routes/
│  ├ TablesRoutes.kt
│  ├ TableWizardRoutes.kt
│  ├ ConnectionsRoutes.kt
│  ├ DriversRoutes.kt
│  └ RuntimeRoutes.kt          # SSE
├ views/
│  ├ Layout.kt                 # 共通レイアウト DSL
│  ├ Components.kt             # ボタン / フォーム / バッジ
│  ├ TablesView.kt
│  ├ TableWizardView.kt
│  ├ ConnectionsView.kt
│  ├ DriversView.kt
│  └ DashboardView.kt
└ form/
   └ FormParsing.kt            # ParameterValueBag → ServerConfig / CapabilityConfig 復元
```

---

## 4. JdbcDriverManager: lib/ 配下の管理

### 4.1 責務

1. **一覧 (I-01)**: `lib/*.jar` をスキャン、JAR 内 `META-INF/services/java.sql.Driver` を読んでドライバクラス検出
2. **アップロード (I-02)**: multipart で受信した jar を `lib/` に保存
3. **アクティベーション (I-03)**: `java -jar driver.jar -l` を起動し、stdin で名前/メール/`TRIAL` を流す
4. **クラス自動検出 (I-04)**: 上記 1 の副産物
5. **削除 (I-05)**: jar + 対応する `.lic` を削除

### 4.2 主要 API

```kotlin
data class JdbcDriverInfo(
    val filename: String,        // cdata.jdbc.googlesheets.jar
    val driverClass: String?,    // cdata.jdbc.googlesheets.GoogleSheetsDriver
    val licenseStatus: LicenseStatus,
    val sizeBytes: Long,
)

enum class LicenseStatus { ACTIVATED, NOT_ACTIVATED, UNKNOWN }

class JdbcDriverManager(private val libDir: Path) {
    fun listDrivers(): List<JdbcDriverInfo>
    fun upload(filename: String, content: InputStream): JdbcDriverInfo
    fun activateTrial(filename: String, name: String, email: String): Result<Unit>
    fun delete(filename: String)
    fun detectDriverClass(jar: Path): String?  // META-INF/services 読み出し
}
```

### 4.3 アクティベーション実装

```kotlin
private fun activate(jarPath: Path, name: String, email: String): Process {
    val pb = ProcessBuilder("java", "-jar", jarPath.fileName.toString(), "-l")
        .directory(jarPath.parent.toFile())
        .redirectErrorStream(true)
    val proc = pb.start()
    // stdin に名前・メール・TRIAL を流す
    proc.outputStream.bufferedWriter().use { w ->
        w.write("$name\n")
        w.write("$email\n")
        w.write("TRIAL\n")
        w.write("\n")  // "Press any key to exit"
        w.flush()
    }
    return proc
}
```

リスク: CData ドライバのプロンプト順が変わるとブレる → タイムアウト + ログから「License installation succeeded.」検出でリトライ判断。

### 4.4 セキュリティ

| リスク | 対策 |
|---|---|
| 任意 JAR の RCE | アップロード受付前に拡張子 `.jar` チェック + Magic Number (PK 0x03 0x04) 確認。ホスト上のみ動作なので限定的リスク |
| Zip Slip | `lib/` 配下に保存するだけで展開しないため非該当 |
| 大容量 DoS | multipart 最大サイズ 50MB |
| 不正な driver class 偽装 | `META-INF/services/java.sql.Driver` で検出した名前のみ採用、ユーザ手入力を排除 |

---

## 5. Adapter 起動・停止と Web UI の連携

### 5.1 MultiAdapterRunner の再利用

Phase 2-A で実装した `MultiAdapterRunner` を Web UI 内で 1 インスタンスだけ持ち、起動・停止ボタンから直接呼び出す。

```kotlin
class WebUiServer(
    private val configSource: ConfigSource,
    private val runner: MultiAdapterRunner,
    private val driverManager: JdbcDriverManager,
) {
    suspend fun handleStart(call: ApplicationCall, tableName: String) {
        try {
            runner.startOne(tableName)
            call.respondHtml(...) // 起動成功フラグメント
        } catch (e: IllegalStateException) {
            call.respondHtml(...) // 既に起動済みエラー
        }
    }
}
```

### 5.2 Server-Sent Events (active-adapters)

`runner.listActive()` を 1 秒間隔（または変更通知ベース）で配信し、ブラウザの該当行を JS で更新。

```kotlin
get("/runtime/sse") {
    call.respondSse {
        while (isActive) {
            val active = runner.listActive()
            send(SseEvent(event = "active-adapters", data = Json.encodeToString(active)))
            delay(1000)
        }
    }
}
```

ブラウザ側は以下のスクリプト 1 つで対応:

```html
<script>
  const es = new EventSource('/runtime/sse');
  es.addEventListener('active-adapters', (e) => {
    const data = JSON.parse(e.data);
    // 各 <tr data-table="..."> の status セルを更新
    data.forEach(s => {
      const row = document.querySelector(`[data-table="${s.tableName}"]`);
      if (row) row.querySelector('.status').textContent = s.status;
    });
  });
</script>
```

---

## 6. Salesforce 新階層への移行

### 6.1 現状

- `config/server.yaml`, `config/jdbc.yaml`, `config/table.yaml`, `config/capability.yaml` がプロジェクト直下に存在（Phase 1 互換層で動作中）
- `serve` 引数なしで `default` テーブルとして読み込まれる

### 6.2 移行後

```
config/
├── jdbc/
│   └── salesforce.yaml       # 既存 config/jdbc.yaml の内容
└── tables/
    └── account/
        ├── server.yaml       # 既存 config/server.yaml + port: 18001 に変更
        ├── jdbc-ref.yaml     # 新規 (name: salesforce)
        ├── table.yaml        # 既存 config/table.yaml
        └── capability.yaml   # 既存 config/capability.yaml
```

### 6.3 移行手順

1. **既存 `migrate-config` を活用**: 自動移行は Phase 1 → `tables/default/` まで。Phase 1 の jdbc.yaml を共有設定化する後処理を新規追加するか、ユーザ手動で移行する
2. **新コマンド `migrate-to-multi-table`**（推奨）: より明示的に `tables/account/` 配下に整列し、`jdbc-ref` を作る
3. または **Web UI 起動時に Phase 1 構成を検出して移行ガイド表示**

設計選択: **Web UI 上にバナー** で「Phase 1 構成を検出。1 クリックで移行可能」と表示し、ボタンで実行する案 (新規 API `POST /migrate/phase1-to-tables`)。CLI も補助として残す。

### 6.4 移行後のテスト

- 既存 kintone Connector / Agent の token は不変（同じ public-key・同じ Connector ID）
- Adapter 側で port=18001 にする必要があるため、Agent の `adapter_addr` を `host.docker.internal:18001` に変更
- 検証: kintone でレコード一覧が以前と同じく表示される

---

## 7. データクラス・インターフェース変更点

### 7.1 既存に対する変更

| 既存 | 変更内容 |
|---|---|
| `ConfigSource` | 変更なし（既存契約をそのまま使う） |
| `MultiAdapterRunner` | 変更なし |
| `YamlConfigSource` | 変更なし |
| `Application.kt` | `WebUiCommand` サブコマンドを追加 |

### 7.2 新規追加

| クラス | 役割 |
|---|---|
| `SqliteConfigSource` | ConfigSource の SQLite 実装 |
| `SqliteSchema` | DDL 文字列定数 |
| `ConfigSourceFactory` | Yaml / Sqlite モード切替 |
| `JdbcDriverManager` | lib/ 配下の管理 |
| `JdbcDriverInfo` | 一覧表示用 DTO |
| `DriverActivator` | トライアルアクティベーション ProcessBuilder |
| `WebUiCommand` | CLI サブコマンド |
| `WebUiServer` | Ktor アプリケーション |
| `MigrateToSqliteCommand` | YAML → SQLite |
| `ExportYamlCommand` | SQLite → YAML |
| `MigrateToMultiTableCommand` | Phase 1 → Phase 2-A 新階層 |

---

## 8. 影響範囲と互換性

| 影響領域 | 変更内容 | 後方互換 |
|---|---|---|
| CLI コマンド | `web-ui`, `migrate-to-sqlite`, `export-yaml`, `migrate-to-multi-table` 追加 | ✅ 既存コマンドは変更なし |
| 設定ファイル | YAML 構造は変更なし | ✅ |
| 永続化 | SQLite を**追加**（既存 YAML 利用者は影響なし） | ✅ |
| `lib/` 配下 | 引き続き手動配置可、Web UI からの upload も同居 | ✅ |
| Agent / kintone Connector | 変更なし | ✅ |
| ポート | Web UI 8080 (新規)、Adapter 群 18001+（既存）| ✅ |
| Salesforce 移行 | port 8083 → 18001 に変更（kintone Connector 側 token は不変） | ⚠ Agent の `adapter_addr` 更新が必要 |

---

## 9. テスト戦略

### 9.1 ユニットテスト

- **SqliteConfigSource**: YamlConfigSource と同じ契約テストを共有（パラメータ化テスト）
- **JdbcDriverManager**: `@TempDir` で lib/ 操作、ProcessBuilder はモック
- **ConfigSourceFactory**: env var / file 検出ロジック
- **Migrate コマンド**: 移行前後の整合性

### 9.2 統合テスト

- **Ktor TestApplication** で各ルートをテスト
  - ステータスコード、HTML フラグメント検証
  - HTMX `HX-Request` ヘッダの分岐
- SSE エンドポイントは別の純粋関数 (state → events) に切り出してテスト

### 9.3 手動 E2E

- ブラウザで実際にウィザード操作 → Adapter 起動 → kintone 経由レコード表示
- [e2e-checklist.md](../20260522-multi-table-support/e2e-checklist.md) と同形式の Phase 2-B 用チェックリスト作成

### 9.4 既存テスト維持

- Phase 2-A の **189 件全件緑** が大前提
- 全テスト実行時間が 30 秒以内に収まること（CI 配慮）

---

## 10. リスクと緩和

| リスク | 影響 | 緩和策 |
|---|---|---|
| Web UI の規模が膨らみすぎ | スケジュール超過 | 23 機能に厳格に絞る。E/F/G/H は Phase 2-C 以降 |
| Ktor 学習コスト | 着手遅れ | 公式チュートリアル + 既存 Kotlin 経験で 1 日 |
| アクティベーションのプロンプト変化 | I-03 が壊れる | CData ドライバ更新時に手動再確認、SKILL に手順記録 |
| SQLite WAL とプロセス間ロック | Yaml/Sqlite 混在時の不整合 | Phase 2-B では同時起動を制限（Yaml モードか Sqlite モードのどちらか） |
| JAR upload の脆弱性 | サイト改ざん | localhost listen のみ、サイズ上限、Magic Number 検証 |
| 移行後 Salesforce が動かない | 既存運用停止 | 移行前に `migrate-config` のドライランオプション追加、Phase 1 構成をバックアップ |

---

## 11. 段階的実装方針

Phase 2-B は規模が大きいため、ミルストーンを以下のように設計:

| M | 内容 | 完了基準 |
|---|---|---|
| M1 | SqliteConfigSource 基盤 | YamlConfigSource と同じ 15 件契約テスト緑 |
| M2 | ConfigSourceFactory + マイグレーションコマンド | yaml → sqlite → yaml の往復で同値性 |
| M3 | JdbcDriverManager | アップロード・一覧・削除・アクティベーション の単体テスト緑 |
| M4 | Web UI スケルトン + ダッシュボード | `adapter web-ui` で起動、ダッシュボードが表示 |
| M5 | Tables 一覧・詳細・編集 | A-01/02/04 動作 |
| M6 | Connections 一覧・新規・接続テスト | B-01/02/04 動作 |
| M7 | Drivers 画面 | I-01〜I-05 動作 |
| M8 | Table 新規ウィザード | A-03 + C-01/02/03 動作 |
| M9 | ランタイム制御 + SSE | D-01〜D-04 動作 |
| M10 | Salesforce 新階層移行 + E2E | SF + GS 両方が serve-all で動作 |
| M11 | ドキュメント / リリース | README / docs / known-issues 更新 + tag |

タスクリスト詳細は [tasklist.md](./tasklist.md) で展開。

---

## 12. 関連ドキュメント

- 要求定義: [requirements.md](./requirements.md)
- タスク詳細: tasklist.md (次に作成)
- Phase 2-A 設計: [../20260522-multi-table-support/design.md](../20260522-multi-table-support/design.md)
- 機能設計（永続）: [docs/functional-design.md](../../docs/functional-design.md)
- アーキテクチャ（永続）: [docs/architecture.md](../../docs/architecture.md)
