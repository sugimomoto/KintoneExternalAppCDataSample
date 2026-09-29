package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.jdbc.JdbcDriverInfo
import com.cdata.kintone.adapter.jdbc.LicenseStatus
import com.cdata.kintone.adapter.jdbc.LicenseVerification
import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.ButtonType
import kotlinx.html.FormEncType
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
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun HTML.driversListView(ctx: AppContext) {
    val drivers = ctx.driverManager.listDrivers()
    layout(
        pageTitle = "ドライバー",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/drivers",
    ) {
        h2 { +"ドライバー (${drivers.size} 件)" }
        small(classes = "muted") {
            +"配置先: "
            code { +ctx.libDir.toString() }
        }

        section {
            h3 { +"ドライバーを追加" }
            form(
                action = "/drivers/upload",
                method = FormMethod.post,
                encType = FormEncType.multipartFormData,
            ) {
                input(type = InputType.file, name = "file") {
                    accept = ".jar"
                    required = true
                }
                button(type = ButtonType.submit) { +"↑ アップロード" }
            }
            small { +"※ .jar 拡張子 + JAR Magic Number (PK\\x03\\x04) 検証あり。最大 50 MB。" }
        }

        if (drivers.isEmpty()) {
            article {
                p { +"ドライバーがまだ配置されていません。" }
            }
        } else {
            div(classes = "table-scroll") {
                table(classes = "striped") {
                    thead {
                        tr {
                            th { +"ファイル名" }
                            th { +"ドライバークラス" }
                            th { +"ライセンスファイル" }
                            th { +"実際に使えるか" }
                            th { +"サイズ" }
                            th { +"操作" }
                        }
                    }
                    tbody {
                        drivers.forEach { d ->
                            driverRow(d)
                        }
                    }
                }
            }
        }
    }
}

fun HTML.driverActivateView(ctx: AppContext, filename: String) {
    layout(
        pageTitle = "トライアルを有効化: $filename",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/drivers",
    ) {
        h2 { +"トライアルライセンスを有効化" }
        article {
            p {
                +"対象: "; code { +filename }
            }
            p {
                +"CData のトライアルライセンス（無料・30 日）を取得します。"
                +"以下の情報が "
                a(href = "https://www.cdata.com/jp/", target = "_blank") { +"CData" }
                +" ライセンスサーバへ送信されます。"
            }
            form(action = "/drivers/$filename/activate", method = FormMethod.post) {
                label {
                    +"お名前:"
                    input(type = InputType.text, name = "name") {
                        required = true
                        placeholder = "山田 太郎"
                    }
                }
                label {
                    +"メールアドレス:"
                    input(type = InputType.email, name = "email") {
                        required = true
                        placeholder = "you@example.com"
                    }
                }
                label {
                    +"プロダクトキー:"
                    input(type = InputType.text, name = "product_key") {
                        value = "TRIAL"
                        readonly = true
                    }
                }
                div(classes = "action-bar") {
                    a(href = "/drivers", classes = "button secondary outline") { +"キャンセル" }
                    button(type = ButtonType.submit) { +"有効化" }
                }
            }
        }
    }
}

fun HTML.driverActivateResultView(
    ctx: AppContext,
    filename: String,
    success: Boolean,
    message: String,
    stdout: String,
) {
    layout(
        pageTitle = "有効化の結果",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/drivers",
    ) {
        h2 { +"有効化の結果" }
        article(classes = if (success) "" else "warning-banner") {
            p {
                if (success) +"✅ " else +"❌ "
                +message
            }
            if (!success) {
                p {
                    small { +"標準出力 (デバッグ用):" }
                }
                code(classes = "stdout-debug") { +stdout }
            }
            div(classes = "action-bar") {
                a(href = "/drivers", classes = "button secondary outline") { +"← ドライバー一覧に戻る" }
            }
        }
    }
}

/** ドライバー 1 行。列が増えて `driversListView` が長くなるため切り出している。 */
private fun kotlinx.html.TBODY.driverRow(d: com.cdata.kintone.adapter.jdbc.JdbcDriverInfo) {
    tr {
        td { code { +d.filename } }
        td(classes = "cell-truncate") {
            val driverClass = d.driverClass ?: "(不明)"
            attributes["title"] = driverClass
            code { +driverClass }
        }
        td { licenseBadge(d.licenseStatus) }
        td {
            // 検証結果の差し替え先。表示のたびに全ドライバーへ
            // 接続すると重いため、押したときだけ検証する (Issue #31)。
            div {
                attributes["id"] = "license-check-${d.filename}"
                button(type = ButtonType.button, classes = "secondary outline") {
                    attributes["hx-post"] = "/drivers/${d.filename}/verify-license"
                    attributes["hx-target"] = "#license-check-${d.filename}"
                    +"検証"
                }
            }
        }
        td { +"${d.sizeBytes / 1024} KB" }
        td(classes = "cell-actions") {
            if (d.licenseStatus == LicenseStatus.NOT_ACTIVATED) {
                a(href = "/drivers/${d.filename}/activate", classes = "button secondary") {
                    +"トライアルを有効化"
                }
            }
            form(
                action = "/drivers/${d.filename}/delete",
                method = FormMethod.post,
                classes = "inline-form",
            ) {
                attributes["onsubmit"] = "return confirm('${d.filename} を削除しますか？')"
                button(type = ButtonType.submit, classes = "danger") { +"削除" }
            }
        }
    }
}

/**
 * ライセンス検証の結果。
 *
 * `.lic` の有無 ([licenseBadge]) とは**別の軸**。ファイルがあっても
 * 別マシンで認証されたものは使えないため、実際に試した結果を出す。
 */
fun kotlinx.html.FlowContent.licenseVerificationResult(verification: LicenseVerification) {
    when (verification) {
        is LicenseVerification.Valid ->
            span(classes = "status-badge serving") { +"利用可能" }

        is LicenseVerification.Invalid -> {
            span(classes = "status-badge failing") { +"要アクティベーション" }
            // メッセージはロケール依存なので分類せずそのまま見せる。
            p { small(classes = "muted") { +verification.rawMessage } }
        }
    }
}

private fun kotlinx.html.FlowContent.licenseBadge(status: LicenseStatus) {
    val (cls, label) = when (status) {
        // ラベルに ● / ○ を入れない。.status-badge::before が既にドットを描画するため二重になる。
        LicenseStatus.ACTIVATED -> "status-badge serving" to "有効"
        LicenseStatus.NOT_ACTIVATED -> "status-badge stopped" to "未有効化"
        LicenseStatus.UNKNOWN -> "status-badge unknown" to "不明"
    }
    span(classes = cls) { +label }
}
