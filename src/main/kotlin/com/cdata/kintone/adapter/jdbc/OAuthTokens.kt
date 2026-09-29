package com.cdata.kintone.adapter.jdbc

/**
 * `GetOAuthAccessToken` が ResultSet で返すトークン。
 *
 * **アクセストークンは保存しない。** 短命で、リフレッシュトークンを接続設定に
 * 入れておけば `InitiateOAuth=REFRESH` でドライバーが自動更新する。
 * ここで受け取るのは、取得できたかどうかを判定するため。
 *
 * 関連: [Issue #34](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/34)
 */
data class OAuthTokens(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: String?,
) {
    companion object {
        const val ACCESS_TOKEN_COLUMN = "OAuthAccessToken"
        const val REFRESH_TOKEN_COLUMN = "OAuthRefreshToken"
        const val EXPIRES_IN_COLUMN = "ExpiresIn"

        /**
         * ResultSet の 1 行（列名 → 値）からトークンを読む。
         *
         * 列名は**大文字小文字を無視**して探す。300+ のデータソースを扱うため、
         * ドライバーによる綴りの揺れに備える。空文字・空白は未取得として扱う。
         */
        fun from(row: Map<String, String?>): OAuthTokens {
            val normalized = row.entries.associate { (key, value) -> key.lowercase() to value }
            fun valueOf(column: String) = normalized[column.lowercase()]?.trim()?.takeIf { it.isNotEmpty() }
            return OAuthTokens(
                accessToken = valueOf(ACCESS_TOKEN_COLUMN),
                refreshToken = valueOf(REFRESH_TOKEN_COLUMN),
                expiresIn = valueOf(EXPIRES_IN_COLUMN),
            )
        }
    }
}
