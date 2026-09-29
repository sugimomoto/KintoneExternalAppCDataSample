# ユビキタス言語定義（Glossary）

| 項目 | 内容 |
|---|---|
| プロダクト名 | kintone External App CData Adapter Sample |
| バージョン | 2.0（フェーズ2-A：マルチテーブル） |
| 作成日 | 2026-05-15（フェーズ1）/ 2026-05-22 更新（フェーズ2-A） |
| ステータス | ドラフト |

---

## 1. ドメイン用語（kintone「外部システムのアプリ化」関連）

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| 外部システムのアプリ化 | External App | - | kintone の機能名。外部 DB のデータをリアルタイムに kintone アプリから操作する機能 |
| コネクター | Connector | - | kintone 側のインターフェース。サイボウズ提供 |
| エージェント | Agent | - | kintone とデータベースを中継するプログラム。サイボウズ提供のバイナリ |
| アダプター | Adapter | - | Agent からのリクエストを受け、データソースを操作するプログラム。**本プロジェクトが実装するもの** |
| 外部レコード | External Record | `Record` | Adapter が提供する1レコードのデータ |
| 外部連携アプリ | External App | - | kintone 側で、外部データソースに接続して作成されたアプリ |
| ワイドコース | Wide Course | - | kintone の料金プラン。本機能はワイドコース限定 |

## 1-A. フェーズ2-A 追加用語

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| テーブル設定セット | Table Config Set | `TableConfigSet` | 1 テーブル分の server/jdbc/table/capability 4 設定をまとめた単位。`AdapterConfig` の typealias |
| マルチアダプター | Multi Adapter | `MultiAdapterRunner` | 1 JVM 内で複数 `TableAdapterServer` を統合管理するランナー |
| テーブルアダプターサーバ | Table Adapter Server | `TableAdapterServer` | 1 テーブル分の gRPC サーバ。`MultiAdapterRunner` から複数インスタンス起動される |
| 共有 JDBC 設定 | Shared JDBC Config | `shared_jdbcs` テーブル | 複数の連携で共有する JDBC 接続情報。連携から名前で参照する |
| JDBC リファレンス | JDBC Reference | `JdbcRef` | 連携から共有 JDBC 設定を `name` で参照する指定 |
| 設定ソース | Config Source | `ConfigSource` | 設定の永続化レイヤ抽象。実装は `SqliteConfigSource` のみ。`ConfigStore.open()` で取得する |
| 稼働 Adapter 一覧 | Active Adapters | `ActiveAdaptersFile` | `./run/active-adapters.json` に書き出される稼働中 Adapter のスナップショット |
| OAuth キャッシュ分離 | Per-table OAuth Cache | `JdbcUrlEnhancer.withOAuthCachePerTable` | テーブルごとに `OAuthSettingsLocation` を `./run/oauth/<table>.txt` に分離 |

## 2. 通信プロトコル用語

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| Connect プロトコル | Connect Protocol | - | Buf 社の RPC プロトコル。gRPC + REST の両対応 |
| gRPC | gRPC | - | Google 発の RPC フレームワーク |
| HTTP/2 | HTTP/2 | - | Connect / gRPC が前提とする HTTP バージョン |
| protobuf | Protocol Buffers | - | Google 発の IDL（インターフェース定義言語） |
| BSR | Buf Schema Registry | - | protobuf スキーマ管理サービス |
| Unary RPC | Unary RPC | - | 単純なリクエスト/レスポンス型 RPC（双方向ストリーミングではない） |
| RPC メソッド | RPC method | - | サービスが提供する操作（例: `GetCapability`） |

## 3. AdapterService の RPC

| 日本語 | 英語 | コード上の名前 | 必須/任意 |
|---|---|---|---|
| ケイパビリティ取得 | Get Capability | `GetCapability` | 必須 |
| スキーマ取得 | Get Schema | `GetSchema` | 必須 |
| 選択取得 | Select | `Select` | 任意 |
| 挿入 | Insert | `Insert` | 任意 |
| 更新 | Update | `Update` | 任意 |
| 削除 | Delete | `Delete` | 任意 |
| 件数取得 | Count | `Count` | 任意 |
| 検索 | Search | `Search` | 任意 |
| 集計 | Aggregate | `Aggregate` | 任意 |

## 4. フィールド型（protobuf 由来）

