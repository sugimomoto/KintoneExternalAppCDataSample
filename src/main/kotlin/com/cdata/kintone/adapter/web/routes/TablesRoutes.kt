package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.agent.AgentConfig
import com.cdata.kintone.adapter.config.ConfigFileMissingException
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.tableDetailView
import com.cdata.kintone.adapter.web.views.tableEditView
import com.cdata.kintone.adapter.web.views.tablesListView
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.tablesRoutes(ctx: AppContext) {
    get("/tables") {
        call.respondHtml { tablesListView(ctx) }
    }

    get("/tables/{name}") {
        val name = call.parameters["name"]!!
        val set = runCatching { ctx.configSource.loadTableSet(name) }.getOrNull()
            ?: return@get call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        call.respondHtml { tableDetailView(ctx, name, set) }
    }

    get("/tables/{name}/edit") {
        val name = call.parameters["name"]!!
        val set = runCatching { ctx.configSource.loadTableSet(name) }.getOrNull()
            ?: return@get call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        call.respondHtml { tableEditView(ctx, name, set) }
    }

    post("/tables/{name}") {
        val name = call.parameters["name"]!!
        val original = runCatching { ctx.configSource.loadTableSet(name) }.getOrNull()
            ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        val updated = mergeTableSet(original, form)
        ctx.configSource.saveTableSet(name, updated)

        when (form["action"]) {
            "save-and-restart" -> {
                runCatching { ctx.runner.stopOne(name) }
                runCatching { ctx.runner.startOne(name) }
            }
        }
        call.respondRedirect("/tables/$name")
    }

    post("/tables/{name}/delete") {
        val name = call.parameters["name"]!!
        runCatching { ctx.runner.stopOne(name) }
        ctx.configSource.deleteTable(name)
        call.respondRedirect("/tables")
    }

    post("/tables/{name}/start") {
        val name = call.parameters["name"]!!
        try {
            ctx.runner.startOne(name)
        } catch (e: ConfigFileMissingException) {
            return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        } catch (e: IllegalStateException) {
            // 既に起動中
        } catch (e: Exception) {
            return@post call.respondText(
                "起動失敗: ${e.message}",
                status = HttpStatusCode.InternalServerError,
            )
        }
        call.respondRedirect(call.request.headers["Referer"] ?: "/tables")
    }

    post("/tables/{name}/stop") {
        val name = call.parameters["name"]!!
        ctx.runner.stopOne(name)
        call.respondRedirect(call.request.headers["Referer"] ?: "/tables")
    }

    post("/tables/{name}/restart") {
        val name = call.parameters["name"]!!
        runCatching { ctx.runner.stopOne(name) }
        runCatching { ctx.runner.startOne(name) }
        call.respondRedirect(call.request.headers["Referer"] ?: "/tables/$name")
    }

    post("/tables/{name}/agent") {
        val name = call.parameters["name"]!!
        val form = call.receiveParameters()
        val token = form["token"]?.trim()
            ?: return@post call.respondText("token required", status = HttpStatusCode.BadRequest)
        val adapterAddr = form["adapter_addr"]?.trim()
            ?: return@post call.respondText("adapter_addr required", status = HttpStatusCode.BadRequest)
        val plaintext = form["adapter_plaintext"] == "on"
        val privateKeyPath = form["private_key_path"]?.takeIf { it.isNotBlank() } ?: "/opt/agent/private-key.pem"
        ctx.agentConfigManager.save(
            name,
            AgentConfig(
                token = token,
                adapterAddr = adapterAddr,
                adapterPlaintext = plaintext,
                privateKeyPath = privateKeyPath,
            ),
        )
        call.respondRedirect("/tables/$name")
    }

    post("/tables/{name}/agent/delete") {
        val name = call.parameters["name"]!!
        ctx.agentConfigManager.delete(name)
        call.respondRedirect("/tables/$name")
    }
}

/**
 * フォーム入力 (sub-section ごとにドット記法) を既存 TableConfigSet にマージ。
 * Phase 2-B では server / jdbc / capability の主要フィールドのみ編集対応。
 * カラム編集は新規ウィザード (M8) の再実行で代替。
 */
private fun mergeTableSet(
    original: com.cdata.kintone.adapter.config.TableConfigSet,
    form: io.ktor.http.Parameters,
): com.cdata.kintone.adapter.config.TableConfigSet {
    val server = original.server.copy(
        port = form["server.port"]?.toIntOrNull() ?: original.server.port,
        bindAddress = form["server.bindAddress"] ?: original.server.bindAddress,
        plaintext = form["server.plaintext"] == "on",
    )
    val jdbc = original.jdbc.copy(
        driverClass = form["jdbc.driverClass"] ?: original.jdbc.driverClass,
        driverJar = form["jdbc.driverJar"] ?: original.jdbc.driverJar,
        url = form["jdbc.url"] ?: original.jdbc.url,
    )
    val capability = original.capability.copy(
        recordIdType = form["capability.recordIdType"]?.let {
            runCatching { RecordIdType.valueOf(it) }.getOrDefault(original.capability.recordIdType)
        } ?: original.capability.recordIdType,
        filterableFields = form["capability.filterableFields"]?.let { csvToList(it) }
            ?: original.capability.filterableFields,
        sortableFields = form["capability.sortableFields"]?.let { csvToList(it) }
            ?: original.capability.sortableFields,
    )
    return original.copy(server = server, jdbc = jdbc, capability = capability)
}

private fun csvToList(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
