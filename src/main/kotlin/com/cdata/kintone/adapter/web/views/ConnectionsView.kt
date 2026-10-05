package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.jdbc.ConnectionPropertiesResult
import com.cdata.kintone.adapter.jdbc.ConnectionProperty
import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import com.cdata.kintone.adapter.jdbc.OAuthCapability
import com.cdata.kintone.adapter.jdbc.QueryPassthroughAdvice
import com.cdata.kintone.adapter.jdbc.authSchemeDefaultOf
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
import kotlinx.html.li
import kotlinx.html.ul
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.textArea
import kotlinx.html.tr

fun HTML.connectionsListView(ctx: AppContext, blockedDelete: BlockedDelete? = null) {
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

        blockedDelete?.let { blocked -> blockedDeleteNotice(blocked) }

        // 接続テスト結果の差し替え先。テーブルの外に置く。操作列に入れると
        // 長いメッセージでレイアウトが崩れる (Issue #42)。
        div { attributes["id"] = CONNECTION_TEST_RESULT_ID }

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
                            // 行単位で失敗を閉じ込める。1 行壊れただけで一覧全体が
                            // 500 になり、正常な接続も操作できなくなる (Issue #39)。
                            connectionRow(name, ConnectionRow.load { ctx.configSource.loadSharedJdbcConfig(name) })
                        }
                    }
                }
            }
        }
    }
}

/** データソース接続 1 行。操作列が増えて `connectionsListView` が長くなるため切り出している。 */
private fun kotlinx.html.TBODY.connectionRow(name: String, row: ConnectionRow) {
    tr {
        td { a(href = "/connections/$name") { +name } }
        when (row) {
            is ConnectionRow.Loaded -> loadedCells(name, row.config)
            is ConnectionRow.Unreadable -> unreadableCells(name, row.reason)
        }
    }
}

/** 設定が読めた行のドライバークラス・接続文字列・操作。 */
private fun kotlinx.html.TR.loadedCells(name: String, config: JdbcConfig) {
    td { code { +config.driverClass } }
    // 全文は title で参照する。マスク済みの値のみを入れること
    // (生の接続文字列を入れると DOM に平文の資格情報が載る)。
    val masked = ConnectionStringMasker.mask(config.url)
    td(classes = "cell-truncate") {
        attributes["title"] = masked
        code { +masked }
    }
    td(classes = "cell-actions") {
        connectionTestButton(name)
        a(href = "/connections/$name/edit", classes = "button secondary") { +"編集" }
        deleteButton(name)
    }
}

/**
 * 設定が読めなかった行。
 *
 * 操作は削除だけにする。接続テストも編集も設定の中身を必要とするため成立しないが、
 * 削除は名前だけで足りる。画面から復旧できる導線を残す (Issue #39)。
 */
private fun kotlinx.html.TR.unreadableCells(name: String, reason: String) {
    td { span(classes = "status-badge failing") { +"読み込めません" } }
    td(classes = "cell-truncate") {
        attributes["title"] = reason
        // 理由もマスクを通す。壊れた JSON の断片に資格情報が残っている可能性がある。
        small(classes = "muted") { +ConnectionStringMasker.mask(reason) }
    }
    td(classes = "cell-actions") { deleteButton(name) }
}

/**
 * 接続テスト結果バナーの差し替え先 id。ビューとハンドラで共有する。
 */
internal const val CONNECTION_TEST_RESULT_ID = "connection-test-result"

/**
 * 接続テストボタン。
 *
 * ハンドラは HTML 断片を返すため、フォーム送信すると裸の断片がページとして表示されて
 * しまう。htmx で行内を差し替えて一覧に留まる (Issue #40)。
 * `DriversView` のライセンス検証と同じ流儀。
 *
 * 接続テストは OAuth 絡みでタイムアウトまで数十秒かかることがあるため、
 * 実行中を示し (`htmx-indicator`)、連打を防ぐ (`hx-disabled-elt`)。
 */
private fun kotlinx.html.FlowContent.connectionTestButton(name: String) {
    button(type = ButtonType.button, classes = "secondary outline") {
        attributes["hx-post"] = "/connections/$name/test"
        // 差し替え先はテーブルの外の共有バナー。ボタン自身を対象にすると
        // 結果表示でボタンが消えて再テストできない (Issue #42)。
        attributes["hx-target"] = "#$CONNECTION_TEST_RESULT_ID"
        attributes["hx-disabled-elt"] = "this"
        +"接続テスト"
        // htmx は要求元の要素に htmx-request を付けるため、target が外でも効く。
        // .htmx-indicator の CSS は htmx が自前で注入するので app.css の追加は不要。
        span(classes = "htmx-indicator") { +" 実行中…" }
    }
}

/**
 * 削除ボタン。
 *
 * 参照中かどうかで出し分けない。一覧描画時に判定すると全連携の jdbc_ref を
 * 毎回引くことになる。押したときにハンドラ側で判定して拒否する (Issue #36)。
 */
