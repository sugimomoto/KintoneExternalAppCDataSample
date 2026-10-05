package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    // --- withInitiateOAuthOff (Issue #12) ---

    @Test
    fun `withInitiateOAuthOff - GETANDREFRESH を OFF に置き換える`() {
        // GETANDREFRESH のままだとドライバーがブラウザを開こうとして
        // ヘッドレスなコンテナでは 60 秒タイムアウトする。
        val url = "jdbc:salesforce:AuthScheme=OAuth;InitiateOAuth=GETANDREFRESH;OAuthClientId=x;"

        val result = JdbcUrlEnhancer.withInitiateOAuthOff(url)

        assertTrue(result.contains("InitiateOAuth=OFF"), "実際: $result")
        assertFalse(result.contains("GETANDREFRESH"), "実際: $result")
        assertTrue(result.contains("OAuthClientId=x"), "他のプロパティが消えている: $result")
    }

    @Test
    fun `withInitiateOAuthOff - 指定が無ければ追加する`() {
        val result = JdbcUrlEnhancer.withInitiateOAuthOff("jdbc:salesforce:AuthScheme=OAuth;")

        assertEquals("jdbc:salesforce:AuthScheme=OAuth;InitiateOAuth=OFF", result)
    }

    @Test
    fun `withInitiateOAuthOff - 最初のプロパティにある場合も置き換える`() {
        val result = JdbcUrlEnhancer.withInitiateOAuthOff("jdbc:salesforce:InitiateOAuth=REFRESH;User=u;")

        assertTrue(result.contains("InitiateOAuth=OFF"), "実際: $result")
        assertFalse(result.contains("REFRESH"), "実際: $result")
    }

    @Test
    fun `withInitiateOAuthOff - 大文字小文字を無視する`() {
        val result = JdbcUrlEnhancer.withInitiateOAuthOff("jdbc:salesforce:initiateoauth=getandrefresh;")

        assertTrue(result.contains("=OFF"), "実際: $result")
        assertFalse(result.lowercase().contains("getandrefresh"), "実際: $result")
    }

    @Test
    fun `withInitiateOAuthOff - 既に OFF なら変わらない`() {
        val url = "jdbc:salesforce:AuthScheme=OAuth;InitiateOAuth=OFF;"

        assertTrue(JdbcUrlEnhancer.withInitiateOAuthOff(url).contains("InitiateOAuth=OFF"))
    }

    // --- withProperty (Issue #34) ---

    @Test
    fun `withProperty - 無いプロパティを追加する`() {
        val result = JdbcUrlEnhancer.withProperty("jdbc:salesforce:AuthScheme=OAuth;", "InitiateOAuth", "REFRESH")
        assertTrue(result.contains("AuthScheme=OAuth"))
        assertTrue(result.contains("InitiateOAuth=REFRESH"), "result was: $result")
    }

    @Test
    fun `withProperty - 既にあるプロパティを置換する`() {
        val url = "jdbc:salesforce:InitiateOAuth=OFF;OAuthRefreshToken=old;User=u;"
        val result = JdbcUrlEnhancer.withProperty(url, "OAuthRefreshToken", "new")
        assertTrue(result.contains("OAuthRefreshToken=new"), "result was: $result")
        assertFalse(result.contains("OAuthRefreshToken=old"))
        // 他のプロパティを壊さない (AC-7)
        assertTrue(result.contains("InitiateOAuth=OFF"))
        assertTrue(result.contains("User=u"))
    }

    @Test
    fun `withProperty - 最初のプロパティも置換する`() {
        val url = "jdbc:salesforce:InitiateOAuth=OFF;User=u;"
        val result = JdbcUrlEnhancer.withProperty(url, "InitiateOAuth", "REFRESH")
        assertTrue(result.contains("jdbc:salesforce:InitiateOAuth=REFRESH"), "result was: $result")
        assertFalse(result.contains("InitiateOAuth=OFF"))
    }

    @Test
    fun `withProperty - 大文字小文字を無視して置換する`() {
        val url = "jdbc:googlesheets:initiateoauth=getandrefresh;"
        val result = JdbcUrlEnhancer.withProperty(url, "InitiateOAuth", "REFRESH")
        assertTrue(result.contains("=REFRESH"), "result was: $result")
        assertFalse(result.lowercase().contains("getandrefresh"))
    }

    @Test
    fun `withProperty - 値に含まれるセミコロンを除去する`() {
        // 接続文字列の区切りを壊さないため。CData のトークンに ; は現れないが防御的に。
        val result = JdbcUrlEnhancer.withProperty("jdbc:salesforce:User=u;", "OAuthRefreshToken", "a;b")
        assertTrue(result.contains("OAuthRefreshToken=ab"), "result was: $result")
    }

    // --- propertyOf (Issue #69) ---

    @Test
    fun `propertyOf - プロパティ値を読める`() {
        val url = "jdbc:sql:Server=x;QueryPassthrough=False;Port=1433;"

        assertEquals("False", JdbcUrlEnhancer.propertyOf(url, "QueryPassthrough"))
    }

    @Test
    fun `propertyOf - 無ければ null`() {
        assertNull(JdbcUrlEnhancer.propertyOf("jdbc:sql:Server=x;", "QueryPassthrough"))
    }

    @Test
    fun `propertyOf - 最初のプロパティでも読める`() {
        // CData の接続文字列は jdbc:<product>:<最初のプロパティ>=... の形。
        val url = "jdbc:sql:QueryPassthrough=False;Server=x;"

        assertEquals("False", JdbcUrlEnhancer.propertyOf(url, "QueryPassthrough"))
    }

    @Test
    fun `propertyOf - 大文字小文字を無視する`() {
        val url = "jdbc:sql:querypassthrough=false;"

        assertEquals("false", JdbcUrlEnhancer.propertyOf(url, "QueryPassthrough"))
    }

    @Test
    fun `propertyOf - 空値は null`() {
        assertNull(JdbcUrlEnhancer.propertyOf("jdbc:sql:QueryPassthrough=;Server=x;", "QueryPassthrough"))
    }

    @Test
    fun `propertyOf - 似た名前のプロパティを誤検出しない`() {
        // 前方一致で拾うと別プロパティを誤って読む。
        assertNull(JdbcUrlEnhancer.propertyOf("jdbc:sql:MyQueryPassthroughExtra=x;", "QueryPassthrough"))
    }
}
