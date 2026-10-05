package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SQL 識別子のクォート [SqlIdentifier] のテスト。
 *
 * スキーマ修飾を [SqlIdentifier.quote] に任せず別関数にしているのは、`quote` が
 * 識別子 1 つをクォートする責務で、正規のテーブル名にドットが含まれる場合に
 * 壊れるため。
 *
 * 関連: [Issue #63](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/63)
 */
class SqlIdentifierTest {

    // --- quote ---

    @Test
    fun `quote - 識別子を角括弧で囲む`() {
        assertEquals("[Customer]", SqlIdentifier.quote("Customer"))
    }

    @Test
    fun `quote - ドットを含む名前も 1 つの識別子として扱う`() {
        // これが qualified を別関数にしている理由。quote はドットで分割しない。
        assertEquals("[SalesLT.Customer]", SqlIdentifier.quote("SalesLT.Customer"))
    }

    @Test
    fun `quote - 既にクォート済みなら二重にしない`() {
        assertEquals("[CRM Data]", SqlIdentifier.quote("[CRM Data]"))
    }

    @Test
    fun `quote - 閉じ括弧をエスケープする`() {
        assertEquals("[a]]b]", SqlIdentifier.quote("a]b"))
    }

    // --- qualified (Issue #63) ---

    @Test
    fun `qualified - スキーマがあれば修飾する`() {
        assertEquals("[SalesLT].[Customer]", SqlIdentifier.qualified("SalesLT", "Customer"))
    }

    @Test
    fun `qualified - スキーマが null なら修飾しない`() {
        // スキーマを持たないデータソース、およびスキーマ無しの既存設定（後方互換）。
        assertEquals("[Customer]", SqlIdentifier.qualified(null, "Customer"))
    }

    @Test
    fun `qualified - スキーマが空文字や空白なら修飾しない`() {
        assertEquals("[Customer]", SqlIdentifier.qualified("", "Customer"))
        assertEquals("[Customer]", SqlIdentifier.qualified("   ", "Customer"))
    }

    @Test
    fun `qualified - スキーマ名もエスケープする`() {
        assertEquals("[a]]b].[Customer]", SqlIdentifier.qualified("a]b", "Customer"))
    }

    @Test
    fun `qualified - 既にクォート済みの値を二重クォートしない`() {
        assertEquals("[My Schema].[CRM Data]", SqlIdentifier.qualified("[My Schema]", "[CRM Data]"))
    }
}
