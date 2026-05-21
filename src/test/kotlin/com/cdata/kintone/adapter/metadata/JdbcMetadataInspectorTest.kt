package com.cdata.kintone.adapter.metadata

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.util.UUID

class JdbcMetadataInspectorTest {

    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:h2:mem:metaTest_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE Account (
                    Id VARCHAR(18) PRIMARY KEY,
                    Name VARCHAR(200) NOT NULL,
                    AnnualRevenue DECIMAL(15, 2),
                    Industry VARCHAR(50),
                    CreatedDate TIMESTAMP
                )
                """.trimIndent(),
            )
            stmt.execute(
                """
                CREATE TABLE Contact (
                    Id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    AccountId VARCHAR(18),
                    Name VARCHAR(200)
                )
                """.trimIndent(),
            )
            stmt.execute("INSERT INTO Account VALUES ('001', 'Acme', 100, 'Banking', NOW())")
            stmt.execute("INSERT INTO Account VALUES ('002', 'Beta', 200, 'Banking', NOW())")
            stmt.execute("INSERT INTO Account VALUES ('003', 'Gamma', 300, 'Retail', NOW())")
            stmt.execute("INSERT INTO Account VALUES ('004', 'Delta', 400, NULL, NOW())")
        }
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `listTables で全テーブルを取得`() {
        val inspector = JdbcMetadataInspector(connection)
        val tables = inspector.listTables()
        val names = tables.map { it.name }.sorted()
        // H2 はテーブル名を大文字化することがある
        assertEquals(listOf("ACCOUNT", "CONTACT"), names.map { it.uppercase() })
    }

    @Test
    fun `listColumns で Account のカラム一覧`() {
        val inspector = JdbcMetadataInspector(connection)
        val columns = inspector.listColumns("ACCOUNT")
        val names = columns.map { it.name.uppercase() }.sorted()
        assertEquals(listOf("ANNUALREVENUE", "CREATEDDATE", "ID", "INDUSTRY", "NAME"), names)
        val idColumn = columns.first { it.name.equals("ID", ignoreCase = true) }
        assertEquals(Types.VARCHAR, idColumn.jdbcType)
        val nameColumn = columns.first { it.name.equals("NAME", ignoreCase = true) }
        assertEquals(false, nameColumn.nullable)
        val industryColumn = columns.first { it.name.equals("INDUSTRY", ignoreCase = true) }
        assertEquals(true, industryColumn.nullable)
    }

    @Test
    fun `findPrimaryKey TEXT 型`() {
        val inspector = JdbcMetadataInspector(connection)
        val pk = inspector.findPrimaryKey("ACCOUNT")
        assertNotNull(pk)
        assertEquals(Types.VARCHAR, pk!!.jdbcType)
        assertEquals("ID", pk.column.uppercase())
    }

    @Test
    fun `findPrimaryKey BIGINT 型`() {
        val inspector = JdbcMetadataInspector(connection)
        val pk = inspector.findPrimaryKey("CONTACT")
        assertNotNull(pk)
        assertEquals(Types.BIGINT, pk!!.jdbcType)
    }

    @Test
    fun `findPrimaryKey なしのとき null`() {
        connection.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE NoPk (col1 VARCHAR(50))")
        }
        val inspector = JdbcMetadataInspector(connection)
        assertNull(inspector.findPrimaryKey("NOPK"))
    }

    @Test
    fun `distinctValues で選択肢候補を取得`() {
        val inspector = JdbcMetadataInspector(connection)
        val values = inspector.distinctValues("ACCOUNT", "INDUSTRY", limit = 10).sorted()
        assertEquals(listOf("Banking", "Retail"), values)
    }

    @Test
    fun `distinctValues limit が効く`() {
        val inspector = JdbcMetadataInspector(connection)
        val values = inspector.distinctValues("ACCOUNT", "NAME", limit = 2)
        assertEquals(2, values.size)
    }
}
