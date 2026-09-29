package com.cdata.kintone.adapter.jdbc

/**
 * OAuth 認可 URL をログに出すためのマスク。
 *
 * 認可 URL のクエリ文字列には `client_id` が含まれる。URL 全体を出さないと
 * 障害調査の手掛かりが無くなるため、**クエリだけを落として**ホストとパスは残す。
 *
 * verifier（認可コード）はマスクせず**一切ログに出さない**方針なので、
 * ここでは扱わない。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
object OAuthUrlMasker {

    const val MASK = "***"

    /** クエリ文字列を [MASK] に置き換える。クエリが無ければそのまま返す。 */
    fun maskQuery(url: String): String {
        val separator = url.indexOf('?')
        return if (separator < 0) url else url.substring(0, separator + 1) + MASK
    }

    /**
     * メッセージ中の認可コードを伏せる。
     *
     * プロシージャの引数は SQL リテラルに埋め込むため、**ドライバーの例外メッセージに
     * SQL がそのまま含まれると認可コードが画面とログに漏れる**。
     * 例外を利用者に見せる前にこれを通す。
     */
    fun redactVerifier(message: String?, verifier: String): String? {
        if (message == null || verifier.isBlank()) return message
        return message.replace(verifier, MASK)
    }
}
