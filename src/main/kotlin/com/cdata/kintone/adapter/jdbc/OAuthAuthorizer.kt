package com.cdata.kintone.adapter.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection

private val log = KotlinLogging.logger {}

/**
 * ドライバーのヘッドレス向け OAuth プロシージャを実行する。
 *
 * 認可 URL の生成は**外部通信なし**でローカルに組み立てられるが、
 * トークン取得は実際にデータソースへリクエストを飛ばす。
 *
 * 呼び出し側は `InitiateOAuth=OFF` にした接続を渡すこと
 * （[JdbcUrlEnhancer.withInitiateOAuthOff]）。`GETANDREFRESH` のままだと
 * ドライバーが接続時にブラウザを開こうとして 60 秒タイムアウトする。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
class OAuthAuthorizer(private val connection: Connection) {

    /** ドライバーが認可プロシージャを持つか。SAP Gateway のように持たないものがある。 */
    fun hasAuthorizationProcedure(): Boolean =
        procedureNames().any { it.equals(OAuthProcedureSql.AUTHORIZATION_URL_PROCEDURE, ignoreCase = true) }

    /**
     * 認可 URL を生成する。
     * URL にはクライアント ID が含まれるため、ログにはクエリを落として出す。
     */
    fun authorizationUrl(callbackUrl: String?): String {
        val url = connection.createStatement().use { statement ->
            statement.execute(OAuthProcedureSql.authorizationUrl(callbackUrl))
            statement.resultSet?.use { rs ->
                if (rs.next()) rs.getString(URL_COLUMN) else null
            }
        }
        checkNotNull(url) { "認可 URL を取得できませんでした（ドライバーが $URL_COLUMN 列を返していません）" }
        log.info { "OAuth 認可 URL を生成しました: ${OAuthUrlMasker.maskQuery(url)}" }
        return url
    }

    /**
     * verifier からトークンを取得する。
     * 取得したトークンはドライバーが `OAuthSettingsLocation` のキャッシュに保存する。
     *
     * **verifier はログに出さない**（認可コードそのもの）。
     */
    fun fetchAccessToken(verifier: String, callbackUrl: String?) {
        connection.createStatement().use { statement ->
            statement.execute(OAuthProcedureSql.accessToken(verifier, callbackUrl))
        }
        log.info { "OAuth トークンを取得してキャッシュに保存しました" }
    }

    private fun procedureNames(): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(PROCEDURES_QUERY).use { rs ->
                buildList {
                    while (rs.next()) {
                        rs.getString("ProcedureName")?.let { add(it) }
                    }
                }
            }
        }

    private companion object {
        const val URL_COLUMN = "Url"
        const val PROCEDURES_QUERY = "SELECT ProcedureName FROM sys_procedures"
    }
}
