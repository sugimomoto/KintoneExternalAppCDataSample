package com.cdata.kintone.adapter.jdbc

import build.buf.gen.cybozu.data_connector.adapter.v1.DatetimeField
import build.buf.gen.cybozu.data_connector.adapter.v1.Field
import build.buf.gen.cybozu.data_connector.adapter.v1.NumberField
import build.buf.gen.cybozu.data_connector.adapter.v1.Option
import build.buf.gen.cybozu.data_connector.adapter.v1.Record
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordId
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordIdField
import build.buf.gen.cybozu.data_connector.adapter.v1.SelectionField
import build.buf.gen.cybozu.data_connector.adapter.v1.TextField
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.google.protobuf.Timestamp
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.ResultSet
import java.sql.Timestamp as SqlTimestamp
import java.time.Instant

class RowMapperTest {

    private val tableConfig = TableConfig(
        name = "Account",
        primaryKey = PrimaryKeyConfig("id", "Id"),
        columns = listOf(
            ColumnConfig("name", "Name", ColumnType.TEXT),
            ColumnConfig("revenue", "AnnualRevenue", ColumnType.NUMBER),
            ColumnConfig("created_at", "CreatedDate", ColumnType.DATETIME),
            ColumnConfig("industry", "Industry", ColumnType.SELECTION, options = listOf("Banking", "Retail")),
        ),
    )

    @Test
    fun `RecordIdField NUMBER`() {
        val mapper = RowMapper(tableConfig, RecordIdType.NUMBER)
        val rs = mockk<ResultSet>()
        every { rs.getObject("Id") } returns 100L
        every { rs.getObject("Name") } returns null
        every { rs.getObject("AnnualRevenue") } returns null
        every { rs.getObject("CreatedDate") } returns null
        every { rs.getObject("Industry") } returns null
        every { rs.wasNull() } returnsMany listOf(false, true, true, true, true)

        val record = mapper.resultSetToRecord(rs)
        assertTrue(record.containsFields("id"))
        assertEquals(100L, record.getFieldsOrThrow("id").recordIdField.value)
    }

    @Test
    fun `RecordIdField TEXT`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val rs = mockk<ResultSet>()
        every { rs.getObject("Id") } returns "001xx000003DIlo"
        every { rs.getObject("Name") } returns null
        every { rs.getObject("AnnualRevenue") } returns null
        every { rs.getObject("CreatedDate") } returns null
        every { rs.getObject("Industry") } returns null
        every { rs.wasNull() } returnsMany listOf(false, true, true, true, true)

