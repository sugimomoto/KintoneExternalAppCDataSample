package com.cdata.kintone.adapter.config

import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class YamlConfigSourceTest {

    // --- ヘルパー ---

    private fun writePhase1Layout(dir: Path) {
        Files.writeString(
            dir.resolve("server.yaml"),
            """
            port: 8083
            bind-address: 0.0.0.0
            plaintext: true
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("jdbc.yaml"),
            """
            driver-class: cdata.jdbc.salesforce.SalesforceDriver
            driver-jar: ./lib/cdata.jdbc.salesforce.jar
            url: "jdbc:salesforce:AuthScheme=Basic;User=u;Password=p;SecurityToken=t;"
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("table.yaml"),
            """
            name: Account
            primary-key:
              kintone-field-id: id
              jdbc-column: Id
            columns:
              - kintone-field-id: name
                jdbc-column: Name
                type: TEXT
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("capability.yaml"),
            """
            record-id-type: TEXT
            filterable-fields: [id, name]
            sortable-fields: [id]
            """.trimIndent(),
        )
    }

    private fun writeTableLayout(dir: Path, dirName: String, jdbcInline: Boolean = true, tableName: String = "Account") {
        val tableDir = dir.resolve("tables/$dirName")
        Files.createDirectories(tableDir)
        Files.writeString(tableDir.resolve("server.yaml"), "port: 0\nbind-address: 0.0.0.0\nplaintext: true\n")
        if (jdbcInline) {
            Files.writeString(
                tableDir.resolve("jdbc.yaml"),
                """
                driver-class: cdata.jdbc.salesforce.SalesforceDriver
                driver-jar: ./lib/cdata.jdbc.salesforce.jar
                url: "jdbc:salesforce:AuthScheme=Basic;User=u;Password=p;SecurityToken=t;"
                """.trimIndent(),
            )
        } else {
            Files.writeString(tableDir.resolve("jdbc-ref.yaml"), "name: salesforce\n")
        }
        Files.writeString(
            tableDir.resolve("table.yaml"),
            """
            name: $tableName
            primary-key:
              kintone-field-id: id
              jdbc-column: Id
            columns:
              - kintone-field-id: name
                jdbc-column: Name
                type: TEXT
            """.trimIndent(),
        )
        Files.writeString(
            tableDir.resolve("capability.yaml"),
            """
            record-id-type: TEXT
            filterable-fields: [id]
            sortable-fields: [id]
            """.trimIndent(),
        )
    }

    private fun writeSharedJdbc(dir: Path, name: String) {
        val jdbcDir = dir.resolve("jdbc")
        Files.createDirectories(jdbcDir)
        Files.writeString(
            jdbcDir.resolve("$name.yaml"),
            """
            driver-class: cdata.jdbc.salesforce.SalesforceDriver
            driver-jar: ./lib/cdata.jdbc.salesforce.jar
            url: "jdbc:salesforce:AuthScheme=OAuth;InitiateOAuth=GETANDREFRESH;"
            """.trimIndent(),
        )
    }

    // --- マルチテーブル構成 ---

    @Test
    fun `listTables - tables 配下のディレクトリ名を返す`(@TempDir tempDir: Path) {
        writeTableLayout(tempDir, "account")
        writeTableLayout(tempDir, "contact")
        val source = YamlConfigSource(tempDir)
        assertEquals(listOf("account", "contact"), source.listTables())
    }

    @Test
    fun `loadTableSet - 個別 jdbc-yaml を直接読込`(@TempDir tempDir: Path) {
        writeTableLayout(tempDir, "account", jdbcInline = true)
        val source = YamlConfigSource(tempDir)
        val set = source.loadTableSet("account")
        assertEquals("Account", set.table.name)
        assertEquals(RecordIdType.TEXT, set.capability.recordIdType)
        assertTrue(set.jdbc.url.contains("AuthScheme=Basic"))
    }

    @Test
    fun `loadTableSet - jdbc-ref-yaml で共通設定を解決`(@TempDir tempDir: Path) {
        writeSharedJdbc(tempDir, "salesforce")
        writeTableLayout(tempDir, "account", jdbcInline = false)
        val source = YamlConfigSource(tempDir)
        val set = source.loadTableSet("account")
        assertTrue(set.jdbc.url.contains("AuthScheme=OAuth"), "共通 jdbc が解決された")
    }

    @Test
    fun `loadTableSet - jdbc-yaml と jdbc-ref-yaml 両方ある場合は個別が優先`(@TempDir tempDir: Path) {
        writeSharedJdbc(tempDir, "salesforce")
        writeTableLayout(tempDir, "account", jdbcInline = true)
        // jdbc-ref も書く
        Files.writeString(tempDir.resolve("tables/account/jdbc-ref.yaml"), "name: salesforce\n")
        val source = YamlConfigSource(tempDir)
        val set = source.loadTableSet("account")
        assertTrue(set.jdbc.url.contains("AuthScheme=Basic"), "個別 jdbc が優先")
    }

    @Test
    fun `loadTableSet - jdbc も jdbc-ref もないとエラー`(@TempDir tempDir: Path) {
        val tableDir = tempDir.resolve("tables/account")
        Files.createDirectories(tableDir)
        Files.writeString(tableDir.resolve("server.yaml"), "port: 0\n")
        // jdbc.yaml も jdbc-ref.yaml も書かない
        Files.writeString(
            tableDir.resolve("table.yaml"),
            """
            name: Account
            primary-key: {kintone-field-id: id, jdbc-column: Id}
            columns: []
            """.trimIndent(),
        )
        Files.writeString(
            tableDir.resolve("capability.yaml"),
            "record-id-type: TEXT\n",
        )

        assertThrows<ConfigFileMissingException> {
            YamlConfigSource(tempDir).loadTableSet("account")
        }
    }

    @Test
    fun `loadTableSet - jdbc-ref に対応する共通設定がないとエラー`(@TempDir tempDir: Path) {
        // 共通 jdbc を作らず、参照だけ書く
        writeTableLayout(tempDir, "account", jdbcInline = false)
        assertThrows<ConfigParseException> {
            YamlConfigSource(tempDir).loadTableSet("account")
        }
    }

    @Test
    fun `loadSharedJdbcConfig - 存在しない名前は null`(@TempDir tempDir: Path) {
        val source = YamlConfigSource(tempDir)
        assertNull(source.loadSharedJdbcConfig("nonexistent"))
    }

    @Test
    fun `loadSharedJdbcConfig - 既存ファイルは読み込まれる`(@TempDir tempDir: Path) {
        writeSharedJdbc(tempDir, "salesforce")
        val source = YamlConfigSource(tempDir)
        val config = source.loadSharedJdbcConfig("salesforce")
        assertNotNull(config)
        assertTrue(config!!.url.contains("AuthScheme=OAuth"))
    }

    // --- saveTableSet / deleteTable ---

    @Test
    fun `saveTableSet で書き出し、loadTableSet で読み戻し`(@TempDir tempDir: Path) {
        val original = TableConfigSet(
            server = ServerConfig(port = 8084, bindAddress = "0.0.0.0", plaintext = true),
            jdbc = JdbcConfig(
                driverClass = "cdata.jdbc.salesforce.SalesforceDriver",
                driverJar = "./lib/cdata.jdbc.salesforce.jar",
                url = "jdbc:salesforce:AuthScheme=Basic;",
            ),
            table = TableConfig(
                name = "Contact",
                primaryKey = PrimaryKeyConfig("id", "Id"),
                columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
            ),
            capability = CapabilityConfig(
                recordIdType = RecordIdType.TEXT,
                filterableFields = listOf("id", "name"),
                sortableFields = listOf("id"),
            ),
        )
        val source = YamlConfigSource(tempDir)
        source.saveTableSet("contact", original)
        val loaded = source.loadTableSet("contact")
        assertEquals(original.table.name, loaded.table.name)
        assertEquals(original.server.port, loaded.server.port)
        assertEquals(original.capability.filterableFields, loaded.capability.filterableFields)
    }

    @Test
    fun `deleteTable でディレクトリが削除される`(@TempDir tempDir: Path) {
        writeTableLayout(tempDir, "account")
        val source = YamlConfigSource(tempDir)
        assertTrue(source.listTables().contains("account"))
        source.deleteTable("account")
        assertFalse(source.listTables().contains("account"))
        assertFalse(Files.exists(tempDir.resolve("tables/account")))
    }

    // --- フェーズ1 互換層 ---

    @Test
    fun `フェーズ1 構成 - listTables は default のみ返す`(@TempDir tempDir: Path) {
        writePhase1Layout(tempDir)
        val source = YamlConfigSource(tempDir)
        assertEquals(listOf("default"), source.listTables())
    }

    @Test
    fun `フェーズ1 構成 - loadTableSet default で読み込める`(@TempDir tempDir: Path) {
        writePhase1Layout(tempDir)
        val source = YamlConfigSource(tempDir)
        val set = source.loadTableSet("default")
        assertEquals("Account", set.table.name)
        assertEquals(8083, set.server.port)
    }

    @Test
    fun `マルチテーブル構成が優先 - tables 配下があれば default は無視`(@TempDir tempDir: Path) {
        writePhase1Layout(tempDir)
        writeTableLayout(tempDir, "account")
        val source = YamlConfigSource(tempDir)
        assertEquals(listOf("account"), source.listTables())
    }

    @Test
    fun `存在しないテーブルでエラー`(@TempDir tempDir: Path) {
        val source = YamlConfigSource(tempDir)
        assertThrows<ConfigFileMissingException> {
            source.loadTableSet("nonexistent")
        }
    }

    // --- 環境変数展開 ---

    @Test
    fun `loadTableSet - jdbc-url 内の環境変数が展開される`(@TempDir tempDir: Path) {
        val tableDir = tempDir.resolve("tables/account")
        Files.createDirectories(tableDir)
        Files.writeString(tableDir.resolve("server.yaml"), "port: 0\n")
        Files.writeString(
            tableDir.resolve("jdbc.yaml"),
            """
            driver-class: cdata.jdbc.salesforce.SalesforceDriver
            driver-jar: ./lib/cdata.jdbc.salesforce.jar
            url: "jdbc:salesforce:User=${'$'}{SF_USER};"
            """.trimIndent(),
        )
        Files.writeString(
            tableDir.resolve("table.yaml"),
            """
            name: Account
            primary-key: {kintone-field-id: id, jdbc-column: Id}
            columns: []
            """.trimIndent(),
        )
        Files.writeString(tableDir.resolve("capability.yaml"), "record-id-type: TEXT\n")

        val source = YamlConfigSource(tempDir) { name ->
            if (name == "SF_USER") "alice@example.com" else null
        }
        val set = source.loadTableSet("account")
        assertTrue(set.jdbc.url.contains("alice@example.com"))
    }

    @Test
    fun `expandEnvVars - 未定義の変数はそのまま残る`() {
        val text = "jdbc:test://\${HOST}/\${UNDEFINED}"
        val result = YamlConfigSource.expandEnvVars(text) { name ->
            if (name == "HOST") "localhost" else null
        }
        assertEquals("jdbc:test://localhost/\${UNDEFINED}", result)
    }
}
