package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `withOAuthCachePerTable - 既存 URL に OAuthSettingsLocation を付与`() {
        val url = "jdbc:salesforce:User=u;Password=p;"
        val result = JdbcUrlEnhancer.withOAuthCachePerTable(url, "./run/oauth/account.txt")
        assertTrue(
            result.contains("OAuthSettingsLocation=./run/oauth/account.txt"),
            "result was: $result",
        )
        // 既存パラメータが残っている
        assertTrue(result.contains("User=u"))
        assertTrue(result.contains("Password=p"))
    }

    @Test
    fun `withOAuthCachePerTable - 既に OAuthSettingsLocation がある場合は変更しない`() {
        val url = "jdbc:salesforce:User=u;OAuthSettingsLocation=./existing.txt;"
        val result = JdbcUrlEnhancer.withOAuthCachePerTable(url, "./run/oauth/account.txt")
        assertEquals(url, result)
        assertFalse(result.contains("/run/oauth/account.txt"))
    }

    @Test
    fun `withOAuthCachePerTable - 末尾セミコロンの有無に関わらず正しく付与`() {
        val a = JdbcUrlEnhancer.withOAuthCachePerTable("jdbc:foo:User=u", "/p.txt")
        val b = JdbcUrlEnhancer.withOAuthCachePerTable("jdbc:foo:User=u;", "/p.txt")
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
}
