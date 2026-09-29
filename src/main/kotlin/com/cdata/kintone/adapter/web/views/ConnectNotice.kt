package com.cdata.kintone.adapter.web.views

/**
 * 接続画面に出す通知。個別の引数にすると `connectKintoneView` の引数が増え続けるためまとめている。
 */
data class ConnectNotice(
    /** 接続成功。これがあるとフォームは出さず完了表示にする。 */
    val message: String? = null,
    val error: String? = null,
    val infoMessage: String? = null,
    /** 接続キーが kintone に拒否された場合。kintone 側の再発行手順を併せて出す (Issue #21)。 */
    val authRejected: Boolean = false,
)
