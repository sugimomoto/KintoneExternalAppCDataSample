package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.JdbcConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [PortMigrator] の検証 (Issue #3)。
 */
class PortMigratorTest {

    private fun migrator(
        source: MutableFakeConfigSource,
        onPortChanged: (String, Int) -> Unit = { _, _ -> },
    ) = PortMigrator(
        configSource = source,
        allocator = SyncPortAllocator(source, rangeStart = 19_800, rangeEnd = 19_810),
        onPortChanged = onPortChanged,
    )

    @Test
    fun `port 0 の Sync を publish 範囲のポートへ移行する`() {
        val source = MutableFakeConfigSource(mutableMapOf("Categories" to set(0)))

        val migrated = migrator(source).migrate()

        assertEquals(1, migrated.size)
        assertEquals("Categories", migrated[0].syncName)
        assertTrue(migrated[0].newPort in 19_800..19_810)
        assertEquals(migrated[0].newPort, source.sets["Categories"]!!.server.port)
    }

    @Test
    fun `固定ポートの Sync は触らない`() {
        val source = MutableFakeConfigSource(mutableMapOf("gs-opportunity" to set(18_003)))

        val migrated = migrator(source).migrate()

        assertTrue(migrated.isEmpty())
        assertEquals(18_003, source.sets["gs-opportunity"]!!.server.port)
    }

    @Test
    fun `複数の port 0 を移行してもポートが衝突しない`() {
        val source = MutableFakeConfigSource(
            mutableMapOf("a" to set(0), "b" to set(0), "c" to set(0)),
        )

        val migrated = migrator(source).migrate()

        assertEquals(3, migrated.size)
        val ports = migrated.map { it.newPort }
        assertEquals(ports.size, ports.toSet().size, "ポートが衝突した: $ports")
    }

    @Test
    fun `移行したポートをコールバックで通知する`() {
        val source = MutableFakeConfigSource(mutableMapOf("Categories" to set(0)))
        val notified = mutableListOf<Pair<String, Int>>()

        val migrated = migrator(source) { name, port -> notified += name to port }.migrate()

        assertEquals(listOf("Categories" to migrated[0].newPort), notified)
    }

    @Test
    fun `jdbc-ref で保存されている Sync は参照形式を保つ`() {
        val jdbc = JdbcConfig(driverClass = "fake.Driver", driverJar = "/dev/null", url = "jdbc:fake:t")
        val source = MutableFakeConfigSource(
            mutableMapOf("Categories" to set(0)),
            sharedJdbc = mutableMapOf("bcart" to jdbc),
        )

        migrator(source).migrate()

        assertEquals(listOf("Categories" to "bcart"), source.savedWithRef)
    }

    @Test
    fun `読み込めない設定があっても他の Sync の移行は続行する`() {
        val source = MutableFakeConfigSource(
            mutableMapOf("ok" to set(0)),
            failingTables = setOf("broken"),
        )

        val migrated = migrator(source).migrate()

        assertEquals(listOf("ok"), migrated.map { it.syncName })
        assertFalse(source.sets["ok"]!!.server.isAutoPort())
    }
}
