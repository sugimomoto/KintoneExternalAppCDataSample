# Phase 2-B: タスクリスト

| 項目 | 内容 |
|---|---|
| フェーズ | 2-B |
| 作成日 | 2026-05-25 |
| 関連 | [requirements.md](./requirements.md), [design.md](./design.md) |
| 概算工数 | 約 14 日 |

## 凡例

- ⬜ 未着手 / ✅ 完了
- 🔴 RED (失敗テストを書く) / 🟢 GREEN (実装で緑にする) / 🔵 REFACTOR
- ⚙️ 設定・スキャフォールド系 / 📝 ドキュメント / ✅ 動作確認
- [S] 小 (〜1h) / [M] 中 (〜4h) / [L] 大 (〜1日)

---

## マイルストーン全体図

| M | 内容 | 主要成果物 | 概算 |
|---|---|---|---|
| M1 | SqliteConfigSource 基盤 | SqliteConfigSource + ConfigSource 契約テスト共有 | 1.5 日 |
| M2 | ConfigSourceFactory + Migrate コマンド | yaml/sqlite 切替 + migrate-to-sqlite / export-yaml | 1 日 |
| M3 | JdbcDriverManager | lib/ 操作 + アクティベーション | 1.5 日 |
| M4 | Web UI スケルトン + ダッシュボード | adapter web-ui + S-01 | 1 日 |
| M5 | Tables CRUD | S-02 / S-03 / S-04 | 1.5 日 |
| M6 | Connections CRUD + sys_connection_props フォーム | S-09 / S-10 | 2 日 |
| M7 | Drivers 画面 | S-11 / S-12 | 1 日 |
| M8 | Table 新規ウィザード | S-05 〜 S-08 | 2 日 |
| M9 | ランタイム制御 + SSE | D-02〜D-04 / SSE | 1 日 |
| M10 | Salesforce 新階層移行 + E2E | migrate-to-multi-table + 実機確認 | 1 日 |
| M11 | ドキュメント / リリース | README / docs / known-issues / tag | 1.5 日 |

---

# M1: SqliteConfigSource 基盤

## M1-A: 依存追加とスキーマ定義

- ✅ **T-M1-A-01** ⚙️ [S] `build.gradle.kts` に `org.xerial:sqlite-jdbc:3.46.x` 追加
- ✅ **T-M1-A-02** 🟢 [S] `SqliteSchema.kt` に DDL 文字列定数（shared_jdbcs, tables, schema_meta）
- ✅ **T-M1-A-03** 🔴🟢 [S] `SqliteSchema.initialize(conn)` のテスト：初回起動でテーブル作成 + version 行 INSERT
- ✅ **T-M1-A-04** 🔴🟢 [S] 既に初期化済みの場合は no-op（CREATE IF NOT EXISTS + INSERT OR IGNORE で冪等）
- ✅ **T-M1-A-05** ⚙️ [S] 接続時に `PRAGMA journal_mode=WAL`, `PRAGMA foreign_keys=ON`, `synchronous=NORMAL` を設定

## M1-B: SqliteConfigSource 実装

- ✅ **T-M1-B-01** 🔴 [S] `listTables()` 空 DB で empty list を返すテスト
- ✅ **T-M1-B-02** 🔴🟢 [S] `saveTableSet()` で 1 テーブル保存 → `listTables()` に出る
- ✅ **T-M1-B-03** 🔴🟢 [S] `loadTableSet(name)` で保存内容が同等に取得できる（往復同値性）
- ✅ **T-M1-B-04** 🔴🟢 [S] `deleteTable(name)` で削除
- ✅ **T-M1-B-05** 🔴🟢 [S] 存在しないテーブル名で `loadTableSet` → `ConfigFileMissingException`
- ✅ **T-M1-B-06** 🔴🟢 [S] `loadSharedJdbcConfig(name)` で共通 JDBC を取得
- ✅ **T-M1-B-07** 🔴🟢 [S] 共通 JDBC を保存する API（`ConfigSource.saveSharedJdbcConfig` に追加。Yaml 側も実装）
- ✅ **T-M1-B-08** 🔴🟢 [S] jdbc-ref 経由のテーブル: `loadTableSet` が共通 JDBC を解決して返す
- ✅ **T-M1-B-09** 🔴🟢 [S] inline jdbc のテーブル: `loadTableSet` が inline jdbc を返す
- ✅ **T-M1-B-10** 🔴🟢 [S] 環境変数プレースホルダ展開も Yaml と同じく動く

