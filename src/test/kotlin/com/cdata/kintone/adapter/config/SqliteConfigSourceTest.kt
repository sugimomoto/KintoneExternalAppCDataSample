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
import java.nio.file.Path

/**
 * SqliteConfigSource の単体テスト。
 *
 * ConfigSource 契約を満たすことを確認する。
 * Phase 2-B M1-C で `ConfigSourceContractTest` に共通化を検討。
 */
class SqliteConfigSourceTest {

    private fun newSource(tempDir: Path) = SqliteConfigSource(tempDir.resolve("config.db"))

    private fun sampleJdbc(scheme: String = "Basic") = JdbcConfig(
        driverClass = "cdata.jdbc.salesforce.SalesforceDriver",
        driverJar = "./lib/cdata.jdbc.salesforce.jar",
        url = "jdbc:salesforce:AuthScheme=$scheme;User=u;Password=p;",
    )

    private fun sampleTableSet(name: String = "Account") = TableConfigSet(
        server = ServerConfig(port = 18001, bindAddress = "0.0.0.0", plaintext = true),
        jdbc = sampleJdbc(),
        table = TableConfig(
            name = name,
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        ),
        capability = CapabilityConfig(
            recordIdType = RecordIdType.TEXT,
            filterableFields = listOf("id", "name"),
            sortableFields = listOf("id"),
        ),
    )

    // --- 初期化 ---

