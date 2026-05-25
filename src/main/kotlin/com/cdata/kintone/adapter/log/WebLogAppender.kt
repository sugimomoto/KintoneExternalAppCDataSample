package com.cdata.kintone.adapter.log

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Logback Appender でメモリ内に直近 N 件のログを保持し、Web UI へ配信する。
 *
 * Phase 2-C M5 ライブログ表示の Adapter 側ソース。
 * Sync 別フィルタは MDC (Mapped Diagnostic Context) の `sync` キーで実現。
 */
class WebLogAppender : AppenderBase<ILoggingEvent>() {

    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val size = AtomicInteger(0)
    private val subscribers = mutableListOf<(LogEntry) -> Unit>()

    @Volatile var capacity: Int = 1000

    override fun append(event: ILoggingEvent) {
        val entry = LogEntry(
            timestamp = event.timeStamp,
            level = event.level.toString(),
            logger = event.loggerName,
            message = event.formattedMessage,
            sync = event.mdcPropertyMap?.get("sync"),
        )
        buffer.add(entry)
        if (size.incrementAndGet() > capacity) {
            buffer.pollFirst()
            size.decrementAndGet()
        }
        // listeners は append 中にロックフリーで通知
        synchronized(subscribers) {
            subscribers.toList().forEach { runCatching { it(entry) } }
        }
    }

    fun snapshot(syncName: String? = null, levels: Set<String>? = null): List<LogEntry> {
        val all = buffer.toList()
        return all.filter { entry ->
            (syncName == null || entry.sync == syncName) &&
                (levels == null || entry.level in levels)
        }
    }

    fun subscribe(callback: (LogEntry) -> Unit): () -> Unit {
        synchronized(subscribers) { subscribers.add(callback) }
        return {
            synchronized(subscribers) { subscribers.remove(callback) }
        }
    }

    companion object {
        @Volatile private var instance: WebLogAppender? = null

        /** logback.xml から参照されたインスタンスを取り出す。 */
        fun current(): WebLogAppender? = instance

        /** logback の初期化中に呼ばれる (set instance)。 */
        fun register(appender: WebLogAppender) {
            instance = appender
        }
    }

    init {
        register(this)
    }
}

data class LogEntry(
    val timestamp: Long,
    val level: String,
    val logger: String,
    val message: String,
    val sync: String?,
)
