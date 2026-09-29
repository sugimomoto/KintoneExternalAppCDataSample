# コンテナのタイムゾーン — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | container-timezone |
| 対応 Issue | [#25](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/25) |
| 作成日 | 2026-09-29 |
| 経緯 | [#21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21) の実機確認で発見 |
| 目的 | 画面とログの時刻をホストのローカル時刻に合わせ、どの zone の時刻か判別できるようにする |

---

## 1. 背景

### 1.1 事象

`docker-compose.yml` が `TZ` を渡していないため、`eclipse-temurin` イメージの
既定タイムゾーン（UTC）がそのまま使われる。`ZoneId.systemDefault()` も UTC になり、
**画面とログの時刻がホストのローカル時刻と 9 時間ずれる**。

```
$ docker exec adapter-console sh -c 'date; echo TZ=$TZ'
Tue Sep 29 06:39:37 AM UTC 2026
TZ=
```

#21 の実機確認で、15:32 (JST) の操作に対して検知時刻が `06:32` と表示された。

### 1.2 影響箇所

| 箇所 | 表示 | 現状 |
|---|---|---|
| `web/views/DashboardView.kt:128` | 稼働中連携の開始時刻 | UTC・zone 表記なし |
| `web/views/TablesView.kt:472` | 接続キー拒否の検知時刻 | UTC・**zone 表記あり**（#21 で対応済み） |
| `cli/ListActiveCommand.kt:47` | `list-active` の `started=` | UTC・zone 表記なし |
| logback のログ | `2026-09-29 06:32:29.501` | UTC |
| Agent コンテナのログ（コンテナ内の時刻） | — | env を渡していないため UTC |

### 1.3 調査で判明した事実

| # | 事実 | 根拠 |
|---|---|---|
| F-1 | イメージは `TZ` を渡せば追従する | `docker run --rm -e TZ=Asia/Tokyo eclipse-temurin:21-jre-jammy date` → `03:39:40 PM JST` |
| F-2 | Agent コンテナは env を一切渡していない | `AgentContainerManager.ensureCreated` に `withEnv` が無い |
| F-3 | Docker のログタイムスタンプは `TZ` で変わらない | `withTimestamps(true)` は Docker Engine が RFC3339 の UTC で返す |
| F-4 | logback の `%d` は既定でローカル時刻 | `TZ` を設定すればログ出力も追従する |

---

## 2. ユーザーストーリー

- **運用者として**、画面の時刻が自分の時計と一致していてほしい。
  9 時間ずれていると「いつ起きたのか」を読み違える。

- **運用者として**、Agent のログと console のログの時刻が揃っていてほしい。
  障害調査で 2 つを並べて読むため。

- **別のタイムゾーンで運用する利用者として**、`TZ` で切り替えられてほしい。

- **運用者として**、`TZ` が設定されていない環境でも、
  表示されている時刻がどの zone なのか分かってほしい。

---

## 3. 受け入れ条件

- [ ] **AC-1** adapter-console の画面の時刻がホストのローカル時刻と一致する
- [ ] **AC-2** `TZ` 環境変数で上書きできる
- [ ] **AC-3** console のログ出力の時刻も揃う
- [ ] **AC-4** Agent コンテナの時刻も console と揃う
- [ ] **AC-5** 画面・CLI の時刻表示にタイムゾーンが併記される
- [ ] **AC-6** `TZ` を設定しない環境でも動作する（表示が UTC になるだけ）
- [ ] **AC-7** `./gradlew test` が通り、本変更で lint の指摘が増えない

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | `TZ` をコードにハードコードしない | 別タイムゾーンでの運用を塞ぐ。既定値は compose 側で `${TZ:-...}` として与える |
| C-2 | Agent コンテナには **console と同じ** `TZ` を渡す | ログを並べて読むため。console 側の値を引き継ぐ形にし、二重管理しない |
| C-3 | Docker のログタイムスタンプは対象外 | F-3 のとおり `TZ` では変わらない |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| Docker ログタイムスタンプのローカル表示 | Docker Engine の仕様。ブラウザ側で変換する話になるため別途判断 |
| 時刻表示形式の統一（フォーマッタの共通化） | 3 箇所で同じパターンを持つが、web / CLI の層をまたぐ共通化は別途判断 |
