package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.helpView
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.helpRoutes(ctx: AppContext) {
    get("/help") {
        call.respondHtml { helpView(ctx) }
    }
}
