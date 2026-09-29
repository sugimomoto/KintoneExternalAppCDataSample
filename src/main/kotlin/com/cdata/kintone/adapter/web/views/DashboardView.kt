package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.runtime.AdapterStatus
import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.small
import kotlinx.html.span
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
    val totalSyncs = ctx.configSource.listTables().size
    val totalConnections = ctx.configSource.listSharedJdbcConfigs().size
    val totalDrivers = ctx.driverManager.listDrivers().size

    layout(
        pageTitle = "ダッシュボード",
        activeCount = active.size,
        currentPath = "/",
    ) {
        // ヒーローセクション (KPI 表示)
        div(classes = "hero") {
            h2 { +"ダッシュボード" }
            p(classes = "lead") {
                +"連携の状態を一目で確認。新しい連携の追加・既存連携の管理がここからできます。"
            }
            div(classes = "kpis") {
                kpiCard(active.size.toString(), "稼働中")
                kpiCard(totalSyncs.toString(), "連携 (合計)")
                kpiCard(totalConnections.toString(), "データソース接続")
                kpiCard(totalDrivers.toString(), "ドライバー")
            }
        }

        article {
            h3 { +"稼働中の連携 (${active.size} 件)" }
            if (active.isEmpty()) {
                div(classes = "empty-state") {
                    div(classes = "icon") { +"💤" }
                    h3 { +"稼働中の連携はありません" }
                    p { +"連携画面から「開始」ボタンを押すか、新しい連携を追加してください。" }
                    div(classes = "actions") {
                        a(href = "/syncs", classes = "button secondary") { +"連携一覧へ" }
                        a(href = "/syncs/new", classes = "button") { +"+ 新しい連携" }
                        a(href = "/help", classes = "button secondary outline") { +"ヘルプを開く" }
                    }
                }
            } else {
                activeAdaptersTable(active)
            }
        }

        article {
            h3 { +"クイックアクション" }
            div(classes = "action-bar") {
                a(href = "/syncs/new", classes = "button") { +"+ 新しい連携" }
                a(href = "/connections/new", classes = "button secondary") { +"+ 新しいデータソース接続" }
                a(href = "/drivers", classes = "button secondary") { +"ドライバー管理" }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.kpiCard(value: String, label: String) {
    div(classes = "kpi") {
        div(classes = "kpi-label") { +label }
        div(classes = "kpi-value") { +value }
    }
}

internal fun kotlinx.html.FlowContent.activeAdaptersTable(active: List<AdapterStatus>) {
    table(classes = "striped") {
        attributes["id"] = "active-adapters-table"
        thead {
            tr {
                th { +"名前" }
                th { +"ポート" }
                th { +"開始時刻" }
                th { +"状態" }
            }
        }
        tbody {
            active.forEach { row ->
                tr {
                    attributes["data-table"] = row.tableName
                    td {
                        a(href = "/syncs/${row.tableName}") { +row.tableName }
                    }
                    td { code { +row.port.toString() } }
                    td {
                        small(classes = "muted") {
                            +(if (row.startedAt > 0) {
                                FORMATTER.format(Instant.ofEpochMilli(row.startedAt))
                            } else {
                                "-"
                            })
                        }
                    }
                    td(classes = "status") {
                        span(classes = "status-badge serving") { +"稼働中" }
                    }
                }
            }
        }
    }
}

// タイムゾーンまで出す。コンテナの既定は UTC で、TZ を設定していなければ
// ホストのローカル時刻とずれる。ずれたまま時刻だけ出すと読み違える (Issue #25)。
private val FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss z")
    .withZone(ZoneId.systemDefault())
