# データソース画面の動線改善 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#42](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/42), [#43](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/43) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 接続テスト結果の差し替え先を**全行共有のバナー 1 つ**にする | テーブルの外に出せばレイアウトが崩れない。#36 の削除拒否バナーと同じ位置で一貫する | 行ごとに結果セルを増やす案 → 列が増え、長いメッセージで崩れる問題は残る |
| D-2 | `hx-target` を**ボタンの外**に向ける | ボタンが差し替え対象から外れるため残り、再テストできる（AC-5）。現状はボタンごと消えていた | ボタンを毎回応答に含める案 → ハンドラがボタンの HTML を知ることになり、ビューと二重管理になる |
| D-3 | 結果メッセージに**接続名を含める** | 結果が行の隣に出なくなるため、どの接続のものか分からなくなる（AC-3） | 位置で示す案 → バナーが表から離れるので成立しない |
| D-4 | 成功・失敗で **`success-banner` / `warning-banner`** を出し分ける | 既存スタイルを流用でき CSS 追加が不要（AC-4） | テキストの記号（●）だけで区別する案 → 現状もそうだが弱い |
| D-5 | 実効 AuthScheme の解決を **`OAuthCapability.effectiveAuthScheme`** に切り出す | ビューと保存ハンドラの両方で必要（C-3）。ビューのインライン実装を除く | 保存ハンドラにも同じ式を書く案 → 判定が 2 箇所に散り、片方だけ直す事故が起きる |
| D-6 | 既存の `requiresBrowserAuthorization(String?)` は**残す** | #12 のテストが依存している（C-4）。実効値を解決する責務は別関数に分ける | 既存関数のシグネチャを変える案 |
| D-7 | 新規保存時のみ**ウィザードへリダイレクト**する | ウィザードは保存済み設定の読み書きが前提（#34）。名前が確定した直後が唯一の自然な合流点 | 新規フォームにボタンを足す案 → AuthScheme は htmx の再描画で変わり、描画時点の判定がずれる |
| D-8 | 編集保存は**従来どおり一覧へ** | 編集画面には既にバナーがあり押せば行ける。保存のたびに飛ばされるのは煩わしい（AC-11） | 編集でも飛ばす案 |

### 1.2 フロー

```
[接続テスト] (#42)
  行のボタン → hx-post /connections/{name}/test
    → 応答（断片）が #conn-test-result（テーブルの上）に入る
    → ボタンは残るので再テストできる
    → 別の行を押すと同じバナーが置き換わる

[新規保存] (#43)
  POST /connections
    → 保存
    → 実効 AuthScheme = URL の値 ?: ドライバーの既定値
        ├─ ブラウザ認可が必要 → /connections/{name}/oauth へ
        └─ それ以外           → /connections へ（従来どおり）

[編集保存] POST /connections/{name} → /connections へ（変更なし）
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/OAuthCapability.kt` | 変更 | `effectiveAuthScheme` と、それを使う `requiresBrowserAuthorization(url, default)` を追加 |
| `web/views/ConnectionsView.kt` | 変更 | 結果バナーの差し替え先を追加。`hx-target` 変更。実効 AuthScheme を共有関数に置き換え |
| `web/routes/ConnectionsRoutes.kt` | 変更 | 接続テスト応答に接続名とバナークラス。新規保存のリダイレクト分岐 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/OAuthCapabilityTest.kt` | 変更 | 実効 AuthScheme の解決（URL 優先 / 既定値フォールバック / 両方無し） |

---

## 3. 実装

### 3.1 実効 AuthScheme の解決

```kotlin
/**
 * 実効の `AuthScheme`。接続文字列に明示された値を優先し、無ければ [authSchemeDefault]。
 *
 * #27 以降、既定値は接続文字列に保存されないため未指定が普通に起こる。
 */
fun effectiveAuthScheme(jdbcUrl: String, authSchemeDefault: String?): String? =
    authSchemeOf(jdbcUrl) ?: authSchemeDefault?.trim()?.takeIf { it.isNotEmpty() }

/** 実効値を解決してからブラウザ認可の要否を判定する。 */
fun requiresBrowserAuthorization(jdbcUrl: String, authSchemeDefault: String?): Boolean =
    requiresBrowserAuthorization(effectiveAuthScheme(jdbcUrl, authSchemeDefault))
```

ビュー側の抽出も共有する。`ConnectionPropertiesResult` に依存させたくないので、
既定値の取り出しはビュー／ルート側の小さなヘルパに留める。

```kotlin
// AuthScheme の既定値を取り出す。ビューとルートで同じ形を使う。
private fun authSchemeDefaultOf(properties: ConnectionPropertiesResult?): String? =
    properties?.properties
        ?.firstOrNull { it.propertyName.equals("AuthScheme", ignoreCase = true) }
        ?.defaultValue
```

### 3.2 結果バナーの差し替え先

`blockedDeleteNotice` の直後、テーブルより上に置く。

```kotlin
blockedDelete?.let { blocked -> blockedDeleteNotice(blocked) }

