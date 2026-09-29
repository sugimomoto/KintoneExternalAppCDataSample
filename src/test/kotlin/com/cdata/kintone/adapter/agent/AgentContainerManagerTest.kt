package com.cdata.kintone.adapter.agent

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectContainerCmd
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.command.RestartContainerCmd
import com.github.dockerjava.api.command.StartContainerCmd
import com.github.dockerjava.api.command.StopContainerCmd
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.NotModifiedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * [AgentContainerManager] の Docker 応答ハンドリングを検証する (Issue #1, #2)。
 * Docker Engine は不要で、[DockerClient] を mockk で差し替える。
 */
class AgentContainerManagerTest {

    private val dockerClient: DockerClient = mockk()

    /** 起動中コンテナとして inspect が応答するようにする。 */
    private fun stubRunningContainer() = stubContainer(status = "running", running = true)

    /**
     * inspect の応答を組み立てる。
     *
     * `status` に null を渡すと Docker からステータス文字列が得られないケースを再現する。
     */
    private fun stubContainer(status: String?, running: Boolean, restartCount: Int = 0) {
        val state = mockk<InspectContainerResponse.ContainerState> {
            every { this@mockk.running } returns running
            every { this@mockk.status } returns status
        }
        val config = mockk<com.github.dockerjava.api.model.ContainerConfig> {
            every { image } returns "kintone-data-connector-agent:0.9.2"
        }
        val inspect = mockk<InspectContainerResponse> {
            every { this@mockk.state } returns state
            every { this@mockk.config } returns config
            every { created } returns "2026-09-29T00:00:00.000000000Z"
            every { id } returns "container-id"
            every { this@mockk.restartCount } returns restartCount
        }
        val cmd = mockk<InspectContainerCmd> { every { exec() } returns inspect }
        every { dockerClient.inspectContainerCmd(any()) } returns cmd
    }

    @Test
    fun `start - 起動済みコンテナの 304 を失敗扱いしない`() {
        stubRunningContainer()
        val startCmd = mockk<StartContainerCmd> {
            // Docker Engine API は起動済みコンテナの start に 304 を返す
            every { exec() } throws NotModifiedException("")
        }
        every { dockerClient.startContainerCmd(any<String>()) } returns startCmd

        val manager = AgentContainerManager(dockerClient)
        val info = assertDoesNotThrow { manager.start("Categories") }

        assertEquals(AgentContainerManager.State.RUNNING, info.state)
        assertEquals("kintone-agent-Categories", info.name)
    }

    @Test
    fun `start - 304 のメッセージは Status 304 で始まり文字列マッチでは判定できない`() {
        // 回帰防止: 以前は message.contains("already started") で判定していたためすり抜けていた
        val e = NotModifiedException("")
        assertEquals("Status 304: ", e.message)
    }

    @Test
    fun `stop - 停止済みコンテナの 304 を失敗扱いしない`() {
        stubRunningContainer()
        val stopCmd = mockk<StopContainerCmd> {
            every { withTimeout(any()) } returns this
            every { exec() } throws NotModifiedException("")
        }
        every { dockerClient.stopContainerCmd(any()) } returns stopCmd

        val manager = AgentContainerManager(dockerClient)
        assertDoesNotThrow { manager.stop("Categories") }
    }

    @Test
    fun `restart - restartContainerCmd を呼ぶ`() {
        stubRunningContainer()
        val restartCmd = mockk<RestartContainerCmd> {
            every { withTimeout(any()) } returns this
            every { exec() } returns null
        }
        every { dockerClient.restartContainerCmd(any()) } returns restartCmd

        val manager = AgentContainerManager(dockerClient)
        val info = manager.restart("Categories")

        verify(exactly = 1) { restartCmd.exec() }
        assertEquals(AgentContainerManager.State.RUNNING, info.state)
    }

    @Test
    fun `restart - コンテナが無ければ作成して起動する`() {
        // inspect は常に NotFound → ensureCreated 経由で createContainerCmd が呼ばれる
        val inspectCmd = mockk<InspectContainerCmd> {
            every { exec() } throws NotFoundException("no such container")
        }
        every { dockerClient.inspectContainerCmd(any()) } returns inspectCmd
        val restartCmd = mockk<RestartContainerCmd> {
            every { withTimeout(any()) } returns this
            every { exec() } throws NotFoundException("no such container")
        }
        every { dockerClient.restartContainerCmd(any()) } returns restartCmd

        // ビルダーは自分自身を返すのでチェーンを自己参照で stub する
        val createCmd = mockk<com.github.dockerjava.api.command.CreateContainerCmd>()
        every { createCmd.withName(any()) } returns createCmd
        every { createCmd.withHostConfig(any()) } returns createCmd
        every { createCmd.withLabels(any<Map<String, String>>()) } returns createCmd
        every { createCmd.exec() } returns mockk { every { id } returns "new-id" }
        every { dockerClient.createContainerCmd(any<String>()) } returns createCmd
        val startCmd = mockk<StartContainerCmd> { every { exec() } returns null }
        every { dockerClient.startContainerCmd(any<String>()) } returns startCmd

        val manager = AgentContainerManager(dockerClient)
        val info = manager.restart("Categories")

        verify(exactly = 1) { createCmd.exec() }
        verify(exactly = 1) { startCmd.exec() }
        assertEquals(AgentContainerManager.State.NOT_FOUND, info.state)
    }

    // --- 状態判定 (Issue #20) ---

    @Test
    fun `status - restarting は RESTARTING として返す`() {
        // 再起動ループ中も State.Running は true を返すため、真偽値では判定できない。
        stubContainer(status = "restarting", running = true, restartCount = 295)

        val info = AgentContainerManager(dockerClient).status("Product")

        assertEquals(AgentContainerManager.State.RESTARTING, info.state)
    }

    @Test
    fun `status - running は RUNNING として返す`() {
        stubContainer(status = "running", running = true)

        assertEquals(
            AgentContainerManager.State.RUNNING,
            AgentContainerManager(dockerClient).status("Categories").state,
        )
    }

    @Test
    fun `status - exited は STOPPED として返す`() {
        stubContainer(status = "exited", running = false)

        assertEquals(
            AgentContainerManager.State.STOPPED,
            AgentContainerManager(dockerClient).status("Product").state,
        )
    }

    @Test
    fun `status - paused や dead を RUNNING と誤判定しない`() {
        listOf("paused", "dead", "removing", "created").forEach { status ->
            stubContainer(status = status, running = false)
            assertEquals(
                AgentContainerManager.State.STOPPED,
                AgentContainerManager(dockerClient).status("Product").state,
                "status=$status",
            )
        }
    }

    @Test
    fun `status - ステータス文字列が無ければ running 真偽にフォールバックする`() {
        stubContainer(status = null, running = true)
        assertEquals(
            AgentContainerManager.State.RUNNING,
            AgentContainerManager(dockerClient).status("Categories").state,
        )

        stubContainer(status = null, running = false)
        assertEquals(
            AgentContainerManager.State.STOPPED,
            AgentContainerManager(dockerClient).status("Product").state,
        )
    }

    @Test
    fun `status - 再起動回数を返す`() {
        stubContainer(status = "restarting", running = true, restartCount = 295)

        assertEquals(295, AgentContainerManager(dockerClient).status("Product").restartCount)
    }

    // --- statusesBySyncName (Issue #20) ---

    @Test
    fun `statusesBySyncName - コンテナ名の接頭辞を剥がして連携名をキーにする`() {
        stubListContainers(
            listOf("kintone-agent-Product" to "restarting", "kintone-agent-Categories" to "running"),
        )

        val statuses = AgentContainerManager(dockerClient).statusesBySyncName()

        assertEquals(setOf("Product", "Categories"), statuses.keys)
        assertEquals(AgentContainerManager.State.RESTARTING, statuses["Product"]?.state)
        assertEquals(AgentContainerManager.State.RUNNING, statuses["Categories"]?.state)
    }

    @Test
    fun `statusesBySyncName - Docker 呼び出しが失敗したら空マップを返す`() {
        // 状態表示は補助情報。一覧本体が見られなくなるほうが困る。
        every { dockerClient.listContainersCmd() } throws RuntimeException("docker unavailable")

        assertEquals(emptyMap<String, AgentContainerManager.ContainerInfo>(), AgentContainerManager(dockerClient).statusesBySyncName())
    }

    /** コンテナ一覧 API の応答を組み立てる。 */
    private fun stubListContainers(nameAndState: List<Pair<String, String>>) {
        val containers = nameAndState.map { (name, state) ->
            mockk<com.github.dockerjava.api.model.Container> {
                every { names } returns arrayOf("/$name")
                every { this@mockk.state } returns state
                every { image } returns "kintone-data-connector-agent:0.9.2"
                every { created } returns 1_759_000_000L
                every { id } returns "id-$name"
            }
        }
        val cmd = mockk<com.github.dockerjava.api.command.ListContainersCmd> {
            every { withShowAll(any()) } returns this
            every { withLabelFilter(any<Map<String, String>>()) } returns this
            every { exec() } returns containers
        }
        every { dockerClient.listContainersCmd() } returns cmd
    }
}
