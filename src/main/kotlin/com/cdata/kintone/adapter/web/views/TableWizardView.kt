package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.metadata.ColumnInfo
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.cdata.kintone.adapter.metadata.TableInfo
import com.cdata.kintone.adapter.runtime.PortAllocator
import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.small
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

private fun kotlinx.html.FlowContent.wizardSteps(current: Int) {
    div(classes = "wizard-steps") {
        +"Step $current of 4: "
        +listOf("Conn", "Table", "Cols", "Map")[current - 1]
    }
}

fun HTML.wizardStep1View(ctx: AppContext) {
    val connections = ctx.configSource.listSharedJdbcConfigs()
    layout(
        pageTitle = "New Table — Step 1",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 1 of 4: Select Connection" }
        wizardSteps(1)

        if (connections.isEmpty()) {
            article(classes = "warning-banner") {
                p {
                    +"⚠ JDBC Connection が登録されていません。"
                    a(href = "/connections/new") { +" Connection を新規作成" }
                    +" してから戻ってきてください。"
                }
            }
            return@layout
        }

        form(action = "/syncs/new/step2", method = FormMethod.get) {
            connections.forEach { conn ->
                label {
                    input(type = InputType.radio, name = "connection") {
                        value = conn
                        required = true
                    }
                    +" $conn"
                }
            }
            div(classes = "action-bar") {
                a(href = "/syncs", classes = "button secondary") { +"Cancel" }
                button(type = ButtonType.submit) { +"Next →" }
            }
        }
    }
}

fun HTML.wizardStep2View(ctx: AppContext, connectionName: String, tables: List<TableInfo>) {
    layout(
        pageTitle = "New Table — Step 2",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 2 of 4: Select Table" }
        wizardSteps(2)
        p { +"Via connection: "; code { +connectionName } }

        form(action = "/syncs/new/step3", method = FormMethod.get) {
            input(type = InputType.hidden, name = "connection") { value = connectionName }

            p { +"Found ${tables.size} tables (showing first 100)" }
            div(classes = "scrollable-list") {
                tables.take(100).forEach { t ->
                    val tableLabel = (if (t.schema != null) "${t.schema}." else "") + t.name
                    label {
                        input(type = InputType.radio, name = "table") {
                            value = tableLabel
                            required = true
                        }
                        +" $tableLabel"
                    }
                }
            }
            label {
                +"Config name (短い識別子、英数小文字): "
                input(type = InputType.text, name = "configName") {
                    required = true
                    placeholder = "例: account / contact / gs-orders"
                }
            }
            div(classes = "action-bar") {
                a(href = "/syncs/new", classes = "button secondary") { +"← Back" }
                button(type = ButtonType.submit) { +"Next →" }
            }
        }
    }
}

fun HTML.wizardStep3View(
    ctx: AppContext,
    connectionName: String,
    tableName: String,
    configName: String,
    columns: List<ColumnInfo>,
) {
    layout(
        pageTitle = "New Table — Step 3",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 3 of 4: Select Columns" }
        wizardSteps(3)
        p {
            +"Via "; code { +connectionName }
            +" / "; code { +tableName }
        }

        form(action = "/syncs/new/step4", method = FormMethod.post) {
            input(type = InputType.hidden, name = "connection") { value = connectionName }
            input(type = InputType.hidden, name = "table") { value = tableName }
            input(type = InputType.hidden, name = "configName") { value = configName }

            p { +"${columns.size} columns. Select target columns to map." }
            table(classes = "striped") {
                thead {
                    tr {
                        th { +"☑" }
                        th { +"Column" }
                        th { +"JDBC Type" }
                    }
                }
                tbody {
                    columns.forEach { col ->
                        tr {
                            td {
                                input(type = InputType.checkBox, name = "selectedColumns") {
                                    value = col.name
                                    checked = true
                                }
                            }
                            td { code { +col.name } }
                            td { +col.typeName }
                        }
                    }
                }
            }

            div(classes = "action-bar") {
                a(href = "/syncs/new", classes = "button secondary") { +"← Back to Step 1" }
                button(type = ButtonType.submit) { +"Next →" }
            }
        }
    }
}

