package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.error.ErrorMessageTranslator
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.jdbc.JdbcUrlEnhancer
import com.cdata.kintone.adapter.jdbc.OAuthAuthorizer
import com.cdata.kintone.adapter.jdbc.OAuthCapability
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.views.OAuthNotice
import com.cdata.kintone.adapter.web.views.connectionOAuthView
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private val log = KotlinLogging.logger {}

/**
 * OAuth 認可ウィザード。ヘッドレスなコンテナのまま初回認可を完結させる。
 *
 * ドライバーの `InitiateOAuth=GETANDREFRESH` は自分のマシンでブラウザを開く方式で
 * コンテナでは成立しないため、`GetOAuthAuthorizationUrl` / `GetOAuthAccessToken` の
 * 2 段階フローを使う。
 *
 * 関連: [Issue #12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12)
 */
fun Route.connectionOAuthRoutes(ctx: AppContext) {

    /** 認可 URL を生成して表示する。 */
    get("/connections/{name}/oauth") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@get call.respondText("Not found: $name", status = HttpStatusCode.NotFound)

        val notice = runCatching { authorizationNotice(name, config) }
            .getOrElse { failureNotice(it, "認可 URL の生成に失敗しました") }
        call.respondHtml { connectionOAuthView(ctx, name, notice) }
    }

    /** 認可コードからトークンを取得し、OAuth キャッシュに保存する。 */
    post("/connections/{name}/oauth/token") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        // 認可コードはログに出さない (Issue #12)。
        val verifier = call.receiveParameters()["verifier"]?.trim()

        val notice = if (verifier.isNullOrBlank()) {
            OAuthNotice(error = ErrorMessageTranslator.translate("認可コードを入力してください。"))
        } else {
            runCatching { completionNotice(name, config, verifier) }
                .getOrElse { failureNotice(it, "トークンの取得に失敗しました") }
        }
        call.respondHtml { connectionOAuthView(ctx, name, notice) }
    }
}

/** 認可 URL を生成する。プロシージャが無いドライバーは専用の案内を返す。 */
private fun authorizationNotice(name: String, config: JdbcConfig): OAuthNotice =
    withAuthorizer(name, config) { authorizer ->
        if (!authorizer.hasAuthorizationProcedure()) {
            return@withAuthorizer OAuthNotice(error = unsupportedDriverMessage())
        }
        val callbackUrl = OAuthCapability.callbackUrlOf(config.url)
        OAuthNotice(
            authorizationUrl = authorizer.authorizationUrl(callbackUrl),
            callbackUrl = callbackUrl,
        )
    }

private fun completionNotice(name: String, config: JdbcConfig, verifier: String): OAuthNotice =
    withAuthorizer(name, config) { authorizer ->
        authorizer.fetchAccessToken(verifier, OAuthCapability.callbackUrlOf(config.url))
        OAuthNotice(
            message = "OAuth トークンを取得して保存しました。接続テストで確認してください。",
        )
    }

/**
 * OAuth プロシージャ実行用の接続を張る。
 *
 * `InitiateOAuth=OFF` を強制する。`GETANDREFRESH` のままだとドライバーが接続時に
 * ブラウザを開こうとして 60 秒タイムアウトする。
 * OAuth キャッシュのパスは #11 で一本化した接続単位のものを使う。
 */
private fun <T> withAuthorizer(name: String, config: JdbcConfig, block: (OAuthAuthorizer) -> T): T {
    val headless = config.copy(url = JdbcUrlEnhancer.withInitiateOAuthOff(config.url))
    return JdbcConnectionProvider(headless, oauthCacheKey = name).use { provider ->
        provider.connection().use { connection -> block(OAuthAuthorizer(connection)) }
    }
}

private fun unsupportedDriverMessage() = ErrorMessageTranslator.UserMessage(
    text = "このドライバーは Web UI からの OAuth 認可に対応していません " +
        "(認可用のストアドプロシージャを持っていません)。" +
        "取得済みのトークン（リフレッシュトークン / JWT 等）を接続設定に入力してください。",
    severity = ErrorMessageTranslator.Severity.ERROR,
    actions = listOf(ErrorMessageTranslator.UserAction.GO_TO_CONNECTIONS),
)

/** 例外を利用者向けメッセージに変換する。認可コードは含めない。 */
private fun failureNotice(cause: Throwable, context: String): OAuthNotice {
    log.warn(cause) { context }
    return OAuthNotice(error = ErrorMessageTranslator.translate(cause.message ?: context))
}
