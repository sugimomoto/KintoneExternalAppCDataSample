# 初回実装の要求内容（フェーズ1）

| 項目 | 内容 |
|---|---|
| 作業タイトル | initial-implementation |
| 開始日 | 2026-05-15 |
| ステータス | ドラフト（承認待ち） |
| 親文書 | [docs/product-requirements.md](../../docs/product-requirements.md) |

---

## 1. 本作業の概要

CData JDBC Driver を介して Salesforce Account テーブルに kintone から接続する **Adapter のリファレンス実装** を初回リリースとして構築する。

本作業は OSS サンプルプロジェクトの **新規ゼロベース実装** であり、`docs/` 配下の永続的ドキュメントに沿った最初のコード化となる。

## 2. 本作業のゴール

フェーズ1完了時、以下が達成されていること：

- ✅ kintone Agent から Connect RPC で接続可能な Adapter プロセスが起動する
- ✅ Salesforce Account テーブルに対して **読み書き両方** を動作確認できる
- ✅ 設定ファイル4分割の構成で運用できる
- ✅ `adapter init-table` で対話的に `table.yaml` を生成できる
- ✅ curl で全 9 RPC をローカル検証できる
- ✅ Docker イメージ1コマンドで起動できる
- ✅ FilterTranslator の単体テストが 37 ケース全て通る
- ✅ README に従ってパートナー SI が **30 分以内** に Salesforce 連携を試せる

---

## 3. 機能要求（本イテレーション）

詳細は [docs/product-requirements.md](../../docs/product-requirements.md) を参照。本作業で実装する範囲：

### 3.1 必須機能（PRD F-01 〜 F-16 すべて）

| 範囲 | 内容 |
|---|---|
| Adapter プロセス | 起動・終了、`serve` サブコマンド |
| 設定読込 | 4ファイル分割（server / jdbc / table / capability）の YAML 読込・環境変数展開 |
| Connect RPC サーバ | Ktor + connect-kotlin で HTTP/2 待受 |
| 9 RPC 実装 | GetCapability / GetSchema / Select / Insert / Update / Delete / Count / Search / Aggregate |
| FilterCondition 変換 | **37/39 種** 必須対応（`multiple_selection_*` 除く） |
| RecordIdType | NUMBER / TEXT 両対応 |
| Count 戦略 | ACTUAL / ALWAYS_ZERO の設定切替 |
| JDBC 接続 | HikariCP + 動的 JAR ロード |
| CLI サブコマンド | `serve` / `init-table` / `list-tables` / `test-connection` |
| Docker | Dockerfile + docker-compose.yml |

### 3.2 望ましい機能（PRD F-21 〜 F-25）

時間が許せばフェーズ1 で着手、不足する場合はフェーズ2 へ：
- Search（`LIKE` ベースの簡易実装）
- Aggregate（基本的な COUNT / SUM / AVG / MAX / MIN と日時グルーピング）
- `/health` エンドポイント

### 3.3 除外事項（フェーズ2 以降、F-91 〜 F-95）

明示的に **やらない**：
- 複数テーブル対応 UI
- 設定 GUI
- kintone Connect AI 連携
- MultipleSelectionField 対応
- 自動デプロイ

---

## 4. ユーザーストーリー（本イテレーション）

PRD §6 のうち、フェーズ1 で検証対象とする3本：

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

## 5. 受け入れ条件

### 5.1 機能受け入れ条件

- [ ] `./gradlew shadowJar` でビルドが成功する
- [ ] `java -jar build/libs/adapter-all.jar serve` で Adapter が起動する
- [ ] `curl ... /GetCapability` で正しい応答が返る
- [ ] `curl ... /GetSchema` で Salesforce Account のスキーマが返る
- [ ] `curl ... /Select` で Salesforce のレコードが返る
- [ ] `curl ... /Insert` で Salesforce にレコードが作成され、生成された TEXT ID が返る
- [ ] `curl ... /Update` で Salesforce のレコードが更新される
- [ ] `curl ... /Delete` で Salesforce のレコードが削除される
- [ ] `curl ... /Count` で件数が返る（または `ALWAYS_ZERO` 設定時は 0）
- [ ] **kintone Agent 経由で kintone アプリから Salesforce データを操作できる**（E2E 検証）
- [ ] `adapter init-table` 対話で Salesforce Account の `table.yaml` を自動生成できる
- [ ] `docker compose up` で Adapter が起動する

### 5.2 品質受け入れ条件（TDD ベース）

- [ ] `FieldTypeSuggester` の単体テストが 100% パス
- [ ] `FilterTranslator` の **37 ケース全て** の単体テストが 100% パス
- [ ] `QueryBuilder` / `RowMapper` の主要パターン単体テストが 100% パス
- [ ] **テストが先にコミットされている**ことが git log で確認できる
- [ ] `./gradlew test ktlintCheck detekt jacocoTestReport` がパス
- [ ] テストカバレッジ 80% 以上

