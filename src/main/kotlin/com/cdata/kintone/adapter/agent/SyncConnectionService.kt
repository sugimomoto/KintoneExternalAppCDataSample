package com.cdata.kintone.adapter.agent

import com.cdata.kintone.adapter.config.ConfigSource
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
 * 3. Agent コンテナを ensureCreated + start
 * 4. Agent ログから "successfully connected" を検出するまで最大 N 秒待機
 */
class SyncConnectionService(
    private val configSource: ConfigSource,
    private val runner: MultiAdapterRunner,
    private val agentConfigManager: AgentConfigManager,
    private val agentContainerManager: AgentContainerManager?,
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
        // テーブル設定の存在確認 + port 取得
        val set = runCatching { configSource.loadTableSet(syncName) }.getOrNull()
            ?: return Result.Failure("連携設定が見つかりません: $syncName")

        // 1. agent.json 保存 (port をベースに adapter_addr を組み立て)
        val port = set.server.port
        val adapterAddr = if (port == 0) {
            // 0 = auto-allocate の場合、Adapter 起動後に runner.get(syncName) で actualPort を取得する
            null
        } else {
            "host.docker.internal:$port"
        }

        // 2. Adapter 起動 (まだなら)
        runCatching { runner.startOne(syncName) }
            .onFailure { e ->
                if (e !is IllegalStateException) {
                    return Result.Failure("Adapter 起動に失敗: ${e.message}")
                }
                // IllegalStateException = 既に起動中、続行
            }

        // 動的ポートの場合は起動後に取得
        val effectiveAdapterAddr = adapterAddr
            ?: runner.get(syncName)?.let { "host.docker.internal:${it.actualPort}" }
            ?: return Result.Failure("Adapter ポートを取得できません")

        agentConfigManager.save(
            syncName,
            AgentConfig(
                token = token,
                adapterAddr = effectiveAdapterAddr,
                adapterPlaintext = set.server.plaintext,
                privateKeyPath = "/opt/agent/private-key.pem",
            ),
        )

        // 3. Agent コンテナ作成 + 起動
        val containerMgr = agentContainerManager
            ?: return Result.Pending("Docker socket が利用不可のため、Agent コンテナを手動起動してください")
        runCatching {
            containerMgr.ensureCreated(syncName)
            containerMgr.start(syncName)
        }.onFailure { return Result.Failure("Agent コンテナ起動に失敗: ${it.message}") }

        // 4. kintone 接続を待機
        val connected = waitForKintoneConnection(syncName, containerMgr, waitTimeoutSec)
        return if (connected) Result.Success
        else Result.Pending("Agent は起動しましたが、kintone 接続確認が ${waitTimeoutSec} 秒以内にできませんでした。ログを確認してください。")
    }

    private fun waitForKintoneConnection(
        syncName: String,
        containerMgr: AgentContainerManager,
        timeoutSec: Long,
    ): Boolean = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000
        while (System.currentTimeMillis() < deadline) {
            val logs = runCatching { containerMgr.fetchLogs(syncName, tail = 50) }.getOrNull() ?: ""
            if (logs.contains("successfully connected to kintone")) {
                return@runBlocking true
            }
            delay(500)
        }
        false
    }
}
