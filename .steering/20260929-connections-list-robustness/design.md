# データソース一覧の堅牢性改善 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#39](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/39), [#40](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/40) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 読み込み失敗を **`ConnectionRow` という sealed interface** で表す | 「読めた」「読めない」を型で分け、ビューが失敗ケースを書き忘れられないようにする | `JdbcConfig?` の null で表す案 → 「未登録」と「壊れている」を区別できず、AC-2 の表示が書けない |
| D-2 | 読み込みを**ラムダで受け取る**（`load: () -> JdbcConfig?`） | DB に触らずテストできる（AC-11）。`ConfigSource` は変更しない（C-1） | `ConfigSource` に失敗許容版の API を足す案 → インターフェースが増え、静かに失敗する経路を作ることになる |
| D-3 | `loadSharedJdbcConfig` 自体は**変更しない** | 設定の読み込みが静かに失敗するのは危険（C-2）。捕捉するのは画面描画という「落ちてはいけない」文脈に限る | 実装側で例外を握って null を返す案 → 連携の起動経路でも壊れた設定が見逃される |
| D-4 | 読めない行にも**削除ボタンを出す** | 画面から復旧できる導線を残す（AC-3）。削除は `name` だけで足り、設定の中身を必要としない | 読めない行は操作不可にする案 → DB を直接触るしかなくなる |
| D-5 | 接続テストを **htmx の `hx-post` + `hx-target`** にする | ハンドラは既に HTML 断片を返しており、htmx 側を合わせるだけで済む（C-3）。`DriversView` のライセンス検証と同じ流儀（C-5） | ハンドラをページ全体を返す形に変える案 → 断片の利点を捨て、テスト結果で一覧を再描画することになる |
| D-6 | 実行中表示は htmx 内蔵の **`htmx-indicator`** を使う | htmx 自身が `.htmx-indicator{opacity:0}` / `.htmx-request .htmx-indicator{opacity:1}` を注入するため、CSS 追加が不要 | 独自のスピナー CSS を書く案 |
| D-7 | 二重送信は **`hx-disabled-elt="this"`** で防ぐ | 接続テストは OAuth 絡みでタイムアウトまで数十秒かかることがあり、連打されやすい | 何もしない案 |
| D-8 | 失敗メッセージは **`ConnectionStringMasker.mask`** を通す | 例外メッセージに接続文字列が含まれた場合の漏洩を防ぐ（C-4）。分類はしない（C-6） | `ErrorMessageTranslator` で分類する案 → ロケール依存メッセージは分類しない方針（#19） |

### 1.2 フロー

```
[一覧描画]
  各行: ConnectionRow.load { configSource.loadSharedJdbcConfig(name) }
      ├─ Loaded(config)      → 従来どおり（ドライバークラス / マスク済み URL / 全操作）
      └─ Unreadable(reason)  → 「読み込めません」＋ 理由、操作は削除のみ (AC-2, AC-3)

[接続テスト] hx-post → 断片が #conn-test-<name> に入る（画面遷移なし: AC-6, AC-7）
             実行中は htmx-indicator が見え、ボタンが無効化される (AC-8)
             行ごとに target が違うので結果が混ざらない (AC-9)
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/ConnectionRow.kt` | 新規 | 読み込み結果を表す sealed interface と `load` |
| `web/views/ConnectionsView.kt` | 変更 | `connectionRow` が `ConnectionRow` を受ける。テストボタンを htmx 化 |
| `web/routes/ConnectionsRoutes.kt` | 変更 | 失敗メッセージをマスク。KDoc を実態に合わせる |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/ConnectionRowTest.kt` | 新規 | 読めた / 例外 / null の分岐 |
| `jdbc/ConnectionStringMaskerTest.kt` | 変更 | 例外メッセージ中の接続文字列がマスクされること |

---

## 3. データ構造

```kotlin
/**
 * 一覧 1 行分の設定読み込み結果。
 *
 * `config_json` が壊れていると `loadSharedJdbcConfig` が例外を投げ、
 * そのままでは一覧画面全体が 500 になる。行単位で失敗を閉じ込める。
 */
sealed interface ConnectionRow {
    data class Loaded(val config: JdbcConfig) : ConnectionRow
    data class Unreadable(val reason: String) : ConnectionRow

