# フェーズ2-A: 複数テーブル・複数ドライバー運用支援の要求内容

| 項目 | 内容 |
|---|---|
| 作業タイトル | multi-table-support（フェーズ2-A） |
| 開始日 | 2026-05-22 |
| ステータス | ドラフト（承認待ち） |
| 親文書 | [docs/product-requirements.md](../../docs/product-requirements.md) |
| 前提 | [v0.1.0-phase1](../20260515-initial-implementation/) 完了 |

---

## 1. 本作業の概要

kintone「外部システムのアプリ化」機能の **1 Connector = 1 Agent = 1 Adapter = 1 Table** という構造制約のもと、**複数テーブル（複数の外部連携アプリ）を1サーバで運用** できる基盤を整備する。

加えて、**複数の CData JDBC Driver を同一サーバ上で混在運用** できる構造にする（例: Salesforce の Account/Contact と Google Sheets の Spreadsheet を同時提供）。

フェーズ1 は単一テーブル PoC だったが、実案件では複数テーブル・複数データソース連携が前提となるため、これを支援する仕組みを作る。

## 2. 本作業のゴール

フェーズ2-A 完了時、以下が達成されていること：

- ✅ 1 サーバ上で複数の Adapter プロセスが別ポートで並列稼働する
- ✅ 各 Adapter に対応する Agent コンテナを Docker Compose で一括管理できる
- ✅ 設定追加（新規テーブル）でプロセスを動的に立ち上げられる
- ✅ 1 つの Adapter を再起動しても他テーブルの運用に影響しない
- ✅ Salesforce Account + Salesforce Contact + Salesforce Opportunity 等の **複数テーブル同時動作** を実 kintone で E2E 確認できる
- ✅ **複数の CData JDBC Driver の混在運用** が可能（Salesforce + Google Sheets 等）
- ✅ 設定ファイルの重複を最小化（共通 `jdbc.yaml` をデータソース毎に共有、テーブル毎にも個別保持可能）
- ✅ **鍵ペアは全 Connector で使い回し可能**、トークンのみテーブル毎に保持

---

## 3. 機能要求

### 3.1 必須機能（MUST）

| ID | 機能 | 説明 |
|---|---|---|
| MT-01 | 複数 Adapter 同時起動 | 1 設定ディレクトリから複数 Adapter インスタンスを別ポートで起動 |
| MT-02 | 設定ファイル分割の階層化 | テーブル毎の設定を `config/tables/<name>/` 配下に整理 |
| MT-03 | 共通 jdbc 参照（オプション） | `config/jdbc/<datasource>.yaml` を複数テーブルで共有可能 |
| MT-04 | テーブル別 jdbc 個別保持 | テーブル個別の jdbc.yaml も書ける（複数ドライバー混在のため） |
| MT-05 | テーブル別 capability | 各テーブルで `capability.yaml` を独立に持つ |
| MT-06 | 一括起動コマンド | `adapter serve-all` で全テーブルを起動 |
| MT-07 | 個別管理コマンド | `adapter serve --table <name>` で1テーブルのみ |
| MT-08 | ポート自動採番 | 設定で port が省略された場合は 8083 から連番で採番 |
| MT-09 | 複数 Agent コンテナ管理 | docker-compose.yml で複数 Agent サービスを定義 |
| MT-10 | グレースフル停止 | 1 Adapter 停止時、他に影響しない |
| MT-11 | ヘルスチェック統合 | 全 Adapter の状態を一括取得 |
| MT-12 | 鍵ペア共有・トークン個別管理 | private-key.pem は全 Agent で共有、token のみ Connector 毎 |
| MT-13 | OAuth 設定の分離 | 複数ドライバー混在時、各々の `OAuthSettingsLocation` を別パスに |
| MT-14 | `ConfigSource` 抽象化 | 設定の永続化レイヤをインターフェース分離し、後続フェーズの SQLite 等への切替を容易にする |

