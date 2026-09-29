package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.FieldTypeSuggester
import com.cdata.kintone.adapter.metadata.JdbcMetadataInspector
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.WizardMapping
import com.cdata.kintone.adapter.web.views.wizardStep1View
import com.cdata.kintone.adapter.web.views.wizardStep2View
import com.cdata.kintone.adapter.web.views.wizardStep3View
import com.cdata.kintone.adapter.web.views.wizardStep4View
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.tableWizardRoutes(ctx: AppContext) {

    get("/syncs/new") {
        call.respondHtml { wizardStep1View(ctx) }
    }

    get("/syncs/new/step2") {
        val connectionName = call.request.queryParameters["connection"]
            ?: return@get call.respondRedirect("/syncs/new")
        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)
        val tables = JdbcConnectionProvider(jdbc).use { provider ->
            provider.connection().use { conn ->
                JdbcMetadataInspector(conn).listTables()
            }
        }
        call.respondHtml { wizardStep2View(ctx, connectionName, tables) }
    }

    get("/syncs/new/step3") {
        val connectionName = call.request.queryParameters["connection"]
            ?: return@get call.respondRedirect("/syncs/new")
        val tableLabel = call.request.queryParameters["table"]
            ?: return@get call.respondRedirect("/syncs/new")
        val configName = call.request.queryParameters["configName"]
            ?: return@get call.respondRedirect("/syncs/new")
        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)
        val tableName = tableLabel.substringAfter(".")
        val columns = JdbcConnectionProvider(jdbc).use { provider ->
            provider.connection().use { conn ->
                JdbcMetadataInspector(conn).listColumns(tableName)
            }
        }
        call.respondHtml { wizardStep3View(ctx, connectionName, tableLabel, configName, columns) }
    }

    post("/syncs/new/step4") {
        val form = call.receiveParameters()
        val connectionName = form["connection"]!!
        val tableLabel = form["table"]!!
        val configName = form["configName"]!!
        val selectedColumns = form.getAll("selectedColumns") ?: emptyList()

        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@post call.respondText("Connection not found", status = HttpStatusCode.NotFound)
        val tableName = tableLabel.substringAfter(".")

        val (primaryKey, recommendedRecordIdType, mappings) = JdbcConnectionProvider(jdbc).use { provider ->
            provider.connection().use { conn ->
                val inspector = JdbcMetadataInspector(conn)
                val pk = inspector.findPrimaryKey(tableName)
                    ?: throw IllegalStateException("主キーが定義されていません: $tableName")
                val allColumns = inspector.listColumns(tableName)
                val selectedColumnInfos = allColumns.filter { it.name in selectedColumns && it.name != pk.column }
                val maps = selectedColumnInfos.map { col ->
                    WizardMapping(
                        kintoneFieldId = toSnakeCase(col.name),
                        jdbcColumn = col.name,
                        columnType = FieldTypeSuggester.suggest(col.jdbcType).name,
                    )
                }
                Triple(pk.column, FieldTypeSuggester.suggestRecordIdType(pk.jdbcType), maps)
            }
        }

        call.respondHtml {
            wizardStep4View(
                ctx, connectionName, tableLabel, configName,
                primaryKey = primaryKey,
                recommendedRecordIdType = recommendedRecordIdType,
                mappings = mappings,
            )
        }
    }

    post("/syncs") {
        val form = call.receiveParameters()
        val connectionName = form["connection"]!!
        val tableLabel = form["table"]!!
        val configName = form["configName"]!!
        val primaryKey = form["primaryKeyColumn"]!!
        val recordIdType = form["recordIdType"]?.let { RecordIdType.valueOf(it) } ?: RecordIdType.TEXT
        // 空欄 / 0 (auto) は publish 範囲から採番する。範囲外の ephemeral port を掴ませない (Issue #3)。
        val requestedPort = form["port"]?.trim()?.toIntOrNull() ?: 0
        val port = if (requestedPort > 0) {
            requestedPort
        } else {
            runCatching { ctx.syncPortAllocator.allocate() }.getOrElse {
                return@post call.respondText(
                    it.message ?: "Adapter 用ポートを採番できませんでした",
                    status = HttpStatusCode.Conflict,
                )
            }
        }
        val filterableFields = form["filterableFields"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val sortableFields = form["sortableFields"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        // mapping[N].* を集める
        val mappingIndices = form.entries()
            .asSequence()
            .map { it.key }
            .filter { it.startsWith("mapping[") && it.endsWith("].kintoneFieldId") }
            .map { it.removePrefix("mapping[").substringBefore("]").toInt() }
            .toList()
            .sorted()
        val columns = mappingIndices.mapNotNull { idx ->
            val kfid = form["mapping[$idx].kintoneFieldId"]?.trim() ?: return@mapNotNull null
            val jcol = form["mapping[$idx].jdbcColumn"]?.trim() ?: return@mapNotNull null
            val type = form["mapping[$idx].columnType"]?.let { ColumnType.valueOf(it) } ?: ColumnType.TEXT
            if (kfid.isEmpty() || jcol.isEmpty()) null
            else ColumnConfig(kintoneFieldId = kfid, jdbcColumn = jcol, type = type)
        }

        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@post call.respondText("Connection not found", status = HttpStatusCode.NotFound)

        // db テーブル名は schema.name の name 部分
        val dbTableName = tableLabel.substringAfter(".")

        val set = TableConfigSet(
            server = ServerConfig(port = port, bindAddress = "0.0.0.0", plaintext = true),
            jdbc = jdbc,  // SqliteConfigSource なら saveTableSetWithRef を使うがここでは shared を inline 保存
            table = TableConfig(
                name = dbTableName,
                primaryKey = PrimaryKeyConfig(kintoneFieldId = "id", jdbcColumn = primaryKey),
                columns = columns,
            ),
            capability = CapabilityConfig(
                recordIdType = recordIdType,
                filterableFields = filterableFields.ifEmpty { listOf("id") },
                sortableFields = sortableFields.ifEmpty { listOf("id") },
            ),
        )

        // Phase 2-C: connectionName が共通 JDBC として登録されていれば jdbc-ref で保存。
        // そうでなければ inline JDBC として保存。
        if (connectionName in ctx.configSource.listSharedJdbcConfigs()) {
            ctx.configSource.saveTableSetWithRef(configName, set, jdbcRef = connectionName)
        } else {
            ctx.configSource.saveTableSet(configName, set)
        }

        // 「Save & Connect」を押した場合は kintone 接続画面へ自動遷移
        if (form["andConnect"] == "true") {
            runCatching { ctx.runner.startOne(configName) }
            call.respondRedirect("/syncs/$configName/connect")
            return@post
        }
        if (form["andStart"] == "true") {
            runCatching { ctx.runner.startOne(configName) }
        }
        call.respondRedirect("/syncs/$configName")
    }
}

private fun toSnakeCase(s: String): String {
    if (s.isEmpty()) return s
    if (s.contains('_')) return s.lowercase()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && c.isUpperCase()) sb.append('_')
        sb.append(c.lowercaseChar())
    }
    return sb.toString()
}