    companion object {
        fun load(load: () -> JdbcConfig?): ConnectionRow
    }
}
```

`null`（名前はあるが設定が無い）も `Unreadable` にまとめる。一覧に名前が出ている以上、
設定が引けないのは異常であり、利用者にとっては「読めない」と同じ。

---

## 4. 実装

### 4.1 読み込みの失敗許容

```kotlin
fun load(load: () -> JdbcConfig?): ConnectionRow =
    runCatching { load() }.fold(
        onSuccess = { config ->
            config?.let { Loaded(it) } ?: Unreadable("設定が見つかりません")
        },
        // 例外の型で分類しない。壊れ方は JSON の構文エラー・必須フィールド欠落など
        // 一通りではなく、いずれも利用者の対処は同じ（作り直すか削除する）。
        onFailure = { cause -> Unreadable(cause.message ?: cause::class.simpleName ?: "読み込めません") },
    )
```

`runCatching` は `Throwable` を捕まえるため、`CancellationException` も飲み込む点に注意が必要だが、
この呼び出しは suspend しない同期処理なので問題にならない。

### 4.2 接続テストの htmx 化

```kotlin
td(classes = "cell-actions") {
    div {
        attributes["id"] = "conn-test-$name"
        button(type = ButtonType.button, classes = "secondary outline") {
            attributes["hx-post"] = "/connections/$name/test"
            attributes["hx-target"] = "#conn-test-$name"
            attributes["hx-disabled-elt"] = "this"
            +"接続テスト"
            span(classes = "htmx-indicator") { +" 実行中…" }
        }
    }
    ...
}
```

`hx-target` を自分の親 `div` にすることで、応答の断片がボタンごと置き換わる。
結果表示後は再テストできないが、再読み込みで戻る。`DriversView` のライセンス検証と同じ挙動。

### 4.3 失敗メッセージのマスク

```kotlin
p { +"●Failed: ${ConnectionStringMasker.mask(result.exceptionOrNull()?.message ?: "")}" }
```

`ConnectionStringMasker.mask` は `Name=Value` の形を拾って機密名の値を伏せる。
例外メッセージに接続文字列が混ざっていても値が出ない。

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| `/connections` | 壊れた行があっても 200 で表示される | 意図した変更（AC-1） |
| #36 の削除拒否画面 | 同じ `connectionsListView` を通るため同時に直る | AC-5 |
| 接続テストの応答形式 | **変更なし**（HTML 断片のまま） | C-3 |
| `loadSharedJdbcConfig` の呼び出し元（連携の起動経路等） | **変更なし**。例外はそのまま伝播する | C-2, D-3 |
| `ConfigSource` とその実装 | **変更なし** | C-1 |
| `app.css` | **変更不要**（htmx が indicator の CSS を注入する） | D-6 |
| `docs/` | 影響なし | 基本設計・データモデルの変更を伴わない |

---

## 6. テスト設計

### 6.1 `ConnectionRowTest`

- 設定が読めれば `Loaded` になり、中身が保持される
- 例外が出れば `Unreadable` になり、例外メッセージが理由に入る
- メッセージが無い例外でも理由が空にならない
- `null` が返れば `Unreadable` になる
- 例外の型で分岐しない（`MissingFieldException` 相当でも `SerializationException` 相当でも同じ扱い）

### 6.2 `ConnectionStringMaskerTest`（追加）

- 例外メッセージに埋め込まれた接続文字列の機密値がマスクされる
- 接続文字列を含まないメッセージはそのまま残る

### 6.3 実機確認

1. `config_json` を壊した行を仕込み、`/connections` が 200 で表示される（AC-1, AC-2, AC-4）
2. 壊れた行を画面から削除できる（AC-3）
3. 壊れた行がある状態で削除拒否画面が 200 になる（AC-5）
4. 接続テストの応答が断片で、`hx-target` が行内を指している（AC-6, AC-7, AC-8, AC-9）
5. 検証後に仕込んだ行を削除し、`config.db` をバックアップと突き合わせる

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 / AC-4 | D-1, §4.1 | §6.1, §6.3-1 |
| AC-3 | D-4 | §6.3-2 |
| AC-5 | §5 | §6.3-3 |
| AC-6 / AC-7 / AC-9 | D-5, §4.2 | §6.3-4 |
| AC-8 | D-6, D-7 | §6.3-4 |
| AC-10 | D-8, §4.3 | §6.2 |
| AC-11 | §6.1, §6.2 | — |
| AC-12 | — | `./gradlew test detekt` |
