# 01. アーキテクチャ

## 全体構成

```
┌─────────────┐         gRPC          ┌─────────┐    Connect RPC   ┌──────────┐    ※     ┌──────────┐
│ kintone     │ ◄──────────────────► │ Agent   │ ◄──────────────► │ Adapter  │ ◄──────► │ Data     │
│ (Connector) │  双方向ストリーミング │         │  (HTTP/2 + JSON  │          │  各DBの   │ Source   │
└─────────────┘                       └─────────┘   または binary) └──────────┘  ﾌﾟﾛﾄｺﾙ  └──────────┘

  kintone環境                        外部システム環境（顧客／パートナー側）
```

## 3つのモジュール

### Connector（コネクター）
- **提供元**: サイボウズ（kintone組み込み）
- **役割**: kintone アプリの操作イベントを検知し、Agent に送信
- **設定**: kintone システム管理画面から行う
- **認証**: 公開鍵を kintone に登録 → トークン発行

### Agent（エージェント）
- **提供元**: サイボウズ（バイナリ配布、カスタマイズ不要）
- **配布物**: `kintone-data-connector-agent_v0.9.2_{os}_{arch}.{tar.gz|zip}`
- **対応 OS/CPU**: Windows / Linux / macOS（macOSは開発用） × x86_64 / ARM64
- **役割**: kintone Connector からの gRPC リクエストを受け取り、Adapter に Connect RPC 経由でリクエスト
- **設定ファイル**: `agent.json`
  ```json
  {
    "token": "<kintone発行のJWTトークン>",
    "adapter_addr": "localhost:8083",
    "adapter_plaintext": true,
    "private_key_path": "private-key.pem"
  }
  ```
- **起動**: `./kintone-data-connector-agent --config agent.json`
- **認証**: RSA 2048 秘密鍵で署名（公開鍵は kintone 側に登録済み）

### Adapter（アダプター）
- **提供元**: 顧客／パートナー実装（サンプルあり）
- **役割**: Agent からの Connect RPC リクエストを受け、データソースとやり取り
- **プロトコル**: **Connect RPC**（HTTP/2 上で gRPC バイナリと JSON の両方をサポート）
- **デフォルトポート**: 8083（サンプルは ADAPTER_PORT 環境変数で指定）

## プロトコル詳細

### Connector ↔ Agent: gRPC

- 双方向ストリーミング
- Agent 側からの **アウトバウンド接続のみ** で kintone に繋がる（Agent側でインバウンドポートを開ける必要がない）
- セキュリティ/メンテナンス上のメリット
- 認証：Agent が秘密鍵で署名 → kintone が公開鍵で検証 + トークン

### Agent ↔ Adapter: Connect RPC

- **Connect プロトコル** = gRPC + REST 両対応の `@connectrpc` 系プロトコル
- HTTP/2 上で動作
- リクエスト形式：
  - **gRPC バイナリ** (Content-Type: `application/grpc`)
  - **gRPC-Web** (`application/grpc-web`)
  - **JSON over HTTP/2** (`application/json`) ← curl でアクセス可
- TLS あり / なしの両方対応：
  - 本番：HTTPS + 証明書
  - サンプル：TLS なし → `agent.json` で `"adapter_plaintext": true`
- エンドポイント URL 形式：
  ```
  http://{adapter_host}:{adapter_port}/cybozu.data_connector.adapter.v1.AdapterService/{MethodName}
  ```
  例: `http://localhost:8083/cybozu.data_connector.adapter.v1.AdapterService/GetCapability`

## ポート一覧

| 接続元 → 接続先 | プロトコル | ポート | 備考 |
|---|---|---|---|
| Connector → kintone | gRPC over HTTPS | 443 | kintone外向き |
| Agent → kintone | HTTPS | 443 | アウトバウンド |
| Agent → Adapter | HTTPS/HTTP (Connect RPC) | 443 / 任意 | サンプルは平文 |
| Adapter → 外部DB | DB毎 | DB毎 | JDBC接続文字列で指定 |
| Adapter → インターネット | HTTPS | 443 | 依存ライブラリインストール時のみ |

## 認証フロー

```
1. Adapter 配置ディレクトリで openssl で鍵ペア生成
   $ openssl genrsa 2048 > private-key.pem
   $ openssl rsa -pubout < private-key.pem > public-key.pem

2. kintone 管理画面の「外部システムコネクター管理」で
   コネクター追加 → 公開鍵を貼り付け → 「追加する」
   → JWT トークンが発行される（一度しか表示されないので控える）

3. agent.json に token と private_key_path を記載

4. Agent 起動 → kintone に接続成功するとセッション確立
   ログ: {"msg":"successfully connected to kintone","session_id":"..."}

5. kintone 管理画面で「接続する」ボタン押下
   → Agent 経由で Adapter の GetCapability が呼ばれる
   → ステータスが「利用可能」になる

6. kintone アプリストアで「外部システムに接続して作成」を選択
   → コネクター選択 → GetSchema 呼出 → カラムとフィールド型を割当
```

## 1:1:1:1 の制約

- **1つの Connector** ↔ **1つの Agent** ↔ **1つの Adapter** ↔ **1つのテーブル**
- 複数テーブルを扱うには、各々別ポートで Adapter / Agent / Connector のセットを用意
- 代替案：データソース側でビューを作って 1 Adapter に集約

## 制限

- 1 ドメインあたり **最大 20 コネクター**
- 1 コネクターに対する外部連携アプリ数は無制限（通常のアプリ数としてはカウントされる）
