package com.cdata.kintone.adapter.agent

import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * 接続キーを拒否されて再起動を繰り返している Agent コンテナを停止する。
 *
 * 接続操作の経路 ([SyncConnectionService]) だけでは、**前のセッションから
 * 走り続けているコンテナに手が届かない**。Agent は認証失敗でも終了コード 0 で
 * 終わるため `restart: unless-stopped` が延々と再起動を続ける
 * （実環境では 288 回再起動していた）。console の起動時に一度棚卸しする。
 *
 * 判定は「**直近のログ**に認証失敗が出ているか」で行う。全ログを見ると、
 * 過去に失敗して既に復旧した連携まで止めてしまう (Issue #4 と同じ失敗)。
 *
 * 関連: [Issue #19](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/19)
 */
class AgentAuthFailureSweeper(
    private val containerManager: AgentContainerManager,
    private val connectionStatusStore: AgentConnectionStatusStore = AgentConnectionStatusStore(),
    private val logWindowSeconds: Int = DEFAULT_LOG_WINDOW_SEC,
) {

    /** 認証失敗しているコンテナを停止し、停止した連携名を返す。 */
    fun sweep(): List<String> {
        val containers = runCatching { containerManager.listAll() }
            .onFailure { log.warn(it) { "Agent コンテナの一覧取得に失敗したため棚卸しを行いません" } }
            .getOrDefault(emptyList())

        // 1 つの失敗で棚卸し全体を止めない。Docker の状態に依存する処理で
        // console の起動を落とさないため。
        return containers.mapNotNull { info ->
            val syncName = info.name.removePrefix(AgentContainerManager.CONTAINER_PREFIX)
            runCatching { stopIfAuthFailed(syncName) }
                .onFailure { log.warn(it) { "Agent コンテナの棚卸しに失敗: $syncName" } }
                .getOrNull()
        }
    }

    /** 認証失敗していれば停止して連携名を返す。そうでなければ null。 */
    private fun stopIfAuthFailed(syncName: String): String? {
        val logs = containerManager.fetchLogs(syncName, tail = LOG_TAIL, sinceSeconds = logWindowSeconds)
        if (!AgentLogMarkers.indicatesAuthFailure(logs)) return null

        log.warn {
            "接続キーが kintone に拒否されているため Agent コンテナを停止します: $syncName " +
                "(kintone 側で接続キーを再発行し、入力し直してください)"
        }
        containerManager.stop(syncName)
        return syncName
    }

    companion object {
        /**
         * ログを遡る秒数。
         *
         * Agent は失敗すると即終了し、Docker が約 1 分後に再起動する。
         * ループ中なら 1 分の窓に必ず認証エラーが現れるが、
         * 取りこぼしを避けるため 3 分ぶん見る。
         */
        const val DEFAULT_LOG_WINDOW_SEC = 180

        /** 判定に使うログ行数。1 回の起動で数行しか出ないため小さくてよい。 */
        const val LOG_TAIL = 50
    }
}
