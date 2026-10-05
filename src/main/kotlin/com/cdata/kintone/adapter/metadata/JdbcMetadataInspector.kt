package com.cdata.kintone.adapter.metadata

import java.sql.Connection

/** テーブル情報。 */
data class TableInfo(val schema: String?, val name: String)

/**
 * カラム情報。
 *
 * 自動生成に関する 3 項目は既定値付きにしてある。情報を返さないドライバーでも
 * 「自動生成ではない」として扱えば従来どおり動く (Issue #75)。
 */
data class ColumnInfo(
    val name: String,
    val jdbcType: Int,
    val typeName: String,
    val nullable: Boolean,
    /** 既定値の式（`COLUMN_DEF`）。`(newid())` 等。無ければ null。 */
    val defaultValue: String? = null,
    /** 自動採番列か（`IS_AUTOINCREMENT`）。 */
    val autoIncrement: Boolean = false,
    /** 計算列か（`IS_GENERATEDCOLUMN`）。 */
    val generated: Boolean = false,
)

/** 主キー情報。`init-table` で record-id-type を推定するため、JDBC 型もセットで返す。 */
data class PrimaryKeyInfo(val column: String, val jdbcType: Int)

/**
 * `java.sql.DatabaseMetaData` の薄いラッパ。
 * `init-table` CLI が対話で接続先のテーブル・カラム情報を取得するために利用する。
 */
class JdbcMetadataInspector(private val connection: Connection) {

    /**
     * 接続先のすべてのテーブル・ビュー一覧を返す。
     * 一般的なシステムスキーマ（`INFORMATION_SCHEMA`、`pg_catalog`、`sys` 等）は除外する。
     */
    fun listTables(): List<TableInfo> {
        val tables = mutableListOf<TableInfo>()
        connection.metaData.getTables(null, null, "%", arrayOf("TABLE", "VIEW")).use { rs ->
            while (rs.next()) {
                val schema = rs.getString("TABLE_SCHEM")
                if (schema != null && SYSTEM_SCHEMAS.any { it.equals(schema, ignoreCase = true) }) continue
                tables += TableInfo(schema = schema, name = rs.getString("TABLE_NAME"))
            }
        }
        return tables
    }

    /**
     * 指定テーブルのカラム一覧を返す。
     *
     * [schema] を渡さないと、スキーマを持つデータソースで目的のテーブルを特定できない。
     * JDBC は `schema = null` を「すべてのスキーマ」として扱うため、既定値は従来の
     * 挙動を保つ (Issue #63)。
     */
    fun listColumns(tableName: String, schema: String? = null): List<ColumnInfo> {
        val columns = mutableListOf<ColumnInfo>()
        connection.metaData.getColumns(null, schema, tableName, "%").use { rs ->
            // 存在するラベルを 1 度だけ調べる。`IS_AUTOINCREMENT` / `IS_GENERATEDCOLUMN` は
            // JDBC 4.0 / 4.1 で追加された列で、返さないドライバーがある (Issue #75)。
            val labels = (1..rs.metaData.columnCount)
                .map { rs.metaData.getColumnLabel(it).uppercase() }
                .toSet()
            while (rs.next()) {
                columns += ColumnInfo(
                    name = rs.getString("COLUMN_NAME"),
                    jdbcType = rs.getInt("DATA_TYPE"),
                    typeName = rs.getString("TYPE_NAME"),
                    nullable = rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable,
                    defaultValue = rs.stringIfPresent(labels, "COLUMN_DEF"),
                    autoIncrement = rs.isYes(labels, "IS_AUTOINCREMENT"),
                    generated = rs.isYes(labels, "IS_GENERATEDCOLUMN"),
                )
            }
        }
        return columns
    }

    /** 列があればその文字列を返す。無ければ null。 */
    private fun java.sql.ResultSet.stringIfPresent(labels: Set<String>, label: String): String? =
        if (label in labels) getString(label) else null

    /** 列の値が `YES` か。列自体が無ければ false。 */
    private fun java.sql.ResultSet.isYes(labels: Set<String>, label: String): Boolean =
        "YES".equals(stringIfPresent(labels, label)?.trim(), ignoreCase = true)

    /** 主キー（最初の1カラム）を返す。複合主キーは未対応。なければ null。 */
    fun findPrimaryKey(tableName: String, schema: String? = null): PrimaryKeyInfo? {
        var pkColumn: String? = null
        connection.metaData.getPrimaryKeys(null, schema, tableName).use { rs ->
            if (rs.next()) {
                pkColumn = rs.getString("COLUMN_NAME")
            }
        }
        val column = pkColumn ?: return null
        val columnInfo = listColumns(tableName, schema).firstOrNull { it.name == column }
            ?: error("主キー '$column' のカラム情報が取得できません")
        return PrimaryKeyInfo(column = column, jdbcType = columnInfo.jdbcType)
    }

    /** 指定カラムの DISTINCT な値（最大 limit 件）を返す。SELECTION 型の選択肢自動検出に利用。 */
    fun distinctValues(
        tableName: String,
        columnName: String,
        limit: Int = DEFAULT_DISTINCT_LIMIT,
        schema: String? = null,
    ): List<String> {
        // 識別子をクォートしない。ここはウィザードの選択肢検出だけに使う補助クエリで、
        // クォート形式を変えるとデータソースによって通らなくなる（既存の制約）。
        // スキーマは接頭辞として付ける (Issue #63)。
        val target = if (schema.isNullOrBlank()) tableName else "$schema.$tableName"
        val sql = "SELECT DISTINCT $columnName FROM $target WHERE $columnName IS NOT NULL LIMIT ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, limit)
            stmt.executeQuery().use { rs -> readStrings(rs) }
        }
    }

    /** 1 列目の文字列を全行読む。null は除く。 */
    private fun readStrings(rs: java.sql.ResultSet): List<String> = buildList {
        while (rs.next()) {
            rs.getString(1)?.let { add(it) }
        }
    }

    companion object {
        const val DEFAULT_DISTINCT_LIMIT = 100

        /** 一般的な DBMS のシステムスキーマ。listTables から除外する。 */
        private val SYSTEM_SCHEMAS = setOf(
            "INFORMATION_SCHEMA",
            "pg_catalog",
            "pg_toast",
            "sys",
            "performance_schema",
            "mysql",
        )
    }
}
