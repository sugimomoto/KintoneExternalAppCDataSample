# リダイレクト URL の貼り付け対応 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#57](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/57) |
| 作成日 | 2026-09-30 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **URL と素のコードの両方を受け付ける** | URL を貼れるようにしつつ、既に手元にコードがある使い方も壊さない（AC-3） | URL 専用にする案 → 既存の使い方が使えなくなる |
| D-2 | 判定は「`code=` を含むか」だけで行う | URL としてパースできるかに依存させない。利用者は前後に空白や引用符を付けて貼ることがある | `URI.create()` でパースする案 → 壊れた入力で例外になり、素のコードも URL として解釈できない |
| D-3 | 抽出結果を **sealed interface** で返す | 「コードが取れた」「`error=` だった」「何も取れなかった」を型で分ける。ビューが分岐を書き忘れられない | `String?` を返す案 → `error=` の内容を伝えられない（AC-6） |
| D-4 | `+` を**空白に変換しない** | OAuth の認可コードに `+` が含まれる場合に壊れる（C-3）。`URLDecoder` は `+` を空白にするため、デコード前に `%2B` へ退避する | `URLDecoder.decode` をそのまま使う案 |
| D-5 | 抽出は `jdbc` パッケージの純粋関数にする | DB もドライバーも要らずテストできる（C-1）。OAuth の知識は `jdbc` 側に集まっている（`OAuthCapability` 等） | ルートにインラインで書く案 |
| D-6 | **入力値をログに出さない** | URL ごと認可コードを含む（C-4, AC-8）。既存の #12 の方針を維持 | デバッグのため出す案 |
| D-7 | `error=` の値はそのまま見せる | プロバイダが返す値（`access_denied` 等）で、分類してもロケール依存の問題が出る（#19 の方針） | エラーコードを日本語に対訳する案 |

### 1.2 フロー

```
POST /connections/{name}/oauth/token
  → 入力を trim
  → AuthorizationCodeExtractor.extract(input)
      ├─ Code(value)    → プロシージャに渡す（従来の処理）
      ├─ Error(value)   → 「認可が拒否されました: <値>」を表示 (AC-6)
      └─ Empty          → 「認可コードまたはリダイレクト先 URL を入力してください」
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/AuthorizationCodeExtractor.kt` | 新規 | 入力から `code` / `error` を取り出す |
| `web/routes/ConnectionOAuthRoutes.kt` | 変更 | 抽出結果で分岐する |
| `web/views/ConnectionOAuthView.kt` | 変更 | ラベルと案内文 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/AuthorizationCodeExtractorTest.kt` | 新規 | 実際に観測された URL 形式を中心に |

---

## 3. データ構造

```kotlin
/**
 * 認可コード入力の解釈結果。
 *
 * `error=` を伝えるために型で分ける。`String?` では「認可を拒否された」ことを
 * 呼び出し側に渡せない。
 */
sealed interface AuthorizationCodeInput {
    data class Code(val value: String) : AuthorizationCodeInput
    data class Error(val value: String) : AuthorizationCodeInput
    data object Empty : AuthorizationCodeInput
}
```

---

## 4. 実装

### 4.1 抽出

```kotlin
object AuthorizationCodeExtractor {

    /**
     * 入力から認可コードを取り出す。
     *
     * リダイレクト先 URL をそのまま貼れるようにするため、`code=` を含む入力からは
     * その値を取り出す。含まれなければ入力全体を認可コードとして扱う（従来の
     * 使い方を壊さない）。
     *
     * **URL としてパースしない。** 利用者は前後に空白や引用符を付けて貼ることがあり、
     * また素のコードは URL として解釈できない。`code=` の検出だけで判断する。
     *
     * 入力は認可コードを含むためログに出さないこと (Issue #12)。
     */
    fun extract(input: String): AuthorizationCodeInput {
        val trimmed = input.trim().trim('"', '\'')
        if (trimmed.isEmpty()) return AuthorizationCodeInput.Empty

        queryValue(trimmed, "code")?.let { return AuthorizationCodeInput.Code(it) }
        queryValue(trimmed, "error")?.let { return AuthorizationCodeInput.Error(it) }
        return AuthorizationCodeInput.Code(trimmed)
    }

