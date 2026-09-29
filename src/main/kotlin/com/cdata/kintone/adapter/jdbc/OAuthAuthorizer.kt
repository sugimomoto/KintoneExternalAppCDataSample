package com.cdata.kintone.adapter.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.SQLException

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
     * verifier からトークンを取得して返す。
     *
     * **戻り値を必ず保存すること。** プロシージャを実行しただけでは
     * `OAuthSettingsLocation` のキャッシュは作られない。取得したリフレッシュトークンを
     * 接続設定に書き戻す必要がある (Issue #34)。
     *
     * **verifier はログに出さない**（認可コードそのもの）。
     * トークン値もログに出さない。
     */
    fun fetchAccessToken(verifier: String, callbackUrl: String?): OAuthTokens {
        val tokens = try {
            connection.createStatement().use { statement ->
                statement.execute(OAuthProcedureSql.accessToken(verifier, callbackUrl))
                statement.resultSet?.use { rs -> readTokens(rs) } ?: OAuthTokens(null, null, null)
            }
        } catch (e: SQLException) {
            // 引数は SQL リテラルに埋め込むため、ドライバーの例外に SQL が含まれると
            // 認可コードが画面とログに漏れる。伏せた上で投げ直す。
            throw SQLException(OAuthUrlMasker.redactVerifier(e.message, verifier), e.sqlState, e.errorCode, e)
        }
        log.info {
            "OAuth トークンを取得しました " +
                "(アクセストークン: ${tokens.accessToken != null}, リフレッシュトークン: ${tokens.refreshToken != null})"
        }
        return tokens
    }

    /** 先頭行を列名 → 値の Map として読む。列名の綴りの差は [OAuthTokens.from] が吸収する。 */
    private fun readTokens(rs: java.sql.ResultSet): OAuthTokens {
        if (!rs.next()) return OAuthTokens(null, null, null)
        val meta = rs.metaData
        val row = (1..meta.columnCount).associate { i -> meta.getColumnLabel(i) to rs.getString(i) }
        return OAuthTokens.from(row)
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
