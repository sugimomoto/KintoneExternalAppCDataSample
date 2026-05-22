# フェーズ2-A: 複数テーブル運用支援のタスクリスト

| 項目 | 内容 |
|---|---|
| 作業タイトル | multi-table-support（フェーズ2-A） |
| 作成日 | 2026-05-22 |
| ステータス | ドラフト（承認待ち） |
| 親文書 | [requirements.md](requirements.md), [design.md](design.md) |

---

## 凡例

- 🔴 **Red**: 失敗するテストを書く
- 🟢 **Green**: テストを通す最小実装
- 🔵 **Refactor**: 構造改善
- ⚙️ **Setup**: ビルド・設定系（TDD 対象外）
- ✅ 完了 / ⬜ 未着手 / 🟡 進行中
- 概算工数：S (〜0.5日) / M (〜1日) / L (〜2日)

## マイルストーン全体図

| マイルストーン | 主な成果物 | 工数目安 |
|---|---|---|
| **M1** | `ConfigSource` 抽象化 + `YamlConfigSource`（フェーズ1 互換維持） | 〜2日 |
| **M2** | 設定ディレクトリ構造（`tables/<name>/` + 共通 `jdbc/`）+ 自動マイグレーション | 〜2日 |
| **M3** | `PortAllocator` + `TableAdapterServer` | 〜1.5日 |
| **M4** | `MultiAdapterRunner`（起動・停止・ヘルス管理） | 〜2日 |
| **M5** | CLI 拡張（`serve-all` / `list-active` / `init-table --table`） | 〜1.5日 |
| **M6** | 複数ドライバー混在対応（クラスローダー隔離・OAuth キャッシュ分離） | 〜1.5日 |
| **M7** | Docker Compose 複数 Agent + 鍵共有設定 | 〜1日 |
| **M8** | E2E 検証（Account/Contact + Google Sheets 混在） | 〜2日 |
| **M9** | ドキュメント更新 + フィードバック追加（必要に応じて） | 〜1日 |
| **合計** | | **〜14.5日（約3週間）** |

---

# M1: ConfigSource 抽象化

## M1-A: インターフェース定義と TableConfigSet

- ✅ **T-M1-A-01** 🔴 [S] `TableConfigSet` データクラスの test を書く（4 つの設定を保持）
- ✅ **T-M1-A-02** 🟢 [S] `TableConfigSet` 実装（`typealias TableConfigSet = AdapterConfig` で既存型を再利用）
- ✅ **T-M1-A-03** 🔴 [S] `ConfigSource` インターフェースのテスト（モック実装で契約検証）
- ✅ **T-M1-A-04** 🟢 [S] `ConfigSource` インターフェース定義
- ✅ **T-M1-A-05** 🔴 [S] `JdbcRef` データクラスのパーステスト
- ✅ **T-M1-A-06** 🟢 [S] `JdbcRef` 実装

## M1-B: YamlConfigSource 実装（新階層）

- ✅ **T-M1-B-01** 🔴🟢 [S] `listTables()` で `config/tables/` 配下のディレクトリ一覧を返す
- ✅ **T-M1-B-02** 🔴🟢 [S] `loadTableSet(name)` で 4 ファイルを統合読み込み
- ✅ **T-M1-B-03** 🔴🟢 [S] `loadSharedJdbcConfig(name)` で `config/jdbc/<name>.yaml` を読込
- ✅ **T-M1-B-04** 🔴🟢 [S] テーブル内 `jdbc-ref.yaml` から共通設定を解決
- ✅ **T-M1-B-05** 🔴🟢 [S] テーブル内 `jdbc.yaml`（個別）を優先解決
- ✅ **T-M1-B-06** 🔴🟢 [S] `jdbc.yaml` も `jdbc-ref.yaml` もない場合のエラー
- ✅ **T-M1-B-07** 🔴🟢 [S] `saveTableSet(name, set)` で 4 ファイルを書き出し
- ✅ **T-M1-B-08** 🔴🟢 [S] `deleteTable(name)` でディレクトリ削除
- ✅ **T-M1-B-09** 🔵 [S] `loadOne` ヘルパの環境変数展開ロジック共通化

