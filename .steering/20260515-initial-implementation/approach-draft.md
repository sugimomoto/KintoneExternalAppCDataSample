# 対応方針（フェーズ1：CData JDBC Driver + 設定ファイル版 Adapter サンプル）

作成日: 2026-05-15
更新日: 2026-05-15（最終確定版）
ステータス: **承認済み（2026-05-15）**

承認事項追記：
- #1 実装言語: Kotlin で承認
- #8 最初のデータソース: Salesforce で承認
- #10 GitHub 公開: 一旦保留（フェーズ1完了後に再検討）

---

## 1. 本ドキュメントの位置づけ

`docs/` 配下の永続的ドキュメント、および `.steering/20260515-initial-implementation/` 配下の正式な requirements.md / design.md / tasklist.md を作成する前段として、**フェーズ1の最終方針** を整理する。

本ドキュメント承認後、以下の手順で詳細化を進める：

1. ✅ ドラフト作成
2. ✅ 仕様調査（PDF・サンプル・protobuf）
3. ✅ 仕様参照スキル作成（`kintone-external-app-spec`）
4. ✅ Salesforce JDBC Driver セットアップ + ヘルプスキル化
5. ⬜ 本方針の承認 ← **いまここ**
6. ⬜ `docs/` 永続的ドキュメント作成
7. ⬜ `.steering/` 正式作業ドキュメント作成
8. ⬜ 実装着手

---

## 2. プロジェクトの目的

kintone の「外部システムのアプリ化」機能において、**任意のデータソースを CData JDBC Driver 経由で接続可能にする Adapter サンプル実装** を提供する。

### ターゲット（フェーズ1）

- **OSS サンプルとしてインテグレーター（パートナーSI）に公開**
- 自由カスタマイズを前提とし、CData がサポート役務として価値を提供する素地を作る
- CData Connect AI 連携検証はスコープ外（後続の別検討）

### 提供価値

- CData JDBC Driver（250+ コネクタ）の中から、**設定ファイル変更だけで接続先を切り替え可能**
- 個別 DB ごとに Adapter を書き直す必要がない
- Salesforce / SAP / Oracle / Snowflake / Google Sheets / Kintone自身 等、Prisma サンプルでは対応困難な多様なデータソースに対応
- CData JDBC の **`RecordIdType = TEXT` サポート** により、Salesforce 等の文字列IDデータソースに直接連携可能（サンプルAdapter は BigInt 限定）

---

## 3. 前提となる kintone 側仕様（確定情報）

詳細は `.claude/skills/kintone-external-app-spec/` 参照。要点：

- **通信プロトコル**: Connector↔Agent は **gRPC**（双方向ストリーミング）、Agent↔Adapter は **Connect RPC（HTTP/2 上で gRPC バイナリと JSON 両対応）**
- **構造**: Connector・Agent・Adapter・テーブルは **1:1:1:1**（複数テーブル時は別ポートで起動）
- **公式提示の代替案**: 複数テーブル統合ビューを1 Adapter で参照
- **既存サンプル**: TypeScript（Node.js）+ Prisma、デフォルトポート 8083
- **kintone 側コース**: ワイドコース限定
- **API**: AdapterService の **9 RPC**（GetCapability 必須 / GetSchema 必須 / Select 等7つは任意）
- **フィールド型**: 6種（RecordId / Text / Datetime / Number / Selection / MultipleSelection）
- **フィルター条件**: 39種
- **RecordId**: NUMBER と TEXT の両方をサポート

---

## 4. フェーズ1のスコープ

### やること

- **設定ファイルベースで動作する Adapter サンプル** を実装
- CData JDBC Driver を介して任意のデータソースに接続
- 単一テーブル / 単一ポート / 単一 Agent 構成
- 4ファイル分割の `config/*.yaml` で以下を指定可能（詳細は §6 参照）：
  - `server.yaml`: リッスンポート、bind-address、plaintext モード
  - `jdbc.yaml`: JDBC Driver クラス名 / 接続文字列 / 接続プール設定
  - `capability.yaml`: サポート機能宣言、RecordIdType、count-strategy、`filterable_fields` / `sortable_fields`
  - `table.yaml`: 対象テーブル名、主キー、対象カラム（kintone field_id ↔ JDBC カラム名 ↔ kintone型）、SELECTION 型の選択肢
