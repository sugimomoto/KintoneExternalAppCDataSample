package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.jdbc.DriverActivator
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.driverActivateResultView
import com.cdata.kintone.adapter.web.views.driverActivateView
import com.cdata.kintone.adapter.web.views.driversListView
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
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
import com.cdata.kintone.adapter.web.views.licenseVerificationResult
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import io.ktor.utils.io.jvm.javaio.toInputStream

fun Route.driversRoutes(ctx: AppContext) {
    get("/drivers") {
        call.respondHtml { driversListView(ctx) }
    }

    post("/drivers/upload") {
        val error = receiveDriverJar(ctx, call.receiveMultipart())
        ctx.connectionPropertyInspector.invalidateCache()
        if (error != null) {
            call.respondText("Upload failed: $error", status = HttpStatusCode.BadRequest)
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

    post("/drivers/{filename}/verify-license") {
        val html = licenseVerificationHtml(ctx, call.parameters["filename"]!!)
        call.respondText(html, io.ktor.http.ContentType.Text.Html)
    }

    post("/drivers/{filename}/delete") {
        val filename = call.parameters["filename"]!!
        ctx.driverManager.delete(filename)
        ctx.connectionPropertyInspector.invalidateCache()
        call.respondRedirect("/drivers")
    }
}

/**
 * ライセンスが実際に使えるかを検証した結果のフラグメント。
 *
 * `.lic` の有無では分からないため明示的に確認する。
 * 検証はライセンスファイルを書き換えない (Issue #31)。
 * ドライバークラスが判別できない場合はその旨を返す。
 */
private fun licenseVerificationHtml(ctx: AppContext, filename: String): String {
    val driverClass = ctx.driverManager.listDrivers()
        .firstOrNull { it.filename == filename }
        ?.driverClass
        ?: return createHTML().span { +"ドライバークラスを判別できません" }

    val verification = ctx.licenseVerifier.verify(driverClass, filename)
    return createHTML().div { licenseVerificationResult(verification) }
}

/**
 * アップロードされた JAR を `lib/` に保存する。失敗した場合はその理由を返す。
 *
 * multipart の解析とファイル保存を `driversRoutes` から切り出している。
 */
private suspend fun receiveDriverJar(ctx: AppContext, multipart: MultiPartData): String? {
    var error: String? = null
    multipart.forEachPart { part ->
        if (part is PartData.FileItem) {
            val filename = part.originalFileName ?: "uploaded.jar"
            try {
                part.provider().toInputStream().use { stream -> ctx.driverManager.upload(filename, stream) }
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        }
        part.dispose()
    }
    return error
}
