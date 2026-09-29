# プロダクト要求定義書（Product Requirements Document）

| 項目 | 内容 |
|---|---|
| プロダクト名 | kintone External App CData Adapter Sample（仮称） |
| バージョン | 1.0（フェーズ1） |
| 作成日 | 2026-05-15 |
| ステータス | ドラフト（承認待ち） |

---

## 1. プロダクトビジョン

> kintone の「外部システムのアプリ化」機能において、**CData JDBC Driver 経由で 250+ の多様なデータソースに接続できる Adapter のリファレンス実装** を OSS サンプルとして提供する。

### なぜ作るか（背景）

- kintone の「外部システムのアプリ化」機能では、各データソースへの接続を担う **Adapter** を顧客／パートナーが個別実装する必要がある
- サイボウズ提供のサンプル Adapter は **Prisma ORM ベース** であり、MySQL / PostgreSQL 等の限定的な DB しか実用にならない
- Salesforce / SAP / Oracle / Snowflake / Google Sheets 等の **SaaS・エンタープライズデータソース** に接続したい場合、個別開発の難易度が非常に高い
- インテグレーター（パートナーSI）が個別案件で実装するには敷居が高く、CData JDBC Driver と組み合わせる方式を **サンプル＋サポート役務** で提供する価値が大きい

### ビジョン

- **「Salesforce のデータをそのまま kintone アプリのように扱える」** を、設定ファイル1つで実現する
- 250+ のデータソース対応を、サンプル本体のコード変更なし（設定ファイル変更のみ）で切替可能にする
- CData がパートナー SI に対して「Adapter のサンプルコード + サポート役務」を提供するビジネス展開の素地を作る

---

## 2. ターゲットユーザーと課題・ニーズ

### 主要ターゲット：インテグレーター／パートナーSI

| 項目 | 内容 |
|---|---|
| ペルソナ像 | kintone を業務システム基盤として顧客に提案・構築する Sler エンジニア |
| 技術背景 | Java/Kotlin/Node.js のいずれかに知見、Web API 統合経験あり |
| 課題 | エンドユーザーから「kintone から SAP / Salesforce のデータを直接参照・編集したい」要望があるが、Adapter の新規開発は工数大、保守も負担 |
| ニーズ | データソース別の Adapter を個別開発するのではなく、**1つの実装で多数のデータソースに対応** したい。検証コスト・保守コスト最小化したい |

### サブターゲット：CData 社内エバンジェリスト・営業

| 項目 | 内容 |
|---|---|
| ペルソナ像 | kintone 案件のあるパートナー向けに技術提案・PoC 支援を行う担当者 |
| 課題 | 「Adapter のサンプルがあれば話が早いが、現状の TS/Prisma サンプルではエンタープライズデータソースをデモできない」 |
| ニーズ | Salesforce 等の SaaS データを kintone アプリ化して見せる **デモ環境**。技術検証コストを下げて商談化につなげたい |

### 想定しないユーザー（除外スコープ）

- **エンドユーザー（業務担当者）** が直接設定・起動する想定はしない。SIer が構築・運用する前提
- kintone を使ったことがない開発者
- CData 製品を全く知らない開発者（最低限の知識を前提とする）

---

## 3. 主要な機能一覧

### 必須機能（MUST）

| ID | 機能 | 説明 |
|---|---|---|
| F-01 | Adapter プロセスの起動 | 設定ファイル指定で Connect RPC サーバを起動 |
| F-02 | GetCapability 応答 | Adapter が提供する機能を kintone 側に宣言 |
| F-03 | GetSchema 応答 | 設定ファイルに基づいてフィールド定義を返す |
| F-04 | Select 実装 | フィルター・ソート・ページネーション対応 |
| F-05 | Insert 実装 | レコード追加と生成 ID 返却（NUMBER/TEXT 両対応） |
| F-06 | Update 実装 | ID 指定で部分更新 |
| F-07 | Delete 実装 | ID 指定で複数レコード削除 |
| F-08 | Count 実装 | フィルター適用後の件数取得。データソースによっては COUNT クエリが重いケースに備え、**常に 0 を返すモード**を設定可能（FR-07 参照） |
| F-09 | FilterCondition 主要パターン対応 | kintone UI で使われる FilterCondition 37/39 種を SQL 変換（除外: `multiple_selection_in/not_in` のみ。理由は機能要件 FR-02 参照） |
| F-10 | 設定ストア | **SQLite (`config/config.db`)** に一元管理（FR-04 参照）<br>※ 当初は YAML 4 ファイル分割。2026-09-29 に SQLite へ一本化 |
| F-11 | RecordIdType の NUMBER/TEXT 両対応 | Salesforce 等の文字列 ID データソースに対応 |
| F-12 | エラーハンドリング | バリデーション失敗時に適切な ConnectError を返却 |
| F-13 | curl による動作確認 | Connect プロトコルの JSON 経由でローカルテスト可能 |
| F-14 | Docker イメージ提供 | 1コマンドで起動可能なコンテナイメージ |
| F-15 | 連携作成ウィザード | Web UI `/syncs/new` で JDBC メタデータから設定を構築（FR-08 参照）<br>※ 当初は `init-table` CLI。2026-09-29 に Web UI へ集約 |
| F-16 | 補助 CLI サブコマンド | `web-ui` / `serve` / `serve-all` / `list-active` / `list-tables` / `test-connection` |

