package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OAuthUrlMaskerTest {

    @Test
    fun `クエリを落としてホストとパスを残す`() {
        val url = "https://login.salesforce.com/services/oauth2/authorize" +
            "?client_id=3MVG9abc&response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A33333"

        val masked = OAuthUrlMasker.maskQuery(url)

        assertEquals("https://login.salesforce.com/services/oauth2/authorize?***", masked)
    }

    @Test
    fun `クライアント ID が残らない`() {
        val url = "https://login.salesforce.com/services/oauth2/authorize?client_id=3MVG9secret"

        assertFalse(OAuthUrlMasker.maskQuery(url).contains("3MVG9secret"))
    }

    @Test
    fun `クエリが無い URL はそのまま返す`() {
        val url = "https://login.salesforce.com/services/oauth2/authorize"

        assertEquals(url, OAuthUrlMasker.maskQuery(url))
    }

    @Test
    fun `空文字はそのまま返す`() {
        assertEquals("", OAuthUrlMasker.maskQuery(""))
    }
}
