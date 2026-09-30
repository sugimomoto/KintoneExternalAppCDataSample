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
     * 実効の `AuthScheme`。接続文字列に明示された値を優先し、無ければ [authSchemeDefault]。
     *
     * #27 以降、既定値は接続文字列に保存されないため未指定が普通に起こる。
     * 「URL の値 → ドライバーの既定値」という解決を**ここに一本化**する。
     * 経路ごとに書くと、画面の導線と保存時の判定が食い違う (Issue #43)。
     */
    fun effectiveAuthScheme(jdbcUrl: String, authSchemeDefault: String?): String? =
        authSchemeOf(jdbcUrl) ?: authSchemeDefault?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 実効値を解決してからブラウザ認可の要否を判定する。
     *
     * [authSchemeDefault] はドライバーが `sys_connection_props` で返す `AuthScheme` の既定値。
     */
    fun requiresBrowserAuthorization(jdbcUrl: String, authSchemeDefault: String?): Boolean =
        requiresBrowserAuthorization(effectiveAuthScheme(jdbcUrl, authSchemeDefault))

    /**
     * 接続文字列から `AuthScheme` の値を取り出す。明示されていなければ null。
     *
     * 区切りは `;` だけでなく `:` も見る。CData の接続文字列は
     * `jdbc:<product>:<最初のプロパティ>=...` の形で、最初に書かれた場合は
     * 直前が `:` になる。
     */
    fun authSchemeOf(jdbcUrl: String): String? = propertyOf(jdbcUrl, "AuthScheme")

    /**
     * CData の組み込み OAuth アプリが受け付けるローカルコールバック。
     *
     * ポートは組み込みアプリ側に登録されている値なので変更できない。
     * 認可後この URL に飛ぶとブラウザは接続エラーになるが、クエリに `code=` が
     * 付くので値をコピーできる。
     */
    const val DEFAULT_CALLBACK_URL = "http://localhost:33333"

    /**
     * 認可とトークン交換に渡す実効コールバック。設定値があればそれ、無ければ [DEFAULT_CALLBACK_URL]。
     *
     * **未設定でもドライバー既定値に委ねない。** 既定値はプロバイダごとに違い、
     * Google 系は Google が廃止した OOB (`urn:ietf:wg:oauth:2.0:oob`) になるため
     * `invalid_request` で認可できない (Issue #55)。
     *
     * [DEFAULT_CALLBACK_URL] を渡せばドライバーがプロバイダごとに適切な形へ変換する。
     * Salesforce 系は `oauth.cdata.com` + `state=base64(localhost)` になり、
     * **未指定時の結果と完全に一致する**ため回帰しない（実機で確認済み）。
     * なお `oauth.cdata.com` を明示すると両プロバイダとも拒否される。
     *
     * `GetOAuthAccessToken` は認可時と同じ `CallbackUrl` を要求するため、
     * **認可とトークン交換の両方がこの関数を通ること。**
     */
    fun effectiveCallbackUrl(jdbcUrl: String): String =
        callbackUrlOf(jdbcUrl) ?: DEFAULT_CALLBACK_URL

    /**
     * 接続文字列から `CallbackURL` を取り出す。明示されていなければ null。
     *
     * 未指定の場合はドライバーの既定値（Salesforce なら `http://localhost:33333`）が
     * 使われる。認可後のリダイレクト先がそこになり、ブラウザは接続エラーになるが、
     * URL のクエリに `code=` が付くので値はコピーできる。
     */
    fun callbackUrlOf(jdbcUrl: String): String? = propertyOf(jdbcUrl, "CallbackURL")

    /**
     * 接続文字列から任意のプロパティ値を取り出す。
     *
     * 区切りは `;` だけでなく `:` も見る。CData の接続文字列は
     * `jdbc:<product>:<最初のプロパティ>=...` の形で、最初に書かれた場合は
     * 直前が `:` になる。
     */
    private fun propertyOf(jdbcUrl: String, propertyName: String): String? =
        Regex("""(?i)(?:^|[;:])\s*$propertyName\s*=\s*([^;]*)""")
            .find(jdbcUrl)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
