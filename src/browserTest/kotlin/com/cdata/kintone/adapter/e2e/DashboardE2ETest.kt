package com.cdata.kintone.adapter.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * E2E-01: ダッシュボード表示の基本確認。
 * E2E-08: ナビゲーションが日本語化されていること。
 */
class DashboardE2ETest : BrowserTestBase() {

    @Test
    fun `E2E-01 ダッシュボードが表示される (連携 0 件)`() {
        page.navigate(baseUrl)
        assertEquals("ダッシュボード — CData Kintone Adapter Console", page.title())
        assertTrue(page.locator("h2").innerText().contains("ダッシュボード"))
        // 連携 0 件のメッセージ
        val activeText = page.locator("h3").allInnerTexts().joinToString("\n")
        assertTrue(activeText.contains("稼働中の連携"), "見出しに '稼働中の連携' が含まれる")
    }

    @Test
    fun `E2E-08 ナビゲーションが日本語化されている`() {
        page.navigate(baseUrl)
        val navLabels = page.locator("header nav ul li").allInnerTexts()
        // ヘッダの最初は ⚙ Adapter Console、その他はナビ
        assertTrue(navLabels.any { it.contains("ダッシュボード") }, "ナビに 'ダッシュボード'")
        assertTrue(navLabels.any { it.contains("連携") }, "ナビに '連携'")
        assertTrue(navLabels.any { it.contains("データソース") }, "ナビに 'データソース'")
        assertTrue(navLabels.any { it.contains("ドライバー") }, "ナビに 'ドライバー'")
    }

    @Test
    fun `E2E Quick Actions に主要ボタンが表示される`() {
        page.navigate(baseUrl)
        assertTrue(page.locator("text=+ 新しい連携").count() > 0)
        assertTrue(page.locator("text=ドライバー管理").count() > 0)
        assertTrue(page.locator("text=データソース接続を追加").count() > 0)
    }
}