- AdapterService の主要 RPC を実装：
  - **必須**: GetCapability, GetSchema
  - **CRUD 基本**: Select, Insert, Update, Delete, Count
  - **任意（後回し可）**: Search, Aggregate
- 39 種の FilterCondition のうち **37 種を必須対応**（`multiple_selection_in/not_in` の2種を除外、理由はPRD FR-02参照）
- README + 動作確認手順（curl サンプル含む）を整備

### やらないこと（フェーズ2以降）

- 複数テーブル管理 UI
- 設定画面付き Web アプリ化
- 複数 Adapter / Agent プロセスのオーケストレーション
- EC2 などへのデプロイ自動化
- CData Connect AI との連携検証
- MultipleSelectionField 対応（kintone UI 側で未対応のため当面不要）
- 検索 AI（Search RPC）の高度な実装（基本 `LIKE` 検索のみ）

---

## 5. 技術選定（確定）

### 実装言語：**Kotlin + Ktor + connect-kotlin + CData JDBC Driver**

| 観点 | 評価 | 補足 |
|---|---|---|
| Connect RPC 対応 | ◎ | connect-kotlin が JVM 唯一のファーストクラス Connect 実装 |
| CData JDBC との相性 | ◎ | JVM ネイティブで JDBC API を直接呼出 |
| サーバ実装 | ◎ | Ktor は軽量・非同期ファースト、Connect 統合容易 |
| パッケージング | ◎ | Fat JAR / Docker / GraalVM Native Image 対応容易 |
| Java 相互運用 | ◎ | Kotlin コードベースに Java ライブラリ（CData JDBC）をそのまま利用可 |
| 日本SIerでの受容性 | ○ | Spring Boot Kotlin 等で広く採用され始め、JVM 知識があれば移行容易 |

### なぜ Java + Spring Boot ではなく Kotlin + Ktor か

| 項目 | Java + Spring Boot | Kotlin + Ktor |
|---|---|---|
| Connect protocol | × （grpc-java で gRPC バイナリのみ。JSON over HTTP/2 非対応 → curl テスト不可） | ○ |
| RPC スタイル | △（grpc-spring-boot-starter は2軍） | ◎ |
| 起動時間 | 遅い | 速い |
| Docker イメージサイズ | 大きい（~250MB） | 中（~150MB） |
| 学習コスト（Java→） | - | 低（数日） |

ただし、ユーザーが **Java 強い希望** がある場合は Java + grpc-java も実装可能（curl テスト機能を犠牲にする）。

### 代替案（記録のみ）

- **Node.js + node-jdbc**：サンプル踏襲だが JNI ブリッジの不安定性
- **Java + grpc-java**：Connect プロトコルが部分的にしか使えない
- **Go + connect-go**：Connect は完璧だが CData は JDBC（Java）のため ODBC ドライバ別途必要

### パッケージング方針：**Fat JAR + Docker 両対応**

- ビルド成果物：Fat JAR（`gradle shadowJar`）
- 配布形態：
  - **Fat JAR**: シンプル起動・ローカル開発・既存JVM環境への組込み
  - **Docker イメージ**: 推奨運用形態。CData JDBC ライセンスキー + JDK + 設定をまとめて配布
- CData JDBC Driver の同梱方針：**Docker イメージにはバンドル**、JAR 配布時は別途配置 + `jdbc.yaml` の `driver-jar` でパス指定

### ビルドツール：**Gradle (Kotlin DSL)**

- `build.gradle.kts` で記述
- `buf` Gradle プラグインで protobuf → Kotlin コード生成
- 依存: connect-kotlin, ktor-server-netty, hikari-cp, CData JDBC jar（ローカル参照）, kotlinx-serialization-yaml or jackson-yaml

---

## 6. 設定ファイル構成（4ファイル分割）

設定は `config/` ディレクトリ配下に **4ファイル分割** で管理する。
責務分離・Git管理ポリシーの差異化・CLI による自動生成のしやすさを目的とする。

