package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.ConfigSourceFactory
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.footer
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.title
import kotlinx.html.ul

/**
 * 全画面共通のレイアウト DSL。
 * `siteTitle` / `mode` / `activeCount` をヘッダ・フッタに反映し、
 * 各ページコンテンツは `content` ブロックでメインに差し込む。
 */
fun HTML.layout(
    pageTitle: String,
    mode: ConfigSourceFactory.Mode,
    activeCount: Int,
    currentPath: String = "/",
    content: FlowContent.() -> Unit,
) {
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"$pageTitle — CData Kintone Adapter Console" }
        link(rel = "stylesheet", href = "/static/pico.min.css")
        link(rel = "stylesheet", href = "/static/app.css")
        script(src = "/static/htmx.min.js") {}
        script(src = "/static/app.js") {}
    }
    body {
        header(classes = "container") {
            nav {
                ul {
                    li {
                        a(href = "/") {
                            strong { +"⚡ CData Kintone Adapter Console" }
                        }
                    }
                }
                ul {
                    navItem("/", "🏠 ダッシュボード", currentPath)
                    navItem("/syncs", "🔄 連携", currentPath)
                    navItem("/connections", "🔌 データソース", currentPath)
                    navItem("/drivers", "📦 ドライバー", currentPath)
                }
            }
        }
        main(classes = "container") {
            content()
        }
        footer(classes = "container") {
            small {
                +"CData Kintone Adapter Console v0.4.0  |  ConfigSource: ${mode.name.lowercase()}  |  稼働中: $activeCount 件  |  © 2026 CData Software Japan"
            }
        }
    }
}

private fun UL.navItem(href: String, label: String, currentPath: String) {
    li {
        if (currentPath == href || (href != "/" && currentPath.startsWith(href))) {
            span(classes = "nav-active") { +label }
        } else {
            a(href = href) { +label }
        }
    }
}
