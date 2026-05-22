package com.cdata.kintone.adapter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * フェーズ1 構成（config 直下の YAML 群）からフェーズ2-A 構成
 * （config/tables/default/ 配下）への移行ヘルパ [ConfigMigrator] の単体テスト。
 */
class ConfigMigratorTest {

    private fun writePhase1Files(dir: Path) {
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
            url: "jdbc:salesforce:User=u;Password=p;"
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
            "record-id-type: TEXT\n",
        )
    }

    // --- detect ---

    @Test
    fun `detect - フェーズ1 構成を NEEDS_MIGRATION として検出`(@TempDir tempDir: Path) {
        writePhase1Files(tempDir)
        val migrator = ConfigMigrator(tempDir)
        assertEquals(ConfigMigrator.State.NEEDS_MIGRATION, migrator.detect())
    }

    @Test
    fun `detect - tables 配下がある場合は ALREADY_MIGRATED`(@TempDir tempDir: Path) {
        Files.createDirectories(tempDir.resolve("tables/account"))
        Files.writeString(tempDir.resolve("tables/account/server.yaml"), "port: 0\n")
        val migrator = ConfigMigrator(tempDir)
        assertEquals(ConfigMigrator.State.ALREADY_MIGRATED, migrator.detect())
    }

    @Test
    fun `detect - 何も設定がなければ EMPTY`(@TempDir tempDir: Path) {
        val migrator = ConfigMigrator(tempDir)
        assertEquals(ConfigMigrator.State.EMPTY, migrator.detect())
    }

    // --- migrate ---

    @Test
    fun `migrate - 旧4ファイルが tables-default 配下に移動`(@TempDir tempDir: Path) {
        writePhase1Files(tempDir)
        val migrator = ConfigMigrator(tempDir)
        val result = migrator.migrate(targetTableName = "default")

        val defaultDir = tempDir.resolve("tables/default")
        assertTrue(Files.exists(defaultDir.resolve("server.yaml")))
        assertTrue(Files.exists(defaultDir.resolve("jdbc.yaml")))
        assertTrue(Files.exists(defaultDir.resolve("table.yaml")))
        assertTrue(Files.exists(defaultDir.resolve("capability.yaml")))

        assertFalse(Files.exists(tempDir.resolve("server.yaml")))
        assertFalse(Files.exists(tempDir.resolve("jdbc.yaml")))
        assertFalse(Files.exists(tempDir.resolve("table.yaml")))
        assertFalse(Files.exists(tempDir.resolve("capability.yaml")))

        assertEquals(4, result.movedFiles.size)
        assertEquals("default", result.targetTable)
    }

    @Test
    fun `migrate - 任意のテーブル名を指定可能`(@TempDir tempDir: Path) {
        writePhase1Files(tempDir)
        val migrator = ConfigMigrator(tempDir)
        migrator.migrate(targetTableName = "account")

        assertTrue(Files.exists(tempDir.resolve("tables/account/server.yaml")))
        assertFalse(Files.exists(tempDir.resolve("tables/default/server.yaml")))
    }

    @Test
    fun `migrate - 移行後 YamlConfigSource で loadTableSet が成功`(@TempDir tempDir: Path) {
        writePhase1Files(tempDir)
        ConfigMigrator(tempDir).migrate()
        val source = YamlConfigSource(tempDir)
        val set = source.loadTableSet("default")
        assertEquals("Account", set.table.name)
        assertEquals(8083, set.server.port)
    }

    @Test
    fun `migrate - example ファイル等は移動しない`(@TempDir tempDir: Path) {
        writePhase1Files(tempDir)
        Files.writeString(tempDir.resolve("server.yaml.example"), "port: 9999\n")
        ConfigMigrator(tempDir).migrate()

        assertTrue(Files.exists(tempDir.resolve("server.yaml.example")), "example はそのまま残す")
        assertFalse(Files.exists(tempDir.resolve("tables/default/server.yaml.example")))
    }

    @Test
    fun `migrate - tables 配下が既にあるとエラー`(@TempDir tempDir: Path) {
        writePhase1Files(tempDir)
        Files.createDirectories(tempDir.resolve("tables/default"))
        assertThrows<IllegalStateException> {
            ConfigMigrator(tempDir).migrate()
        }
    }

    @Test
    fun `migrate - 旧構成がないとエラー`(@TempDir tempDir: Path) {
        assertThrows<IllegalStateException> {
            ConfigMigrator(tempDir).migrate()
        }
    }
}