### config/server.yaml
```yaml
port: 8083
bind-address: 127.0.0.1
plaintext: true  # TLSなし（Agent側 adapter_plaintext: true と整合）
```

### config/jdbc.yaml（機密含む → `.gitignore` 推奨）
```yaml
driver-class: cdata.jdbc.salesforce.SalesforceDriver
driver-jar: ./lib/cdata.jdbc.salesforce.jar
url: "jdbc:salesforce:User=${SF_USER};Password=${SF_PASSWORD};SecurityToken=${SF_TOKEN};"
pool:
  maximum-pool-size: 10
  connection-timeout: 30000
```

### config/capability.yaml
```yaml
select-supported: true
insert-supported: true
update-supported: true
delete-supported: true
count-supported: true
count-strategy: ACTUAL  # ACTUAL (default) | ALWAYS_ZERO
search-supported: false
aggregate-supported: false
record-id-type: TEXT  # NUMBER or TEXT
filterable-fields:
  - id
  - name
  - account_id
sortable-fields:
  - id
  - created_at
```

### config/table.yaml（**`adapter init-table` で自動生成可能**）
```yaml
name: Account  # JDBC のテーブル名
primary-key:
  kintone-field-id: id
  jdbc-column: Id
columns:
  - kintone-field-id: name
    jdbc-column: Name
    type: TEXT
  - kintone-field-id: revenue
    jdbc-column: AnnualRevenue
    type: NUMBER
  - kintone-field-id: industry
    jdbc-column: Industry
    type: SELECTION
    options: [Agriculture, Banking, Construction, Manufacturing]
  - kintone-field-id: created_at
    jdbc-column: CreatedDate
    type: DATETIME
```

### CLI による table.yaml 自動生成

```bash
# 対話式生成（jdbc.yaml の接続情報で CData JDBC メタデータをクエリ）
adapter init-table

# 非対話モード（CI/CD 用、推奨型でそのまま出力）
adapter init-table --non-interactive

# テーブル一覧表示（接続確認兼ねる）
adapter list-tables

# 接続テスト
adapter test-connection
```

設定ファイル形式：**YAML**（Spring Boot / Ktor / Kotlin で標準的、コメント対応、複雑な階層構造に向く）

---

## 7. 接続デモする最初のデータソース：**Salesforce**

| 観点 | Salesforce | Google Sheets |
|---|---|---|
| 営業デモのインパクト | ◎ | △ |
| 既にローカル準備済か | ✅ 完了 | × |
| TEXT ID 対応の検証 | ✅ 自然 | △ |
| 顧客が触れる頻度 | 高 | 中 |
| CData JDBC ヘルプ充実度 | ◎ | ◎ |

**Salesforce 一本で実装着手**。フェーズ1完了時、READMEに「他データソースへの差し替え方」を記載。Phase1完了後 PoC として Google Sheets / MySQL 等で動作確認するのは任意。

---

## 8. ライセンス：**Apache License 2.0**

| 観点 | MIT | Apache 2.0 |
|---|---|---|
| 簡潔性 | 短い | 長い |
| 特許条項 | なし | あり（明示的な特許利用許諾） |
| 派生物の改変表示義務 | なし | あり |
| エンタープライズ受容性 | ○ | ◎（明示的な特許保護） |
| 業界標準度 | OSS全般 | Apache系・Cybozu kintone関連も多い |

**Apache 2.0 を採用**：
- パートナー SI が安心してビジネス利用できる（特許保護条項）
- kintone・サイボウズ系プロジェクトでもよく採用される
- 派生物の表記義務が明確で、CData クレジット保持が自然

---

## 9. GitHub リポジトリ：**最初は限定公開 (Private)、検証後に公開**

| 段階 | 状態 | 期間目安 |
|---|---|---|
| 開発初期 | Private | フェーズ1実装中（〜数週間） |
| 内部検証完了 | Internal（CData 内＋特定パートナー招待） | 検証フェーズ |
| 正式公開 | **Public** | サイボウズデイズ等の発表タイミングに合わせる |

