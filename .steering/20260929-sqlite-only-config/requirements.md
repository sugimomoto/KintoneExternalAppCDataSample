# 設定ストアの SQLite 一本化 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | sqlite-only-config |
| 作成日 | 2026-09-29 |
| 前提 | Phase 2-C (Sync 概念 + Docker API 制御) 完了済み |
| 目的 | YAML / SQLite の二重実装を解消し、設定ストアを SQLite 単一実装にする |
| 関連 Issue | [#5](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/5) |

---

## 1. 背景

設定ストアは Phase 2-B で `ConfigSource` インターフェースを切り、
`YamlConfigSource` / `SqliteConfigSource` の 2 実装を併存させた。
併存は YAML からの移行期を想定した措置だったが、以下の理由で維持コストに見合わない。

- 同じ責務の実装が 2 つあり、変更のたびに両方を直す必要がある
- テストも二重（`YamlConfigSourceTest` / `SqliteConfigSourceTest` / `YamlSqliteMigrationTest`）
- 移行系コマンドが 4 本（`migrate-config` / `migrate-to-multi-table` / `migrate-to-sqlite` / `export-yaml`）あり、
  いずれも一度使えば不要になるものが常設されている

### 1.1 調査で判明した現状の不具合

| # | 内容 | 影響 |
|---|---|---|
| 1 | `serve` / `serve-all` が `YamlConfigSource` を直接生成しており `ConfigSourceFactory` を経由しない | **SQLite モードで起動できない**のは Web UI 以外の全経路 |
| 2 | `init-table` / `list-tables` / `test-connection` の `--jdbc-config` 既定値が `./config/jdbc.yaml`（フェーズ1 のパス） | 引数を省くと `NoSuchFileException` で落ちる |
| 3 | `migrate-to-sqlite` が実体 YAML を持たないディレクトリで停止し、**部分的な `config.db` が残る** | 移行が中途半端な状態で終わる |
| 4 | `export-yaml` が共有 JDBC 参照をインライン展開する | **接続文字列が全テーブルディレクトリに複製される** |

1 は本作業で解消される。2 も対象コマンドの改修に含める。
3 と 4 は廃止対象のため修正しない。

---

## 2. スコープ

### 2.1 対象

- `YamlConfigSource` とその関連コードの**完全削除**
- 全実行経路（`web-ui` / `serve` / `serve-all`）の SQLite 化
- YAML を前提とした CLI サブコマンドの廃止・改修
- リポジトリ同梱の YAML 雛形（`*.yaml.example`）の削除
- 関連ドキュメントの更新

### 2.2 スコープ外

- 外部 DB (PostgreSQL / MySQL) の `ConfigSource` 実装
  （`.steering/20260522-web-ui-and-sqlite/requirements.md` で次フェーズ送りとされたまま）
- TLS 終端 / Web UI 認証
- `agent/tables/<name>/agent.json`（Agent 側のトークン保管。設定ストアとは別系統）

---

## 3. 機能要件

| ID | 要件 |
|---|---|
| FR-01 | 設定の読み書きは `SqliteConfigSource` のみを経由する |
| FR-02 | `web-ui` / `serve` / `serve-all` がいずれも SQLite から設定を読んで起動できる |
| FR-03 | `config.db` が存在しない状態で起動した場合、スキーマを自動生成し「連携 0 件」として正常に動作する |
| FR-04 | 環境変数 `CONFIG_SOURCE` を廃止する（選択肢が 1 つしかないため） |
| FR-05 | `--config-dir` から `config.db` のパスを解決する。`--sqlite-path` で明示指定もできる |
| FR-06 | `test-connection` / `list-tables` は SQLite の共有 JDBC 設定を名前で参照する |
| FR-07 | Web UI フッタの `ConfigSource: yaml/sqlite` 表示を削除する |

### 3.1 廃止するコマンド

| コマンド | 廃止理由 |
|---|---|
| `migrate-config` | フェーズ1 → 2-A の YAML 移行専用 |
| `migrate-to-multi-table` | 単一テーブル → マルチテーブルの YAML 移行専用 |
| `migrate-to-sqlite` | 移行元の YAML がなくなる |
| `export-yaml` | YAML 出力先がなくなる |
| `init-table` | YAML 4 ファイルの生成が役割。Web UI の新規連携ウィザードと完全に重複する |

> `init-table` の廃止により `kaml` 依存を完全に除去できる。
> CLI からの連携作成手段は失われるが、通常運用は Web UI から完結する方針と整合する。

### 3.2 残すコマンド

| コマンド | 変更 |
|---|---|
| `web-ui` | `CONFIG_SOURCE` 判定を削除 |
| `serve` / `serve-all` | `SqliteConfigSource` を使う |
| `list-active` | 変更なし（`run/active-adapters.json` を読むのみ） |
| `test-connection` / `list-tables` | `--jdbc-name` で SQLite の共有 JDBC 設定を参照する |

---

## 4. 非機能要件

| ID | 要件 |
|---|---|
| NFR-01 | 既存のユニットテストは、削除対象のもの以外すべて緑を維持する |
| NFR-02 | `ktlintCheck` / `detekt` が緑 |
| NFR-03 | Playwright E2E が緑（SQLite 前提に書き換えたうえで） |
| NFR-04 | `kaml` 依存を `build.gradle.kts` から除去する |

---

## 5. 移行方針

**移行機能は提供しない。** 設定は Web UI から新規に作成する。

> ただし本作業の実施にあたり、現行環境の 11 連携 + 5 共有 JDBC 設定は
> 削除前に `migrate-to-sqlite` を一度実行して `config/config.db` に取り込み済み。
> これは一度きりのデータ変換であり、移行「機能」を残すこととは別。

---

## 6. 受け入れ条件

- [ ] `YamlConfigSource` およびその参照がコードベースに存在しない
- [ ] `grep -r "kaml\|Yaml.default" src/main` が空
- [ ] `web-ui` / `serve` / `serve-all` がすべて SQLite で起動・動作する
- [ ] `config.db` が無い状態から `web-ui` を起動しても異常終了しない
- [ ] `./gradlew test browserTest ktlintCheck detekt` がすべて緑
- [ ] README / docs から YAML 設定ストアに関する記述が消えている

---

## 7. 関連

- Issue [#5](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/5)
- `.steering/20260522-web-ui-and-sqlite/` — `SqliteConfigSource` 追加時の要求・設計
- `docs/extending.md` R-9（設定ストアの差し替え）