    @Test
    fun `初回 listTables は空`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        assertEquals(emptyList<String>(), source.listTables())
    }

    @Test
    fun `初回 listSharedJdbcConfigs は空`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        assertEquals(emptyList<String>(), source.listSharedJdbcConfigs())
    }

    // --- テーブル CRUD ---

    @Test
    fun `saveTableSet 後に listTables にあらわれる`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveTableSet("account", sampleTableSet())
        assertEquals(listOf("account"), source.listTables())
    }

    @Test
    fun `saveTableSet から loadTableSet で同等のセットを取得 (往復同値)`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        val original = sampleTableSet()
        source.saveTableSet("account", original)
        val loaded = source.loadTableSet("account")
        assertEquals(original.table.name, loaded.table.name)
        assertEquals(original.server.port, loaded.server.port)
        assertEquals(original.jdbc.url, loaded.jdbc.url)
        assertEquals(original.capability.filterableFields, loaded.capability.filterableFields)
        assertEquals(original.table.columns.size, loaded.table.columns.size)
    }

    @Test
    fun `存在しないテーブルで ConfigFileMissingException`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        assertThrows<ConfigFileMissingException> {
            source.loadTableSet("nonexistent")
        }
    }

    @Test
    fun `deleteTable で削除`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveTableSet("account", sampleTableSet())
        source.deleteTable("account")
        assertFalse(source.listTables().contains("account"))
    }

    @Test
    fun `deleteTable 存在しないテーブルは no-op`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.deleteTable("nope")
    }

    // --- 共通 JDBC ---

    @Test
    fun `saveSharedJdbcConfig 後に loadSharedJdbcConfig で取得`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveSharedJdbcConfig("salesforce", sampleJdbc("OAuth"))
        val loaded = source.loadSharedJdbcConfig("salesforce")
        assertNotNull(loaded)
        assertTrue(loaded!!.url.contains("AuthScheme=OAuth"))
    }

    @Test
    fun `loadSharedJdbcConfig 存在しないと null`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        assertNull(source.loadSharedJdbcConfig("nope"))
    }

    @Test
    fun `listSharedJdbcConfigs で一覧取得`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveSharedJdbcConfig("salesforce", sampleJdbc())
        source.saveSharedJdbcConfig("googlesheets", sampleJdbc())
        assertEquals(listOf("googlesheets", "salesforce"), source.listSharedJdbcConfigs())
    }

    @Test
    fun `deleteSharedJdbcConfig で削除`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveSharedJdbcConfig("salesforce", sampleJdbc())
        source.deleteSharedJdbcConfig("salesforce")
        assertNull(source.loadSharedJdbcConfig("salesforce"))
    }

    // --- jdbc-ref 解決 ---

    @Test
    fun `jdbc-ref 経由のテーブル - loadTableSet で共通 JDBC が解決される`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveSharedJdbcConfig("salesforce", sampleJdbc("OAuth"))
        // jdbc-ref で salesforce を参照するテーブル設定
        val original = sampleTableSet()
        // saveTableSet 内部で jdbc-ref への切り替えを判断するため、専用 API を用意する想定。
        // ここでは saveTableSetWithRef で明示的に ref モード指定
        source.saveTableSetWithRef("account", original, jdbcRef = "salesforce")
        val loaded = source.loadTableSet("account")
        assertTrue(loaded.jdbc.url.contains("AuthScheme=OAuth"), "共通 JDBC が解決された: ${loaded.jdbc.url}")
    }

    @Test
    fun `jdbc-ref が指す共通 JDBC がない場合は ConfigParseException`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveTableSetWithRef("account", sampleTableSet(), jdbcRef = "missing")
        assertThrows<ConfigParseException> {
            source.loadTableSet("account")
        }
    }

    // --- 環境変数展開 ---

    @Test
    fun `環境変数プレースホルダが展開される`(@TempDir tempDir: Path) {
        val source = SqliteConfigSource(tempDir.resolve("config.db")) { name ->
            if (name == "SF_USER") "alice" else null
        }
        val original = sampleTableSet().copy(
            jdbc = sampleJdbc().copy(url = "jdbc:salesforce:User=\${SF_USER};"),
        )
        source.saveTableSet("account", original)
        val loaded = source.loadTableSet("account")
        assertTrue(loaded.jdbc.url.contains("alice"), "展開後: ${loaded.jdbc.url}")
    }

    // --- スキーマ初期化の冪等性 ---

    @Test
    fun `2 回 instantiate しても問題ない`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("config.db")
        SqliteConfigSource(path).saveTableSet("a", sampleTableSet())
        // 別インスタンスで開き直す
        val source2 = SqliteConfigSource(path)
        assertEquals(listOf("a"), source2.listTables())
    }

    // --- OAuth キャッシュを接続単位で共有するための参照名解決 (Issue #11) ---

    @Test
    fun `sharedJdbcRefOf - 共通 JDBC を参照する連携は参照名を返す`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveSharedJdbcConfig("sf-main", sampleJdbc())
        source.saveTableSetWithRef("Account", sampleTableSet(), jdbcRef = "sf-main")

        assertEquals("sf-main", source.sharedJdbcRefOf("Account"))
    }

    @Test
    fun `sharedJdbcRefOf - 同じ接続を参照する複数の連携が同じ名前を返す`(@TempDir tempDir: Path) {
        // 同一接続の連携がキャッシュを共有できることが本質。
        val source = newSource(tempDir)
        source.saveSharedJdbcConfig("sf-main", sampleJdbc())
        source.saveTableSetWithRef("Account", sampleTableSet("Account"), jdbcRef = "sf-main")
        source.saveTableSetWithRef("Contact", sampleTableSet("Contact"), jdbcRef = "sf-main")

        assertEquals("sf-main", source.sharedJdbcRefOf("Account"))
        assertEquals(source.sharedJdbcRefOf("Account"), source.sharedJdbcRefOf("Contact"))
    }

    @Test
    fun `sharedJdbcRefOf - インライン設定の連携は null を返す`(@TempDir tempDir: Path) {
        val source = newSource(tempDir)
        source.saveTableSet("Inline", sampleTableSet("Inline"))

        assertNull(source.sharedJdbcRefOf("Inline"))
    }

    @Test
    fun `sharedJdbcRefOf - 存在しない連携は null を返す`(@TempDir tempDir: Path) {
        assertNull(newSource(tempDir).sharedJdbcRefOf("NotExist"))
    }
}