## M1-C: 共有契約テスト

- ⬜ **T-M1-C-01** 🔵 [M] `ConfigSourceContractTest` (抽象クラス) — 次フェーズで検討（現状 Sqlite/Yaml 個別テストで 30 件カバー）
- ✅ **T-M1-C-02** ✅ [S] 全 204 件テスト緑 (Phase 2-A 189 + Sqlite 15)

- **M1 完了基準**: Sqlite で Yaml と同等の契約を満たす ✅

---

# M2: ConfigSourceFactory + マイグレーションコマンド

## M2-A: ConfigSourceFactory

- ✅ **T-M2-A-01** 🔴 [S] `detectMode()` が `CONFIG_SOURCE` env var を読むテスト
- ✅ **T-M2-A-02** 🔴🟢 [S] `config.db` が存在すれば SQLITE モードを返す
- ✅ **T-M2-A-03** 🔴🟢 [S] どちらもなければ YAML がデフォルト
- ✅ **T-M2-A-04** 🟢 [S] `create()` でモードに応じた ConfigSource を返す

## M2-B: migrate-to-sqlite コマンド

- ✅ **T-M2-B-01** 🔴🟢 [M] `ConfigMigrator.copyAll(from, to)` テスト: Yaml → SQLite 移行
- ✅ **T-M2-B-02** 🔴🟢 [S] 既存 SQLite ファイルがある場合は `--force` 必須
- ✅ **T-M2-B-03** ⚙️ [S] CLI 登録 (`Application.kt`)
- ⬜ **T-M2-B-04** ✅ [S] M10 で実 config/ から `migrate-to-sqlite` 実行確認

## M2-C: export-yaml コマンド

- ✅ **T-M2-C-01** 🔴🟢 [M] `ExportYamlCommand` テスト: SQLite から Yaml 構造を出力
- ✅ **T-M2-C-02** 🔴🟢 [S] 往復テスト: Yaml → SQLite → Yaml で同値
- ✅ **T-M2-C-03** ⚙️ [S] CLI 登録

- **M2 完了基準**: yaml ⇔ sqlite 往復で同値性が保たれる。CLI で切替可 ✅

---

# M3: JdbcDriverManager

## M3-A: lib/ スキャン

- ✅ **T-M3-A-01** 🔴🟢 [S] `listDrivers()` で `lib/*.jar` を列挙
- ✅ **T-M3-A-02** 🔴🟢 [S] 各 JAR の `META-INF/services/java.sql.Driver` を読んで `driverClass` 抽出
- ✅ **T-M3-A-03** 🔴🟢 [S] 対応する `.lic` の有無で `licenseStatus` 判定
- ✅ **T-M3-A-04** 🔴🟢 [S] サイズ (`sizeBytes`) を含める

## M3-B: アップロード

- ✅ **T-M3-B-01** 🔴🟢 [S] `upload(filename, InputStream)` で lib/ に保存
- ✅ **T-M3-B-02** 🔴🟢 [S] 拡張子 `.jar` チェック (それ以外は拒否)
- ✅ **T-M3-B-03** 🔴🟢 [S] Magic Number (PK 0x03 0x04) 検証
- ⬜ **T-M3-B-04** 🔴🟢 [S] 上書きフラグ（後付け、Web UI 側で同名チェックする方針に変更）

## M3-C: アクティベーション

- ✅ **T-M3-C-01** 🟢 [M] `DriverActivator.activateTrial(jar, name, email)` を ProcessBuilder で実装
- ✅ **T-M3-C-02** 🟢 [S] stdin に "name\\nemail\\nTRIAL\\n\\n" を送信
- ✅ **T-M3-C-03** 🟢 [S] stdout から "License installation succeeded." を検出
- ✅ **T-M3-C-04** 🟢 [S] エラー検出 (Invalid product key 等) → Result.Failure
- ✅ **T-M3-C-05** 🟢 [S] タイムアウト 60 秒で `destroyForcibly()`

> Note: アクティベーションは外部 subprocess + CData ライセンスサーバ通信を伴うため、
> 単体テストは省略。M10 / M7 で実機で動作確認する。

## M3-D: 削除

