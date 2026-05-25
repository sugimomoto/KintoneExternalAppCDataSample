package com.cdata.kintone.adapter.web.views

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
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.small
import kotlinx.html.textArea
import kotlinx.html.unsafe

/**
 * `/syncs/{name}/connect` — kintone との接続を 1 ボタンで確立する画面。
 */
fun HTML.connectKintoneView(ctx: AppContext, syncName: String, message: String? = null, error: String? = null) {
    val publicKey = ctx.publicKeyManager.read()
    val fingerprint = ctx.publicKeyManager.fingerprint()

    layout(
        pageTitle = "kintone と接続: $syncName",
        mode = ctx.configSourceMode,
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"kintone と接続する: $syncName" }

        message?.let { msg ->
            article(classes = "success-banner") {
                p { +"✅ $msg" }
                p { a(href = "/syncs/$syncName", classes = "button") { +"連携の詳細を見る →" } }
            }
            return@layout
        }

        error?.let { err ->
            article(classes = "warning-banner") {
                p { +"⚠ $err" }
            }
        }

        // Step 1: 公開鍵
        section {
            h3 { +"Step 1: 公開鍵を kintone に登録" }
            if (publicKey == null) {
                article(classes = "warning-banner") {
                    p {
                        +"公開鍵 ("
                        code { +ctx.publicKeyManager.pathString() }
                        +") が見つかりません。"
                    }
                    p {
                        +"`scripts/generate-keypair.sh` 等で公開鍵を生成してから戻ってきてください。"
                    }
                }
            } else {
                p { +"以下の公開鍵を kintone コネクター管理画面で登録してください。" }
                div(classes = "public-key-display") {
                    code(classes = "stdout-debug") { +publicKey }
                }
                p {
                    button(type = ButtonType.button, classes = "secondary") {
                        attributes["onclick"] = "copyPublicKey()"
                        +"📋 公開鍵をコピー"
                    }
                    a(href = "/syncs/$syncName/public-key.pem", classes = "button secondary outline") {
                        attributes["download"] = "public-key.pem"
                        +"⬇ ダウンロード"
                    }
                }
                fingerprint?.let { fp ->
                    small {
                        +"SHA-256 fingerprint: "
                        val fpShort = fp.take(47) + "..."
                        code { +fpShort }
                    }
                }
            }
        }

        // Step 2: kintone 管理画面リンク
        section {
            h3 { +"Step 2: kintone 管理画面でコネクタを追加" }
            form(action = "/syncs/$syncName/connect/save-domain", method = FormMethod.post) {
                label {
                    +"kintone ドメイン (例: example.cybozu.com):"
                    input(type = InputType.text, name = "domain") {
                        placeholder = "example.cybozu.com"
                        value = readKintoneDomain()
                    }
                }
                button(type = ButtonType.submit, classes = "secondary outline") { +"保存して開く" }
            }
            val domain = readKintoneDomain()
            if (domain.isNotBlank()) {
                p {
                    a(href = "https://$domain/k/admin/system/admin/dataConnector.html", target = "_blank", classes = "button secondary") {
                        +"kintone 管理画面を開く ↗"
                    }
                }
            }
        }

        // Step 3: 接続キー入力 + 1 ボタン
        section {
            h3 { +"Step 3: 接続キーを入力して接続" }
            form(action = "/syncs/$syncName/connect", method = FormMethod.post) {
                label {
                    +"接続キー (kintone コネクタ登録時に発行された JWT):"
                    textArea {
                        name = "token"
                        rows = "4"
                        required = true
                        placeholder = "eyJhbGciOi..."
                    }
                }
                p {
                    small {
                        +"このボタンを押すと、内部で以下が自動実行されます："
                        +" Adapter 起動 / agent.json 保存 / Agent コンテナ作成・起動 / kintone との疎通確認。"
                    }
                }
                p {
                    button(type = ButtonType.submit, classes = "primary") { +"接続して開始 ▶" }
                    a(href = "/syncs/$syncName", classes = "button secondary outline") { +"キャンセル" }
                }
            }
        }

        // クリップボードコピー用 JS
        script {
            unsafe {
                +"""
                function copyPublicKey() {
                    const text = document.querySelector('.public-key-display code').textContent;
                    navigator.clipboard.writeText(text).then(() => {
                        alert('公開鍵をクリップボードにコピーしました');
                    });
                }
                """.trimIndent()
            }
        }
    }
}

/**
 * SQLite/YAML に永続化するのは Phase 2-D 送り。
 * 現状は環境変数 KINTONE_DOMAIN や application preferences (簡易) から読む。
 * 未設定なら空文字列。
 */
private fun readKintoneDomain(): String =
    System.getenv("KINTONE_DOMAIN") ?: ""
