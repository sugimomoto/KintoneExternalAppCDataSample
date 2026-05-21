package com.cdata.kintone.adapter.metadata

import java.sql.Types

/**
 * kintone のフィールド型を表す。設定ファイル `table.yaml` の `type` フィールドに対応。
 */
enum class ColumnType { TEXT, NUMBER, DATETIME, SELECTION }

/**
 * RecordId（主キー）の型を表す。`capability.yaml` の `record-id-type` に対応。
 */
enum class RecordIdType { NUMBER, TEXT }

/**
 * JDBC 型から kintone のフィールド型を推奨する純粋関数群。
 * `init-table` コマンドの対話式テーブル設定生成で使用される。
 */
object FieldTypeSuggester {

    /**
     * JDBC 型コード（`java.sql.Types`）に対応する kintone フィールド型を返す。
     * 未対応型はフォールバックとして TEXT を返す。
     */
    fun suggest(jdbcType: Int): ColumnType = when (jdbcType) {
        Types.VARCHAR,
        Types.CHAR,
        Types.LONGVARCHAR,
        Types.NVARCHAR,
        Types.NCHAR,
        Types.LONGNVARCHAR -> ColumnType.TEXT

        Types.BIGINT,
        Types.INTEGER,
        Types.SMALLINT,
        Types.TINYINT,
        Types.DECIMAL,
        Types.NUMERIC,
        Types.FLOAT,
        Types.DOUBLE,
        Types.REAL -> ColumnType.NUMBER

        Types.TIMESTAMP,
        Types.DATE,
        Types.TIME,
        Types.TIMESTAMP_WITH_TIMEZONE,
        Types.TIME_WITH_TIMEZONE -> ColumnType.DATETIME

        else -> ColumnType.TEXT
    }

    /**
     * 主キーカラムの JDBC 型から、RecordIdType（NUMBER/TEXT）を推奨する。
     * 数値型でも文字列型でもない型の場合はエラー。
     */
    fun suggestRecordIdType(jdbcType: Int): RecordIdType = when (jdbcType) {
        Types.VARCHAR,
        Types.CHAR,
        Types.LONGVARCHAR,
        Types.NVARCHAR,
        Types.NCHAR,
        Types.LONGNVARCHAR -> RecordIdType.TEXT

        Types.BIGINT,
        Types.INTEGER,
        Types.SMALLINT,
        Types.TINYINT -> RecordIdType.NUMBER

        else -> error("主キーとして対応できない JDBC 型: $jdbcType")
    }
}
