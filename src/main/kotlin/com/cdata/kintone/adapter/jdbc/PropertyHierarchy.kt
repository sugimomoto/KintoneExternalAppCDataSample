package com.cdata.kintone.adapter.jdbc

/**
 * `sys_connection_props` の `Hierarchy` 列が表す条件。
 *
 * 「[dependsOn] の値が [allowedValues] のいずれかのときだけ、このプロパティは意味を持つ」。
 * 形式は `<依存プロパティ名>=<値1>,<値2>,...`
 * （例: `AuthScheme=Basic,OAuthPassword,OneLogin,PingFederate,OKTA,ADFS`）。
 *
 * 同梱 4 ドライバーの全プロパティを走査した範囲では `=` はちょうど 1 個、
 * 区切りは `,` のみで、入れ子や AND / OR 表現は存在しなかった。
 *
 * 関連: [Issue #14](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/14)
 */
data class PropertyHierarchy(
    val dependsOn: String,
    val allowedValues: List<String>,
) {

    /**
     * 大文字小文字を無視して判定する。
     * 同一ドライバー内でも表記が揺れる（Salesforce は `OKTA` と `Okta` の両方を返す）。
     */
    fun accepts(value: String): Boolean = allowedValues.any { it.equals(value, ignoreCase = true) }

    companion object {
        /** 解析できない場合は null を返す。呼び出し側は「条件なし」として扱う。 */
        fun parse(raw: String): PropertyHierarchy? = TODO()
    }
}
