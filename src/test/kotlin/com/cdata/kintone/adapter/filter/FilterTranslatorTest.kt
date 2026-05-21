package com.cdata.kintone.adapter.filter

import build.buf.gen.cybozu.data_connector.adapter.v1.FilterCondition
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionAllRecords
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeGreaterThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeGreaterThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeInRange
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeLessThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeLessThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeNotEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeNotInRange
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionMultipleSelectionIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberGreaterThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberGreaterThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberLessThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberLessThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberNotEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionNumberNotIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdContains
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdGreaterThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdGreaterThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdLessThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdLessThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdNotContains
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdNotEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionRecordIdNotIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionSelectionIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionSelectionNotIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextContains
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextIn
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextIs
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextIsNot
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextNotContains
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextNotEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionTextNotIn
import build.buf.gen.cybozu.data_connector.adapter.v1.MatchOperator
import build.buf.gen.cybozu.data_connector.adapter.v1.NullableOption
import build.buf.gen.cybozu.data_connector.adapter.v1.Option
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordId
import build.buf.gen.cybozu.data_connector.adapter.v1.TextFieldValueState
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.metadata.ColumnType
import com.google.protobuf.Timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.sql.Timestamp as SqlTimestamp

@Suppress("LargeClass")
class FilterTranslatorTest {

    private val tableConfig = TableConfig(
        name = "Account",
        primaryKey = PrimaryKeyConfig(kintoneFieldId = "id", jdbcColumn = "Id"),
        columns = listOf(
            ColumnConfig(kintoneFieldId = "name", jdbcColumn = "Name", type = ColumnType.TEXT),
            ColumnConfig(kintoneFieldId = "revenue", jdbcColumn = "AnnualRevenue", type = ColumnType.NUMBER),
            ColumnConfig(kintoneFieldId = "created_at", jdbcColumn = "CreatedDate", type = ColumnType.DATETIME),
            ColumnConfig(
                kintoneFieldId = "industry",
                jdbcColumn = "Industry",
                type = ColumnType.SELECTION,
                options = listOf("Banking", "Manufacturing", "Retail"),
            ),
        ),
    )

    private val translator = FilterTranslator(tableConfig)

    private fun fc(builder: FilterCondition.Builder.() -> Unit): FilterCondition =
        FilterCondition.newBuilder().apply(builder).build()

    private fun timestamp(epochSec: Long): Timestamp = Timestamp.newBuilder().setSeconds(epochSec).build()

    private fun sqlTimestamp(epochSec: Long): SqlTimestamp = SqlTimestamp.from(Instant.ofEpochSecond(epochSec))

    // ===== 基盤 =====

    @Test
    fun `空条件のときは ALL_RECORDS を返す`() {
        val result = translator.translate(emptyList(), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("1=1", result.sql)
        assertTrue(result.params.isEmpty())
    }

    @Test
    fun `all_records 単独で 1=1 を返す`() {
        val cond = fc { allRecords = FilterConditionAllRecords.getDefaultInstance() }
        val result = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(1=1)", result.sql)
        assertTrue(result.params.isEmpty())
    }

    // ===== record_id 系（10ケース）=====

    @Test
    fun `record_id_equal NUMBER`() {
        val cond = fc {
            recordIdEqual = FilterConditionRecordIdEqual.newBuilder()
                .setFieldId("id").setValue(123L).build()
        }
        val result = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id = ?)", result.sql)
        assertEquals(listOf<Any>(123L), result.params)
    }

