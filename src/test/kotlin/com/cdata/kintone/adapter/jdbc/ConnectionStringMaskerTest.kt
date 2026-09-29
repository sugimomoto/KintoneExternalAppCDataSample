package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionStringMaskerTest {
    @Test
    fun `Password をマスクする`() {
        val masked = ConnectionStringMasker.mask("jdbc:salesforce:User=u;Password=p@ss;")
        assertEquals("jdbc:salesforce:User=u;Password=***;", masked)
    }

    @Test
    fun `大文字小文字を問わずマスクする`() {
        assertEquals("jdbc:x:password=***;", ConnectionStringMasker.mask("jdbc:x:password=secret;"))
        assertEquals("jdbc:x:PASSWORD=***;", ConnectionStringMasker.mask("jdbc:x:PASSWORD=secret;"))
    }

    @Test
    fun `PersonalAccessToken をマスクする（Issue 10 の再現ケース）`() {
        val url = "jdbc:bcart:AuthScheme=PersonalAccessToken;PersonalAccessToken=eyJ0eXAiOiJKV1Qi.abc.def;BatchSize=0;"
        val masked = ConnectionStringMasker.mask(url)
        assertFalse(masked.contains("eyJ0eXAiOiJKV1Qi"), "JWT 本体が残ってはいけない: $masked")
        assertTrue(masked.contains("PersonalAccessToken=***"))
        assertTrue(masked.contains("BatchSize=0"), "機密でない値は残す")
        assertTrue(masked.contains("AuthScheme=PersonalAccessToken"), "値が機密名でも値自体は機密でない")
    }

    @Test
    fun `OAuth 系の各プロパティをマスクする`() {
        val url = "jdbc:x:OAuthClientSecret=s1;OAuthAccessToken=s2;OAuthRefreshToken=s3;OAuthClientId=public;"
        val masked = ConnectionStringMasker.mask(url)
        listOf("s1", "s2", "s3").forEach { assertFalse(masked.contains(it), "$it が残っている: $masked") }
        assertTrue(masked.contains("OAuthClientId=public"), "ClientId は機密ではないので残す")
    }

    @Test
    fun `未知のプロパティでも名前に token secret key を含めばマスクする`() {
        val url = "jdbc:x:SomeVendorApiKey=k1;WeirdSecretThing=k2;MyAuthToken=k3;Passphrase=k4;"
        val masked = ConnectionStringMasker.mask(url)
        listOf("k1", "k2", "k3", "k4").forEach { assertFalse(masked.contains(it), "$it が残っている: $masked") }
    }

    @Test
    fun `機密でないプロパティはそのまま残す`() {
        val url = "jdbc:salesforce:User=alice@example.com;Timeout=60;Verbosity=1;UseSandbox=true;"
        assertEquals(url, ConnectionStringMasker.mask(url))
    }

    @Test
    fun `末尾のセミコロンが無くてもマスクする`() {
        assertEquals("jdbc:x:Password=***", ConnectionStringMasker.mask("jdbc:x:Password=secret"))
    }

    @Test
    fun `空の値でもマスク表記になる`() {
        assertEquals("jdbc:x:Password=***;", ConnectionStringMasker.mask("jdbc:x:Password=;"))
    }

    @Test
    fun `空文字を渡しても落ちない`() {
        assertEquals("", ConnectionStringMasker.mask(""))
    }

    @Test
    fun `同じプロパティが複数回あってもすべてマスクする`() {
        val masked = ConnectionStringMasker.mask("jdbc:x:Password=a;Foo=1;Password=b;")
        assertFalse(masked.contains("=a;"))
        assertFalse(masked.contains("=b;"))
        assertEquals("jdbc:x:Password=***;Foo=1;Password=***;", masked)
    }
}
