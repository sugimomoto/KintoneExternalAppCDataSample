package com.cdata.kintone.adapter.web.views

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
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.h4
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.small
import kotlinx.html.textArea
import kotlinx.html.unsafe

/**
 * `/syncs/{name}/connect` — kintone との接続を 1 ボタンで確立する画面。
 */
fun HTML.connectKintoneView(
    ctx: AppContext,
    syncName: String,
    notice: ConnectNotice = ConnectNotice(),
) {
    val message = notice.message
    val error = notice.error
    val infoMessage = notice.infoMessage
    val authRejected = notice.authRejected

    val publicKey = ctx.publicKeyManager.read()
    val fingerprint = ctx.publicKeyManager.fingerprint()

    layout(
        pageTitle = "kintone と接続: $syncName",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/syncs",
    ) {
        h2 { +"kintone と接続する: $syncName" }

        message?.let { msg ->
            article(classes = "success-banner") {
                p { +"✅ $msg" }
                div(classes = "action-bar") {
                    a(href = "/syncs/$syncName", classes = "button") { +"連携の詳細を見る →" }
                }
            }
            return@layout
        }

        infoMessage?.let { msg ->
            article(classes = "success-banner") {
                p { +"✅ $msg" }
            }
        }

        error?.let { err ->
            article(classes = "warning-banner") {
                p { +"⚠ $err" }
            }
        }

        if (authRejected) {
            authRejectedGuide()
        }

        // Step 1: 公開鍵
        section {
            h3 { +"Step 1: 公開鍵を kintone に登録" }
            if (publicKey == null) {
                article(classes = "warning-banner") {
                    p {
                        +"鍵ペアがまだ生成されていません。下のボタンで生成してください。"
                        +" 秘密鍵は adapter-console 側に保存され、画面には表示されません。"
                    }
                    form(action = "/syncs/$syncName/keypair/generate", method = FormMethod.post) {
                        button(type = ButtonType.submit) { +"鍵ペアを生成する" }
                    }
                    small(classes = "muted") {
                        +"保存先: "
                        code { +ctx.publicKeyManager.pathString() }
                        +" (公開鍵) / 同ディレクトリの "
                        code { +"private-key.pem" }
                        +" (秘密鍵, パーミッション 600)"
                    }
                }
            } else {
                p { +"以下の公開鍵を kintone コネクター管理画面で登録してください。" }
                div(classes = "public-key-display") {
                    code(classes = "stdout-debug") { +publicKey }
                }
                div(classes = "action-bar") {
                    button(type = ButtonType.button, classes = "secondary") {
                        attributes["onclick"] = "copyPublicKey()"
                        +"公開鍵をコピー"
                    }
                    a(href = "/syncs/$syncName/public-key.pem", classes = "button secondary outline") {
                        attributes["download"] = "public-key.pem"
                        +"↓ ダウンロード"
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

        // Step 2: kintone 管理画面での操作案内
        //
        // ドメインの入力欄は置かない。Adapter は kintone のドメインを必要としない
        // （Agent が kintone へアウトバウンドで接続し、Adapter は Agent に gRPC を
        // 提供するだけ）。以前は入力欄と「保存して開く」があったが、保存先が未実装で
        // KINTONE_DOMAIN もコンテナに渡っておらず、押しても何も起きなかった (Issue #51)。
        section {
            h3 { +"Step 2: kintone 管理画面でコネクタを追加" }
            p {
                +"kintone の管理画面で「外部システムのアプリ化」を開き、この連携に対応する"
                +"コネクタを追加してください。発行された接続キーを Step 3 に貼り付けます。"
            }
            p {
                +"管理画面のパス: "
                code { +"https://<kintone ドメイン>$ADMIN_CONNECTOR_PATH" }
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
                div(classes = "action-bar") {
                    button(type = ButtonType.submit) { +"接続して開始 ▶" }
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

/** kintone 管理画面の「外部システムのアプリ化」のパス。 */
private const val ADMIN_CONNECTOR_PATH = "/k/admin/system/admin/dataConnector.html"

/**
 * 接続キーを拒否されたときの kintone 側の手順 (Issue #21)。
 *
 * 「接続キーを再発行してください」だけでは、どの画面のどの操作か分からない。
 * また拒否の原因は有効期限切れとは限らないため（kintone 側で接続を作り直すと、
 * 署名も期限も有効なまま拒否される）、その点も明記する。
 */
private fun kotlinx.html.FlowContent.authRejectedGuide() {
    article(classes = "warning-banner") {
        h4 { +"kintone 側で行う操作" }
        ol {
            li { +"kintone の管理画面で「外部システムのアプリ化」を開く (パスは下の Step 2)" }
            li { +"この連携に対応する接続を選ぶ" }
            li { +"接続キーを再発行する" }
            li { +"発行された接続キーを下の Step 3 に貼り付けて「接続して開始」" }
        }
        p {
            small {
                +"接続を作り直した場合、以前の接続キーは"
                strong { +"有効期限内でも拒否されます" }
                +"。接続キーは kintone 側の接続そのものに紐づくため、"
                +"接続を再作成すると古いキーは無効になります。"
            }
        }
    }
}
