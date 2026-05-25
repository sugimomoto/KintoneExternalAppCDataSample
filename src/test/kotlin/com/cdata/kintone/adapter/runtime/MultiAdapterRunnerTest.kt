package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.ConfigFileMissingException
import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MultiAdapterRunnerTest {

    private val runners = mutableListOf<MultiAdapterRunner>()

    @AfterEach
    fun tearDown() {
        runners.forEach { runCatching { it.close() } }
        runners.clear()
    }

    private fun makeConfigSource(tableNames: List<String>, autoPort: Boolean = true): ConfigSource =
        FakeConfigSource(
            sets = tableNames.associateWith { name ->
                buildSet(name, port = if (autoPort) 0 else 9_000)
            },
        )

    private fun buildSet(name: String, port: Int): TableConfigSet = TableConfigSet(
        server = ServerConfig(port = port, bindAddress = "127.0.0.1", plaintext = true),
        jdbc = JdbcConfig(
            driverClass = "fake.Driver",
            driverJar = "/dev/null",
            url = "jdbc:fake:$name",
        ),
        table = TableConfig(
            name = name.replaceFirstChar { it.uppercase() },
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        ),
        capability = CapabilityConfig(recordIdType = RecordIdType.TEXT),
    )

    private fun makeRunner(source: ConfigSource): MultiAdapterRunner {
        val runner = MultiAdapterRunner(
            configSource = source,
            connectionProviderFactory = { _ -> FakeConnectionProvider() },
        )
        runners.add(runner)
        return runner
    }

    @Test
    fun `startOne - 1 テーブル起動できる`() {
        val source = makeConfigSource(listOf("account"))
        val runner = makeRunner(source)
        runner.startOne("account")
        assertEquals(1, runner.listActive().size)
        assertEquals("account", runner.listActive()[0].tableName)
    }

    @Test
    fun `startAll - listTables 全件を一括起動`() {
        val source = makeConfigSource(listOf("account", "contact"))
        val runner = makeRunner(source)
        runner.startAll()
        val active = runner.listActive().map { it.tableName }.toSet()
        assertEquals(setOf("account", "contact"), active)
    }

    @Test
    fun `異なるポートで複数テーブル起動 - port 衝突なし`() {
        val source = makeConfigSource(listOf("a", "b", "c"))
        val runner = makeRunner(source)
        runner.startAll()
        val ports = runner.listActive().map { it.port }
        assertEquals(ports.size, ports.toSet().size, "全テーブルが異なるポートで起動")
        ports.forEach { assertNotEquals(0, it) }
    }

    @Test
    fun `stopOne - 1 テーブルのみ停止 他は継続`() {
        val source = makeConfigSource(listOf("a", "b"))
        val runner = makeRunner(source)
        runner.startAll()
        runner.stopOne("a")
        val remaining = runner.listActive().map { it.tableName }
        assertEquals(listOf("b"), remaining)
    }

    @Test
    fun `同じテーブルを 2 回起動するとエラー`() {
        val source = makeConfigSource(listOf("a"))
        val runner = makeRunner(source)
        runner.startOne("a")
        assertThrows<IllegalStateException> { runner.startOne("a") }
    }

    @Test
    fun `存在しないテーブル名はエラー`() {
        val source = makeConfigSource(listOf("a"))
        val runner = makeRunner(source)
        assertThrows<ConfigFileMissingException> { runner.startOne("nonexistent") }
    }

    @Test
    fun `startAll - 1 件失敗しても他は起動継続`() {
        val source = FakeConfigSource(
            sets = mapOf(
                "good" to buildSet("good", port = 0),
                // bad は loadTableSet で例外
            ),
            failingTables = setOf("bad"),
            tableList = listOf("good", "bad"),
        )
        val runner = makeRunner(source)
        runner.startAll()
        val active = runner.listActive().map { it.tableName }
        assertEquals(listOf("good"), active, "bad は失敗したが good は起動")
    }

    @Test
    fun `close - 全停止 + 状態クリア`() {
        val source = makeConfigSource(listOf("a", "b"))
        val runner = makeRunner(source)
        runner.startAll()
        runner.close()
        assertTrue(runner.listActive().isEmpty())
    }

    @Test
    fun `listActive - port と startedAt を含む`() {
        val source = makeConfigSource(listOf("a"))
        val runner = makeRunner(source)
        runner.startOne("a")
        val status = runner.listActive().first()
        assertEquals("a", status.tableName)
        assertTrue(status.port > 0)
        assertTrue(status.startedAt > 0)
    }
}

/** テスト用 [ConfigSource] 実装。 */
private class FakeConfigSource(
    private val sets: Map<String, TableConfigSet>,
    private val failingTables: Set<String> = emptySet(),
    private val tableList: List<String> = sets.keys.toList() + failingTables.toList(),
) : ConfigSource {
    override fun listTables(): List<String> = tableList
    override fun loadTableSet(tableName: String): TableConfigSet {
        if (tableName in failingTables) {
            throw RuntimeException("simulated load failure: $tableName")
        }
        return sets[tableName]
            ?: throw ConfigFileMissingException("not found: $tableName")
    }
    override fun saveTableSet(tableName: String, set: TableConfigSet) = error("not used")
    override fun deleteTable(tableName: String) = error("not used")
    override fun loadSharedJdbcConfig(name: String): JdbcConfig? = null
    override fun listSharedJdbcConfigs(): List<String> = emptyList()
    override fun saveSharedJdbcConfig(name: String, config: JdbcConfig) = error("not used")
    override fun deleteSharedJdbcConfig(name: String) = error("not used")
}