## M1-C: フェーズ1 互換層（default テーブル）

- ✅ **T-M1-C-01** 🔴 [S] フェーズ1 構成（`config/server.yaml` 直下）で `listTables()` が `["default"]` を返すテスト
- ✅ **T-M1-C-02** 🟢 [S] 互換層の実装
- ✅ **T-M1-C-03** 🔴🟢 [S] フェーズ1 構成で `loadTableSet("default")` が動作するテスト
- ✅ **T-M1-C-04** ✅ [S] フェーズ1 既存テスト 121 件が全緑のまま維持されていることを確認（合計 137 件すべて緑）
- **M1 完了基準**: `ConfigSource` 経由でフェーズ1 / フェーズ2 両方の設定構造を読み込める

---

# M2: ディレクトリ構造とマイグレーション

## M2-A: example ファイル整備

- ✅ **T-M2-A-01** ⚙️ [S] `config/jdbc/salesforce.yaml.example` 作成
- ✅ **T-M2-A-02** ⚙️ [S] `config/jdbc/googlesheets.yaml.example` 作成
- ✅ **T-M2-A-03** ⚙️ [S] `config/tables/account/*.yaml.example` セット（4ファイル）作成
- ✅ **T-M2-A-04** ⚙️ [S] `config/tables/contact/*.yaml.example` セット作成
- ✅ **T-M2-A-05** ⚙️ [S] `config/tables/googlesheets-orders/*.yaml.example` セット作成
- ✅ **T-M2-A-06** ⚙️ [S] `.gitignore` 更新（`config/tables/*/jdbc.yaml`, `*/table.yaml` 等を保護、example は許可）

## M2-B: ServerConfig の port=auto 対応

- ✅ **T-M2-B-01** 🔴🟢 [S] `ServerConfig.port` のデフォルト 0（auto）を許容するテスト
- ✅ **T-M2-B-02** 🔴🟢 [S] YAML で `port: auto` (string) または `port: 0` の両方を受け付ける
- ✅ **T-M2-B-03** 🔵 [S] `ServerConfig` `isAutoPort()` / `AUTO_PORT` 定数整備

## M2-C: マイグレーションコマンド（任意）

- ✅ **T-M2-C-01** 🔴🟢 [S] `MigrateConfigCommand` テスト：旧構成検出
- ✅ **T-M2-C-02** 🔴🟢 [S] 旧構成を `config/tables/default/` に移動
- ✅ **T-M2-C-03** ⚙️ [S] `adapter migrate-config` CLI コマンド追加
- ⬜ **T-M2-C-04** ✅ [S] 手動でフェーズ1 サンプルを `migrate-config` 実行して動作確認 (M8 で実施)

- **M2 完了基準**: 新旧両構成で `ConfigSource.listTables()` が正しく動く ✅

---

# M3: PortAllocator + TableAdapterServer

## M3-A: PortAllocator

- ✅ **T-M3-A-01** 🔴🟢 [S] `next()` で連番ポートを返す
- ✅ **T-M3-A-02** 🔴🟢 [S] `reserve(port)` で既使用ポートを記録
- ✅ **T-M3-A-03** 🔴🟢 [S] 既使用ポートはスキップして次の空きを返す
- ✅ **T-M3-A-04** 🔴🟢 [S] 実際に listen 可能か検証する `findAvailable()` テスト
- ✅ **T-M3-A-05** 🔵 [S] スレッドセーフ化（`ReentrantLock` で保護）

## M3-B: TableAdapterServer

