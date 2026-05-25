package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.jdbc.DriverActivator
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.driverActivateResultView
import com.cdata.kintone.adapter.web.views.driverActivateView
import com.cdata.kintone.adapter.web.views.driversListView
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream

fun Route.driversRoutes(ctx: AppContext) {
    get("/drivers") {
        call.respondHtml { driversListView(ctx) }
    }

    post("/drivers/upload") {
        var uploadedFilename: String? = null
        var uploadError: String? = null
        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val filename = part.originalFileName ?: "uploaded.jar"
                    try {
                        part.provider().toInputStream().use { stream ->
                            val info = ctx.driverManager.upload(filename, stream)
                            uploadedFilename = info.filename
                        }
                    } catch (e: IllegalArgumentException) {
                        uploadError = e.message
                    }
                }
                else -> {}
            }
            part.dispose()
        }
        ctx.connectionPropertyInspector.invalidateCache()
        if (uploadError != null) {
            call.respondText("Upload failed: $uploadError", status = HttpStatusCode.BadRequest)
        } else {
            call.respondRedirect("/drivers")
        }
    }

    get("/drivers/{filename}/activate") {
        val filename = call.parameters["filename"]!!
        call.respondHtml { driverActivateView(ctx, filename) }
    }

    post("/drivers/{filename}/activate") {
        val filename = call.parameters["filename"]!!
        val form = call.receiveParameters()
        val name = form["name"]?.trim().orEmpty()
        val email = form["email"]?.trim().orEmpty()
        if (name.isEmpty() || email.isEmpty()) {
            return@post call.respondText("name and email required", status = HttpStatusCode.BadRequest)
        }
        val jarPath = ctx.libDir.resolve(filename)
        val result = DriverActivator().activateTrial(jarPath, name, email)
        ctx.connectionPropertyInspector.invalidateCache()
        val (success, msg, stdout) = when (result) {
            is DriverActivator.Result.Success -> Triple(true, "License installation succeeded.", "")
            is DriverActivator.Result.Failure -> Triple(false, result.message, result.stdout)
        }
        call.respondHtml { driverActivateResultView(ctx, filename, success, msg, stdout) }
    }

    post("/drivers/{filename}/delete") {
        val filename = call.parameters["filename"]!!
        ctx.driverManager.delete(filename)
        ctx.connectionPropertyInspector.invalidateCache()
        call.respondRedirect("/drivers")
    }
}
