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

    get("/tables/new") {
        call.respondHtml { wizardStep1View(ctx) }
    }

    get("/tables/new/step2") {
        val connectionName = call.request.queryParameters["connection"]
            ?: return@get call.respondRedirect("/tables/new")
        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)
        val tables = JdbcConnectionProvider(jdbc).use { provider ->
            provider.connection().use { conn ->
                JdbcMetadataInspector(conn).listTables()
            }
        }
        call.respondHtml { wizardStep2View(ctx, connectionName, tables) }
    }

    get("/tables/new/step3") {
        val connectionName = call.request.queryParameters["connection"]
            ?: return@get call.respondRedirect("/tables/new")
        val tableLabel = call.request.queryParameters["table"]
            ?: return@get call.respondRedirect("/tables/new")
        val configName = call.request.queryParameters["configName"]
            ?: return@get call.respondRedirect("/tables/new")
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

    post("/tables/new/step4") {
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

    post("/tables") {
        val form = call.receiveParameters()
        val connectionName = form["connection"]!!
        val tableLabel = form["table"]!!
        val configName = form["configName"]!!
        val primaryKey = form["primaryKeyColumn"]!!
        val recordIdType = form["recordIdType"]?.let { RecordIdType.valueOf(it) } ?: RecordIdType.TEXT
        val port = form["port"]?.toIntOrNull() ?: 0
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

        // SQLite 側は jdbc-ref を使う方が綺麗だが、ConfigSource 共通 API では未対応。
        // 次フェーズで saveTableSetWithRef を ConfigSource interface に昇格させる。
        ctx.configSource.saveTableSet(configName, set)

        if (form["andStart"] == "true") {
            runCatching { ctx.runner.startOne(configName) }
        }
        call.respondRedirect("/tables/$configName")
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
