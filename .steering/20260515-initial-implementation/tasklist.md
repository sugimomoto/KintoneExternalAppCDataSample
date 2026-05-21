# 初回実装のタスクリスト（フェーズ1）

| 項目 | 内容 |
|---|---|
| 作業タイトル | initial-implementation |
| 作成日 | 2026-05-15 |
| ステータス | ドラフト（承認待ち） |
| 親文書 | [requirements.md](requirements.md), [design.md](design.md) |

---

## 凡例

- 🔴 **Red**: 失敗するテストを書く
- 🟢 **Green**: テストを通す最小実装
- 🔵 **Refactor**: 構造改善
- ⚙️ **Setup**: ビルド・設定系（TDD 対象外）
- ✅ 完了
- ⬜ 未着手
- 🟡 進行中
- 概算工数：S (〜0.5日) / M (〜1日) / L (〜2日)

## マイルストーン全体図

| マイルストーン | 主な成果物 | 工数目安 |
|---|---|---|
| **M1** | プロジェクト基盤 + 純粋ロジック層完成 | 〜3日 |
| **M2** | 設定読込 + JDBC 接続 + Select/GetSchema PoC（Salesforce 動作） | 〜5日 |
| **M3** | CRUD 全 RPC 完成 + 統合テスト | 〜3日 |
| **M4** | CLI + kintone Agent 経由 E2E 動作 | 〜3日 |
| **M5** | Docker + ドキュメント + フェーズ1 完了 | 〜2日 |

---

# M1: 基盤構築 + 純粋ロジック層

## M1-A: プロジェクト初期化（⚙️ Setup）

- ✅ **T-M1-A-01** ⚙️ [S] Git リポジトリ初期化 + `.gitignore` 作成
  - 含めるべき除外：`.gradle/`, `build/`, `lib/*.jar`, `lib/*.lic`, `config/jdbc.yaml`, `config/table.yaml`, `reference/`, `.idea/`, `.DS_Store`
- ✅ **T-M1-A-02** ⚙️ [M] Gradle 初期化（Kotlin DSL）
  - `gradle init --type kotlin-application --dsl kotlin`
  - `build.gradle.kts` に design.md §6.1 の構成を反映
  - `settings.gradle.kts` でプロジェクト名 `cdata-kintone-adapter` 設定
- ✅ **T-M1-A-03** ⚙️ [S] `.editorconfig` 配置（ktlint 設定）
- ✅ **T-M1-A-04** ⚙️ [S] `detekt.yml` 配置
- ✅ **T-M1-A-05** ⚙️ [S] `logback.xml` 配置（`src/main/resources/`）
- ✅ **T-M1-A-06** ⚙️ [S] `gradle.properties` で Kotlin / Java バージョン固定

## M1-B: protobuf コード生成（⚙️ Setup）

- ✅ **T-M1-B-01** ⚙️ [M] `buf.yaml` / `buf.gen.yaml` 配置
- ✅ **T-M1-B-02** ⚙️ [M] `build.buf` Gradle プラグイン設定
- ✅ **T-M1-B-03** ⚙️ [S] `./gradlew bufGenerate` 動作確認
  - 完了基準: `build/generated/source/buf/main/kotlin/cybozu/data_connector/adapter/v1/` に Kotlin コードが生成される
- ✅ **T-M1-B-04** ⚙️ [S] 生成コードを IDE インデックスに含める（`sourceSets`）

## M1-C: FieldTypeSuggester（TDD 入門）

- ✅ **T-M1-C-OK** 🔴 [S] `FieldTypeSuggesterTest.kt` 作成、`VARCHAR → TEXT` の失敗テスト
- ✅ **T-M1-C-OK** 🟢 [S] `FieldTypeSuggester.suggest()` 最小実装
- ✅ **T-M1-C-OK** 🔴🟢 [S] `BIGINT → NUMBER`, `INTEGER → NUMBER` 等の数値型を追加
- ✅ **T-M1-C-OK** 🔴🟢 [S] `TIMESTAMP → DATETIME`, `DATE → DATETIME` 等の日時型を追加
- ✅ **T-M1-C-OK** 🔴🟢 [S] `LONGVARCHAR`, `NVARCHAR` 等のテキストバリエーション追加
- ✅ **T-M1-C-OK** 🔴🟢 [S] 未知型のフォールバック（`TEXT`）テスト
- ✅ **T-M1-C-OK** 🔴🟢 [S] `suggestRecordIdType()` の TEXT/NUMBER 判定テスト
- ✅ **T-M1-C-OK** 🔵 [S] 重複ロジックを `when` ブロック1つに集約
- **完了基準**: `FieldTypeSuggesterTest` の全テストが緑、カバレッジ 100%

