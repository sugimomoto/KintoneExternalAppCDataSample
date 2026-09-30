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

    // --- 実効 AuthScheme の解決 (Issue #43) ---

    @Test
    fun `effectiveAuthScheme - URL の値が既定値より優先される`() {
        val scheme = OAuthCapability.effectiveAuthScheme("jdbc:salesforce:AuthScheme=Basic;", "OAuth")

        assertEquals("Basic", scheme)
    }

    @Test
    fun `effectiveAuthScheme - URL に無ければ既定値を使う`() {
        // #27 以降、既定値は接続文字列に保存されないため未指定が普通に起こる。
        val scheme = OAuthCapability.effectiveAuthScheme("jdbc:salesforce:User=u;", "OAuth")

        assertEquals("OAuth", scheme)
    }

    @Test
    fun `effectiveAuthScheme - 両方無ければ null`() {
        assertNull(OAuthCapability.effectiveAuthScheme("jdbc:salesforce:User=u;", null))
    }

    @Test
    fun `effectiveAuthScheme - 既定値が空白なら null`() {
        assertNull(OAuthCapability.effectiveAuthScheme("jdbc:salesforce:User=u;", "   "))
    }

    @Test
    fun `requiresBrowserAuthorization - URL の値が OAuth なら true`() {
        assertTrue(OAuthCapability.requiresBrowserAuthorization("jdbc:salesforce:AuthScheme=OAuth;", null))
    }

    @Test
    fun `requiresBrowserAuthorization - URL に無く既定値が OAuth なら true`() {
        // AuthScheme を明示せず保存した OAuth 接続も認可ウィザードへ送る (AC-10)。
        assertTrue(OAuthCapability.requiresBrowserAuthorization("jdbc:googlesheets:", "OAuth"))
    }

    @Test
    fun `requiresBrowserAuthorization - URL に無く既定値が Basic なら false`() {
        assertFalse(OAuthCapability.requiresBrowserAuthorization("jdbc:salesforce:User=u;", "Basic"))
    }

    @Test
    fun `requiresBrowserAuthorization - URL の値が既定値を上書きして false になる`() {
        // 既定が OAuth のドライバーでも、明示的に Basic を選んだ接続は対象外。
        assertFalse(OAuthCapability.requiresBrowserAuthorization("jdbc:salesforce:AuthScheme=Basic;", "OAuth"))
    }

    @Test
    fun `requiresBrowserAuthorization - ブラウザ認可を伴わない OAuth 系は false`() {
        assertFalse(OAuthCapability.requiresBrowserAuthorization("jdbc:salesforce:", "OAuthJWT"))
        assertFalse(OAuthCapability.requiresBrowserAuthorization("jdbc:salesforce:", "OAuthPassword"))
    }

    // --- 実効コールバック URL (Issue #55) ---

    @Test
    fun `effectiveCallbackUrl - 設定されていればその値を使う`() {
        val url = "jdbc:googlesheets:AuthScheme=OAuth;CallbackURL=http://localhost:9999;"

        assertEquals("http://localhost:9999", OAuthCapability.effectiveCallbackUrl(url))
    }

    @Test
    fun `effectiveCallbackUrl - 未設定なら既定値を使う`() {
        // ドライバー既定値に委ねると Google 系は OOB になり認可できない。
        assertEquals(
            OAuthCapability.DEFAULT_CALLBACK_URL,
            OAuthCapability.effectiveCallbackUrl("jdbc:googlesheets:AuthScheme=OAuth;"),
        )
    }

    @Test
    fun `effectiveCallbackUrl - 空白なら既定値を使う`() {
        assertEquals(
            OAuthCapability.DEFAULT_CALLBACK_URL,
            OAuthCapability.effectiveCallbackUrl("jdbc:googlesheets:CallbackURL=   ;AuthScheme=OAuth;"),
        )
    }

    @Test
    fun `effectiveCallbackUrl - 最初のプロパティに書かれていても拾う`() {
        val url = "jdbc:salesforce:CallbackURL=http://localhost:9999;AuthScheme=OAuth;"

        assertEquals("http://localhost:9999", OAuthCapability.effectiveCallbackUrl(url))
    }

    @Test
    fun `effectiveCallbackUrl - 大文字小文字が違っても拾う`() {
        val url = "jdbc:salesforce:callbackurl=http://localhost:9999;"

        assertEquals("http://localhost:9999", OAuthCapability.effectiveCallbackUrl(url))
    }

    @Test
    fun `DEFAULT_CALLBACK_URL - 組み込みアプリが受け付ける localhost コールバック`() {
        // ポートは CData の組み込み OAuth アプリ側に登録されている値。
        // Salesforce 系はドライバーが oauth.cdata.com + state=base64(この値) に変換する。
        assertEquals("http://localhost:33333", OAuthCapability.DEFAULT_CALLBACK_URL)
    }
}
