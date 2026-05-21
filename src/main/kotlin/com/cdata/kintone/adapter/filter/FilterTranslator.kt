package com.cdata.kintone.adapter.filter

import build.buf.gen.cybozu.data_connector.adapter.v1.FilterCondition
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeGreaterThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeGreaterThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeInRange
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeLessThan
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeLessThanOrEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeNotEqual
import build.buf.gen.cybozu.data_connector.adapter.v1.FilterConditionDatetimeNotInRange
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
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordId
import build.buf.gen.cybozu.data_connector.adapter.v1.TextFieldValueState
import com.cdata.kintone.adapter.config.TableConfig
import com.google.protobuf.Timestamp
import java.sql.Timestamp as SqlTimestamp
import java.time.Instant

/**
 * `FilterCondition` のリストを SQL WHERE 句に変換するクラス。
 * 各 case の意味と SQL 変換規約は `.claude/skills/kintone-external-app-spec/reference/08-jdbc-mapping.md` 参照。
 */
class FilterTranslator(private val table: TableConfig) {

    /**
     * フィルター条件のリストを SQL WHERE 句に変換する。
     *
     * @param conditions フィルター条件のリスト（空の場合は全件取得）
     * @param matchOperator AND（ALL）か OR（ANY）か。UNSPECIFIED の場合は ALL を採用
     * @return WHERE 句と PreparedStatement パラメータ
     * @throws UnsupportedFilterException サポート外の case を渡された場合（multiple_selection_*）
     * @throws IllegalArgumentException 不明な FilterCondition case
     */
    fun translate(
        conditions: List<FilterCondition>,
        matchOperator: MatchOperator,
    ): WhereClause {
        if (conditions.isEmpty()) return WhereClause.ALL_RECORDS

        val translated = conditions.map { translateOne(it) }
        val connector = when (matchOperator) {
            MatchOperator.MATCH_OPERATOR_ANY -> " OR "
            else -> " AND "
        }
        val combinedSql = translated.joinToString(connector, prefix = "(", postfix = ")") { it.sql }
        val combinedParams = translated.flatMap { it.params }
        return WhereClause(combinedSql, combinedParams)
    }

