package com.cdata.kintone.adapter.e2e

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * E2E-03 / WZ: ウィザード Step 1 画面の基本表示。
 * 接続が無い状態では警告バナー、ある状態ではラジオボタンを期待。
 */
class WizardE2ETest : BrowserTestBase() {

    @Test
    fun `E2E ウィザード Step1 にアクセス可能`() {
        page.navigate("$baseUrl/syncs/new")
        // 連携無し + 接続無しの状態では「Connection を新規作成してください」案内
        val hasWarning = page.locator("text=JDBC Connection が登録されていません").count() > 0
        val hasRadio = page.locator("input[type=\"radio\"][name=\"connection\"]").count() > 0
        assertTrue(hasWarning || hasRadio, "警告バナー or ラジオボタンが見える")
    }

    @Test
    fun `E2E ウィザードへの導線がダッシュボードから存在`() {
        page.navigate(baseUrl)
        val link = page.locator("a:has-text(\"+ 新しい連携\")").first()
        link.click()
        // 遷移先 URL
        assertTrue(page.url().contains("/syncs/new"), "URL: ${page.url()}")
    }
}
