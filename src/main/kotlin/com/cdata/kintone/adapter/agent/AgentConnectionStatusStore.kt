package com.cdata.kintone.adapter.agent

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val log = KotlinLogging.logger {}

/**
 * Agent の接続失敗の記録を JSON ファイルで保持する。
 *
 * console を再起動しても残す必要がある。認証失敗の棚卸し
 * ([AgentAuthFailureSweeper]) は「直近のログ」で判定するため、いったん停止した
 * コンテナは次の起動時には検知されない。記録が無いと「なぜ止まっているのか」が
 * 永久に分からなくなる。
 *
 * 実行時の状態なので `config.db` ではなく `run/` 配下に置く
 * （`runtime/ActiveAdaptersFile` と同じ流儀）。
 *
 * 関連: [Issue #21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21)
 */
class AgentConnectionStatusStore(private val path: Path = DEFAULT_PATH) {

    /** 記録する。同じ連携の記録は上書きされる。 */
    fun record(status: AgentConnectionStatus) {
        write(all() + (status.syncName to status))
    }

    /** 指定連携の記録を消す。接続が成立したときに呼ぶ。 */
    fun clear(syncName: String) {
        val current = all()
        if (syncName !in current) return
        write(current - syncName)
    }

    /** 指定連携の記録。無ければ null。 */
    fun get(syncName: String): AgentConnectionStatus? = all()[syncName]

    /** 全記録。連携名がキー。読み取りに失敗した場合は空マップ。 */
    fun all(): Map<String, AgentConnectionStatus> {
        if (!Files.exists(path)) return emptyMap()
        return runCatching {
            JSON.decodeFromString(ListSerializer(AgentConnectionStatus.serializer()), Files.readString(path))
                .associateBy { it.syncName }
        }.onFailure {
            // 補助情報のため、壊れたファイルで画面を落とさない。
            log.warn(it) { "接続失敗の記録を読めませんでした: $path" }
        }.getOrDefault(emptyMap())
    }

    private fun write(statuses: Map<String, AgentConnectionStatus>) {
        path.parent?.let { Files.createDirectories(it) }
        val text = JSON.encodeToString(
            ListSerializer(AgentConnectionStatus.serializer()),
            statuses.values.sortedBy { it.syncName },
        )
        Files.writeString(
            path,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    companion object {
        val DEFAULT_PATH: Path = Path.of("./run/agent-connection-status.json")

        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
}