    private fun translateOne(c: FilterCondition): WhereClause = when (c.conditionCase) {
        FilterCondition.ConditionCase.ALL_RECORDS -> WhereClause.ALL_RECORDS

        FilterCondition.ConditionCase.RECORD_ID_EQUAL -> recordIdEqual(c.recordIdEqual)
        FilterCondition.ConditionCase.RECORD_ID_NOT_EQUAL -> recordIdNotEqual(c.recordIdNotEqual)
        FilterCondition.ConditionCase.RECORD_ID_GREATER_THAN -> recordIdComparison(c.recordIdGreaterThan, ">")
        FilterCondition.ConditionCase.RECORD_ID_GREATER_THAN_OR_EQUAL -> recordIdComparison(c.recordIdGreaterThanOrEqual, ">=")
        FilterCondition.ConditionCase.RECORD_ID_LESS_THAN -> recordIdComparison(c.recordIdLessThan, "<")
        FilterCondition.ConditionCase.RECORD_ID_LESS_THAN_OR_EQUAL -> recordIdComparison(c.recordIdLessThanOrEqual, "<=")
        FilterCondition.ConditionCase.RECORD_ID_IN -> recordIdIn(c.recordIdIn, negate = false)
        FilterCondition.ConditionCase.RECORD_ID_NOT_IN -> recordIdIn(c.recordIdNotIn, negate = true)
        FilterCondition.ConditionCase.RECORD_ID_CONTAINS -> recordIdContains(c.recordIdContains, negate = false)
        FilterCondition.ConditionCase.RECORD_ID_NOT_CONTAINS -> recordIdContains(c.recordIdNotContains, negate = true)

        FilterCondition.ConditionCase.TEXT_EQUAL -> textEqual(c.textEqual)
        FilterCondition.ConditionCase.TEXT_NOT_EQUAL -> textNotEqual(c.textNotEqual)
        FilterCondition.ConditionCase.TEXT_IN -> textIn(c.textIn, negate = false)
        FilterCondition.ConditionCase.TEXT_NOT_IN -> textIn(c.textNotIn, negate = true)
        FilterCondition.ConditionCase.TEXT_CONTAINS -> textContains(c.textContains, negate = false)
        FilterCondition.ConditionCase.TEXT_NOT_CONTAINS -> textContains(c.textNotContains, negate = true)
        FilterCondition.ConditionCase.TEXT_IS -> textIs(c.textIs, negate = false)
        FilterCondition.ConditionCase.TEXT_IS_NOT -> textIs(c.textIsNot, negate = true)

        FilterCondition.ConditionCase.DATETIME_EQUAL -> datetimeEqual(c.datetimeEqual)
        FilterCondition.ConditionCase.DATETIME_NOT_EQUAL -> datetimeNotEqual(c.datetimeNotEqual)
        FilterCondition.ConditionCase.DATETIME_GREATER_THAN -> datetimeComparison(c.datetimeGreaterThan, ">")
        FilterCondition.ConditionCase.DATETIME_GREATER_THAN_OR_EQUAL -> datetimeComparison(c.datetimeGreaterThanOrEqual, ">=")
        FilterCondition.ConditionCase.DATETIME_LESS_THAN -> datetimeComparison(c.datetimeLessThan, "<")
        FilterCondition.ConditionCase.DATETIME_LESS_THAN_OR_EQUAL -> datetimeComparison(c.datetimeLessThanOrEqual, "<=")
        FilterCondition.ConditionCase.DATETIME_IN_RANGE -> datetimeInRange(c.datetimeInRange, negate = false)
        FilterCondition.ConditionCase.DATETIME_NOT_IN_RANGE -> datetimeInRange(c.datetimeNotInRange, negate = true)

        FilterCondition.ConditionCase.NUMBER_EQUAL -> numberEqual(c.numberEqual)
        FilterCondition.ConditionCase.NUMBER_NOT_EQUAL -> numberNotEqual(c.numberNotEqual)
        FilterCondition.ConditionCase.NUMBER_GREATER_THAN -> numberComparison(c.numberGreaterThan, ">")
        FilterCondition.ConditionCase.NUMBER_GREATER_THAN_OR_EQUAL -> numberComparison(c.numberGreaterThanOrEqual, ">=")
        FilterCondition.ConditionCase.NUMBER_LESS_THAN -> numberComparison(c.numberLessThan, "<")
        FilterCondition.ConditionCase.NUMBER_LESS_THAN_OR_EQUAL -> numberComparison(c.numberLessThanOrEqual, "<=")
        FilterCondition.ConditionCase.NUMBER_IN -> numberIn(c.numberIn, negate = false)
        FilterCondition.ConditionCase.NUMBER_NOT_IN -> numberIn(c.numberNotIn, negate = true)

        FilterCondition.ConditionCase.SELECTION_IN -> selectionIn(c.selectionIn, negate = false)
        FilterCondition.ConditionCase.SELECTION_NOT_IN -> selectionIn(c.selectionNotIn, negate = true)

        FilterCondition.ConditionCase.MULTIPLE_SELECTION_IN,
        FilterCondition.ConditionCase.MULTIPLE_SELECTION_NOT_IN ->
            throw UnsupportedFilterException(
                "multiple_selection_* は対応していません（kintone UI で複数選択型は非対応のため）",
            )

        FilterCondition.ConditionCase.CONDITION_NOT_SET, null ->
            throw IllegalArgumentException("FilterCondition の oneof condition が未設定")
    }

    // record_id 系
    private fun recordIdEqual(f: FilterConditionRecordIdEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val value = recordIdValue(f.value, f.recordIdValue)
        return WhereClause("$col = ?", listOf(value))
    }

    private fun recordIdNotEqual(f: FilterConditionRecordIdNotEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val value = recordIdValue(f.value, f.recordIdValue)
        return WhereClause("$col <> ?", listOf(value))
    }

    private fun recordIdComparison(
        f: FilterConditionRecordIdGreaterThan,
        op: String,
    ): WhereClause = recordIdComparisonCommon(f.fieldId, f.value, op)

    private fun recordIdComparison(
        f: FilterConditionRecordIdGreaterThanOrEqual,
        op: String,
    ): WhereClause = recordIdComparisonCommon(f.fieldId, f.value, op)

    private fun recordIdComparison(
        f: FilterConditionRecordIdLessThan,
        op: String,
    ): WhereClause = recordIdComparisonCommon(f.fieldId, f.value, op)

    private fun recordIdComparison(
        f: FilterConditionRecordIdLessThanOrEqual,
        op: String,
    ): WhereClause = recordIdComparisonCommon(f.fieldId, f.value, op)

    private fun recordIdComparisonCommon(fieldId: String, value: Long, op: String): WhereClause {
        val col = table.toJdbcColumn(fieldId)
        return WhereClause("$col $op ?", listOf(value))
    }

    private fun recordIdIn(f: FilterConditionRecordIdIn, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val values = collectRecordIdValues(f.valuesList, f.recordIdValuesList)
        return inClause(col, values, negate)
    }

