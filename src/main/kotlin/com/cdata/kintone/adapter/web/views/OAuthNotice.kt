package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.error.ErrorMessageTranslator

/**
 * OAuth 認可ウィザードに出す通知。
 * 個別の引数にすると `connectionOAuthView` の引数が増え続けるためまとめている。
 */
data class OAuthNotice(
    /** 生成した認可 URL。これがあると Step 1 / Step 2 を表示する。 */
    val authorizationUrl: String? = null,
    /** 接続文字列に明示された `CallbackURL`。未指定ならドライバー既定が使われる。 */
    val callbackUrl: String? = null,
    /** 認可完了。これがあると完了表示にする。 */
    val message: String? = null,
    val error: ErrorMessageTranslator.UserMessage? = null,
)
