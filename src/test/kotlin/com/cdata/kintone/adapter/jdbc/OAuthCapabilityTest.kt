package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OAuthCapabilityTest {

    @Test
    fun `ブラウザ認可が必要な認証方式を判定する`() {
        listOf("OAuth", "OAuthClient", "OAuthPKCE", "AzureAD", "AzureADPKCE").forEach {
            assertTrue(OAuthCapability.requiresBrowserAuthorization(it), "対象: $it")
        }
    }

    @Test
    fun `プロパティ入力だけで完結する方式はウィザードの対象にしない`() {
        // OAuthPassword はユーザー名とパスワード、OAuthJWT は証明書で完結する。
        listOf("OAuthPassword", "OAuthJWT", "GCPInstanceAccount", "AWSWorkloadIdentity")
            .forEach { assertFalse(OAuthCapability.requiresBrowserAuthorization(it), "対象: $it") }
    }

    @Test
    fun `OAuth 以外の認証方式は対象にしない`() {
        listOf("Basic", "PersonalAccessToken", "Token", "SAPBTP", "NTLM")
            .forEach { assertFalse(OAuthCapability.requiresBrowserAuthorization(it), "対象: $it") }
    }

    @Test
    fun `認証方式が不明なときは対象にしない`() {
        assertFalse(OAuthCapability.requiresBrowserAuthorization(null))
        assertFalse(OAuthCapability.requiresBrowserAuthorization(""))
        assertFalse(OAuthCapability.requiresBrowserAuthorization("   "))
    }

    @Test
    fun `大文字小文字と前後の空白を無視する`() {
        assertTrue(OAuthCapability.requiresBrowserAuthorization("oauth"))
        assertTrue(OAuthCapability.requiresBrowserAuthorization("OAUTH"))
        assertTrue(OAuthCapability.requiresBrowserAuthorization(" OAuthClient "))
    }

    @Test
    fun `接続文字列から AuthScheme を取り出す`() {
        assertEquals("Basic", OAuthCapability.authSchemeOf("jdbc:salesforce:User=u;AuthScheme=Basic;"))
    }

    @Test
    fun `最初のプロパティに書かれた AuthScheme も取り出す`() {
        // CData の接続文字列は最初のプロパティの直前が ';' ではなく ':' になる。
        assertEquals("OAuth", OAuthCapability.authSchemeOf("jdbc:salesforce:AuthScheme=OAuth;User=u;"))
    }

    @Test
    fun `AuthScheme が無ければ null を返す`() {
        // #27 以降、既定値は接続文字列に保存されないため未指定が普通に起こる。
        assertNull(OAuthCapability.authSchemeOf("jdbc:salesforce:User=u;Password=p;"))
    }

    @Test
    fun `AuthScheme の値が空なら null を返す`() {
        assertNull(OAuthCapability.authSchemeOf("jdbc:salesforce:AuthScheme=;User=u;"))
    }

    @Test
    fun `末尾の AuthScheme もセミコロン無しで取り出せる`() {
        assertEquals("OAuth", OAuthCapability.authSchemeOf("jdbc:salesforce:User=u;AuthScheme=OAuth"))
    }
}
