# サイボウズ社へのフィードバック

| 項目 | 内容 |
|---|---|
| 文書種別 | 検証フィードバック |
| 対象機能 | kintone「外部システムのアプリ化」（v1.0） |
| 検証実施者 | CData Software Japan |
| 検証期間 | 2026-05 |
| 検証対象 | Agent v0.9.2 + Adapter 自社実装（Kotlin + CData JDBC + Salesforce 連携） |

---

## 1. Agent の停止・再起動運用に関する課題

### 1.1 観察された事象

Adapter を再ビルド・再起動した直後に kintone でレコード一覧表示を試みると、
**「セッションが見つかりません」エラー** が発生するケースがあった。

### 1.2 原因分析

- Agent は kintone との接続を **長期的な gRPC 双方向ストリーミング** で維持しており、kintone 側でも session_id 管理されている
- Adapter 側を再起動しても Agent 自体は生存し続けるが、その間に kintone から送られた Select 等のリクエストは Agent が Adapter に転送できず失敗する
- Docker daemon が OOM などで落ちると Agent コンテナごと停止 → 復旧時に **新しい session_id で kintone に再接続** されるため、再接続前のリクエストが宙に浮く
- kintone 側のセッションタイムアウト挙動と組み合わさり、ユーザーには「セッションが見つかりません」というメッセージで見える

### 1.3 運用上の負担

開発・検証時には Adapter の再ビルドが頻繁に発生する。そのたびに：

1. Adapter 再起動 → 一時的に Agent → Adapter への gRPC 接続が失敗
2. kintone がリクエストを送るタイミング次第でユーザーがエラーに遭遇
3. Agent も再起動すると kintone セッション切れ → ユーザーが kintone 画面の再読込が必要

実運用でも、Adapter 側の障害復旧時に同様の問題が起こりうる。

### 1.4 改善案（提案）

#### A. Agent 側のリトライ・バックオフ強化

- Agent → Adapter への接続失敗時、**指数バックオフでリトライ** する
- リトライ中は Adapter 側の `Connection refused` を即時 kintone に転送せず、設定可能なタイムアウトまで保留する

#### B. ヘルスチェック連動

- Agent が gRPC の `grpc.health.v1.Health` をポーリングして Adapter の生存を監視
- Adapter が `NOT_SERVING` になっている間は kintone 側にも「メンテナンス中」相当の中間状態を返す
- 完全 `SERVING` 復活後にリクエストを再開

#### C. セッション復旧の透過性向上

- Agent コンテナを再起動した際、可能であれば **既存 session_id で再接続を試みる**（kintone 側仕様の対応が必要）
- 不可能な場合でも、エラーメッセージを「Agent 再接続中です。数秒後に再試行してください」など、ユーザーに状況が伝わる文言にする

#### D. 停止順序のドキュメント化

- 「Adapter → Agent → kintone」のチェーン依存を明示し、再起動順序を公式マニュアルに記載
- 推奨：停止時は **Agent → Adapter**、起動時は **Adapter → Agent**

### 1.5 当方の対応（ワークアラウンド）

`scripts/restart-stack.sh` で起動・停止順序を自動化し、再起動時の不整合を回避する仕組みを構築。
ただし本来は Agent 側で耐性を持っていただきたい部分。

---

## 2. その他の観察事項（参考）

### 2.1 「セッションが見つかりません」エラー文言の改善余地

- ユーザー視点では原因（Agent 再起動・Adapter 停止・ネットワーク等）が判別できない
- エラーコード `CB_IL02` の意味が公開資料からは読み取れない
- 「外部システム側との通信に失敗しました（コネクター: my-salesforce-adapter）」など、より具体的なメッセージを提案

### 2.2 `不正なリクエスト (CB_IL02 ...)` エラーの遠因

- Adapter が `GetSchema` で返した field のうち、Select レスポンスで一部の field（NULL 値）を **省略** した場合に発生
- サイボウズ提供のサンプル Adapter（TypeScript）でも同様の挙動（NULL でも field を含める）になっており、これが仕様として暗黙の前提
- 公式仕様書 / protobuf コメントで **「Select レスポンスは GetSchema で宣言した全 field を必ず含めること」** を明示すると、Adapter 実装者が躓きにくい

### 2.3 macOS 用 Agent バイナリ未提供

