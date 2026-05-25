package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.jdbc.JdbcDriverInfo
import com.cdata.kintone.adapter.jdbc.LicenseStatus
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
        pageTitle = "Drivers",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/drivers",
    ) {
        h2 { +"JDBC Drivers in ${ctx.libDir} (${drivers.size})" }

        section {
            h3 { +"Upload new driver" }
            form(
                action = "/drivers/upload",
                method = FormMethod.post,
                encType = FormEncType.multipartFormData,
            ) {
                input(type = InputType.file, name = "file") {
                    accept = ".jar"
                    required = true
                }
                button(type = ButtonType.submit) { +"⬆ Upload" }
            }
            small { +"※ .jar 拡張子 + JAR Magic Number (PK\\x03\\x04) 検証あり。最大 50 MB。" }
        }

        if (drivers.isEmpty()) {
            article {
                p { +"ドライバがまだ配置されていません。" }
            }
        } else {
            table(classes = "striped") {
                thead {
                    tr {
                        th { +"Filename" }
                        th { +"Driver Class" }
                        th { +"License" }
                        th { +"Size" }
                        th { +"Action" }
                    }
                }
                tbody {
                    drivers.forEach { d ->
                        tr {
                            td { code { +d.filename } }
                            td { code { +(d.driverClass ?: "(unknown)") } }
                            td { licenseBadge(d.licenseStatus) }
                            td { +"${d.sizeBytes / 1024} KB" }
                            td {
                                if (d.licenseStatus == LicenseStatus.NOT_ACTIVATED) {
                                    a(href = "/drivers/${d.filename}/activate", classes = "button outline") {
                                        +"Activate Trial"
                                    }
                                }
                                form(action = "/drivers/${d.filename}/delete", method = FormMethod.post, classes = "inline-form") {
                                    attributes["onsubmit"] = "return confirm('${d.filename} を削除しますか？')"
                                    button(type = ButtonType.submit, classes = "secondary outline") { +"Delete" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.driverActivateView(ctx: AppContext, filename: String) {
    layout(
        pageTitle = "Activate $filename",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/drivers",
    ) {
        h2 { +"Activate trial license" }
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
                    +"Name:"
                    input(type = InputType.text, name = "name") {
                        required = true
                        placeholder = "Taro Yamada"
                    }
                }
                label {
                    +"Email:"
                    input(type = InputType.email, name = "email") {
                        required = true
                        placeholder = "you@example.com"
                    }
                }
                label {
                    +"Product Key:"
                    input(type = InputType.text, name = "product_key") {
                        value = "TRIAL"
                        readonly = true
                    }
                }
                p {
                    a(href = "/drivers", classes = "button secondary") { +"Cancel" }
                    button(type = ButtonType.submit) { +"Activate" }
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
        pageTitle = "Activate result",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/drivers",
    ) {
        h2 { +"Activation Result" }
        article(classes = if (success) "" else "warning-banner") {
            p {
                if (success) +"✅ " else +"❌ "
                +message
            }
            if (!success) {
                p {
                    small { +"stdout (debug):" }
                }
                code(classes = "stdout-debug") { +stdout }
            }
            p {
                a(href = "/drivers", classes = "button") { +"Back to Drivers" }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.licenseBadge(status: LicenseStatus) {
    val (cls, label) = when (status) {
        LicenseStatus.ACTIVATED -> "status-badge serving" to "●Activated"
        LicenseStatus.NOT_ACTIVATED -> "status-badge stopped" to "○Not activated"
        LicenseStatus.UNKNOWN -> "status-badge" to "Unknown"
    }
    span(classes = cls) { +label }
}
