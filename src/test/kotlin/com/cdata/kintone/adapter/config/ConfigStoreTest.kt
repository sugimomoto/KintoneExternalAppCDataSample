package com.cdata.kintone.adapter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

class ConfigStoreTest {
    @Test
    fun `configDir 直下の config_db を既定のパスとする`(
        @TempDir tempDir: Path,
    ) {
        assertEquals(tempDir.resolve("config.db"), ConfigStore.resolveDbPath(tempDir))
    }

    @Test
    fun `sqlitePath を指定するとそちらが優先される`(
        @TempDir tempDir: Path,
    ) {
        val explicit = tempDir.resolve("custom/other.db")
        assertEquals(explicit, ConfigStore.resolveDbPath(tempDir, explicit))
    }

    @Test
    fun `config_db が無くても開けてスキーマが自動生成される`(
        @TempDir tempDir: Path,
    ) {
        val dbPath = tempDir.resolve("config.db")
        assertTrue(!dbPath.exists(), "前提: DB ファイルは存在しない")

        val source = ConfigStore.open(tempDir, envResolver = { null })

        assertTrue(dbPath.exists(), "DB ファイルが生成されること")
        assertTrue(source.listTables().isEmpty(), "連携 0 件として扱えること")
        assertTrue(source.listSharedJdbcConfigs().isEmpty(), "共有 JDBC 0 件として扱えること")
    }

    @Test
    fun `親ディレクトリが無い sqlitePath でも作成される`(
        @TempDir tempDir: Path,
    ) {
        val nested = tempDir.resolve("a/b/c/config.db")
        val source = ConfigStore.open(tempDir, sqlitePath = nested, envResolver = { null })
        assertTrue(nested.exists())
        assertTrue(source.listTables().isEmpty())
    }

    @Test
    fun `保存した連携を読み戻せる`(
        @TempDir tempDir: Path,
    ) {
        val source = ConfigStore.open(tempDir, envResolver = { null })
        val set =
            TableConfigSet(
                server = ServerConfig(port = 18001, bindAddress = "0.0.0.0"),
                jdbc =
                    JdbcConfig(
                        driverClass = "cdata.jdbc.salesforce.SalesforceDriver",
                        driverJar = "./lib/cdata.jdbc.salesforce.jar",
                        url = "jdbc:salesforce:User=u;",
                    ),
                table =
                    TableConfig(
                        name = "Account",
                        primaryKey = PrimaryKeyConfig("id", "Id"),
                        columns = emptyList(),
                    ),
                capability = CapabilityConfig(recordIdType = com.cdata.kintone.adapter.metadata.RecordIdType.TEXT),
            )
        source.saveTableSet("account", set)

        val reopened = ConfigStore.open(tempDir, envResolver = { null })
        assertEquals(listOf("account"), reopened.listTables())
        assertEquals("Account", reopened.loadTableSet("account").table.name)
    }
}
