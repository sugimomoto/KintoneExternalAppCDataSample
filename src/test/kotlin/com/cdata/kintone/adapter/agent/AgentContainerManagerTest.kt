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
    private fun stubRunningContainer() {
        val state = mockk<InspectContainerResponse.ContainerState> {
            every { running } returns true
        }
        val config = mockk<com.github.dockerjava.api.model.ContainerConfig> {
            every { image } returns "kintone-data-connector-agent:0.9.2"
        }
        val inspect = mockk<InspectContainerResponse> {
            every { this@mockk.state } returns state
            every { this@mockk.config } returns config
            every { created } returns "2026-09-29T00:00:00.000000000Z"
            every { id } returns "container-id"
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
}
