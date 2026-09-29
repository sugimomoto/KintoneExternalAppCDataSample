package com.cdata.kintone.adapter.config

/**
 * 設定値に埋め込まれた `${VAR}` 形式のプレースホルダを環境変数で置換する。
 *
 * 接続文字列に認証情報を直接書かずに済ませるための仕組みで、
 * 設定ストアの実装（YAML / SQLite など）に依存しない共通処理として切り出している。
 */
object EnvVarExpander {
    private val ENV_VAR_REGEX = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")

    /**
     * [text] 中の `${VAR}` を [envResolver] の戻り値で置換する。
     * 解決できない変数はプレースホルダのまま残す（設定ミスを握りつぶさないため）。
     */
    fun expand(
        text: String,
        envResolver: (String) -> String?,
    ): String =
        ENV_VAR_REGEX.replace(text) { match ->
            envResolver(match.groupValues[1]) ?: match.value
        }
}