- ✅ **T-M3-D-01** 🔴🟢 [S] `delete(filename)` で .jar + 対応する .lic を削除
- ✅ **T-M3-D-02** 🔴🟢 [S] 存在しない場合は no-op

- **M3 完了基準**: lib/ 配下の Driver CRUD + アクティベーション API 完成 ✅

---

# M4: Web UI スケルトン + ダッシュボード

## M4-A: 依存追加と CLI

- ✅ **T-M4-A-01** ⚙️ [S] `build.gradle.kts` に Ktor 3.0.1 依存追加
- ✅ **T-M4-A-02** ⚙️ [S] `WebUiCommand` を作成 (`adapter web-ui --port 8080`)
- ✅ **T-M4-A-03** ⚙️ [S] `Application.kt` に登録

## M4-B: Ktor アプリケーション

- ⬜ **T-M4-B-01** 🔴🟢 [S] TestApplication ベースの単体テスト（M5-M9 を含めた段階で追加）
- ✅ **T-M4-B-02** ✅ [S] `GET /` で 200 + ダッシュボード HTML
- ✅ **T-M4-B-03** 🟢 [S] `bindAddress = 127.0.0.1` デフォルト
- ✅ **T-M4-B-04** ⚙️ [S] CallLogging 有効化
- ✅ **T-M4-B-05** ⚙️ [S] 静的アセット配信 `/static/{filename}` (resources/static)

## M4-C: 共通レイアウト + ダッシュボード

- ✅ **T-M4-C-01** 🟢 [S] `Layout.kt`: ヘッダー + ナビ (Dashboard/Tables/Connections/Drivers) + フッター
- ✅ **T-M4-C-02** 🟢 [S] `DashboardView.kt`: 稼働中 Adapter テーブル + Quick Actions
- ✅ **T-M4-C-03** 🟢 [S] フッターに ConfigSource モード表示 (yaml/sqlite)
- ✅ **T-M4-C-04** 🟢 [S] Phase 1 構成検出時の移行バナー表示 (POST /migrate/phase1-to-tables のフォームは M10 で実装)
- ✅ **T-M4-C-05** ✅ [S] ブラウザで `localhost:8090` を開いてダッシュボードが表示・静的アセットも 200

- **M4 完了基準**: `adapter web-ui` でサーバ起動、ダッシュボード画面が表示される ✅

---

# M5: Tables CRUD (S-02 / S-03 / S-04)

## M5-A: テーブル一覧

- ⬜ **T-M5-A-01** 🔴🟢 [S] `GET /tables` が `ConfigSource.listTables()` 結果を表示
- ⬜ **T-M5-A-02** 🟢 [S] 各行: name / db_table / jdbc_ref / status / Action
- ⬜ **T-M5-A-03** 🔴🟢 [S] フィルター入力で client-side フィルタ
- ⬜ **T-M5-A-04** 🟢 [S] 「+ New Table」ボタンで `/tables/new` へリンク

## M5-B: テーブル詳細

- ⬜ **T-M5-B-01** 🔴🟢 [S] `GET /tables/{name}` で各設定セクションを表示
- ⬜ **T-M5-B-02** 🔴🟢 [S] 存在しない name で 404
- ⬜ **T-M5-B-03** 🟢 [S] 機密値 (Password 等の環境変数プレースホルダ) をそのまま表示

## M5-C: テーブル編集

- ⬜ **T-M5-C-01** 🔴🟢 [M] `GET /tables/{name}/edit` でフォーム表示
- ⬜ **T-M5-C-02** 🔴🟢 [M] `PUT /tables/{name}` で保存 → 詳細画面へリダイレクト
- ⬜ **T-M5-C-03** 🔴🟢 [S] バリデーション (port が数値 or "auto"、必須カラム漏れチェック)
- ⬜ **T-M5-C-04** 🔴🟢 [S] 「保存して再起動」ボタン: 保存 → `runner.stopOne()` → `runner.startOne()`

## M5-D: テーブル削除

- ⬜ **T-M5-D-01** 🔴🟢 [S] `DELETE /tables/{name}` で削除
- ⬜ **T-M5-D-02** 🔴🟢 [S] 稼働中 Adapter は事前に停止
- ⬜ **T-M5-D-03** 🟢 [S] 確認モーダル

- **M5 完了基準**: A-01〜A-05 動作。Yaml/Sqlite どちらでも

---

