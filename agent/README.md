# kintone Agent Docker

`kintone-data-connector-agent` を Docker コンテナで起動するための構成。
macOS など Agent バイナリが直接実行できないホストでも、Linux コンテナ経由で動作させられる。

> **注意**: `kintone-data-connector-agent` のプログラム本体はサイボウズ社から
> 個別に受領するバイナリです。公開レジストリでは配布されていません。
> 受領した tar を `bin/linux_<arch>/` に展開してから本ドキュメントの手順を進めてください。
>
> 通常運用では本ファイルではなく adapter-console を介した Web UI から
> Agent コンテナが自動制御されます。本ファイルは Agent 単体起動 (開発・デバッグ用) の手順です。

## 構成

```
agent/
├ Dockerfile             # Debian slim ベースの Linux コンテナ
├ bin/
│  ├ linux_amd64/kintone-data-connector-agent  # x86_64 用 (サイボウズ受領, .gitignore)
│  └ linux_arm64/kintone-data-connector-agent  # arm64 用 (サイボウズ受領, .gitignore)
├ agent.json.example     # 設定例（.gitignore 済みの実体: agent.json）
├ private-key.pem        # 秘密鍵（.gitignore 済み）
├ public-key.pem         # 公開鍵 (kintone へ登録)
└ README.md              # このファイル
```

## セットアップ手順

### 1. 鍵ペア生成

**通常は adapter-console の Web UI から生成します** (`/syncs/{name}/connect` の Step 1 で
「🔑 鍵ペアを生成する」ボタン)。CLI で生成したい場合のみ以下:

```bash
cd agent
openssl genrsa 2048 > private-key.pem
openssl rsa -pubout -in private-key.pem -out public-key.pem
cat public-key.pem  # この内容を kintone に貼り付け
```

### 2. kintone でコネクター登録

1. kintone システム管理 → 「外部システムコネクター管理」
2. 「+ コネクターを追加」
3. 公開鍵を貼り付け → 「追加する」
4. 表示された **JWT トークンをコピー**（1度しか表示されない）

### 3. agent.json の作成

```bash
cd agent
cp agent.json.example agent.json
# agent.json を編集してトークンを設定
```

`agent.json` の主要項目：

| 項目 | 値 | 説明 |
|---|---|---|
| `token` | kintone 発行 JWT | コネクター登録時に発行 |
| `adapter_addr` | `host.docker.internal:8083` | Mac/Windows: host の Adapter へ。Linux ホストでは `--network host` + `localhost:8083` |
| `adapter_plaintext` | `true` | Adapter が TLS なし（フェーズ1 デフォルト） |
| `private_key_path` | `/opt/agent/private-key.pem` | コンテナ内パス |

### 4. Adapter をホストで起動

別ターミナルで：

```bash
cd /Users/kazuyasugimoto/Documents/Work/Projects/KintoneExternalAppCDataSample
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar serve
```

ただし `config/server.yaml` の `bind-address` を `0.0.0.0` にしておく必要があります（コンテナから接続できるように）：

```yaml
# config/server.yaml
port: 8083
bind-address: 0.0.0.0
plaintext: true
```

### 5. Agent コンテナをビルド・起動

プロジェクトルートで：

```bash
# ビルド
docker compose -f agent/docker-compose.yml build

# 起動（フォアグラウンド、ログ確認しやすい）
docker compose -f agent/docker-compose.yml up

# 起動（バックグラウンド）
docker compose -f agent/docker-compose.yml up -d

# ログ確認
docker compose -f agent/docker-compose.yml logs -f
```

成功すると以下のようなログが出る：

```
{"level":"INFO","msg":"starting agent","version":"v0.9.2"}
{"level":"INFO","msg":"successfully connected to kintone","session_id":"019c895e-..."}
```

### 6. kintone でコネクター接続

1. kintone システム管理 → 外部システムコネクター管理
2. 該当コネクターの「接続する」ボタンをクリック
3. ステータスが「**利用可能**」になればOK
   - この時点で Adapter の `GetCapability` RPC が呼ばれている

### 7. 外部連携アプリの作成

kintone アプリストア → 「外部システムに接続して作成」→ コネクター選択 → カラムマッピング → アプリ公開

## トラブルシュート

### Agent が `Connection refused` で起動しない

- Adapter が `0.0.0.0:8083` で待ち受けているか確認（`bind-address: 0.0.0.0`）
- Mac/Windows の場合: `agent.json` で `adapter_addr: "host.docker.internal:8083"`
- Linux の場合: `docker run --add-host=host.docker.internal:host-gateway` か `--network host`

### Agent が `failed to connect to kintone`

- `agent.json` の token が正しいか確認
- kintone 側でコネクターが削除されていないか確認

### マルチアーキビルド

`docker compose build` は実行アーキ用にビルドします。M1/M2 Mac なら arm64、Intel Mac/Linux なら amd64 が自動選択されます。明示指定する場合：

```bash
docker build --platform linux/amd64 -t kintone-agent agent/
```

## 停止

```bash
docker compose -f agent/docker-compose.yml down
```
