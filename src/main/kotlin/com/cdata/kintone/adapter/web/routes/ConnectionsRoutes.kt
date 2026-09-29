package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PoolConfig
import com.cdata.kintone.adapter.jdbc.JdbcConnectionPropertyInspector
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.connectionFormView
import com.cdata.kintone.adapter.web.views.connectionsListView
import com.cdata.kintone.adapter.web.views.propertiesFormContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML

fun Route.connectionsRoutes(ctx: AppContext) {
    get("/connections") {
        call.respondHtml { connectionsListView(ctx) }
    }

    get("/connections/new") {
        call.respondHtml { connectionFormView(ctx, editMode = false) }
    }

    get("/connections/{name}") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@get call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        call.respondHtml { connectionFormView(ctx, editMode = true, existingName = name, existing = config) }
    }

    get("/connections/{name}/edit") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@get call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        call.respondHtml { connectionFormView(ctx, editMode = true, existingName = name, existing = config) }
    }

    /**
     * ドライバ選択時に動的フォームを返す HTMX エンドポイント。
     * `?driver=<driverClass>|<jarFilename>` を受けて `sys_connection_props` 結果を返す。
     */
    get("/connections/properties") {
        val driverParam = call.request.queryParameters["driver"]
            ?: return@get call.respondText("missing driver param")
        val (driverClass, jarFilename) = driverParam.split("|", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return@get call.respondText("invalid driver param")
        }
        val props = ctx.connectionPropertyInspector.listProperties(driverClass, jarFilename)
        val html = createHTML().div {
            propertiesFormContent(props)
        }
        call.respondText(html, io.ktor.http.ContentType.Text.Html)
    }

    /**
     * フォーム入力値から JDBC URL プレビューを生成して返す。
     */
    post("/connections/preview-url") {
        val form = call.receiveParameters()
        val driverParam = form["driver"] ?: return@post call.respondText("missing driver")
        val (driverClass, _) = driverParam.split("|", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return@post call.respondText("invalid driver")
        }
        val jdbcPrefix = runCatching {
            JdbcConnectionPropertyInspector.jdbcPrefixOf(driverClass)
        }.getOrElse { return@post call.respondText("not a CData driver") }

        val url = buildUrlFromForm(jdbcPrefix, form)
        // プレビューは「どのプロパティが組み込まれたか」の構造確認が目的。
        // 秘密の値そのものは出さない (Issue #16)。
        val html = createHTML().p {
            code { +ConnectionStringMasker.mask(url) }
        }
        call.respondText(html, io.ktor.http.ContentType.Text.Html)
    }

    post("/connections") {
        val form = call.receiveParameters()
        val name = form["name"]?.trim()
            ?: return@post call.respondText("name required", status = HttpStatusCode.BadRequest)
        val driverParam = form["driver"]
            ?: return@post call.respondText("driver required", status = HttpStatusCode.BadRequest)
        val (driverClass, jarFilename) = driverParam.split("|", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return@post call.respondText("invalid driver")
        }
        val jdbcPrefix = JdbcConnectionPropertyInspector.jdbcPrefixOf(driverClass)
        val url = buildUrlFromForm(jdbcPrefix, form)
        val config = JdbcConfig(
            driverClass = driverClass,
            driverJar = "./lib/$jarFilename",
            url = url,
            pool = PoolConfig(
                maximumPoolSize = form["pool.maximumPoolSize"]?.toIntOrNull() ?: 10,
                connectionTimeout = form["pool.connectionTimeout"]?.toLongOrNull() ?: 30_000L,
            ),
        )
        ctx.configSource.saveSharedJdbcConfig(name, config)
        call.respondRedirect("/connections")
    }

    post("/connections/{name}") {
        // 編集保存 (POST /connections/{name} as update)
        val name = call.parameters["name"]!!
        val form = call.receiveParameters()
        val existing = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        val driverParam = form["driver"] ?: "${existing.driverClass}|${existing.driverJar.substringAfterLast('/')}"
        val (driverClass, jarFilename) = driverParam.split("|", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return@post call.respondText("invalid driver")
        }
        val jdbcPrefix = JdbcConnectionPropertyInspector.jdbcPrefixOf(driverClass)
        val url = buildUrlFromForm(jdbcPrefix, form, fallbackUrl = existing.url)
        val config = JdbcConfig(
            driverClass = driverClass,
            driverJar = "./lib/$jarFilename",
            url = url,
            pool = PoolConfig(
                maximumPoolSize = form["pool.maximumPoolSize"]?.toIntOrNull() ?: existing.pool.maximumPoolSize,
                connectionTimeout = form["pool.connectionTimeout"]?.toLongOrNull() ?: existing.pool.connectionTimeout,
            ),
        )
        ctx.configSource.saveSharedJdbcConfig(name, config)
        call.respondRedirect("/connections")
    }

    post("/connections/{name}/delete") {
        val name = call.parameters["name"]!!
        ctx.configSource.deleteSharedJdbcConfig(name)
        call.respondRedirect("/connections")
    }

    /**
     * 接続テスト。フォーム値 (まだ保存前でも) で接続を試みる。
     */
    post("/connections/{name}/test") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        val result = runCatching {
            JdbcConnectionProvider(config).use { provider ->
                provider.connection().use { conn ->
                    conn.metaData.let { md ->
                        "${md.databaseProductName} ${md.databaseProductVersion} / ${md.driverName} ${md.driverVersion}"
                    }
                }
            }
        }
        val html = createHTML().div {
            if (result.isSuccess) {
                p { +"●Connected: ${result.getOrNull()}" }
            } else {
                p { +"●Failed: ${result.exceptionOrNull()?.message}" }
            }
        }
        call.respondText(html, io.ktor.http.ContentType.Text.Html)
    }
}

/**
 * フォーム入力 (`prop.<Name>=<Value>`) を集めて JDBC URL を組み立てる。
 * - 値が空のものはスキップ
 * - URL 直接入力 (`jdbc.url.manual`) が指定されていればそちらを優先
 */
private fun buildUrlFromForm(jdbcPrefix: String, form: Parameters, fallbackUrl: String? = null): String {
    form["jdbc.url.manual"]?.takeIf { it.isNotBlank() }?.let { return it }
    val pairs = form.entries()
        .asSequence()
        .filter { it.key.startsWith("prop.") }
        .mapNotNull { (key, values) ->
            val v = values.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = key.removePrefix("prop.")
            "$name=$v"
        }
        .joinToString(";")
    return if (pairs.isEmpty()) fallbackUrl ?: "$jdbcPrefix:" else "$jdbcPrefix:$pairs;"
}
