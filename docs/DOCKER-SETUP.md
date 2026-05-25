# Docker セットアップガイド (管理者向け)

CData Kintone Adapter Console を Docker / Docker Compose で起動するための手順書です。
本書はサービスを構築・運用する管理者向けで、エンドユーザー向けの操作ガイドは
Web UI 上の `/help` を参照してください。

---

## 1. 構成概要

```
┌───────────────────────────────────────────────────────────────┐
│  Host (Linux / macOS / Windows + Docker Desktop)              │
│                                                               │
│   ┌──────────────────────┐                                   │
│   │  adapter-console     │  ← Web UI (8080) + Adapter (gRPC) │
│   │  (このサービス本体)   │     ports 18000-18099            │
│   └──────────┬───────────┘                                   │
│              │ docker.sock                                    │
│              ▼                                                │
│   ┌──────────────────────┐  ┌──────────────────────┐         │
│   │ kintone-agent-{sync} │  │ kintone-agent-{sync} │  ...    │
│   │  (動的生成 / 連携ごと) │  │                       │         │
│   └──────────┬───────────┘  └──────────┬───────────┘         │
└──────────────┼─────────────────────────┼─────────────────────┘
               │ HTTPS                   │ HTTPS
               ▼                         ▼
            kintone (cybozu.com 等)
```

- **adapter-console**: Web UI と Adapter (gRPC) を同居させた本体コンテナ
- **kintone-agent-{sync}**: 連携ごとに adapter-console が動的に作成・起動するコンテナ
- Docker socket (`/var/run/docker.sock`) を adapter-console にマウントして
  Agent の制御を行います

---

## 2. 前提条件

| 項目 | バージョン / 条件 |
|---|---|
| Docker Engine | 20.10 以上 (Compose v2 同梱版を推奨) |
| Docker Desktop (macOS/Windows) | 4.20 以上 |
| ホスト OS | Linux / macOS / Windows (WSL2) |
| ホストの空きディスク | 5 GB 以上 (JDBC Driver + イメージ) |
| ネットワーク | kintone (HTTPS) への送信が許可されていること |

> **ホストに JDK / Gradle / git をインストールする必要はありません。**
> ソースから自前ビルドする場合のみ JDK 21 が必要です。
> 既ビルドの jar を入手して使う運用も可能です。

---

## 3. ファイル準備

リポジトリをクローンし、必要なディレクトリを用意します。

```bash
git clone <repo-url> cdata-kintone-adapter
cd cdata-kintone-adapter

# 必要なホスト側ディレクトリを作成
mkdir -p lib config agent run
```

### 3.1 CData JDBC Driver

JDBC Driver jar は **起動後に Web UI (`/drivers`) からアップロード** できるため、
事前準備は不要です (`lib/` ディレクトリだけ用意しておけば OK)。

```bash
ls lib/
# (空でOK。Web UI からアップロードすると ./lib/ に保存される)
```

トライアルライセンスのアクティベーションも `/drivers` 画面で完結します。
CLI でアクティベートしたい場合のみ:

```bash
java -jar lib/cdata.jdbc.salesforce.jar -license
# → 名前 / メールアドレス / Trial を入力
```

> `docker-compose.yml` の `./lib` マウントは **rw** で行われます (Web UI からの
> アップロードがホスト側に書き込まれるため)。

### 3.2 kintone Agent バイナリの受領と配置

**Agent (`kintone-data-connector-agent`) のプログラム本体はサイボウズ社から
個別に受領する必要があります。** 公開レジストリでは配布されていません。

受領した zip / tar を展開し、対応アーキ (Linux amd64 / arm64) のバイナリを
`agent/bin/linux_<arch>/kintone-data-connector-agent` に配置してください。

```bash
# サイボウズから受領したファイル例:
#   kintone-data-connector-agent_v0.9.2_linux_amd64.tar.gz
#   kintone-data-connector-agent_v0.9.2_linux_arm64.tar.gz

mkdir -p agent/bin/linux_amd64 agent/bin/linux_arm64

tar -xzf kintone-data-connector-agent_v0.9.2_linux_amd64.tar.gz \
    -C agent/bin/linux_amd64 --strip-components=1
tar -xzf kintone-data-connector-agent_v0.9.2_linux_arm64.tar.gz \
    -C agent/bin/linux_arm64 --strip-components=1

chmod +x agent/bin/linux_amd64/kintone-data-connector-agent
chmod +x agent/bin/linux_arm64/kintone-data-connector-agent
```

> **配置されているか確認:**
> `ls agent/bin/linux_amd64/kintone-data-connector-agent` でファイルが
> 見えれば OK。Apple Silicon Mac でホスト実行する場合は `arm64` 側のみで構いません。

### 3.3 Agent コンテナイメージのビルド

adapter-console は Agent コンテナを名前付きイメージ
`kintone-data-connector-agent:0.9.2` から起動します。
このイメージは **管理者が同梱の `agent/Dockerfile` でローカルにビルド** します
(=`docker pull` ではありません)。

```bash
docker compose -f agent/docker-compose.yml build
# → kintone-data-connector-agent:0.9.2 が docker images に登録される

docker images | grep kintone-data-connector-agent
# kintone-data-connector-agent   0.9.2   <id>   ... MB
```

Dockerfile は配置済みのバイナリを `/usr/local/bin/` に COPY するだけの
軽量な内容です。`agent/Dockerfile` を参照してください。

> **マルチアーキ環境で運用する場合**は `docker buildx build --platform linux/amd64,linux/arm64`
> でビルドし、社内レジストリに push して各ホストで `docker pull` する運用が便利です。

### 3.4 `.env` の作成

```bash
cp .env.example .env
vi .env
```

