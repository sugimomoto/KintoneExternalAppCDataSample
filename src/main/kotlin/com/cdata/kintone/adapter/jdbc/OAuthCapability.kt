package com.cdata.kintone.adapter.jdbc

/**
 * OAuth 認可ウィザードを出すべき接続かを判定する。
 *
 * 判定の軸は 2 つある。
 * 1. **`AuthScheme` がブラウザ認可を伴うか** — 本オブジェクトの純粋関数で判定する
 * 2. **ドライバーに認可プロシージャがあるか** — SAP Gateway には無いため、
 *    実際に接続して `sys_procedures` を引く必要がある ([OAuthAuthorizer])
 *
 * `OAuthPassword` / `OAuthJWT` / `GCPInstanceAccount` などはプロパティ入力だけで
 * 完結しブラウザ認可を必要としないため、ウィザードの対象にしない。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
object OAuthCapability {

    /**
     * ブラウザでの認可が必要な `AuthScheme` の値（小文字）。
     *
     * 同梱ドライバーが `sys_connection_props` の `Values` で返す値から、
     * 対話的な認可を伴うものだけを列挙している。
     */
    private val BROWSER_AUTH_SCHEMES = setOf(
        "oauth",
        "oauthclient",
        "oauthpkce",
        "azuread",
        "azureadpkce",
    )

    /**
     * ブラウザ認可が必要な認証方式か。
     *
     * [authScheme] には**実効値**を渡す。接続文字列に明示された値が無い場合は
     * ドライバーの既定値を解決してから渡すこと（#27 以降、既定値は接続文字列に
     * 保存されないため未指定が普通に起こる）。
     */
    fun requiresBrowserAuthorization(authScheme: String?): Boolean =
        authScheme?.trim()?.lowercase() in BROWSER_AUTH_SCHEMES

    /**
     * 接続文字列から `AuthScheme` の値を取り出す。明示されていなければ null。
     *
     * 区切りは `;` だけでなく `:` も見る。CData の接続文字列は
     * `jdbc:<product>:<最初のプロパティ>=...` の形で、最初に書かれた場合は
     * 直前が `:` になる。
     */
    fun authSchemeOf(jdbcUrl: String): String? =
        AUTH_SCHEME_REGEX.find(jdbcUrl)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private val AUTH_SCHEME_REGEX = Regex("""(?i)(?:^|[;:])\s*AuthScheme\s*=\s*([^;]*)""")
}
