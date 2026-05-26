package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.agent.KeyPairGeneratorService
import com.cdata.kintone.adapter.agent.SyncConnectionService
import com.cdata.kintone.adapter.web.AppContext
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
        call.respondHtml { connectKintoneView(ctx, syncName, infoMessage = infoMessage, error = error) }
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

    /**
     * kintone ドメインの永続化エンドポイント (未実装スタブ)。
     * 現状は環境変数 KINTONE_DOMAIN を使う運用のため、何も保存しない。
     */
    post("/syncs/{name}/connect/save-domain") {
        call.respondText(
            "ドメインは現状 KINTONE_DOMAIN 環境変数から読み込んでいます。永続化は未実装です。",
            status = HttpStatusCode.OK,
        )
    }
}