private fun kotlinx.html.FlowContent.deleteButton(name: String) {
    form(action = "/connections/$name/delete", method = FormMethod.post, classes = "inline-form") {
        attributes["onsubmit"] = DeleteConfirm.connectionDeleteOnSubmit(name)
        button(type = ButtonType.submit, classes = "danger") { +"削除" }
    }
}

/**
 * 参照中のため削除できなかったことを伝える。
 *
 * 先に参照元の連携を削除すればデータソースも削除できる、と分かるように
 * 参照元の連携名を全件出す (AC-6, AC-7)。
 */
private fun kotlinx.html.FlowContent.blockedDeleteNotice(blocked: BlockedDelete) {
    article(classes = "warning-banner") {
        p {
            +"データソース接続 "
            code { +blocked.connectionName }
            +" は次の連携が使用しているため削除できません。先に連携を削除してください。"
        }
        ul {
            blocked.referencingTables.forEach { tableName ->
                li { a(href = "/syncs/$tableName") { +tableName } }
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

        // 動的プロパティは導線の判定とフォーム描画の両方で使うため、ここで 1 回だけ取る。
        val propertiesResult = propertiesResultFor(ctx, drivers, existing)

        if (editMode) {
            oauthAuthorizationLinkIfNeeded(existingName, existing, propertiesResult)
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

            queryPassthroughNotice(existing, propertiesResult)

            // 動的プロパティフォーム挿入箇所。
            // 認証方式など条件に使われる入力欄が変わると、その入力欄自身が
            // ここを差し替える (propertyFieldTargets を参照)。
            div {
                attributes["id"] = "properties-form-container"
                if (existing != null && propertiesResult != null) {
                    propertiesFormContent(propertiesResult, existingValuesOf(existing))
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

/** 編集対象の接続に対応する動的プロパティ。新規作成時やドライバー不明時は null。 */
private fun propertiesResultFor(
    ctx: AppContext,
    drivers: List<com.cdata.kintone.adapter.jdbc.JdbcDriverInfo>,
    existing: JdbcConfig?,
): ConnectionPropertiesResult? {
    val config = existing ?: return null
    val driverInfo = drivers.firstOrNull { it.driverClass == config.driverClass } ?: return null
    return ctx.connectionPropertyInspector.fetchProperties(config.driverClass, driverInfo.filename)
}

/** 必要な情報が揃っているときだけ OAuth 認可の導線を描画する。 */
private fun kotlinx.html.FlowContent.oauthAuthorizationLinkIfNeeded(
    connectionName: String?,
    config: JdbcConfig?,
    properties: ConnectionPropertiesResult?,
) {
    if (connectionName == null || config == null || properties == null) return
    oauthAuthorizationLink(connectionName, config, properties)
}

/**
 * OAuth 認可ウィザードへの導線。
 *
 * ブラウザ認可が必要な認証方式のときだけ出す。`OAuthPassword` / `OAuthJWT` などは
 * プロパティ入力だけで完結するため対象にしない。
 * ドライバーが認可プロシージャを持つかはウィザード側で判定する
 * （編集画面を開くたびに接続を張るのを避けるため）。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
private fun kotlinx.html.FlowContent.oauthAuthorizationLink(
    connectionName: String,
    config: JdbcConfig,
    properties: ConnectionPropertiesResult,
) {
    // 実効値で判定する。#27 以降、既定値は接続文字列に保存されないため
    // AuthScheme 未指定が普通に起こる。解決は OAuthCapability に一本化しており、
    // 新規保存時のリダイレクト判定と同じ基準になる (Issue #43)。
    val authScheme = OAuthCapability.effectiveAuthScheme(config.url, authSchemeDefaultOf(properties))
    if (!OAuthCapability.requiresBrowserAuthorization(authScheme)) return

    article(classes = "info-banner") {
        p {
            +"この接続は OAuth 認可 ("
            code { +(authScheme ?: "OAuth") }
            +") を使います。初回はブラウザでの認可が必要です。"
        }
        div(classes = "action-bar") {
            a(href = "/connections/$connectionName/oauth", classes = "button") { +"OAuth 認可を行う →" }
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
        // プロパティフォームが作れないため、手動入力が唯一の編集手段。事前入力する。
        manualUrlField(existingValues, prefill = true)
        return
    }

    propertyCategories(result.properties, existingValues)

    // 縮退時は取得漏れのプロパティを補えるよう、直接入力も併記する。
    //
    // **事前入力はしない。** 埋めた値はブラウザがそのまま送り返し、
    // ConnectionFormUrl.build で無条件に優先されるため、プロパティ側の編集が
    // 黙って捨てられていた (Issue #59)。
    if (result.isDegraded) {
        manualUrlField(existingValues, prefill = false)
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

/**
 * `QueryPassthrough` が既定で有効なコネクタへの注意書き。
 *
 * 接続テストもウィザードも通るため、kintone からレコードを読んだ時点で初めて
 * 失敗する。原因から遠い場所でエラーに遭遇するのを防ぐ (Issue #69)。
 *
 * 判定を関数に切り出しているのは、`connectionFormView` の
 * `CyclomaticComplexMethod` を増やさないため。
 */
private fun kotlinx.html.FlowContent.queryPassthroughNotice(
    existing: JdbcConfig?,
    properties: ConnectionPropertiesResult?,
) {
    if (existing == null || properties == null) return
    if (!QueryPassthroughAdvice.isNeeded(properties.properties, existing.url)) return

    article(classes = "warning-banner") { p { +QueryPassthroughAdvice.MESSAGE } }
}

/** 既存の接続文字列を `existingValues` に載せるときのキー。プロパティ名と衝突しない形。 */
private const val URL_VALUE_KEY = "__url__"

/**
 * プロパティフォームを生成できない / 補完が必要なときのフォールバック入力欄。
 *
 * [prefill] は**プロパティフォームが併記されないときだけ true** にする。
 * 併記時に既存の接続文字列を埋めると、ブラウザがそれをそのまま送り返し、
 * [ConnectionFormUrl][com.cdata.kintone.adapter.web.routes.ConnectionFormUrl] が
 * 無条件に優先するため、プロパティ側の編集が黙って捨てられる (Issue #59)。
 */
private fun kotlinx.html.FlowContent.manualUrlField(
    existingValues: Map<String, String>,
    prefill: Boolean,
) {
    val current = existingValues[URL_VALUE_KEY].orEmpty()
    label {
        +if (prefill) "JDBC 接続文字列 (直接入力):" else "JDBC 接続文字列 (直接入力、任意):"
        textArea {
            name = "jdbc.url.manual"
            rows = "3"
            if (prefill) +current
        }
    }
    if (prefill) return

    small(classes = "muted") {
        +"空欄のままなら上のプロパティから組み立てます。入力した場合はその値が優先されます。"
    }
    if (current.isNotBlank()) {
        // 参照用。マスク済みの値のみを出すこと (生の接続文字列を入れると
        // DOM に平文の資格情報が載る)。
        p {
            small(classes = "muted") {
                +"現在の値: "
                code { +ConnectionStringMasker.mask(current) }
            }
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
    // 既定値は入力欄に埋めない。埋めると「利用者が設定した値」と区別できず、
    // 触っていないプロパティまで接続文字列に保存されてしまう (Issue #27)。
    // 既定値は placeholder / 選択肢のラベルで見せるだけにする。
    val value = currentValue ?: ""
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
                // チェックボックスでは「未指定」を表現できない。未チェックが
                // 「False を明示」なのか「触っていない」のか区別できないため 3 択にする。
                select {
                    propertyFieldTargets(isDependency, "change")
                    name = "prop.${prop.propertyName}"
                    option {
                        this.value = ""
                        +unspecifiedLabel(prop.defaultValue)
                    }
                    BOOLEAN_CHOICES.forEach { choice ->
                        option {
                            this.value = choice
                            if (choice.equals(value, ignoreCase = true)) selected = true
                            +choice
                        }
                    }
                }
            }
            prop.type == PropertyType.INT -> {
                input(type = InputType.number, name = "prop.${prop.propertyName}") {
                    propertyFieldTargets(isDependency, "keyup changed delay:200ms")
                    this.value = value
                    prop.defaultValue?.takeIf { it.isNotBlank() }?.let { placeholder = it }
                }
            }
            prop.allowedValues.isNotEmpty() -> {
                select {
                    propertyFieldTargets(isDependency, "change")
                    name = "prop.${prop.propertyName}"
                    option {
                        this.value = ""
                        +unspecifiedLabel(prop.defaultValue)
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
                    prop.defaultValue?.takeIf { it.isNotBlank() }?.let { placeholder = it }
                }
            }
            else -> {
                input(type = InputType.text, name = "prop.${prop.propertyName}") {
                    propertyFieldTargets(isDependency, "keyup changed delay:200ms")
                    this.value = value
                    prop.defaultValue?.takeIf { it.isNotBlank() }?.let { placeholder = it }
                }
            }
        }
        small(classes = "description") { +prop.shortDescription }
        if (prop.hierarchy.isNotBlank()) {
            small(classes = "hierarchy-hint") { +"ⓘ ${prop.hierarchy} の条件で有効" }
        }
    }
}

/** 真偽値プロパティの選択肢。CData ドライバーは True / False を受け付ける。 */
private val BOOLEAN_CHOICES = listOf("True", "False")

/**
 * 未選択時のラベル。既定値があれば併記する。
 *
 * 既定値を入力欄に埋めなくなった代わりに、未入力のとき何が使われるかを
 * 画面で分かるようにする (Issue #27)。
 */
private fun unspecifiedLabel(defaultValue: String?): String =
    if (defaultValue.isNullOrBlank()) "-- 未指定 --" else "-- 未指定 (既定: $defaultValue) --"

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
    map[URL_VALUE_KEY] = config.url
    return map
}
