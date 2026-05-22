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
            "SELECT [Id], [Name], [AnnualRevenue], [CreatedDate] FROM [Account] WHERE 1=1 LIMIT ? OFFSET ?",
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
            "SELECT [Name], [AnnualRevenue] FROM [Account] WHERE 1=1 LIMIT ? OFFSET ?",
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
            "SELECT [Id], [Name] FROM [Account] WHERE (Name LIKE ?) ORDER BY [AnnualRevenue] DESC LIMIT ? OFFSET ?",
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
            "SELECT [Id], [Name], [AnnualRevenue], [CreatedDate] FROM [Account] WHERE 1=1 ORDER BY [AnnualRevenue] DESC, [Id] ASC LIMIT ? OFFSET ?",
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
        assertEquals("INSERT INTO [Account] ([Name], [AnnualRevenue]) VALUES (?, ?)", q.sql)
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
        assertEquals("UPDATE [Account] SET [Name] = ? WHERE [Id] = ?", q.sql)
        assertEquals(listOf<Any?>("NewName", "001xx"), q.params)
    }

    @Test
    fun `buildUpdate id を含めても無視される`() {
        val q = builder.buildUpdate(
            idValue = 100L,
            columnValues = mapOf("id" to 100L, "name" to "NewName"),
        )
        assertEquals("UPDATE [Account] SET [Name] = ? WHERE [Id] = ?", q.sql)
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
        assertEquals("DELETE FROM [Account] WHERE [Id] IN (?, ?, ?)", q.sql)
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
        assertEquals("SELECT COUNT(*) FROM [Account] WHERE (Name = ?)", q.sql)
        assertEquals(listOf<Any>("Acme"), q.params)
    }

    // --- ISSUE-001: 識別子クォート ---

    @Test
    fun `buildSelect 空白を含むテーブル名でも SQL が壊れない`() {
        val tableWithSpace = TableConfig(
            name = "CRM Data sugimotok_Opportunity",
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableWithSpace)
        val q = builder.buildSelect(
            fields = emptyList(),
            where = WhereClause.ALL_RECORDS,
            sortConditions = emptyList(),
            limit = 10,
            offset = 0,
        )
        assertEquals(
            "SELECT [Id], [Name] FROM [CRM Data sugimotok_Opportunity] WHERE 1=1 LIMIT ? OFFSET ?",
            q.sql,
        )
    }

    @Test
    fun `buildSelect カラム名に空白があってもクォートされる`() {
        val tableWithSpaceCol = TableConfig(
            name = "Orders",
            primaryKey = PrimaryKeyConfig("id", "Order Id"),
            columns = listOf(ColumnConfig("customer", "Customer Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableWithSpaceCol)
        val q = builder.buildSelect(emptyList(), WhereClause.ALL_RECORDS, emptyList(), 10, 0)
        assertEquals(
            "SELECT [Order Id], [Customer Name] FROM [Orders] WHERE 1=1 LIMIT ? OFFSET ?",
            q.sql,
        )
    }

    @Test
    fun `buildSelect 既に角括弧で囲まれた名前は二重クォートしない`() {
        // ユーザが table.yaml に `[Name with bracket]` のように既にクォートを書いていた場合
        // の後方互換を保つ。
        val tableQuoted = TableConfig(
            name = "[CRM Data sugimotok_Opportunity]",
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableQuoted)
        val q = builder.buildSelect(emptyList(), WhereClause.ALL_RECORDS, emptyList(), 10, 0)
        assertEquals(
            "SELECT [Id], [Name] FROM [CRM Data sugimotok_Opportunity] WHERE 1=1 LIMIT ? OFFSET ?",
            q.sql,
        )
    }

    @Test
    fun `buildInsert もテーブル名・カラム名をクォート`() {
        val tableWithSpace = TableConfig(
            name = "CRM Data",
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("first_name", "First Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableWithSpace)
        val q = builder.buildInsert(mapOf("first_name" to "Acme"))
        assertEquals("INSERT INTO [CRM Data] ([First Name]) VALUES (?)", q.sql)
    }

    @Test
    fun `buildUpdate もテーブル名・カラム名をクォート`() {
        val tableWithSpace = TableConfig(
            name = "CRM Data",
            primaryKey = PrimaryKeyConfig("id", "Order Id"),
            columns = listOf(ColumnConfig("first_name", "First Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableWithSpace)
        val q = builder.buildUpdate(idValue = 1L, columnValues = mapOf("first_name" to "Bob"))
        assertEquals("UPDATE [CRM Data] SET [First Name] = ? WHERE [Order Id] = ?", q.sql)
    }

    @Test
    fun `buildDelete もテーブル名・カラム名をクォート`() {
        val tableWithSpace = TableConfig(
            name = "CRM Data",
            primaryKey = PrimaryKeyConfig("id", "Order Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableWithSpace)
        val q = builder.buildDelete(listOf(1L, 2L))
        assertEquals("DELETE FROM [CRM Data] WHERE [Order Id] IN (?, ?)", q.sql)
    }

    @Test
    fun `buildCount もテーブル名をクォート`() {
        val tableWithSpace = TableConfig(
            name = "CRM Data",
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        )
        val builder = QueryBuilder(tableWithSpace)
        val q = builder.buildCount(WhereClause.ALL_RECORDS)
        assertEquals("SELECT COUNT(*) FROM [CRM Data] WHERE 1=1", q.sql)
    }

    @Test
    fun `buildSelect 既存パターン (Salesforce 互換) も クォートあり SQL を返す`() {
        // Salesforce Adapter の既存呼び出しが新クォート形式 SQL でも動く前提。
        // 既存テスト一括書き換えの代表ケースとしてここでも確認しておく。
        val q = builder.buildSelect(
            fields = emptyList(),
            where = WhereClause.ALL_RECORDS,
            sortConditions = emptyList(),
            limit = 100,
            offset = 0,
        )
        assertEquals(
            "SELECT [Id], [Name], [AnnualRevenue], [CreatedDate] FROM [Account] WHERE 1=1 LIMIT ? OFFSET ?",
            q.sql,
        )
    }
}
