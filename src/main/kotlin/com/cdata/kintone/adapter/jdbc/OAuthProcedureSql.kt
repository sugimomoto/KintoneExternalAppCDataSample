package com.cdata.kintone.adapter.jdbc

/**
 * CData ドライバーの OAuth ストアドプロシージャを呼ぶ SQL を組み立てる。
 *
 * ドライバーは `EXEC <Proc> Param = 'value'` 形式を受け付ける。
 * プレースホルダが使えないため**値を SQL リテラルに埋め込む**必要があり、
 * verifier のような外部由来の文字列はエスケープが必須。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
internal object OAuthProcedureSql {

    const val AUTHORIZATION_URL_PROCEDURE = "GetOAuthAuthorizationUrl"
    const val ACCESS_TOKEN_PROCEDURE = "GetOAuthAccessToken"

    /** 認可 URL を取得する SQL。出力は `Url` 列を持つ ResultSet。 */
    fun authorizationUrl(callbackUrl: String?): String =
        exec(AUTHORIZATION_URL_PROCEDURE, listOfNotNull(param("CallbackUrl", callbackUrl)))

    /** verifier からトークンを取得する SQL。 */
    fun accessToken(verifier: String, callbackUrl: String?): String =
        exec(
            ACCESS_TOKEN_PROCEDURE,
            listOfNotNull(param("Verifier", verifier), param("CallbackUrl", callbackUrl)),
        )

    private fun exec(procedure: String, params: List<String>): String =
        if (params.isEmpty()) "EXEC $procedure" else "EXEC $procedure ${params.joinToString(", ")}"

    private fun param(name: String, value: String?): String? =
        value?.takeIf { it.isNotBlank() }?.let { "$name = '${escapeSqlLiteral(it)}'" }

    /** SQL の単一引用符文字列として安全な形に変換する。 */
    fun escapeSqlLiteral(value: String): String = value.replace("'", "''")
}