### 望ましい機能（SHOULD）

| ID | 機能 | 説明 |
|---|---|---|
| F-21 | Search 実装（基本） | `LIKE` ベースの簡易検索 |
| F-22 | Aggregate 実装（基本） | COUNT / SUM / AVG / MAX / MIN と GroupBy DATETIME_* |
| F-23 | 環境変数による接続情報の上書き | 接続文字列内 `${VAR_NAME}` で環境変数展開 |
| F-24 | ヘルスチェックエンドポイント | `/health` などで起動状態確認 |
| F-25 | ログレベル設定 | 環境変数 `LOG_LEVEL` でログ出力レベル切替 |

### 除外機能（WON'T、フェーズ2以降）

| ID | 機能 | 説明 |
|---|---|---|
| F-91 | 複数テーブル対応 UI | 複数 Adapter/Agent を1つの UI で管理 |
| F-92 | 設定 GUI | ~~YAML を編集せず GUI で設定変更~~ → Phase 2-B で実装済み |
| F-93 | kintone Connect AI 連携 | gRPC プロキシ経由の中継 |
| F-94 | MultipleSelectionField | kintone UI 側で未対応のため見送り |
| F-95 | 自動デプロイ | EC2/Cloud Run へのワンクリックデプロイ |

---

## 4. 成功の定義

### 定量指標

| 指標 | 目標値 |
|---|---|
| Salesforce との接続を試せるまでの所要時間（手順実行～動作確認まで） | **30 分以内** |
| 設定ファイル変更だけで切替えられるデータソース数 | CData JDBC Driver 全 250+ |
| FilterCondition 対応カバレッジ | **37/39 ケース**（`multiple_selection_*` の2件のみ除外） |
| 動作確認可能な curl サンプル数 | **9 RPC × 主要パターン = 約 20 件** |

### 定性指標

- パートナー SI が README 通りに実行して、エラーなく Salesforce → kintone 連携を実現できる
- CData 社内エバンジェリストがデモ環境を 10 分で立ち上げられる
- 一般的な Java/Kotlin エンジニアが、ソースを読んで30分以内に「カスタム実装する場合の修正箇所」を理解できる

---

## 5. ビジネス要件

### 提供形態

- **OSS サンプルコード**として提供（Apache License 2.0）
- GitHub への公開は **当面 Private**（フェーズ1完了後に再検討）
- 公開タイミングはサイボウズの本機能正式リリースに合わせる

### 想定するビジネスモデル（参考、フェーズ1スコープ外）

| 役割 | 想定内容 |
|---|---|
| CData | サンプルコード・ドキュメント・技術サポート役務の提供 |
| CData JDBC Driver | 別途ライセンス販売（OEM/ランタイム） |
| パートナー SI | サンプルをベースに顧客案件でカスタマイズ実装・運用提供 |
| エンドユーザー | パートナー SI 経由で導入。kintone + 外部システム連携の運用 |

### 制約

- 「外部システムのアプリ化」機能自体がサイボウズの **未公開情報** を含むため、社外開示には注意
- ドキュメント・スクリーンショット類の二次配布制限あり

### スコープ：JDBC Driver の対象範囲

- **対象**: CData JDBC Driver 製品群（Salesforce、Google Sheets、SAP、Snowflake、Oracle SaaS 等の 250+ コネクタ）
- **対象外**: 各データソースのネイティブ JDBC Driver（Oracle JDBC Driver、MySQL Connector/J、PostgreSQL JDBC 等）

本サンプルは常に **CData JDBC Driver を介して** データソースに接続する設計とする。ユーザーが Salesforce 以外の **CData JDBC Driver**（例: CData JDBC Driver for Google Sheets）に差し替えて利用するのは想定内のユースケース。