### 3.2 望ましい機能（SHOULD）

| ID | 機能 | 説明 |
|---|---|---|
| MT-21 | プロセス監視 | 1 Adapter プロセスがクラッシュした場合の自動再起動 |
| MT-22 | リスト表示 | `adapter list-active` で稼働中の全テーブル状態を一覧表示 |
| MT-23 | 起動時の検証 | 全設定ファイルの妥当性チェック後に起動 |
| MT-24 | ログの分離 | テーブル毎に独立したログ出力 |

### 3.3 除外事項（WON'T、後続フェーズ）

| ID | 内容 | 理由・予定フェーズ |
|---|---|---|
| MT-91 | Web UI による設定 | フェーズ2-B のスコープ |
| MT-92 | EC2/AWS デプロイ自動化 | フェーズ2-C のスコープ |
| MT-93 | ホットリロード | 設定変更時は再起動運用を前提 |
| MT-94 | Search / Aggregate の本格実装 | フェーズ2-D（別タスク） |
| MT-95 | **設定永続化を SQLite 等の RDB に移行** | **フェーズ2-B（Web UI）と同時に導入予定**。本フェーズでは MT-14 の抽象化のみ実施し、実装は YAML ベース継続 |

---

## 4. ユーザーストーリー

### US-01：パートナー SI が3テーブル構成を構築する

> **As a** パートナー SI のエンジニア,
> **I want** Salesforce の Account / Contact / Opportunity の3テーブルを kintone 外部連携アプリとして同時提供する,
> **so that** 顧客の SFA 業務を kintone 上で完結させられる。

### US-02：運用担当者が個別テーブルを再起動する

> **As a** 運用担当者,
> **I want** 1 テーブルだけの設定を変更して、そのテーブルの Adapter のみ再起動する,
> **so that** 他テーブルの運用を止めずに変更を反映できる。

### US-03：開発者が新テーブルを段階的に追加する

> **As a** 開発者,
> **I want** 既存稼働中の Adapter 群を止めずに、新テーブル用の設定を追加して新規 Adapter を立ち上げる,
> **so that** 段階的にデータソースを拡張できる。

### US-04：運用担当者が全体ヘルスを確認する

> **As a** 運用担当者,
> **I want** 1 コマンドで全 Adapter の稼働状態を確認する,
> **so that** 問題が起きているテーブルを素早く特定できる。

---

## 5. 受け入れ条件

### 5.1 機能受け入れ条件

- [ ] `config/tables/account/` と `config/tables/contact/` の2セット定義で、port 8083 と 8084 で同時起動する
- [ ] `adapter serve-all` で全テーブルを一括起動できる
- [ ] `adapter serve --table account` で account だけ起動できる
- [ ] `docker compose -f agent/docker-compose.yml up -d` で複数 Agent コンテナが起動する
- [ ] kintone で2つの外部連携アプリを作成し、それぞれ独立に動作する
- [ ] account 側 Adapter を停止しても contact 側は動作継続する
- [ ] `adapter list-active` で稼働中の全テーブル状態が表示される

### 5.2 設定ファイル受け入れ条件

- [ ] `config/jdbc.yaml`（共通）を 2 テーブルで参照する構成が動作する
- [ ] テーブル毎に独立した `capability.yaml` を持てる
- [ ] テーブル毎に独立した `table.yaml` を持てる
- [ ] `port` 省略時に自動採番される

### 5.3 品質受け入れ条件（TDD ベース）

- [ ] `ConfigLoader` の階層構造対応の単体テスト
- [ ] `MultiAdapterRunner` 相当の単体テスト
- [ ] 既存テスト121件は引き続き全て緑（後方互換性維持）
- [ ] `./gradlew test ktlintCheck detekt jacocoTestReport` がパス

### 5.4 ドキュメント受け入れ条件

