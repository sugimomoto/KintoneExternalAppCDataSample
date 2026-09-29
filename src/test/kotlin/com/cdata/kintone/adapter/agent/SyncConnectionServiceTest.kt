package com.cdata.kintone.adapter.agent

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
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * [SyncConnectionService] の接続フロー検証 (Issue #2, #4, #7)。
 */
class SyncConnectionServiceTest {

    @TempDir
    lateinit var agentRoot: Path

    private val syncName = "Categories"
    private val adapterPort = 18_010

    private fun configSource(): ConfigSource = mockk {
        every { loadTableSet(syncName) } returns TableConfigSet(
            server = ServerConfig(port = adapterPort, bindAddress = "0.0.0.0", plaintext = true),
            jdbc = JdbcConfig(driverClass = "fake.Driver", driverJar = "/dev/null", url = "jdbc:fake:x"),
            table = TableConfig(
                name = syncName,
                primaryKey = PrimaryKeyConfig(kintoneFieldId = "id", jdbcColumn = "Id"),
                columns = listOf(ColumnConfig(kintoneFieldId = "name", jdbcColumn = "Name", type = ColumnType.TEXT)),
            ),
            capability = CapabilityConfig(
                recordIdType = RecordIdType.NUMBER,
                filterableFields = listOf("id"),
                sortableFields = listOf("id"),
            ),
        )
    }

    /** Adapter は既に起動中 (startOne が IllegalStateException) という前提。 */
    private fun runner(): MultiAdapterRunner = mockk {
        every { startOne(syncName) } throws IllegalStateException("テーブルはすでに起動中です: $syncName")
    }

    private fun reachable(): AdapterReachabilityChecker = mockk {
        every { check(any()) } returns AdapterReachabilityChecker.Result.Reachable("host.docker.internal:$adapterPort")
    }

    private fun containerManager(
        state: AgentContainerManager.State,
        logs: String,
    ): AgentContainerManager = mockk(relaxed = true) {
        every { status(syncName) } returns AgentContainerManager.ContainerInfo(
            name = "kintone-agent-$syncName",
            state = state,
            image = "kintone-data-connector-agent:0.9.2",
            createdAt = null,
        )
        every { fetchLogs(syncName, any(), any()) } returns logs
    }

    private fun service(
        containerMgr: AgentContainerManager,
        checker: AdapterReachabilityChecker = reachable(),
    ) = SyncConnectionService(
        configSource = configSource(),
        runner = runner(),
        agentConfigManager = AgentConfigManager(agentRoot),
        agentContainerManager = containerMgr,
        reachabilityChecker = checker,
    )

    @Test
    fun `running なコンテナは restart される`() {
        val containerMgr = containerManager(
            AgentContainerManager.State.RUNNING,
            logs = "successfully connected to kintone",
        )

        val result = service(containerMgr).connect(syncName, "token-123", waitTimeoutSec = 1)

        assertEquals(SyncConnectionService.Result.Success, result)
        // agent.json はプロセス起動時にしか読まれないため start では不十分 (Issue #2)
        verify(exactly = 1) { containerMgr.restart(syncName) }
        verify(exactly = 0) { containerMgr.start(syncName) }
    }

    @Test
    fun `stopped なコンテナは start される`() {
        val containerMgr = containerManager(
            AgentContainerManager.State.STOPPED,
            logs = "successfully connected to kintone",
        )

        service(containerMgr).connect(syncName, "token-123", waitTimeoutSec = 1)

        verify(exactly = 1) { containerMgr.start(syncName) }
        verify(exactly = 0) { containerMgr.restart(syncName) }
    }

    @Test
    fun `接続操作以降のログだけを判定対象にする`() {
        val containerMgr = containerManager(
            AgentContainerManager.State.RUNNING,
            logs = "successfully connected to kintone",
        )
        val since = slot<Int>()
        every { containerMgr.fetchLogs(syncName, any(), capture(since)) } returns
            "successfully connected to kintone"

        service(containerMgr).connect(syncName, "token-123", waitTimeoutSec = 1)

        // sinceSeconds を渡さないと過去の接続成功ログを拾ってしまう (Issue #4)
        assertNotNull(since.captured)
        assertTrue(since.captured <= 5, "since が広すぎる: ${since.captured}")
    }

    @Test
    fun `トークン失効を検知したら即座に失敗を返す`() {
        val containerMgr = containerManager(
            AgentContainerManager.State.RUNNING,
            logs = """{"level":"ERROR","msg":"failed to receive request","err":"token has been revoked"}""",
        )

        val result = service(containerMgr).connect(syncName, "expired-token", waitTimeoutSec = 30)

        val failure = assertInstanceOf(SyncConnectionService.Result.Failure::class.java, result)
        assertTrue(failure.reason.contains("接続キー"), "実際のメッセージ: ${failure.reason}")
    }

    @Test
    fun `Adapter に到達できない場合はコンテナを触らず失敗を返す`() {
        val containerMgr = containerManager(AgentContainerManager.State.RUNNING, logs = "")
        val unreachable: AdapterReachabilityChecker = mockk {
            every { check(any()) } returns AdapterReachabilityChecker.Result.Unreachable(
                "host.docker.internal:43343",
                "Adapter (ポート 43343) に到達できません。",
            )
        }

        val result = service(containerMgr, unreachable).connect(syncName, "token-123", waitTimeoutSec = 1)

        val failure = assertInstanceOf(SyncConnectionService.Result.Failure::class.java, result)
        assertTrue(failure.reason.contains("到達できません"), "実際のメッセージ: ${failure.reason}")
        verify(exactly = 0) { containerMgr.restart(syncName) }
        verify(exactly = 0) { containerMgr.start(syncName) }
    }

    @Test
    fun `接続確認が取れなければ Pending を返す`() {
        val containerMgr = containerManager(AgentContainerManager.State.RUNNING, logs = "starting agent")

        val result = service(containerMgr).connect(syncName, "token-123", waitTimeoutSec = 1)

        assertInstanceOf(SyncConnectionService.Result.Pending::class.java, result)
    }

    @Test
    fun `認証失敗したらコンテナを停止して再試行を断つ`() {
        // 接続キーが無効な状態では再試行しても直らない。restart: unless-stopped の
        // まま放置すると 1 分おきに永久に再試行する (Issue #19)。
        val containerMgr = containerManager(
            AgentContainerManager.State.RUNNING,
            logs = """{"level":"ERROR","err":"rpc error: code = Unauthenticated desc = invalid token"}""",
        )

        val result = service(containerMgr).connect(syncName, "stale-token", waitTimeoutSec = 1)

        assertInstanceOf(SyncConnectionService.Result.Failure::class.java, result)
        verify(exactly = 1) { containerMgr.stop(syncName, any()) }
    }

    @Test
    fun `接続に成功したらコンテナを停止しない`() {
        val containerMgr = containerManager(
            AgentContainerManager.State.RUNNING,
            logs = "successfully connected to kintone",
        )

        service(containerMgr).connect(syncName, "token-123", waitTimeoutSec = 1)

        verify(exactly = 0) { containerMgr.stop(syncName, any()) }
    }

    @Test
    fun `接続確認がタイムアウトしてもコンテナを停止しない`() {
        // Adapter の起動待ちなど一過性の要因がありうる。Docker の再起動で復帰する
        // 余地を残す。
        val containerMgr = containerManager(AgentContainerManager.State.RUNNING, logs = "starting agent")

        val result = service(containerMgr).connect(syncName, "token-123", waitTimeoutSec = 1)

        assertInstanceOf(SyncConnectionService.Result.Pending::class.java, result)
        verify(exactly = 0) { containerMgr.stop(syncName, any()) }
    }

    @Test
    fun `停止に失敗しても認証失敗として結果を返す`() {
        val containerMgr = containerManager(
            AgentContainerManager.State.RUNNING,
            logs = """{"level":"ERROR","err":"invalid token"}""",
        )
        every { containerMgr.stop(syncName, any()) } throws RuntimeException("docker error")

        val result = service(containerMgr).connect(syncName, "stale-token", waitTimeoutSec = 1)

        val failure = assertInstanceOf(SyncConnectionService.Result.Failure::class.java, result)
        assertTrue(failure.reason.contains("接続キー"), "実際のメッセージ: ${failure.reason}")
    }
}