# M6: Connections CRUD + sys_connection_props 動的フォーム

## M6-A: 接続一覧

- ⬜ **T-M6-A-01** 🔴🟢 [S] `GET /connections` で共通 JDBC 一覧
- ⬜ **T-M6-A-02** 🟢 [S] 各行: name / driver_class / URL (機密マスク) / [test][edit]
- ⬜ **T-M6-A-03** 🔴🟢 [S] URL 内の `Password=xxx` 等を `Password=***` にマスク

## M6-B: 接続テスト

- ⬜ **T-M6-B-01** 🔴🟢 [S] `POST /connections/{name}/test` で `JdbcConnectionProvider` 経由接続
- ⬜ **T-M6-B-02** 🟢 [S] 成功時: ドライバ名・バージョン取得して返す
- ⬜ **T-M6-B-03** 🟢 [S] 失敗時: 例外メッセージを表示
- ⬜ **T-M6-B-04** 🟢 [S] HTMX で結果フラグメントを返す

## M6-C: JdbcConnectionPropertyInspector

- ⬜ **T-M6-C-01** 🔴🟢 [M] `listProperties(driverClass, jarPath)` で `sys_connection_props` を取得
- ⬜ **T-M6-C-02** 🔴🟢 [S] `ConnectionProperty` への正規化 (Type/Sensitivity/Values パース)
- ⬜ **T-M6-C-03** 🔴🟢 [S] CategoryOrdinal / Ordinal でソート
- ⬜ **T-M6-C-04** 🔴🟢 [S] キャッシュ (driverClass + jar lastModified)
- ⬜ **T-M6-C-05** 🔴🟢 [S] 取得失敗時のフォールバック (空リスト返却)
- ⬜ **T-M6-C-06** 🔴🟢 [S] CData 以外のドライバ対応 (jdbcPrefix 自動判定 or 手動指定)

## M6-D: 動的フォーム

- ⬜ **T-M6-D-01** 🔴🟢 [M] `GET /connections/new` で初期表示 (ドライバ未選択)
- ⬜ **T-M6-D-02** 🔴🟢 [M] ドライバ選択時 (HTMX) `GET /connections/properties?driver=X` でフォーム HTML 返却
- ⬜ **T-M6-D-03** 🟢 [S] カテゴリ別グルーピング、Authentication のみ初期展開
- ⬜ **T-M6-D-04** 🟢 [S] 「Required only」フィルター
- ⬜ **T-M6-D-05** 🟢 [S] Type/Sensitivity/Values に応じた入力要素 (text/password/checkbox/select)
- ⬜ **T-M6-D-06** 🟢 [S] Hierarchy ヒント表示 (ⓘ AuthScheme が ... の場合に有効)

## M6-E: URL プレビュー (下部 sticky)

- ⬜ **T-M6-E-01** 🟢 [S] CSS で `position: sticky; bottom: 0`
- ⬜ **T-M6-E-02** 🔴🟢 [S] `POST /connections/preview-url` でフォーム値から URL 生成
- ⬜ **T-M6-E-03** 🟢 [S] HTMX `hx-trigger="keyup changed delay:200ms"` で発火
- ⬜ **T-M6-E-04** 🟢 [S] Copy / Edit ボタン

## M6-F: 保存

- ⬜ **T-M6-F-01** 🔴🟢 [S] `POST /connections` で `SqliteConfigSource.saveSharedJdbcConfig()` 呼び出し
- ⬜ **T-M6-F-02** 🔴🟢 [S] バリデーション (name 重複チェック、必須プロパティ未入力チェック)

- **M6 完了基準**: ドライバ選択で動的フォーム表示、URL リアルタイム生成、保存・編集・削除

---

# M7: Drivers 画面 (S-11 / S-12)

## M7-A: 一覧

- ⬜ **T-M7-A-01** 🔴🟢 [S] `GET /drivers` で `JdbcDriverManager.listDrivers()` 結果表示
- ⬜ **T-M7-A-02** 🟢 [S] 各行: filename / driver_class / license_status / size / [activate][delete]

## M7-B: アップロード

- ⬜ **T-M7-B-01** 🔴🟢 [M] `POST /drivers/upload` multipart で受信
- ⬜ **T-M7-B-02** 🔴🟢 [S] 最大 50MB 制限
- ⬜ **T-M7-B-03** 🔴🟢 [S] 拡張子 + Magic Number 検証
- ⬜ **T-M7-B-04** 🟢 [S] 完了後リダイレクト + 「アクティベーション要 (未ライセンス)」表示

