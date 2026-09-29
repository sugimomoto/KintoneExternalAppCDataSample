# コンテナのタイムゾーン — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | container-timezone |
| 対応 Issue | [#25](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/25) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | `docker-compose.yml` で `TZ: ${TZ:-Asia/Tokyo}` を渡す | イメージが `TZ` に追従する（F-1）。既定値を compose に置けば、コードはタイムゾーンを知らなくて済む（C-1） | `/etc/localtime` をマウントする案 → ホスト OS に依存し、macOS の Docker Desktop では扱いが変わる |
| D-2 | Agent コンテナには **console 自身の `TZ`** を引き継ぐ | ログを並べて読むため揃える（C-2）。compose とコードで既定値を二重に持たない | Agent 用に別の設定を持つ案 |
| D-3 | `TZ` が未設定なら env を渡さない | 渡すべき値が無いときに空文字を渡すと、イメージ側の解決が壊れる恐れがある（AC-6） | 常に既定値を埋め込む案 → C-1 に反する |
| D-4 | 時刻表示にタイムゾーンを併記する | `TZ` の設定漏れや別環境での実行時に、どの zone の時刻か判別できるようにする（AC-5） | 表示を変えない案 → ずれていても気付けない |
| D-5 | `TZ` の解決を純粋関数として切り出す | env 依存の分岐をユニットテストするため | `ensureCreated` の中でインラインに書く案 → テストで Docker の builder チェーンを組む必要がある |

### 1.2 変更の全体像

```
docker-compose.yml (adapter-console)
  environment:
    TZ: ${TZ:-Asia/Tokyo}          ← D-1
        │
        ├─ logback の %d            → JST で出力 (F-4)
        ├─ ZoneId.systemDefault()   → 画面・CLI の時刻が JST
        └─ AgentContainerManager
             .withEnv("TZ=...")     ← D-2 / console の値を引き継ぐ
                  └─ Agent コンテナのログも JST

agent/docker-compose.yml (開発用の単体起動)
  environment:
    TZ: ${TZ:-Asia/Tokyo}
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `docker-compose.yml` | 変更 | adapter-console に `TZ` を追加 |
| `agent/docker-compose.yml` | 変更 | 開発用の単体 Agent に `TZ` を追加 |
| `agent/AgentContainerManager.kt` | 変更 | 作成するコンテナに `TZ` を引き継ぐ。解決を `timeZoneEnv` に切り出す |
| `web/views/DashboardView.kt` | 変更 | 時刻表示にタイムゾーンを併記 |
| `cli/ListActiveCommand.kt` | 変更 | 同上 |
| `README.md` | 変更 | `TZ` を環境変数として記載 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `agent/AgentContainerManagerTest.kt` | 変更 | `timeZoneEnv` の分岐（設定あり / 未設定 / 空文字） |

---

## 3. 実装

### 3.1 `timeZoneEnv`

```kotlin
/**
 * Agent コンテナに渡す `TZ` 環境変数。
 *
 * console 自身の `TZ` を引き継ぐ。ログを並べて読むため時刻を揃える必要があり、
 * かつ既定値を compose とコードで二重に持たないようにする。
 * 未設定なら何も渡さない（イメージ既定の UTC になる）。
 */
internal fun timeZoneEnv(timeZone: String?): List<String> =
    timeZone?.takeIf { it.isNotBlank() }?.let { listOf("TZ=$it") } ?: emptyList()
```

`ensureCreated` では `withEnv(timeZoneEnv(System.getenv("TZ")))` として使う。

### 3.2 時刻表示

```kotlin
private val FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss z")
    .withZone(ZoneId.systemDefault())
```

`TZ` を設定していれば `2026-09-29 15:32:29 JST`、未設定なら `... UTC` と出る。

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 既存の Agent コンテナ | `ensureCreated` は既存コンテナには手を出さない（`NOT_FOUND` のときだけ作る） | **再作成するまで `TZ` は反映されない**。README に明記する |
| console のログ | タイムスタンプが JST になる。既存のログファイルとは混在する | 運用上の影響のみ |
| `ListActiveCommand` の出力幅 | `started=` が 4 文字ぶん伸びる | 固定幅の列ではないため問題なし |
| E2E テスト | 時刻表示に依存していないか確認 | 確認する |
| 時刻表示のテスト | `ZoneId.systemDefault()` に依存するためユニットテストしにくい | `timeZoneEnv` のみテストし、表示は実機で確認する |

---

## 5. テスト設計

### 5.1 `AgentContainerManagerTest`（追加）

- `TZ` が設定されていれば `TZ=<値>` を返す
- `TZ` が未設定なら空リストを返す
- `TZ` が空文字なら空リストを返す

### 5.2 実機確認

| 確認項目 | 期待 |
|---|---|
| `docker exec adapter-console date` | JST |
| console のログのタイムスタンプ | JST |
| 連携詳細の検知時刻 | `... JST` |
| ダッシュボードの開始時刻 | `... JST` |
| Agent コンテナを作り直したときの `date` | JST |

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1 | §5.2 |
| AC-2 | D-1 (`${TZ:-Asia/Tokyo}`) | §5.2 |
| AC-3 | D-1 + F-4 | §5.2 |
| AC-4 | D-2 | §5.1, §5.2 |
| AC-5 | D-4 | §5.2 |
| AC-6 | D-3 | §5.1 |
| AC-7 | — | `./gradlew test` |