---

## 6. ユーザーストーリー

### US-01：パートナー SI が Salesforce 連携を試す

> **As a** パートナー SI のエンジニア,
> **I want** README に従って Salesforce JDBC 接続情報を設定し、Adapter を起動する,
> **so that** kintone アプリから Salesforce の Account データを直接閲覧・編集できる。

### US-02：CData 営業がデモ環境を立ち上げる

> **As a** CData 営業担当者,
> **I want** Docker Compose 一発で Adapter を起動する,
> **so that** 顧客との商談中に「Salesforce のデータを kintone アプリのように使える」デモを即座に見せられる。

### US-03：開発者が動作をデバッグする

> **As a** Adapter 開発者,
> **I want** curl で各 RPC を直接叩いて挙動を確認する,
> **so that** Agent を介さずに Adapter 単体の動作検証ができる。

---

## 7. 受け入れ条件

フェーズ1の完了基準：

### 機能受け入れ条件

- [ ] 必須機能 F-01 〜 F-14 が全て実装され、READMEに記載された手順で動作する
- [ ] Salesforce JDBC Driver を使った Account テーブル連携が、kintone Agent 経由で動作確認できる
- [ ] curl で 9 RPC すべてのリクエスト/レスポンスが確認できる
- [ ] FilterCondition 37種の単体テストがある（`multiple_selection_*` を除く）
- [ ] Docker イメージから 1 コマンドで Adapter が起動できる

### ドキュメント受け入れ条件

- [ ] README に以下が記載されている：
  - クイックスタート手順（30分以内に Salesforce 接続）
  - 設定ファイルの全プロパティ説明
  - 他データソース（MySQL / Google Sheets 等）への切替方法
  - トラブルシューティング
- [ ] LICENSE ファイルが Apache 2.0 で配置されている
- [ ] 設定ファイルのサンプルが Salesforce / MySQL / Google Sheets の3パターンある

### コード品質受け入れ条件

- [ ] ktlint / detekt 等の静的解析がクリーン
- [ ] 主要クラスにテストカバレッジあり（FilterTranslator は必須）
- [ ] Gradle ビルドが警告なしで通る

---

## 8. 機能要件

### FR-01: AdapterService の実装

`cybozu.data_connector.adapter.v1.AdapterService` 配下の以下 RPC を実装する：

| RPC | 要件 |
|---|---|
| GetCapability | 設定ファイルの `capability` セクションをそのまま返す |
| GetSchema | 設定ファイルの `table.columns` から FieldDefinition マップを生成して返す |
| Select | 動的 SQL を組み立て、CData JDBC で実行、Record 配列で返す |
| Insert | 各 Record を INSERT 文に変換、生成 ID を返す |
| Update | ID で WHERE 条件付き UPDATE を実行 |
| Delete | IN 句で DELETE を実行 |
| Count | WHERE 付き COUNT(*) を実行 |
| Search | LIKE 検索を実装（基本のみ） |
| Aggregate | GROUP BY + 集計関数の SQL を組み立て（基本のみ） |

### FR-02: FilterCondition の SQL 変換

protobuf 定義の 39 種 FilterCondition のうち、**37 種を必須対応**：

- `all_records`
- `record_id_*`（equal, not_equal, greater_than(_or_equal), less_than(_or_equal), in, not_in, **contains, not_contains**）
- `text_*`（equal, not_equal, in, not_in, contains, not_contains, is, is_not）
- `datetime_*`（equal, not_equal, greater_than(_or_equal), less_than(_or_equal), in_range, not_in_range）
- `number_*`（equal, not_equal, greater_than(_or_equal), less_than(_or_equal), in, not_in）
- `selection_*`（in, not_in、NULL 含む扱い）

**除外（OPTIONAL）**: `multiple_selection_in` / `multiple_selection_not_in` の2種のみ。

**除外理由**: 対応する `MultipleSelectionField` は kintone UI 側で **複数選択フィールド型が非対応**（構築マニュアルPDF 3.5節）であり、kintone Agent からこれらのフィルター条件が送信されることが想定されない。protobuf 定義上は将来用に存在するが、フェーズ1では実装してもデッドコードとなるため除外。

### FR-03: RecordIdType の両対応

- 設定ファイルで `record-id-type: NUMBER | TEXT` を選択
- NUMBER 時：`InsertResponsePayload.ids`（int64）を使用
- TEXT 時：`InsertResponsePayload.record_ids`（RecordId.value_text）を使用
- 同様に Delete/Update リクエストの両形式に対応