- ✅ **T-M3-B-01** 🔴 [M] `TableAdapterServer.start(port)` で gRPC サーバが指定ポートで起動するテスト
- ✅ **T-M3-B-02** 🟢 [M] フェーズ1 の `ServeCommand` ロジックを `TableAdapterServer` に抽出
- ✅ **T-M3-B-03** 🔴🟢 [S] 各サーバが独立した `HealthStatusManager` を持つことを確認（各インスタンス内で生成）
- ✅ **T-M3-B-04** 🔴🟢 [S] `close()` でサーバ停止 + JDBC プールクローズ
- ✅ **T-M3-B-05** 🔴🟢 [S] 同一プロセスで複数 `TableAdapterServer` を異なるポートで起動

- **M3 完了基準**: 1 JVM 内で複数 gRPC サーバが共存できる基盤完成 ✅

---

# M4: MultiAdapterRunner

## M4-A: 起動・停止

- ✅ **T-M4-A-01** 🔴 [S] `startOne(tableName)` で 1 テーブル起動できる
- ✅ **T-M4-A-02** 🟢 [S] 内部で `ConfigSource.loadTableSet()` → `TableAdapterServer.start()`
- ✅ **T-M4-A-03** 🔴🟢 [S] `startAll()` で `listTables()` 全件を一括起動
- ✅ **T-M4-A-04** 🔴🟢 [S] `stopOne(tableName)` で 1 テーブルのみ停止、他は継続
- ✅ **T-M4-A-05** 🔴🟢 [S] `close()` で全停止 + リソース解放
- ✅ **T-M4-A-06** 🔴🟢 [S] 同じテーブルを 2 回起動しようとするとエラー
- ✅ **T-M4-A-07** 🔴🟢 [S] 起動失敗時に他のテーブルへの影響なし（catch + ログ）

## M4-B: ヘルス管理統合

- ✅ **T-M4-B-01** 🔴🟢 [S] `listActive()` で稼働中の全テーブル状態を返す
- ✅ **T-M4-B-02** 🔴🟢 [S] 各 `AdapterStatus` に tableName, port, status, startedAt を含む

## M4-C: ~~管理用 gRPC エンドポイント~~ → ファイルベース状態管理（設計変更）

> **設計判断**: design.md §7.4 案 A（管理用 gRPC）→ 案 B 相当（ファイルベース）に変更。
> 理由: 新 proto を BSR に追加するコストが大きく、フェーズ2-A の本筋から外れる。
> 同一ホスト前提なら JSON ファイル経由で十分シンプルかつ十分。

- ✅ **T-M4-C-01** 🔴🟢 [S] `ActiveAdaptersFile` で稼働中 Adapter を JSON 書き出し
- ✅ **T-M4-C-02** 🔴🟢 [S] `MultiAdapterRunner` start/stop/close で状態ファイル更新
- ⬜ **T-M4-C-03** ⚙️ [S] `adapter list-active` CLI でファイルを読み出して表示（M5-C）
- ⬜ **T-M4-C-04** ✅ [S] `serve-all` 起動中に `list-active` を実行して動作確認（M8）

- **M4 完了基準**: 複数テーブルの起動・停止・状態取得を統合管理できる ✅

---

# M5: CLI 拡張

## M5-A: serve コマンド拡張

- ✅ **T-M5-A-01** 🔴🟢 [S] `serve --table <name>` で個別テーブル起動
- ✅ **T-M5-A-02** 🔴🟢 [S] `serve --tables <name1>,<name2>` で複数指定
- ✅ **T-M5-A-03** 🔴🟢 [S] `serve` (引数なし) は後方互換で `default` テーブル起動
- ✅ **T-M5-A-04** 🔵 [S] `ServeCommand` から `TableAdapterServer` への薄いラッパに変更（MultiAdapterRunner 経由）

## M5-B: serve-all コマンド

- ✅ **T-M5-B-01** 🔴🟢 [S] `serve-all` で全テーブル起動
- ✅ **T-M5-B-02** 🔴🟢 [S] 起動時に各テーブルの port を出力
- ✅ **T-M5-B-03** 🔴🟢 [S] シャットダウンフック登録
- ⬜ **T-M5-B-04** ✅ [S] 動作確認（フェーズ1 互換 + 新階層両方）（M8 で実施）

