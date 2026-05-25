package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.runtime.AdapterStatus
import com.cdata.kintone.adapter.web.AppContext
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.runtimeRoutes(ctx: AppContext) {
    // 単純 polling 用: 現在の active adapters を JSON で返す
    get("/runtime/active") {
        val active = ctx.runner.listActive()
        val json = Json.encodeToString(activeAdaptersJson(active))
        call.respondText(json, io.ktor.http.ContentType.Application.Json)
    }

    // Server-Sent Events: 1 秒間隔で active adapters を push
    sse("/runtime/sse") {
        while (this.coroutineContext.isActive) {
            val active = ctx.runner.listActive()
            val payload = Json.encodeToString(activeAdaptersJson(active))
            send(ServerSentEvent(data = payload, event = "active-adapters"))
            delay(1000)
        }
    }
}

@kotlinx.serialization.Serializable
private data class AdapterStatusJson(
    val tableName: String,
    val port: Int,
    val status: String,
    val startedAt: Long,
)

private fun activeAdaptersJson(active: List<AdapterStatus>): List<AdapterStatusJson> =
    active.map {
        AdapterStatusJson(
            tableName = it.tableName,
            port = it.port,
            status = it.status,
            startedAt = it.startedAt,
        )
    }
