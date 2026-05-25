package com.cdata.kintone.adapter.web

import com.cdata.kintone.adapter.web.routes.connectKintoneRoutes
import com.cdata.kintone.adapter.web.routes.connectionsRoutes
import com.cdata.kintone.adapter.web.routes.driversRoutes
import com.cdata.kintone.adapter.web.routes.helpRoutes
import com.cdata.kintone.adapter.web.routes.runtimeRoutes
import com.cdata.kintone.adapter.web.routes.syncLogsRoutes
import com.cdata.kintone.adapter.web.routes.tableWizardRoutes
import com.cdata.kintone.adapter.web.routes.tablesRoutes
import com.cdata.kintone.adapter.web.views.dashboardView
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import org.slf4j.event.Level

private val log = KotlinLogging.logger {}

/**
 * Web UI 用 Ktor アプリケーション。
 * `adapter web-ui` サブコマンドから起動される。
 */
class WebUiServer(
    private val context: AppContext,
    private val port: Int = 8080,
    private val bindAddress: String = "127.0.0.1",
) : AutoCloseable {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start(wait: Boolean = false) {
        log.info { "Web UI 起動: http://$bindAddress:$port" }
        server = embeddedServer(Netty, port = port, host = bindAddress) {
            module(context)
        }.also { it.start(wait = wait) }
    }

    override fun close() {
        server?.stop(1000, 5000)
        context.close()
    }
}

internal fun Application.module(context: AppContext) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.local.uri.startsWith("/static/").not() }
    }
    install(SSE)
    routing {
        get("/") {
            call.respondHtml { dashboardView(context) }
        }
        get("/static/{filename}") {
            val filename = call.parameters["filename"] ?: return@get call.respondText("missing filename")
            val resource = this::class.java.classLoader.getResourceAsStream("static/$filename")
                ?: return@get call.respondText("not found: $filename")
            val contentType = when {
                filename.endsWith(".css") -> ContentType.Text.CSS
                filename.endsWith(".js") -> ContentType.Application.JavaScript
                else -> ContentType.Application.OctetStream
            }
            call.respondBytes(resource.readAllBytes(), contentType)
        }
        // 順序重要: tableWizardRoutes が /syncs/new, POST /syncs を担当するため tablesRoutes より先
        tableWizardRoutes(context)
        connectKintoneRoutes(context)
        syncLogsRoutes(context)
        tablesRoutes(context)
        connectionsRoutes(context)
        driversRoutes(context)
        runtimeRoutes(context)
        helpRoutes(context)
    }
}
