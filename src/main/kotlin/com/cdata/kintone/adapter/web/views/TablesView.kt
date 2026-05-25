package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.small
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe

fun HTML.tablesListView(ctx: AppContext) {
    val tableNames = ctx.configSource.listTables()
    val active = ctx.runner.listActive().associateBy { it.tableName }

    layout(
        pageTitle = "Tables",
        mode = ctx.configSourceMode,
        activeCount = active.size,
        currentPath = "/tables",
    ) {
        h2 { +"Tables (${tableNames.size})" }
        p { a(href = "/tables/new", classes = "button") { +"+ New Table" } }

        if (tableNames.isEmpty()) {
            article {
                p { +"テーブルがまだ登録されていません。「+ New Table」から作成してください。" }
            }
        } else {
            table(classes = "striped") {
                thead {
                    tr {
                        th { +"Name" }
                        th { +"DB Table" }
                        th { +"JDBC" }
                        th { +"Status" }
                        th { +"Action" }
                    }
                }
                tbody {
                    tableNames.forEach { name ->
                        val set = runCatching { ctx.configSource.loadTableSet(name) }.getOrNull()
                        val isActive = active.containsKey(name)
                        tr {
                            attributes["data-table"] = name
                            td { a(href = "/tables/$name") { +name } }
                            td { +(set?.table?.name ?: "-") }
                            td { +driverDescription(set) }
                            td(classes = "status") {
                                statusBadge(isActive, active[name]?.port)
                            }
                            td {
                                tableActions(name, isActive)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.tableDetailView(ctx: AppContext, name: String, set: TableConfigSet) {
    val active = ctx.runner.listActive().firstOrNull { it.tableName == name }
    val agentConfig = ctx.agentConfigManager.load(name)

    layout(
        pageTitle = name,
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/tables",
    ) {
        h2 {
            +"$name "
            statusBadge(active != null, active?.port)
        }
        div(classes = "action-bar") {
            tableActions(name, active != null)
            a(href = "/tables/$name/edit", classes = "button secondary") { +"Edit" }
            form(action = "/tables/$name/delete", method = FormMethod.post, classes = "inline-form") {
                attributes["onsubmit"] = "return confirm('テーブル \"$name\" を削除しますか？稼働中なら自動停止します。')"
                button(type = ButtonType.submit, classes = "secondary outline") { +"Delete" }
            }
        }

        section {
            h3 { +"Server" }
            ul {
                li { +"Port: "; code { +set.server.port.toString() } }
                li { +"Bind: "; code { +set.server.bindAddress } }
                li { +"Plaintext: "; code { +set.server.plaintext.toString() } }
            }
        }
        section {
            h3 { +"JDBC" }
            ul {
                li { +"Driver: "; code { +set.jdbc.driverClass } }
                li { +"Driver JAR: "; code { +set.jdbc.driverJar } }
                li { +"URL (masked): "; code { +maskUrl(set.jdbc.url) } }
                li { +"Pool size: "; code { +set.jdbc.pool.maximumPoolSize.toString() } }
            }
        }
        section {
            h3 { +"Table" }
            p { +"DB Table: "; code { +set.table.name } }
            p {
                +"Primary Key: "
                code { +set.table.primaryKey.kintoneFieldId }
                +" ↔ "
                code { +set.table.primaryKey.jdbcColumn }
            }
            h4 { +"Columns (${set.table.columns.size})" }
            table(classes = "striped") {
                thead {
                    tr {
                        th { +"kintone field" }
                        th { +"JDBC column" }
                        th { +"Type" }
                        th { +"Options" }
                    }
                }
                tbody {
                    set.table.columns.forEach { col ->
                        tr {
                            td { code { +col.kintoneFieldId } }
                            td { code { +col.jdbcColumn } }
                            td { +col.type.name }
                            td {
                                col.options?.let { +"${it.size} options" }
                            }
                        }
                    }
                }
            }
        }
        section {
            h3 { +"Capability" }
            ul {
                li { +"Record ID Type: "; code { +set.capability.recordIdType.name } }
                li { +"Count Strategy: "; code { +set.capability.countStrategy.name } }
                li {
                    +"CRUD: "
                    code {
                        val cap = set.capability
                        +"select=${cap.selectSupported} insert=${cap.insertSupported} update=${cap.updateSupported} delete=${cap.deleteSupported}"
                    }
                }
                li { +"Filterable: "; code { +set.capability.filterableFields.joinToString(", ") } }
                li { +"Sortable: "; code { +set.capability.sortableFields.joinToString(", ") } }
            }
        }
        agentSection(ctx, name, set, agentConfig)
    }
}

private fun kotlinx.html.FlowContent.agentSection(
    ctx: AppContext,
    name: String,
    set: com.cdata.kintone.adapter.config.TableConfigSet,
    agentConfig: com.cdata.kintone.adapter.agent.AgentConfig?,
) {
    section {
        h3 { +"kintone Agent" }
        p {
            small {
                +"このテーブルに対応する kintone Agent コンテナの設定 ("
                code { +ctx.agentConfigManager.pathFor(name).toString() }
                +") を編集します。kintone でコネクタを登録して取得した JWT トークンをここに設定してください。"
            }
        }

        val portForAgent = if (set.server.port == 0) "<dynamic>" else set.server.port.toString()
        val defaultAdapterAddr = "host.docker.internal:$portForAgent"

        form(action = "/tables/$name/agent", method = FormMethod.post) {
            label {
                +"Token (kintone Connector で発行された JWT):"
                textArea {
                    this.name = "token"
                    rows = "3"
                    required = true
                    placeholder = "eyJhbGciOi..."
                    +(agentConfig?.token ?: "")
                }
            }
            label {
                +"Adapter Address (Agent コンテナから見た Adapter の host:port):"
                input(type = kotlinx.html.InputType.text) {
                    this.name = "adapter_addr"
                    required = true
                    value = agentConfig?.adapterAddr ?: defaultAdapterAddr
                }
            }
            label {
                input(type = kotlinx.html.InputType.checkBox) {
                    this.name = "adapter_plaintext"
                    checked = agentConfig?.adapterPlaintext ?: set.server.plaintext
                }
                +" Adapter plaintext (TLS なし)"
            }
            label {
                +"Private Key Path (コンテナ内):"
                input(type = kotlinx.html.InputType.text) {
                    this.name = "private_key_path"
                    value = agentConfig?.privateKeyPath ?: "/opt/agent/private-key.pem"
                }
            }
            div(classes = "action-bar") {
                button(type = ButtonType.submit, classes = "primary") { +"Save agent.json" }
                if (agentConfig != null) {
                    form(action = "/tables/$name/agent/delete", method = FormMethod.post, classes = "inline-form") {
                        attributes["onsubmit"] = "return confirm('agent.json を削除しますか？')"
                        button(type = ButtonType.submit, classes = "secondary outline") { +"Delete agent.json" }
                    }
                }
            }
        }

        details {
            summary { +"docker-compose スニペット (agent/docker-compose.multi.yml に追記)" }
            code(classes = "stdout-debug") { +ctx.agentConfigManager.composeSnippet(name) }
        }

        if (agentConfig != null) {
            p {
                small {
                    +"✅ agent.json 保存済み: "
                    code { +ctx.agentConfigManager.pathFor(name).toString() }
                    +" — docker compose で kintone-agent-$name を (再) 起動してください。"
                }
            }
        } else {
            p {
                small { +"⚠ agent.json 未設定。kintone との接続には設定が必要です。" }
            }
        }
    }
}

fun HTML.tableEditView(ctx: AppContext, name: String, set: TableConfigSet) {
    layout(
        pageTitle = "Edit $name",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/tables",
    ) {
        h2 { +"Edit table: $name" }
        form(action = "/tables/$name", method = FormMethod.post) {
            // Ktor 標準は GET/POST のみ。実装は POST + _method=put で扱うか、POST のみで素直に
            input(type = kotlinx.html.InputType.hidden, name = "_method") { value = "put" }

            section {
                h3 { +"Server" }
                label {
                    +"Port (number, 0/auto で自動割り当て): "
                    input(type = kotlinx.html.InputType.text, name = "server.port") {
                        value = set.server.port.toString()
                    }
                }
                label {
                    +"Bind address: "
                    input(type = kotlinx.html.InputType.text, name = "server.bindAddress") {
                        value = set.server.bindAddress
                    }
                }
                label {
                    input(type = kotlinx.html.InputType.checkBox, name = "server.plaintext") {
                        checked = set.server.plaintext
                    }
                    +" Plaintext (TLS なし)"
                }
            }

            section {
                h3 { +"JDBC" }
                p {
                    small {
                        +"※ Phase 2-B では Web UI からの JDBC URL 直接編集のみサポート。"
                        +" sys_connection_props ベースの動的フォームは Connections 画面で利用してください。"
                    }
                }
                label {
                    +"Driver class: "
                    input(type = kotlinx.html.InputType.text, name = "jdbc.driverClass") {
                        value = set.jdbc.driverClass
                    }
                }
                label {
                    +"Driver JAR: "
                    input(type = kotlinx.html.InputType.text, name = "jdbc.driverJar") {
                        value = set.jdbc.driverJar
                    }
                }
                label {
                    +"URL: "
                    input(type = kotlinx.html.InputType.text, name = "jdbc.url") {
                        value = set.jdbc.url
                    }
                }
            }

            section {
                h3 { +"Capability" }
                label {
                    +"Record ID Type: "
                    input(type = kotlinx.html.InputType.text, name = "capability.recordIdType") {
                        value = set.capability.recordIdType.name
                    }
                }
                label {
                    +"Filterable fields (カンマ区切り): "
                    input(type = kotlinx.html.InputType.text, name = "capability.filterableFields") {
                        value = set.capability.filterableFields.joinToString(",")
                    }
                }
                label {
                    +"Sortable fields (カンマ区切り): "
                    input(type = kotlinx.html.InputType.text, name = "capability.sortableFields") {
                        value = set.capability.sortableFields.joinToString(",")
                    }
                }
            }

            div(classes = "action-bar") {
                a(href = "/tables/$name", classes = "button secondary") { +"Cancel" }
                button(type = ButtonType.submit, name = "action", classes = "primary") {
                    value = "save"
                    +"Save"
                }
                button(type = ButtonType.submit, name = "action") {
                    value = "save-and-restart"
                    +"Save & Restart"
                }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.tableActions(name: String, isActive: Boolean) {
    if (isActive) {
        form(action = "/tables/$name/stop", method = FormMethod.post, classes = "inline-form") {
            button(type = ButtonType.submit, classes = "secondary") { +"Stop" }
        }
    } else {
        form(action = "/tables/$name/start", method = FormMethod.post, classes = "inline-form") {
            button(type = ButtonType.submit) { +"Start" }
        }
    }
}

private fun kotlinx.html.FlowContent.statusBadge(active: Boolean, port: Int?) {
    if (active) {
        small(classes = "status-badge serving") {
            +"●Running"
            port?.let { +" on $it" }
        }
    } else {
        small(classes = "status-badge stopped") { +"○Stopped" }
    }
}

private fun driverDescription(set: TableConfigSet?): String {
    if (set == null) return "(load error)"
    val cls = set.jdbc.driverClass
    return cls.substringAfter("cdata.jdbc.").substringBefore('.')
}

private fun maskUrl(url: String): String {
    return url
        .replace(Regex("(?i)(password=)([^;]*)"), "\$1***")
        .replace(Regex("(?i)(securitytoken=)([^;]*)"), "\$1***")
        .replace(Regex("(?i)(oauthclientsecret=)([^;]*)"), "\$1***")
}
