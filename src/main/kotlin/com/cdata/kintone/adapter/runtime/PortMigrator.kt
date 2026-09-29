package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.TableConfigSet
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * `port: 0` (自動採番) で保存されている既存 Sync を、Docker の publish 範囲内の
 * 固定ポートへ移行する (Issue #3)。
 *
 * `port: 0` のままだと Adapter が起動のたびに別の ephemeral port を listen するため、
 * - Agent コンテナから到達できない (publish 範囲外)
 * - `agent.json` の `adapter_addr` がすぐ陳腐化する
 * という二重の問題が起きる。web-ui 起動時に一度だけ実行する。
 *
 * `agent.json` の更新はパッケージ依存を増やさないよう [onPortChanged] コールバックに委譲する。
 */
class PortMigrator(
    private val configSource: ConfigSource,
    private val allocator: SyncPortAllocator,
    /** 移行が発生した Sync について (syncName, newPort) で呼ばれる。agent.json の更新等に使う。 */
    private val onPortChanged: (String, Int) -> Unit = { _, _ -> },
) {

    data class Migration(val syncName: String, val newPort: Int)

    /**
     * `port: 0` の Sync をすべて移行する。
     * 個別の失敗はログに残して続行し、成功したものだけを返す。
     */
    fun migrate(): List<Migration> =
        configSource.listTables()
            .mapNotNull { name -> loadAutoPortSet(name)?.let { name to it } }
            .mapNotNull { (name, set) -> migrateOne(name, set) }

    /** `port: 0` の設定だけを返す。読めない / 固定ポートの場合は null。 */
    private fun loadAutoPortSet(name: String): TableConfigSet? {
        val set = runCatching { configSource.loadTableSet(name) }
            .onFailure { log.warn { "ポート移行のため設定を読めませんでした: $name (${it.message})" } }
            .getOrNull()
        return set?.takeIf { it.server.isAutoPort() }
    }

    private fun migrateOne(name: String, set: TableConfigSet): Migration? = runCatching {
        val newPort = allocator.allocate(excludeSync = name)
        saveKeepingJdbcRef(name, set.copy(server = set.server.copy(port = newPort)))
        onPortChanged(name, newPort)
        log.info {
            "Sync '$name' のポートを自動採番 (0) から $newPort へ移行しました " +
                "(publish 範囲 ${PortAllocator.publishedRangeLabel})"
        }
        Migration(name, newPort)
    }.onFailure {
        log.error(it) { "Sync '$name' のポート移行に失敗しました: ${it.message}" }
    }.getOrNull()

    /**
     * jdbc-ref で保存されている Sync は参照形式を保ったまま保存し直す。
     * 判定できない場合は通常の saveTableSet にフォールバックする。
     */
    private fun saveKeepingJdbcRef(name: String, set: TableConfigSet) {
        val refName = configSource.listSharedJdbcConfigs()
            .firstOrNull { configSource.loadSharedJdbcConfig(it) == set.jdbc }
        if (refName != null) {
            configSource.saveTableSetWithRef(name, set, jdbcRef = refName)
        } else {
            configSource.saveTableSet(name, set)
        }
    }
}
