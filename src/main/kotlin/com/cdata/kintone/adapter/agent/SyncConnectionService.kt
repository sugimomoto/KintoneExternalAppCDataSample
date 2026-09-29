package com.cdata.kintone.adapter.agent

import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private val log = KotlinLogging.logger {}

/**
 * 「kintone とつなぐ」1 ボタン処理を集約するサービス。
 *
 * 1. agent.json を保存
 * 2. Adapter を起動 (未起動なら)
 * 3. Agent → Adapter の到達性を検証 (Issue #7)
 * 4. Agent コンテナを起動 / 再起動 (Issue #1, #2)
 * 5. 「今回の接続操作以降の」Agent ログから接続成立を確認 (Issue #4)
 */
class SyncConnectionService(
    private val configSource: ConfigSource,
    private val runner: MultiAdapterRunner,
    private val agentConfigManager: AgentConfigManager,
    private val agentContainerManager: AgentContainerManager?,
    private val reachabilityChecker: AdapterReachabilityChecker = AdapterReachabilityChecker(),
) {

    sealed class Result {
        data object Success : Result()
        data class Pending(val reason: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun connect(
        syncName: String,
        token: String,
        kintoneDomain: String? = null,
        waitTimeoutSec: Long = 10,
    ): Result {
        val operationStartedAtMs = System.currentTimeMillis()

        val set = runCatching { configSource.loadTableSet(syncName) }.getOrNull()
            ?: return Result.Failure("連携設定が見つかりません: $syncName")

        // 1. Adapter を起動して接続先アドレスを確定させる
        val adapterAddr = resolveAdapterAddr(syncName, set)
            .getOrElse { return Result.Failure(it.message ?: "Adapter を起動できません") }

        // 2. agent.json 保存
        agentConfigManager.save(
            syncName,
            AgentConfig(
                token = token,
                adapterAddr = adapterAddr,
                adapterPlaintext = set.server.plaintext,
                privateKeyPath = AGENT_PRIVATE_KEY_PATH,
            ),
        )

        // 3. Agent → Adapter の到達性を検証 (Issue #7)
        //    ここで弾いておかないと、kintone には接続できるのに実行時だけ
        //    「Adapterが利用できません」になる状態で成功表示してしまう。
        val reachability = reachabilityChecker.check(adapterAddr)
        val containerMgr = agentContainerManager

        return when {
            reachability is AdapterReachabilityChecker.Result.Unreachable ->
                Result.Failure(reachability.reason)

            containerMgr == null ->
                Result.Pending("Docker socket が利用不可のため、Agent コンテナを手動起動してください")

            else -> startAgentAndAwait(syncName, containerMgr, waitTimeoutSec, operationStartedAtMs)
        }
    }

    /**
     * Adapter を起動し (未起動なら)、Agent から見た接続先 `host:port` を確定する。
     */
    private fun resolveAdapterAddr(
        syncName: String,
        set: TableConfigSet,
    ): kotlin.Result<String> {
        val startFailure = runCatching { runner.startOne(syncName) }.exceptionOrNull()
        // IllegalStateException = 既に起動中。それ以外は本当の起動失敗。
        if (startFailure != null && startFailure !is IllegalStateException) {
            return kotlin.Result.failure(IllegalStateException("Adapter 起動に失敗: ${startFailure.message}"))
        }

        // port: 0 (auto) の場合は起動後の実ポートを使う
        val addr = set.server.port.takeIf { it > 0 }?.let { "$DOCKER_HOST_ALIAS:$it" }
            ?: runner.get(syncName)?.let { "$DOCKER_HOST_ALIAS:${it.actualPort}" }

        return addr?.let { kotlin.Result.success(it) }
            ?: kotlin.Result.failure(IllegalStateException("Adapter ポートを取得できません"))
    }

    /** Agent コンテナを起動 / 再起動し、kintone 接続の成立を待つ。 */
    private fun startAgentAndAwait(
        syncName: String,
        containerMgr: AgentContainerManager,
        waitTimeoutSec: Long,
        operationStartedAtMs: Long,
    ): Result {
        val startFailure = runCatching { ensureRunningWithLatestConfig(syncName, containerMgr) }.exceptionOrNull()
        if (startFailure != null) {
            return Result.Failure("Agent コンテナ起動に失敗: ${startFailure.message}")
        }

        return when (waitForKintoneConnection(syncName, containerMgr, waitTimeoutSec, operationStartedAtMs)) {
            ConnectionOutcome.CONNECTED -> Result.Success
            ConnectionOutcome.AUTH_FAILED -> {
                // 接続キーが無効な状態では再試行しても直らない。restart: unless-stopped の
                // まま放置すると 1 分おきに永久に再試行し続ける (Issue #19)。
                runCatching { containerMgr.stop(syncName) }
                    .onFailure { log.warn(it) { "認証失敗後の Agent コンテナ停止に失敗: $syncName" } }
                Result.Failure(
                    "接続キーが kintone に拒否されました。kintone 側で接続キーを再発行し、入力し直してください。" +
                        "再試行を止めるため Agent コンテナは停止しました。",
                )
            }
            ConnectionOutcome.TIMEOUT -> Result.Pending(
                "Agent は起動しましたが、kintone 接続確認が $waitTimeoutSec 秒以内にできませんでした。" +
                    "ログを確認してください。",
            )
        }
    }

    /**
     * Agent コンテナを「最新の agent.json を読み込んだ状態」で running にする。
     *
     * Agent は agent.json をプロセス起動時にしか読まないため、既に running の場合は
     * start (no-op) ではなく restart が必要 (Issue #2)。
     */
    private fun ensureRunningWithLatestConfig(
        syncName: String,
        containerMgr: AgentContainerManager,
    ): AgentContainerManager.ContainerInfo =
        when (containerMgr.status(syncName).state) {
            AgentContainerManager.State.RUNNING -> {
                log.info { "Agent コンテナが起動中のため再起動して agent.json を再読み込みします: $syncName" }
                containerMgr.restart(syncName)
            }
            AgentContainerManager.State.STOPPED -> containerMgr.start(syncName)
            AgentContainerManager.State.NOT_FOUND -> {
                containerMgr.ensureCreated(syncName)
                containerMgr.start(syncName)
            }
        }

    private enum class ConnectionOutcome { CONNECTED, AUTH_FAILED, TIMEOUT }

    /**
     * Agent ログを監視して kintone 接続の成立を確認する。
     *
     * 判定対象は **今回の接続操作以降に出力されたログのみ** に限定する。
     * 全ログを対象にすると、過去の接続成功ログを拾って誤って成功と判定してしまう (Issue #4)。
     */
    private fun waitForKintoneConnection(
        syncName: String,
        containerMgr: AgentContainerManager,
        timeoutSec: Long,
        operationStartedAtMs: Long,
    ): ConnectionOutcome = runBlocking {
        val deadline = operationStartedAtMs + timeoutSec * 1000
        while (System.currentTimeMillis() < deadline) {
            val logs = runCatching {
                containerMgr.fetchLogs(syncName, tail = LOG_TAIL, sinceSeconds = sinceSeconds(operationStartedAtMs))
            }.getOrNull() ?: ""

            if (AgentLogMarkers.indicatesConnected(logs)) return@runBlocking ConnectionOutcome.CONNECTED
            if (AgentLogMarkers.indicatesAuthFailure(logs)) {
                log.warn { "Agent の認証に失敗しました: $syncName" }
                return@runBlocking ConnectionOutcome.AUTH_FAILED
            }
            delay(POLL_INTERVAL_MS)
        }
        ConnectionOutcome.TIMEOUT
    }

    /**
     * 接続操作開始からの経過秒数。Docker の `since` は秒精度のため、
     * 取りこぼしを防ぐマージンを加える。
     */
    private fun sinceSeconds(operationStartedAtMs: Long): Int =
        ((System.currentTimeMillis() - operationStartedAtMs) / 1000).toInt() + SINCE_MARGIN_SEC

    companion object {
        /** Agent コンテナから見たホスト側のアドレス (docker-compose の extra_hosts と対応)。 */
        private const val DOCKER_HOST_ALIAS = "host.docker.internal"
        private const val AGENT_PRIVATE_KEY_PATH = "/opt/agent/private-key.pem"
        private const val LOG_TAIL = 200
        private const val POLL_INTERVAL_MS = 500L
        private const val SINCE_MARGIN_SEC = 2
    }
}