- [ ] README に複数テーブル構成のセットアップ手順
- [ ] `config/tables/<table>/*.yaml.example` で構成例提供
- [ ] `agent/docker-compose.yml` の複数 Agent サービス例
- [ ] フェーズ1 単一テーブル構成からの移行手順

---

## 6. 制約事項

### 6.1 kintone 仕様制約（不変）

- 1 Connector ↔ 1 Agent ↔ 1 Adapter ↔ 1 Table は変えられない
- 各 Adapter は別ポート必須
- 各 Agent は **個別 token が必要**（Connector 毎に kintone が発行）
- private-key.pem は **全 Connector で共有可能**（kintone コネクター登録時に同じ公開鍵を貼り付けてよい）

> 💡 鍵ペアは1つ用意して使い回し、token のみ Connector 毎に管理するのが運用上シンプル。

### 6.2 技術制約

- 言語：Kotlin（変更なし）
- フェーズ1 で構築したコア層（FilterTranslator/QueryBuilder/RowMapper 等）は **再利用**
- `AdapterServiceImpl` は **1 テーブル単位** で生成し、テーブル毎にインスタンス化
- gRPC サーバは **テーブル毎に別ポートで別 Server インスタンス**

### 6.3 プロセスモデル

選択肢を要検討：

| 案 | 内容 | 長所 | 短所 |
|---|---|---|---|
| A. 1 プロセス内マルチサーバ | 1 JVM 内で複数 gRPC Server を別ポートで起動 | リソース効率良・運用シンプル | クラッシュ時に全停止リスク |
| B. テーブル毎に独立プロセス | テーブル毎に別 JVM | クラッシュ独立・systemd で個別管理 | リソース重複・運用やや複雑 |
| C. プロセスマネージャ常駐 | 親プロセスが子 Adapter プロセスを管理 | A+B の中間 | 実装複雑 |

→ 設計フェーズで決定（design.md で扱う）。

### 6.4 スコープ制約

- 引き続き **CData JDBC Driver 限定**（ネイティブ Oracle JDBC 等は対象外）
- データソースは **Salesforce（複数オブジェクト）+ CData Google Sheets Driver（軽い動作確認）** で複数ドライバー混在動作検証
- フェーズ2-B（Web UI）, フェーズ2-C（デプロイ自動化）は別作業
- 本格的な Google Sheets PoC はフェーズ2-D（他データソース PoC）で実施

---

## 7. 検証シナリオ（実 Salesforce + 実 kintone）

| シナリオ | 操作 | 期待結果 |
|---|---|---|
| S-01 | `config/tables/account/` + `config/tables/contact/` 設定 | `adapter serve-all` 起動成功、port 8083 + 8084 |
| S-02 | kintone でアプリ2つ作成 (Account 用、Contact 用) | 各々独立に外部連携アプリが作れる |
| S-03 | Account 一覧表示 | Adapter A (port 8083) 経由で表示 |
| S-04 | Contact 一覧表示 | Adapter B (port 8084) 経由で表示 |
| S-05 | Account Adapter のみ再起動 | Contact 側は影響なし |
| S-06 | 新規テーブル Opportunity 追加 → 起動 | 既存 2 テーブルに影響なく追加できる |
| S-07 | `adapter list-active` | 3 つのテーブル状態が表示される |
| S-08 | Account でレコード追加・編集・削除 | Contact 側には影響なく動作 |
| S-09 | Account の Adapter プロセス crash → 自動復旧（MT-21 実装時） | 自動再起動される |
| S-10 | 全 Adapter のヘルスチェック | 全テーブルの SERVING 状態確認 |
| S-11 | **複数ドライバー混在**: Salesforce Account + CData Google Sheets で各々起動 | 別ポート・別 OAuth キャッシュで併存動作 |
| S-12 | 鍵ペア共有確認: 全 Connector で同じ公開鍵を登録 → 別 token で接続 | 同じ private-key.pem で複数 Agent が動作 |

---

## 8. 想定外と見なすもの

