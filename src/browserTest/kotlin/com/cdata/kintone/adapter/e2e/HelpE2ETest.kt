package com.cdata.kintone.adapter.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * E2E-21: ヘルプページのレンダリング確認。
 * - タイトル・主要セクションが含まれる
 * - ナビゲーションからアクセスできる
 */
class HelpE2ETest : BrowserTestBase() {

    @Test
    fun `E2E-21 ヘルプページが表示される`() {
        page.navigate("$baseUrl/help")
        assertEquals("ヘルプ — CData Kintone Adapter Console", page.title())
        val text = page.locator("main").innerText()
        assertTrue(text.contains("ヘルプ・ガイド"), "見出しに 'ヘルプ・ガイド'")
        assertTrue(text.contains("クイックスタート"), "クイックスタートの章がある")
        assertTrue(text.contains("主要な用語"), "用語セクションがある")
        assertTrue(text.contains("よくある問題"), "Troubleshooting セクションがある")
    }

    @Test
    fun `E2E-22 ヘッダのナビからヘルプに遷移できる`() {
        page.navigate(baseUrl)
        page.locator("header nav a[href='/help']").click()
        page.waitForURL("**/help")
        assertTrue(page.locator("h2").innerText().contains("ヘルプ"))
    }
}