理由：
- 「外部システムのアプリ化」機能自体がサイボウズの未公開情報を含む（PDF 2.2 節「未公開情報につき社外開示禁止」）
- 一般公開は **サイボウズが本機能を正式リリースしたタイミング** と歩調を合わせる必要
- CData 社内でレビュー・ブラッシュアップしてから出すのが妥当

リポジトリ名（暫定）: `kintone-external-app-adapter-jdbc-sample`
Organization: `CData Software Japan`（あるいは個人 fork）

---

## 10. プロジェクト構造（暫定）

```
KintoneExternalAppCDataSample/
├ CLAUDE.md                        # Claude向けプロジェクトメモリ
├ README.md                        # ユーザー向け説明
├ LICENSE                          # Apache 2.0
├ .gitignore
├ build.gradle.kts                 # Gradle ビルド定義
├ settings.gradle.kts
├ buf.yaml                         # protobuf 依存
├ buf.gen.yaml                     # コード生成設定
├ Dockerfile
├ docker-compose.yml               # ローカル動作確認用
│
├ docs/                            # 永続的ドキュメント
│  ├ product-requirements.md
│  ├ functional-design.md
│  ├ architecture.md
│  ├ repository-structure.md
│  ├ development-guidelines.md
│  └ glossary.md
│
├ .steering/                       # 作業単位ドキュメント
│  └ 20260515-initial-implementation/
│     ├ approach-draft.md          # 本ファイル
│     ├ requirements.md
│     ├ design.md
│     └ tasklist.md
│
├ .claude/                         # Claude 向けナレッジ
│  └ skills/
│     └ kintone-external-app-spec/ # 仕様参照スキル
│
├ lib/                             # CData JDBC Driver 配置
│  ├ cdata.jdbc.salesforce.jar
│  ├ cdata.jdbc.salesforce.lic
│  └ help/                         # ヘルプドキュメント（参考用）
│
├ reference/                       # 元の参考資料（Git管理対象外推奨）
│  ├ db-connector-sample/
│  ├ kintone-data-connector/
│  └ *.pdf
│
├ src/
│  ├ main/
│  │  ├ kotlin/
│  │  │  └ com/cdata/kintone/adapter/
│  │  │     ├ Application.kt             # エントリポイント
│  │  │     ├ config/                    # YAML config 読込
│  │  │     ├ service/
│  │  │     │  └ AdapterServiceImpl.kt   # 9 RPC 実装
│  │  │     ├ jdbc/                      # JDBC 抽象化
│  │  │     │  ├ ConnectionPool.kt
│  │  │     │  ├ QueryBuilder.kt
│  │  │     │  └ TypeMapper.kt
│  │  │     ├ filter/                    # FilterCondition → SQL 変換
│  │  │     │  └ FilterTranslator.kt
│  │  │     └ proto/                     # buf generate 出力先
│  │  └ resources/
│  │     └ logback.xml
│  │
│  └ ...（config/ 配下に server.yaml / jdbc.yaml / table.yaml / capability.yaml を外部配置、詳細は §6）
│  └ test/
│     └ kotlin/
│        └ ...
│
└ scripts/
   ├ test-adapter.sh                # curl で各 RPC をテスト
   └ run-local.sh
```

---

## 11. 開発フロー（TDD ベース）

**本プロジェクトは TDD（テスト駆動開発）で進める**（Red → Green → Refactor サイクル）。
詳細は [docs/development-guidelines.md §4](../../docs/development-guidelines.md) 参照。

### 11.1 推奨実装順（純粋関数 → 副作用ありの順）

1. **基盤層: `FieldTypeSuggester`**（JDBC型 → kintone型推奨）
   - 純粋関数。TDD 入門に最適。各 java.sql.Types 値の推奨ロジックを1ケースずつ追加
2. **基盤層: `FilterTranslator`**（FilterCondition → WhereClause）
   - 37 ケースを Red → Green → Refactor で1つずつ実装
   - NULL を含む IN 句のエッジケースもテスト先行で網羅
3. **基盤層: `QueryBuilder`**（動的 SQL 組立）
   - SELECT / INSERT / UPDATE / DELETE / COUNT の各パターンをテスト先行で
4. **基盤層: `RowMapper`**（ResultSet ⇄ Record）
   - 6つの Field 型 × NULL/非NULL のマトリクスを網羅
