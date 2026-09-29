package com.cdata.kintone.adapter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * データソース接続の参照チェック [JdbcReferenceIndex] のテスト。
 *
 * `tables.jdbc_ref` に外部キー制約が無いため、削除前の参照チェックをアプリ層で行う。
 * 参照中の接続を消すと連携の `loadTableSet` が `ConfigParseException` で失敗する。
 *
 * 関連: [Issue #36](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/36)
 */
class JdbcReferenceIndexTest {

    @Test
    fun `tablesReferencing - 参照している連携を返す`() {
        val refs = mapOf("account" to "Salesforce1", "orders" to "Bart")
        assertEquals(listOf("account"), JdbcReferenceIndex.tablesReferencing(refs, "Salesforce1"))
    }

    @Test
    fun `tablesReferencing - 複数参照していれば全部返す`() {
        val refs = mapOf(
            "opportunity" to "Salesforce1",
            "account" to "Salesforce1",
            "orders" to "Bart",
        )
        // 表示順とテストを安定させるためソートされている
        assertEquals(
            listOf("account", "opportunity"),
            JdbcReferenceIndex.tablesReferencing(refs, "Salesforce1"),
        )
    }

    @Test
    fun `tablesReferencing - 参照が無ければ空リスト`() {
        val refs = mapOf("account" to "Salesforce1")
        assertTrue(JdbcReferenceIndex.tablesReferencing(refs, "GoogleSheetsOAuth").isEmpty())
    }

    @Test
    fun `tablesReferencing - inline JDBC の連携は挙がらない`() {
        // jdbc_ref が null の連携は接続設定を参照していない
        val refs = mapOf("account" to null, "orders" to null)
        assertTrue(JdbcReferenceIndex.tablesReferencing(refs, "Salesforce1").isEmpty())
    }

    @Test
    fun `tablesReferencing - 大文字小文字が違っても参照として扱う`() {
        // 厳密一致にすると表記違いの参照を見落として削除を許してしまう
        val refs = mapOf("account" to "salesforce1")
        assertEquals(listOf("account"), JdbcReferenceIndex.tablesReferencing(refs, "Salesforce1"))
    }

    @Test
    fun `tablesReferencing - 連携が 0 件なら空リスト`() {
        assertTrue(JdbcReferenceIndex.tablesReferencing(emptyMap(), "Salesforce1").isEmpty())
    }

    @Test
    fun `tablesReferencing - 部分一致では参照としない`() {
        val refs = mapOf("account" to "Salesforce1")
        assertTrue(JdbcReferenceIndex.tablesReferencing(refs, "Salesforce").isEmpty())
    }
}
