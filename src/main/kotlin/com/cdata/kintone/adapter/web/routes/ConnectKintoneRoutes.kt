package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.agent.SyncConnectionService
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.connectKintoneView
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.connectKintoneRoutes(ctx: AppContext) {

    /** 公開鍵ダウンロード (KC-01)。 */
    get("/syncs/{name}/public-key.pem") {
        val bytes = ctx.publicKeyManager.bytes()
            ?: return@get call.respondText("公開鍵が見つかりません", status = HttpStatusCode.NotFound)
        call.response.header("Content-Disposition", "attachment; filename=\"public-key.pem\"")
        call.respondBytes(bytes, ContentType.parse("application/x-pem-file"))
    }

    /** kintone 接続画面 (KC-03)。 */
    get("/syncs/{name}/connect") {
        val syncName = call.parameters["name"]!!
        call.respondHtml { connectKintoneView(ctx, syncName) }
    }

    /** 「接続して開始」 1 ボタン処理。 */
    post("/syncs/{name}/connect") {
        val syncName = call.parameters["name"]!!
        val form = call.receiveParameters()
        val token = form["token"]?.trim()
            ?: return@post call.respondHtml {
                connectKintoneView(ctx, syncName, error = "接続キーを入力してください")
            }
        val result = ctx.syncConnectionService.connect(syncName, token)
        when (result) {
            is SyncConnectionService.Result.Success ->
                call.respondHtml {
                    connectKintoneView(ctx, syncName, message = "kintone との接続が確立されました。")
                }
            is SyncConnectionService.Result.Pending ->
                call.respondHtml {
                    connectKintoneView(ctx, syncName, error = result.reason)
                }
            is SyncConnectionService.Result.Failure ->
                call.respondHtml {
                    connectKintoneView(ctx, syncName, error = result.reason)
                }
        }
    }

    /** kintone ドメイン保存 (Phase 2-D で永続化、現状はリダイレクトのみ)。 */
    post("/syncs/{name}/connect/save-domain") {
        val syncName = call.parameters["name"]!!
        // TODO: KintonePreferences 実装で永続化
        call.respondText(
            "ドメイン永続化は Phase 2-D で実装予定です。当面は環境変数 KINTONE_DOMAIN をご利用ください。",
            status = HttpStatusCode.OK,
        )
    }
}
