package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.runtime.AdapterStatus
import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.small
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun HTML.dashboardView(ctx: AppContext) {
    val active = ctx.runner.listActive()
    val phase1Detected = ctx.configDir.resolve("server.yaml").toFile().exists() &&
        !ctx.configDir.resolve("tables").toFile().exists()

    layout(
        pageTitle = "Dashboard",
        mode = ctx.configSourceMode,
        activeCount = active.size,
        currentPath = "/",
    ) {
        h2 { +"Dashboard" }

        if (phase1Detected) {
            article(classes = "warning-banner") {
                p {
                    +"⚠ Phase 1 構成を検出: config/ 直下に server.yaml 等があります。"
                }
                form(action = "/migrate/phase1-to-tables", method = kotlinx.html.FormMethod.post) {
                    button(type = kotlinx.html.ButtonType.submit) { +"Migrate to multi-table" }
                }
            }
        }

        article {
            h3 { +"Active Adapters (${active.size})" }
            if (active.isEmpty()) {
                p { +"稼働中の Adapter はありません。Tables 画面で個別に起動するか、`adapter serve-all` を実行してください。" }
            } else {
                activeAdaptersTable(active)
            }
        }

        article {
            h3 { +"Quick Actions" }
            div(classes = "quick-actions") {
                a(href = "/tables/new", classes = "button") { +"+ New Table" }
                a(href = "/drivers", classes = "button secondary") { +"Manage Drivers" }
                a(href = "/connections/new", classes = "button secondary") { +"+ New Connection" }
            }
        }
    }
}

internal fun kotlinx.html.FlowContent.activeAdaptersTable(active: List<AdapterStatus>) {
    table(classes = "striped") {
        attributes["id"] = "active-adapters-table"
        thead {
            tr {
                th { +"Name" }
                th { +"Port" }
                th { +"Started" }
                th { +"Status" }
            }
        }
        tbody {
            active.forEach { row ->
                tr {
                    attributes["data-table"] = row.tableName
                    td { +row.tableName }
                    td { +row.port.toString() }
                    td {
                        +(if (row.startedAt > 0) {
                            FORMATTER.format(Instant.ofEpochMilli(row.startedAt))
                        } else {
                            "-"
                        })
                    }
                    td(classes = "status") {
                        small(classes = "status-badge serving") { +row.status }
                    }
                }
            }
        }
    }
}

private val FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
