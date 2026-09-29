# 認証失敗した Agent コンテナの停止 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | stop-auth-failed-agents |
| 対応 Issue | [#19](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/19) |
| 作成日 | 2026-09-29 |
| 目的 | 接続キーを拒否された Agent コンテナを止め、無限の再試行を断つ |
| 後続 | [#20](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/20) 状態の可視化 / [#21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21) 再入力への導線 |

---

## 1. 背景

### 1.1 事象

kintone に接続キーを拒否された Agent コンテナが、1 分おきに起動 → 失敗 → 終了を無限に繰り返す。
実環境では 4 つのコンテナが 263 回以上再起動していた。

```
kintone-agent-Product                        Restarting (0) 28 seconds ago
kintone-agent-ProductPlant                   Restarting (0) 28 seconds ago
kintone-agent-GoogleSheetOpportunitySample   Restarting (0) 28 seconds ago
kintone-agent-bcartorders                    Restarting (0) 28 seconds ago
```

```
{"level":"ERROR","msg":"failed to connect to kintone","err":"rpc error: code = Unauthenticated desc = invalid token"}
Error: rpc error: code = Unauthenticated desc = invalid token
```

### 1.2 現状の実装

| 箇所 | 内容 |
|---|---|
| `agent/SyncConnectionService.kt:186` | `AUTH_FAILURE_MARKERS` で認証失敗を**検知している** |
| `agent/SyncConnectionService.kt:112` | `AUTH_FAILED` は `Result.Failure` を返すだけ。**コンテナを停止しない** |
| `agent/docker-compose.yml` / `AgentContainerManager.kt:99` | 再起動ポリシーは `unless-stopped` |
| `web/AppContext.kt:87` | 起動時に復元するのは Adapter だけ。Agent の健全性は見ていない |

### 1.3 課題

1. **再試行で直らない失敗を再試行し続けている**

   認証拒否は接続キーを入れ替えるまで永久に直らない。にもかかわらず
   `restart: unless-stopped` により 1 分おきに kintone へ失敗リクエストを投げ続ける。

2. **前のセッションから走り続けているコンテナには手が届かない**

   検知は「kintone とつなぐ」操作の経路にしかない。console を再起動しても
   ループ中のコンテナは放置される（実環境の 4 つがこの状態）。

### 1.4 調査で判明した事実

| # | 事実 | 根拠 |
|---|---|---|
| F-1 | **トークンの期限切れではない** | ループ中 4 件の JWT をデコード。`exp` は 2027 年で全て有効期限内 |
| F-2 | 署名鍵は正常な連携と同一 | 全て `kid=5d6feec2`、`alg=EdDSA` |
| F-3 | 拒否の原因は kintone 側の接続の作り直し | ペイロードは `{"iss":"kintone-data-connector","sub":<接続ID>,"aud":[<ID>]}`。`sub` が指す接続が kintone 側に無くなると、署名・期限が有効でも拒否される |
| F-4 | 正常な連携との違いは発行日時だけ | 正常 = 当日発行 (09-29)、ループ = 09-24 / 06-02 発行 |
| F-5 | **Agent は認証失敗でも終了コード 0 で終わる** | `docker ps` が `Restarting (0)` / `Exited (0)` を表示 |
| F-6 | `stop()` は停止済みでも安全 | `NotModifiedException` (304) を正常系として扱う実装がある |
| F-7 | 管理対象コンテナは列挙できる | `listAll()` がラベル `com.cdata.adapter.managed` で絞り込む |
| F-8 | ログは時間で絞り込める | `fetchLogs(sinceSeconds = ...)` が Docker の `since` を使う |

---

## 2. ユーザーストーリー

- **運用者として**、接続キーが無効になった連携の Agent は止まってほしい。
  直らない失敗を延々と繰り返して kintone にリクエストを投げ続けるのは避けたい。

- **運用者として**、console を再起動したら、すでにループに入っている Agent も止まってほしい。
  気付いていないコンテナを手で探して止めたくない。

- **運用者として**、一過性の失敗で止められては困る。
  Adapter の起動待ちのような状況では、従来どおり自動で復帰してほしい。

---

## 3. 受け入れ条件

- [ ] **AC-1** 接続キーを拒否されたとき、Agent コンテナが停止する（再起動ループに入らない）
- [ ] **AC-2** 停止後、Docker の再起動ポリシーによって自動復活しない
- [ ] **AC-3** console 起動時に、認証失敗で再起動を繰り返しているコンテナを検知して停止する
- [ ] **AC-4** 一過性の失敗（Adapter 未起動など）では停止しない。従来どおり `Pending` として扱う
- [ ] **AC-5** 接続成功時の挙動は変わらない（停止しない）
- [ ] **AC-6** 過去に認証失敗したが現在は正常稼働しているコンテナを停止しない
- [ ] **AC-7** Docker socket が使えない環境で起動が失敗しない
- [ ] **AC-8** 停止した連携がログに残り、後から理由が分かる
- [ ] **AC-9** 認証失敗の判定ルールが 1 箇所に集約されている（接続操作と起動時棚卸しで二重実装しない）
- [ ] **AC-10** `./gradlew test` が通り、本変更で lint の指摘が増えない

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | **再起動ポリシーは `unless-stopped` から変えない** | F-5 のとおり認証失敗でも終了コード 0 なので、`on-failure` では**そもそも再起動されない**。一過性の失敗からも復帰しなくなる。ホスト・console 再起動後の自動復帰も維持したい |
| C-2 | 起動時の棚卸しは**直近のログだけ**を見る | 全ログを見ると、過去に認証失敗して既に復旧した連携を誤って止める（AC-6）。Issue #4 と同じ失敗を繰り返さない |
| C-3 | 起動時の棚卸しで例外を外に出さない | Docker の状態に依存する処理で console の起動を落とさない |
| C-4 | 判定マーカーを複製しない | 接続操作側と棚卸し側で文言がずれると片方だけ検知漏れする |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| Agent の状態を画面に出す | [#20](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/20) |
| 接続キー再入力への導線 | [#21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21) |
| `State` への `RESTARTING` 追加 | #20。本件は「直近ログに認証失敗があるか」で判定するため State を触らない |
| 定期的な監視（cron 的な常時監視） | 起動時 + 接続操作時で足りる。常駐監視は別途判断 |
