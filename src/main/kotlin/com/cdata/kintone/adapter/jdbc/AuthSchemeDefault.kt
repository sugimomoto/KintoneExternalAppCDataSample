package com.cdata.kintone.adapter.jdbc

/**
 * ドライバーが返す `AuthScheme` の既定値を取り出す。
 *
 * #27 以降、既定値は接続文字列に保存されない。実効値を求めるには
 * [OAuthCapability.effectiveAuthScheme] にこの既定値を渡す必要がある。
 *
 * 編集画面の OAuth 導線と、新規保存時のリダイレクト判定の両方で使うため、
 * 取り出し方をここに一本化する。
 *
 * 関連: [Issue #43](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/43)
 */
fun authSchemeDefaultOf(properties: ConnectionPropertiesResult?): String? =
    properties?.properties
        ?.firstOrNull { it.propertyName.equals("AuthScheme", ignoreCase = true) }
        ?.defaultValue