        val record = mapper.resultSetToRecord(rs)
        assertEquals("001xx000003DIlo", record.getFieldsOrThrow("id").recordIdField.recordIdValue.valueText)
    }

    @Test
    fun `TextField 非NULL`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val rs = mockk<ResultSet>()
        every { rs.getObject("Id") } returns "1"
        every { rs.getObject("Name") } returns "Acme Corp"
        every { rs.getObject("AnnualRevenue") } returns null
        every { rs.getObject("CreatedDate") } returns null
        every { rs.getObject("Industry") } returns null
        every { rs.wasNull() } returnsMany listOf(false, false, true, true, true)

        val record = mapper.resultSetToRecord(rs)
        assertEquals("Acme Corp", record.getFieldsOrThrow("name").textField.value)
    }

    @Test
    fun `TextField NULL でも Record に含まれ value は空文字列`() {
        // kintone は要求した全フィールドが Record に含まれていることを期待するため、
        // NULL でもフィールド自体は省略しない。
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val rs = mockk<ResultSet>()
        every { rs.getObject("Id") } returns "1"
        every { rs.getObject("Name") } returns null
        every { rs.getObject("AnnualRevenue") } returns null
        every { rs.getObject("CreatedDate") } returns null
        every { rs.getObject("Industry") } returns null
        every { rs.wasNull() } returns false

        val record = mapper.resultSetToRecord(rs)
        assertTrue(record.containsFields("name"))
        assertEquals("", record.getFieldsOrThrow("name").textField.value)
        // NUMBER は optional なので hasValue() が false
        assertTrue(record.containsFields("revenue"))
        assertFalse(record.getFieldsOrThrow("revenue").numberField.hasValue())
    }

    @Test
    fun `NumberField BigDecimal を double に変換`() {
        // wasNull() は non-null の getObject の後にのみ呼ばれる: Id と AnnualRevenue の 2 回
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val rs = mockk<ResultSet>()
        every { rs.getObject("Id") } returns "1"
        every { rs.getObject("Name") } returns null
        every { rs.getObject("AnnualRevenue") } returns java.math.BigDecimal("12345.67")
        every { rs.getObject("CreatedDate") } returns null
        every { rs.getObject("Industry") } returns null
        every { rs.wasNull() } returnsMany listOf(false, false)

        val record = mapper.resultSetToRecord(rs)
        assertEquals(12345.67, record.getFieldsOrThrow("revenue").numberField.value)
    }

    @Test
    fun `DatetimeField TIMESTAMP`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val rs = mockk<ResultSet>()
        val instant = Instant.parse("2026-05-15T10:00:00Z")
        every { rs.getObject("Id") } returns "1"
        every { rs.getObject("Name") } returns null
        every { rs.getObject("AnnualRevenue") } returns null
        every { rs.getObject("CreatedDate") } returns SqlTimestamp.from(instant)
        every { rs.getObject("Industry") } returns null
        every { rs.wasNull() } returnsMany listOf(false, false)

        val record = mapper.resultSetToRecord(rs)
        val ts = record.getFieldsOrThrow("created_at").datetimeField.value
        assertEquals(instant.epochSecond, ts.seconds)
    }

    @Test
    fun `SelectionField`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val rs = mockk<ResultSet>()
        every { rs.getObject("Id") } returns "1"
        every { rs.getObject("Name") } returns null
        every { rs.getObject("AnnualRevenue") } returns null
        every { rs.getObject("CreatedDate") } returns null
        every { rs.getObject("Industry") } returns "Banking"
        every { rs.wasNull() } returnsMany listOf(false, false)

        val record = mapper.resultSetToRecord(rs)
        assertEquals("Banking", record.getFieldsOrThrow("industry").selectionField.value.value)
    }

    @Test
    fun `recordToColumnValues 各フィールド型を value に変換`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val record = Record.newBuilder()
            .putFields(
                "id",
                Field.newBuilder()
                    .setRecordIdField(
                        RecordIdField.newBuilder()
                            .setFieldId("id")
                            .setRecordIdValue(RecordId.newBuilder().setValueText("001xx").build()),
                    )
                    .build(),
            )
            .putFields(
                "name",
                Field.newBuilder().setTextField(TextField.newBuilder().setFieldId("name").setValue("Acme")).build(),
            )
            .putFields(
                "revenue",
                Field.newBuilder().setNumberField(NumberField.newBuilder().setFieldId("revenue").setValue(100.0)).build(),
            )
            .putFields(
                "created_at",
                Field.newBuilder()
                    .setDatetimeField(
                        DatetimeField.newBuilder()
                            .setFieldId("created_at")
                            .setValue(Timestamp.newBuilder().setSeconds(1_700_000_000L)),
                    )
                    .build(),
            )
            .putFields(
                "industry",
                Field.newBuilder()
                    .setSelectionField(
                        SelectionField.newBuilder()
                            .setFieldId("industry")
                            .setValue(Option.newBuilder().setValue("Banking")),
                    )
                    .build(),
            )
            .build()

        val values = mapper.recordToColumnValues(record)
        assertEquals("001xx", values["id"])
        assertEquals("Acme", values["name"])
        assertEquals(100.0, values["revenue"])
        // java.sql.Timestamp ではなく文字列。CData の SQL Server ドライバーが
        // Timestamp を解釈できない文字列に変換するため (Issue #71)。
        assertEquals(
            SqlDateTime.format(Timestamp.newBuilder().setSeconds(1_700_000_000L).build()),
            values["created_at"],
        )
        assertEquals("Banking", values["industry"])
    }

    @Test
    fun `extractRecordId NUMBER`() {
        val mapper = RowMapper(tableConfig, RecordIdType.NUMBER)
        val record = Record.newBuilder()
            .putFields(
                "id",
                Field.newBuilder().setRecordIdField(RecordIdField.newBuilder().setFieldId("id").setValue(42L)).build(),
            )
            .build()
        assertEquals(42L, mapper.extractRecordId(record))
    }

    @Test
    fun `extractRecordId TEXT`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val record = Record.newBuilder()
            .putFields(
                "id",
                Field.newBuilder()
                    .setRecordIdField(
                        RecordIdField.newBuilder()
                            .setFieldId("id")
                            .setRecordIdValue(RecordId.newBuilder().setValueText("001xx").build()),
                    )
                    .build(),
            )
            .build()
        assertEquals("001xx", mapper.extractRecordId(record))
    }

    @Test
    fun `extractRecordId なしのとき null`() {
        val mapper = RowMapper(tableConfig, RecordIdType.TEXT)
        val record = Record.newBuilder().build()
        assertNull(mapper.extractRecordId(record))
    }
}
