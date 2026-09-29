package com.cdata.kintone.adapter.e2e

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * E2E-04 / E2E-05: Drivers と Connections の基本画面表示。
 */
class DriversAndConnectionsE2ETest : BrowserTestBase() {

    @Test
    fun `E2E-04 drivers 画面でアップロードフォームが見える`() {
        page.navigate("$baseUrl/drivers")
        assertTrue(page.locator("text=ドライバーを追加").count() > 0)
        // file input
        assertTrue(page.locator("input[type=\"file\"]").count() > 0)
    }

    @Test
    fun `E2E-05 connections 新規フォームが表示される`() {
        page.navigate("$baseUrl/connections/new")
        assertTrue(page.locator("text=新しいデータソース接続").count() > 0)
        // ドライバーが lib に無い場合は警告バナーが表示される
        val hasWarning = page.locator("text=JDBC Driver が見つかりません").count() > 0
        val hasForm = page.locator("input[name=\"name\"]").count() > 0
        assertTrue(hasWarning || hasForm, "警告バナー or 入力フォームのいずれかが表示")
    }
}
