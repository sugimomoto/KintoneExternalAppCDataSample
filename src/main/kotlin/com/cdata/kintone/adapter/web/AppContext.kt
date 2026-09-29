package com.cdata.kintone.adapter.web

import com.cdata.kintone.adapter.agent.AdapterReachabilityChecker
import com.cdata.kintone.adapter.agent.AgentAuthFailureSweeper
import com.cdata.kintone.adapter.agent.AgentConfigManager
import com.cdata.kintone.adapter.agent.AgentConnectionStatusStore
import com.cdata.kintone.adapter.agent.AgentContainerManager
import com.cdata.kintone.adapter.agent.AgentControlMode
import com.cdata.kintone.adapter.agent.KeyPairGeneratorService
import com.cdata.kintone.adapter.agent.PublicKeyManager
import com.cdata.kintone.adapter.agent.SyncConnectionService
import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.jdbc.JdbcConnectionPropertyInspector
import com.cdata.kintone.adapter.jdbc.JdbcDriverManager
import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import com.cdata.kintone.adapter.runtime.PortMigrator
import com.cdata.kintone.adapter.runtime.SyncPortAllocator
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
    val driverManager: JdbcDriverManager,
    val runner: MultiAdapterRunner,
    val configDir: Path,
    val libDir: Path,
    val connectionPropertyInspector: JdbcConnectionPropertyInspector,
    val agentConfigManager: AgentConfigManager,
    val agentControlMode: AgentControlMode,
    /** Docker socket が利用可能な場合のみ非 null。 */
    val agentContainerManager: AgentContainerManager?,
    /** Agent の接続失敗の記録 (Issue #21)。 */
    val agentConnectionStatusStore: AgentConnectionStatusStore,
    val publicKeyManager: PublicKeyManager,
    val keyPairGeneratorService: KeyPairGeneratorService,
    val syncConnectionService: SyncConnectionService,
    val syncPortAllocator: SyncPortAllocator,
    /** 起動時に自動復元した Adapter の Sync 名 (Issue #6)。 */
    val startedAdapters: List<String> = emptyList(),
    /** 起動時に port: 0 から移行した Sync (Issue #3)。 */
    val migratedPorts: List<PortMigrator.Migration> = emptyList(),
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
            /** 設定 SQLite のパス。未指定なら `<configDir>/config.db`。 */
            sqlitePath: Path? = null,
            startup: StartupOptions = StartupOptions(),
        ): AppContext {
            val source = ConfigStore.open(configDir, sqlitePath)
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
            val connectionStatusStore = AgentConnectionStatusStore()
            val portAllocator = SyncPortAllocator(configSource = source, runner = runner)

            val migrated =
                if (startup.migratePorts) {
                    migratePorts(source, portAllocator, agentConfigMgr, containerMgr)
                } else {
                    emptyList()
                }
            val started = if (startup.startAdapters) startAdapters(runner) else skipAutoStart()

            // Adapter を先に起動してから棚卸しする。順序を逆にすると、本来つながるはずの
            // Agent を「到達できない」状態で評価してしまう。
            if (startup.stopAuthFailedAgents && containerMgr != null) {
                sweepAuthFailedAgents(containerMgr, connectionStatusStore)
            }

            return AppContext(
                configSource = source,
                driverManager = JdbcDriverManager(libDir),
                runner = runner,
                configDir = configDir,
                libDir = libDir,
                connectionPropertyInspector = JdbcConnectionPropertyInspector(libDir),
                agentConfigManager = agentConfigMgr,
                agentControlMode = controlMode,
                agentContainerManager = containerMgr,
                agentConnectionStatusStore = connectionStatusStore,
                publicKeyManager = PublicKeyManager(agentRoot.resolve("public-key.pem")),
                keyPairGeneratorService = KeyPairGeneratorService(
                    privateKeyPath = agentRoot.resolve("private-key.pem"),
                    publicKeyPath = agentRoot.resolve("public-key.pem"),
                ),
                syncConnectionService = SyncConnectionService(
                    configSource = source,
                    runner = runner,
                    agentConfigManager = agentConfigMgr,
                    agentContainerManager = containerMgr,
                    reachabilityChecker = AdapterReachabilityChecker(),
                    connectionStatusStore = connectionStatusStore,
                ),
                syncPortAllocator = portAllocator,
                startedAdapters = started,
                migratedPorts = migrated,
            )
        }

        /**
         * 起動時に行う自動処理の切り替え。
         *
         * いずれも既定は有効で、環境変数で無効化できる。
         * 個別の引数にすると `create` の引数が増え続けるためまとめている。
         */
        data class StartupOptions(
            /** `port: 0` の既存 Sync を publish 範囲へ移行する (Issue #3)。 */
            val migratePorts: Boolean = envFlag("AUTO_MIGRATE_PORTS", default = true),
            /** 設定済み Sync の Adapter をすべて起動する (Issue #6)。 */
            val startAdapters: Boolean = envFlag("AUTO_START_ADAPTERS", default = true),
            /** 接続キーを拒否されて再起動を繰り返している Agent を停止する (Issue #19)。 */
            val stopAuthFailedAgents: Boolean = envFlag("AUTO_STOP_AUTH_FAILED_AGENTS", default = true),
        )

        /**
         * 接続キーを拒否されて再起動を繰り返している Agent コンテナを停止する (Issue #19)。
         *
         * 接続操作の経路だけでは、前のセッションから走り続けているコンテナに手が届かない。
         */
        private fun sweepAuthFailedAgents(
            containerMgr: AgentContainerManager,
            statusStore: AgentConnectionStatusStore,
        ) {
            val stopped = AgentAuthFailureSweeper(containerMgr, statusStore).sweep()
            if (stopped.isEmpty()) return
            log.warn {
                "接続キーが拒否されている Agent コンテナを停止しました (${stopped.size} 件): " +
                    stopped.joinToString(", ")
            }
        }

        /**
         * `port: 0` の Sync を publish 範囲の固定ポートへ移行する (Issue #3)。
         * Adapter が publish 範囲外の ephemeral port を掴まないよう、起動前に確定させる。
         */
        private fun migratePorts(
            source: ConfigSource,
            portAllocator: SyncPortAllocator,
            agentConfigMgr: AgentConfigManager,
            containerMgr: AgentContainerManager?,
        ): List<PortMigrator.Migration> = PortMigrator(
            configSource = source,
            allocator = portAllocator,
            onPortChanged = { syncName, newPort ->
                // agent.json が既にある Sync は adapter_addr も追従させる
                agentConfigMgr.load(syncName)?.let { current ->
                    agentConfigMgr.save(syncName, current.copy(adapterAddr = "host.docker.internal:$newPort"))
                }
                // Agent は agent.json を起動時にしか読まないため、稼働中なら再起動しないと
                // 旧ポートを向いたままになる (Issue #2 と同じ理由)
                restartRunningAgent(containerMgr, syncName)
            },
        ).migrate()

        /** 稼働中の Agent コンテナだけを再起動する。失敗してもログに残して続行する。 */
        private fun restartRunningAgent(containerMgr: AgentContainerManager?, syncName: String) {
            if (containerMgr == null) return
            runCatching {
                if (containerMgr.status(syncName).state == AgentContainerManager.State.RUNNING) {
                    containerMgr.restart(syncName)
                    log.info { "ポート変更に伴い Agent コンテナを再起動しました: $syncName" }
                }
            }.onFailure {
                log.warn(it) { "Agent コンテナの再起動に失敗しました: $syncName (${it.message})" }
            }
        }

        /**
         * 設定済み Sync の Adapter をすべて起動する (Issue #6)。
         * Agent コンテナは `restart: unless-stopped` で自動復帰するため、
         * Adapter 側も復元しないと「Agent だけ居る」状態になる。
         */
        private fun startAdapters(runner: MultiAdapterRunner): List<String> =
            runner.startAll().also { log.info { "起動時に ${it.size} 件の Adapter を復元しました" } }

        private fun skipAutoStart(): List<String> {
            log.info { "AUTO_START_ADAPTERS=false のため Adapter の自動起動をスキップしました" }
            return emptyList()
        }

        /** 環境変数を true/false として読む。未設定なら [default]。 */
        private fun envFlag(name: String, default: Boolean): Boolean =
            System.getenv(name)?.trim()?.lowercase()?.let { it !in setOf("false", "0", "no") } ?: default
    }
}
