# Agent コンテナの状態表示 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | agent-status-visibility |
| 対応 Issue | [#20](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/20) |
| 作成日 | 2026-09-29 |
| 前提 | [#19](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/19) 完了済み（認証失敗した Agent は自動停止される） |
| 目的 | Agent コンテナの異常を画面から把握できるようにする |
| 後続 | [#21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21) 接続キー再入力への導線 |

---

## 1. 背景

### 1.1 なぜ必要か

#19 では Agent コンテナが 295 回以上再起動していたが、**画面からは異常だと分からなかった**。
気付きが遅れた直接の原因は、UI が Agent の状態を表示していないこと。
#19 で自動停止するようにしたが、「なぜ止まっているのか」も画面からは分からない。

### 1.2 現状の実装

| 箇所 | 内容 |
|---|---|
| `web/views/TablesView.kt:387` | `statusBadge(active, port)` は **Adapter の起動状態しか見ていない** |
| `web/views/TablesView.kt:85` | 一覧の「状態」列は `ctx.runner.listActive()` の結果だけ |
| `agent/AgentContainerManager.kt:45` | `State` は `RUNNING / STOPPED / NOT_FOUND` の 3 値 |
| `agent/AgentContainerManager.kt:61` | `status()` は `inspect.state.running` の真偽で判定 |
| `web/routes/` | `agentContainerManager` を参照するのは `SyncLogsRoutes` と `TablesRoutes` だけ。一覧では未使用 |

### 1.3 課題

1. **Agent の異常が一覧に出ない**

   | 実際の状態 | 現在の表示 |
   |---|---|
   | Adapter 稼働 + Agent 正常 | 稼働中 |
   | Adapter 稼働 + Agent が再起動ループ | **稼働中**（区別できない） |
   | Adapter 稼働 + Agent が停止（#19 で自動停止された） | **稼働中**（区別できない） |
   | Adapter 稼働 + Agent コンテナ未作成 | **稼働中**（区別できない） |

2. **`State` に再起動ループを表す値が無い**

   `status()` が `inspect.state.running` の真偽で判定しているため、
   **再起動ループ中も `RUNNING` を返す**。#19 の調査で実測した値:

   ```
   ProductPlant restarts=295 running=true status=restarting
   ```

   `running=true` かつ `status=restarting` という状態を、現在の `State` では表現できない。

3. **再起動回数が見えない**

   ループの深刻度（実環境では 295 回）が分からない。

### 1.4 調査で判明した事実

| # | 事実 | 詳細 |
|---|---|---|
| F-1 | Docker の `State.Status` は再起動中を表せる | `restarting` を返す。`created` / `running` / `paused` / `exited` / `dead` などの取り得る値がある |
| F-2 | 再起動回数は `inspect` にしかない | `InspectContainerResponse.restartCount`。コンテナ一覧 API (`listContainers`) には含まれない |
| F-3 | コンテナ一覧 API は状態文字列を返す | `listAll()` が既に `c.state` を見ているが、`"running"` 以外を全て `STOPPED` に畳んでいる |
| F-4 | 一覧は連携ごとに設定を読み込んでいる | `TablesView` のループ内で `loadTableSet(name)` を呼ぶ構造 |
| F-5 | バッジの CSS には 3 状態が定義済み | `.status-badge` の `serving` / `stopped` / `unknown`。危険色のトークン (`--color-danger-soft`) も既にある |

---

## 2. ユーザーストーリー

- **運用者として**、連携一覧を見たときに Agent が異常な連携を見つけたい。
  正常な連携と同じ見た目だと、異常に気付けない。

- **運用者として**、連携詳細で再起動回数を見たい。
  1 回の失敗と 295 回の失敗を区別したい。

- **運用者として**、Agent コンテナをまだ作っていない連携と、
  作ったが止まっている連携を区別したい。

- **Docker を使わずに動かしている利用者として**、
  使えない情報で画面が埋まったり壊れたりしないでほしい。

---

## 3. 受け入れ条件

### 3.1 状態の判定

- [ ] **AC-1** 再起動ループ中のコンテナが `RESTARTING` として判定される
- [ ] **AC-2** 正常稼働中は `RUNNING`、停止中は `STOPPED`、未作成は `NOT_FOUND` のまま（デグレなし）
- [ ] **AC-3** `paused` / `dead` など未知の状態を `RUNNING` と誤判定しない
- [ ] **AC-4** 再起動回数が `ContainerInfo` から取れる

### 3.2 表示

- [ ] **AC-5** 連携一覧に Agent の状態が出る。Adapter の状態とは別に表示される
- [ ] **AC-6** 再起動ループが視覚的に「異常」と分かる（正常・停止と色で区別される）
- [ ] **AC-7** 連携詳細に再起動回数が出る
- [ ] **AC-8** Adapter の状態表示は従来どおり（デグレなし）

### 3.3 動作条件

- [ ] **AC-9** Docker socket が使えない環境で画面が壊れない。Agent 列を出さない
- [ ] **AC-10** Docker 呼び出しが連携数に比例して増えない（一覧の表示で N+1 にしない）
- [ ] **AC-11** Docker 呼び出しが失敗しても一覧が表示できる

### 3.4 品質

- [ ] **AC-12** 状態判定にユニットテストがある
- [ ] **AC-13** `./gradlew test` が通り、本変更で lint の指摘が増えない

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | 一覧で連携ごとに `inspect` を呼ばない | 連携数ぶん Docker 呼び出しが増える。一覧は表示のたびに実行される（AC-10） |
| C-2 | Docker 呼び出しの失敗で一覧を落とさない | 状態表示は補助情報。本体の一覧が見られなくなるほうが困る（AC-11） |
| C-3 | 画面の呼称を新設しない | 開発ガイドライン 3.5.1。「連携」「Agent」など既存の呼称を使う |
| C-4 | `State` に値を足す際、既存の分岐を壊さない | `SyncConnectionService.ensureRunningWithLatestConfig` が `when` で分岐している |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| 接続キー再入力への導線 | [#21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21) |
| 停止理由（認証失敗など）の記録・表示 | #21。本件は Docker から取れる状態だけを扱う |
| 状態の自動更新（ポーリング） | 画面を開き直せば分かる。常時更新は別途判断 |
| ダッシュボードへの集約表示 | 連携一覧と詳細に出せば足りる |
