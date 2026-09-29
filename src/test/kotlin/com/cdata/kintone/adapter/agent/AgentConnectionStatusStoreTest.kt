package com.cdata.kintone.adapter.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AgentConnectionStatusStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun store() = AgentConnectionStatusStore(tempDir.resolve("agent-connection-status.json"))

    private fun status(syncName: String, reason: String = "接続キーが kintone に拒否されました") =
        AgentConnectionStatus(
            syncName = syncName,
            state = AgentConnectionStatus.State.AUTH_REJECTED,
            reason = reason,
            detectedAt = 1_759_000_000_000L,
        )

    @Test
    fun `記録した内容を読み出せる`() {
        val store = store()
        store.record(status("Product"))

        val loaded = store.get("Product")
        assertEquals(AgentConnectionStatus.State.AUTH_REJECTED, loaded?.state)
        assertEquals("接続キーが kintone に拒否されました", loaded?.reason)
        assertEquals(1_759_000_000_000L, loaded?.detectedAt)
    }

    @Test
    fun `複数の連携を記録できる`() {
        val store = store()
        store.record(status("Product"))
        store.record(status("bcartorders"))

        assertEquals(setOf("Product", "bcartorders"), store.all().keys)
    }

    @Test
    fun `同じ連携を再記録すると上書きされる`() {
        val store = store()
        store.record(status("Product", reason = "1 回目"))
        store.record(status("Product", reason = "2 回目"))

        assertEquals(1, store.all().size)
        assertEquals("2 回目", store.get("Product")?.reason)
    }

    @Test
    fun `clear で指定した連携だけ消える`() {
        val store = store()
        store.record(status("Product"))
        store.record(status("bcartorders"))

        store.clear("Product")

        assertNull(store.get("Product"))
        assertEquals(setOf("bcartorders"), store.all().keys)
    }

    @Test
    fun `記録が無い連携は null を返す`() {
        val store = store()
        store.record(status("Product"))

        assertNull(store.get("Categories"))
    }

    @Test
    fun `記録の無い連携を clear しても失敗しない`() {
        assertDoesNotThrow { store().clear("Categories") }
    }

    @Test
    fun `ファイルが存在しないときは空を返す`() {
        val store = store()

        assertEquals(emptyMap<String, AgentConnectionStatus>(), store.all())
        assertNull(store.get("Product"))
    }

    @Test
    fun `ファイルが壊れていても例外を投げず空を返す`() {
        // 補助情報のため、壊れたファイルで画面を落としてはいけない。
        val path = tempDir.resolve("agent-connection-status.json")
        Files.writeString(path, "{ これは JSON ではない")
        val store = AgentConnectionStatusStore(path)

        assertEquals(emptyMap<String, AgentConnectionStatus>(), assertDoesNotThrow { store.all() })
    }

    @Test
    fun `記録には接続キーを含めない`() {
        // 記録するのは理由と時刻だけ。トークンを渡す経路が無いことを型で担保する。
        val store = store()
        store.record(status("Product"))

        val text = Files.readString(tempDir.resolve("agent-connection-status.json"))
        assertTrue(text.contains("AUTH_REJECTED"), "実際の内容: $text")
        assertTrue(
            AgentConnectionStatus::class.java.declaredFields.none { it.name.lowercase().contains("token") },
            "記録に token フィールドがあってはいけない",
        )
    }

    @Test
    fun `親ディレクトリが無くても記録できる`() {
        val store = AgentConnectionStatusStore(tempDir.resolve("nested/dir/status.json"))

        assertDoesNotThrow { store.record(status("Product")) }
        assertEquals(AgentConnectionStatus.State.AUTH_REJECTED, store.get("Product")?.state)
    }
}