## M1-D: FilterTranslator（37ケース TDD）

### 基盤
- ✅ **T-M1-D-01** 🔴 [S] `FilterTranslatorTest`、空の condition リストで `WhereClause("1=1", emptyList())` 返却の失敗テスト
- ✅ **T-M1-D-02** 🟢 [S] `FilterTranslator.translate()` のスケルトン実装
- ✅ **T-M1-D-03** 🔴🟢 [S] `all_records` 単独で `1=1` を返す

### record_id 系（10ケース）
- ✅ **T-M1-D-04** 🔴🟢 [S] `record_id_equal` (NUMBER) → `field = ?`
- ✅ **T-M1-D-05** 🔴🟢 [S] `record_id_equal` (TEXT)
- ✅ **T-M1-D-06** 🔴🟢 [S] `record_id_not_equal` (NUMBER + TEXT)
- ✅ **T-M1-D-07** 🔴🟢 [S] `record_id_greater_than`
- ✅ **T-M1-D-08** 🔴🟢 [S] `record_id_greater_than_or_equal`
- ✅ **T-M1-D-09** 🔴🟢 [S] `record_id_less_than`
- ✅ **T-M1-D-10** 🔴🟢 [S] `record_id_less_than_or_equal`
- ✅ **T-M1-D-11** 🔴🟢 [S] `record_id_in`
- ✅ **T-M1-D-12** 🔴🟢 [S] `record_id_not_in`
- ✅ **T-M1-D-13** 🔴🟢 [S] `record_id_contains` (TEXT ID 専用)
- ✅ **T-M1-D-14** 🔴🟢 [S] `record_id_not_contains` (TEXT ID 専用)
- ✅ **T-M1-D-15** 🔵 [S] record_id 系の重複削減

### text 系（9ケース）
- ✅ **T-M1-D-16** 🔴🟢 [S] `text_equal`
- ✅ **T-M1-D-17** 🔴🟢 [S] `text_not_equal`
- ✅ **T-M1-D-18** 🔴🟢 [S] `text_in`
- ✅ **T-M1-D-19** 🔴🟢 [S] `text_not_in`
- ✅ **T-M1-D-20** 🔴🟢 [S] `text_contains` (`field LIKE '%value%'`)
- ✅ **T-M1-D-21** 🔴🟢 [S] `text_not_contains`
- ✅ **T-M1-D-22** 🔴🟢 [S] `text_is` (EMPTY → `field IS NULL OR field = ''`)
- ✅ **T-M1-D-23** 🔴🟢 [S] `text_is_not`
- ✅ **T-M1-D-24** 🔵 [S] text 系の重複削減

### datetime 系（8ケース）
- ✅ **T-M1-D-25** 🔴🟢 [S] `datetime_equal`
- ✅ **T-M1-D-26** 🔴🟢 [S] `datetime_not_equal`
- ✅ **T-M1-D-27** 🔴🟢 [S] `datetime_greater_than`
- ✅ **T-M1-D-28** 🔴🟢 [S] `datetime_greater_than_or_equal`
- ✅ **T-M1-D-29** 🔴🟢 [S] `datetime_less_than`
- ✅ **T-M1-D-30** 🔴🟢 [S] `datetime_less_than_or_equal`
- ✅ **T-M1-D-31** 🔴🟢 [S] `datetime_in_range` (`>= start AND < end`)
- ✅ **T-M1-D-32** 🔴🟢 [S] `datetime_not_in_range`
- ✅ **T-M1-D-33** 🔵 [S] datetime 系の重複削減（Timestamp変換ヘルパ抽出）

### number 系（8ケース）
- ✅ **T-M1-D-34** 🔴🟢 [S] `number_equal` (value=null → `IS NULL`)
- ✅ **T-M1-D-35** 🔴🟢 [S] `number_not_equal` (value=null → `IS NOT NULL`)
- ✅ **T-M1-D-36** 🔴🟢 [S] `number_greater_than`
- ✅ **T-M1-D-37** 🔴🟢 [S] `number_greater_than_or_equal`
- ✅ **T-M1-D-38** 🔴🟢 [S] `number_less_than`
- ✅ **T-M1-D-39** 🔴🟢 [S] `number_less_than_or_equal`
- ✅ **T-M1-D-40** 🔴🟢 [S] `number_in`
- ✅ **T-M1-D-41** 🔴🟢 [S] `number_not_in`