主要な変数:

| 変数 | 用途 |
|---|---|
| `LOG_LEVEL` | `INFO` / `DEBUG` |
| `CONFIG_SOURCE` | `yaml` / `sqlite` (デフォルト `yaml`) |
| `HOST_AGENT_ROOT` | Agent コンテナの bind マウント解決に使うホスト絶対パス (compose 経由なら自動) |
| `SF_USER` 等 | 各データソースの環境変数 (オプション) |

### 3.5 Agent 鍵ペア

Agent と kintone は公開鍵認証で通信しますが、**鍵ペアは起動後に Web UI から
1 ボタンで生成できる**ため、事前準備は不要です。

連携詳細画面 (`/syncs/{name}/connect`) の Step 1 で「🔑 鍵ペアを生成する」を
押すと、adapter-console プロセスが `agent/public-key.pem` と
`agent/private-key.pem` (パーミッション 600) をホストに書き出します。
秘密鍵は画面に表示されません。

その後、画面に表示される公開鍵を kintone 「外部システムコネクター管理」に
貼り付け、発行された JWT トークンを Web UI で保存してください。

> OpenSSL CLI で自前生成する場合:
> ```bash
> cd agent
> openssl genrsa 2048 > private-key.pem
> openssl rsa -pubout -in private-key.pem -out public-key.pem
> chmod 600 private-key.pem
> ```

---

## 4. 初回起動

```bash
docker compose build adapter-console
docker compose up -d adapter-console
docker compose logs -f adapter-console
```

起動確認:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
# → 200
```

ブラウザで <http://localhost:8080> を開き、ダッシュボードが表示されれば成功です。

---

## 5. ボリュームとデータ永続化

`docker-compose.yml` で以下のホストディレクトリをマウントしています。
バックアップ対象に含めてください。

| マウント元 | コンテナ内 | 内容 |
|---|---|---|
| `./lib` | `/app/lib` (ro) | JDBC Driver jar + ライセンス |
| `./config` | `/app/config` | テーブル定義 YAML / SQLite (config.db) |
| `./agent` | `/app/agent` | 公開鍵 + agent.json (Agent と共有) |
| `./run` | `/app/run` | 稼働状態 (`active-adapters.json`) |
| `/var/run/docker.sock` | 同左 | Agent コンテナ制御 |

> **ホスト OS で SELinux / AppArmor が有効な場合**は `:z` / `:Z` フラグや
> ポリシー調整が必要です。

---

## 6. 運用コマンド

```bash
# 起動 / 停止
docker compose up -d adapter-console
docker compose stop adapter-console

# 再起動 (設定変更後など)
docker compose restart adapter-console

# ログ確認
docker compose logs -f adapter-console
docker logs -f kintone-agent-<syncName>     # 個別 Agent のログ

# 状態確認
docker compose ps
docker ps --filter label=com.cdata.adapter.managed=true

# 更新 (新しい jar を反映)
docker compose build adapter-console
docker compose up -d adapter-console

# 完全削除 (Agent コンテナも含む)
docker compose down
docker ps -aq --filter label=com.cdata.adapter.managed=true | xargs docker rm -f
```

---

## 7. セキュリティ上の注意

- **Docker socket のマウントはルート権限相当**です。本サンプルはローカル開発・
  サンプル提供を目的としており、本番運用ではアクセスを社内ネットワークに限定し、
  Docker socket proxy 等の隔離手段を検討してください。
- `lib/*.lic` / `.env` / `agent/*.pem` には機密情報が含まれます。
  `.gitignore` 済みですが、誤コミットに注意してください。
- adapter-console コンテナを公開ネットワークに直接公開しないでください。
  リバースプロキシ + 認証 (BASIC / OIDC) を前にかぶせる構成を推奨します。

---

## 8. トラブルシューティング

### 起動直後に「Cannot connect to Docker daemon」

Docker socket がマウントされていません。`docker-compose.yml` の
`/var/run/docker.sock:/var/run/docker.sock` 行を確認してください。

### 連携を開始すると 500 が返る

`docker logs adapter-console` で次を確認:

- Agent イメージ (`kintone-data-connector-agent:0.9.2`) が `docker images` に
  ローカルビルド済みで存在するか
  → 無ければ `docker compose -f agent/docker-compose.yml build` を実行
- `agent/bin/linux_<arch>/kintone-data-connector-agent` バイナリが
  存在し実行権限があるか (サイボウズから受領したバイナリ本体)
- 既存の `kintone-agent-<syncName>` が `Exited` で残っていないか
  → `docker rm kintone-agent-<syncName>`
- `HOST_AGENT_ROOT` の絶対パスがホスト側に実在するか

### Web UI に変更が反映されない

`docker compose build adapter-console` でイメージを再生成してから
`docker compose up -d` してください。`restart` だけでは古い jar のままです。

### JDBC Driver を後から追加した

`lib/` にコピーした後、adapter-console を再起動 (`docker compose restart`) すると
`/drivers` 画面に追加分が表示されます。

---

## 9. アップグレード手順

1. 既存の `lib/` `config/` `agent/` `run/` をバックアップ
2. `git pull` で新バージョンを取得
3. `docker compose build adapter-console`
4. `docker compose up -d adapter-console`
5. Web UI でバージョン (フッター) が更新されたことを確認
6. 既存の連携を順次再起動 (画面の「再起動」ボタン)

---

## 10. 関連ドキュメント

- [README.md](../README.md) — 開発者向けクイックスタート (ホスト Java で起動する手順)
- [docs/architecture.md](architecture.md) — 全体アーキテクチャ
- [docs/functional-design.md](functional-design.md) — 機能設計
- Web UI `/help` — エンドユーザー向け操作ガイド
