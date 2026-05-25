package com.cdata.kintone.adapter.config

import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * YAML ⇄ SQLite 往復同値性のテスト。
 * migrate-to-sqlite / export-yaml の核となる処理。
 */
class YamlSqliteMigrationTest {

    private fun seedYamlConfig(yamlDir: Path) {
        // 共通 JDBC
        val jdbcDir = yamlDir.resolve("jdbc")
        Files.createDirectories(jdbcDir)
        Files.writeString(
            jdbcDir.resolve("salesforce.yaml"),
            """
            driver-class: cdata.jdbc.salesforce.SalesforceDriver
            driver-jar: ./lib/cdata.jdbc.salesforce.jar
            url: "jdbc:salesforce:User=u;Password=p;"
            """.trimIndent(),
        )
        // テーブル 1 (jdbc-ref)
        val acctDir = yamlDir.resolve("tables/account")
        Files.createDirectories(acctDir)
        Files.writeString(acctDir.resolve("server.yaml"), "port: 18001\nbind-address: 0.0.0.0\nplaintext: true\n")
        Files.writeString(acctDir.resolve("jdbc-ref.yaml"), "name: salesforce\n")
        Files.writeString(
            acctDir.resolve("table.yaml"),
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
        Files.writeString(acctDir.resolve("capability.yaml"), "record-id-type: TEXT\nfilterable-fields: [id]\nsortable-fields: [id]\n")

        // テーブル 2 (inline jdbc)
        val gsDir = yamlDir.resolve("tables/gs-order")
        Files.createDirectories(gsDir)
        Files.writeString(gsDir.resolve("server.yaml"), "port: 18003\nplaintext: true\n")
        Files.writeString(
            gsDir.resolve("jdbc.yaml"),
            """
            driver-class: cdata.jdbc.googlesheets.GoogleSheetsDriver
            driver-jar: ./lib/cdata.jdbc.googlesheets.jar
            url: "jdbc:googlesheets:SpreadsheetId=abc;"
            """.trimIndent(),
        )
        Files.writeString(gsDir.resolve("table.yaml"), "name: Orders\nprimary-key: {kintone-field-id: id, jdbc-column: Id}\ncolumns: []\n")
        Files.writeString(gsDir.resolve("capability.yaml"), "record-id-type: NUMBER\n")
    }

    @Test
    fun `YAML から SQLite への migrate`(@TempDir tempDir: Path) {
        val yamlDir = tempDir.resolve("yaml")
        val sqlitePath = tempDir.resolve("config.db")
        Files.createDirectories(yamlDir)
        seedYamlConfig(yamlDir)

        val yamlSource = YamlConfigSource(yamlDir)
        val sqliteSource = SqliteConfigSource(sqlitePath)
        ConfigMigrator.copyAll(from = yamlSource, to = sqliteSource)

        // Sqlite 側で同じデータが取れる
        assertEquals(listOf("salesforce"), sqliteSource.listSharedJdbcConfigs())
        assertEquals(listOf("account", "gs-order"), sqliteSource.listTables())

        val account = sqliteSource.loadTableSet("account")
        assertEquals("Account", account.table.name)
        assertTrue(account.jdbc.url.contains("User=u"), "shared JDBC が解決されている: ${account.jdbc.url}")

        val gs = sqliteSource.loadTableSet("gs-order")
        assertEquals("Orders", gs.table.name)
        assertTrue(gs.jdbc.url.contains("SpreadsheetId=abc"))
    }

    @Test
    fun `SQLite から YAML への export`(@TempDir tempDir: Path) {
        val yamlDir = tempDir.resolve("yaml")
        val sqlitePath = tempDir.resolve("config.db")
        Files.createDirectories(yamlDir)
        seedYamlConfig(yamlDir)

        // 1) yaml → sqlite
        val sqliteSource = SqliteConfigSource(sqlitePath)
        ConfigMigrator.copyAll(from = YamlConfigSource(yamlDir), to = sqliteSource)

        // 2) sqlite → 新しい yaml ディレクトリ
        val exportDir = tempDir.resolve("export")
        Files.createDirectories(exportDir)
        val exportSource = YamlConfigSource(exportDir)
        ConfigMigrator.copyAll(from = sqliteSource, to = exportSource)

        // 3) yaml ファイルが書かれている
        assertTrue(Files.exists(exportDir.resolve("jdbc/salesforce.yaml")))
        assertTrue(Files.exists(exportDir.resolve("tables/account/server.yaml")))
        assertTrue(Files.exists(exportDir.resolve("tables/account/jdbc.yaml")))  // インライン JDBC として保存
        assertTrue(Files.exists(exportDir.resolve("tables/account/table.yaml")))
        assertTrue(Files.exists(exportDir.resolve("tables/account/capability.yaml")))

        // 4) 往復同値性: 内容は元と同じ
        val exported = exportSource.loadTableSet("account")
        val original = YamlConfigSource(yamlDir).loadTableSet("account")
        assertEquals(original.table.name, exported.table.name)
        assertEquals(original.server.port, exported.server.port)
        assertEquals(original.jdbc.url, exported.jdbc.url)
    }
}
