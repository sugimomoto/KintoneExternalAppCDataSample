package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
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
        pageTitle = "データソース",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/connections",
    ) {
        div(classes = "action-bar") {
            h2 { +"データソース接続 (${names.size} 件)" }
            a(href = "/connections/new", classes = "button") { +"+ 新しいデータソース接続" }
        }

        if (names.isEmpty()) {
            article {
                p { +"データソース接続がまだ登録されていません。" }
            }
        } else {
            div(classes = "table-scroll") {
                table(classes = "striped") {
                    thead {
                        tr {
                            th { +"名前" }
                            th { +"ドライバークラス" }
                            th { +"接続文字列 (マスク済み)" }
                            th { +"操作" }
                        }
                    }
                    tbody {
                        names.forEach { name ->
                            val config = ctx.configSource.loadSharedJdbcConfig(name)
                            tr {
                                td { a(href = "/connections/$name") { +name } }
                                td { code { +(config?.driverClass ?: "-") } }
                                // 全文は title で参照する。マスク済みの値のみを入れること
                                // (生の接続文字列を入れると DOM に平文の資格情報が載る)。
                                val masked = ConnectionStringMasker.mask(config?.url ?: "")
                                td(classes = "cell-truncate") {
                                    attributes["title"] = masked
                                    code { +masked }
                                }
                                td(classes = "cell-actions") {
                                    form(
                                        action = "/connections/$name/test",
                                        method = FormMethod.post,
                                        classes = "inline-form",
                                    ) {
                                        button(type = ButtonType.submit, classes = "secondary outline") { +"接続テスト" }
                                    }
                                    a(href = "/connections/$name/edit", classes = "button secondary") { +"編集" }
                                }
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
    val pageTitle = if (editMode) "データソース接続を編集: $existingName" else "新しいデータソース接続"
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
                    +"⚠ JDBC ドライバーが見つかりません。"
                    a(href = "/drivers") { +"ドライバー画面" }
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
                +"名前 (識別子、英数小文字):"
                input(type = InputType.text, name = "name") {
                    required = true
                    value = existingName ?: ""
                    if (editMode) readonly = true
                }
            }

            label {
                +"JDBC ドライバー:"
                select {
                    name = "driver"
                    attributes["hx-get"] = "/connections/properties"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-target"] = "#properties-form-container"
                    attributes["hx-include"] = "this"
                    attributes["hx-vals"] = "js:{prefill: false}"
                    option {
                        value = ""
                        +"-- ドライバーを選択 --"
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
                h3 { +"接続プール設定" }
                label {
                    +"最大接続数:"
                    input(type = InputType.number, name = "pool.maximumPoolSize") {
                        value = (existing?.pool?.maximumPoolSize ?: 10).toString()
                    }
                }
                label {
                    +"接続タイムアウト (ミリ秒):"
                    input(type = InputType.number, name = "pool.connectionTimeout") {
                        value = (existing?.pool?.connectionTimeout ?: 30000).toString()
                    }
                }
            }

            div(classes = "action-bar") {
                a(href = "/connections", classes = "button secondary outline") { +"キャンセル" }
                button(type = ButtonType.submit) { +"保存" }
            }
        }

        // URL Preview (下部 sticky)
        div(classes = "url-preview-sticky") {
            attributes["id"] = "url-preview-container"
            if (existing != null) {
                p {
                    // 生の接続文字列を DOM に載せない (Issue #16)。
                    code { +ConnectionStringMasker.mask(existing.url) }
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
            +"JDBC 接続文字列 (直接入力):"
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
            +" 必須かつ表示対象のものだけ展開しています。その他はカテゴリを開いて確認してください。"
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
                h3 { +categoryLabel(category) }
                catProps.forEach { propertyField(it, existingValues[it.propertyName]) }
            }
        } else {
            details {
                summary {
                    +"${categoryLabel(category)} (${catProps.size} 件)"
                }
                catProps.forEach { propertyField(it, existingValues[it.propertyName]) }
            }
        }
    }
}

/**
 * 接続プロパティのカテゴリ名を表示用の日本語に変換する。
 *
 * キーは CData ドライバの `sys_connection_props` が返す生の Category 値。
 * 同梱の 4 ドライバー (bcart / googlesheets / salesforce / sapgateway) が返す
 * 全カテゴリを網羅している。
 *
 * `OAuth` / `JWT OAuth` / `SSL` / `SSO` / `BulkAPI` は固有名詞・略語なので**意図的に翻訳しない**。
 * 未知のカテゴリも翻訳せずそのまま返すため、ドライバー更新で新カテゴリが増えても
 * 表示が欠落しない。
 *
 * 並び順 (`orderedCategories`) と `isAuth` 判定は生値で行うため、本関数は表示専用。
 */
private fun categoryLabel(category: String): String = when (category) {
    "Authentication" -> "認証"
    "Connection" -> "接続"
    "Caching" -> "キャッシュ"
    "Firewall" -> "ファイアウォール"
    "Logging" -> "ログ"
    "Proxy" -> "プロキシ"
    "Schema" -> "スキーマ"
    "Other" -> "その他"
    else -> category
}

private fun kotlinx.html.FlowContent.propertyField(prop: ConnectionProperty, currentValue: String?) {
    val value = currentValue ?: prop.defaultValue ?: ""
    label(classes = "property-row") {
        div {
            +prop.displayName
            if (prop.required) span(classes = "required") { +" *" }
            when (prop.sensitivity) {
                Sensitivity.PASSWORD -> span(classes = "sensitivity") { +" パスワード" }
                Sensitivity.SENSITIVE -> span(classes = "sensitivity") { +" 機微情報" }
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
