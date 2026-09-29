package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PropertyHierarchyTest {

    @Test
    fun `依存プロパティ名と許可値を取り出す`() {
        val hierarchy = PropertyHierarchy.parse("AuthScheme=Basic,OAuthPassword,OKTA")
        assertEquals("AuthScheme", hierarchy?.dependsOn)
        assertEquals(listOf("Basic", "OAuthPassword", "OKTA"), hierarchy?.allowedValues)
    }

    @Test
    fun `許可値が 1 個でもパースできる`() {
        val hierarchy = PropertyHierarchy.parse("UseBulkAPI=True")
        assertEquals("UseBulkAPI", hierarchy?.dependsOn)
        assertEquals(listOf("True"), hierarchy?.allowedValues)
    }

    @Test
    fun `前後の空白を取り除く`() {
        val hierarchy = PropertyHierarchy.parse(" AuthScheme = Basic , OAuth ")
        assertEquals("AuthScheme", hierarchy?.dependsOn)
        assertEquals(listOf("Basic", "OAuth"), hierarchy?.allowedValues)
    }

    @Test
    fun `空文字は条件なしとして null を返す`() {
        assertNull(PropertyHierarchy.parse(""))
        assertNull(PropertyHierarchy.parse("   "))
    }

    @Test
    fun `区切りが無い文字列は null を返す`() {
        assertNull(PropertyHierarchy.parse("AuthScheme"))
    }

    @Test
    fun `依存プロパティ名が空なら null を返す`() {
        assertNull(PropertyHierarchy.parse("=Basic"))
    }

    @Test
    fun `許可値が空なら null を返す`() {
        assertNull(PropertyHierarchy.parse("AuthScheme="))
    }

    @Test
    fun `accepts は大文字小文字を無視する`() {
        val hierarchy = PropertyHierarchy.parse("AuthScheme=OKTA,Basic")!!
        assertTrue(hierarchy.accepts("okta"), "同じドライバー内で OKTA と Okta の表記揺れがある")
        assertTrue(hierarchy.accepts("BASIC"))
        assertFalse(hierarchy.accepts("OAuth"))
    }

    @Test
    fun `accepts は空文字を受け付けない`() {
        assertFalse(PropertyHierarchy.parse("AuthScheme=Basic")!!.accepts(""))
    }
}
