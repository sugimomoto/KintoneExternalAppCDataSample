package com.cdata.kintone.adapter.jdbc

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 認可コードの入力を解釈する。
 *
 * リダイレクト先 URL をそのまま貼り付けられるようにするためのもの。`code=` は URL の
 * 途中にあり前後に他のパラメータが付くため、手作業での切り出しは手間で、範囲を誤ると
 * **認可コードは 1 回しか使えない**ためやり直しになる。
 *
 * 関連: [Issue #57](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/57)
 */
object AuthorizationCodeExtractor {

    /**
     * 入力から認可コードを取り出す。
     *
     * `code=` を含む入力からはその値を取り出し、含まなければ入力全体を認可コードとして
     * 扱う（既に手元にコードがある使い方を壊さない）。
     *
     * **URL としてパースしない。** 利用者は前後に空白や引用符を付けて貼ることがあり、
     * 素のコードは URL として解釈できない。区切り (`?` / `&`) に続く `code=` の
     * 検出だけで判断する。
     *
     * **入力値はログに出さないこと。** 認可コードそのものを含む (Issue #12)。
     */
    fun extract(input: String): AuthorizationCodeInput {
        val trimmed = input.trim().trim('"', '\'').trim()
        if (trimmed.isEmpty()) return AuthorizationCodeInput.Empty
        return fromQuery(trimmed) ?: AuthorizationCodeInput.Code(trimmed)
    }

    /**
     * クエリ文字列として解釈できる場合の結果。解釈できなければ null。
     *
     * `code` を `error` より優先する。認可が成功していれば `code` が付くため。
     */
    private fun fromQuery(input: String): AuthorizationCodeInput? =
        queryValue(input, "code")?.let { AuthorizationCodeInput.Code(it) }
            ?: queryValue(input, "error")?.let { AuthorizationCodeInput.Error(it) }

    /**
     * 区切り (`?` / `&`) に続く `<name>=<値>` を次の `&` または末尾まで取り出して復号する。
     *
     * 区切りを要求するのは、素のコードに `code=` という文字列が偶然含まれた場合に
     * 誤検出しないため。値が空なら null を返す。
     */
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
     * `+` は**空白に変換しない**。[URLDecoder] はフォームエンコードの規則で `+` を
     * 空白にするが、認可コードに `+` が含まれる場合に壊れるため、デコード前に
     * `%2B` へ退避する。
     *
     * 復号に失敗した場合は元の値を返す。不正なエスケープで入力全体を拒否するより、
     * そのまま渡してプロシージャ側のエラーに委ねた方が利用者に分かりやすい。
     */
    private fun decode(value: String): String =
        runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
        }.getOrDefault(value)
}
