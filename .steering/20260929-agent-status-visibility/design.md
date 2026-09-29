# Agent コンテナの状態表示 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | agent-status-visibility |
| 対応 Issue | [#20](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/20) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | `State` に `RESTARTING` を追加し、`status()` を **`state.status` 文字列ベース**の判定に統一する | `running` 真偽では再起動ループを表現できない（実測で `running=true status=restarting`）。`status` は `restarting` を明示的に返す（F-1） | `restartCount > 0` で判定する案 → 過去に再起動しただけの正常なコンテナを異常扱いしてしまう |
| D-2 | 一覧は **`listAll()` を 1 回**呼んで連携名 → 状態のマップを作る | 行ごとに `inspect` すると連携数ぶん Docker 呼び出しが増える（C-1, AC-10） | 行ごとに `status(name)` を呼ぶ案 |
| D-3 | 再起動回数は**詳細画面だけ**に出す | `restartCount` は `inspect` にしかなく、一覧 API には含まれない（F-2）。一覧で出すには N+1 になる | 一覧にも出す案 |
| D-4 | Adapter と Agent は**別のバッジ**にする | 別軸の情報。1 つに混ぜると「どちらが落ちているのか」が分からない（AC-5） | 1 つのバッジに統合する案 |
| D-5 | Docker socket 不可なら **Agent 列そのものを出さない** | 常に「不明」が並ぶ列は情報量がなく、幅を食うだけ（AC-9） | 「不明」と表示する案 |
| D-6 | 状態取得の失敗は**空マップ**として扱う | 状態表示は補助情報。一覧本体が見られなくなるほうが困る（C-2, AC-11） | 例外を伝播させる案 |
| D-7 | 連携名 → 状態の変換は `AgentContainerManager` に置く | View でコンテナ名の接頭辞を剥がす処理を持たせたくない。#19 の `AgentAuthFailureSweeper` も同じ変換をしている | View 側でマップを組む案 |

### 1.2 状態のマッピング

Docker の `State.Status` から `State` への変換。

| Docker の status | `State` | 画面 |
|---|---|---|
| `running` | `RUNNING` | 稼働中（緑） |
| `restarting` | `RESTARTING` | 再起動中（赤・異常） |
| `created` / `exited` / `paused` / `dead` / `removing` | `STOPPED` | 停止中（グレー） |
| コンテナが存在しない | `NOT_FOUND` | 未作成（グレー・薄い） |

`status` が取れない場合のみ、従来どおり `running` 真偽にフォールバックする。
docker-java の応答が想定と違っても RUNNING / STOPPED の判定は維持される。

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `agent/AgentContainerManager.kt` | 変更 | `State.RESTARTING` を追加、`status()` / `listAll()` の判定を `status` 文字列ベースに統一、`ContainerInfo.restartCount` を追加、`statusesBySyncName()` を追加 |
| `web/views/TablesView.kt` | 変更 | 一覧に Agent 列を追加、詳細に Agent の状態と再起動回数を表示、`agentStatusBadge` を追加 |
| `resources/static/app.css` | 変更 | `.status-badge.failing`（危険色）を追加 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `agent/AgentContainerManagerTest.kt` | 変更 | 状態判定（`restarting` / 未知の値 / `status` 欠落時のフォールバック）、`restartCount`、`statusesBySyncName` |

---

## 3. データ構造

### 3.1 `State` / `ContainerInfo`

```kotlin
enum class State {
    RUNNING,
    /** 再起動ループ中。認証失敗などで起動に失敗し続けている状態 (Issue #19, #20)。 */
    RESTARTING,
    STOPPED,
    NOT_FOUND,
}

data class ContainerInfo(
    val name: String,
    val state: State,
    val image: String,
    val createdAt: Instant?,
    val containerId: String? = null,
    /**
     * Docker がこのコンテナを再起動した回数。
     * コンテナ一覧 API では取得できないため、`listAll()` 由来では 0 になる。
     */
    val restartCount: Int = 0,
)
```

`RESTARTING` を `RUNNING` の直後に置く。`when` の網羅漏れはコンパイラが検出する。

### 3.2 `statusesBySyncName`

```kotlin
/**
 * 管理対象コンテナの状態を「連携名 → 状態」で返す。
 *
 * 一覧画面が行ごとに `inspect` を呼ばないための入口。
 * Docker 呼び出しに失敗した場合は空マップを返す（状態表示は補助情報のため）。
 */
fun statusesBySyncName(): Map<String, ContainerInfo>
```

`listAll()` の結果からコンテナ名の接頭辞を剥がして連携名にする。

---

## 4. 実装

### 4.1 状態判定の共通化

```kotlin
private fun parseState(status: String?, running: Boolean?): State = when (status?.lowercase()) {
    "running" -> State.RUNNING
    "restarting" -> State.RESTARTING
    null -> if (running == true) State.RUNNING else State.STOPPED   // フォールバック
    else -> State.STOPPED
}
```

`status()`（`inspect` 由来）と `listAll()`（一覧 API 由来）の両方から呼ぶ。
判定を 1 箇所にまとめ、片方だけ `restarting` を落とすことを防ぐ。

### 4.2 一覧画面

```kotlin
// 行ごとに inspect を呼ばないよう、1 回で全件分の状態を取る。
val agentStatuses = ctx.agentContainerManager?.let {
    runCatching { it.statusesBySyncName() }.getOrDefault(emptyMap())
}
```

`agentStatuses` が null（Docker 不可）のときは Agent 列のヘッダーとセルを出さない。

| 列 | 内容 |
|---|---|
| 名前 / 接続先テーブル / データソース | 変更なし |
| 状態 | Adapter のバッジ（変更なし） |
| **Agent** | **新規。Agent コンテナのバッジ** |
| 操作 | 変更なし |

### 4.3 バッジ

```kotlin
private fun kotlinx.html.FlowContent.agentStatusBadge(info: AgentContainerManager.ContainerInfo?) {
    when (info?.state) {
        AgentContainerManager.State.RUNNING -> badge("serving", "稼働中")
        AgentContainerManager.State.RESTARTING -> badge("failing", "再起動中")
        AgentContainerManager.State.STOPPED -> badge("stopped", "停止中")
        AgentContainerManager.State.NOT_FOUND, null -> badge("stopped", "未作成")
    }
}
```

`failing` は新規。`--color-danger-soft` / `--color-danger` を当てる（F-5）。

### 4.4 詳細画面

Agent のバッジに加えて再起動回数を出す。0 回のときは出さない（ノイズになる）。

```
Agent: 再起動中  (295 回再起動)
```

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| `SyncConnectionService.ensureRunningWithLatestConfig` | `when (state)` に `RESTARTING` が増え網羅漏れになる | `RESTARTING` は restart して agent.json を読み直させる（RUNNING と同じ扱いが妥当） |
| `AgentAuthFailureSweeper` | `state` を参照していない（ログで判定） | 変更なし |
| `web/routes/TablesRoutes.kt` | `status()` の戻り値を使う箇所があれば `RESTARTING` を考慮 | 確認して必要なら対応 |
| `AgentContainerManagerTest` | `ContainerState` のモックに `status` が必要 | 既存モックに `every { status }` を追加 |
| 一覧画面の表示速度 | Docker 呼び出しが 1 回増える | `listContainers` 1 回のみ。連携数に依存しない |

---

## 6. テスト設計

### 6.1 `AgentContainerManagerTest`（追加）

- `status` が `restarting` のとき `RESTARTING` を返す
- `status` が `running` のとき `RUNNING` を返す
- `status` が `exited` のとき `STOPPED` を返す
- `status` が `paused` / `dead` のとき `STOPPED` を返す（`RUNNING` と誤判定しない）
- `status` が取れないとき `running` 真偽にフォールバックする
- `restartCount` を `ContainerInfo` に載せる
- `statusesBySyncName` がコンテナ名の接頭辞を剥がして連携名をキーにする
- `statusesBySyncName` が Docker 呼び出しの失敗時に空マップを返す

### 6.2 手動確認

#19 で停止した連携（`exited`）と正常な連携（`running`）が一覧で区別できることを確認する。

| 連携 | 期待 |
|---|---|
| ProductPlant / Product / GoogleSheetOpportunitySample / bcartorders | 停止中 |
| Categories / BCartCustomers / AccountHistory | 稼働中 |

再起動ループの表示は、#19 の自動停止があるため通常は発生しない。
`AUTO_STOP_AUTH_FAILED_AGENTS=false` で起動して確認する。

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | §4.1 | §6.1 |
| AC-2 | §4.1 | §6.1 |
| AC-3 | §4.1（`else -> STOPPED`） | §6.1 |
| AC-4 | §3.1 `restartCount` | §6.1 |
| AC-5 | §4.2 の Agent 列 | §6.2 |
| AC-6 | §4.3 `failing` バッジ | §6.2 |
| AC-7 | §4.4 | §6.2 |
| AC-8 | §4.2（Adapter 列は変更なし） | §6.2 |
| AC-9 | D-5 | §6.2 |
| AC-10 | D-2 | §6.2（Docker 呼び出し 1 回） |
| AC-11 | D-6 | §6.1 |
| AC-12 | §6.1 | — |
| AC-13 | — | `./gradlew test` |