### selection 系（2ケース、NULL含むIN対応）
- ✅ **T-M1-D-42** 🔴🟢 [S] `selection_in` (NULL なし)
- ✅ **T-M1-D-43** 🔴🟢 [S] `selection_in` (NULL あり → `IN (...) OR IS NULL` 分割)
- ✅ **T-M1-D-44** 🔴🟢 [S] `selection_not_in` (NULL あり/なし両方)
- ✅ **T-M1-D-45** 🔵 [S] `convertNullableOptionsToInClause` ヘルパ抽出

### multiple_selection 系（除外）
- ✅ **T-M1-D-46** 🔴🟢 [S] `multiple_selection_in/not_in` で `ConnectException(UNIMPLEMENTED)` を投げる

### AND/OR 結合
- ✅ **T-M1-D-47** 🔴🟢 [S] `MATCH_OPERATOR_ALL` で AND 結合
- ✅ **T-M1-D-48** 🔴🟢 [S] `MATCH_OPERATOR_ANY` で OR 結合
- ✅ **T-M1-D-49** 🔴🟢 [S] 複数条件の合成テスト（AND + OR ネスト不要、トップレベルのみ）
- ✅ **T-M1-D-50** 🔵 [S] 全体のリファクタリング（`when` 分岐の整理・ヘルパ抽出）

- **完了基準**: 37 ケース全テストが緑、`multiple_selection_*` の Unimplemented 検証も含む、カバレッジ 100%

---

# M2: 設定読込 + JDBC 接続 + Select PoC

## M2-A: 設定データクラス

- ✅ **T-M2-A-01** 🔴🟢 [S] `ServerConfig` データクラス + YAML パーステスト
- ✅ **T-M2-A-02** 🔴🟢 [S] `JdbcConfig` + `PoolConfig`
- ✅ **T-M2-A-03** 🔴🟢 [S] `CapabilityConfig`（`CountStrategy` enum 含む）
- ✅ **T-M2-A-04** 🔴🟢 [S] `TableConfig` + `PrimaryKeyConfig` + `ColumnConfig`
- ✅ **T-M2-A-05** 🔵 [S] `@SerialName` の kebab-case マッピング統一確認

## M2-B: ConfigLoader

- ✅ **T-M2-B-01** 🔴 [S] 4ファイル全て揃った正常読込の失敗テスト
- ✅ **T-M2-B-02** 🟢 [S] `ConfigLoader.load()` 実装
- ✅ **T-M2-B-03** 🔴🟢 [S] `${VAR_NAME}` 環境変数展開テスト
- ✅ **T-M2-B-04** 🔴🟢 [S] ファイル欠落時のエラーメッセージ
- ✅ **T-M2-B-05** 🔴🟢 [S] 不正な YAML 時のエラー
- ✅ **T-M2-B-06** 🔵 [S] 環境変数展開ロジック抽出

## M2-C: JdbcConnectionProvider

- ✅ **T-M2-C-01** ⚙️ [M] `lib/cdata.jdbc.salesforce.jar` を Gradle 依存に追加
- ✅ **T-M2-C-02** 🔴 [M] JAR からドライバクラスを動的ロードする失敗テスト
- ✅ **T-M2-C-03** 🟢 [M] `URLClassLoader` 経由でロード実装
- ✅ **T-M2-C-04** 🔴🟢 [S] HikariCP の `dataSource.connection` 取得テスト（H2 / Testcontainers）
- ✅ **T-M2-C-05** 🔴🟢 [S] `close()` でプール解放
- ✅ **T-M2-C-06** 🔵 [S] 接続文字列ログ出力時の Password マスキング

## M2-D: QueryBuilder