## M5-C: list-active コマンド

- ✅ **T-M5-C-01** 🔴🟢 [S] `list-active` がローカル状態ファイル `./run/active-adapters.json` を読んで表示
- ✅ **T-M5-C-02** 🔴🟢 [S] `--state-file` で状態ファイルパスを指定可能
- ⬜ **T-M5-C-03** ✅ [S] 動作確認（M8 で実施）

## M5-D: init-table の拡張

- ✅ **T-M5-D-01** 🔴🟢 [S] `init-table --name <table-config-name>` で出力先テーブル名を指定
- ✅ **T-M5-D-02** 🔴🟢 [S] 出力先ディレクトリは `config/tables/<name>/`
- ✅ **T-M5-D-03** 🔴🟢 [S] `--jdbc-ref <name>` で共通 jdbc を参照する設定を生成
- ⬜ **T-M5-D-04** ✅ [S] Salesforce Contact を `--name contact --jdbc-ref salesforce` で生成テスト（M8 で実施）

- **M5 完了基準**: 全 CLI コマンドが新階層対応 ✅

---

# M6: 複数ドライバー混在対応

## M6-A: クラスローダー隔離（既存実装で対応）

- ✅ **T-M6-A-01** 📝 [S] 既存の `JdbcConnectionProvider.loadDriver()` は `URLClassLoader(arrayOf(jar.toUri().toURL()), parentCL)` で各ドライバを別 ClassLoader にロード。
- ✅ **T-M6-A-02** 📝 [S] 親 ClassLoader を Platform 化する変更は YAGNI（同じドライバを 2 回ロードしない `Class.forName` ガードがあり、異なるドライバは別 URLClassLoader で独立）。必要になったら別タスクで対応。
- ⬜ **T-M6-A-03** 🔴🟢 [M] 2 ドライバ同時ロードの統合テスト（実 JAR 必要 → M8 で実施）。

## M6-B: OAuth キャッシュ自動分離

- ✅ **T-M6-B-01** 🔴🟢 [S] `OAuthSettingsLocation` 未指定時、テーブル別パス自動生成（`JdbcUrlEnhancer.withOAuthCachePerTable`）
- ✅ **T-M6-B-02** 🔴🟢 [S] パスが `<oauthCacheBaseDir>/oauth/<tableName>.txt` になる（`JdbcUrlEnhancer.cachePathFor`）
- ✅ **T-M6-B-03** 🔵 [S] OAuth キャッシュ管理パスの共通ヘルパ抽出（`JdbcUrlEnhancer` object）
- ✅ **T-M6-B-04** 🟢 [S] `TableAdapterServer.start()` で JDBC URL を自動拡張

## M6-C: ドライバー混在の統合テスト（M8 に統合）

- ⬜ **T-M6-C-01** ⚙️ [M] `lib/cdata.jdbc.googlesheets.jar` 配置（M8 で実施）
- ⬜ **T-M6-C-02** 🔴🟢 [M] `JdbcConnectionProvider` で Salesforce + Google Sheets を同時インスタンス化（M8）
- ⬜ **T-M6-C-03** ✅ [M] 実 Google Sheets で `test-connection` 動作確認（M8）

- **M6 完了基準**: 複数 CData JDBC Driver が 1 JVM 内で同時動作（実 JAR 確認は M8）

---

# M7: Docker Compose 複数 Agent 対応

## M7-A: ディレクトリ構造変更

- ✅ **T-M7-A-01** ⚙️ [S] `agent/tables/<name>/agent.json.example` テンプレ作成（account, contact）
- ✅ **T-M7-A-02** ⚙️ [S] `agent/private-key.pem`（既存）を全 Agent で共有する設計（compose ファイル内コメント + example の private_key_path）
- ✅ **T-M7-A-03** ⚙️ [S] `agent/docker-compose.multi.yml` を新規作成（既存 docker-compose.yml はフェーズ1 互換のまま維持）