    @Test
    fun `record_id_equal TEXT`() {
        val cond = fc {
            recordIdEqual = FilterConditionRecordIdEqual.newBuilder()
                .setFieldId("id")
                .setRecordIdValue(RecordId.newBuilder().setValueText("001xx").build())
                .build()
        }
        val result = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id = ?)", result.sql)
        assertEquals(listOf<Any>("001xx"), result.params)
    }

    @Test
    fun `record_id_not_equal NUMBER`() {
        val cond = fc {
            recordIdNotEqual = FilterConditionRecordIdNotEqual.newBuilder()
                .setFieldId("id").setValue(5L).build()
        }
        val result = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id <> ?)", result.sql)
        assertEquals(listOf<Any>(5L), result.params)
    }

    @Test
    fun `record_id_greater_than`() {
        val cond = fc {
            recordIdGreaterThan = FilterConditionRecordIdGreaterThan.newBuilder()
                .setFieldId("id").setValue(10L).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id > ?)", r.sql)
        assertEquals(listOf<Any>(10L), r.params)
    }

    @Test
    fun `record_id_greater_than_or_equal`() {
        val cond = fc {
            recordIdGreaterThanOrEqual = FilterConditionRecordIdGreaterThanOrEqual.newBuilder()
                .setFieldId("id").setValue(10L).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id >= ?)", r.sql)
        assertEquals(listOf<Any>(10L), r.params)
    }

    @Test
    fun `record_id_less_than`() {
        val cond = fc {
            recordIdLessThan = FilterConditionRecordIdLessThan.newBuilder()
                .setFieldId("id").setValue(10L).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id < ?)", r.sql)
        assertEquals(listOf<Any>(10L), r.params)
    }

    @Test
    fun `record_id_less_than_or_equal`() {
        val cond = fc {
            recordIdLessThanOrEqual = FilterConditionRecordIdLessThanOrEqual.newBuilder()
                .setFieldId("id").setValue(10L).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id <= ?)", r.sql)
        assertEquals(listOf<Any>(10L), r.params)
    }

    @Test
    fun `record_id_in NUMBER values`() {
        val cond = fc {
            recordIdIn = FilterConditionRecordIdIn.newBuilder()
                .setFieldId("id").addAllValues(listOf(1L, 2L, 3L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id IN (?, ?, ?))", r.sql)
        assertEquals(listOf<Any>(1L, 2L, 3L), r.params)
    }

    @Test
    fun `record_id_in TEXT values`() {
        val cond = fc {
            recordIdIn = FilterConditionRecordIdIn.newBuilder()
                .setFieldId("id")
                .addRecordIdValues(RecordId.newBuilder().setValueText("a").build())
                .addRecordIdValues(RecordId.newBuilder().setValueText("b").build())
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id IN (?, ?))", r.sql)
        assertEquals(listOf<Any>("a", "b"), r.params)
    }

    @Test
    fun `record_id_not_in`() {
        val cond = fc {
            recordIdNotIn = FilterConditionRecordIdNotIn.newBuilder()
                .setFieldId("id").addAllValues(listOf(1L, 2L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id NOT IN (?, ?))", r.sql)
        assertEquals(listOf<Any>(1L, 2L), r.params)
    }

    @Test
    fun `record_id_contains`() {
        val cond = fc {
            recordIdContains = FilterConditionRecordIdContains.newBuilder()
                .setFieldId("id").setValue("ABC").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id LIKE ?)", r.sql)
        assertEquals(listOf<Any>("%ABC%"), r.params)
    }

    @Test
    fun `record_id_not_contains`() {
        val cond = fc {
            recordIdNotContains = FilterConditionRecordIdNotContains.newBuilder()
                .setFieldId("id").setValue("XYZ").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Id NOT LIKE ?)", r.sql)
        assertEquals(listOf<Any>("%XYZ%"), r.params)
    }

    // ===== text 系（8ケース）=====

    @Test
    fun `text_equal`() {
        val cond = fc {
            textEqual = FilterConditionTextEqual.newBuilder()
                .setFieldId("name").setValue("Acme").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name = ?)", r.sql)
        assertEquals(listOf<Any>("Acme"), r.params)
    }

    @Test
    fun `text_not_equal`() {
        val cond = fc {
            textNotEqual = FilterConditionTextNotEqual.newBuilder()
                .setFieldId("name").setValue("Acme").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name <> ?)", r.sql)
        assertEquals(listOf<Any>("Acme"), r.params)
    }

    @Test
    fun `text_in`() {
        val cond = fc {
            textIn = FilterConditionTextIn.newBuilder()
                .setFieldId("name").addAllValues(listOf("A", "B")).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name IN (?, ?))", r.sql)
        assertEquals(listOf<Any>("A", "B"), r.params)
    }

    @Test
    fun `text_not_in`() {
        val cond = fc {
            textNotIn = FilterConditionTextNotIn.newBuilder()
                .setFieldId("name").addAllValues(listOf("A", "B")).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name NOT IN (?, ?))", r.sql)
        assertEquals(listOf<Any>("A", "B"), r.params)
    }

    @Test
    fun `text_contains`() {
        val cond = fc {
            textContains = FilterConditionTextContains.newBuilder()
                .setFieldId("name").setValue("foo").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name LIKE ?)", r.sql)
        assertEquals(listOf<Any>("%foo%"), r.params)
    }

    @Test
    fun `text_not_contains`() {
        val cond = fc {
            textNotContains = FilterConditionTextNotContains.newBuilder()
                .setFieldId("name").setValue("foo").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name NOT LIKE ?)", r.sql)
        assertEquals(listOf<Any>("%foo%"), r.params)
    }

    @Test
    fun `text_is EMPTY`() {
        val cond = fc {
            textIs = FilterConditionTextIs.newBuilder()
                .setFieldId("name")
                .setState(TextFieldValueState.TEXT_FIELD_VALUE_STATE_EMPTY)
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("((Name IS NULL OR Name = ''))", r.sql)
        assertTrue(r.params.isEmpty())
    }

    @Test
    fun `text_is_not EMPTY`() {
        val cond = fc {
            textIsNot = FilterConditionTextIsNot.newBuilder()
                .setFieldId("name")
                .setState(TextFieldValueState.TEXT_FIELD_VALUE_STATE_EMPTY)
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("((Name IS NOT NULL AND Name <> ''))", r.sql)
        assertTrue(r.params.isEmpty())
    }

    // ===== datetime 系（8ケース）=====

    @Test
    fun `datetime_equal`() {
        val cond = fc {
            datetimeEqual = FilterConditionDatetimeEqual.newBuilder()
                .setFieldId("created_at").setValue(timestamp(1_700_000_000L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(CreatedDate = ?)", r.sql)
        assertEquals(listOf<Any>(sqlTimestamp(1_700_000_000L)), r.params)
    }

    @Test
    fun `datetime_not_equal`() {
        val cond = fc {
            datetimeNotEqual = FilterConditionDatetimeNotEqual.newBuilder()
                .setFieldId("created_at").setValue(timestamp(1L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(CreatedDate <> ?)", r.sql)
    }

    @Test
    fun `datetime_greater_than`() {
        val cond = fc {
            datetimeGreaterThan = FilterConditionDatetimeGreaterThan.newBuilder()
                .setFieldId("created_at").setValue(timestamp(1L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(CreatedDate > ?)", r.sql)
    }

    @Test
    fun `datetime_greater_than_or_equal`() {
        val cond = fc {
            datetimeGreaterThanOrEqual = FilterConditionDatetimeGreaterThanOrEqual.newBuilder()
                .setFieldId("created_at").setValue(timestamp(1L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(CreatedDate >= ?)", r.sql)
    }

    @Test
    fun `datetime_less_than`() {
        val cond = fc {
            datetimeLessThan = FilterConditionDatetimeLessThan.newBuilder()
                .setFieldId("created_at").setValue(timestamp(1L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(CreatedDate < ?)", r.sql)
    }

    @Test
    fun `datetime_less_than_or_equal`() {
        val cond = fc {
            datetimeLessThanOrEqual = FilterConditionDatetimeLessThanOrEqual.newBuilder()
                .setFieldId("created_at").setValue(timestamp(1L)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(CreatedDate <= ?)", r.sql)
    }

    @Test
    fun `datetime_in_range`() {
        val cond = fc {
            datetimeInRange = FilterConditionDatetimeInRange.newBuilder()
                .setFieldId("created_at")
                .setStart(timestamp(1_700_000_000L))
                .setEnd(timestamp(1_800_000_000L))
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("((CreatedDate >= ? AND CreatedDate < ?))", r.sql)
        assertEquals(2, r.params.size)
    }

    @Test
    fun `datetime_not_in_range`() {
        val cond = fc {
            datetimeNotInRange = FilterConditionDatetimeNotInRange.newBuilder()
                .setFieldId("created_at")
                .setStart(timestamp(1L)).setEnd(timestamp(2L))
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(NOT (CreatedDate >= ? AND CreatedDate < ?))", r.sql)
    }

    // ===== number 系（8ケース）=====

    @Test
    fun `number_equal value`() {
        val cond = fc {
            numberEqual = FilterConditionNumberEqual.newBuilder()
                .setFieldId("revenue").setValue(1000.0).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue = ?)", r.sql)
        assertEquals(listOf<Any>(1000.0), r.params)
    }

    @Test
    fun `number_equal null becomes IS NULL`() {
        val cond = fc {
            numberEqual = FilterConditionNumberEqual.newBuilder()
                .setFieldId("revenue").build() // value 未設定
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue IS NULL)", r.sql)
        assertTrue(r.params.isEmpty())
    }

    @Test
    fun `number_not_equal value`() {
        val cond = fc {
            numberNotEqual = FilterConditionNumberNotEqual.newBuilder()
                .setFieldId("revenue").setValue(1.0).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue <> ?)", r.sql)
    }

    @Test
    fun `number_not_equal null becomes IS NOT NULL`() {
        val cond = fc {
            numberNotEqual = FilterConditionNumberNotEqual.newBuilder()
                .setFieldId("revenue").build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue IS NOT NULL)", r.sql)
    }

    @Test
    fun `number_greater_than`() {
        val cond = fc {
            numberGreaterThan = FilterConditionNumberGreaterThan.newBuilder()
                .setFieldId("revenue").setValue(1.0).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue > ?)", r.sql)
    }

    @Test
    fun `number_greater_than_or_equal`() {
        val cond = fc {
            numberGreaterThanOrEqual = FilterConditionNumberGreaterThanOrEqual.newBuilder()
                .setFieldId("revenue").setValue(1.0).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue >= ?)", r.sql)
    }

    @Test
    fun `number_less_than`() {
        val cond = fc {
            numberLessThan = FilterConditionNumberLessThan.newBuilder()
                .setFieldId("revenue").setValue(1.0).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue < ?)", r.sql)
    }

    @Test
    fun `number_less_than_or_equal`() {
        val cond = fc {
            numberLessThanOrEqual = FilterConditionNumberLessThanOrEqual.newBuilder()
                .setFieldId("revenue").setValue(1.0).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue <= ?)", r.sql)
    }

    @Test
    fun `number_in`() {
        val cond = fc {
            numberIn = FilterConditionNumberIn.newBuilder()
                .setFieldId("revenue").addAllValues(listOf(1.0, 2.0)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue IN (?, ?))", r.sql)
        assertEquals(listOf<Any>(1.0, 2.0), r.params)
    }

    @Test
    fun `number_not_in`() {
        val cond = fc {
            numberNotIn = FilterConditionNumberNotIn.newBuilder()
                .setFieldId("revenue").addAllValues(listOf(1.0, 2.0)).build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(AnnualRevenue NOT IN (?, ?))", r.sql)
    }

    // ===== selection 系（2ケース、NULL 含むIN対応含む）=====

    @Test
    fun `selection_in 非NULL のみ`() {
        val cond = fc {
            selectionIn = FilterConditionSelectionIn.newBuilder()
                .setFieldId("industry")
                .addValues(NullableOption.newBuilder().setOption(Option.newBuilder().setValue("Banking")).build())
                .addValues(NullableOption.newBuilder().setOption(Option.newBuilder().setValue("Retail")).build())
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Industry IN (?, ?))", r.sql)
        assertEquals(listOf<Any>("Banking", "Retail"), r.params)
    }

    @Test
    fun `selection_in NULL のみ`() {
        val cond = fc {
            selectionIn = FilterConditionSelectionIn.newBuilder()
                .setFieldId("industry")
                .addValues(NullableOption.getDefaultInstance())
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Industry IS NULL)", r.sql)
        assertTrue(r.params.isEmpty())
    }

    @Test
    fun `selection_in 非NULL + NULL 混在`() {
        val cond = fc {
            selectionIn = FilterConditionSelectionIn.newBuilder()
                .setFieldId("industry")
                .addValues(NullableOption.newBuilder().setOption(Option.newBuilder().setValue("Banking")).build())
                .addValues(NullableOption.getDefaultInstance())
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("((Industry IN (?) OR Industry IS NULL))", r.sql)
        assertEquals(listOf<Any>("Banking"), r.params)
    }

    @Test
    fun `selection_not_in 非NULL のみ`() {
        val cond = fc {
            selectionNotIn = FilterConditionSelectionNotIn.newBuilder()
                .setFieldId("industry")
                .addValues(NullableOption.newBuilder().setOption(Option.newBuilder().setValue("Banking")).build())
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Industry NOT IN (?))", r.sql)
    }

    @Test
    fun `selection_not_in 非NULL + NULL 混在`() {
        val cond = fc {
            selectionNotIn = FilterConditionSelectionNotIn.newBuilder()
                .setFieldId("industry")
                .addValues(NullableOption.newBuilder().setOption(Option.newBuilder().setValue("Banking")).build())
                .addValues(NullableOption.getDefaultInstance())
                .build()
        }
        val r = translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("((Industry NOT IN (?) AND Industry IS NOT NULL))", r.sql)
    }

    // ===== multiple_selection 系（除外、Unsupported を返す）=====

    @Test
    fun `multiple_selection_in は UnsupportedFilterException`() {
        val cond = fc {
            multipleSelectionIn = FilterConditionMultipleSelectionIn.newBuilder()
                .setFieldId("industry")
                .addValues(NullableOption.newBuilder().setOption(Option.newBuilder().setValue("X")).build())
                .build()
        }
        assertThrows<UnsupportedFilterException> {
            translator.translate(listOf(cond), MatchOperator.MATCH_OPERATOR_ALL)
        }
    }

    // ===== AND / OR 結合 =====

    @Test
    fun `MATCH_OPERATOR_ALL で AND 結合`() {
        val cond1 = fc {
            textContains = FilterConditionTextContains.newBuilder()
                .setFieldId("name").setValue("Acme").build()
        }
        val cond2 = fc {
            numberGreaterThanOrEqual = FilterConditionNumberGreaterThanOrEqual.newBuilder()
                .setFieldId("revenue").setValue(100.0).build()
        }
        val r = translator.translate(listOf(cond1, cond2), MatchOperator.MATCH_OPERATOR_ALL)
        assertEquals("(Name LIKE ? AND AnnualRevenue >= ?)", r.sql)
        assertEquals(listOf<Any>("%Acme%", 100.0), r.params)
    }

    @Test
    fun `MATCH_OPERATOR_ANY で OR 結合`() {
        val cond1 = fc {
            textEqual = FilterConditionTextEqual.newBuilder().setFieldId("name").setValue("A").build()
        }
        val cond2 = fc {
            textEqual = FilterConditionTextEqual.newBuilder().setFieldId("name").setValue("B").build()
        }
        val r = translator.translate(listOf(cond1, cond2), MatchOperator.MATCH_OPERATOR_ANY)
        assertEquals("(Name = ? OR Name = ?)", r.sql)
        assertEquals(listOf<Any>("A", "B"), r.params)
    }

    @Test
    fun `MATCH_OPERATOR_UNSPECIFIED は AND として扱う`() {
        val cond1 = fc {
            textEqual = FilterConditionTextEqual.newBuilder().setFieldId("name").setValue("A").build()
        }
        val r = translator.translate(listOf(cond1), MatchOperator.MATCH_OPERATOR_UNSPECIFIED)
        assertEquals("(Name = ?)", r.sql)
    }
}
