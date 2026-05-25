package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.log.WebLogAppender
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.syncLogsView
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Frame
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.syncLogsRoutes(ctx: AppContext) {

    /** ログ画面 HTML。 */
    get("/syncs/{name}/logs") {
        val syncName = call.parameters["name"]!!
        call.respondHtml { syncLogsView(ctx, syncName) }
    }

    /** Adapter のログ SSE。 */
    sse("/syncs/{name}/logs/adapter/sse") {
        val syncName = call.parameters["name"]!!
        val appender = WebLogAppender.current() ?: return@sse
        // 既存スナップショット (このセッションの開始時点)
        appender.snapshot(syncName).takeLast(100).forEach { entry ->
            send(ServerSentEvent(event = "log", data = jsonOf(entry)))
        }
        // 以後の追加分を購読
        val ch = Channel<String>(capacity = 256)
        val unsubscribe = appender.subscribe { entry ->
            if (entry.sync == syncName || entry.sync == null) {
                ch.trySend(jsonOf(entry))
            }
        }
        try {
            for (line in ch) {
                send(ServerSentEvent(event = "log", data = line))
            }
        } finally {
            unsubscribe()
            ch.close()
        }
    }

    /** Agent コンテナのログ SSE (Docker API 中継)。 */
    sse("/syncs/{name}/logs/agent/sse") {
        val syncName = call.parameters["name"]!!
        val containerMgr = ctx.agentContainerManager ?: return@sse run {
            send(ServerSentEvent(event = "log", data = "(Docker socket 利用不可)"))
        }
        val containerName = containerMgr.containerName(syncName)
        val ch = Channel<String>(capacity = 256)
        val cb = object : ResultCallback.Adapter<Frame>() {
            override fun onNext(frame: Frame) {
                String(frame.payload, Charsets.UTF_8).split('\n').forEach { line ->
                    if (line.isNotBlank()) ch.trySend(line)
                }
            }
            override fun onComplete() { ch.close() }
            override fun onError(throwable: Throwable) { ch.close(throwable) }
        }
        val cmd = try {
            ctx.agentContainerManager.let { /* same as containerMgr */ }
            val dockerClient = AgentContainerManagerAccessor.dockerClientOf(containerMgr)
            dockerClient.logContainerCmd(containerName)
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(true)
                .withTail(100)
                .exec(cb)
        } catch (e: NotFoundException) {
            send(ServerSentEvent(event = "log", data = "(コンテナが見つかりません: $containerName)"))
            return@sse
        }
        try {
            for (line in ch) {
                send(ServerSentEvent(event = "log", data = line))
            }
        } finally {
            runCatching { cmd.close() }
        }
    }
}

private val JSON = Json { ignoreUnknownKeys = true }

@kotlinx.serialization.Serializable
private data class LogEntryJson(
    val timestamp: Long,
    val level: String,
    val logger: String,
    val message: String,
    val sync: String? = null,
)

private fun jsonOf(entry: com.cdata.kintone.adapter.log.LogEntry): String {
    val j = LogEntryJson(
        timestamp = entry.timestamp,
        level = entry.level,
        logger = entry.logger,
        message = entry.message,
        sync = entry.sync,
    )
    return JSON.encodeToString(j)
}

/** AgentContainerManager から DockerClient にアクセスするためのヘルパ (パッケージプライベート相当)。 */
internal object AgentContainerManagerAccessor {
    fun dockerClientOf(mgr: com.cdata.kintone.adapter.agent.AgentContainerManager): com.github.dockerjava.api.DockerClient {
        val field = mgr.javaClass.getDeclaredField("dockerClient")
        field.isAccessible = true
        return field.get(mgr) as com.github.dockerjava.api.DockerClient
    }
}