- `kintone-data-connector-agent_v0.9.2_*` は Linux/Windows のみ
- 開発者の多くが Mac 利用のため、Docker Linux コンテナで動かす必要があった
- macOS 版バイナリ（少なくとも開発用）の提供を要望

### 2.4 Connector・Agent・Adapter・Table が 1:1:1:1 の制約

- 複数テーブル提供時、設計・運用負担が大きい（テーブル数 × 鍵・トークン・プロセス管理）
- 同一 Adapter プロセスから複数テーブルを提供できると、運用負荷が大きく下がる
- 「複数テーブル統合ビュー → 1 Adapter」という代替案は公式提案だが、ビュー側のスキーマ設計が必要で柔軟性に欠ける

### 2.5 Agent ↔ Adapter の通信ログ

- Agent は `operation_id` と `payload_size` をログに出しており有用
- ただし **RPC メソッド名 (Select / Insert 等) と Adapter 側応答時間** もログに含むと、ボトルネック特定が容易になる

### 2.6 gRPC server reflection の Agent 側サポート

- Adapter 開発時に `grpcurl` で直接テストできることが非常に有益だった
- Agent 側にも reflection を有効にしたデバッグ用エンドポイントがあると、Agent → Adapter の通信内容を直接観察できて便利

### 2.7 `agent.json` の環境変数展開

- 現状の `agent.json` は JSON 静的ファイルでトークンを直書きする必要がある
- 設定ファイル内で `${KINTONE_AGENT_TOKEN}` のような環境変数展開ができると、シークレット管理（Docker secrets, K8s secrets, .env 等）に統合しやすい

### 2.8 OAuth トークンキャッシュの永続化（Adapter 側考慮）

- Adapter 側で CData JDBC Driver を OAuth で接続する場合、初回はブラウザ認証が必要
- Docker 化する際、`OAuthSettingsLocation` を永続ボリュームに置く工夫が必要
- 本機能とは直接関係ないが、Adapter 開発者向けノウハウとしてマニュアルに記載があると親切

### 2.9 `GetCapability` の `filterable_fields` / `sortable_fields`

- 公式 protobuf に存在するが、構築マニュアルでの説明が少ない
- 特定フィールドのみフィルター対象にしたい場合のユースケースとセットで記載されると、設計判断しやすい

### 2.10 Aggregate / Search RPC の実装ガイダンス

- protobuf スキーマには定義があるが、サンプル Adapter では未実装
- 「最低限のフォールバック実装」「PostgreSQL/MySQL 以外のデータソースでの実装パターン」等のガイドがあると、開発開始時の心理的ハードルが下がる

---

## 3. CData として可能な貢献の検討事項

本検証は CData JDBC Driver と組み合わせた Adapter 実装の PoC として実施した。
本検証で得られた知見：

- **CData JDBC Driver で 250+ のデータソース（Salesforce/SAP/Snowflake/Google Sheets 等）に統一 IF で接続可能**
- Prisma ベースの公式サンプルでは対応が難しい SaaS 系データソースに、設定変更のみで対応できる
- パートナー SI が個別実装する負担を軽減できる

サイボウズ・CData 両社で連携した提供形態として：

- **CData がリファレンス Adapter 実装を OSS で公開**（フェーズ1 で実装済み）
- パートナーは CData JDBC Driver と本サンプルをセットで導入することで、エンタープライズデータソース連携を短期で実現

これにより、サイボウズの「外部システムのアプリ化」機能の実用可能なエコシステムを広げられる可能性があると考えている。

---

## 4. まとめ

| 優先度 | 項目 | 影響 |
|---|---|---|
| 🔴 高 | Agent 側のリトライ・ヘルスチェック連動（§1.4） | 運用安定性・ユーザー体験 |
| 🔴 高 | エラー文言の改善（§2.1） | サポート負荷削減 |
| 🟡 中 | macOS 版 Agent（§2.3） | 開発者体験 |
| 🟡 中 | Select レスポンス仕様明文化（§2.2） | Adapter 実装者の負担軽減 |
| 🟡 中 | `agent.json` 環境変数展開（§2.7） | DevOps 連携 |
| 🟢 低 | 複数テーブル対応（§2.4） | 中長期の拡張性 |
| 🟢 低 | Aggregate/Search ガイダンス（§2.10） | 完成度 |

ご検討よろしくお願いいたします。
