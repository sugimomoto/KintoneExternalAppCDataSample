package com.cdata.kintone.adapter.jdbc

/**
 * 接続テストの結果。
 *
 * `Connection.isValid` は失敗時に例外を投げず `false` を返すだけなので、
 * 「例外は出ていないが検証に失敗した」状態を表せる型が必要になる。
 * `Result<String>` ではこの状態を表現できない。
 *
 * 関連: [Issue #45](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/45)
 */
sealed interface ConnectionValidation {

    /** 実際に使える接続。[description] は接続先の製品名・バージョンとドライバー情報。 */
    data class Valid(val description: String) : ConnectionValidation

    /** 使えない接続。[reason] は利用者向けの説明。 */
    data class Invalid(val reason: String) : ConnectionValidation
}
