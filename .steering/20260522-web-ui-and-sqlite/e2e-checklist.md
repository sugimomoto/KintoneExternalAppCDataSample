# Phase 2-B E2E チェックリスト

Web UI + SqliteConfigSource + Salesforce 新階層移行 の手動 E2E 手順。

## 前提

- Phase 2-A 完了済み（v0.2.1-quoted-identifiers 以上）
- Salesforce + Google Sheets の Connector / Adapter が稼働中（Phase 2-A 検証で実証済）
- Java 21、Docker Desktop、kintone 環境

## 0. ビルドとスモーク

```bash
./gradlew clean test shadowJar
# 期待: 223+ テスト緑、BUILD SUCCESSFUL
```

確認:
- [ ] `build/libs/adapter-0.1.0-SNAPSHOT-all.jar` 生成
- [ ] `java -jar build/libs/adapter-*-all.jar --help` で 11 サブコマンド表示

## 1. Web UI 起動とダッシュボード

```bash
java -jar build/libs/adapter-*-all.jar web-ui --port 8080
# ブラウザで http://127.0.0.1:8080/
```

確認:
- [ ] ダッシュボード表示（稼働 Adapter 一覧 + Quick Actions）
- [ ] フッタに `ConfigSource: yaml` 表示
- [ ] Phase 1 構成検出時に migrate バナー表示

## 2. Drivers 画面

確認:
- [ ] `/drivers` に既存 jar（salesforce, googlesheets）が一覧表示
- [ ] license_status が `●Activated`
- [ ] 新規 JAR アップロード → 「アクティベーション要」表示
- [ ] アクティベーション画面で name/email 入力 → 成功画面

## 3. Connections 画面 + 動的フォーム

確認:
- [ ] `/connections` に既存（フェーズ1 設定がある場合は空）
- [ ] `/connections/new` でドライバ選択 → `sys_connection_props` ベースのフォーム表示
- [ ] カテゴリグルーピング: Authentication 展開、他折りたたみ
- [ ] Required プロパティに `*` マーク
- [ ] PASSWORD/SENSITIVE プロパティが masked input
- [ ] 値を入力すると下部 sticky URL がリアルタイム更新
- [ ] 「Test connection」で実際の接続テスト
- [ ] 保存後 `/connections` に追加される

## 4. Salesforce を新階層に移行

```bash
# 1. 既存 Salesforce Adapter を停止
lsof -ti:8083 | xargs kill

# 2. バックアップ
cp -r config /tmp/config-backup-$(date +%Y%m%d)

# 3. 移行
java -jar build/libs/adapter-*-all.jar migrate-to-multi-table \
    --config-dir ./config --table-name account --shared-jdbc-name salesforce

# 4. ポート変更 (任意): config/tables/account/server.yaml の port を 18001 に
```

確認:
- [ ] `config/tables/account/{server,jdbc-ref,table,capability}.yaml` 生成
- [ ] `config/jdbc/salesforce.yaml` 生成
- [ ] `config/{server,jdbc,table,capability}.yaml` (直下) が消えている
- [ ] `adapter list-tables --jdbc-config config/jdbc/salesforce.yaml` で接続成功

## 5. Tables 画面でのテーブル管理

確認:
- [ ] `/tables` に account + gs-opportunity が一覧表示
- [ ] 各行に Start / Stop ボタン
- [ ] テーブル詳細画面で 4 セクション（Server / JDBC / Columns / Capability）表示
- [ ] Stop → Start で Adapter が再起動

## 6. Web UI から Adapter 起動 → kintone でレコード表示

```bash
# Agent 側 adapter_addr が 18001 (account) 等に合致しているか確認
docker compose -f agent/docker-compose.multi.yml restart kintone-agent
```

確認:
- [ ] Web UI で account を「Start」 → SSE で status が `●Running` に変わる
- [ ] kintone Account アプリでレコード一覧表示
- [ ] gs-opportunity も並列稼働

## 7. Table 新規ウィザード (S-05〜S-08)

確認:
- [ ] `/tables/new` Step1 で Connection 選択
- [ ] Step2 で接続先 DB テーブル一覧表示
- [ ] Step3 でカラム選択
- [ ] Step4 で自動マッピング表示、編集可能
- [ ] 「Save & Start」で即起動 → kintone でレコード表示

## 8. SQLite 切替

```bash
java -jar build/libs/adapter-*-all.jar migrate-to-sqlite --config-dir ./config
ls -la config/config.db

# 再起動
CONFIG_SOURCE=sqlite java -jar build/libs/adapter-*-all.jar web-ui
```

確認:
- [ ] `config.db` 生成
- [ ] Web UI フッタが `ConfigSource: sqlite` に
- [ ] Tables / Connections 画面で同じ内容が表示される

エクスポート確認:
- [ ] `adapter export-yaml --sqlite-path config/config.db --out-dir config-export`
- [ ] config-export 配下に同等の YAML 構造

## 9. SSE リアルタイム更新

確認:
- [ ] ブラウザを 2 タブ開く（`/`, `/tables`）
- [ ] 別ターミナルで `curl -X POST http://127.0.0.1:8080/tables/account/stop`
- [ ] 両タブで status バッジが 1 秒以内に `○Stopped` に変わる

## 10. 既知の制約・観察事項を記録

E2E 中に発見した課題は known-issues.md に追記:
- ISSUE-002 解決報告（migrate-to-multi-table 動作確認済み）
- 新しい改善案があれば次フェーズ送り
