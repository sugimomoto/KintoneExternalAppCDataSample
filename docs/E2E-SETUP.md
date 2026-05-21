# E2E セットアップ手順（kintone Agent 連携）

本ドキュメントは kintone Agent を経由した Adapter の E2E 検証手順を示す。
フェーズ1 タスクリストの **T-M4-C-03 〜 T-M4-C-10** に対応。

## 前提

- Adapter が `localhost:8083` で起動可能（README §4 まで完了している）
- kintone ワイドコース環境にシステム管理者権限がある
- サイボウズから提供された `kintone-data-connector-agent` バイナリを保持している

## 1. 鍵ペアの生成

Adapter ホストで実行：

```bash
cd /path/to/adapter
mkdir -p .agent
cd .agent

# 秘密鍵
openssl genrsa 2048 > private-key.pem

# 公開鍵を抽出
openssl rsa -pubout -in private-key.pem -out public-key.pem

# 公開鍵の内容を表示（コピペ用）
cat public-key.pem
```

## 2. kintone でコネクター登録

1. kintone にシステム管理者でログイン
2. システム管理 → 「外部システムコネクターの管理」
3. 「+ コネクターを追加」をクリック
4. フォームに入力：
   - コネクター名: `my-salesforce-adapter`
   - 説明: `Salesforce 連携用（CData JDBC Driver 経由）`
   - 公開鍵: 上記 `public-key.pem` の内容を貼り付け（`-----BEGIN PUBLIC KEY-----` から `-----END PUBLIC KEY-----` まで）
5. 「追加する」クリック
6. **トークンが表示される（1度しか見られないのでコピーして控える）**

## 3. Agent 設定ファイル

```bash
cd /path/to/kintone-data-connector-agent_v0.9.2_*
cat > agent.json <<EOF
{
  "token": "（kintone で発行された JWT トークン）",
  "adapter_addr": "localhost:8083",
  "adapter_plaintext": true,
  "private_key_path": "/path/to/adapter/.agent/private-key.pem"
}
EOF
```

## 4. Adapter 起動

別ターミナルで：

```bash
cd /path/to/adapter
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
export SF_USER=your_user
export SF_PASSWORD=your_password
export SF_SECURITY_TOKEN=your_token

java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar serve
```

ログに `Adapter サーバ起動: port=8083` が表示されること。

## 5. Agent 起動

別ターミナルで：

```bash
cd /path/to/kintone-data-connector-agent_v0.9.2_linux_amd64
./kintone-data-connector-agent --config agent.json
```

ログに以下が表示されれば成功：
```
{"level":"INFO","msg":"starting agent","version":"v0.9.2"}
{"level":"INFO","msg":"successfully connected to kintone","session_id":"..."}
```

## 6. kintone でコネクター接続

1. kintone システム管理 → 外部システムコネクター管理
2. 該当コネクターの「接続する」ボタンをクリック
3. ステータスが「**利用可能**」になることを確認
   - この時点で Adapter の `GetCapability` RPC が呼ばれている

## 7. 外部連携アプリの作成

1. kintone アプリストア → 「外部システムに接続して作成」
2. 作成済みのコネクターを選択
3. 表示されたカラム一覧で、各カラムの kintone フィールド型を選択
4. 「アプリを作成」をクリック
5. アプリ設定画面で「アプリを公開」

## 8. 動作確認シナリオ（要件 §7 と対応）

| シナリオ | 操作 | 期待結果 | 対応 RPC |
|---|---|---|---|
| S-01 | アプリ作成画面でカラム表示 | Salesforce Account のスキーマ表示 | GetSchema |
| S-02 | レコード一覧画面を開く | Salesforce Account レコード表示 | Select, Count |
| S-03 | 新規レコード追加 | Salesforce に新規 Account が作成される | Insert |
| S-04 | レコード編集して保存 | Salesforce 側で更新される | Update |
| S-05 | レコード削除 | Salesforce 側で削除される | Delete |
| S-06 | 業種 = Banking で絞り込み | WHERE 句が発行される | Select (filter) |
| S-07 | 年商の降順でソート | ORDER BY が発行される | Select (sort) |
| S-08 | ページング操作 | OFFSET/LIMIT で取得 | Select (pagination) |
| S-09 | `count-strategy: ALWAYS_ZERO` 時 | 件数 0 と表示されるが Select は動作 | Count |
| S-10 | jdbc.yaml を Google Sheets に変更 → 再起動 | 別データソースで動作 | 全般 |

## トラブルシュート

### Agent が `failed to connect to kintone` で起動しない

- `agent.json` の token が正しいか確認
- kintone 側でコネクターが削除されていないか確認

### コネクター接続で「失敗」になる

- Adapter が起動しているか確認 (`./scripts/test-adapter.sh GetCapability` で応答するか)
- Agent の `adapter_addr` が正しいか確認

### kintone アプリでレコードが表示されない

- Adapter ログで `Select called` が出ているか
- SQL 文がエラーになっていないか（DEBUG レベルでログ出力）：
  ```bash
  LOG_LEVEL=DEBUG java -jar build/libs/adapter-0.1.0-SNAPSHOT-all.jar serve
  ```

### Insert で 失敗

- CData JDBC Driver の `Statement.RETURN_GENERATED_KEYS` 対応を確認
- Salesforce の場合、必須項目（Name 等）が漏れていないか確認

## 補足：開発者向け Adapter 単体テスト

Agent を介さず curl で Adapter 単体を確認したい場合は `scripts/test-adapter.sh` を使用：

```bash
brew install grpcurl
./scripts/test-adapter.sh GetCapability
./scripts/test-adapter.sh GetSchema
./scripts/test-adapter.sh Select
./scripts/test-adapter.sh Count
```