    private fun recordIdIn(f: FilterConditionRecordIdNotIn, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val values = collectRecordIdValues(f.valuesList, f.recordIdValuesList)
        return inClause(col, values, negate)
    }

    private fun recordIdContains(f: FilterConditionRecordIdContains, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val op = if (negate) "NOT LIKE" else "LIKE"
        return WhereClause("$col $op ?", listOf("%${f.value}%"))
    }

    private fun recordIdContains(f: FilterConditionRecordIdNotContains, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val op = if (negate) "NOT LIKE" else "LIKE"
        return WhereClause("$col $op ?", listOf("%${f.value}%"))
    }

    // text 系
    private fun textEqual(f: FilterConditionTextEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return WhereClause("$col = ?", listOf(f.value))
    }

    private fun textNotEqual(f: FilterConditionTextNotEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return WhereClause("$col <> ?", listOf(f.value))
    }

    private fun textIn(f: FilterConditionTextIn, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return inClause(col, f.valuesList, negate)
    }

    private fun textIn(f: FilterConditionTextNotIn, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return inClause(col, f.valuesList, negate)
    }

    private fun textContains(f: FilterConditionTextContains, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val op = if (negate) "NOT LIKE" else "LIKE"
        return WhereClause("$col $op ?", listOf("%${f.value}%"))
    }

    private fun textContains(f: FilterConditionTextNotContains, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        val op = if (negate) "NOT LIKE" else "LIKE"
        return WhereClause("$col $op ?", listOf("%${f.value}%"))
    }

    private fun textIs(f: FilterConditionTextIs, negate: Boolean): WhereClause = textStateClause(f.fieldId, f.state, negate)
    private fun textIs(f: FilterConditionTextIsNot, negate: Boolean): WhereClause = textStateClause(f.fieldId, f.state, negate)

    private fun textStateClause(fieldId: String, state: TextFieldValueState, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(fieldId)
        return when (state) {
            TextFieldValueState.TEXT_FIELD_VALUE_STATE_EMPTY -> {
                if (negate) {
                    WhereClause("($col IS NOT NULL AND $col <> '')", emptyList())
                } else {
                    WhereClause("($col IS NULL OR $col = '')", emptyList())
                }
            }
            else -> throw IllegalArgumentException("不明な TextFieldValueState: $state")
        }
    }

    // datetime 系
    private fun datetimeEqual(f: FilterConditionDatetimeEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return WhereClause("$col = ?", listOf(timestampToSql(f.value)))
    }

    private fun datetimeNotEqual(f: FilterConditionDatetimeNotEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return WhereClause("$col <> ?", listOf(timestampToSql(f.value)))
    }

    private fun datetimeComparison(
        f: FilterConditionDatetimeGreaterThan,
        op: String,
    ): WhereClause = datetimeComparisonCommon(f.fieldId, f.value, op)

    private fun datetimeComparison(
        f: FilterConditionDatetimeGreaterThanOrEqual,
        op: String,
    ): WhereClause = datetimeComparisonCommon(f.fieldId, f.value, op)

    private fun datetimeComparison(
        f: FilterConditionDatetimeLessThan,
        op: String,
    ): WhereClause = datetimeComparisonCommon(f.fieldId, f.value, op)

    private fun datetimeComparison(
        f: FilterConditionDatetimeLessThanOrEqual,
        op: String,
    ): WhereClause = datetimeComparisonCommon(f.fieldId, f.value, op)

    private fun datetimeComparisonCommon(fieldId: String, ts: Timestamp, op: String): WhereClause {
        val col = table.toJdbcColumn(fieldId)
        return WhereClause("$col $op ?", listOf(timestampToSql(ts)))
    }

    private fun datetimeInRange(f: FilterConditionDatetimeInRange, negate: Boolean): WhereClause =
        datetimeRangeCommon(f.fieldId, f.start, f.end, negate)

    private fun datetimeInRange(f: FilterConditionDatetimeNotInRange, negate: Boolean): WhereClause =
        datetimeRangeCommon(f.fieldId, f.start, f.end, negate)

    private fun datetimeRangeCommon(fieldId: String, start: Timestamp, end: Timestamp, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(fieldId)
        val inRange = "($col >= ? AND $col < ?)"
        val sql = if (negate) "NOT $inRange" else inRange
        return WhereClause(sql, listOf(timestampToSql(start), timestampToSql(end)))
    }

    // number 系
    private fun numberEqual(f: FilterConditionNumberEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return if (!f.hasValue()) {
            WhereClause("$col IS NULL", emptyList())
        } else {
            WhereClause("$col = ?", listOf(f.value))
        }
    }

