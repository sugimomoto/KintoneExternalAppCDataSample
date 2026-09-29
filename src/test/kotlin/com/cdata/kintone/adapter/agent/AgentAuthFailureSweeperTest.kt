package com.cdata.kintone.adapter.agent

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentAuthFailureSweeperTest {

    private val authFailureLog =
        """{"level":"ERROR","msg":"failed to connect to kintone","err":"Unauthenticated desc = invalid token"}"""
    private val healthyLog = """{"level":"INFO","msg":"successfully connected to kintone"}"""

    private fun containerInfo(syncName: String) = AgentContainerManager.ContainerInfo(
        name = "${AgentContainerManager.CONTAINER_PREFIX}$syncName",
        state = AgentContainerManager.State.RUNNING,
        image = "kintone-data-connector-agent:0.9.2",
        createdAt = null,
    )

    private fun containerManager(logsBySync: Map<String, String>): AgentContainerManager = mockk(relaxed = true) {
        every { listAll() } returns logsBySync.keys.map { containerInfo(it) }
        logsBySync.forEach { (syncName, logs) ->
            every { fetchLogs(syncName, any(), any()) } returns logs
        }
    }

    @Test
    fun `認証失敗しているコンテナを停止して連携名を返す`() {
        val containerMgr = containerManager(mapOf("Product" to authFailureLog))

        val stopped = AgentAuthFailureSweeper(containerMgr).sweep()

        assertEquals(listOf("Product"), stopped)
        verify(exactly = 1) { containerMgr.stop("Product", any()) }
    }

    @Test
    fun `正常稼働中のコンテナは停止しない`() {
        val containerMgr = containerManager(mapOf("Categories" to healthyLog))

        val stopped = AgentAuthFailureSweeper(containerMgr).sweep()

        assertTrue(stopped.isEmpty(), "停止対象: $stopped")
        verify(exactly = 0) { containerMgr.stop(any(), any()) }
    }

    @Test
    fun `直近ログが空のコンテナは停止しない`() {
        // 正常稼働中のコンテナは時間窓内に何も出力しない。
        val containerMgr = containerManager(mapOf("AccountHistory" to ""))

        assertTrue(AgentAuthFailureSweeper(containerMgr).sweep().isEmpty())
        verify(exactly = 0) { containerMgr.stop(any(), any()) }
    }

    @Test
    fun `失敗しているコンテナだけを停止する`() {
        val containerMgr = containerManager(
            mapOf(
                "Product" to authFailureLog,
                "Categories" to healthyLog,
                "bcartorders" to authFailureLog,
                "AccountHistory" to "",
            ),
        )

        val stopped = AgentAuthFailureSweeper(containerMgr).sweep()

        assertEquals(listOf("Product", "bcartorders"), stopped)
        verify(exactly = 1) { containerMgr.stop("Product", any()) }
        verify(exactly = 1) { containerMgr.stop("bcartorders", any()) }
        verify(exactly = 0) { containerMgr.stop("Categories", any()) }
    }

    @Test
    fun `ログ取得が例外を投げても他のコンテナの処理を続ける`() {
        val containerMgr = containerManager(mapOf("Broken" to "", "Product" to authFailureLog))
        every { containerMgr.fetchLogs("Broken", any(), any()) } throws RuntimeException("docker error")

        val stopped = AgentAuthFailureSweeper(containerMgr).sweep()

        assertEquals(listOf("Product"), stopped)
    }

    @Test
    fun `停止が例外を投げても他のコンテナの処理を続ける`() {
        val containerMgr = containerManager(
            mapOf("Broken" to authFailureLog, "Product" to authFailureLog),
        )
        every { containerMgr.stop("Broken", any()) } throws RuntimeException("docker error")

        val stopped = AgentAuthFailureSweeper(containerMgr).sweep()

        assertEquals(listOf("Product"), stopped, "停止に失敗したものは返さない")
    }

    @Test
    fun `管理対象コンテナが無ければ何もしない`() {
        val containerMgr = containerManager(emptyMap())

        assertTrue(AgentAuthFailureSweeper(containerMgr).sweep().isEmpty())
        verify(exactly = 0) { containerMgr.stop(any(), any()) }
    }

    @Test
    fun `指定した時間窓でログを取得する`() {
        val containerMgr = containerManager(mapOf("Product" to authFailureLog))
        val since = slot<Int>()
        every { containerMgr.fetchLogs("Product", any(), capture(since)) } returns authFailureLog

        AgentAuthFailureSweeper(containerMgr, logWindowSeconds = 120).sweep()

        // 全ログを見ると、過去に失敗して復旧済みの連携まで止めてしまう。
        assertEquals(120, since.captured)
    }
}
