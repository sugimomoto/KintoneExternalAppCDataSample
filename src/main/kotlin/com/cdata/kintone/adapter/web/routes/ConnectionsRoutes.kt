package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import com.cdata.kintone.adapter.jdbc.ConnectionValidator
import com.cdata.kintone.adapter.jdbc.ConnectionValidation
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.JdbcReferenceIndex
import com.cdata.kintone.adapter.config.PoolConfig
import com.cdata.kintone.adapter.jdbc.JdbcConnectionPropertyInspector
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.jdbc.authSchemeDefaultOf
import com.cdata.kintone.adapter.jdbc.OAuthCapability
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.BlockedDelete
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
import kotlinx.html.article
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
     * 動的プロパティフォームを返す HTMX エンドポイント。
     *
     * ドライバー選択時と、認証方式など条件に使われるプロパティの変更時に呼ばれる。
     * 後者では入力済みの値を保ったまま再描画する必要があるため、フォーム値ごと
     * 受け取れるよう POST にしている (Issue #14)。認証情報がクエリ文字列に
     * 載らない利点もある。
     */
    post("/connections/properties") {
        val form = call.receiveParameters()
        val driverParam = form["driver"]
            ?: return@post call.respondText("missing driver param")
        val (driverClass, jarFilename) = driverParam.split("|", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return@post call.respondText("invalid driver param")
        }
        // ドライバーを切り替えたときは、前のドライバーの入力値を引き継がない。
        val values = if (form["prefill"] == "false") emptyMap() else ConnectionFormUrl.propertyValues(form)

        val result = ctx.connectionPropertyInspector.fetchProperties(driverClass, jarFilename)
        val html = createHTML().div {
            propertiesFormContent(result, values)
        }
        // 条件に使われる入力欄はプレビュー更新ではなくこちらを呼ぶため、
        // 接続文字列プレビューは out-of-band で一緒に差し替える。
        call.respondText(html + urlPreviewOutOfBand(driverClass, values), io.ktor.http.ContentType.Text.Html)
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

        val url = ConnectionFormUrl.build(jdbcPrefix, form)
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
        val url = ConnectionFormUrl.build(jdbcPrefix, form)
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
        // OAuth 接続は保存しただけでは使えない。認可ウィザードは保存済み設定の
        // 読み書きを前提にしているため (#34)、名前が確定したこの時点が唯一の
        // 合流点になる。必要な次の一歩へそのまま繋ぐ (Issue #43)。
        call.respondRedirect(nextPathAfterCreate(ctx, name, config, driverClass, jarFilename))
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
        val url = ConnectionFormUrl.build(jdbcPrefix, form, fallbackUrl = existing.url)
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
        // 参照中の接続を消すと連携の loadTableSet が ConfigParseException で失敗し、
        // 起動できなくなる。UI にその動線を作らないため削除を拒否する (Issue #36)。
        val referencing = JdbcReferenceIndex.tablesReferencing(referenceMapOf(ctx), name)
        if (referencing.isNotEmpty()) {
            return@post call.respondHtml {
                connectionsListView(ctx, BlockedDelete(name, referencing))
            }
        }
        ctx.configSource.deleteSharedJdbcConfig(name)
        call.respondRedirect("/connections")
    }

    /**
     * 接続テスト。**保存済みの設定**で接続を試みる。
     *
     * レイアウトを含まない HTML 断片を返す。呼び出し側は htmx で行内を差し替えること
     * （フォーム送信すると裸の断片がページとして表示される (Issue #40)）。
     */
    post("/connections/{name}/test") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        val validation = validateConnection(config, name)
        call.respondText(connectionTestFragment(name, validation), io.ktor.http.ContentType.Text.Html)
    }
}

/**
 * フォーム入力 (`prop.<Name>=<Value>`) を集めて JDBC URL を組み立てる。
 * - 値が空のものはスキップ
 * - URL 直接入力 (`jdbc.url.manual`) が指定されていればそちらを優先
 */
/**
 * 接続文字列プレビューの out-of-band 差し替え断片。
 * `#url-preview-container` の中身だけでなく要素ごと置き換えるため、class も付け直す。
 */
