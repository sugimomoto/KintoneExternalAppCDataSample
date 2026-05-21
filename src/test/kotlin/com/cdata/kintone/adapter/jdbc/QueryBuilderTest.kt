package com.cdata.kintone.adapter.jdbc

import build.buf.gen.cybozu.data_connector.adapter.v1.SortCondition
import build.buf.gen.cybozu.data_connector.adapter.v1.SortDirection
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.filter.WhereClause
import com.cdata.kintone.adapter.metadata.ColumnType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QueryBuilderTest {

    private val tableConfig = TableConfig(
        name = "Account",
        primaryKey = PrimaryKeyConfig("id", "Id"),
        columns = listOf(
            ColumnConfig("name", "Name", ColumnType.TEXT),
            ColumnConfig("revenue", "AnnualRevenue", ColumnType.NUMBER),
            ColumnConfig("created_at", "CreatedDate", ColumnType.DATETIME),
        ),
    )

    private val builder = QueryBuilder(tableConfig)

    @Test
    fun `buildSelect 全カラム取得`() {
        val q = builder.buildSelect(
            fields = emptyList(),
            where = WhereClause.ALL_RECORDS,
            sortConditions = emptyList(),
            limit = 100,
            offset = 0,
        )
        assertEquals(
            "SELECT Id, Name, AnnualRevenue, CreatedDate FROM Account WHERE 1=1 LIMIT ? OFFSET ?",
            q.sql,
        )
        assertEquals(listOf<Any>(100L, 0L), q.params)
    }

    @Test
    fun `buildSelect 一部カラム指定`() {
        val q = builder.buildSelect(
            fields = listOf("name", "revenue"),
            where = WhereClause.ALL_RECORDS,
            sortConditions = emptyList(),
            limit = 50,
            offset = 100,
        )
        assertEquals(
            "SELECT Name, AnnualRevenue FROM Account WHERE 1=1 LIMIT ? OFFSET ?",
            q.sql,
        )
    }

    @Test
    fun `buildSelect WHERE と ORDER BY 付き`() {
        val where = WhereClause("(Name LIKE ?)", listOf("%Acme%"))
        val sort = SortCondition.newBuilder()
            .setFieldId("revenue")
            .setSortDirection(SortDirection.SORT_DIRECTION_DESC)
            .build()
        val q = builder.buildSelect(
            fields = listOf("id", "name"),
            where = where,
            sortConditions = listOf(sort),
            limit = 10,
            offset = 0,
        )
        assertEquals(
            "SELECT Id, Name FROM Account WHERE (Name LIKE ?) ORDER BY AnnualRevenue DESC LIMIT ? OFFSET ?",
            q.sql,
        )
        assertEquals(listOf<Any>("%Acme%", 10L, 0L), q.params)
    }

    @Test
    fun `buildSelect 複数 ORDER BY`() {
        val sorts = listOf(
            SortCondition.newBuilder().setFieldId("revenue").setSortDirection(SortDirection.SORT_DIRECTION_DESC).build(),
            SortCondition.newBuilder().setFieldId("id").setSortDirection(SortDirection.SORT_DIRECTION_ASC).build(),
        )
        val q = builder.buildSelect(emptyList(), WhereClause.ALL_RECORDS, sorts, 10, 0)
        assertEquals(
            "SELECT Id, Name, AnnualRevenue, CreatedDate FROM Account WHERE 1=1 ORDER BY AnnualRevenue DESC, Id ASC LIMIT ? OFFSET ?",
            q.sql,
        )
    }

    @Test
    fun `buildInsert 通常パターン`() {
        val q = builder.buildInsert(
            mapOf(
                "name" to "Acme",
                "revenue" to 1000.0,
            ),
        )
        // Map の順序は LinkedHashMap で保たれる前提
        assertEquals("INSERT INTO Account (Name, AnnualRevenue) VALUES (?, ?)", q.sql)
        assertEquals(listOf<Any?>("Acme", 1000.0), q.params)
    }

    @Test
    fun `buildInsert 空マップでエラー`() {
        assertThrows<IllegalArgumentException> {
            builder.buildInsert(emptyMap())
        }
    }

    @Test
    fun `buildUpdate 主キーで WHERE`() {
        val q = builder.buildUpdate(
            idValue = "001xx",
            columnValues = mapOf("name" to "NewName"),
        )
        assertEquals("UPDATE Account SET Name = ? WHERE Id = ?", q.sql)
        assertEquals(listOf<Any?>("NewName", "001xx"), q.params)
    }

    @Test
    fun `buildUpdate id を含めても無視される`() {
        val q = builder.buildUpdate(
            idValue = 100L,
            columnValues = mapOf("id" to 100L, "name" to "NewName"),
        )
        assertEquals("UPDATE Account SET Name = ? WHERE Id = ?", q.sql)
    }

    @Test
    fun `buildUpdate 更新対象なしでエラー`() {
        assertThrows<IllegalArgumentException> {
            builder.buildUpdate(idValue = 1L, columnValues = mapOf("id" to 1L))
        }
    }

    @Test
    fun `buildDelete 複数 ID`() {
        val q = builder.buildDelete(listOf(1L, 2L, 3L))
        assertEquals("DELETE FROM Account WHERE Id IN (?, ?, ?)", q.sql)
        assertEquals(listOf<Any>(1L, 2L, 3L), q.params)
    }

    @Test
    fun `buildDelete 空リストでエラー`() {
        assertThrows<IllegalArgumentException> {
            builder.buildDelete(emptyList())
        }
    }

    @Test
    fun `buildCount`() {
        val where = WhereClause("(Name = ?)", listOf("Acme"))
        val q = builder.buildCount(where)
        assertEquals("SELECT COUNT(*) FROM Account WHERE (Name = ?)", q.sql)
        assertEquals(listOf<Any>("Acme"), q.params)
    }
}
