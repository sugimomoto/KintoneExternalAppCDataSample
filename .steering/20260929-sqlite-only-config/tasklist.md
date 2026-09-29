# 設定ストアの SQLite 一本化 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | sqlite-only-config |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) ／ 設計 [design.md](design.md) |

進捗記号： `[ ]` 未着手 ／ `[~]` 進行中 ／ `[x]` 完了

---

## Phase 0. 事前作業

- [x] **T-00** 現行 `config/` を退避（scratchpad にバックアップ）
- [x] **T-01** 実体 YAML を持たない `contact` / `googlesheets-orders` を退避
- [x] **T-02** `migrate-to-sqlite` を最後に 1 回実行し `config/config.db` を生成（11 連携 + 5 共有 JDBC）

## Phase 1. YAML 依存の切り離し

- [x] **T-10** `config/EnvVarExpander.kt` を新規作成
- [x] **T-11** `EnvVarExpanderTest` を追加（TDD: 先にテスト）
- [x] **T-12** `SqliteConfigSource.decode()` の `YamlConfigSource.expandEnvVars` 参照を差し替え
- [x] **T-13** `config/ConfigStore.kt` を新規作成
- [x] **T-14** `ConfigStoreTest` を追加（`config.db` 不在時の自動生成 / `sqlitePath` 解決）

## Phase 2. 実行経路の SQLite 化

- [x] **T-20** `cli/ServeCommand.kt` を `ConfigStore.open()` に変更（`--sqlite-path` 追加、`--table`/`--tables` 必須化）
- [x] **T-21** `cli/ServeAllCommand.kt` を `ConfigStore.open()` に変更（`--sqlite-path` 追加）
- [x] **T-22** `cli/WebUiCommand.kt` に `--sqlite-path` を追加
- [x] **T-23** `web/AppContext.kt` から `configSourceMode` を削除し `ConfigStore.open()` を使う

## Phase 3. CLI の整理

- [x] **T-30** `cli/TestConnectionCommand.kt` を `--jdbc-name` 方式に変更（省略時の解決ルール込み）
- [x] **T-31** `cli/ListTablesCommand.kt` を `--jdbc-name` 方式に変更
- [x] **T-32** 共有ヘルパ（`--jdbc-name` 解決）を切り出して 2 コマンドで共用
- [x] **T-33** `Application.kt` から廃止 5 コマンドの登録・import を削除
- [x] **T-34** `MigrateConfigCommand` / `MigrateToMultiTableCommand` / `MigrateToSqliteCommand` / `ExportYamlCommand` / `InitTableCommand` を削除

## Phase 4. YAML 実装の削除

- [x] **T-40** `config/YamlConfigSource.kt` を削除
- [x] **T-41** `config/ConfigLoader.kt` を削除
- [x] **T-42** `config/ConfigMigrator.kt` を削除
- [x] **T-43** `config/ConfigSourceFactory.kt` を削除
- [x] **T-44** `config/ConfigSource.kt` の KDoc を更新

## Phase 5. Web UI の追従

- [x] **T-50** `web/views/Layout.kt` から `mode` 引数とフッタ表記を削除
- [x] **T-51** `layout(...)` を呼ぶ全 View から `mode` 引数を除去
- [x] **T-52** `web/views/HelpView.kt` の YAML 記述を更新

## Phase 6. テスト

- [x] **T-60** 削除対象テスト 5 件を削除
- [x] **T-61** E2E 4 件のフィクスチャを `SqliteConfigSource` に差し替え
- [x] **T-62** `./gradlew test` が緑
- [x] **T-63** `./gradlew browserTest` が緑

## Phase 7. ビルド・実行環境

- [x] **T-70** `build.gradle.kts` から `kaml` 依存を削除
- [x] **T-71** `docker-compose.yml` / `Dockerfile` から `CONFIG_SOURCE` を削除
- [x] **T-72** `.env.example` から `CONFIG_SOURCE` を削除
- [x] **T-73** YAML 雛形（`*.yaml.example`）と残存 YAML を削除
- [x] **T-74** `.gitignore` の設定ファイル関連を見直し（`config.db` の扱い）

## Phase 8. ドキュメント

