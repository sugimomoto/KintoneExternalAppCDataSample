package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.ConfigSource
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Sync (テーブル) 単位の gRPC ポートを、Docker が publish している範囲から採番する。
 *
 * `port: 0` (OS 任意ポート) にしてしまうと publish 範囲外の ephemeral port を listen するため、
 * Agent コンテナから `host.docker.internal:<port>` に到達できない (Issue #3)。
 * Sync 作成時・移行時はこのクラスを通してポートを決めること。
 *
 * 採番時は以下の両方を「使用中」として避ける。
 * - `ConfigSource` に保存済みの各 Sync の `server.port`
 * - `MultiAdapterRunner` が実際に listen 中のポート (設定と実態がズレている場合の保険)
 *
 * さらに [PortAllocator.findAvailable] が実際に bind を試すため、
 * 他プロセスが掴んでいるポートも回避される。
 */
class SyncPortAllocator(
    private val configSource: ConfigSource,
    private val runner: MultiAdapterRunner? = null,
    private val rangeStart: Int = PortAllocator.DEFAULT_START,
    private val rangeEnd: Int = PortAllocator.DEFAULT_END,
) {

    /**
     * 使用中のポート一覧。[excludeSync] に指定した Sync 自身のポートは除外する
     * (自分自身のポートを再採番するケース)。
     */
    fun usedPorts(excludeSync: String? = null): Set<Int> {
        val fromConfig = configSource.listTables()
            .filter { it != excludeSync }
            .mapNotNull { name ->
                runCatching { configSource.loadTableSet(name).server.port }
                    .onFailure { log.debug { "ポート収集のため設定を読めませんでした: $name (${it.message})" } }
                    .getOrNull()
            }
            .filter { it > 0 }

        val fromRuntime = runner?.listActive()
            ?.filter { it.tableName != excludeSync }
            ?.map { it.port }
            ?.filter { it > 0 }
            ?: emptyList()

        return (fromConfig + fromRuntime).toSet()
    }

    /**
     * publish 範囲内の空きポートを 1 つ採番する。
     *
     * @throws IllegalStateException 範囲内に空きが無い場合
     */
    fun allocate(excludeSync: String? = null): Int {
        val allocator = PortAllocator(start = rangeStart, end = rangeEnd)
        usedPorts(excludeSync).forEach { allocator.reserve(it) }
        val port = allocator.findAvailable()
        check(port in rangeStart..rangeEnd) {
            "Adapter 用ポートの空きがありません ($rangeStart-$rangeEnd)。" +
                "不要な Sync を削除するか、docker-compose.yml の publish 範囲を広げてください。"
        }
        return port
    }
}
