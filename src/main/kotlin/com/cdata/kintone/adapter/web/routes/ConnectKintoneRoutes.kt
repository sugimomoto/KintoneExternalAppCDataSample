package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.agent.KeyPairGeneratorService
import com.cdata.kintone.adapter.agent.SyncConnectionService
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.ConnectNotice
import com.cdata.kintone.adapter.web.views.connectKintoneView
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * 接続結果を画面の通知に変換する。
 *
 * `AuthRejected` だけ復旧手順が違う（接続キーの再発行が必要）ため、
 * kintone 側の手順を出すフラグを立てる (Issue #21)。
 */
private fun noticeFor(result: SyncConnectionService.Result): ConnectNotice = when (result) {
    is SyncConnectionService.Result.Success ->
        ConnectNotice(message = "kintone との接続が確立されました。")

    is SyncConnectionService.Result.Pending ->
        ConnectNotice(error = result.reason)

    is SyncConnectionService.Result.Failure ->
        ConnectNotice(error = result.reason)

    is SyncConnectionService.Result.AuthRejected ->
        ConnectNotice(error = result.reason, authRejected = true)
}

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
        val keypairFlag = call.request.queryParameters["keypair"]
        val (infoMessage, error) = when (keypairFlag) {
            "generated" -> "RSA 2048 ビットの鍵ペアを生成しました。公開鍵を kintone に登録してください。" to null
            "already-exists" -> null to "既に鍵ペアが存在します。誤上書きを防ぐため生成をスキップしました。"
            "failed" -> null to "鍵生成に失敗しました。サーバログを確認してください。"
            else -> null to null
        }
        call.respondHtml { connectKintoneView(ctx, syncName, ConnectNotice(infoMessage = infoMessage, error = error)) }
    }

    /** 「接続して開始」 1 ボタン処理。 */
    post("/syncs/{name}/connect") {
        val syncName = call.parameters["name"]!!
        val form = call.receiveParameters()
        val token = form["token"]?.trim()
            ?: return@post call.respondHtml {
                connectKintoneView(ctx, syncName, ConnectNotice(error = "接続キーを入力してください"))
            }
        val notice = noticeFor(ctx.syncConnectionService.connect(syncName, token))
        call.respondHtml { connectKintoneView(ctx, syncName, notice) }
    }

    /** Agent 用 RSA 鍵ペアを生成 (公開鍵が無いときに UI から呼ばれる)。 */
    post("/syncs/{name}/keypair/generate") {
        val syncName = call.parameters["name"]!!
        val flag = when (ctx.keyPairGeneratorService.generate()) {
            KeyPairGeneratorService.GenerationResult.Success -> "generated"
            KeyPairGeneratorService.GenerationResult.AlreadyExists -> "already-exists"
            is KeyPairGeneratorService.GenerationResult.Failed -> "failed"
        }
        call.respondRedirect("/syncs/$syncName/connect?keypair=$flag")
    }
}
