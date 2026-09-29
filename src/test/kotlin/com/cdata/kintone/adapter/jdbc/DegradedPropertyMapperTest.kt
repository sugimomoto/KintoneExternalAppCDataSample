package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DegradedPropertyMapperTest {

    private fun prop(
        name: String,
        required: Boolean = false,
        description: String = "",
        allowedValues: List<String> = emptyList(),
    ) = DriverProperty(name, description, required, allowedValues)

    @Test
    fun `名前と説明を引き継ぐ`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(
            listOf(prop("Namespace", description = "OData サービスの名前空間")),
        ).single()
        assertEquals("Namespace", mapped.propertyName)
        assertEquals("Namespace", mapped.displayName)
        assertEquals("OData サービスの名前空間", mapped.shortDescription)
    }

    @Test
    fun `必須フラグを引き継ぐ`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(
            listOf(prop("URL", required = true), prop("BatchSize")),
        )
        assertEquals(listOf(true, false), mapped.map { it.required })
    }

    @Test
    fun `機密を示す名前は PASSWORD 扱いにする`() {
        val names = listOf("Password", "OAuthAccessToken", "APIKey", "OAuthClientSecret", "PersonalAccessToken")
        val mapped = DegradedPropertyMapper.toConnectionProperties(names.map { prop(it) })
        mapped.forEach {
            assertEquals(Sensitivity.PASSWORD, it.sensitivity, "平文入力欄にしてはいけない: ${it.propertyName}")
        }
    }

    @Test
    fun `機密でない名前は NONE にする`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(
            listOf(prop("User"), prop("URL"), prop("OAuthClientId")),
        )
        mapped.forEach { assertEquals(Sensitivity.NONE, it.sensitivity, "対象: ${it.propertyName}") }
    }

    @Test
    fun `既定値を持ち込まない`() {
        // getPropertyInfo の value にはプローブで渡したダミー値が入り得るため使わない。
        val mapped = DegradedPropertyMapper.toConnectionProperties(listOf(prop("URL", required = true))).single()
        assertNull(mapped.defaultValue)
    }

    @Test
    fun `選択肢があれば引き継ぐ`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(
            listOf(prop("AuthScheme", allowedValues = listOf("Basic", "OAuth"))),
        ).single()
        assertEquals(listOf("Basic", "OAuth"), mapped.allowedValues)
    }

    @Test
    fun `元の順序を ordinal に保つ`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(
            listOf(prop("A"), prop("B"), prop("C")),
        )
        assertEquals(listOf(0, 1, 2), mapped.map { it.ordinal })
        assertEquals(listOf("A", "B", "C"), mapped.map { it.propertyName })
    }

    @Test
    fun `取得できない情報は空にする`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(listOf(prop("URL"))).single()
        assertEquals("", mapped.category, "getPropertyInfo は Category を返さない")
        assertEquals("", mapped.hierarchy, "getPropertyInfo は Hierarchy を返さない")
        assertEquals(PropertyType.STRING, mapped.type, "型情報が無いので文字列として扱う")
    }

    @Test
    fun `全件を表示対象にする`() {
        val mapped = DegradedPropertyMapper.toConnectionProperties(listOf(prop("URL"), prop("BatchSize")))
        assertTrue(mapped.all { it.visible }, "隠す判断材料が無いので全件出す")
    }

    @Test
    fun `空リストは空リストになる`() {
        assertEquals(emptyList<ConnectionProperty>(), DegradedPropertyMapper.toConnectionProperties(emptyList()))
    }
}