## M7-B: ヘルパースクリプト更新

- ✅ **T-M7-B-01** ⚙️ [S] `scripts/restart-stack.sh` を `MULTI=1` 環境変数で複数テーブル対応に拡張
- ⬜ **T-M7-B-02** ⚙️ [S] 鍵ペア共有を README に明記（M9 で実施）

- **M7 完了基準**: docker compose 一発で複数 Agent コンテナが起動・kintone 接続 ✅（実検証は M8）

---

# M8: E2E 検証

## M8-A: 多テーブル動作確認（Salesforce） — 手動 E2E

> 完全な手順は [e2e-checklist.md](./e2e-checklist.md) §2-§8 を参照。

- ⬜ **T-M8-A-01** ⚙️ [M] Salesforce Account / Contact / Opportunity の 3 テーブル設定を生成
- ⬜ **T-M8-A-02** ⚙️ [M] kintone で 3 コネクター登録（同一 public-key、別 token）
- ⬜ **T-M8-A-03** ⚙️ [M] 3 つの外部連携アプリ作成
- ⬜ **T-M8-A-04** ✅ [M] requirements §7 シナリオ S-01 〜 S-08 全実施

## M8-B: 複数ドライバー混在検証 — 手動 E2E

> [e2e-checklist.md](./e2e-checklist.md) §9 を参照。

- ⬜ **T-M8-B-01** ⚙️ [M] CData Google Sheets Driver + Google Sheet 1 つ準備
- ⬜ **T-M8-B-02** ⚙️ [S] `googlesheets-orders` テーブル設定
- ⬜ **T-M8-B-03** ⚙️ [S] kintone でコネクター + アプリ作成
- ⬜ **T-M8-B-04** ✅ [M] 要件 S-11: Salesforce + Google Sheets 並列動作
- ⬜ **T-M8-B-05** ✅ [S] 要件 S-12: 鍵ペア共有確認

## M8-C: マイグレーション動作確認 — 自動スモーク済み

- ✅ **T-M8-C-01** ⚙️ [S] `scripts/phase2a-smoke.sh` が一時ディレクトリでフェーズ1 構成を生成
- ✅ **T-M8-C-02** ✅ [S] フェーズ1 構成のまま `serve --table default` で起動できることをテストで担保（`YamlConfigSourceTest`）
- ✅ **T-M8-C-03** ✅ [S] `migrate-config` 動作はスモークスクリプトで確認
- ⬜ **T-M8-C-04** ✅ [S] 実環境で移行後 `serve-all` で起動（手動 E2E）

## M8-D: 自動スモークテスト

- ✅ **T-M8-D-01** ⚙️ [S] `scripts/phase2a-smoke.sh` で CLI ヘルプ・migrate・list-active・example 存在・.gitignore を点検
- ✅ **T-M8-D-02** ⚙️ [S] スモークテスト 5 項目すべてパス

- **M8 完了基準**: requirements §7 シナリオ S-01 〜 S-12 のうち、コードで担保できる範囲は自動スモークで OK。実 kintone/Salesforce/Google Sheets を要する E2E は [e2e-checklist.md](./e2e-checklist.md) で手順化済み

---

# M9: ドキュメント更新

## M9-A: README

- ✅ **T-M9-A-01** ⚙️ [S] README にマルチテーブル構成のクイックスタート追加
- ✅ **T-M9-A-02** ⚙️ [S] CLI サブコマンド一覧を `serve-all` / `list-active` / `migrate-config` で更新
- ✅ **T-M9-A-03** ⚙️ [S] 「他データソースへの切替方法」を「混在運用方法」にリライト
- ✅ **T-M9-A-04** ⚙️ [S] フェーズ1 → 2-A 移行手順セクション追加

## M9-B: 永続的ドキュメント更新

