package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.small
import kotlinx.html.unsafe

/**
 * `/syncs/{name}/logs` — Adapter (左) と Agent (右) のライブログを並べて表示。
 */
fun HTML.syncLogsView(ctx: AppContext, syncName: String) {
    layout(
        pageTitle = "ログ: $syncName",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"ログ: $syncName" }
        p {
            a(href = "/syncs/$syncName", classes = "button secondary outline") { +"← 連携詳細に戻る" }
        }

        article {
            div(classes = "log-filters") {
                label { input(type = kotlinx.html.InputType.checkBox, classes = "lvl") {
                    attributes["data-level"] = "INFO"; checked = true } ; +" INFO" }
                label { input(type = kotlinx.html.InputType.checkBox, classes = "lvl") {
                    attributes["data-level"] = "WARN"; checked = true } ; +" WARN" }
                label { input(type = kotlinx.html.InputType.checkBox, classes = "lvl") {
                    attributes["data-level"] = "ERROR"; checked = true } ; +" ERROR" }
                label {
                    +"検索: "
                    input(type = kotlinx.html.InputType.text) {
                        attributes["id"] = "log-search"
                        placeholder = "部分一致..."
                    }
                }
                label {
                    input(type = kotlinx.html.InputType.checkBox) {
                        attributes["id"] = "log-autoscroll"
                        checked = true
                    }
                    +" 自動スクロール"
                }
            }
        }

        div(classes = "log-grid") {
            div {
                h3 { +"Adapter" }
                div(classes = "log-pane") {
                    attributes["id"] = "log-adapter"
                }
            }
            div {
                h3 { +"Agent (kintone コンテナ)" }
                div(classes = "log-pane") {
                    attributes["id"] = "log-agent"
                }
            }
        }

        small { +"※ 最大 1000 行までブラウザに保持されます。" }

        script {
            unsafe {
                +"""
                (function () {
                  const syncName = ${"\"" + syncName.replace("\"", "\\\"") + "\""};
                  const adapter = document.getElementById('log-adapter');
                  const agent = document.getElementById('log-agent');
                  const autoscroll = () => document.getElementById('log-autoscroll').checked;
                  const visibleLevels = () => Array.from(document.querySelectorAll('.lvl'))
                      .filter(e => e.checked).map(e => e.dataset.level);
                  const search = () => (document.getElementById('log-search').value || '').toLowerCase();
                  function appendLine(pane, level, text) {
                    const lvls = visibleLevels();
                    if (level && !lvls.includes(level)) return;
                    const s = search();
                    if (s && !text.toLowerCase().includes(s)) return;
                    const div = document.createElement('div');
                    div.className = 'log-line lvl-' + (level || 'INFO');
                    div.textContent = text;
                    pane.appendChild(div);
                    while (pane.children.length > 1000) pane.removeChild(pane.firstChild);
                    if (autoscroll()) pane.scrollTop = pane.scrollHeight;
                  }
                  const adapterSse = new EventSource('/syncs/' + syncName + '/logs/adapter/sse');
                  adapterSse.addEventListener('log', e => {
                    const d = JSON.parse(e.data);
                    appendLine(adapter, d.level, '[' + d.level + '] ' + d.message);
                  });
                  const agentSse = new EventSource('/syncs/' + syncName + '/logs/agent/sse');
                  agentSse.addEventListener('log', e => {
                    appendLine(agent, null, e.data);
                  });
                  window.addEventListener('beforeunload', () => {
                    adapterSse.close(); agentSse.close();
                  });
                })();
                """.trimIndent()
            }
        }
    }
}
