package com.cdata.kintone.adapter.jdbc

/**
 * CData JDBC URL に対するヘルパ。
 *
 * フェーズ2-A の主用途は OAuth キャッシュ場所 (`OAuthSettingsLocation`) を
 * テーブル別に自動分離すること。
 */
object JdbcUrlEnhancer {

    /**
     * URL に `OAuthSettingsLocation=<path>` を付与する。
     * 既にユーザが明示指定している場合は尊重し、URL を変更しない。
     */
    fun withOAuthCachePerTable(jdbcUrl: String, cachePath: String): String {
        if (KEY_REGEX.containsMatchIn(jdbcUrl)) {
            return jdbcUrl // 既に明示指定あり
        }
        val trimmed = jdbcUrl.trimEnd(';')
        return "$trimmed;OAuthSettingsLocation=$cachePath"
    }

    /**
     * `OAuthSettingsLocation` のキャッシュパスを `<configDir>/oauth/<safeName>.txt` 形式で返す。
     * `tableName` に含まれる `/` や `..` 等のパス区切り文字は `_` に変換する。
     */
    fun cachePathFor(configDir: String, tableName: String): String {
        val safe = tableName.replace(Regex("[/\\\\.]"), "_")
        val base = configDir.trimEnd('/', '\\')
        return "$base/oauth/$safe.txt"
    }

    private val KEY_REGEX = Regex("""(?i)(?:^|;)\s*OAuthSettingsLocation\s*=""")
}
