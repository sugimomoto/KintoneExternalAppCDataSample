package com.cdata.kintone.adapter.jdbc

import build.buf.gen.cybozu.data_connector.adapter.v1.SortCondition
import build.buf.gen.cybozu.data_connector.adapter.v1.SortDirection
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.filter.WhereClause
import com.cdata.kintone.adapter.jdbc.SqlIdentifier.quote

/**
 * 動的 SQL 文字列と PreparedStatement バインドパラメータをセットで保持する。
 */
data class PreparedQuery(val sql: String, val params: List<Any?>)

/**
 * `TableConfig` に基づいて、SELECT/INSERT/UPDATE/DELETE/COUNT の SQL を組み立てる。
 * SQL インジェクション対策のため、値はすべて `?` プレースホルダで PreparedStatement にバインドする。
 */
class QueryBuilder(private val table: TableConfig) {

    /**
     * SELECT 文を組み立てる。
     *
     * @param fields kintone field_id のリスト（空の場合は全カラム）
     * @param where WHERE 句（[FilterTranslator] で生成）
     * @param sortConditions ORDER BY 条件のリスト
     * @param limit LIMIT 値
     * @param offset OFFSET 値
     */
    fun buildSelect(
        fields: List<String>,
        where: WhereClause,
        sortConditions: List<SortCondition>,
        limit: Long,
        offset: Long,
    ): PreparedQuery {
        val columns = if (fields.isEmpty()) {
            table.allJdbcColumns()
        } else {
            fields.map { table.toJdbcColumn(it) }
        }
        val orderBy = if (sortConditions.isEmpty()) {
            ""
        } else {
            " ORDER BY " + sortConditions.joinToString(", ") { sc ->
                val col = quote(table.toJdbcColumn(sc.fieldId))
                val direction = if (sc.sortDirection == SortDirection.SORT_DIRECTION_DESC) "DESC" else "ASC"
                "$col $direction"
            }
        }
        val colsList = columns.joinToString(", ") { quote(it) }
        val sql = "SELECT $colsList FROM ${table.qualifiedName()} WHERE ${where.sql}$orderBy LIMIT ? OFFSET ?"
        return PreparedQuery(sql, where.params + listOf(limit, offset))
    }

    /**
     * INSERT 文を組み立てる。1 レコード分。生成 ID を返したい場合は呼出側で
     * `Statement.RETURN_GENERATED_KEYS` を指定する。
     *
     * @param columnValues kintone field_id → 値のマップ（主キーは含まない想定だが、含まれていてもよい）
     */
    fun buildInsert(columnValues: Map<String, Any?>): PreparedQuery {
        require(columnValues.isNotEmpty()) { "INSERT 対象のカラムがありません" }
        val jdbcColumnValues = columnValues.mapKeys { (k, _) -> table.toJdbcColumn(k) }
        val cols = jdbcColumnValues.keys.toList()
        val placeholders = cols.joinToString(", ") { "?" }
        val colsList = cols.joinToString(", ") { quote(it) }
        val sql = "INSERT INTO ${table.qualifiedName()} ($colsList) VALUES ($placeholders)"
        return PreparedQuery(sql, cols.map { jdbcColumnValues[it] })
    }

    /**
     * UPDATE 文を組み立てる。主キーで WHERE する。
     *
     * @param idValue 主キー値
     * @param columnValues 更新対象の kintone field_id → 値のマップ（主キーを含めるとフィルタされる）
     */
    fun buildUpdate(idValue: Any, columnValues: Map<String, Any?>): PreparedQuery {
        val nonIdColumns = columnValues
            .filterKeys { it != table.primaryKey.kintoneFieldId }
            .mapKeys { (k, _) -> table.toJdbcColumn(k) }
        require(nonIdColumns.isNotEmpty()) { "UPDATE 対象のカラムがありません" }
        val setClause = nonIdColumns.keys.joinToString(", ") { "${quote(it)} = ?" }
        val sql = "UPDATE ${table.qualifiedName()} SET $setClause WHERE ${quote(table.primaryKey.jdbcColumn)} = ?"
        return PreparedQuery(sql, nonIdColumns.values.toList() + listOf(idValue))
    }

    /**
     * DELETE 文を組み立てる。IN 句で複数 ID を一括削除。
     */
    fun buildDelete(idValues: List<Any>): PreparedQuery {
        require(idValues.isNotEmpty()) { "削除対象の ID がありません" }
        val placeholders = idValues.joinToString(", ") { "?" }
        val sql = "DELETE FROM ${table.qualifiedName()} WHERE ${quote(table.primaryKey.jdbcColumn)} IN ($placeholders)"
        return PreparedQuery(sql, idValues)
    }

    /**
     * COUNT(*) 文を組み立てる。
     */
    fun buildCount(where: WhereClause): PreparedQuery {
        val sql = "SELECT COUNT(*) FROM ${table.qualifiedName()} WHERE ${where.sql}"
        return PreparedQuery(sql, where.params)
    }
}