// 接続テスト結果の差し替え先。テーブルの外に置く。操作列に入れると
// 長いメッセージでレイアウトが崩れる (Issue #42)。
div { attributes["id"] = CONNECTION_TEST_RESULT_ID }
```

ボタン側:

```kotlin
button(type = ButtonType.button, classes = "secondary outline") {
    attributes["hx-post"] = "/connections/$name/test"
    attributes["hx-target"] = "#$CONNECTION_TEST_RESULT_ID"
    attributes["hx-disabled-elt"] = "this"
    +"接続テスト"
    span(classes = "htmx-indicator") { +" 実行中…" }
}
```

`div(classes = "inline-form")` のラッパは不要になるので外す。
`htmx-indicator` は htmx が**要求元の要素**に `htmx-request` を付けるため、
target を変えても効く。

### 3.3 応答の断片

```kotlin
val html = createHTML().article(classes = if (result.isSuccess) "success-banner" else "warning-banner") {
    p {
        code { +name }          // どの接続の結果か (AC-3)
        if (result.isSuccess) {
            +" 接続成功: ${result.getOrNull()}"
        } else {
            // 例外メッセージに接続文字列が含まれる場合があるため必ずマスクを通す。
            // メッセージはロケール依存なので分類はしない (#19 の方針)。
            +" 接続失敗: ${ConnectionStringMasker.mask(result.exceptionOrNull()?.message ?: "")}"
        }
    }
}
```

`article` を返すので、差し替え先の `div` の中にバナーが入る。

### 3.4 新規保存のリダイレクト

```kotlin
ctx.configSource.saveSharedJdbcConfig(name, config)
// OAuth 接続は保存しただけでは使えない。認可が必要な次の一歩へそのまま繋ぐ。
// ウィザードは保存済み設定の読み書きを前提にしているため、名前が確定した
// この時点が唯一の合流点 (Issue #43)。
val authSchemeDefault = authSchemeDefaultOf(ctx.connectionPropertyInspector.fetchProperties(driverClass, jarFilename))
if (OAuthCapability.requiresBrowserAuthorization(url, authSchemeDefault)) {
    call.respondRedirect("/connections/$name/oauth")
} else {
    call.respondRedirect("/connections")
}
```

`fetchProperties` はキャッシュ済み（`invalidateCache` があることから明らか）なので
保存のたびに接続を張ることはない。

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 接続テストの表示位置 | 操作列 → テーブル上のバナー | 意図した変更（AC-1, AC-2） |
| 接続テストの応答形式 | 断片のまま。`div` → `article` にクラスが付く | C-1 を満たす |
| 再テスト | できるようになる（現状は不可） | AC-5 |
| 新規保存のリダイレクト先 | OAuth 接続のみウィザードへ | AC-8。それ以外は不変（AC-9） |
| 編集保存 | **変更なし** | AC-11 |
| 編集画面の OAuth バナー | 判定を共有関数に置き換えるが結果は同じ | AC-12 |
| `requiresBrowserAuthorization(String?)` | **シグネチャ不変**。オーバーロードを追加するだけ | C-4 |
| `app.css` | **変更不要**（`success-banner` / `warning-banner` は既存） | — |
| `docs/` | 影響なし | — |

---

## 5. テスト設計

### 5.1 `OAuthCapabilityTest`（追加）

- URL に `AuthScheme` があればそれを使う（既定値より優先）
- URL に無ければ既定値を使う
- 両方無ければ null
- 既定値が空文字・空白なら null
- URL の値が OAuth なら `requiresBrowserAuthorization(url, default)` が true
- URL に無く既定値が OAuth なら true（AC-10）
- URL に無く既定値が Basic なら false（AC-9）
- 既存の `requiresBrowserAuthorization(String?)` のテストが通り続ける（C-4）

### 5.2 実機確認

1. 接続テストの結果がテーブルの上に出て、操作列に入らない（AC-1, AC-2）
2. 結果に接続名が含まれ、成功・失敗でクラスが変わる（AC-3, AC-4）
3. 結果表示後もボタンが残る（AC-5）
4. 別の接続をテストするとバナーが置き換わる（AC-6）
5. 失敗メッセージがマスクされる（AC-7）
6. `AuthScheme=OAuth` の接続を新規保存 → ウィザードへ遷移（AC-8）
7. `AuthScheme=Basic` の接続を新規保存 → 一覧へ（AC-9）
8. 編集保存 → 一覧へ（AC-11）
9. 検証で作った接続を片付け、`config.db` をバックアップと突き合わせる

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 | D-1, §3.2 | §5.2-1 |
| AC-3 / AC-4 | D-3, D-4, §3.3 | §5.2-2 |
| AC-5 | D-2 | §5.2-3 |
| AC-6 | D-1 | §5.2-4 |
| AC-7 | §3.3 | §5.2-5 |
| AC-8 / AC-10 | D-7, §3.4 | §5.1, §5.2-6 |
| AC-9 | §3.4 | §5.1, §5.2-7 |
| AC-11 | D-8 | §5.2-8 |
| AC-12 | D-5（結果は不変） | §5.2 全体 |
| AC-13 | §5.1 | — |
| AC-14 | — | `./gradlew test detekt` |