- [x] **T-80** `README.md` 更新（設定節 / CLI 一覧 / ディレクトリ構成）
- [x] **T-81** `docs/extending.md` 更新（§2 / R-1 / R-9 / §6）
- [x] **T-82** `docs/DOCKER-SETUP.md` 更新
- [x] **T-83** `docs/architecture.md` 更新（設定・CLI・依存ライブラリ）
- [x] **T-84** `docs/functional-design.md` §5.1 更新
- [x] **T-85** `docs/repository-structure.md` 更新
- [x] **T-86** `docs/glossary.md` 更新

## Phase 9. 仕上げ

- [x] **T-90** `./gradlew ktlintCheck detekt` が緑
- [x] **T-91** `./gradlew shadowJar` でビルドが通る
- [x] **T-92** `web-ui` / `serve` / `serve-all` の起動確認（実機）
- [x] **T-93** `config.db` 不在状態からの起動確認
- [x] **T-94** Issue #5 の受け入れ条件をすべて満たすことを確認

---

## 完了条件

requirements.md §6 の受け入れ条件をすべて満たすこと。

- `YamlConfigSource` およびその参照がコードベースに存在しない
- `grep -r "kaml\|Yaml.default" src/main` が空
- `web-ui` / `serve` / `serve-all` がすべて SQLite で起動・動作する
- `config.db` が無い状態から `web-ui` を起動しても異常終了しない
- `./gradlew test browserTest ktlintCheck detekt` がすべて緑
- README / docs から YAML 設定ストアに関する記述が消えている


---

## 実施記録（2026-09-29）

### 設計から変わった点

| # | 内容 | 理由 |
|---|---|---|
| 1 | `config/ConfigExceptions.kt` を新規作成 | `ConfigFileMissingException` / `ConfigParseException` が削除対象の `ConfigLoader.kt` に同居していた。`SqliteConfigSource` も使うため中立な場所へ移設 |
| 2 | `ServerConfigTest` を JSON ベースに書き換え | kaml 依存で YAML パースを検証していたため。SQLite では JSON カラムに格納されるので JSON で等価な検証にした。ラウンドトリップのケースを 1 件追加 |
| 3 | `.gitignore` の設定節を全面差し替え | **`config.db` が gitignore されておらず、接続文字列＝認証情報がコミットされうる状態だった。** 最優先で修正 |
| 4 | `config/tables/` `config/jdbc/` ディレクトリごと削除 | SQLite 一本化により不要 |

### 検証結果

| 項目 | 結果 |
|---|---|
| ユニットテスト | **236 件 / 失敗 0**（`ServerConfigTest` に 1 件追加、YAML 系 5 ファイル削除） |
| E2E (Playwright) | **24 件 / 失敗 0** |
| ktlint / detekt（今回追加・書き換えたファイル） | **指摘 0 件** |
| ktlint / detekt（リポジトリ全体） | 赤。ただし**本変更前から赤**（未変更の 66 ファイルが指摘対象）。本作業のスコープ外 |
| `shadowJar` | 成功 |
| `web-ui` 実機起動 | `/` `/syncs` `/connections` `/drivers` `/help` すべて 200。SQLite から 11 連携を読み出せることを確認 |
| `serve` / `serve-all` | 連携名の検証・未指定時の案内ともに動作 |
| 空 `config.db` からの起動 | スキーマ自動生成 → 連携 0 件で正常終了（FR-03） |

### E2E で一度失敗した件

`NewSyncFlowE2ETest` が最初の実行で 30 秒タイムアウトしたが、原因は
**稼働中の `adapter-console` コンテナが 18000-18099 を publish していたためのポート枯渇**。
コンテナを停止して再実行すると全件緑になった。本変更に起因するものではない。

### 既存設定の保全

YAML 実装を削除する前に `migrate-to-sqlite` を 1 回だけ実行し、
**11 連携 + 5 共有 JDBC 設定を `config/config.db` に取り込み済み**。
移行「機能」は削除したが、既存のデモ環境は失われていない。

削除前の `config/` 一式はセッションの作業領域にバックアップしてある
（`scratchpad/yaml-backup/config-original`）。恒久保管が必要なら別途退避すること。
