package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionPropertyProbeTest {

    private fun prop(name: String, required: Boolean = false) =
        DriverProperty(name = name, description = "", required = required, allowedValues = emptyList())

    // --- dummyValueFor ---

    @Test
    fun `URL を含む名前には名前解決されない URL を返す`() {
        assertEquals(ConnectionPropertyProbe.PROBE_URL, ConnectionPropertyProbe.dummyValueFor("URL"))
        assertEquals(ConnectionPropertyProbe.PROBE_URL, ConnectionPropertyProbe.dummyValueFor("CallbackURL"))
    }

    @Test
    fun `URI や Endpoint を含む名前にも URL 形式のダミー値を返す`() {
        assertEquals(ConnectionPropertyProbe.PROBE_URL, ConnectionPropertyProbe.dummyValueFor("ServiceUri"))
        assertEquals(ConnectionPropertyProbe.PROBE_URL, ConnectionPropertyProbe.dummyValueFor("RegionEndpoint"))
    }

    @Test
    fun `ダミー URL は http で始まる（書式検証を通すため）`() {
        assertTrue(
            ConnectionPropertyProbe.PROBE_URL.startsWith("http://"),
            "SAP Gateway の URL は '^(http|https)://.*' を要求する",
        )
    }

    @Test
    fun `名前の一部に uri を含むだけのプロパティを URL 扱いしない`() {
        // "security" には "uri" が含まれる。部分一致で判定すると
        // Salesforce の必須プロパティ SecurityToken が URL 扱いされてしまう。
        assertEquals(ConnectionPropertyProbe.PROBE_VALUE, ConnectionPropertyProbe.dummyValueFor("SecurityToken"))
    }

    @Test
    fun `真偽値に使われる接頭辞の名前には False を返す`() {
        listOf("UseSandbox", "IsOffline", "EnableCaching", "AllowFormula", "IgnoreErrorValues", "RecurseFolders")
            .forEach { assertEquals("False", ConnectionPropertyProbe.dummyValueFor(it), "対象: $it") }
    }

    @Test
    fun `Port を含む名前には数値を返す`() {
        assertEquals("443", ConnectionPropertyProbe.dummyValueFor("Port"))
        assertEquals("443", ConnectionPropertyProbe.dummyValueFor("ProxyPort"))
    }

    @Test
    fun `その他の名前には汎用ダミー値を返す`() {
        assertEquals(ConnectionPropertyProbe.PROBE_VALUE, ConnectionPropertyProbe.dummyValueFor("Namespace"))
        assertEquals(ConnectionPropertyProbe.PROBE_VALUE, ConnectionPropertyProbe.dummyValueFor("User"))
    }

    @Test
    fun `URL の判定は大文字小文字を問わない`() {
        assertEquals(ConnectionPropertyProbe.PROBE_URL, ConnectionPropertyProbe.dummyValueFor("url"))
        assertEquals(ConnectionPropertyProbe.PROBE_URL, ConnectionPropertyProbe.dummyValueFor("callbackurl"))
    }

    @Test
    fun `真偽値の判定は camelCase の語境界で行う`() {
        // 接頭辞の文字列一致だけで判定すると、User が "Use" + "r" として真偽値扱いされる。
        assertEquals("False", ConnectionPropertyProbe.dummyValueFor("UseSandbox"))
        assertEquals(ConnectionPropertyProbe.PROBE_VALUE, ConnectionPropertyProbe.dummyValueFor("User"))
        assertEquals(ConnectionPropertyProbe.PROBE_VALUE, ConnectionPropertyProbe.dummyValueFor("Issuer"))
    }

    // --- candidateUrls ---

    @Test
    fun `先頭の候補は安全プロパティのみを含む`() {
        val urls = ConnectionPropertyProbe.candidateUrls(
            "jdbc:salesforce",
            listOf(prop("Offline"), prop("InitiateOAuth"), prop("User", required = true)),
        )
        assertEquals("jdbc:salesforce:Offline=true;InitiateOAuth=OFF;", urls.first())
    }

    @Test
    fun `安全プロパティを持たないドライバには付与しない`() {
        val urls = ConnectionPropertyProbe.candidateUrls("jdbc:x", listOf(prop("User", required = true)))
        assertTrue(urls.none { it.contains("Offline") }, "存在しないプロパティを指定すると接続が失敗する: $urls")
        assertTrue(urls.none { it.contains("InitiateOAuth") }, "同上: $urls")
        assertEquals("jdbc:x:", urls.first())
    }

    @Test
    fun `必須プロパティのダミー値を含む候補が後ろに並ぶ`() {
        val urls = ConnectionPropertyProbe.candidateUrls(
            "jdbc:sapgateway",
            listOf(
                prop("Offline"),
                prop("InitiateOAuth"),
                prop("URL", required = true),
                prop("Namespace", required = true),
                prop("BatchSize"),
            ),
        )
        val withDummies = urls.last()
        assertTrue(withDummies.contains("URL=${ConnectionPropertyProbe.PROBE_URL};"), "実際: $withDummies")
        assertTrue(withDummies.contains("Namespace=${ConnectionPropertyProbe.PROBE_VALUE};"), "実際: $withDummies")
        assertFalse(withDummies.contains("BatchSize"), "必須でないプロパティは入れない: $withDummies")
        assertTrue(urls.indexOf(withDummies) > 0, "ダミー値付きは素の候補より後に試す")
    }

    @Test
    fun `必須プロパティが無いときダミー値付きの候補を作らない`() {
        val urls = ConnectionPropertyProbe.candidateUrls("jdbc:x", listOf(prop("Offline"), prop("InitiateOAuth")))
        assertEquals(listOf("jdbc:x:Offline=true;InitiateOAuth=OFF;", "jdbc:x:"), urls)
    }

    @Test
    fun `候補に重複が無い`() {
        val urls = ConnectionPropertyProbe.candidateUrls("jdbc:x", emptyList())
        assertEquals(urls.distinct(), urls, "重複した接続文字列を試す意味はない: $urls")
        assertEquals(listOf("jdbc:x:"), urls)
    }

    @Test
    fun `ダミー値は接続文字列の区切り文字を含まない`() {
        val urls = ConnectionPropertyProbe.candidateUrls(
            "jdbc:x",
            listOf("URL", "Namespace", "Port", "UseSandbox", "Token").map { prop(it, required = true) },
        )
        val values = urls.last().removePrefix("jdbc:x:").split(";").filter { it.isNotEmpty() }
            .map { it.substringAfter("=") }
        assertTrue(values.isNotEmpty(), "ダミー値付きの候補が無い: $urls")
        values.forEach { assertFalse(it.contains(";"), "値に区切り文字が含まれる: $it") }
    }

    @Test
    fun `必須プロパティ名の前後の空白はダミー値の判定に影響しない`() {
        // getPropertyInfo は "User " のように末尾空白付きの名前を返すことがある。
        // DriverProperty.from で trim しているが、万一残っても壊れないことを保証する。
        val urls = ConnectionPropertyProbe.candidateUrls("jdbc:x", listOf(prop("URL ", required = true)))
        assertTrue(urls.last().contains("URL=${ConnectionPropertyProbe.PROBE_URL};"), "実際: ${urls.last()}")
    }
}
