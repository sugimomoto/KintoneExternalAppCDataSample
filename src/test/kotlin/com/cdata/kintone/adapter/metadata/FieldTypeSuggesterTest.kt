package com.cdata.kintone.adapter.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Types

class FieldTypeSuggesterTest {

    @Test
    fun `VARCHAR は TEXT として推奨される`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.VARCHAR))
    }

    @Test
    fun `CHAR は TEXT として推奨される`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.CHAR))
    }

    @Test
    fun `LONGVARCHAR は TEXT として推奨される`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.LONGVARCHAR))
    }

    @Test
    fun `NVARCHAR は TEXT として推奨される`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.NVARCHAR))
    }

    @Test
    fun `NCHAR は TEXT として推奨される`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.NCHAR))
    }

    @Test
    fun `LONGNVARCHAR は TEXT として推奨される`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.LONGNVARCHAR))
    }

    @Test
    fun `BIGINT は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.BIGINT))
    }

    @Test
    fun `INTEGER は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.INTEGER))
    }

    @Test
    fun `SMALLINT は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.SMALLINT))
    }

    @Test
    fun `TINYINT は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.TINYINT))
    }

    @Test
    fun `DECIMAL は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.DECIMAL))
    }

    @Test
    fun `NUMERIC は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.NUMERIC))
    }

    @Test
    fun `FLOAT は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.FLOAT))
    }

    @Test
    fun `DOUBLE は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.DOUBLE))
    }

    @Test
    fun `REAL は NUMBER として推奨される`() {
        assertEquals(ColumnType.NUMBER, FieldTypeSuggester.suggest(Types.REAL))
    }

    @Test
    fun `TIMESTAMP は DATETIME として推奨される`() {
        assertEquals(ColumnType.DATETIME, FieldTypeSuggester.suggest(Types.TIMESTAMP))
    }

    @Test
    fun `DATE は DATETIME として推奨される`() {
        assertEquals(ColumnType.DATETIME, FieldTypeSuggester.suggest(Types.DATE))
    }

    @Test
    fun `TIME は DATETIME として推奨される`() {
        assertEquals(ColumnType.DATETIME, FieldTypeSuggester.suggest(Types.TIME))
    }

    @Test
    fun `TIMESTAMP_WITH_TIMEZONE は DATETIME として推奨される`() {
        assertEquals(ColumnType.DATETIME, FieldTypeSuggester.suggest(Types.TIMESTAMP_WITH_TIMEZONE))
    }

    @Test
    fun `未知の型は TEXT にフォールバック`() {
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.BINARY))
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.BLOB))
        assertEquals(ColumnType.TEXT, FieldTypeSuggester.suggest(Types.OTHER))
    }

    @Test
    fun `主キーが VARCHAR なら TEXT 型 RecordId として推奨`() {
        assertEquals(RecordIdType.TEXT, FieldTypeSuggester.suggestRecordIdType(Types.VARCHAR))
    }

    @Test
    fun `主キーが CHAR なら TEXT 型 RecordId として推奨`() {
        assertEquals(RecordIdType.TEXT, FieldTypeSuggester.suggestRecordIdType(Types.CHAR))
    }

    @Test
    fun `主キーが BIGINT なら NUMBER 型 RecordId として推奨`() {
        assertEquals(RecordIdType.NUMBER, FieldTypeSuggester.suggestRecordIdType(Types.BIGINT))
    }

    @Test
    fun `主キーが INTEGER なら NUMBER 型 RecordId として推奨`() {
        assertEquals(RecordIdType.NUMBER, FieldTypeSuggester.suggestRecordIdType(Types.INTEGER))
    }

    @Test
    fun `主キーが対応外の型ならエラー`() {
        assertThrows<IllegalStateException> {
            FieldTypeSuggester.suggestRecordIdType(Types.BLOB)
        }
    }

    // --- canBeRecordId (Issue #66) ---

    @Test
    fun `canBeRecordId - 整数型は候補になる`() {
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.BIGINT))
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.INTEGER))
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.SMALLINT))
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.TINYINT))
    }

    @Test
    fun `canBeRecordId - 文字列型は候補になる`() {
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.VARCHAR))
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.CHAR))
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.NVARCHAR))
        assertTrue(FieldTypeSuggester.canBeRecordId(Types.LONGVARCHAR))
    }

    @Test
    fun `canBeRecordId - 日時や真偽値は候補にならない`() {
        // kintone のレコード番号は BIGINT 系か VARCHAR 系のみ。
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.TIMESTAMP))
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.DATE))
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.BOOLEAN))
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.BLOB))
    }

    @Test
    fun `canBeRecordId - 小数型は候補にならない`() {
        // NumberField としては使えるが、レコード番号には使えない。
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.DECIMAL))
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.DOUBLE))
        assertFalse(FieldTypeSuggester.canBeRecordId(Types.FLOAT))
    }

    @Test
    fun `canBeRecordId と suggestRecordIdType の対応表が一致する`() {
        // 対応表を 2 箇所に書かないための回帰テスト。
        val allTypes = listOf(
            Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT,
            Types.VARCHAR, Types.CHAR, Types.NVARCHAR, Types.NCHAR,
            Types.LONGVARCHAR, Types.LONGNVARCHAR,
            Types.TIMESTAMP, Types.DATE, Types.BOOLEAN, Types.BLOB,
            Types.DECIMAL, Types.DOUBLE, Types.FLOAT, Types.REAL,
        )
        allTypes.forEach { type ->
            val canBe = FieldTypeSuggester.canBeRecordId(type)
            val suggests = runCatching { FieldTypeSuggester.suggestRecordIdType(type) }.isSuccess
            assertEquals(canBe, suggests, "型 $type で判定がずれている")
        }
    }
}
