package com.cdata.kintone.adapter.agent

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.RestartPolicy
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists

private val log = KotlinLogging.logger {}

/**
 * Sync 用 Agent コンテナを Docker Engine API 経由で制御する。
 * Phase 2-C で docker-compose ファイルの手動編集を廃止し、すべてここを通す。
 *
 * 設計詳細: `.steering/20260525-sync-and-docker-control/design.md §2`
 */
class AgentContainerManager(
    private val dockerClient: DockerClient,
    private val agentImage: String = "kintone-data-connector-agent:0.9.2",
    /**
     * ホスト側の agent root パス。Web UI 自身がコンテナで動く場合は、コンテナ内では
     * `/app/agent` だが、bind に使う際にはホスト上のパスである必要がある。
     * 環境変数 `HOST_AGENT_ROOT` で外から指定する想定 (Docker 配布時)。
     */
    private val hostAgentRoot: String = System.getenv("HOST_AGENT_ROOT")
        ?: Path.of("./agent").toAbsolutePath().toString(),
) {

    enum class State { RUNNING, STOPPED, NOT_FOUND }

    data class ContainerInfo(
        val name: String,
        val state: State,
        val image: String,
        val createdAt: Instant?,
        val containerId: String? = null,
    )

    fun containerName(syncName: String): String = "$CONTAINER_PREFIX$syncName"

    fun status(syncName: String): ContainerInfo {
        val containerName = containerName(syncName)
        return try {
            val inspect = dockerClient.inspectContainerCmd(containerName).exec()
            val state = if (inspect.state.running == true) State.RUNNING else State.STOPPED
            ContainerInfo(
                name = containerName,
                state = state,
                image = inspect.config.image ?: agentImage,
                createdAt = inspect.created?.let { runCatching { Instant.parse(it) }.getOrNull() },
                containerId = inspect.id,
            )
        } catch (e: NotFoundException) {
            ContainerInfo(name = containerName, state = State.NOT_FOUND, image = agentImage, createdAt = null)
        }
    }

    /**
     * Agent コンテナを作成 (まだ無ければ)。既存ならそのまま返す。
     */
    fun ensureCreated(syncName: String): ContainerInfo {
        val containerName = containerName(syncName)
        val current = status(syncName)
        if (current.state != State.NOT_FOUND) return current

        val agentJsonHost = "$hostAgentRoot/tables/$syncName/agent.json"
        val privateKeyHost = "$hostAgentRoot/private-key.pem"

        // ホスト上のパスが見えない (Web UI 自身がコンテナ内、bind 元が無い) 場合は warn
        if (!Path.of(agentJsonHost).exists()) {
            log.warn { "agent.json が見つかりません: $agentJsonHost — bind マウントで存在しない可能性" }
        }

        val resp: CreateContainerResponse = dockerClient.createContainerCmd(agentImage)
            .withName(containerName)
            .withHostConfig(
                HostConfig.newHostConfig()
                    .withBinds(
                        Bind(agentJsonHost, Volume("/opt/agent/agent.json"), AccessMode.ro),
                        Bind(privateKeyHost, Volume("/opt/agent/private-key.pem"), AccessMode.ro),
                    )
                    .withExtraHosts("host.docker.internal:host-gateway")
                    .withRestartPolicy(RestartPolicy.unlessStoppedRestart()),
            )
            .withLabels(
                mapOf(
                    LABEL_SYNC to syncName,
                    LABEL_MANAGED to "true",
                ),
            )
            .exec()
        log.info { "Created container: $containerName (id=${resp.id})" }
        return status(syncName)
    }

    fun start(syncName: String): ContainerInfo {
        ensureCreated(syncName)
        val containerName = containerName(syncName)
        try {
            dockerClient.startContainerCmd(containerName).exec()
            log.info { "Started container: $containerName" }
        } catch (e: NotFoundException) {
            log.warn { "Container not found at start: $containerName" }
        } catch (e: NotModifiedException) {
            // Docker Engine API は起動済みコンテナの start に 304 を返す。これは正常系。
            log.debug(e) { "Container already started: $containerName" }
        }
        return status(syncName)
    }

    /**
     * コンテナを再起動する。`agent.json` はプロセス起動時にしか読まれないため、
     * 設定を変更したあとに確実へ反映させたい場合はこちらを使う。
     *
     * コンテナが存在しない場合は作成して起動する。
     */
    fun restart(syncName: String, timeoutSec: Int = 10): ContainerInfo {
        val containerName = containerName(syncName)
        return try {
            dockerClient.restartContainerCmd(containerName).withTimeout(timeoutSec).exec()
            log.info { "Restarted container: $containerName" }
            status(syncName)
        } catch (e: NotFoundException) {
            log.warn(e) { "Container not found at restart, creating: $containerName" }
            // start() が内部で ensureCreated() を呼ぶ
            start(syncName)
        }
    }

    fun stop(syncName: String, timeoutSec: Int = 10): ContainerInfo {
        val containerName = containerName(syncName)
        try {
            dockerClient.stopContainerCmd(containerName).withTimeout(timeoutSec).exec()
            log.info { "Stopped container: $containerName" }
        } catch (e: NotFoundException) {
            return status(syncName)
        } catch (e: NotModifiedException) {
            // 停止済みコンテナの stop も 304。これは正常系。
            log.debug(e) { "Container already stopped: $containerName" }
        }
        return status(syncName)
    }

    fun remove(syncName: String, force: Boolean = false): Boolean {
        val containerName = containerName(syncName)
        return try {
            dockerClient.removeContainerCmd(containerName).withForce(force).exec()
            log.info { "Removed container: $containerName" }
            true
        } catch (e: NotFoundException) {
            false
        } catch (e: ConflictException) {
            // 動作中で force=false の場合
            log.warn { "Container is running, use force=true to remove: $containerName" }
            false
        }
    }

    fun listAll(): List<ContainerInfo> {
        return dockerClient.listContainersCmd()
            .withShowAll(true)
            .withLabelFilter(mapOf(LABEL_MANAGED to "true"))
            .exec()
            .mapNotNull { c ->
                val name = c.names.firstOrNull()?.removePrefix("/") ?: return@mapNotNull null
                val state = when (c.state?.lowercase()) {
                    "running" -> State.RUNNING
                    null -> State.NOT_FOUND
                    else -> State.STOPPED
                }
                ContainerInfo(
                    name = name,
                    state = state,
                    image = c.image ?: agentImage,
                    createdAt = c.created?.let { Instant.ofEpochSecond(it) },
                    containerId = c.id,
                )
            }
    }

    /**
     * コンテナのログを取得 (フォローしない、最新 tail 行のみ)。
     * Phase 2-C M5 ライブログで使う。
     */
    fun fetchLogs(syncName: String, tail: Int = 100, sinceSeconds: Int? = null): String {
        val containerName = containerName(syncName)
        val sb = StringBuilder()
        try {
            val cmd = dockerClient.logContainerCmd(containerName)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(true)
                .withTail(tail)
            if (sinceSeconds != null) cmd.withSince((System.currentTimeMillis() / 1000 - sinceSeconds).toInt())
            cmd.exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    sb.append(String(frame.payload, Charsets.UTF_8))
                }
            }).awaitCompletion()
        } catch (e: NotFoundException) {
            return "(container not found: $containerName)"
        }
        return sb.toString()
    }

    fun close() {
        runCatching { dockerClient.close() }
    }

    companion object {
        const val CONTAINER_PREFIX = "kintone-agent-"
        const val LABEL_SYNC = "com.cdata.adapter.sync"
        const val LABEL_MANAGED = "com.cdata.adapter.managed"

        /**
         * 標準的な Docker socket (`/var/run/docker.sock`) に接続する。
         * 接続できない環境では `available = false` の AgentControlMode を別途使う。
         */
        fun createDockerClient(socketPath: Path = Path.of("/var/run/docker.sock")): DockerClient {
            val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix://${socketPath.toAbsolutePath()}")
                .build()
            val httpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(URI("unix://${socketPath.toAbsolutePath()}"))
                .maxConnections(10)
                .build()
            return DockerClientImpl.getInstance(config, httpClient)
        }
    }
}

/**
 * Docker socket が利用可能かどうかを判定する。
 * Web UI 起動時に判定し、利用不可なら AgentContainerManager 機能を無効化する。
 */
class AgentControlMode(socketPath: Path = Path.of("/var/run/docker.sock")) {
    val available: Boolean = Files.exists(socketPath)
}
