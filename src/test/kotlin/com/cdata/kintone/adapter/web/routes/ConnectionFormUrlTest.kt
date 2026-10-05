package com.cdata.kintone.adapter.web.routes

import io.ktor.http.parametersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ConnectionFormUrlTest {

    private fun form(vararg pairs: Pair<String, String>) =
        parametersOf(*pairs.map { (k, v) -> k to listOf(v) }.toTypedArray())

    @Test
    fun `入力されたプロパティだけを接続文字列に含める`() {
        val url = ConnectionFormUrl.build(
            "jdbc:googlesheets",
            form("prop.AuthScheme" to "OAuth", "prop.SpreadsheetId" to "abc123"),
        )

        assertEquals("jdbc:googlesheets:AuthScheme=OAuth;SpreadsheetId=abc123;", url)
    }

    @Test
    fun `未入力のプロパティは接続文字列に含めない`() {
        // 本件の核心。フォームが既定値を埋めないため、空 = 利用者が触っていない。
        val url = ConnectionFormUrl.build(
            "jdbc:googlesheets",
            form(
                "prop.AuthScheme" to "OAuth",
                "prop.OAuthSettingsLocation" to "",
                "prop.Pagesize" to "",
                "prop.Verbosity" to "",
            ),
        )

        assertEquals("jdbc:googlesheets:AuthScheme=OAuth;", url)
        assertFalse(url.contains("OAuthSettingsLocation"), "既定値のまま触っていない項目が入っている: $url")
    }

    @Test
    fun `プロパティが 1 つも無ければ接頭辞だけを返す`() {
        assertEquals("jdbc:googlesheets:", ConnectionFormUrl.build("jdbc:googlesheets", form()))
    }

    @Test
    fun `プロパティが無く fallbackUrl があればそれを返す`() {
        val url = ConnectionFormUrl.build(
            "jdbc:googlesheets",
            form(),
            fallbackUrl = "jdbc:googlesheets:AuthScheme=OAuth;",
        )

        assertEquals("jdbc:googlesheets:AuthScheme=OAuth;", url)
    }

    @Test
    fun `直接入力があれば最優先で採用する`() {
        // プロパティ定義が取れないドライバーの逃げ道 (Issue #15)。
        val url = ConnectionFormUrl.build(
            "jdbc:googlesheets",
            form("prop.AuthScheme" to "OAuth", "jdbc.url.manual" to "jdbc:custom:Foo=1;"),
        )

        assertEquals("jdbc:custom:Foo=1;", url)
    }

    @Test
    fun `直接入力が空文字ならプロパティから組み立てる`() {
        val url = ConnectionFormUrl.build(
            "jdbc:googlesheets",
            form("prop.AuthScheme" to "OAuth", "jdbc.url.manual" to "   "),
        )

        assertEquals("jdbc:googlesheets:AuthScheme=OAuth;", url)
    }

    @Test
    fun `prop 以外のフォーム項目は含めない`() {
        val url = ConnectionFormUrl.build(
            "jdbc:googlesheets",
            form(
                "name" to "my-connection",
                "driver" to "cdata.jdbc.googlesheets.GoogleSheetsDriver|x.jar",
                "pool.maximumPoolSize" to "10",
                "prop.AuthScheme" to "OAuth",
            ),
        )

        assertEquals("jdbc:googlesheets:AuthScheme=OAuth;", url)
    }

    @Test
    fun `propertyValues は接頭辞を除いた名前をキーにする`() {
        val values = ConnectionFormUrl.propertyValues(
            form("prop.AuthScheme" to "OAuth", "prop.User" to "", "name" to "x"),
        )

        assertEquals(mapOf("AuthScheme" to "OAuth", "User" to ""), values)
    }

    // --- 手動 URL とプロパティの優先順位 (Issue #59) ---

    @Test
    fun `手動 URL が空ならプロパティから組み立てる`() {
        // 編集画面の手動 URL 欄は併記時に事前入力しない。埋めた値がブラウザから
        // そのまま送り返され、プロパティの編集が黙って捨てられていた (Issue #59)。
        val url = ConnectionFormUrl.build(
            "jdbc:sql",
            form("prop.Server" to "NEW-SERVER", "prop.Port" to "1433", "jdbc.url.manual" to ""),
        )

        assertEquals("jdbc:sql:Server=NEW-SERVER;Port=1433;", url)
    }

    @Test
    fun `手動 URL が空白のみならプロパティから組み立てる`() {
        val url = ConnectionFormUrl.build(
            "jdbc:sql",
            form("prop.Server" to "NEW-SERVER", "jdbc.url.manual" to "   "),
        )

        assertEquals("jdbc:sql:Server=NEW-SERVER;", url)
    }

    @Test
    fun `手動 URL が入力されていればプロパティより優先される`() {
        // 利用者が意図して直接入力した場合の逃げ道。この規則自体は正しいので変えない。
        // 問題は編集画面が既存の接続文字列を事前入力していたこと (Issue #59)。
        val url = ConnectionFormUrl.build(
            "jdbc:sql",
            form("prop.Server" to "IGNORED", "jdbc.url.manual" to "jdbc:sql:Server=EXPLICIT;"),
        )

        assertEquals("jdbc:sql:Server=EXPLICIT;", url)
        assertFalse(url.contains("IGNORED"), "手動 URL が優先されていない: $url")
    }
}