    private fun numberNotEqual(f: FilterConditionNumberNotEqual): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return if (!f.hasValue()) {
            WhereClause("$col IS NOT NULL", emptyList())
        } else {
            WhereClause("$col <> ?", listOf(f.value))
        }
    }

    private fun numberComparison(
        f: FilterConditionNumberGreaterThan,
        op: String,
    ): WhereClause = numberComparisonCommon(f.fieldId, f.hasValue(), f.value, op)

    private fun numberComparison(
        f: FilterConditionNumberGreaterThanOrEqual,
        op: String,
    ): WhereClause = numberComparisonCommon(f.fieldId, f.hasValue(), f.value, op)

    private fun numberComparison(
        f: FilterConditionNumberLessThan,
        op: String,
    ): WhereClause = numberComparisonCommon(f.fieldId, f.hasValue(), f.value, op)

    private fun numberComparison(
        f: FilterConditionNumberLessThanOrEqual,
        op: String,
    ): WhereClause = numberComparisonCommon(f.fieldId, f.hasValue(), f.value, op)

    private fun numberComparisonCommon(fieldId: String, hasValue: Boolean, value: Double, op: String): WhereClause {
        val col = table.toJdbcColumn(fieldId)
        if (!hasValue) {
            throw IllegalArgumentException("number 比較条件で値が指定されていません: fieldId=$fieldId, op=$op")
        }
        return WhereClause("$col $op ?", listOf(value))
    }

    private fun numberIn(f: FilterConditionNumberIn, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return inClause(col, f.valuesList, negate)
    }

    private fun numberIn(f: FilterConditionNumberNotIn, negate: Boolean): WhereClause {
        val col = table.toJdbcColumn(f.fieldId)
        return inClause(col, f.valuesList, negate)
    }

    // selection 系
    private fun selectionIn(f: FilterConditionSelectionIn, negate: Boolean): WhereClause =
        selectionClauseCommon(f.fieldId, f.valuesList, negate)

    private fun selectionIn(f: FilterConditionSelectionNotIn, negate: Boolean): WhereClause =
        selectionClauseCommon(f.fieldId, f.valuesList, negate)

    private fun selectionClauseCommon(
        fieldId: String,
        nullableOptions: List<NullableOption>,
        negate: Boolean,
    ): WhereClause {
        val col = table.toJdbcColumn(fieldId)
        val nonNullValues = nullableOptions.filter { it.hasOption() }.map { it.option.value }
        val hasNullValue = nullableOptions.any { !it.hasOption() }

        // SQL の IN 句は NULL を含めないため、明示的に分岐する
        return when {
            nonNullValues.isEmpty() && hasNullValue -> {
                if (negate) WhereClause("$col IS NOT NULL", emptyList()) else WhereClause("$col IS NULL", emptyList())
            }
            nonNullValues.isNotEmpty() && hasNullValue -> {
                val placeholders = nonNullValues.joinToString(", ") { "?" }
                val sql = if (negate) {
                    "($col NOT IN ($placeholders) AND $col IS NOT NULL)"
                } else {
                    "($col IN ($placeholders) OR $col IS NULL)"
                }
                WhereClause(sql, nonNullValues)
            }
            else -> inClause(col, nonNullValues, negate)
        }
    }

    // ヘルパ
    private fun recordIdValue(numberValue: Long, recordIdValue: RecordId): Any {
        return when (recordIdValue.valueCase) {
            RecordId.ValueCase.VALUE_TEXT -> recordIdValue.valueText
            RecordId.ValueCase.VALUE_NUMBER -> recordIdValue.valueNumber
            else -> numberValue
        }
    }

    private fun collectRecordIdValues(numberValues: List<Long>, recordIdValues: List<RecordId>): List<Any> {
        if (recordIdValues.isNotEmpty()) {
            return recordIdValues.map { rid ->
                when (rid.valueCase) {
                    RecordId.ValueCase.VALUE_TEXT -> rid.valueText
                    else -> rid.valueNumber
                }
            }
        }
        return numberValues
    }

    private fun <T : Any> inClause(column: String, values: List<T>, negate: Boolean): WhereClause {
        if (values.isEmpty()) {
            // 空 IN は SQL 仕様上エラーになるので、自明な真偽値で代替
            return if (negate) WhereClause.ALL_RECORDS else WhereClause("1=0", emptyList())
        }
        val placeholders = values.joinToString(", ") { "?" }
        val op = if (negate) "NOT IN" else "IN"
        return WhereClause("$column $op ($placeholders)", values.toList())
    }

    private fun timestampToSql(ts: Timestamp): SqlTimestamp {
        return SqlTimestamp.from(Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()))
    }
}

/** サポートされていないフィルター条件が渡されたときに投げられる例外。 */
class UnsupportedFilterException(message: String) : RuntimeException(message)
