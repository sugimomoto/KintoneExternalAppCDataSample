package com.cdata.kintone.adapter.metadata

import org.junit.jupiter.api.Assertions.assertEquals
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
}
