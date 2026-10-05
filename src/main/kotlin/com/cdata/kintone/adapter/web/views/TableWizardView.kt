package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.metadata.ColumnInfo
import com.cdata.kintone.adapter.metadata.ExcludedColumn
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
import kotlinx.html.strong
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
                a(href = "/syncs", classes = "button secondary outline") { +"キャンセル" }
                button(type = ButtonType.submit) { +"次へ →" }
            }
        }
    }
}

fun HTML.wizardStep2View(
    ctx: AppContext,
    connectionName: String,
    tables: List<TableInfo>,
    selectedSchema: String? = null,
) {
    val schemas = tables.mapNotNull { it.schema }.distinct().sorted()
    // スキーマが 2 種類以上あるときだけ選択させ、1 スキーマに絞って表示する。
    // 絞らないと、どのテーブルがどのスキーマのものか hidden 1 つでは送れない。
    // スキーマが 1 種類以下なら選択 UI を出さない (Issue #63)。
    val effectiveSchema = if (schemas.size >= 2) selectedSchema ?: schemas.first() else schemas.firstOrNull()
    val visibleTables = if (schemas.size >= 2) tables.filter { it.schema == effectiveSchema } else tables
    layout(
        pageTitle = "New Table — Step 2",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 2 of 4: Select Table" }
        wizardSteps(2)
        p { +"Via connection: "; code { +connectionName } }

        if (schemas.size >= 2) {
            schemaSelector(connectionName, schemas, effectiveSchema)
        }

        form(action = "/syncs/new/step3", method = FormMethod.get) {
            input(type = InputType.hidden, name = "connection") { value = connectionName }
            // スキーマはラベルに詰めず独立した値として送る (Issue #63)。
            effectiveSchema?.let { input(type = InputType.hidden, name = "schema") { value = it } }

            p { +"Found ${visibleTables.size} tables (showing first 100)" }
            div(classes = "scrollable-list") {
                visibleTables.take(100).forEach { t ->
                    label {
                        input(type = InputType.radio, name = "table") {
                            value = t.name
                            required = true
                        }
                        +" ${t.name}"
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
                a(href = "/syncs/new", classes = "button secondary outline") { +"← 戻る" }
                button(type = ButtonType.submit) { +"次へ →" }
            }
        }
    }
}

/**
 * スキーマの絞り込み。スキーマが 2 種類以上あるときだけ描画する。
 *
 * 絞り込んで 1 スキーマだけを表示するのは、どのテーブルがどのスキーマのものかを
 * hidden 1 つでは送れないため (Issue #63)。
 */
private fun kotlinx.html.FlowContent.schemaSelector(
    connectionName: String,
    schemas: List<String>,
    effectiveSchema: String?,
) {
    form(action = "/syncs/new/step2", method = FormMethod.get) {
        input(type = InputType.hidden, name = "connection") { value = connectionName }
        label {
            +"スキーマ: "
            select {
                name = "schema"
                schemas.forEach { s ->
                    option {
                        value = s
                        selected = (s == effectiveSchema)
                        +s
                    }
                }
            }
        }
        button(type = ButtonType.submit, classes = "secondary outline") { +"絞り込む" }
    }
}

/**
 * step3 に渡す内容。
 *
 * `columns` と `error` を束ねているのは、`wizardStep3View` の引数が detekt の
 * `LongParameterList`（閾値 6、拡張関数のレシーバも 1 つとして数える）に
 * 引っかかるため。表示する内容という関心でまとまってもいる。
 */
data class Step3Content(
    val columns: List<ColumnInfo>,
    /** 失敗の理由。null なら何も表示しない。 */
    val error: String? = null,
    /**
     * レコード ID 列の候補。空でなければ選択 UI を出す。
     *
     * 主キーが検出できないテーブル・ビューでも、型が合う列があれば指定して
     * 連携できる (Issue #66)。
     */
    val recordIdCandidates: List<ColumnInfo> = emptyList(),
    /**
     * 自動生成列として選択候補から外した列。空なら何も描画しない。
     *
     * 黙って消すと「列が足りない」と誤解される (Issue #75)。
     */
    val excludedColumns: List<ExcludedColumn> = emptyList(),
)

/**
 * 除外した自動生成列の案内。空なら何も描画しない。
 *
 * DB 側で値が決まる列を kintone の入力項目として出すと、空のまま登録されて必ず失敗する。
 * 列自体を送らなければ既定値が効くため候補から外すが、**何をなぜ外したのかは示す**。
 * 既定値の式まで出すのは、省いて良い列なのかを利用者が判断できるようにするため。
 *
 * 関連: [Issue #75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75)
 */
fun kotlinx.html.FlowContent.excludedColumnsNotice(excluded: List<ExcludedColumn>) {
    if (excluded.isEmpty()) return

    article(classes = "warning-banner") {
        p {
            +"以下の ${excluded.size} 列は DB 側で自動的に値が決まるため、マッピング対象から外しました。"
            +"kintone の入力項目として表示すると、空のまま登録しようとして"
            strong { +"失敗します" }
            +"。列を送らなければ DB 側の既定値が入ります。"
        }
        table(classes = "striped") {
            thead {
                tr {
                    th { +"Column" }
                    th { +"JDBC Type" }
                    th { +"除外した理由" }
                }
            }
            tbody {
                excluded.forEach { item ->
                    tr {
                        td { code { +item.column.name } }
                        td { +item.column.typeName }
                        td {
                            +item.reason.label
                            item.column.defaultValue?.takeIf { it.isNotBlank() }?.let { expr ->
                                +" "
                                code { +expr }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * レコード ID 列の選択 UI。候補が空なら何も描画しない。
 *
 * kintone の `RecordIdFieldDefinition` は列が DB 上で主キーかを問わないため、
 * 型が合えば任意の列を指定できる。ただし**一意性は利用者の責任**になるので、
 * 守らなかった場合に何が起きるかまで示す (Issue #66)。
 */
fun kotlinx.html.FlowContent.recordIdSelection(candidates: List<ColumnInfo>) {
    if (candidates.isEmpty()) return

    article(classes = "warning-banner") {
        p {
            +"主キーが無いため、レコード番号に使う列を指定してください。"
            +"選んだ列の値が"
            strong { +"一意であることは利用者の責任です" }
            +"。重複がある列を選ぶと、更新・削除が意図しない行に及びます。"
        }
        label {
            +"レコード番号に使う列: "
            select {
                name = "recordIdColumn"
                candidates.forEach { c ->
                    option {
                        value = c.name
                        +"${c.name} (${c.typeName})"
                    }
                }
            }
        }
    }
}

/**
 * 連携追加ウィザード step3（カラム選択）。
 *
 * step4 の算出に失敗した場合もこの画面に戻す。専用のエラーページを作らないのは、
 * 前のステップに戻れる状態を保つため (Issue #64)。
 */
fun HTML.wizardStep3View(
    ctx: AppContext,
    connectionName: String,
    table: TableInfo,
    configName: String,
    content: Step3Content,
) {
    val columns = content.columns
    val tableName = table.name
    val schema = table.schema
    layout(
        pageTitle = "New Table — Step 3",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 3 of 4: Select Columns" }
        wizardSteps(3)
        content.error?.let { reason ->
            article(classes = "warning-banner") { p { +reason } }
        }
        p {
            +"Via "; code { +connectionName }
            +" / "; code { +tableName }
        }

        form(action = "/syncs/new/step4", method = FormMethod.post) {
            input(type = InputType.hidden, name = "connection") { value = connectionName }
            input(type = InputType.hidden, name = "table") { value = tableName }
            schema?.let { input(type = InputType.hidden, name = "schema") { value = it } }
            input(type = InputType.hidden, name = "configName") { value = configName }
            recordIdSelection(content.recordIdCandidates)
            excludedColumnsNotice(content.excludedColumns)

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
                a(href = "/syncs/new", classes = "button secondary outline") { +"← Step 1 に戻る" }
                button(type = ButtonType.submit) { +"次へ →" }
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
    table: TableInfo,
    configName: String,
    primaryKey: String,
    recommendedRecordIdType: RecordIdType,
    mappings: List<WizardMapping>,
) {
    val tableName = table.name
    val schema = table.schema
    // 空きポートを提示する。使い切っている場合は空欄にして保存時にエラーを出す。
    val suggestedPort = runCatching { ctx.syncPortAllocator.allocate() }.getOrNull()
    layout(
        pageTitle = "New Table — Step 4",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"New Table — Step 4 of 4: Mapping & Capability" }
        wizardSteps(4)

        form(action = "/syncs", method = FormMethod.post) {
            input(type = InputType.hidden, name = "connection") { value = connectionName }
            input(type = InputType.hidden, name = "table") { value = tableName }
            schema?.let { input(type = InputType.hidden, name = "schema") { value = it } }
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
                a(href = "/syncs/new", classes = "button secondary outline") { +"← 最初に戻る" }
                button(type = ButtonType.submit, name = "andStart", classes = "secondary outline") {
                    value = "false"
                    +"保存のみ"
                }
                button(type = ButtonType.submit, name = "andStart", classes = "secondary") {
                    value = "true"
                    +"保存して起動"
                }
                button(type = ButtonType.submit, name = "andConnect") {
                    value = "true"
                    +"保存して kintone と接続 →"
                }
            }
        }
    }
}
