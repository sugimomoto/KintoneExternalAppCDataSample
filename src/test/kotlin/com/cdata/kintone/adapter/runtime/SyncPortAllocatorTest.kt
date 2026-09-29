package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [SyncPortAllocator] の検証 (Issue #3)。
 *
 * 既定レンジ (18000-18099) は Docker が publish していると実ポートが埋まっているため、
 * テストではレンジを明示して環境に依存しないようにする。
 */
class SyncPortAllocatorTest {

    @Test
    fun `使用中ポートを避けて採番する`() {
        val source = MutableFakeConfigSource(
            mutableMapOf(
                "a" to set(19_700),
                "b" to set(19_701),
            ),
        )
        val allocator = SyncPortAllocator(source, rangeStart = 19_700, rangeEnd = 19_710)

        val port = allocator.allocate()

        assertFalse(port == 19_700 || port == 19_701, "使用中ポートが採番された: $port")
        assertTrue(port in 19_700..19_710)
    }

    @Test
    fun `自分自身のポートは除外できる`() {
        val source = MutableFakeConfigSource(mutableMapOf("a" to set(19_720)))
        val allocator = SyncPortAllocator(source, rangeStart = 19_720, rangeEnd = 19_720)

        // excludeSync を指定しなければ空きなしで失敗する
        assertThrows<IllegalStateException> { allocator.allocate() }
        // 自分自身を除外すれば同じポートを再利用できる
        assertEquals(19_720, allocator.allocate(excludeSync = "a"))
    }

    @Test
    fun `port 0 は使用中として扱わない`() {
        val source = MutableFakeConfigSource(mutableMapOf("a" to set(0), "b" to set(0)))
        val allocator = SyncPortAllocator(source, rangeStart = 19_730, rangeEnd = 19_731)

        assertEquals(19_730, allocator.allocate())
        assertEquals(setOf<Int>(), allocator.usedPorts())
    }

    @Test
    fun `レンジを使い切ったら IllegalStateException`() {
        val source = MutableFakeConfigSource(
            mutableMapOf("a" to set(19_740), "b" to set(19_741)),
        )
        val allocator = SyncPortAllocator(source, rangeStart = 19_740, rangeEnd = 19_741)

        val e = assertThrows<IllegalStateException> { allocator.allocate() }
        assertTrue(e.message!!.contains("空きがありません"), "実際のメッセージ: ${e.message}")
    }

    @Test
    fun `設定が壊れているテーブルがあっても採番できる`() {
        val source = MutableFakeConfigSource(
            mutableMapOf("ok" to set(19_750)),
            failingTables = setOf("broken"),
        )
        val allocator = SyncPortAllocator(source, rangeStart = 19_750, rangeEnd = 19_760)

        val port = allocator.allocate()
        assertTrue(port in 19_751..19_760)
    }
}

internal fun set(port: Int, tableName: String = "t"): TableConfigSet = TableConfigSet(
    server = ServerConfig(port = port, bindAddress = "0.0.0.0", plaintext = true),
    jdbc = JdbcConfig(driverClass = "fake.Driver", driverJar = "/dev/null", url = "jdbc:fake:$tableName"),
    table = TableConfig(
        name = tableName,
        primaryKey = PrimaryKeyConfig(kintoneFieldId = "id", jdbcColumn = "Id"),
        columns = listOf(ColumnConfig(kintoneFieldId = "name", jdbcColumn = "Name", type = ColumnType.TEXT)),
    ),
    capability = CapabilityConfig(
        recordIdType = RecordIdType.NUMBER,
        filterableFields = listOf("id"),
        sortableFields = listOf("id"),
    ),
)

/** 保存も出来る Fake。[PortMigrator] のテストと共用する。 */
internal class MutableFakeConfigSource(
    val sets: MutableMap<String, TableConfigSet>,
    private val failingTables: Set<String> = emptySet(),
    val sharedJdbc: MutableMap<String, JdbcConfig> = mutableMapOf(),
) : ConfigSource {
    val savedWithRef = mutableListOf<Pair<String, String>>()

    override fun listTables(): List<String> = sets.keys.toList() + failingTables.toList()
    override fun loadTableSet(tableName: String): TableConfigSet {
        if (tableName in failingTables) error("simulated load failure: $tableName")
        return sets[tableName] ?: error("not found: $tableName")
    }
    override fun saveTableSet(tableName: String, set: TableConfigSet) {
        sets[tableName] = set
    }
    override fun saveTableSetWithRef(tableName: String, set: TableConfigSet, jdbcRef: String) {
        sets[tableName] = set
        savedWithRef += tableName to jdbcRef
    }
    override fun deleteTable(tableName: String) {
        sets.remove(tableName)
    }
    override fun loadSharedJdbcConfig(name: String): JdbcConfig? = sharedJdbc[name]
    override fun listSharedJdbcConfigs(): List<String> = sharedJdbc.keys.toList()
    override fun saveSharedJdbcConfig(name: String, config: JdbcConfig) {
        sharedJdbc[name] = config
    }
    override fun deleteSharedJdbcConfig(name: String) {
        sharedJdbc.remove(name)
    }
}
