package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * テーブル単位で OAuth トークンキャッシュを分離するため、JDBC URL の
 * `OAuthSettingsLocation` を自動付与するヘルパ [JdbcUrlEnhancer] のテスト。
 *
 * フェーズ2-A では複数テーブルが同じドライバー（例: Salesforce）を別 Connector で
 * 使うケースがあり、CData ドライバの既定 OAuth キャッシュ場所が衝突するのを避ける必要がある。
 */
class JdbcUrlEnhancerTest {

    @Test
    fun `withOAuthCache - 既存 URL に OAuthSettingsLocation を付与`() {
        val url = "jdbc:salesforce:User=u;Password=p;"
        val result = JdbcUrlEnhancer.withOAuthCache(url, "./run/oauth/account.txt")
        assertTrue(
            result.contains("OAuthSettingsLocation=./run/oauth/account.txt"),
            "result was: $result",
        )
        // 既存パラメータが残っている
        assertTrue(result.contains("User=u"))
        assertTrue(result.contains("Password=p"))
    }

    @Test
    fun `withOAuthCache - 既に OAuthSettingsLocation がある場合は変更しない`() {
        val url = "jdbc:salesforce:User=u;OAuthSettingsLocation=./existing.txt;"
        val result = JdbcUrlEnhancer.withOAuthCache(url, "./run/oauth/account.txt")
        assertEquals(url, result)
        assertFalse(result.contains("/run/oauth/account.txt"))
    }

    @Test
    fun `withOAuthCache - 最初のプロパティに明示指定された場合も変更しない`() {
        // CData の接続文字列は jdbc:<product>:<最初のプロパティ>= の形で、
        // 直前が ';' ではなく ':' になる。';' だけを見ていると検出できず二重付与になる。
        val url = "jdbc:salesforce:OAuthSettingsLocation=./existing.txt;User=u;"
        assertEquals(url, JdbcUrlEnhancer.withOAuthCache(url, "./run/oauth/account.txt"))
    }

    @Test
    fun `withOAuthCache - 末尾セミコロンの有無に関わらず正しく付与`() {
        val a = JdbcUrlEnhancer.withOAuthCache("jdbc:foo:User=u", "/p.txt")
        val b = JdbcUrlEnhancer.withOAuthCache("jdbc:foo:User=u;", "/p.txt")
        assertTrue(a.endsWith("OAuthSettingsLocation=/p.txt"), "a=$a")
        assertTrue(b.endsWith("OAuthSettingsLocation=/p.txt"), "b=$b")
        // セミコロンで区切られている
        assertTrue(a.contains(";OAuthSettingsLocation"), "a=$a")
        assertTrue(b.contains(";OAuthSettingsLocation"), "b=$b")
    }

    @Test
    fun `cachePathFor - テーブル名から既定パスを生成`() {
        val path = JdbcUrlEnhancer.cachePathFor("./run", "account")
        assertEquals("./run/oauth/account.txt", path)
    }

    @Test
    fun `cachePathFor - テーブル名に不正文字があってもパスとして安全`() {
        val path = JdbcUrlEnhancer.cachePathFor("./run", "../etc")
        // パストラバーサルを防ぐためサニタイズ
        assertFalse(path.contains(".."), "サニタイズ後パス: $path")
    }

    @Test
    fun `cachePathFor - パス区切りを含むキーでディレクトリを抜け出さない`() {
        listOf("../../etc/passwd", "a/b", "a\\b", "..").forEach { key ->
            val path = JdbcUrlEnhancer.cachePathFor("./run", key)
            assertFalse(path.contains(".."), "抜け出している: $path")
            assertTrue(path.startsWith("./run/oauth/"), "実際: $path")
        }
    }

    @Test
    fun `cachePathFor - 同じキーなら同じパスになる`() {
        // 接続テストと実行時で保存先が食い違わないことが本質 (Issue #11)。
        assertEquals(
            JdbcUrlEnhancer.cachePathFor("./run", "googlesheets-main"),
            JdbcUrlEnhancer.cachePathFor("./run", "googlesheets-main"),
        )
    }

    @Test
    fun `cachePathFor - 異なるキーは別パスになる`() {
        assertNotEquals(
            JdbcUrlEnhancer.cachePathFor("./run", "conn-a"),
            JdbcUrlEnhancer.cachePathFor("./run", "conn-b"),
        )
    }

    // --- applyOAuthCache: 接続を張る経路が通る入口 (Issue #11) ---

    private fun jdbcConfig(driverClass: String, url: String) = com.cdata.kintone.adapter.config.JdbcConfig(
        driverClass = driverClass,
        driverJar = "./lib/dummy.jar",
        url = url,
    )

    @Test
    fun `applyOAuthCache - CData ドライバーにはキャッシュパスを付与する`() {
        val config = jdbcConfig("cdata.jdbc.googlesheets.GoogleSheetsDriver", "jdbc:googlesheets:AuthScheme=OAuth;")

        val applied = JdbcUrlEnhancer.applyOAuthCache(config, cacheKey = "gs-main", baseDir = "./run")

        assertTrue(
            applied.url.contains("OAuthSettingsLocation=./run/oauth/gs-main.txt"),
            "実際: ${applied.url}",
        )
    }

    @Test
    fun `applyOAuthCache - CData 以外のドライバーには付与しない`() {
        // OAuthSettingsLocation は CData 固有。H2 は未知の ;KEY=VALUE を接続エラーにする。
        val config = jdbcConfig("org.h2.Driver", "jdbc:h2:mem:test")

        val applied = JdbcUrlEnhancer.applyOAuthCache(config, cacheKey = "h2", baseDir = "./run")

        assertEquals(config, applied)
    }

    @Test
    fun `applyOAuthCache - ユーザーの明示指定を上書きしない`() {
        val config = jdbcConfig(
            "cdata.jdbc.googlesheets.GoogleSheetsDriver",
            "jdbc:googlesheets:OAuthSettingsLocation=/custom/path.txt;",
        )

        val applied = JdbcUrlEnhancer.applyOAuthCache(config, cacheKey = "gs-main", baseDir = "./run")

        assertEquals(config, applied)
    }

    @Test
    fun `isCDataDriver - CData 形式のクラス名を判定する`() {
        assertTrue(JdbcUrlEnhancer.isCDataDriver("cdata.jdbc.salesforce.SalesforceDriver"))
        assertFalse(JdbcUrlEnhancer.isCDataDriver("org.h2.Driver"))
        assertFalse(JdbcUrlEnhancer.isCDataDriver("org.postgresql.Driver"))
        assertFalse(JdbcUrlEnhancer.isCDataDriver("cdata.jdbc"))
        assertFalse(JdbcUrlEnhancer.isCDataDriver(""))
    }
}