- ✅ **T-M2-D-01** 🔴🟢 [S] `buildSelect` 全カラム取得（fields 空）
- ✅ **T-M2-D-02** 🔴🟢 [S] `buildSelect` 一部カラム指定
- ✅ **T-M2-D-03** 🔴🟢 [S] `buildSelect` WHERE 句あり
- ✅ **T-M2-D-04** 🔴🟢 [S] `buildSelect` ORDER BY 単一カラム
- ✅ **T-M2-D-05** 🔴🟢 [S] `buildSelect` ORDER BY 複数カラム
- ✅ **T-M2-D-06** 🔴🟢 [S] `buildSelect` LIMIT / OFFSET
- ✅ **T-M2-D-07** 🔴🟢 [S] `buildInsert` 1レコード
- ✅ **T-M2-D-08** 🔴🟢 [S] `buildUpdate`（ID で WHERE）
- ✅ **T-M2-D-09** 🔴🟢 [S] `buildDelete`（IN 句で複数 ID）
- ✅ **T-M2-D-10** 🔴🟢 [S] `buildCount`
- ✅ **T-M2-D-11** 🔵 [S] SQL 文字列組立ヘルパ抽出

## M2-E: RowMapper

- ✅ **T-M2-E-01** 🔴🟢 [S] `RecordIdField` (NUMBER) → `int64 value`
- ✅ **T-M2-E-02** 🔴🟢 [S] `RecordIdField` (TEXT) → `record_id_value.value_text`
- ✅ **T-M2-E-03** 🔴🟢 [S] `TextField` 非NULL
- ✅ **T-M2-E-04** 🔴🟢 [S] `TextField` NULL (skip)
- ✅ **T-M2-E-05** 🔴🟢 [S] `NumberField` 非NULL
- ✅ **T-M2-E-06** 🔴🟢 [S] `NumberField` NULL (skip)
- ✅ **T-M2-E-07** 🔴🟢 [S] `DatetimeField` (UTC 変換確認)
- ✅ **T-M2-E-08** 🔴🟢 [S] `SelectionField`
- ✅ **T-M2-E-09** 🔴🟢 [S] `recordToParams` (Insert 用)
- ✅ **T-M2-E-10** 🔴🟢 [S] `recordToParams` (Update 用、ID 除外)
- ✅ **T-M2-E-11** 🔵 [S] 型変換ヘルパ統一

## M2-F: JdbcMetadataInspector

- ✅ **T-M2-F-01** 🔴🟢 [S] `listTables` モックベーステスト
- ✅ **T-M2-F-02** 🔴🟢 [S] `listColumns` モックベーステスト
- ✅ **T-M2-F-03** 🔴🟢 [S] `findPrimaryKey` モックベーステスト
- ✅ **T-M2-F-04** 🔴🟢 [S] `distinctValues` モックベーステスト

## M2-G: AdapterServiceImpl - 基本 RPC

- ✅ **T-M2-G-01** 🔴🟢 [S] `getCapability` テスト（設定値を payload にそのまま反映）
- ✅ **T-M2-G-02** 🔴🟢 [M] `getSchema` テスト（FieldDefinition マップ生成）
- ✅ **T-M2-G-03** 🔴🟢 [M] `select` モックベース正常系（fields 空 / filter 単一）
- ✅ **T-M2-G-04** 🔴🟢 [S] `select` 異常系（payload null）

## M2-H: Ktor + Connect 統合（R-01 検証）

- ✅ **T-M2-H-01** ⚙️ [L] `Application.kt` で Clikt + Ktor + connect-kotlin 統合
- ✅ **T-M2-H-02** ⚙️ [M] `ServeCommand` 最小実装（設定読込 → Ktor 起動）
- ✅ **T-M2-H-03** ✅ [S] `./gradlew run --args="serve"` でローカル起動
- ✅ **T-M2-H-04** ✅ [S] `curl ... /GetCapability` で応答確認

## M2-I: Salesforce PoC

- ✅ **T-M2-I-01** ⚙️ [S] `config/server.yaml` 作成
- ✅ **T-M2-I-02** ⚙️ [S] `config/jdbc.yaml`（Salesforce 接続情報、環境変数経由）
- ✅ **T-M2-I-03** ⚙️ [S] `config/capability.yaml`（Salesforce Account 向け）
- ✅ **T-M2-I-04** ⚙️ [M] `config/table.yaml` 手動作成（Salesforce Account の主要カラム）
- ✅ **T-M2-I-05** ✅ [M] curl で Salesforce 接続 → `GetSchema` 動作確認
- ✅ **T-M2-I-06** ✅ [M] curl で `Select` → Salesforce レコード取得確認
- **M2 完了基準**: Salesforce Account の Select が curl で動作する

---

# M3: CRUD 完成 + 統合テスト

## M3-A: Insert / Update / Delete / Count

