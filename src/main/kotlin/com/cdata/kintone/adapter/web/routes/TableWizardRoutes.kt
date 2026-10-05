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
import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import io.github.oshai.kotlinlogging.KotlinLogging
import com.cdata.kintone.adapter.web.views.Step3Content
import com.cdata.kintone.adapter.metadata.JdbcMetadataInspector
import com.cdata.kintone.adapter.metadata.TableInfo
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

private val log = KotlinLogging.logger {}

fun Route.tableWizardRoutes(ctx: AppContext) {

    get("/syncs/new") {
        call.respondHtml { wizardStep1View(ctx) }
    }

    get("/syncs/new/step2") {
        val connectionName = call.request.queryParameters["connection"]
            ?: return@get call.respondRedirect("/syncs/new")
        // 空文字は「（すべて）」の選択。null と同じ扱いにする。
        val selectedSchema = call.request.queryParameters["schema"]?.takeIf { it.isNotBlank() }
        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)
        val tables = JdbcConnectionProvider(jdbc, oauthCacheKey = connectionName).use { provider ->
            provider.connection().use { conn ->
                JdbcMetadataInspector(conn).listTables()
            }
        }
        call.respondHtml { wizardStep2View(ctx, connectionName, tables, selectedSchema) }
    }

    get("/syncs/new/step3") {
        val connectionName = call.request.queryParameters["connection"]
            ?: return@get call.respondRedirect("/syncs/new")
        val tableName = call.request.queryParameters["table"]
            ?: return@get call.respondRedirect("/syncs/new")
        val configName = call.request.queryParameters["configName"]
            ?: return@get call.respondRedirect("/syncs/new")
        // スキーマはラベルから切り出さず独立した値として受け取る (Issue #63)。
        val schema = call.request.queryParameters["schema"]?.takeIf { it.isNotBlank() }
        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@get call.respondText("Connection not found", status = HttpStatusCode.NotFound)
        val columns = JdbcConnectionProvider(jdbc, oauthCacheKey = connectionName).use { provider ->
            provider.connection().use { conn ->
                JdbcMetadataInspector(conn).listColumns(tableName, schema)
            }
        }
        call.respondHtml {
            wizardStep3View(ctx, connectionName, TableInfo(schema, tableName), configName, Step3Content(columns))
        }
    }

    post("/syncs/new/step4") {
        val form = call.receiveParameters()
        val connectionName = form["connection"]!!
        val tableName = form["table"]!!
        val configName = form["configName"]!!
        val schema = form["schema"]?.takeIf { it.isNotBlank() }
        val selectedColumns = form.getAll("selectedColumns") ?: emptyList()

        val jdbc = ctx.configSource.loadSharedJdbcConfig(connectionName)
            ?: return@post call.respondText("Connection not found", status = HttpStatusCode.NotFound)

        // 例外を投げない。以前は主キーが無いと IllegalStateException が未処理となり
        // 500 で画面が真っ白になっていた (Issue #64)。
        val result = runCatching {
            computeStep4(jdbc, connectionName, tableName, schema, selectedColumns)
        }.getOrElse { cause ->
            log.warn(cause) { "step4 の算出に失敗しました: $tableName" }
            // 例外メッセージに接続文字列が含まれる場合に備えてマスクする。
            // ロケール依存のメッセージは分類しない (#19 の方針)。
            Step4Result.Failed(
                ConnectionStringMasker.mask(cause.message ?: "メタデータの取得に失敗しました"),
            )
        }
        val table = TableInfo(schema, tableName)

        when (result) {
            is Step4Result.Ready -> call.respondHtml {
                wizardStep4View(
                    ctx, connectionName, table, configName,
                    primaryKey = result.primaryKey,
                    recommendedRecordIdType = result.recordIdType,
                    mappings = result.mappings,
                )
            }

            // 失敗は step3 に戻して理由を出す。専用のエラーページを作らないのは、
            // 前のステップに戻れる状態を保つため。
            is Step4Result.NoPrimaryKey -> call.respondHtml {
                wizardStep3View(
                    ctx, connectionName, table, configName,
                    Step3Content(result.candidates, noPrimaryKeyMessage(result.tableName)),
                )
            }

            is Step4Result.Failed -> call.respondHtml {
                wizardStep3View(
                    ctx, connectionName, table, configName,
                    Step3Content(emptyList(), result.reason),
                )
            }
        }
    }

    post("/syncs") {
        val form = call.receiveParameters()
        val connectionName = form["connection"]!!
        val tableName = form["table"]!!
        val configName = form["configName"]!!
        val schema = form["schema"]?.takeIf { it.isNotBlank() }
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

        val set = TableConfigSet(
            server = ServerConfig(port = port, bindAddress = "0.0.0.0", plaintext = true),
            jdbc = jdbc,  // SqliteConfigSource なら saveTableSetWithRef を使うがここでは shared を inline 保存
            table = TableConfig(
                name = tableName,
                schema = schema,
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

/**
 * step4 の内容を算出する。主キーが無い場合は例外ではなく [Step4Result.NoPrimaryKey] を返す。
 */
private fun computeStep4(
    jdbc: JdbcConfig,
    connectionName: String,
    tableName: String,
    schema: String?,
    selectedColumns: List<String>,
): Step4Result = JdbcConnectionProvider(jdbc, oauthCacheKey = connectionName).use { provider ->
    provider.connection().use { conn ->
        val inspector = JdbcMetadataInspector(conn)
        val allColumns = inspector.listColumns(tableName, schema)
        val pk = inspector.findPrimaryKey(tableName, schema)
            ?: return@use Step4Result.NoPrimaryKey(tableName, allColumns)
        val maps = allColumns
            .filter { it.name in selectedColumns && it.name != pk.column }
            .map { col ->
                WizardMapping(
                    kintoneFieldId = toSnakeCase(col.name),
                    jdbcColumn = col.name,
                    columnType = FieldTypeSuggester.suggest(col.jdbcType).name,
                )
            }
        Step4Result.Ready(pk.column, FieldTypeSuggester.suggestRecordIdType(pk.jdbcType), maps)
    }
}

/**
 * 主キーが無い場合の案内。
 *
 * kintone の「外部システムのアプリ化」は `GetCapability` で `RecordIdType` を、
 * `GetSchema` で `RecordIdFieldDefinition` を要求する。どちらも必須メソッドなので、
 * レコード番号に使える列が無いテーブル・ビューは**読み取り専用であっても連携できない**。
 * 「主キーが見つかりません」だけでは、DB を直せばよいのか別のテーブルを選ぶのかが
 * 分からないため、理由と根拠を示す (Issue #64)。
 */
internal fun noPrimaryKeyMessage(tableName: String): String =
    "テーブル \"$tableName\" には主キーが定義されていないため、連携を作成できません。" +
        "kintone の「外部システムのアプリ化」はレコード番号を必須とするため、" +
        "レコード番号に使える列が無いテーブル・ビューは読み取り専用でも連携できません。" +
        "主キーを持つ別のテーブルを選び直してください。"
