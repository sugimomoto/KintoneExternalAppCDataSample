package com.cdata.kintone.adapter.jdbc

/**
 * 認可コード入力の解釈結果。
 *
 * `error=` を呼び出し側に伝えるために型で分ける。`String?` では「認可を拒否された」
 * ことを表現できず、コードが不正だったのと区別がつかない。
 *
 * 関連: [Issue #57](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/57)
 */
sealed interface AuthorizationCodeInput {

    /** 認可コードが取れた。 */
    data class Code(val value: String) : AuthorizationCodeInput

    /** 認可が拒否された。[value] はプロバイダが返した値（`access_denied` 等）。 */
    data class Error(val value: String) : AuthorizationCodeInput

    /** 入力が空。 */
    data object Empty : AuthorizationCodeInput
}
