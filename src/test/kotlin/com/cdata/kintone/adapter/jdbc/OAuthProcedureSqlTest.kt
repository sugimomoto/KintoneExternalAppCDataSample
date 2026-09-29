package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OAuthProcedureSqlTest {

    @Test
    fun `認可 URL 取得の SQL を組み立てる`() {
        val sql = OAuthProcedureSql.authorizationUrl("http://localhost:33333")

        assertEquals("EXEC GetOAuthAuthorizationUrl CallbackUrl = 'http://localhost:33333'", sql)
    }

    @Test
    fun `CallbackUrl が無ければパラメータを付けない`() {
        // 未指定ならドライバーの既定値が使われる。
        assertEquals("EXEC GetOAuthAuthorizationUrl", OAuthProcedureSql.authorizationUrl(null))
        assertEquals("EXEC GetOAuthAuthorizationUrl", OAuthProcedureSql.authorizationUrl("   "))
    }

    @Test
    fun `トークン取得の SQL を組み立てる`() {
        val sql = OAuthProcedureSql.accessToken("code123", "http://localhost:33333")

        assertEquals(
            "EXEC GetOAuthAccessToken Verifier = 'code123', CallbackUrl = 'http://localhost:33333'",
            sql,
        )
    }

    @Test
    fun `verifier だけでも SQL を組み立てる`() {
        assertEquals("EXEC GetOAuthAccessToken Verifier = 'code123'", OAuthProcedureSql.accessToken("code123", null))
    }

    @Test
    fun `verifier のシングルクォートをエスケープする`() {
        // verifier は外部由来。プレースホルダが使えないためエスケープが必須。
        val sql = OAuthProcedureSql.accessToken("code'; DROP TABLE x--", null)

        assertTrue(sql.contains("code''; DROP TABLE x--"), "実際: $sql")
        assertFalse(sql.contains("'code'; "), "未エスケープの ' が残っている: $sql")
    }

    @Test
    fun `CallbackUrl のシングルクォートもエスケープする`() {
        val sql = OAuthProcedureSql.authorizationUrl("http://x/?a='b")

        assertTrue(sql.contains("http://x/?a=''b"), "実際: $sql")
    }

    @Test
    fun `escapeSqlLiteral は単一引用符を 2 つに置き換える`() {
        assertEquals("it''s", OAuthProcedureSql.escapeSqlLiteral("it's"))
        assertEquals("plain", OAuthProcedureSql.escapeSqlLiteral("plain"))
    }
}