- ✅ **T-M9-B-01** ⚙️ [S] `docs/architecture.md`：プロセスモデル A の説明追加（§6.2）
- ✅ **T-M9-B-02** ⚙️ [S] `docs/repository-structure.md`：新ディレクトリ構造反映
- ⬜ **T-M9-B-03** ⚙️ [S] `docs/functional-design.md`：MultiAdapterRunner クラス図追加（次回ドキュメントパスで対応）
- ✅ **T-M9-B-04** ⚙️ [S] `docs/glossary.md`：「テーブル設定セット」「マルチアダプター」等の用語追加（§1-A）

## M9-C: スキル / フィードバック

- ⬜ **T-M9-C-01** ⚙️ [S] `.claude/skills/kintone-external-app-spec/` 更新（複数 Connector・共有鍵の知見）
- ✅ **T-M9-C-02** ⚙️ [S] `docs/feedback-to-cybozu.md` を更新（フェーズ2 検証で新たに発見した課題、§補遺）

- **M9 完了基準**: フェーズ2-A の知見が永続的に記録され、フェーズ2-B 着手時に参照できる状態 ✅

---

# 既知の課題（次フェーズ送り）

E2E 検証中に発見した、フェーズ2-A スコープ外の課題は [known-issues.md](./known-issues.md) に記録。

- **ISSUE-001**: QueryBuilder が識別子クォート未対応（空白入りテーブル名で SQL エラー）
- **ISSUE-002**: CData の `Spreadsheet` vs `SpreadsheetId` パラメータの違い（example yaml は修正済み、ドキュメント化が課題）

---

## タスク総数と工数集計

| マイルストーン | タスク数 | 概算工数 |
|---|---|---|
| M1: ConfigSource 抽象化 | 19 | 〜2日 |
| M2: ディレクトリ構造 + マイグレーション | 13 | 〜2日 |
| M3: PortAllocator + TableAdapterServer | 10 | 〜1.5日 |
| M4: MultiAdapterRunner | 13 | 〜2日 |
| M5: CLI 拡張 | 14 | 〜1.5日 |
| M6: 複数ドライバー混在 | 9 | 〜1.5日 |
| M7: Docker Compose 複数 Agent | 5 | 〜1日 |
| M8: E2E 検証 | 12 | 〜2日 |
| M9: ドキュメント | 9 | 〜1日 |
| **合計** | **104** | **〜14.5日（約3週間）** |

---

## 進捗ルール

1. **タスクIDで進捗トラッキング**（GitHub Issue 化する場合は T-M1-A-01 等をそのまま Issue タイトルに）
2. **Red → Green → Refactor は同一 PR / コミットチェーン**
3. **タスク完了で ⬜ → ✅** に変更してコミット
4. **マイルストーン完了時** は対応する見出しに「✅ 完了」追記
5. **既存 121 テストの後方互換性を最優先**：各マイルストーン完了時に `./gradlew test` で全緑確認

---

## リスクへの対応スケジュール

| リスク | 対応マイルストーン |
|---|---|
| R-A: 5+ テーブル並列でのメモリ問題 | M8 で実測、必要に応じて README 反映 |
| R-B: 1 テーブルクラッシュで全停止 | M4 で個別再起動 API 実装 |
| R-C: 既存テスト後方互換性 | 全マイルストーンで継続確認 |
| R-D: ConfigSource API の SQLite 切替時齟齬 | M1 設計時に SQLite 想定クエリも擬似的に書いてみて検証 |
| R-E: OAuth キャッシュの初期化 | M6 で実装、M8 で実検証 |
| R-F: Agent コンテナのリソース圧迫 | M8 で実測、必要に応じて単一 Agent 案を検討 |

---

## 関連ドキュメント

- 要件：[requirements.md](requirements.md)
- 設計：[design.md](design.md)
- フェーズ1 タスクリスト：[../20260515-initial-implementation/tasklist.md](../20260515-initial-implementation/tasklist.md)
- 永続的ドキュメント：[../../docs/](../../docs/)