## M7-C: アクティベーション

- ⬜ **T-M7-C-01** 🔴🟢 [S] `GET /drivers/{filename}/activate` でフォーム表示
- ⬜ **T-M7-C-02** 🔴🟢 [M] `POST /drivers/{filename}/activate` で `DriverActivator` 呼び出し
- ⬜ **T-M7-C-03** 🟢 [S] 進捗表示 (HTMX で結果フラグメント)

## M7-D: 削除

- ⬜ **T-M7-D-01** 🔴🟢 [S] `DELETE /drivers/{filename}` でファイル削除

- **M7 完了基準**: I-01〜I-05 全て動作。実際にアップロード → アクティベーション → 接続成功

---

# M8: Table 新規ウィザード (S-05 〜 S-08)

## M8-A: Step 1 — Connection 選択

- ⬜ **T-M8-A-01** 🔴🟢 [S] `GET /tables/new` でコネクション一覧表示
- ⬜ **T-M8-A-02** 🟢 [S] 「+ Create new connection」リンク

## M8-B: Step 2 — テーブル選択

- ⬜ **T-M8-B-01** 🔴🟢 [M] `GET /tables/new/step2/{conn}` でドライバ接続して `JdbcMetadataInspector.listTables()`
- ⬜ **T-M8-B-02** 🟢 [S] フィルター + ラジオボタン選択
- ⬜ **T-M8-B-03** 🟢 [S] 識別子入力欄 (config name)

## M8-C: Step 3 — カラム選択

- ⬜ **T-M8-C-01** 🔴🟢 [M] `GET /tables/new/step3/{conn}/{table}` で `listColumns()` 結果表示
- ⬜ **T-M8-C-02** 🟢 [S] チェックボックスで複数選択
- ⬜ **T-M8-C-03** 🟢 [S] Select all / Deselect all

## M8-D: Step 4 — マッピング + Capability

- ⬜ **T-M8-D-01** 🔴🟢 [M] `GET /tables/new/step4` で自動マッピング生成 (`FieldTypeSuggester` 利用)
- ⬜ **T-M8-D-02** 🟢 [S] kintone field_id を編集可能 (snake_case 推奨値)
- ⬜ **T-M8-D-03** 🟢 [S] Type ドロップダウン (TEXT/NUMBER/DATETIME/SELECTION)
- ⬜ **T-M8-D-04** 🟢 [S] capability セクション (record-id-type / filterable / sortable / port)
- ⬜ **T-M8-D-05** 🔴🟢 [S] `POST /tables` で保存 + 即起動 (「Save & Start」)

- **M8 完了基準**: ゼロからテーブル作成 → 起動まで Web UI のみで完結

---

# M9: ランタイム制御 + SSE

## M9-A: 起動・停止 API

- ⬜ **T-M9-A-01** 🔴🟢 [S] `POST /tables/{name}/start` で `runner.startOne(name)` を呼ぶ
- ⬜ **T-M9-A-02** 🔴🟢 [S] `POST /tables/{name}/stop` で `runner.stopOne(name)`
- ⬜ **T-M9-A-03** 🔴🟢 [S] `POST /tables/{name}/restart` で stop → start
- ⬜ **T-M9-A-04** 🟢 [S] レスポンスは HTMX フラグメント (該当行の status セル更新)

## M9-B: SSE

- ⬜ **T-M9-B-01** 🔴🟢 [M] `GET /runtime/sse` で `active-adapters` イベントを 1 秒間隔で配信
- ⬜ **T-M9-B-02** 🟢 [S] ブラウザ側 `EventSource` で受信して各 `<tr>` を更新
- ⬜ **T-M9-B-03** 🔴🟢 [S] クライアント切断時のクリーンアップ

- **M9 完了基準**: ブラウザを開いたまま、別タブで操作したアダプターの状態がリアルタイム反映

---

# M10: Salesforce 新階層移行 + 多テーブル E2E

## M10-A: migrate-to-multi-table コマンド