5. **接続層: `JdbcMetadataInspector`**（DatabaseMetaData ラッパ）
   - MockK で `ResultSet` をモック化してテスト
6. **サービス層: `AdapterServiceImpl`**（9 RPC 実装）
   - 各 RPC の正常系・異常系をテスト先行
7. **CLI: `InitTableCommand` 他**
   - 対話プロセスは標準入出力をモック化してテスト
8. **統合**：Testcontainers / 実 Salesforce での E2E 検証
9. **ドキュメント整備**：README、設定例、トラブルシュート
10. **Docker 化**：Dockerfile + docker-compose で 1コマンド起動

### 11.2 マイルストーン

- **M1**: `FieldTypeSuggester` + `FilterTranslator` 完成（純粋ロジックの単体テスト 100% パス）
- **M2**: `QueryBuilder` + `RowMapper` 完成 + GetSchema/Select の PoC（Salesforce で動作）
- **M3**: CRUD（Insert/Update/Delete/Count）完成 + 統合テスト
- **M4**: CLI（`init-table` 等）完成 + 実 Salesforce での E2E 動作
- **M5**: Docker 化 + ドキュメント整備 + フェーズ1 完了

---

## 12. リスクと対応

| リスク | 影響度 | 対応 |
|---|---|---|
| CData JDBC Driver が Connect 必要機能（generated keys等）に未対応 | 中 | Salesforce の場合 INSERT 後の再 SELECT 等で代替 |
| kintone 側の仕様変更（v1.0 → v2.0） | 高 | protobuf 互換性に依存。Buf でバージョン管理 |
| 大量レコード時のメモリ消費 | 中 | limit 上限 501 件があるため OK。Stream API 使用検討 |
| Salesforce APIコールリミット | 中 | CData の `UseEphemeralIdentity` 等で対応。READMEに記載 |
| 日付タイムゾーン問題 | 中 | 全て UTC で扱う規約を CLAUDE.md と README に明記 |

---

## 13. ナレッジベース活用

| ナレッジ | 用途 |
|---|---|
| `.claude/skills/kintone-external-app-spec/` | kintone 側仕様の参照（実装中ずっと） |
| `search-cdata-help` スキル | CData JDBC Driver の API・接続プロパティ参照 |
| `setup-cdata-jdbc` スキル | 他データソース追加時の Driver 配置 |
| `reference/` 配下の原典 | 不明点があれば最終確認 |

---

## 14. 次のアクション

承認後、以下を順に実施：

1. ⬜ `docs/product-requirements.md` 作成 → 承認
2. ⬜ `docs/functional-design.md` 作成 → 承認
3. ⬜ `docs/architecture.md` 作成 → 承認
4. ⬜ `docs/repository-structure.md` 作成 → 承認
5. ⬜ `docs/development-guidelines.md` 作成 → 承認
6. ⬜ `docs/glossary.md` 作成 → 承認
7. ⬜ `.steering/20260515-initial-implementation/requirements.md` 作成 → 承認
8. ⬜ `.steering/20260515-initial-implementation/design.md` 作成 → 承認
9. ⬜ `.steering/20260515-initial-implementation/tasklist.md` 作成 → 承認
10. ⬜ 実装着手（PoC から）

---

## 15. 確定事項サマリー

| # | 項目 | 確定内容 |
|---|---|---|
| 1 | 実装言語 | **Kotlin** |
| 2 | RPCフレームワーク | **Ktor + connect-kotlin** |
| 3 | ビルドツール | **Gradle (Kotlin DSL)** |
| 4 | データソース層 | **CData JDBC Driver** |
| 5 | 接続プール | **HikariCP** |
| 6 | 設定ファイル形式 | **YAML** |
| 7 | 配布形態 | **Fat JAR + Docker** 両対応 |
| 8 | 最初のデータソース | **Salesforce** |
| 9 | ライセンス | **Apache License 2.0** |
| 10 | GitHub公開状態 | **当面 Private** → サイボウズ正式リリースに合わせて Public |
| 11 | フェーズ1スコープ | 単一テーブル・全 RPC 基本実装・Search/Aggregate は最小対応 |