- ✅ **T-M3-A-01** 🔴🟢 [M] `insert` テスト（モック、トランザクション境界）
- ✅ **T-M3-A-02** 🔴🟢 [M] `insert` TEXT ID 対応（`record_ids` 返却）
- ✅ **T-M3-A-03** 🔴🟢 [M] `insert` R-02 対応：`RETURN_GENERATED_KEYS` 失敗時の代替（再 SELECT）
- ✅ **T-M3-A-04** 🔴🟢 [M] `update` テスト（ID 必須、部分更新）
- ✅ **T-M3-A-05** 🔴🟢 [M] `delete` テスト（NUMBER / TEXT 両対応）
- ✅ **T-M3-A-06** 🔴🟢 [S] `count` ACTUAL テスト
- ✅ **T-M3-A-07** 🔴🟢 [S] `count` ALWAYS_ZERO テスト
- ✅ **T-M3-A-08** 🔵 [S] トランザクション境界の共通化

## M3-B: Search / Aggregate（基本）

- ✅ **T-M3-B-01** 🔴🟢 [M] `search` LIKE ベースの簡易実装
- ✅ **T-M3-B-02** 🔴🟢 [M] `aggregate` COUNT / SUM / AVG / MAX / MIN 各テスト
- ✅ **T-M3-B-03** 🔴🟢 [M] `aggregate` GroupingMethod DATETIME_YEAR / MONTH / DAY 等
- ✅ **T-M3-B-04** 🔴🟢 [S] `aggregate` ソート対応

## M3-C: 統合テスト（Testcontainers PostgreSQL）

- ✅ **T-M3-C-01** ⚙️ [M] Testcontainers セットアップ
- ✅ **T-M3-C-02** 🔴🟢 [L] `AdapterServiceImplIntegrationTest` — Select/Insert/Update/Delete/Count を E2E（PostgreSQL）
- ✅ **T-M3-C-03** 🔴🟢 [M] フィルター総合テスト（複数条件 + AND/OR + NULL）
- **M3 完了基準**: 9 RPC 全てが curl で動作、PostgreSQL 統合テストが緑

---

# M4: CLI + kintone Agent 連携 E2E

## M4-A: 補助サブコマンド

- ✅ **T-M4-A-01** 🔴🟢 [S] `TestConnectionCommand` テスト + 実装
- ✅ **T-M4-A-02** 🔴🟢 [S] `ListTablesCommand` テスト + 実装
- ✅ **T-M4-A-03** ✅ [S] 各サブコマンドの手動動作確認

## M4-B: InitTableCommand（対話式）

- ✅ **T-M4-B-01** 🔴🟢 [M] テーブル選択 UI（番号入力）テスト
- ✅ **T-M4-B-02** 🔴🟢 [M] カラム型確認 UI テスト（推奨型 Enter で承認 / 番号で上書き）
- ✅ **T-M4-B-03** 🔴🟢 [M] SELECTION 型の選択肢入力（手動 / 自動検出）
- ✅ **T-M4-B-04** 🔴🟢 [M] record-id-type 推定 + 確認
- ✅ **T-M4-B-05** 🔴🟢 [M] field_id 入力（snake_case デフォルト）
- ✅ **T-M4-B-06** 🔴🟢 [M] `TableConfigWriter` で YAML 書き出し
- ✅ **T-M4-B-07** 🔴🟢 [S] `--non-interactive` モード
- ✅ **T-M4-B-08** ✅ [M] Salesforce Account に対して実行 → `table.yaml` 自動生成
- ✅ **T-M4-B-09** 🔵 [S] 対話プロンプトの共通ヘルパ抽出

## M4-C: kintone Agent 経由 E2E

- ✅ **T-M4-C-01** ⚙️ [S] `scripts/test-adapter.sh` 作成（9 RPC curl サンプル）
- ✅ **T-M4-C-02** ⚙️ [M] Salesforce Account 用の `config/*.yaml` 完成版を準備
- ✅ **T-M4-C-03** ⚙️ [M] `openssl` で秘密鍵・公開鍵ペア生成
- ✅ **T-M4-C-04** ⚙️ [M] kintone 管理画面でコネクター追加 + 公開鍵登録 + トークン取得
- ✅ **T-M4-C-05** ⚙️ [M] `agent.json` を `reference/kintone-data-connector-agent_v0.9.2_*` 配下に配置
- ✅ **T-M4-C-06** ✅ [M] Agent 起動 + Adapter 起動の連携確認
- ✅ **T-M4-C-07** ✅ [L] kintone アプリストアから「外部システムに接続して作成」→ アプリ作成
- ✅ **T-M4-C-08** ✅ [L] 検証シナリオ S-01 〜 S-08 実施（要件 §7）
- ✅ **T-M4-C-09** ✅ [S] S-09（Count ALWAYS_ZERO 設定時の挙動確認）
- ✅ **T-M4-C-10** ✅ [M] S-10（別 CData JDBC Driver への切替テスト）