- ⬜ **T-M10-A-01** 🔴🟢 [M] `MigrateToMultiTableCommand` 実装: `config/{server,jdbc,table,capability}.yaml` を `config/tables/<name>/` と `config/jdbc/<name>.yaml` に分離
- ⬜ **T-M10-A-02** 🔴🟢 [S] `jdbc.yaml` を `config/jdbc/<shared_name>.yaml` に移動、`tables/<name>/jdbc-ref.yaml` 作成
- ⬜ **T-M10-A-03** ⚙️ [S] CLI 登録
- ⬜ **T-M10-A-04** 🟢 [S] Web UI ダッシュボード上に「Migrate to multi-table」バナー (検出時)

## M10-B: Salesforce 移行実機確認

- ⬜ **T-M10-B-01** ✅ [S] 既存 `config/{server,jdbc,table,capability}.yaml` をバックアップ
- ⬜ **T-M10-B-02** ✅ [S] `migrate-to-multi-table --table-name account --shared-jdbc-name salesforce` 実行
- ⬜ **T-M10-B-03** ✅ [S] `config/tables/account/` + `config/jdbc/salesforce.yaml` が生成される
- ⬜ **T-M10-B-04** ⚙️ [S] Agent `adapter_addr` を 18001 に変更 (server.yaml で port: 18001 にしておく)
- ⬜ **T-M10-B-05** ✅ [S] `serve-all` で Salesforce + gs-opportunity 同時稼働
- ⬜ **T-M10-B-06** ✅ [S] kintone 上で両方のアプリが正常動作

- **M10 完了基準**: 既存 Phase 1 構成が全て Phase 2-A 新階層に移行され、SF + GS が `serve-all` で並列稼働

---

# M11: ドキュメント / リリース

## M11-A: README

- ✅ **T-M11-A-01** 📝 [S] Web UI クイックスタート追加 (`adapter web-ui` 起動 → ブラウザ)
- ✅ **T-M11-A-02** 📝 [S] CLI 一覧に `web-ui`, `migrate-to-sqlite`, `export-yaml`, `migrate-to-multi-table` 追加
- ⬜ **T-M11-A-03** 📝 [S] スクリーンショット（次回ドキュメントパスで対応）

## M11-B: 永続的ドキュメント更新

- ⬜ **T-M11-B-01** 📝 [S] `docs/architecture.md` への Web UI レイヤ追記（次回パスで対応）
- ⬜ **T-M11-B-02** 📝 [S] `docs/repository-structure.md` への web/ 追加（次回）
- ⬜ **T-M11-B-03** 📝 [S] `docs/glossary.md` 用語追加（次回）

## M11-C: Phase 2-B 用 E2E チェックリスト

- ✅ **T-M11-C-01** 📝 [M] `.steering/20260522-web-ui-and-sqlite/e2e-checklist.md` 作成

## M11-D: タグ・リリース

- ✅ **T-M11-D-01** ⚙️ [S] `v0.3.0-web-ui` タグ作成
- ✅ **T-M11-D-02** ⚙️ [S] push

- **M11 完了基準**: README + E2E チェックリスト更新、タグ付け済み ✅

---

## タスク総数と工数集計

| マイルストーン | タスク数 | 概算工数 |
|---|---|---|
| M1 | 17 | 1.5日 |
| M2 | 10 | 1日 |
| M3 | 15 | 1.5日 |
| M4 | 12 | 1日 |
| M5 | 13 | 1.5日 |
| M6 | 22 | 2日 |
| M7 | 11 | 1日 |
| M8 | 14 | 2日 |
| M9 | 6 | 1日 |
| M10 | 10 | 1日 |
| M11 | 9 | 1.5日 |
| **合計** | **139** | **約14日** |

---

## 進捗ルール

1. **TDD 厳守**: 🔴 → 🟢 → 🔵
2. **マイルストーンごとに全テスト緑を維持**: 189 件 (Phase 2-A) を割らない
3. **設計変更があれば design.md を更新**してから実装
4. **コミット粒度**: マイルストーン単位 or 機能単位の細かいコミット
5. **既知の問題は known-issues.md に記録**: ブロッカーでなければ進める

---

## 関連ドキュメント

- 要求定義: [requirements.md](./requirements.md)
- 設計書: [design.md](./design.md)
- Phase 2-A 設計: [../20260522-multi-table-support/design.md](../20260522-multi-table-support/design.md)
- Phase 2-A known-issues: [../20260522-multi-table-support/known-issues.md](../20260522-multi-table-support/known-issues.md)
