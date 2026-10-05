package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `QueryPassthrough` の注意書きが必要かを判定する [QueryPassthroughAdvice] のテスト。
 *
 * SQL Server ドライバーは既定で `QueryPassthrough=true`、つまりクエリを素通しする。
 * すると T-SQL の文法が適用され `QueryBuilder` の `LIMIT ? OFFSET ?` が通らず、
 * **kintone からレコードを読んだ時点で初めて失敗する**（接続テストもウィザードも通る）。
 *
 * 判定は `propertyName`（`QueryPassthrough`）で行う。`displayName` は
 * `Query Passthrough`（スペース入り）なので一致しない。
 *
 * 関連: [Issue #69](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/69)
 */
class QueryPassthroughAdviceTest {

    private fun property(name: String, default: String?) = ConnectionProperty(
        propertyName = name,
        displayName = name,
        shortDescription = "",
        type = PropertyType.BOOLEAN,
        defaultValue = default,
        allowedValues = emptyList(),
        category = "",
        required = false,
        sensitivity = Sensitivity.NONE,
        visible = true,
        hierarchy = "",
        ordinal = 0,
        categoryOrdinal = 0,
    )

    private val passthroughTrue = listOf(property("QueryPassthrough", "true"))

    @Test
    fun `既定が true で未設定なら注意書きが必要`() {
        assertTrue(
            QueryPassthroughAdvice.isNeeded(passthroughTrue, "jdbc:sql:Server=x;Database=y;"),
        )
    }

    @Test
    fun `接続文字列で False に設定済みなら不要`() {
        // 対処済みの警告を出し続けると他の警告を見落とす。
        assertFalse(
            QueryPassthroughAdvice.isNeeded(passthroughTrue, "jdbc:sql:Server=x;QueryPassthrough=False;"),
        )
    }

    @Test
    fun `接続文字列で True に設定済みでも不要`() {
        // 利用者が明示的に選んだ場合は口を出さない。
        assertFalse(
            QueryPassthroughAdvice.isNeeded(passthroughTrue, "jdbc:sql:Server=x;QueryPassthrough=True;"),
        )
    }

    @Test
    fun `大文字小文字が違う明示設定も検出する`() {
        assertFalse(
            QueryPassthroughAdvice.isNeeded(passthroughTrue, "jdbc:sql:querypassthrough=false;Server=x;"),
        )
    }

    @Test
    fun `最初のプロパティに書かれた明示設定も検出する`() {
        // CData の接続文字列は jdbc:<product>:<最初のプロパティ>=... の形。
        assertFalse(
            QueryPassthroughAdvice.isNeeded(passthroughTrue, "jdbc:sql:QueryPassthrough=False;Server=x;"),
        )
    }

    @Test
    fun `プロパティを持たないコネクタでは不要`() {
        // SaaS コネクタは素通しの概念が無く、注意書きもノイズになる。
        val saas = listOf(property("AuthScheme", "OAuth"), property("SpreadsheetId", null))

        assertFalse(QueryPassthroughAdvice.isNeeded(saas, "jdbc:googlesheets:AuthScheme=OAuth;"))
    }

    @Test
    fun `既定が false のコネクタでは不要`() {
        val passthroughFalse = listOf(property("QueryPassthrough", "false"))

        assertFalse(QueryPassthroughAdvice.isNeeded(passthroughFalse, "jdbc:x:Server=y;"))
    }

    @Test
    fun `既定値が無い場合は不要`() {
        val noDefault = listOf(property("QueryPassthrough", null))

        assertFalse(QueryPassthroughAdvice.isNeeded(noDefault, "jdbc:x:Server=y;"))
    }

    @Test
    fun `displayName では判定しない`() {
        // Name 列は表示名（Query Passthrough）。propertyName で引く必要がある。
        val displayOnly = listOf(property("Query Passthrough", "true"))

        assertFalse(QueryPassthroughAdvice.isNeeded(displayOnly, "jdbc:x:Server=y;"))
    }

    @Test
    fun `注意書きに設定方法と放置した場合の結果が含まれる`() {
        val message = QueryPassthroughAdvice.MESSAGE

        assertTrue(message.contains("QueryPassthrough"), "実際: $message")
        assertTrue(message.contains("False"), "実際: $message")
        assertTrue(message.contains("レコード"), "放置した場合の結果がない: $message")
    }
}
