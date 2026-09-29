package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PropertyHierarchyResolverTest {

    private fun property(
        name: String,
        hierarchy: String = "",
        required: Boolean = false,
        defaultValue: String? = null,
    ) = ConnectionProperty(
        propertyName = name,
        displayName = name,
        shortDescription = "",
        type = PropertyType.STRING,
        defaultValue = defaultValue,
        allowedValues = emptyList(),
        category = "Authentication",
        required = required,
        sensitivity = Sensitivity.NONE,
        visible = true,
        hierarchy = hierarchy,
        ordinal = 0,
        categoryOrdinal = 1,
    )

    private fun names(properties: List<ConnectionProperty>) = properties.map { it.propertyName }

    // --- 基本 ---

    @Test
    fun `条件を持たないプロパティはそのまま残る`() {
        val props = listOf(property("BatchSize"), property("Timeout"))
        assertEquals(listOf("BatchSize", "Timeout"), names(PropertyHierarchyResolver.resolve(props, emptyMap())))
    }

    @Test
    fun `条件を満たすプロパティは必須のまま残る`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, mapOf("AuthScheme" to "Basic"))
        assertEquals(listOf("AuthScheme", "User"), names(resolved))
        assertTrue(resolved.last().required)
    }

    @Test
    fun `条件を満たさないプロパティは除外される`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, mapOf("AuthScheme" to "OAuth"))
        assertEquals(listOf("AuthScheme"), names(resolved))
    }

    // --- 実効値 ---

    @Test
    fun `入力値が無いときは既定値で判定する`() {
        // Salesforce の AuthScheme 既定値は OAuth。初回表示でも User が出てはいけない。
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
            property("OAuthClientId", hierarchy = "AuthScheme=OAuth"),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, emptyMap())
        assertEquals(listOf("AuthScheme", "OAuthClientId"), names(resolved))
    }

    @Test
    fun `入力値は既定値より優先される`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, mapOf("AuthScheme" to "Basic"))
        assertTrue("User" in names(resolved))
    }

    @Test
    fun `空文字の入力値は未入力として扱う`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "Basic"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, mapOf("AuthScheme" to ""))
        assertTrue("User" in names(resolved), "空文字なら既定値 Basic で判定する")
    }

    @Test
    fun `プロパティ名の大文字小文字が違っても入力値を引き当てる`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "authscheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, mapOf("authScheme" to "Basic"))
        assertTrue("User" in names(resolved))
    }

    @Test
    fun `許可値の大文字小文字が違っても条件が成立する`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "Okta"),
            property("User", hierarchy = "AuthScheme=OKTA", required = true),
        )
        assertTrue("User" in names(PropertyHierarchyResolver.resolve(props, emptyMap())))
    }

    // --- 連鎖 ---

    @Test
    fun `条件が連鎖しても解決できる`() {
        // OAuthAccessToken -> InitiateOAuth -> AuthScheme の 2 段
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("InitiateOAuth", hierarchy = "AuthScheme=OAuth", defaultValue = "OFF"),
            property("OAuthAccessToken", hierarchy = "InitiateOAuth=REFRESH,OFF"),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, emptyMap())
        assertEquals(listOf("AuthScheme", "InitiateOAuth", "OAuthAccessToken"), names(resolved))
    }

    @Test
    fun `依存先が非表示ならそれに依存するプロパティも非表示になる`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "Basic"),
            property("InitiateOAuth", hierarchy = "AuthScheme=OAuth", defaultValue = "OFF"),
            property("OAuthAccessToken", hierarchy = "InitiateOAuth=REFRESH,OFF"),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, emptyMap())
        assertEquals(listOf("AuthScheme"), names(resolved))
    }

    // --- 判定できないケース ---

    @Test
    fun `依存先が一覧に無いときは残す`() {
        // 非表示に倒すと、ドライバー更新で画面が空になり得る。
        val props = listOf(property("SSOProperties", hierarchy = "UnknownProperty=Value"))
        assertEquals(listOf("SSOProperties"), names(PropertyHierarchyResolver.resolve(props, emptyMap())))
    }

    @Test
    fun `条件の形式が不正なときは残す`() {
        val props = listOf(property("SSOProperties", hierarchy = "壊れた条件"))
        assertEquals(listOf("SSOProperties"), names(PropertyHierarchyResolver.resolve(props, emptyMap())))
    }

    @Test
    fun `循環参照があっても例外にならない`() {
        val props = listOf(
            property("A", hierarchy = "B=x", defaultValue = "x"),
            property("B", hierarchy = "A=x", defaultValue = "x"),
        )
        assertEquals(listOf("A", "B"), names(PropertyHierarchyResolver.resolve(props, emptyMap())))
    }

    // --- 値の保全 ---

    @Test
    fun `条件を満たさなくても値があれば残す`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(
            props,
            mapOf("AuthScheme" to "OAuth", "User" to "saved-user"),
        )
        assertEquals(listOf("AuthScheme", "User"), names(resolved))
        assertFalse(resolved.last().required, "その状態では必須ではない")
    }

    @Test
    fun `条件を満たさず値も空なら残さない`() {
        val props = listOf(
            property("AuthScheme", defaultValue = "OAuth"),
            property("User", hierarchy = "AuthScheme=Basic", required = true),
        )
        val resolved = PropertyHierarchyResolver.resolve(props, mapOf("AuthScheme" to "OAuth", "User" to ""))
        assertEquals(listOf("AuthScheme"), names(resolved))
    }

    // --- dependencyNames ---

    @Test
    fun `依存先として参照されている名前を返す`() {
        val props = listOf(
            property("AuthScheme"),
            property("User", hierarchy = "AuthScheme=Basic"),
            property("BulkPageSize", hierarchy = "UseBulkAPI=True"),
            property("UseBulkAPI"),
        )
        assertEquals(setOf("AuthScheme", "UseBulkAPI"), PropertyHierarchyResolver.dependencyNames(props))
    }

    @Test
    fun `条件が無ければ依存先は空になる`() {
        assertEquals(emptySet<String>(), PropertyHierarchyResolver.dependencyNames(listOf(property("BatchSize"))))
    }
}
