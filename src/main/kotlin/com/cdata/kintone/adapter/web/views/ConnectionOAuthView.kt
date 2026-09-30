package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.error.ErrorMessageTranslator
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
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.small
import kotlinx.html.strong
import kotlinx.html.textArea

/**
 * OAuth 認可ウィザード。
 *
 * ドライバーの `InitiateOAuth=GETANDREFRESH` は**ドライバーが動いているマシンで
 * ブラウザを開く**方式のため、ヘッドレスなコンテナでは成立しない。
 * 代わりにヘッドレス向けの 2 段階フローを使い、管理者自身のブラウザで認可させる。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
fun HTML.connectionOAuthView(
    ctx: AppContext,
    connectionName: String,
    notice: OAuthNotice = OAuthNotice(),
) {
    layout(
        pageTitle = "OAuth 認可: $connectionName",
        activeCount = ctx.runner.listActive().size,
        currentPath = "/connections",
    ) {
        h2 { +"OAuth 認可: $connectionName" }

        notice.message?.let { message ->
            article(classes = "success-banner") {
                p { +"✅ $message" }
                div(classes = "action-bar") {
                    a(href = "/connections/$connectionName/edit", classes = "button") { +"接続設定に戻る →" }
                }
            }
            return@layout
        }

        notice.error?.let { error ->
            oauthErrorBanner(error)
        }

        notice.authorizationUrl?.let { url ->
            authorizationSteps(connectionName, url, notice.callbackUrl)
        }

        div(classes = "action-bar") {
            a(href = "/connections/$connectionName/edit", classes = "button secondary outline") { +"接続設定に戻る" }
        }
    }
}

private fun kotlinx.html.FlowContent.oauthErrorBanner(error: ErrorMessageTranslator.UserMessage) {
    article(classes = "warning-banner") {
        p { +"⚠ ${error.text}" }
        if (error.actions.isNotEmpty()) {
            div(classes = "action-bar") {
                error.actions.forEach { action ->
                    a(href = action.href, classes = "button secondary outline") { +action.label }
                }
            }
        }
        error.rawMessage?.takeIf { it != error.text }?.let { raw ->
            p { small(classes = "muted") { +"詳細: $raw" } }
        }
    }
}

private fun kotlinx.html.FlowContent.authorizationSteps(
    connectionName: String,
    authorizationUrl: String,
    callbackUrl: String?,
) {
    section {
        h3 { +"Step 1: 認可 URL を開く" }
        p {
            small {
                +"下のリンクを"
                strong { +"あなたの PC のブラウザ" }
                +"で開き、データソース側で認可してください。"
                +"サーバー側ではブラウザを開けないため、この手順が必要です。"
            }
        }
        div(classes = "action-bar") {
            a(href = authorizationUrl, target = "_blank", classes = "button") { +"認可 URL を開く ↗" }
        }
        label {
            +"コピー用:"
            textArea {
                attributes["readonly"] = "readonly"
                rows = "3"
                +authorizationUrl
            }
        }
    }

    section {
        h3 { +"Step 2: リダイレクト先の URL を貼り付ける" }
        article(classes = "warning-banner") {
            p {
                +"認可後のリダイレクト先 ("
                code { +(callbackUrl ?: "ドライバー既定の localhost") }
                +") はブラウザでエラー表示になりますが、"
                strong { +"アドレスバーの URL に認可コードが含まれています" }
                +"。"
            }
            ol {
                li { +"Step 1 のリンクを開いて認可する" }
                li { +"リダイレクト後のアドレスバーの URL を全部コピーする" }
                li { +"下の欄に貼り付けて「認可を完了する」" }
            }
        }
        form(action = "/connections/$connectionName/oauth/token", method = FormMethod.post) {
            label {
                +"リダイレクト先の URL（または認可コード）:"
                input(type = InputType.text, name = "verifier") {
                    required = true
                    placeholder = "http://localhost:33333/?code=..."
                }
            }
            button(type = ButtonType.submit) { +"認可を完了する" }
        }
        p {
            small(classes = "muted") {
                +"URL から code= の値を自動で取り出します。認可コードだけを貼り付けても構いません。"
                +"認可コードは 1 回しか使えないため、失敗した場合は Step 1 からやり直してください。"
            }
        }
    }
}
