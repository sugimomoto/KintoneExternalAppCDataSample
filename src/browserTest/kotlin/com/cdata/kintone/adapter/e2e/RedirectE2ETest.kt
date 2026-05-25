package com.cdata.kintone.adapter.e2e

import com.microsoft.playwright.options.RequestOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * E2E-02: 旧 /tables URL から /syncs への 301 リダイレクト確認。
 */
class RedirectE2ETest : BrowserTestBase() {

    @Test
    fun `E2E-02 _tables から _syncs に 301 リダイレクト`() {
        // Playwright は自動的にリダイレクトを追跡するため、APIRequestContext で生 HTTP を確認
        val req = page.context().request()
        val resp = req.get(
            "$baseUrl/tables",
            RequestOptions.create().setMaxRedirects(0),
        )
        assertEquals(301, resp.status(), "旧 URL は 301")
        val location = resp.headers()["location"] ?: resp.headers()["Location"]
        assertEquals("/syncs", location)
    }

    @Test
    fun `E2E-02b _tables_NAME から _syncs_NAME に 301`() {
        val req = page.context().request()
        val resp = req.get(
            "$baseUrl/tables/account",
            RequestOptions.create().setMaxRedirects(0),
        )
        assertEquals(301, resp.status())
        val location = resp.headers()["location"] ?: resp.headers()["Location"]
        assertTrue(location?.startsWith("/syncs/") == true, "Location が /syncs/ 始まり: $location")
    }

    @Test
    fun `E2E-02c ブラウザで _tables を開くと最終的に _syncs にいる`() {
        page.navigate("$baseUrl/tables")
        // リダイレクト後の URL
        assertTrue(page.url().endsWith("/syncs"), "実 URL: ${page.url()}")
    }
}