以下が確認できなくてもフェーズ2-A は完了とする：

- **大規模並列**: 10 テーブル以上の同時稼働（PoC では 3 テーブルで十分）
- **マルチデータソース**: Salesforce + Google Sheets の混在運用は別作業
- **Web UI**: GUI からの設定はフェーズ2-B
- **本番デプロイ**: EC2 自動構築はフェーズ2-C
- **ホットリロード**: 設定変更時は再起動運用

---

## 9. リスクと前提検証

| # | リスク | 対応 |
|---|---|---|
| ~~R-01~~ | ~~同一 private-key で複数 Connector が登録できるか不明~~ | **解消：使い回し可能と確認済み** |
| R-02 | 1 プロセス内マルチ gRPC Server がリソース競合しないか | design.md で詳細検討（プロセスモデル選定） |
| R-03 | Agent コンテナを n 個立ち上げると Docker メモリ圧迫 | 軽量代替（OrbStack 等）の検討、Agent バイナリ単体起動の評価 |
| R-04 | 既存 121 テストの後方互換性 | リファクタリング時に注意、テスト先行 |
| R-05 | OAuth トークンキャッシュの競合 | データソース毎に別 `OAuthSettingsLocation` を強制する設計（MT-13） |
| R-06 | 複数ドライバー混在時の lib/ クラスローダー競合 | URLClassLoader を Driver 毎に分離（フェーズ1 既存実装で対応可能性大） |
| R-07 | Google Sheets Driver の初回 OAuth フローでブラウザ起動 | サーバ運用では OAuth 設定を事前生成して配置する手順を文書化 |

---

## 10. 関連ドキュメント

- フェーズ1 対応方針：[../20260515-initial-implementation/approach-draft.md](../20260515-initial-implementation/approach-draft.md)
- 永続的ドキュメント：[../../docs/](../../docs/)
- 設計：[design.md](design.md)（次に作成）
- タスクリスト：[tasklist.md](tasklist.md)（design 承認後に作成）

---

## 11. 将来の進化方向（記録）

本フェーズで決定した方針のうち、**後続フェーズで変更予定の項目** を明示する。

### 11.1 設定永続化レイヤの SQLite 移行

- **方針**: フェーズ2-A では YAML 継続、フェーズ2-B（Web UI）と同時に SQLite へ移行
- **理由**:
  - フェーズ2-A は**マルチテーブル基盤の構築**に集中（設定永続化の刷新は別案件）
  - SQLite の本来の旨味は **GUI 編集と連動した CRUD・トランザクション・履歴** にあり、GUI フェーズで導入が自然
  - 一方で **GitOps ワークフローの選択肢**（YAML を git で管理）も残したいため、Hybrid 検討の余地もあり
- **準備**: 本フェーズで `ConfigSource` インターフェース（MT-14）を導入し、`YamlConfigSource` を実装。フェーズ2-B で `SqliteConfigSource` を追加するだけで切替可能にする
- **想定移行スコープ**:
  - 接続情報（jdbc.yaml 相当）
  - テーブル定義（table.yaml 相当）
  - capability 設定（capability.yaml 相当）
  - kintone token / Agent 関連情報
  - 変更履歴・監査ログ（SQLite 導入で初めて実現）
- **検討事項**:
  - YAML との Hybrid 構成（正規ソース=YAML、ランタイム=SQLite）が必要か
  - エクスポート / インポート機能（YAML ⇄ SQLite 相互変換）の要否
  - パートナー SI が顧客環境に **git 管理で** デプロイする想定が強ければ YAML 残置を継続検討

### 11.2 その他の中長期検討事項

- **設定ホットリロード**（MT-93）: フェーズ2-B 以降、SQLite と組み合わせて検討
- **メトリクス / 監視**: フェーズ2-C（デプロイパッケージ）に含める可能性
- **kintone token のシークレット管理**: AWS Secrets Manager / Vault 連携はフェーズ2-C で検討