### 5.3 ドキュメント受け入れ条件

- [ ] README に以下が記載：
  - クイックスタート（30 分以内に Salesforce 接続）
  - 設定ファイル4種の全プロパティ説明
  - CLI サブコマンド一覧
  - curl による動作確認手順
  - 他データソース（CData JDBC Google Sheets 等）への切替方法
  - トラブルシュート
- [ ] LICENSE ファイル（Apache 2.0）配置済み
- [ ] `config/*.example` で Salesforce 用の設定例提供

---

## 6. 制約事項

### 6.1 技術制約

- **言語**: Kotlin（決定済み）
- **フレームワーク**: Ktor + connect-kotlin
- **ビルド**: Gradle (Kotlin DSL)
- **JDBC**: CData JDBC Driver のみ（ネイティブ JDBC は対象外）
- **接続プール**: HikariCP
- **CLI**: Clikt
- **設定形式**: YAML（kaml）
- **Java**: 17 以上、推奨 21
- 詳細は [docs/architecture.md §1](../../docs/architecture.md) 参照

### 6.2 プロセス制約

- **TDD で開発する**（Red → Green → Refactor）
  - すべてのプロダクションコードは対応するテストが先にコミットされる
  - 詳細は [docs/development-guidelines.md §4](../../docs/development-guidelines.md)
- **1ファイル/1機能の段階的な進め方**（推奨実装順は対応方針 §11.1）
- **GitHub は当面 Private**

### 6.3 スコープ制約

- 最初に対応するデータソースは **Salesforce のみ**
- 動作確認テーブルは **Account 1テーブル**
- 主キー型は **TEXT（Salesforce ID 18 文字）** で確定
- 単一テーブル / 単一ポート / 単一 Agent 構成（kintone 仕様制約）

### 6.4 サイボウズ機能仕様制約（非変更）

- 1 ドメインあたり最大 20 コネクター
- ワイドコース限定
- 対応 / 非対応の kintone フィールド型は構築マニュアル §3.5 に従う
- 詳細は [.claude/skills/kintone-external-app-spec/reference/05-constraints.md](../../.claude/skills/kintone-external-app-spec/reference/05-constraints.md) 参照

---

## 7. 検証シナリオ（Salesforce Account でのE2Eシナリオ）

フェーズ1 完了時に手動で確認するシナリオ：

| シナリオ | 操作 | 期待結果 |
|---|---|---|
| S-01 | kintone アプリストア → 「外部システムに接続して作成」→ Salesforce 接続を選択 | Salesforce Account のスキーマが kintone に表示される |
| S-02 | kintone アプリのレコード一覧画面を開く | Salesforce Account のレコードが表示される |
| S-03 | kintone アプリで新規レコード追加 | Salesforce に新規 Account が作成される |
| S-04 | kintone アプリでレコードを編集して保存 | Salesforce 側で対応レコードが更新される |
| S-05 | kintone アプリでレコードを削除 | Salesforce 側で対応レコードが削除される |
| S-06 | kintone でフィルター（業種=Banking）をかける | Salesforce 側で WHERE 句が発行され、絞り込み結果が返る |
| S-07 | kintone でソート（年商の降順） | Salesforce 側で ORDER BY が発行される |
| S-08 | レコード一覧のページング操作 | OFFSET / LIMIT で正しく動作する |
| S-09 | （Count = ALWAYS_ZERO 設定時）件数 | 0 件と表示されるが Select は動作する |
| S-10 | データソース切替テスト：jdbc.yaml の Salesforce → 別の CData JDBC（例: Google Sheets）に変更 | プロセス再起動後、別データソースで動作する |

---

## 8. 想定外と見なすもの

以下が確認できなくてもフェーズ1 は完了とする：

- **大規模オブジェクト（1000万件以上）での性能** — 想定範囲外。ベンチマーク不要
- **エラーケース網羅** — 主要例外パスのみ。マニアックな失敗ケースは Issue 化のみ
- **Search の高度な検索** — `LIKE` 検索ベースのみ。SOSL 等のデータソース固有検索は未対応
- **Aggregate の四半期/週グルーピング** — protobuf に存在するが未実装で OK
- **複合主キー** — 単一カラム主キーのみ
- **ネイティブ JDBC との比較ベンチ** — 不要

---

## 9. 関連ドキュメント

- 永続的ドキュメント：[docs/](../../docs/)
- 対応方針：[approach-draft.md](approach-draft.md)
- 設計：[design.md](design.md)（次に作成予定）
- タスクリスト：[tasklist.md](tasklist.md)（次に作成予定）
- kintone Adapter 仕様：[.claude/skills/kintone-external-app-spec/](../../.claude/skills/kintone-external-app-spec/)
