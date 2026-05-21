package com.cdata.kintone.adapter.config

import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigLoaderTest {

    private fun writeAll(dir: Path) {
        Files.writeString(
            dir.resolve("server.yaml"),
            """
            port: 9999
            bind-address: 0.0.0.0
            plaintext: true
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("jdbc.yaml"),
            """
            driver-class: cdata.jdbc.salesforce.SalesforceDriver
            driver-jar: ./lib/cdata.jdbc.salesforce.jar
            url: "jdbc:salesforce:User=${'$'}{SF_USER};"
            pool:
              maximum-pool-size: 5
              connection-timeout: 10000
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
              - kintone-field-id: industry
                jdbc-column: Industry
                type: SELECTION
                options: [Banking, Retail]
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("capability.yaml"),
            """
            select-supported: true
            insert-supported: true
            update-supported: false
            delete-supported: true
            count-supported: true
            count-strategy: ALWAYS_ZERO
            search-supported: false
            aggregate-supported: false
            record-id-type: TEXT
            filterable-fields: [id, name]
            sortable-fields: [id]
            """.trimIndent(),
        )
    }

    @Test
    fun `4ファイル揃った正常読込`(@TempDir tempDir: Path) {
        writeAll(tempDir)
        val loader = ConfigLoader(tempDir) { name -> if (name == "SF_USER") "alice" else null }
        val config = loader.load()

        assertEquals(9999, config.server.port)
        assertEquals("0.0.0.0", config.server.bindAddress)
        assertEquals("jdbc:salesforce:User=alice;", config.jdbc.url)
        assertEquals(5, config.jdbc.pool.maximumPoolSize)
        assertEquals("Account", config.table.name)
        assertEquals(2, config.table.columns.size)
        assertEquals(ColumnType.SELECTION, config.table.columns[1].type)
        assertEquals(listOf("Banking", "Retail"), config.table.columns[1].options)
        assertEquals(RecordIdType.TEXT, config.capability.recordIdType)
        assertEquals(CountStrategy.ALWAYS_ZERO, config.capability.countStrategy)
        assertEquals(listOf("id", "name"), config.capability.filterableFields)
    }

    @Test
    fun `未定義の環境変数はそのまま残る`(@TempDir tempDir: Path) {
        writeAll(tempDir)
        val loader = ConfigLoader(tempDir) { _ -> null }
        val config = loader.load()
        assertTrue(config.jdbc.url.contains("\${SF_USER}"))
    }

    @Test
    fun `server_yaml が欠けるとファイル欠落エラー`(@TempDir tempDir: Path) {
        writeAll(tempDir)
        Files.delete(tempDir.resolve("server.yaml"))
        assertThrows<ConfigFileMissingException> {
            ConfigLoader(tempDir).load()
        }
    }

    @Test
    fun `不正YAMLはパースエラー`(@TempDir tempDir: Path) {
        writeAll(tempDir)
        Files.writeString(tempDir.resolve("server.yaml"), "port: [this is not a valid int")
        assertThrows<ConfigParseException> {
            ConfigLoader(tempDir).load()
        }
    }

    @Test
    fun `expandEnvVars 展開`() {
        val text = "url=jdbc:test://\${HOST}:\${PORT}/db user=\${USER}"
        val result = ConfigLoader.expandEnvVars(text) { name ->
            when (name) {
                "HOST" -> "localhost"
                "PORT" -> "5432"
                else -> null
            }
        }
        assertEquals("url=jdbc:test://localhost:5432/db user=\${USER}", result)
    }
}
