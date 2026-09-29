package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.jdbc.ConnectionProperty
import com.cdata.kintone.adapter.jdbc.PropertyType
import com.cdata.kintone.adapter.jdbc.Sensitivity
import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.textArea
import kotlinx.html.tr

fun HTML.connectionsListView(ctx: AppContext) {
    val names = ctx.configSource.listSharedJdbcConfigs()
    layout(
        pageTitle = "Connections",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/connections",
    ) {
        h2 { +"JDBC Connections (${names.size})" }
        p { a(href = "/connections/new", classes = "button") { +"+ New Connection" } }

        if (names.isEmpty()) {
            article {
                p { +"共通 JDBC 接続がまだ登録されていません。" }
            }
        } else {
            table(classes = "striped") {
                thead {
                    tr {
                        th { +"Name" }
                        th { +"Driver" }
                        th { +"URL (masked)" }
                        th { +"Action" }
                    }
                }
                tbody {
                    names.forEach { name ->
                        val config = ctx.configSource.loadSharedJdbcConfig(name)
                        tr {
                            td { a(href = "/connections/$name") { +name } }
                            td { code { +(config?.driverClass ?: "-") } }
                            td { code { +maskUrlGeneric(config?.url ?: "") } }
                            td {
                                form(action = "/connections/$name/test", method = FormMethod.post, classes = "inline-form") {
                                    button(type = ButtonType.submit, classes = "secondary outline") { +"Test" }
                                }
                                a(href = "/connections/$name/edit", classes = "button secondary outline") { +"Edit" }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.connectionFormView(
    ctx: AppContext,
    editMode: Boolean,
    existingName: String? = null,
    existing: JdbcConfig? = null,
) {
    val pageTitle = if (editMode) "Edit Connection: $existingName" else "New Connection"
    layout(
        pageTitle = pageTitle,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/connections",
    ) {
        h2 { +pageTitle }

        // 利用可能ドライバ一覧
        val drivers = ctx.driverManager.listDrivers().filter { it.driverClass != null }

        if (drivers.isEmpty()) {
            article(classes = "warning-banner") {
                p {
                    +"⚠ JDBC Driver が見つかりません。"
                    a(href = "/drivers") { +" Drivers 画面" }
                    +" で JAR をアップロードしてください。"
                }
            }
            return@layout
        }

        val postAction = if (editMode) "/connections/$existingName" else "/connections"
        form(action = postAction, method = FormMethod.post, classes = "connection-form") {
            attributes["id"] = "connection-form"
            attributes["hx-target"] = "#url-preview-container"

            label {
                +"Name (識別子, 英数小文字):"
                input(type = InputType.text, name = "name") {
                    required = true
                    value = existingName ?: ""
                    if (editMode) readonly = true
                }
            }

            label {
                +"JDBC Driver:"
                select {
                    name = "driver"
                    attributes["hx-get"] = "/connections/properties"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-target"] = "#properties-form-container"
                    attributes["hx-include"] = "this"
                    attributes["hx-vals"] = "js:{prefill: false}"
                    option {
                        value = ""
                        +"-- ドライバを選択 --"
                    }
                    drivers.forEach { d ->
                        option {
                            value = "${d.driverClass}|${d.filename}"
                            if (existing?.driverClass == d.driverClass) selected = true
                            +"${d.driverClass} (${d.filename})"
                        }
                    }
                }
            }

            // 動的プロパティフォーム挿入箇所
            div {
                attributes["id"] = "properties-form-container"
                if (existing != null) {
                    val driverInfo = drivers.firstOrNull { it.driverClass == existing.driverClass }
                    if (driverInfo != null) {
                        val props = ctx.connectionPropertyInspector.listProperties(
                            existing.driverClass, driverInfo.filename,
                        )
                        propertiesFormContent(props, existingValuesOf(existing))
                    }
                }
            }

            // Pool
            section {
                h3 { +"Pool Settings" }
                label {
                    +"Pool size: "
                    input(type = InputType.number, name = "pool.maximumPoolSize") {
                        value = (existing?.pool?.maximumPoolSize ?: 10).toString()
                    }
                }
                label {
                    +"Connection timeout (ms): "
                    input(type = InputType.number, name = "pool.connectionTimeout") {
                        value = (existing?.pool?.connectionTimeout ?: 30000).toString()
                    }
                }
            }

            div(classes = "action-bar") {
                a(href = "/connections", classes = "button secondary") { +"Cancel" }
                button(type = ButtonType.submit, classes = "primary") { +"Save" }
            }
        }

        // URL Preview (下部 sticky)
        div(classes = "url-preview-sticky") {
            attributes["id"] = "url-preview-container"
            if (existing != null) {
                p {
                    code { +existing.url }
                }
            } else {
                p { small { +"(ドライバ選択後にプレビューが表示されます)" } }
            }
        }
    }
}

/**
 * sys_connection_props 結果から動的フォームをレンダリング。
 * Web UI のドライバ選択時に HTMX で /connections/properties が呼ばれて
 * このフラグメントが返される。
 */
fun kotlinx.html.FlowContent.propertiesFormContent(
    props: List<ConnectionProperty>,
    existingValues: Map<String, String> = emptyMap(),
) {
    if (props.isEmpty()) {
        article(classes = "warning-banner") {
            p { +"プロパティ取得に失敗しました。CData ドライバではない可能性があります。" }
        }
        // フォールバック: URL 直接入力
        label {
            +"JDBC URL (直接入力): "
            textArea {
                name = "jdbc.url.manual"
                rows = "3"
                +(existingValues["__url__"] ?: "")
            }
        }
        return
    }

    val visibleProps = props.filter { it.visible && it.propertyName.isNotBlank() }
    val byCategory = visibleProps.groupBy { it.category.ifBlank { "Other" } }

    p {
        small {
            +"全 ${props.size} プロパティ、表示中 ${visibleProps.size} 件。"
            +" Required + Visible のみ展開、その他はカテゴリを開いて確認してください。"
        }
    }

    // カテゴリ順 (Authentication を先頭に)
    val orderedCategories = byCategory.keys.sortedBy {
        when (it) {
            "Authentication" -> 0
            "Connection" -> 1
            "OAuth" -> 2
            else -> 99
        }
    }

    orderedCategories.forEach { category ->
        val catProps = byCategory[category] ?: return@forEach
        val isAuth = category == "Authentication"
        if (isAuth) {
            section {
                h3 { +category }
                catProps.forEach { propertyField(it, existingValues[it.propertyName]) }
            }
        } else {
            details {
                summary {
                    +"$category (${catProps.size} props)"
                }
                catProps.forEach { propertyField(it, existingValues[it.propertyName]) }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.propertyField(prop: ConnectionProperty, currentValue: String?) {
    val value = currentValue ?: prop.defaultValue ?: ""
    label(classes = "property-row") {
        div {
            +prop.displayName
            if (prop.required) span(classes = "required") { +" *" }
            when (prop.sensitivity) {
                Sensitivity.PASSWORD -> span(classes = "sensitivity") { +" 🔐PASSWORD" }
                Sensitivity.SENSITIVE -> span(classes = "sensitivity") { +" 🔐" }
                Sensitivity.NONE -> {}
            }
        }
        when {
            prop.type == PropertyType.BOOLEAN -> {
                input(type = InputType.checkBox, name = "prop.${prop.propertyName}") {
                    checked = value.equals("true", ignoreCase = true)
                }
            }
            prop.type == PropertyType.INT -> {
                input(type = InputType.number, name = "prop.${prop.propertyName}") {
                    this.value = value
                    attributes["hx-trigger"] = "keyup changed delay:200ms"
                    attributes["hx-post"] = "/connections/preview-url"
                    attributes["hx-target"] = "#url-preview-container"
                    attributes["hx-include"] = "closest form"
                }
            }
            prop.allowedValues.isNotEmpty() -> {
                select {
                    name = "prop.${prop.propertyName}"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-post"] = "/connections/preview-url"
                    attributes["hx-target"] = "#url-preview-container"
                    attributes["hx-include"] = "closest form"
                    option {
                        this.value = ""
                        +"-- 未指定 --"
                    }
                    prop.allowedValues.forEach { v ->
                        option {
                            this.value = v
                            if (v == value) selected = true
                            +v
                        }
                    }
                }
            }
            prop.sensitivity != Sensitivity.NONE -> {
                input(type = InputType.password, name = "prop.${prop.propertyName}") {
                    this.value = value
                    attributes["hx-trigger"] = "keyup changed delay:200ms"
                    attributes["hx-post"] = "/connections/preview-url"
                    attributes["hx-target"] = "#url-preview-container"
                    attributes["hx-include"] = "closest form"
                }
            }
            else -> {
                input(type = InputType.text, name = "prop.${prop.propertyName}") {
                    this.value = value
                    attributes["hx-trigger"] = "keyup changed delay:200ms"
                    attributes["hx-post"] = "/connections/preview-url"
                    attributes["hx-target"] = "#url-preview-container"
                    attributes["hx-include"] = "closest form"
                }
            }
        }
        small(classes = "description") { +prop.shortDescription }
        if (prop.hierarchy.isNotBlank()) {
            small(classes = "hierarchy-hint") { +"ⓘ ${prop.hierarchy} の条件で有効" }
        }
    }
}

/**
 * 既存 JDBC URL からプロパティ名→値のマップを復元（編集画面用）。
 * `jdbc:salesforce:User=u;Password=p;` → {User=u, Password=p}
 */
fun existingValuesOf(config: JdbcConfig): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val body = config.url.substringAfter(":").substringAfter(":")
    body.split(";").forEach { pair ->
        val (k, v) = pair.split("=", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return@forEach
        }
        map[k.trim()] = v
    }
    map["__url__"] = config.url
    return map
}

fun maskUrlGeneric(url: String): String =
    url.replace(Regex("(?i)(password|securitytoken|oauthclientsecret|oauthaccesstoken|oauthrefreshtoken)=([^;]*)"), "$1=***")
