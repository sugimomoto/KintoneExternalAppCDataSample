package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    // --- redactVerifier ---

    @Test
    fun `例外メッセージ中の認可コードを伏せる`() {
        // ドライバーの例外に SQL が含まれると認可コードが漏れる。
        val raw = "Error executing: EXEC GetOAuthAccessToken Verifier = 'aPrxsmSECRET', CallbackUrl = 'http://x'"

        val redacted = OAuthUrlMasker.redactVerifier(raw, "aPrxsmSECRET")

        assertFalse(redacted!!.contains("aPrxsmSECRET"), "実際: $redacted")
        assertTrue(redacted.contains(OAuthUrlMasker.MASK), "実際: $redacted")
    }

    @Test
    fun `認可コードを含まないメッセージはそのまま返す`() {
        val raw = "OAUTH [30004] invalid_client_id"

        assertEquals(raw, OAuthUrlMasker.redactVerifier(raw, "aPrxsmSECRET"))
    }

    @Test
    fun `メッセージが null なら null を返す`() {
        assertNull(OAuthUrlMasker.redactVerifier(null, "code"))
    }

    @Test
    fun `認可コードが空なら置換しない`() {
        assertEquals("message", OAuthUrlMasker.redactVerifier("message", ""))
    }
}