### FR-04: 設定ストア（SQLite 一元管理）

> **2026-09-29 更新**：当初は YAML 4 ファイル分割（`server` / `jdbc` / `table` / `capability`）
> 構成だったが、二重実装の解消のため **SQLite に一本化**した。YAML 実装と移行コマンドは削除済み。
> 経緯は [Issue #5](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/5) および
> `.steering/20260929-sqlite-only-config/` を参照。

- 保存形式：**SQLite**（`config/config.db`）
- テーブル構成：

  | テーブル | 内容 | Git管理 |
  |---|---|---|
  | `shared_jdbcs` | 共有 JDBC 接続（接続文字列・プール設定）。複数の連携から参照 | × （機密を含む。`.gitignore` 必須） |
  | `tables` | 連携ごとの設定（待ち受けポート・テーブル定義・機能宣言 + 共有 JDBC への参照） | × |
  | `schema_meta` | スキーマバージョン | × |

- 設定ディレクトリの指定：起動時引数 `--config-dir <path>`（デフォルト `./config/`）
- DB パスの指定：`--sqlite-path <path>`（デフォルト `<config-dir>/config.db`）
- 初回起動時、DB ファイルが無ければスキーマを自動生成し、連携 0 件で起動する
- 環境変数展開：接続文字列内で `${VAR_NAME}` 形式の値を上書き可能
- 設定の編集手段は **Web UI のみ**。設定ファイルを手で編集する運用は持たない

### FR-05: JDBC Driver 動的ロード

- `jdbc.yaml` の `driver-jar` で JAR パスを指定
- `Class.forName()` でドライバクラスをロード
- 起動時に1度だけ実施

### FR-06: 接続プール

- HikariCP を使用
- `jdbc.yaml` の `pool` セクションでプールサイズ・タイムアウト設定可能
- デフォルト値あり（max-pool-size: 10, connection-timeout: 30000）

### FR-07: Count 戦略の選択

データソースによっては `COUNT(*)` クエリが大量レコード時に **数十秒〜数分** かかるケースがあるため、戦略を設定可能とする：

| `count-strategy` | 挙動 | 用途 |
|---|---|---|
| `ACTUAL`（デフォルト） | `SELECT COUNT(*) FROM table WHERE ...` を実行して実件数を返す | 件数が少ない or 高速なデータソース |
| `ALWAYS_ZERO` | DB クエリせず即座に `count: 0` を返す | 大規模 Salesforce オブジェクト等、Count が極端に重いケース |

**注意**: `ALWAYS_ZERO` 時は kintone のページング UI（「全N件中M件表示」等）の表示が不正確になる可能性がある。トレードオフとしてユーザーに明示する。

`count-supported: false` の場合は `count-strategy` の指定は無視される。

### FR-08: 連携作成ウィザード

> **2026-09-29 更新**：当初は `adapter init-table` サブコマンドで提供していたが、
> SQLite 一本化に伴い Web UI の新規連携ウィザード（`/syncs/new`）に集約し、CLI は廃止した。
> 以下の対話フローは、ウィザードの 4 ステップに読み替えること。

JDBC メタデータを活用して、テーブル定義と機能宣言を対話的に構築する。

#### 入力

- `jdbc.yaml`（既存ファイル、または別途指定）

#### 対話フロー

1. **接続確立**: `jdbc.yaml` を読み、CData JDBC Driver でデータソースに接続
2. **テーブル一覧表示**: `DatabaseMetaData.getTables()` で取得したテーブル一覧をリスト表示
3. **テーブル選択**: ユーザーが対象テーブルを番号 or 名前で選択
4. **カラム情報取得**: `DatabaseMetaData.getColumns(table)` で全カラムのメタデータ取得
5. **主キー検出**: `DatabaseMetaData.getPrimaryKeys(table)` で主キー特定
6. **kintone フィールド型マッピング（対話確認）**:
   - 各カラムの JDBC 型を表示
   - 推奨 kintone 型を提示（VARCHAR→TEXT、DECIMAL→NUMBER、TIMESTAMP→DATETIME 等、`.claude/skills/kintone-external-app-spec/reference/08-jdbc-mapping.md` の対応表に準拠）
   - ユーザーが Enter で承認 or 番号入力で上書き
   - SELECTION 型を指定した場合：
     - 手動入力モード（カンマ区切り） or
     - 自動検出モード（`SELECT DISTINCT col FROM table LIMIT 100`）
7. **record-id-type の推定**: 主キーが VARCHAR/CHAR 系 → `TEXT`、BIGINT/INTEGER 系 → `NUMBER` を提示、ユーザー確認
8. **kintone field_id の入力**: 各カラムについて、kintone 側の field_id（デフォルトは JDBC カラム名の snake_case 化を提示）を確認
9. **書き出し**: 確認後、`config/table.yaml` に書き出し

#### 出力

`config/table.yaml`（既存ファイルがある場合は上書き確認）

#### 入口

Web UI `/syncs/new`（4 ステップウィザード）。CLI からの連携作成手段は提供しない。

### FR-09: 補助 CLI サブコマンド

| サブコマンド | 説明 |
|---|---|
| `web-ui` | ブラウザ管理コンソールを起動（通常運用の入口） |
| `serve` | 指定連携の Connect RPC サーバを起動（`--table` / `--tables` 必須） |
| `serve-all` | 登録済みの全連携を 1 JVM で並行起動 |
| `list-active` | 稼働中 Adapter 一覧を表示 |
| `list-tables` | 接続先データソースのテーブル一覧表示（接続検証兼ねる） |
| `test-connection` | JDBC 接続テスト + バージョン情報表示 |

共通オプション：`--config-dir`（既定 `./config`）、`--sqlite-path`（既定 `<config-dir>/config.db`）。
`list-tables` / `test-connection` は `--jdbc-name` で共有 JDBC 接続を指定する（1 件だけなら省略可）。

---

## 9. 非機能要件

### NFR-01: パフォーマンス

| 項目 | 目標 |
|---|---|
| 1リクエストあたりの応答時間（500件 Select） | **3秒以内** |
| 同時リクエスト数 | 10 並列で安定動作 |
| メモリ使用量（アイドル時） | **512MB 以下** |

### NFR-02: 信頼性

- DB 接続エラー時、Adapter プロセスは落ちずに ConnectError を返す
- SQL 例外発生時、トランザクションは適切に Rollback
- 設定ファイルの記述ミス時、起動時に明確なエラーメッセージで終了

### NFR-03: セキュリティ

- SQL インジェクション対策：すべて PreparedStatement のパラメータバインドを使用
- 接続情報の YAML 内平文記述を避けられるよう、環境変数展開をサポート
- Connect プロトコルは TLS 対応可能だが、フェーズ1は plaintext 前提（kintone Agent の仕様に合わせる）

### NFR-04: 保守性

- ソースコードはコメントなしで読み解ける構造（命名で意図を表現）
- 各 RPC 実装は1ファイル1責務（services.ts のような巨大ファイルにしない）
- FilterCondition 変換ロジックは独立したクラスに分離（テスト容易性）

### NFR-05: 互換性

- Java 17 以上で動作（Java 21 を推奨）
- Kotlin 1.9 以上
- kintone-data-connector protobuf v1 に対応（v2 は今後対応）

### NFR-06: 配布性

- Fat JAR 1ファイルで配布可能（CData JDBC Driver 以外）
- Docker イメージサイズ：300MB 以下を目標
- Apple Silicon (ARM64) / x86_64 両対応の Docker イメージ

---

## 10. 用語

詳細は [glossary.md](glossary.md) を参照。

| 用語 | 意味 |
|---|---|
| Adapter | kintone と外部データソース間のアクセス中継プログラム（本サンプルが実装するもの） |
| Connector | kintone 側のインターフェース。Agent と gRPC で通信 |
| Agent | サイボウズ提供の中継プログラム。Connector からの gRPC を Adapter への Connect RPC に変換 |
| Connect RPC | Buf 社の RPC プロトコル。gRPC + REST 両対応 |
| field_id | kintone 側のフィールド識別子（1〜128文字） |
| RecordIdType | レコード ID のデータ型（NUMBER または TEXT） |

---

## 11. 関連ドキュメント

- 機能設計：[functional-design.md](functional-design.md)
- 技術仕様：[architecture.md](architecture.md)
- リポジトリ構造：[repository-structure.md](repository-structure.md)
- 開発ガイドライン：[development-guidelines.md](development-guidelines.md)
- ユビキタス言語：[glossary.md](glossary.md)
- 対応方針：[../.steering/20260515-initial-implementation/approach-draft.md](../.steering/20260515-initial-implementation/approach-draft.md)
- kintone Adapter仕様スキル：[.claude/skills/kintone-external-app-spec/](../.claude/skills/kintone-external-app-spec/)