data class WizardMapping(
    val kintoneFieldId: String,
    val jdbcColumn: String,
    val columnType: String,
)

fun HTML.wizardStep4View(
    ctx: AppContext,
    connectionName: String,
    tableName: String,
    configName: String,
    primaryKey: String,
    recommendedRecordIdType: RecordIdType,
    mappings: List<WizardMapping>,
) {
    // 空きポートを提示する。使い切っている場合は空欄にして保存時にエラーを出す。
    val suggestedPort = runCatching { ctx.syncPortAllocator.allocate() }.getOrNull()
    layout(
        pageTitle = "New Table — Step 4",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 4 of 4: Mapping & Capability" }
        wizardSteps(4)

        form(action = "/syncs", method = FormMethod.post) {
            input(type = InputType.hidden, name = "connection") { value = connectionName }
            input(type = InputType.hidden, name = "table") { value = tableName }
            input(type = InputType.hidden, name = "configName") { value = configName }
            input(type = InputType.hidden, name = "primaryKeyColumn") { value = primaryKey }

            section {
                h3 { +"Mapping" }
                p {
                    +"Primary Key: kintone "
                    code { +"id" }
                    +" ↔ JDBC "
                    code { +primaryKey }
                }
                h4 { +"Columns (${mappings.size})" }
                table(classes = "striped") {
                    thead {
                        tr {
                            th { +"kintone field_id" }
                            th { +"JDBC column" }
                            th { +"Type" }
                        }
                    }
                    tbody {
                        mappings.forEachIndexed { i, m ->
                            tr {
                                td {
                                    input(type = InputType.text, name = "mapping[$i].kintoneFieldId") {
                                        value = m.kintoneFieldId
                                    }
                                }
                                td {
                                    input(type = InputType.text, name = "mapping[$i].jdbcColumn") {
                                        value = m.jdbcColumn
                                        readonly = true
                                    }
                                }
                                td {
                                    select { name = "mapping[$i].columnType"
                                        listOf("TEXT", "NUMBER", "DATETIME", "SELECTION").forEach { t ->
                                            option {
                                                value = t
                                                if (t == m.columnType) selected = true
                                                +t
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            section {
                h3 { +"Capability" }
                label {
                    +"Record ID Type: "
                    select { name = "recordIdType"
                        RecordIdType.entries.forEach { t ->
                            option {
                                value = t.name
                                if (t == recommendedRecordIdType) selected = true
                                +t.name
                            }
                        }
                    }
                }
                label {
                    // port: 0 (OS 任意ポート) は Docker の publish 範囲外を掴むため使わない (Issue #3)
                    +"Server Port (Docker 公開範囲 ${PortAllocator.publishedRangeLabel} / 空欄で自動採番): "
                    input(type = InputType.number, name = "port") {
                        value = suggestedPort?.toString() ?: ""
                    }
                }
                label {
                    +"Filterable fields (カンマ区切り, kintone field_id): "
                    input(type = InputType.text, name = "filterableFields") {
                        value = "id"
                    }
                }
                label {
                    +"Sortable fields (カンマ区切り): "
                    input(type = InputType.text, name = "sortableFields") {
                        value = "id"
                    }
                }
            }

            div(classes = "action-bar") {
                a(href = "/syncs/new", classes = "button secondary") { +"← 最初に戻る" }
                button(type = ButtonType.submit, name = "andStart", classes = "secondary") {
                    value = "false"
                    +"保存のみ"
                }
                button(type = ButtonType.submit, name = "andStart") {
                    value = "true"
                    +"保存して起動"
                }
                button(type = ButtonType.submit, name = "andConnect", classes = "primary") {
                    value = "true"
                    +"保存して kintone と接続 ▶"
                }
            }
        }
    }
}
