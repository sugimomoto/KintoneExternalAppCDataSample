package com.cdata.kintone.adapter.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

/**
 * 自動生成列の判定 [GeneratedColumns] のテスト。
 *
 * DB 側で値が決まる列を kintone の入力項目として出すと、空のまま登録されて必ず失敗する。
 * 空文字は `uniqueidentifier` への変換エラーになり、NULL は `NOT NULL` 制約違反になる。
 * 列自体を送らなければ既定値が効くため、マッピング対象から外す。
 *
 * 関連: [Issue #75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75)
 */
class GeneratedColumnsTest {

    private fun column(
        name: String,
        defaultValue: String? = null,
        autoIncrement: Boolean = false,
        generated: Boolean = false,
    ) = ColumnInfo(
        name = name,
        jdbcType = Types.VARCHAR,
        typeName = "varchar",
        nullable = false,
        defaultValue = defaultValue,
        autoIncrement = autoIncrement,
        generated = generated,
    )

    @Test
    fun `自動採番列は AUTO_INCREMENT として除外する`() {
        assertEquals(
            ExclusionReason.AUTO_INCREMENT,
            GeneratedColumns.reasonFor(column("CustomerID", autoIncrement = true)),
        )
    }

    @Test
    fun `計算列は GENERATED として除外する`() {
        assertEquals(
            ExclusionReason.GENERATED,
            GeneratedColumns.reasonFor(column("FullName", generated = true)),
        )
    }

    @Test
    fun `既定値を持つ列は DEFAULT_VALUE として除外する`() {
        assertEquals(
            ExclusionReason.DEFAULT_VALUE,
            GeneratedColumns.reasonFor(column("rowguid", defaultValue = "(newid())")),
        )
    }

    @Test
    fun `リテラルの既定値も除外する`() {
        // NOT NULL かつ既定値の列は、kintone から空で送られると NULL 制約違反になる。
        // 関数呼び出しかリテラルかを文字列解析で区別する判定はドライバー依存で脆い。
        assertEquals(
            ExclusionReason.DEFAULT_VALUE,
            GeneratedColumns.reasonFor(column("NameStyle", defaultValue = "((0))")),
        )
    }

    @Test
    fun `自動採番かつ既定値を持つ列は AUTO_INCREMENT を理由にする`() {
        // より具体的な理由を出す。「既定値あり」では自動採番だと分からない。
        assertEquals(
            ExclusionReason.AUTO_INCREMENT,
            GeneratedColumns.reasonFor(column("Id", defaultValue = "(next value for s)", autoIncrement = true)),
        )
    }

    @Test
    fun `既定値が空文字の列は除外しない`() {
        // 既定値が無いことを空文字で表すドライバーがある。
        assertNull(GeneratedColumns.reasonFor(column("LastName", defaultValue = "")))
    }

    @Test
    fun `既定値が空白のみの列は除外しない`() {
        assertNull(GeneratedColumns.reasonFor(column("LastName", defaultValue = "   ")))
    }

    @Test
    fun `通常の列は除外しない`() {
        assertNull(GeneratedColumns.reasonFor(column("LastName")))
    }

    @Test
    fun `partition が選択候補と除外列に分ける`() {
        val result = GeneratedColumns.partition(
            listOf(
                column("CustomerID", autoIncrement = true),
                column("FirstName"),
                column("rowguid", defaultValue = "(newid())"),
                column("LastName"),
            ),
        )

        assertEquals(listOf("FirstName", "LastName"), result.selectable.map { it.name })
        assertEquals(listOf("CustomerID", "rowguid"), result.excluded.map { it.column.name })
        assertEquals(
            listOf(ExclusionReason.AUTO_INCREMENT, ExclusionReason.DEFAULT_VALUE),
            result.excluded.map { it.reason },
        )
    }

    @Test
    fun `partition は入力の順序を保つ`() {
        val result = GeneratedColumns.partition(
            listOf(column("C"), column("A"), column("B")),
        )

        assertEquals(listOf("C", "A", "B"), result.selectable.map { it.name })
    }

    @Test
    fun `自動生成列が無ければ候補は減らない`() {
        // SaaS 系のように既定値を申告しないデータソースで候補が減らないこと。
        val columns = listOf(column("Id"), column("Name"), column("Email"))

        val result = GeneratedColumns.partition(columns)

        assertEquals(columns, result.selectable)
        assertTrue(result.excluded.isEmpty(), "実際: ${result.excluded}")
    }

    @Test
    fun `空のリストを渡しても落ちない`() {
        val result = GeneratedColumns.partition(emptyList())

        assertTrue(result.selectable.isEmpty())
        assertTrue(result.excluded.isEmpty())
    }
}
