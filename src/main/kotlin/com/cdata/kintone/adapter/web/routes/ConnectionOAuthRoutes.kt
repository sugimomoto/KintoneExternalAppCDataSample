package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.error.ErrorMessageTranslator
import com.cdata.kintone.adapter.jdbc.AuthorizationCodeExtractor
import com.cdata.kintone.adapter.jdbc.AuthorizationCodeInput
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.jdbc.JdbcUrlEnhancer
import com.cdata.kintone.adapter.jdbc.OAuthAuthorizer
import com.cdata.kintone.adapter.jdbc.OAuthCapability
import com.cdata.kintone.adapter.jdbc.OAuthTokens
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

    /** 認可コードからトークンを取得し、接続設定に保存する。 */
    post("/connections/{name}/oauth/token") {
        val name = call.parameters["name"]!!
        val config = ctx.configSource.loadSharedJdbcConfig(name)
            ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)
        // 入力値はログに出さない。リダイレクト URL ごと認可コードを含む (Issue #12)。
        // URL をそのまま貼れるように code= を抽出する (Issue #57)。
        val input = AuthorizationCodeExtractor.extract(call.receiveParameters()["verifier"].orEmpty())

        val notice = when (input) {
            is AuthorizationCodeInput.Empty -> OAuthNotice(
                error = ErrorMessageTranslator.translate(
                    "認可コード、またはリダイレクト先の URL を入力してください。",
                ),
            )

            // プロバイダが返す値 (access_denied 等) をそのまま見せる。
            // ロケール依存のメッセージは分類しない方針 (#19)。
            is AuthorizationCodeInput.Error -> OAuthNotice(
                error = ErrorMessageTranslator.translate(
                    "認可が拒否されました (${input.value})。Step 1 からやり直してください。",
                ),
            )

            is AuthorizationCodeInput.Code ->
                runCatching { completionNotice(ctx, name, config, input.value) }
                    .getOrElse { failureNotice(it, "トークンの取得に失敗しました") }
        }
        call.respondHtml { connectionOAuthView(ctx, name, notice) }
    }
}

/**
 * 認可 URL を生成する。プロシージャが無いドライバーは専用の案内を返す。
 *
 * コールバックは [OAuthCapability.effectiveCallbackUrl] で決める。未設定のまま
 * ドライバー既定値に委ねると Google 系が OOB になり認可できない (Issue #55)。
 */
private fun authorizationNotice(name: String, config: JdbcConfig): OAuthNotice =
    withAuthorizer(name, config) { authorizer ->
        if (!authorizer.hasAuthorizationProcedure()) {
            return@withAuthorizer OAuthNotice(error = unsupportedDriverMessage())
        }
        val callbackUrl = OAuthCapability.effectiveCallbackUrl(config.url)
        OAuthNotice(
            authorizationUrl = authorizer.authorizationUrl(callbackUrl),
            callbackUrl = callbackUrl,
        )
    }

/**
 * 認可コードからトークンを取得し、**接続設定に保存する**。
 *
 * プロシージャを実行しただけではキャッシュは作られないため、取得した
 * リフレッシュトークンを `InitiateOAuth=REFRESH` とともに接続設定へ書き戻す (Issue #34)。
 * 取得できなかった場合は成功として扱わない。保存していないのに成功表示するのが
 * #34 の不具合の本質だった。
 */
private fun completionNotice(ctx: AppContext, name: String, config: JdbcConfig, verifier: String): OAuthNotice {
    val tokens = withAuthorizer(name, config) { authorizer ->
        authorizer.fetchAccessToken(verifier, OAuthCapability.effectiveCallbackUrl(config.url))
    }
    val refreshToken = tokens.refreshToken
        ?: return OAuthNotice(error = ErrorMessageTranslator.translate(missingRefreshTokenMessage(tokens)))

    ctx.configSource.saveSharedJdbcConfig(name, JdbcUrlEnhancer.withRefreshToken(config, refreshToken))
    log.info { "OAuth リフレッシュトークンを接続設定に保存しました: $name" }
    return OAuthNotice(
        message = "OAuth トークンを取得し、接続設定に保存しました " +
            "(InitiateOAuth=REFRESH)。接続テストで確認してください。",
    )
}

/**
 * リフレッシュトークンが取れなかった場合の説明。
 *
 * アクセストークンだけ返るケースと、列自体が想定と違うケースを見分けられるようにする (AC-5)。
 * トークンの値は出さない。
 */
private fun missingRefreshTokenMessage(tokens: OAuthTokens): String =
    if (tokens.accessToken != null) {
        "リフレッシュトークンを取得できませんでした。" +
            "アクセストークンのみ返っています。オフラインアクセスのスコープが" +
            "認可に含まれているか確認してください。"
    } else {
        "リフレッシュトークンを取得できませんでした。" +
            "ドライバーが ${OAuthTokens.REFRESH_TOKEN_COLUMN} 列を返していません。" +
            "認可コードが期限切れ・使用済みでないか確認してください。"
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
