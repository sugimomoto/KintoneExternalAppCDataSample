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
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.google.protobuf.Timestamp
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp as SqlTimestamp
import java.time.Instant

/**
 * `ResultSet` の1行を protobuf `Record` に、protobuf `Record` を SQL バインド用の
 * `kintone field_id → 値` Map に相互変換する。
 *
 * 変換規約は `.claude/skills/kintone-external-app-spec/reference/08-jdbc-mapping.md` 参照。
 */
class RowMapper(
    private val table: TableConfig,
    private val recordIdType: RecordIdType,
) {

    /**
     * `ResultSet` の現在行（カーソル位置）を `Record` に変換する。
     *
     * kintone はリクエストで指定された全フィールドが Record に含まれていることを期待するため、
     * NULL 値でも対応する型のデフォルト値（空文字列・未設定 Timestamp 等）でフィールドを生成する。
     * フィールド自体を省略すると kintone 側で `不正なリクエスト (CB_IL02)` エラーになる。
     *
     * @param selectedFieldIds Select 要求された kintone field_id（空の場合は全カラム取得を想定）
     */
    fun resultSetToRecord(rs: ResultSet, selectedFieldIds: List<String> = emptyList()): Record {
        val builder = Record.newBuilder()
        val includeAll = selectedFieldIds.isEmpty()
        val includeSet = selectedFieldIds.toSet()

        // 主キー
        if (includeAll || table.primaryKey.kintoneFieldId in includeSet) {
            val pkField = readRecordIdField(rs, table.primaryKey.kintoneFieldId, table.primaryKey.jdbcColumn)
            if (pkField != null) {
                builder.putFields(table.primaryKey.kintoneFieldId, pkField)
            }
        }
        // 通常カラム（NULL でも必ずフィールドを生成）
        for (col in table.columns) {
            if (!includeAll && col.kintoneFieldId !in includeSet) continue
            builder.putFields(col.kintoneFieldId, readField(rs, col))
        }
        return builder.build()
    }

    /**
     * Record を `kintone field_id → 値` のマップに変換する（INSERT/UPDATE 用）。
     * Record にない field_id はマップに含まれない。
     */
    fun recordToColumnValues(record: Record): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        for ((fieldId, field) in record.fieldsMap) {
            map[fieldId] = fieldToValue(field)
        }
        return map
    }

    /**
     * Insert 用に Record を `kintone field_id → 値` のマップへ変換する。
     *
     * 主キーと、**値が入っていないフィールド**を含めない。自動生成列をマッピングしている
     * 連携で、空文字が `uniqueidentifier` 列に渡ったり、NULL が `NOT NULL` 列に渡ったりして
     * 登録が失敗するのを防ぐ。列を送らなければ DB 側の既定値が入る (Issue #76)。
     *
     * 省けるのは Insert だけ。Insert には「既存の値を空にする」という操作が無いため、
     * 値が無いフィールドを省いても失うものがない。Update で同じことをすると
     * 項目を空にする操作ができなくなるため、[recordToColumnValues] は手を入れない。
     *
     * 主キーの除外をここで行うのは、省いた結果 0 列になる判定を**主キーを除いた後**で
     * しなければ正しくできないため。
     */
    fun recordToInsertValues(record: Record): Map<String, Any?> {
        val withoutId = record.fieldsMap.filterKeys { it != table.primaryKey.kintoneFieldId }
        val kept = withoutId.filterValues { !isAbsent(it) }
        // すべて空だった場合は省かない。INSERT 対象が 0 列になり組み立てに失敗する。
        // `INSERT INTO t DEFAULT VALUES` は移植性が無いため、従来どおりの値を送る。
        return kept.ifEmpty { withoutId }.mapValues { (_, field) -> fieldToValue(field) }
    }

    /**
     * 値が入っていないフィールドか。
     *
     * テキストは `TextField.value` に proto3 の presence が無いため、空文字を
     * 「未入力」とみなす。そのため**`NOT NULL` で既定値も無いテキスト列は、空のまま
     * 登録すると従来の空文字挿入ではなく NULL 制約違反になる**。空文字を意図的に
     * 入れたい場合と区別できないのはプロトコルの制約で、どちらかを選ぶしかない。
     *
     * 数値・日時・選択は presence を持つので「未入力」を正しく判定できる。
     */
    private fun isAbsent(field: Field): Boolean = when (field.fieldCase) {
        Field.FieldCase.TEXT_FIELD -> field.textField.value.isEmpty()
        Field.FieldCase.NUMBER_FIELD -> !field.numberField.hasValue()
        Field.FieldCase.DATETIME_FIELD -> !field.datetimeField.hasValue()
        Field.FieldCase.SELECTION_FIELD -> !field.selectionField.hasValue()
        else -> false
    }

    /** Record から主キー値だけを抽出する。なければ null。 */
    fun extractRecordId(record: Record): Any? {
        val field = record.fieldsMap[table.primaryKey.kintoneFieldId] ?: return null
        if (!field.hasRecordIdField()) return null
        return when (recordIdType) {
            RecordIdType.NUMBER -> field.recordIdField.value
            RecordIdType.TEXT -> field.recordIdField.recordIdValue.valueText
        }
    }

    private fun readRecordIdField(rs: ResultSet, kintoneFieldId: String, jdbcColumn: String): Field? {
        val raw = rs.getObject(jdbcColumn) ?: return null
        if (rs.wasNull()) return null
        val builder = RecordIdField.newBuilder().setFieldId(kintoneFieldId)
        when (recordIdType) {
            RecordIdType.NUMBER -> {
                builder.value = toLong(raw)
            }
            RecordIdType.TEXT -> {
                builder.recordIdValue = RecordId.newBuilder().setValueText(raw.toString()).build()
            }
        }
        return Field.newBuilder().setRecordIdField(builder).build()
    }

    /**
     * 1 セルを Field に変換する。NULL の場合は型に応じたデフォルト値で Field を生成する：
     * - TEXT       → value=""
     * - NUMBER     → optional value 未設定（kintone 側で null として扱われる）
     * - DATETIME   → value 未設定（Timestamp デフォルト）
     * - SELECTION  → Option 未設定
     */
    private fun readField(rs: ResultSet, col: ColumnConfig): Field {
        val raw = rs.getObject(col.jdbcColumn)
        val isNull = raw == null || rs.wasNull()
        return when (col.type) {
            ColumnType.TEXT -> Field.newBuilder()
                .setTextField(
                    TextField.newBuilder()
                        .setFieldId(col.kintoneFieldId)
                        .setValue(if (isNull) "" else raw.toString()),
                )
                .build()
            ColumnType.NUMBER -> Field.newBuilder()
                .setNumberField(
                    NumberField.newBuilder()
                        .setFieldId(col.kintoneFieldId)
                        .apply { if (!isNull) value = toDouble(raw!!) },
                )
                .build()
            ColumnType.DATETIME -> Field.newBuilder()
                .setDatetimeField(
                    DatetimeField.newBuilder()
                        .setFieldId(col.kintoneFieldId)
                        .apply { if (!isNull) value = toTimestamp(raw!!) },
                )
                .build()
            ColumnType.SELECTION -> Field.newBuilder()
                .setSelectionField(
                    SelectionField.newBuilder()
                        .setFieldId(col.kintoneFieldId)
                        .apply { if (!isNull) value = Option.newBuilder().setValue(raw.toString()).build() },
                )
                .build()
        }
    }

    private fun fieldToValue(field: Field): Any? = when (field.fieldCase) {
        Field.FieldCase.RECORD_ID_FIELD -> {
            val rif = field.recordIdField
            when (recordIdType) {
                RecordIdType.NUMBER -> rif.value
                RecordIdType.TEXT -> rif.recordIdValue.valueText
            }
        }
        Field.FieldCase.TEXT_FIELD -> field.textField.value
        Field.FieldCase.NUMBER_FIELD -> if (field.numberField.hasValue()) field.numberField.value else null
        Field.FieldCase.DATETIME_FIELD -> {
            if (field.datetimeField.hasValue()) {
                // java.sql.Timestamp は渡さない。CData の SQL Server ドライバーが
                // SQL Server が解釈できない文字列に変換して失敗する (Issue #71)。
                SqlDateTime.format(field.datetimeField.value)
            } else {
                null
            }
        }
        Field.FieldCase.SELECTION_FIELD -> field.selectionField.value.value
        Field.FieldCase.MULTIPLE_SELECTION_FIELD -> error("MultipleSelectionField は未対応")
        Field.FieldCase.FIELD_NOT_SET, null -> null
    }

    private fun toLong(value: Any): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLong()
        else -> error("Long に変換できない値: $value (${value.javaClass.name})")
    }

    private fun toDouble(value: Any): Double = when (value) {
        is BigDecimal -> value.toDouble()
        is Number -> value.toDouble()
        is String -> value.toDouble()
        else -> error("Double に変換できない値: $value (${value.javaClass.name})")
    }

    private fun toTimestamp(value: Any): Timestamp {
        val instant: Instant = when (value) {
            is SqlTimestamp -> value.toInstant()
            is java.sql.Date -> Instant.ofEpochMilli(value.time)
            is java.sql.Time -> Instant.ofEpochMilli(value.time)
            is java.util.Date -> value.toInstant()
            is Instant -> value
            else -> error("Timestamp に変換できない値: $value (${value.javaClass.name})")
        }
        return Timestamp.newBuilder()
            .setSeconds(instant.epochSecond)
            .setNanos(instant.nano)
            .build()
    }
}
