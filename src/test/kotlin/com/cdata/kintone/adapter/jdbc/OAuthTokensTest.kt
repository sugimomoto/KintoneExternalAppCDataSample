package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `GetOAuthAccessToken` の戻り値を解釈する [OAuthTokens] のテスト。
 *
 * 列名はドライバーによって綴りが揺れる可能性があるため、大文字小文字を無視して探す。
 *
 * 関連: [Issue #34](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/34)
 */
class OAuthTokensTest {

    @Test
    fun `from - 想定どおりの列名から読める`() {
        val tokens = OAuthTokens.from(
            mapOf(
                "OAuthAccessToken" to "at-1",
                "OAuthRefreshToken" to "rt-1",
                "ExpiresIn" to "3600",
            ),
        )
        assertEquals("at-1", tokens.accessToken)
        assertEquals("rt-1", tokens.refreshToken)
        assertEquals("3600", tokens.expiresIn)
    }

    @Test
    fun `from - 列名の大文字小文字が違っても読める`() {
        val tokens = OAuthTokens.from(
            mapOf(
                "oauthaccesstoken" to "at-2",
                "OAUTHREFRESHTOKEN" to "rt-2",
                "expiresin" to "7200",
            ),
        )
        assertEquals("at-2", tokens.accessToken)
        assertEquals("rt-2", tokens.refreshToken)
        assertEquals("7200", tokens.expiresIn)
    }

    @Test
    fun `from - リフレッシュトークンが無ければ null`() {
        val tokens = OAuthTokens.from(mapOf("OAuthAccessToken" to "at-3"))
        assertEquals("at-3", tokens.accessToken)
        assertNull(tokens.refreshToken)
    }

    @Test
    fun `from - 空文字や空白は null として扱う`() {
        val tokens = OAuthTokens.from(
            mapOf("OAuthAccessToken" to "", "OAuthRefreshToken" to "   "),
        )
        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
    }

    @Test
    fun `from - 空の行では全て null`() {
        val tokens = OAuthTokens.from(emptyMap())
        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.expiresIn)
    }
}