    /** `?` / `&` に続く `<name>=<値>` を次の `&` または末尾まで取り出して復号する。 */
    private fun queryValue(input: String, name: String): String? =
        Regex("""[?&]${Regex.escape(name)}=([^&]*)""")
            .find(input)
            ?.groupValues
            ?.get(1)
            ?.let { decode(it) }
            ?.takeIf { it.isNotEmpty() }

    /**
     * パーセントエンコードを解く。
     *
     * `+` は**空白に変換しない**。`URLDecoder` はフォームエンコードの規則で `+` を
     * 空白にするが、OAuth の認可コードに `+` が含まれる場合に壊れるため、
     * デコード前に `%2B` へ退避する。
     */
    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8) }
            .getOrDefault(value)
}
```

`?` / `&` を前提にするのは、素のコードに `code=` という文字列が偶然含まれた場合に
誤検出しないため。

### 4.2 ルート側

```kotlin
val notice = when (val input = AuthorizationCodeExtractor.extract(raw.orEmpty())) {
    is AuthorizationCodeInput.Empty ->
        OAuthNotice(error = ErrorMessageTranslator.translate(
            "認可コード、またはリダイレクト先の URL を入力してください。"))

    is AuthorizationCodeInput.Error ->
        OAuthNotice(error = ErrorMessageTranslator.translate(
            "認可が拒否されました (${input.value})。Step 1 からやり直してください。"))

    is AuthorizationCodeInput.Code ->
        runCatching { completionNotice(ctx, name, config, input.value) }
            .getOrElse { failureNotice(it, "トークンの取得に失敗しました") }
}
```

### 4.3 ビュー

```
ラベル:  認可コード、またはリダイレクト先の URL:
案内:    リダイレクト後のアドレスバーの URL をそのままコピーして貼り付けてください。
         code= の部分は自動で取り出します。
```

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 認可完了の入力 | URL でもコードでも受け付ける | AC-1, AC-3 |
| 認可拒否時 | 理由が画面に出る（従来はコード不正として扱われた） | AC-6 |
| ログ | 入力値を出さない。**変更なし** | AC-8 |
| `OAuthAuthorizer` / `OAuthProcedureSql` | **変更なし** | C-6 |
| 例外メッセージのマスク | 抽出後のコードで `redactVerifier` を通す（既存動作） | C-5 |
| `docs/` | 影響なし | — |

---

## 6. テスト設計

### 6.1 `AuthorizationCodeExtractorTest`

**実際に観測された URL** を中心に置く（C-2）。

- Google のリダイレクト URL（`iss=` が前、`scope=` が後ろ）から抽出できる（AC-2）
- `code` が末尾にある URL から抽出できる
- 素の認可コードはそのまま返る（AC-3）
- パーセントエンコードが解かれる（`%2F` → `/`）（AC-4）
- `+` が空白にならない（AC-5）
- `error=access_denied` を含む URL は `Error` になる（AC-6）
- 空文字・空白は `Empty`
- 前後の引用符が取り除かれる
- `code=` を含まない素のコードに `code` という文字列が含まれても誤検出しない
- `code=` が空の URL は `Code` にならない

### 6.2 実機確認

1. 実際のリダイレクト URL 形式を貼って `code` が抽出されること（認可は使い切りのため、抽出結果の確認まで）
2. ラベルと案内文が変わっていること（AC-7）
3. `error=` を含む URL で拒否メッセージが出ること（AC-6）
4. ログに入力値が出ていないこと（AC-8）

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 | D-1, D-2, §4.1 | §6.1, §6.2-1 |
| AC-3 | D-1 | §6.1 |
| AC-4 / AC-5 | D-4 | §6.1 |
| AC-6 | D-3, D-7, §4.2 | §6.1, §6.2-3 |
| AC-7 | §4.3 | §6.2-2 |
| AC-8 | D-6 | §6.2-4 |
| AC-9 | §6.1 | — |
| AC-10 | — | `./gradlew test detekt` |
