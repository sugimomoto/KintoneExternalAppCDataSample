package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.jdbc.ConnectionPropertiesResult
import com.cdata.kintone.adapter.jdbc.ConnectionProperty
import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import com.cdata.kintone.adapter.jdbc.PropertyHierarchyResolver
import com.cdata.kintone.adapter.jdbc.PropertySource
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
import kotlinx.html.classes
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
                    attributes["hx-post"] = "/connections/properties"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-target"] = "#properties-form-container"
                    attributes["hx-include"] = "closest form"
                    // 別ドライバーの入力値を引き継がない
                    attributes["hx-vals"] = """{"prefill": "false"}"""
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

            // 動的プロパティフォーム挿入箇所。
            // 認証方式など条件に使われる入力欄が変わると、その入力欄自身が
            // ここを差し替える (propertyFieldTargets を参照)。
            div {
                attributes["id"] = "properties-form-container"
                if (existing != null) {
                    val driverInfo = drivers.firstOrNull { it.driverClass == existing.driverClass }
                    if (driverInfo != null) {
                        val result = ctx.connectionPropertyInspector.fetchProperties(
                            existing.driverClass, driverInfo.filename,
                        )
                        propertiesFormContent(result, existingValuesOf(existing))
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
 * 接続プロパティの取得結果から動的フォームをレンダリングする。
 * ドライバー選択時に HTMX で `/connections/properties` が呼ばれ、このフラグメントが返る。
 *
 * ドライバーによっては `sys_connection_props` が取得できず、
 * `Driver.getPropertyInfo` 由来の縮退結果になる。取得経路ごとに案内を出し分ける。
 */
fun kotlinx.html.FlowContent.propertiesFormContent(
    result: ConnectionPropertiesResult,
    existingValues: Map<String, String> = emptyMap(),
) {
    noticeFor(result.source)?.let { notice ->
        article(classes = "warning-banner") { p { +notice } }
    }

    if (result.properties.isEmpty()) {
        manualUrlField(existingValues)
        return
    }

    propertyCategories(result.properties, existingValues)

    // 縮退時は取得漏れのプロパティを補えるよう、直接入力も併記する。
    if (result.isDegraded) {
        manualUrlField(existingValues)
    }
}

/**
 * 取得経路に応じた案内文。完全に取得できた場合は案内を出さない。
 *
 * 従来は取得に失敗した理由を区別できず、正規の CData ドライバーに対しても
 * 「CData ドライバーではない可能性があります」と表示していた (Issue #15)。
 */
private fun noticeFor(source: PropertySource): String? = when (source) {
    PropertySource.SYS_CONNECTION_PROPS -> null

    PropertySource.DRIVER_PROPERTY_INFO ->
        "ドライバーから完全なプロパティ定義を取得できませんでした。簡易フォームを表示しています " +
            "(カテゴリー分類と選択肢は利用できません)。不足するプロパティは JDBC 接続文字列に直接記述してください。"

    PropertySource.NONE_NOT_CDATA_DRIVER ->
        "CData JDBC Driver ではないため、プロパティフォームを生成できません。" +
            "JDBC 接続文字列を直接入力してください。"

    PropertySource.NONE_JAR_MISSING ->
        "ドライバーの JAR が見つかりません。ドライバー画面で配置状況を確認してください。"

    PropertySource.NONE_FETCH_FAILED ->
        "プロパティの取得に失敗しました。詳細はログを確認してください。" +
            "JDBC 接続文字列を直接入力すればデータソース接続は作成できます。"
}

/** プロパティフォームを生成できない / 補完が必要なときのフォールバック入力欄。 */
private fun kotlinx.html.FlowContent.manualUrlField(existingValues: Map<String, String>) {
    label {
        +"JDBC 接続文字列 (直接入力):"
        textArea {
            name = "jdbc.url.manual"
            rows = "3"
            +(existingValues["__url__"] ?: "")
        }
    }
}

private fun kotlinx.html.FlowContent.propertyCategories(
    props: List<ConnectionProperty>,
    existingValues: Map<String, String>,
) {
    // 認証方式などの条件 (Hierarchy) で、いま意味を持つプロパティだけに絞る。
    // 依存先が非表示のものも参照できるよう、解決には全件を渡す。
    val resolved = PropertyHierarchyResolver.resolve(props, existingValues)
    val dependencies = PropertyHierarchyResolver.dependencyNames(props).map { it.lowercase() }.toSet()

    val visibleProps = resolved.filter { it.visible && it.propertyName.isNotBlank() }
    val hiddenByCondition = props.count { it.visible && it.propertyName.isNotBlank() } - visibleProps.size
    val byCategory = visibleProps.groupBy { it.category.ifBlank { "Other" } }

    p {
        small {
            +"全 ${props.size} プロパティ、表示中 ${visibleProps.size} 件"
            if (hiddenByCondition > 0) {
                +" (認証方式などの条件で ${hiddenByCondition} 件を非表示)"
            }
            +"。 必須プロパティを含むカテゴリーを展開しています。その他はカテゴリーを開いて確認してください。"
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
        // 必須プロパティが折りたたみの中に隠れないようにする。縮退フォームは
        // カテゴリを持たないため、この規則がないと必須項目が初期表示されない。
        val expanded = category == "Authentication" || catProps.any { it.required }
        if (expanded) {
            section {
                h3 { +categoryLabel(category) }
                catProps.forEach {
                    propertyField(it, existingValues[it.propertyName], it.propertyName.lowercase() in dependencies)
                }
            }
        } else {
            details {
                summary {
                    +"${categoryLabel(category)} (${catProps.size} 件)"
                }
                catProps.forEach {
                    propertyField(it, existingValues[it.propertyName], it.propertyName.lowercase() in dependencies)
                }
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

/**
 * @param isDependency 他プロパティの条件から参照されているか。
 *   true のとき入力欄に目印を付け、変更時にフォームを再描画させる。
 */
private fun kotlinx.html.FlowContent.propertyField(
    prop: ConnectionProperty,
    currentValue: String?,
    isDependency: Boolean = false,
) {
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
                    propertyFieldTargets(isDependency, "change")
                    checked = value.equals("true", ignoreCase = true)
                }
            }
            prop.type == PropertyType.INT -> {
                input(type = InputType.number, name = "prop.${prop.propertyName}") {
                    propertyFieldTargets(isDependency, "keyup changed delay:200ms")
                    this.value = value
                }
            }
            prop.allowedValues.isNotEmpty() -> {
                select {
                    propertyFieldTargets(isDependency, "change")
                    name = "prop.${prop.propertyName}"
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
                    propertyFieldTargets(isDependency, "keyup changed delay:200ms")
                    this.value = value
                }
            }
            else -> {
                input(type = InputType.text, name = "prop.${prop.propertyName}") {
                    propertyFieldTargets(isDependency, "keyup changed delay:200ms")
                    this.value = value
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
 * プロパティ入力欄の htmx 設定。
 *
 * 認証方式など**他プロパティの条件に使われる入力欄**は、変更されたら
 * フォーム全体を作り直す。必須・表示がその値で変わるため。
 * それ以外は従来どおり接続文字列プレビューだけを更新する。
 *
 * トリガーをコンテナ側に `hx-trigger="change from:.property-dependency"` として
 * 置く方法は使えない。htmx 1.x はこのセレクタを要素の初期化時にしか解決せず、
 * 後から差し込まれた入力欄を拾わないため。
 */
private fun kotlinx.html.CommonAttributeGroupFacade.propertyFieldTargets(isDependency: Boolean, trigger: String) {
    attributes["hx-trigger"] = trigger
    attributes["hx-include"] = "closest form"
    if (isDependency) {
        classes = setOf("property-dependency")
        attributes["hx-post"] = "/connections/properties"
        attributes["hx-target"] = "#properties-form-container"
    } else {
        attributes["hx-post"] = "/connections/preview-url"
        attributes["hx-target"] = "#url-preview-container"
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
