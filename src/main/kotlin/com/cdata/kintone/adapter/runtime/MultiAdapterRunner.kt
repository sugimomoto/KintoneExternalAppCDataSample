package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.jdbc.ConnectionProvider
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val log = KotlinLogging.logger {}

/**
 * 1 JVM 内で複数 [TableAdapterServer] を統合管理するランナー。
 *
 * - 起動: [startOne] / [startAll]
 * - 停止: [stopOne] / [close]
 * - 状態取得: [listActive]
 *
 * 「ConfigSource 経由でテーブル設定を取得 → TableAdapterServer.start() で起動」
 * の流れをまとめる。テーブルごとに独立した [ConnectionProvider] が生成され、
 * 1 つのテーブルの障害が他に波及しないよう catch + ログでまとめる方針。
 */
class MultiAdapterRunner(
    private val configSource: ConfigSource,
    private val connectionProviderFactory: (JdbcConfig, String) -> ConnectionProvider = ::JdbcConnectionProvider,
    private val activeFile: ActiveAdaptersFile? = null,
) : AutoCloseable {

    private val lock = ReentrantLock()
    private val servers = ConcurrentHashMap<String, TableAdapterServer>()
    private val startedAt = ConcurrentHashMap<String, Long>()

    private fun syncActiveFile() {
        activeFile?.write(listActive())
    }

    /** 1 テーブルを起動。すでに起動中なら [IllegalStateException]。 */
    fun startOne(tableName: String): TableAdapterServer = lock.withLock {
        check(!servers.containsKey(tableName)) {
            "テーブルはすでに起動中です: $tableName"
        }
        val set = configSource.loadTableSet(tableName)
        val server = TableAdapterServer(
            tableName = tableName,
            config = set,
            // OAuth キャッシュは接続単位で共有する。インライン設定なら連携名 (Issue #11)。
            oauthCacheKey = configSource.sharedJdbcRefOf(tableName) ?: tableName,
            connectionProviderFactory = connectionProviderFactory,
        )
        server.start()
        servers[tableName] = server
        startedAt[tableName] = System.currentTimeMillis()
        log.info { "MultiAdapterRunner: started $tableName at port=${server.actualPort}" }
        syncActiveFile()
        server
    }

    /**
     * `configSource.listTables()` の全テーブルを起動する。
     * 個別テーブルの起動失敗は catch してログに残し、他の起動は続行する。
     *
     * @return 起動に成功したテーブル名のリスト
     */
    fun startAll(): List<String> {
        val tables = configSource.listTables()
        val started = mutableListOf<String>()
        for (name in tables) {
            try {
                startOne(name)
                started.add(name)
            } catch (e: Exception) {
                log.error(e) { "テーブル $name の起動に失敗: ${e.message}" }
            }
        }
        return started
    }

    /** 1 テーブルを停止する。存在しないテーブル名は no-op。 */
    fun stopOne(tableName: String) = lock.withLock {
        val server = servers.remove(tableName) ?: return@withLock
        startedAt.remove(tableName)
        runCatching { server.close() }
        log.info { "MultiAdapterRunner: stopped $tableName" }
        syncActiveFile()
    }

    /** 稼働中の全テーブル状態を返す。 */
    fun listActive(): List<AdapterStatus> = servers.map { (name, server) ->
        AdapterStatus(
            tableName = name,
            port = server.actualPort,
            startedAt = startedAt[name] ?: 0L,
        )
    }

    /** 個別 [TableAdapterServer] の参照（テスト/管理用）。 */
    fun get(tableName: String): TableAdapterServer? = servers[tableName]

    override fun close() {
        lock.withLock {
            log.info { "MultiAdapterRunner: closing ${servers.size} servers" }
            for ((_, server) in servers) {
                runCatching { server.close() }
            }
            servers.clear()
            startedAt.clear()
            activeFile?.delete()
        }
    }
}

/** [MultiAdapterRunner.listActive] が返す稼働情報。 */
data class AdapterStatus(
    val tableName: String,
    val port: Int,
    val startedAt: Long,
    val status: String = "SERVING",
)