private fun urlPreviewOutOfBand(driverClass: String, values: Map<String, String>): String {
    val jdbcPrefix = runCatching { JdbcConnectionPropertyInspector.jdbcPrefixOf(driverClass) }
        .getOrElse { return "" }
    return createHTML().div(classes = "url-preview-sticky") {
        attributes["id"] = "url-preview-container"
        attributes["hx-swap-oob"] = "true"
        p {
            // 生の接続文字列を DOM に載せない (Issue #16)。
            code { +ConnectionStringMasker.mask(buildUrlFromValues(jdbcPrefix, values)) }
        }
    }
}

private fun buildUrlFromValues(
    jdbcPrefix: String,
    values: Map<String, String>,
    fallbackUrl: String? = null,
): String {
    val pairs = values.entries
        .filter { it.value.isNotBlank() }
        .joinToString(";") { "${it.key}=${it.value}" }
    return if (pairs.isEmpty()) fallbackUrl ?: "$jdbcPrefix:" else "$jdbcPrefix:$pairs;"
}

/**
 * 連携名 → 参照先データソース名。inline JDBC の連携は `null` になる。
 *
 * `ConfigSource` に専用 API を足さず、既存の `listTables` + `sharedJdbcRefOf` で組む。
 */
private fun referenceMapOf(ctx: AppContext): Map<String, String?> =
    ctx.configSource.listTables().associateWith { ctx.configSource.sharedJdbcRefOf(it) }

/**
 * 新規作成の保存後に送る先。
 *
 * ブラウザ認可が必要な接続だけ認可ウィザードへ送る。判定は編集画面の OAuth 導線と
 * 同じ [OAuthCapability.requiresBrowserAuthorization] を通すので基準がずれない。
 *
 * 編集保存では呼ばない。編集画面には既に導線があり、保存のたびに飛ばされるのは煩わしい。
 */
private fun nextPathAfterCreate(
    ctx: AppContext,
    name: String,
    config: JdbcConfig,
    driverClass: String,
    jarFilename: String,
): String {
    // #27 以降、既定値は接続文字列に保存されないため既定値も見る必要がある。
    // fetchProperties はキャッシュされるので保存のたびに接続を張ることはない。
    val authSchemeDefault = runCatching {
        authSchemeDefaultOf(ctx.connectionPropertyInspector.fetchProperties(driverClass, jarFilename))
    }.getOrNull()
    return if (OAuthCapability.requiresBrowserAuthorization(config.url, authSchemeDefault)) {
        "/connections/$name/oauth"
    } else {
        "/connections"
    }
}

/**
 * 接続を張って検証する。
 *
 * 接続の確立自体に失敗した場合も [ConnectionValidation.Invalid] に畳み込み、
 * 呼び出し側が「例外」と「検証失敗」を区別しなくて済むようにする。
 */
private fun validateConnection(config: JdbcConfig, name: String): ConnectionValidation =
    runCatching {
        JdbcConnectionProvider(config, oauthCacheKey = name).use { provider ->
            provider.connection().use { connection -> ConnectionValidator.validate(connection) }
        }
    }.getOrElse { cause ->
        // 例外メッセージに接続文字列が含まれる場合があるため必ずマスクを通す。
        // メッセージはロケール依存なので分類はしない (#19 の方針)。
        ConnectionValidation.Invalid(ConnectionStringMasker.mask(cause.message ?: "接続に失敗しました"))
    }

/**
 * 接続テスト結果の断片。
 *
 * 結果はテーブルの外の共有バナーに入るため、どの接続のものか分かるように
 * 名前を含める。成功・失敗は既存のバナークラスで出し分ける (Issue #42)。
 */
private fun connectionTestFragment(name: String, validation: ConnectionValidation): String {
    val bannerClass = when (validation) {
        is ConnectionValidation.Valid -> "success-banner"
        is ConnectionValidation.Invalid -> "warning-banner"
    }
    return createHTML().article(classes = bannerClass) {
        p {
            code { +name }
            when (validation) {
                is ConnectionValidation.Valid -> +" 接続成功: ${validation.description}"
                is ConnectionValidation.Invalid -> +" 接続失敗: ${validation.reason}"
            }
        }
    }
}