- **M4 完了基準**: 全 E2E シナリオ S-01 〜 S-10 がパス

---

# M5: Docker + ドキュメント + 完了

## M5-A: Docker 化

- ✅ **T-M5-A-01** ⚙️ [M] `Dockerfile`（multi-stage build）
- ✅ **T-M5-A-02** ⚙️ [S] `.dockerignore`
- ✅ **T-M5-A-03** ⚙️ [M] `docker-compose.yml` 作成（volume mount: `config/`, `lib/`）
- ✅ **T-M5-A-04** ✅ [M] `docker compose up` で起動確認
- ✅ **T-M5-A-05** ⚙️ [M] `linux/amd64` + `linux/arm64` のマルチプラットフォームビルド確認

## M5-B: ドキュメント整備

- ✅ **T-M5-B-01** ⚙️ [L] `README.md` 作成
  - プロジェクト概要
  - クイックスタート（30 分以内の Salesforce 接続手順）
  - 設定ファイル全プロパティ説明（4ファイル）
  - CLI サブコマンド一覧
  - curl 動作確認手順
  - 他データソース切替方法
  - トラブルシュート
- ✅ **T-M5-B-02** ⚙️ [S] `LICENSE` 配置（Apache License 2.0）
- ✅ **T-M5-B-03** ⚙️ [S] `config/server.yaml.example`
- ✅ **T-M5-B-04** ⚙️ [S] `config/jdbc.yaml.example`（Salesforce 用）
- ✅ **T-M5-B-05** ⚙️ [S] `config/table.yaml.example`（Salesforce Account 用）
- ✅ **T-M5-B-06** ⚙️ [S] `config/capability.yaml.example`

## M5-C: 最終品質チェック

- ✅ **T-M5-C-01** ✅ [S] `./gradlew clean test ktlintCheck detekt jacocoTestReport` 全てパス
- ✅ **T-M5-C-02** ✅ [S] テストカバレッジ 80% 以上を確認
- ✅ **T-M5-C-03** ✅ [S] `FilterTranslator` のテスト 37 ケース全て緑を確認
- ✅ **T-M5-C-04** ✅ [S] git log で「テスト先行」コミット履歴を確認
- ✅ **T-M5-C-05** ✅ [M] README に従って 30 分以内に Salesforce 接続できるか実測
- ✅ **T-M5-C-06** ✅ [S] 全ての受け入れ条件（requirements.md §5）を確認

- **M5 完了基準**: requirements.md §5 の全受け入れ条件 ✅

---

## タスク総数と工数集計

| マイルストーン | タスク数 | 概算工数 |
|---|---|---|
| M1 | 68 | 〜3日 |
| M2 | 57 | 〜5日 |
| M3 | 15 | 〜3日 |
| M4 | 22 | 〜3日 |
| M5 | 17 | 〜2日 |
| **合計** | **約180** | **〜16日（約3週間）** |

ほとんどが S サイズ（〜0.5日）のため、工数集計値（〜16日）は妥当な目安。

---

## 進捗ルール

1. **タスクIDで進捗トラッキング**（GitHub Issue 化する場合は T-M1-D-04 などをそのまま Issue タイトルに）
2. **Red → Green → Refactor は同一の PR / コミットチェーン**で進める
3. **タスク完了時は ⬜ → ✅ に変更**（このファイルを直接編集してコミット）
4. **マイルストーン完了時は対応する見出しに「✅ 完了」を追記**

---

## 関連ドキュメント

- 要件：[requirements.md](requirements.md)
- 設計：[design.md](design.md)
- 対応方針：[approach-draft.md](approach-draft.md)
- 永続的ドキュメント：[../../docs/](../../docs/)
- kintone Adapter 仕様：[../../.claude/skills/kintone-external-app-spec/](../../.claude/skills/kintone-external-app-spec/)