| 日本語 | 英語 | コード上の名前 | 設定上の `type` | 説明 |
|---|---|---|---|---|
| レコードIDフィールド | Record ID Field | `RecordIdField` | （主キー専用） | レコードの一意識別子 |
| テキストフィールド | Text Field | `TextField` | `TEXT` | 文字列 |
| 日時フィールド | Datetime Field | `DatetimeField` | `DATETIME` | UTC 日時 |
| 数値フィールド | Number Field | `NumberField` | `NUMBER` | 倍精度浮動小数点 |
| 選択フィールド | Selection Field | `SelectionField` | `SELECTION` | 単一選択（ドロップダウン） |
| 複数選択フィールド | Multiple Selection Field | `MultipleSelectionField` | （未対応） | 複数選択。kintone UI未対応のためフェーズ1除外 |

## 5. レコードID型

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| 数値型レコードID | Number record ID | `RECORD_ID_TYPE_NUMBER` | int64 形式。DB 自動採番ID向け |
| 文字列型レコードID | Text record ID | `RECORD_ID_TYPE_TEXT` | 英数字+`_-`、最大100文字。Salesforce ID 等向け |

## 6. フィルター条件カテゴリ

| カテゴリ | 個数 | 例 |
|---|---|---|
| 全件取得 | 1 | `all_records` |
| レコードID系 | 10 | `record_id_equal`, `record_id_in`, `record_id_contains` 等 |
| テキスト系 | 9 | `text_equal`, `text_contains`, `text_is`（EMPTY判定） 等 |
| 日時系 | 8 | `datetime_equal`, `datetime_in_range` 等 |
| 数値系 | 8 | `number_equal`, `number_in` 等 |
| 選択系（単一） | 2 | `selection_in`, `selection_not_in` |
| 選択系（複数） | 2 | `multiple_selection_in`, `multiple_selection_not_in`（フェーズ1除外） |

合計 39 種類。

## 7. 設定ファイル関連用語

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| サーバ設定 | Server Config | `ServerConfig` | `server.yaml` の内容 |
| JDBC 設定 | JDBC Config | `JdbcConfig` | `jdbc.yaml` の内容 |
| テーブル設定 | Table Config | `TableConfig` | `table.yaml` の内容 |
| ケイパビリティ設定 | Capability Config | `CapabilityConfig` | `capability.yaml` の内容 |
| カラム設定 | Column Config | `ColumnConfig` | 1カラムの kintone-JDBC マッピング |
| 主キー設定 | Primary Key Config | `PrimaryKeyConfig` | 主キーカラムのマッピング |
| プール設定 | Pool Config | `PoolConfig` | HikariCP 接続プール設定 |
| カウント戦略 | Count Strategy | `CountStrategy` | `ACTUAL` または `ALWAYS_ZERO` |
| filterable_fields | Filterable Fields | - | 絞り込み対象に指定できる field_id 一覧 |
| sortable_fields | Sortable Fields | - | ソート対象に指定できる field_id 一覧 |

## 8. CLI 関連用語

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| サブコマンド | Subcommand | - | CLI のサブコマンド（`web-ui`, `serve`, `list-tables` 等） |
| 新規連携ウィザード | New Sync Wizard | `TableWizardRoutes` | Web UI `/syncs/new` の 4 ステップで連携を作成するフロー |
| メタデータ検査 | Metadata Inspection | `JdbcMetadataInspector` | `DatabaseMetaData` を使ったテーブル・カラム情報取得 |
| フィールド型推奨 | Field Type Suggestion | `FieldTypeSuggester` | JDBC 型から kintone 型を推奨するロジック |

## 9. JDBC 関連用語

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| JDBC ドライバー | JDBC Driver | - | データソース接続用 Java ドライバー |
| CData JDBC | CData JDBC | - | CData 社の JDBC ドライバー製品群 |
| 接続プール | Connection Pool | `HikariDataSource` | HikariCP による接続プール |
| プレペアドステートメント | Prepared Statement | `PreparedStatement` | SQL インジェクション対策の SQL 実行方式 |
| ResultSet | Result Set | `ResultSet` | クエリ結果セット |
| DatabaseMetaData | Database Metadata | `DatabaseMetaData` | JDBC のメタデータ取得 API |
| データソース | Data Source | - | Salesforce / SAP / Snowflake 等の接続先 |

## 9-A. Web UI 画面・操作用語

Web UI に表示する呼称。実装は `web/views/Layout.kt` のナビと `web/views/HelpView.kt` が正。
表記ルールは `docs/development-guidelines.md` の「3.5 Web UI 文言規約」を参照。

