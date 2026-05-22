# フェーズ2-A E2E チェックリスト

requirements §7 の S-01 〜 S-08 + マイグレーション + 多ドライバー混在 を網羅的に検証する手順書。
人手・実 kintone 環境・実 Salesforce 環境が必要なので、自動化せず手順を残す。

## 前提環境

- macOS / Java 21 (`/opt/homebrew/opt/openjdk@21`)
- Docker Desktop
- kintone 環境（外部システムアプリ機能有効化）
- Salesforce Developer Edition（OAuth or User+Password+SecurityToken）
- （任意）Google Sheets API のクライアント ID / シークレット

## 0. ビルド・テスト

```bash
./gradlew clean test shadowJar
# 期待: BUILD SUCCESSFUL / 全テスト緑 (現在 181 件)
```

確認項目:
- [ ] `build/libs/adapter-0.1.0-SNAPSHOT-all.jar` が生成された
- [ ] `./gradlew test` で 0 failures
- [ ] `java -jar build/libs/adapter-*-all.jar --help` で 7 サブコマンド表示

## 1. マイグレーション (S-00 / requirements §7 不採用シナリオ)

フェーズ1 の `config/server.yaml`, `jdbc.yaml`, `table.yaml`, `capability.yaml`
が直下にある状態で:

```bash
java -jar build/libs/adapter-*-all.jar migrate-config --config-dir ./config
```

確認項目:
- [ ] `config/tables/default/*.yaml` に 4 ファイル移動
- [ ] `config/server.yaml` 等が config 直下から消えている
- [ ] `config/server.yaml.example` 等は残っている
- [ ] 再度 `migrate-config` を実行すると「既に新構成です」が表示される

## 2. 多テーブル設定生成 (S-01)

```bash
# Account
java -jar build/libs/adapter-*-all.jar init-table \
  --jdbc-config config/jdbc/salesforce.yaml \
  --name account --jdbc-ref salesforce --table Account --non-interactive

# Contact
java -jar build/libs/adapter-*-all.jar init-table \
  --jdbc-config config/jdbc/salesforce.yaml \
  --name contact --jdbc-ref salesforce --table Contact --non-interactive

# Opportunity
java -jar build/libs/adapter-*-all.jar init-table \
  --jdbc-config config/jdbc/salesforce.yaml \
  --name opportunity --jdbc-ref salesforce --table Opportunity --non-interactive
```

確認項目:
- [ ] `config/tables/{account,contact,opportunity}/table.yaml` 生成
- [ ] 各 `jdbc-ref.yaml` に `name: salesforce`
- [ ] `server.yaml` の `port: 0`（auto）
- [ ] `capability.yaml` に `record-id-type: TEXT`

## 3. serve-all 起動 (S-01)

```bash
MULTI=1 ./scripts/restart-stack.sh --build
```

確認項目:
- [ ] 3 つのテーブルで gRPC サーバが別ポートで起動
- [ ] `./run/active-adapters.json` に 3 件の状態
- [ ] `adapter list-active` で 3 件表示
- [ ] 各 Adapter で `grpc.health.v1.Health/Check` が `SERVING` を返す

## 4. 多 Connector 登録 (S-02)

kintone 管理画面で:
- [ ] Account Connector を登録（public-key.pem アップロード、token 取得）
- [ ] Contact Connector を登録（同じ public-key、別 token）
- [ ] Opportunity Connector を登録（同じ public-key、別 token）

`agent/tables/{account,contact,opportunity}/agent.json` に各 token + Adapter ポートを記入。

```bash
docker compose -f agent/docker-compose.multi.yml up -d
```

確認項目:
- [ ] 3 つの Agent コンテナがすべて kintone に接続成功
- [ ] `docker logs kintone-agent-account` 等で `successfully connected to kintone`

## 5. 外部連携アプリ作成 (S-02 / S-03 / S-04)

各 Connector に対応する kintone 外部連携アプリを作成:
- [ ] Account アプリ作成 → レコード一覧表示確認
- [ ] Contact アプリ作成 → レコード一覧表示確認
- [ ] Opportunity アプリ作成 → レコード一覧表示確認

## 6. 1 Adapter のみ再起動 (S-05)

```bash
# Account の Adapter を kill して再起動。Contact/Opportunity は止めない
adapter_jar=build/libs/adapter-0.1.0-SNAPSHOT-all.jar
# 該当ポートを active-adapters.json から取得して... (実検証で手順詰める)
```

確認項目:
- [ ] Contact / Opportunity アプリは影響なくレコード表示が続く
- [ ] Account アプリは一時的に「セッションが見つかりません」など出るが、Adapter 復旧後に正常化

## 7. 4 つ目のテーブル追加 (S-06)

稼働中に Lead テーブルを追加:

```bash
java -jar $adapter_jar init-table --name lead --jdbc-ref salesforce --table Lead --non-interactive
# Adapter を再起動して反映
MULTI=1 ./scripts/restart-stack.sh
```

確認項目:
- [ ] `list-active` で 4 件表示

## 8. 並列 CRUD (S-08)

複数 kintone アプリに同時にレコード追加・更新・削除操作を行う。

確認項目:
- [ ] Adapter ログにエラーなし
- [ ] 各アプリの操作が独立して成功
- [ ] OAuth キャッシュが `./run/oauth/{account,contact,...}.txt` に分かれて作成されている

## 9. 多ドライバー混在 (M6-C)

```bash
# Google Sheets JDBC Driver を lib/ に配置
# (CData サイトからダウンロード)

# Google Sheets 用テーブル設定
java -jar $adapter_jar init-table \
  --jdbc-config config/jdbc/googlesheets.yaml \
  --name gs-orders --jdbc-ref googlesheets --table Orders

# 再起動
MULTI=1 ./scripts/restart-stack.sh
```

確認項目:
- [ ] Salesforce 3 テーブル + Google Sheets 1 テーブルが 1 JVM で同時稼働
- [ ] 各 OAuth キャッシュが独立（`./run/oauth/*.txt`）
- [ ] kintone から両ソースの操作が成功

## 振り返り

E2E 完了後、`docs/feedback-to-cybozu.md` に下記をフィードバックとして追記:
- フェーズ2-A で発見した Agent / kintone 側の課題
- 設計判断（ファイルベース状態管理採用）の運用上の使用感
