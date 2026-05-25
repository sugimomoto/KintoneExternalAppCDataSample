package com.cdata.kintone.adapter.web

import com.cdata.kintone.adapter.agent.AgentConfigManager
import com.cdata.kintone.adapter.agent.AgentContainerManager
import com.cdata.kintone.adapter.agent.AgentControlMode
import com.cdata.kintone.adapter.agent.PublicKeyManager
import com.cdata.kintone.adapter.agent.SyncConnectionService
import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.ConfigSourceFactory
import com.cdata.kintone.adapter.jdbc.JdbcConnectionPropertyInspector
import com.cdata.kintone.adapter.jdbc.JdbcDriverManager
import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * Web UI が依存するサービス群を 1 つに束ねるコンテナ。
 *
 * Ktor の起動時に 1 つ生成して各ルートに注入する。
 */
class AppContext(
    val configSource: ConfigSource,
    val configSourceMode: ConfigSourceFactory.Mode,
    val driverManager: JdbcDriverManager,
    val runner: MultiAdapterRunner,
    val configDir: Path,
    val libDir: Path,
    val connectionPropertyInspector: JdbcConnectionPropertyInspector,
    val agentConfigManager: AgentConfigManager,
    val agentControlMode: AgentControlMode,
    /** Docker socket が利用可能な場合のみ非 null。 */
    val agentContainerManager: AgentContainerManager?,
    val publicKeyManager: PublicKeyManager,
    val syncConnectionService: SyncConnectionService,
) : AutoCloseable {

    override fun close() {
        runner.close()
        agentContainerManager?.close()
    }

    companion object {
        fun create(
            configDir: Path = Path.of("./config"),
            libDir: Path = Path.of("./lib"),
            agentRoot: Path = Path.of("./agent"),
        ): AppContext {
            val mode = ConfigSourceFactory.detectMode(configDir)
            val source = ConfigSourceFactory.create(mode, configDir)
            val runner = MultiAdapterRunner(
                configSource = source,
                activeFile = ActiveAdaptersFile(ActiveAdaptersFile.DEFAULT_PATH),
            )
            val controlMode = AgentControlMode()
            val containerMgr = if (controlMode.available) {
                runCatching {
                    AgentContainerManager(AgentContainerManager.createDockerClient())
                }.onFailure {
                    log.warn(it) { "Docker socket は存在するが DockerClient 初期化に失敗。コンテナ制御は無効化されます。" }
                }.getOrNull()
            } else {
                log.info { "Docker socket が見つかりません (/var/run/docker.sock)。Agent コンテナ制御は無効化されます。" }
                null
            }
            val agentConfigMgr = AgentConfigManager(agentRoot)
            return AppContext(
                configSource = source,
                configSourceMode = mode,
                driverManager = JdbcDriverManager(libDir),
                runner = runner,
                configDir = configDir,
                libDir = libDir,
                connectionPropertyInspector = JdbcConnectionPropertyInspector(libDir),
                agentConfigManager = agentConfigMgr,
                agentControlMode = controlMode,
                agentContainerManager = containerMgr,
                publicKeyManager = PublicKeyManager(agentRoot.resolve("public-key.pem")),
                syncConnectionService = SyncConnectionService(source, runner, agentConfigMgr, containerMgr),
            )
        }
    }
}