| 日本語 | 英語 | コード上の名前 / パス | 説明 |
|---|---|---|---|
| ダッシュボード | Dashboard | `/` | 稼働状況の KPI と稼働中の連携を表示する画面 |
| 連携 | Sync | `/syncs` | 外部データソースの 1 テーブルを kintone に見せる設定の単位。画面名も「連携」 |
| データソース（画面名） | Data Source | `/connections` | データソース接続を管理する画面。**セクション 9 の「データソース」（接続先そのもの）とは別で、こちらは画面の呼称** |
| データソース接続 | Data Source Connection | `shared_jdbcs` / `SharedJdbcConfig` | 共有 JDBC 設定 1 件。複数の連携から名前で参照される |
| ドライバー（画面名） | Driver | `/drivers` | JDBC ドライバー JAR を管理する画面 |
| 接続文字列 | Connection String | `JdbcConfig.url` | JDBC URL。一覧では `ConnectionStringMasker` でマスクして表示する |
| 接続プール設定 | Pool Settings | `JdbcConfig.pool` | 最大接続数・接続タイムアウト |
| ライセンス状態 | License Status | `LicenseStatus` | ドライバーのライセンス状態。表示は「有効 / 未有効化 / 不明」 |
| 有効 | Activated | `LicenseStatus.ACTIVATED` | `.lic` ファイルが存在する |
| 未有効化 | Not activated | `LicenseStatus.NOT_ACTIVATED` | `.lic` ファイルが無い。トライアル有効化が必要 |
| 不明 | Unknown | `LicenseStatus.UNKNOWN` | ライセンス状態を判定できなかった |
| 操作 | Action | - | 一覧テーブルのボタン列の列名 |

## 10. ユーザー・関係者

| 日本語 | 英語 | 説明 |
|---|---|---|
| パートナー SI | Partner SI / Integrator | kintone と外部システムを連携させるシステムインテグレーター |
| エンドユーザー | End User | kintone を使う業務担当者 |
| CData 営業 | CData Sales | パートナーや顧客に CData 製品を提案する担当 |
| CData エバンジェリスト | CData Evangelist | 技術アピール・PoC 支援を行う担当 |
| Adapter 開発者 | Adapter Developer | 本サンプルをカスタマイズ実装する開発者 |

## 11. 略語

| 略語 | 正式名称 | 説明 |
|---|---|---|
| RPC | Remote Procedure Call | リモート関数呼び出し |
| API | Application Programming Interface | アプリケーション間のインターフェース |
| SDK | Software Development Kit | 開発用ライブラリ |
| SI | System Integrator | システムインテグレーター |
| SaaS | Software as a Service | クラウド型ソフトウェア |
| OEM | Original Equipment Manufacturer | 製品供給契約形態 |
| BSR | Buf Schema Registry | protobuf スキーマ管理 |
| OSS | Open Source Software | オープンソースソフトウェア |
| JDBC | Java Database Connectivity | Java の DB 接続 API |
| SQL | Structured Query Language | リレーショナルDB の問合せ言語 |
| JWT | JSON Web Token | トークン形式の認証 |
| TLS | Transport Layer Security | 通信暗号化 |
| PoC | Proof of Concept | 技術検証 |
| CI | Continuous Integration | 継続的インテグレーション |

## 12. 似た用語の使い分け

### 「kintone のフィールド」 vs 「JDBC のカラム」

- **フィールド (Field)**: kintone 側の概念。protobuf では `field_id` で識別
- **カラム (Column)**: データソース（DB）側の概念。JDBC では列名で識別

設定ファイルでは：
- `kintone-field-id`: kintone 側の名前
- `jdbc-column`: データソース側の名前

両者を区別することで、リネーム時の影響範囲を限定できる。

### 「Adapter」 vs 「Agent」

- **Adapter**: 本プロジェクトが実装するもの。データソース連携を担う
- **Agent**: サイボウズ提供のバイナリ。Adapter と kintone Connector の中継

混同しないよう、コード内では `Adapter` のみを扱い、Agent は外部システムとして扱う。

### 「Connect RPC」 vs 「gRPC」

- **gRPC**: Google 発の RPC プロトコル。HTTP/2 + protobuf
- **Connect RPC**: Buf 社のプロトコル。gRPC 互換 + REST 互換

本プロジェクトでは **Connect RPC** をサーバ実装に採用するが、protobuf 定義は gRPC と共通。

### 「設定ファイル」 vs 「コンフィグ」

- 日本語：設定ファイル
- 英語：Config / Configuration
- どちらも同じ意味。ドキュメント内では「設定ファイル」を優先

---

## 13. 用語の追加・変更ルール

- 新しい概念を導入する際は、本ファイルに追記してから他ドキュメントで使う
- 翻訳に揺れがあった場合はここで標準化
- 廃止された用語も「廃止 (deprecated)」として残し、移行を促す

---

## 14. 関連ドキュメント

- プロダクト要求：[product-requirements.md](product-requirements.md)
- 機能設計：[functional-design.md](functional-design.md)
- 技術仕様：[architecture.md](architecture.md)
- リポジトリ構造：[repository-structure.md](repository-structure.md)
- 開発ガイドライン：[development-guidelines.md](development-guidelines.md)
