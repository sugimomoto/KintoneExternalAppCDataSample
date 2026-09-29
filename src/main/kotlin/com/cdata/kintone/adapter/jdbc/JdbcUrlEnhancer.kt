package com.cdata.kintone.adapter.jdbc

/**
 * CData JDBC URL に対するヘルパ。
 *
 * 主用途は OAuth キャッシュ場所 (`OAuthSettingsLocation`) の決定。
 * **キャッシュパスを決めるのはここだけ**にする。経路ごとに書くと、接続テストと
 * 実行時で保存先が食い違う (Issue #11)。付与自体は [JdbcConnectionProvider] が
 * 必ず行うため、呼び出し側が忘れることはない。
 */
object JdbcUrlEnhancer {

    /**
     * URL に `OAuthSettingsLocation=<path>` を付与する。
     * 既にユーザが明示指定している場合は尊重し、URL を変更しない。
     */
    fun withOAuthCache(jdbcUrl: String, cachePath: String): String {
        if (KEY_REGEX.containsMatchIn(jdbcUrl)) {
            return jdbcUrl // 既に明示指定あり
        }
        val trimmed = jdbcUrl.trimEnd(';')
        return "$trimmed;OAuthSettingsLocation=$cachePath"
    }

    /**
     * `OAuthSettingsLocation` のキャッシュパスを `<baseDir>/oauth/<safeKey>.txt` 形式で返す。
     *
     * [cacheKey] はデータソース接続名（共有接続を参照しない連携では連携名）。
     * 名前に含まれる `/` や `..` 等のパス区切り文字は `_` に変換し、
     * ディレクトリを抜け出せないようにする。
     */
    fun cachePathFor(baseDir: String, cacheKey: String): String {
        val safe = cacheKey.replace(Regex("[/\\\\.]"), "_")
        val base = baseDir.trimEnd('/', '\\')
        return "$base/oauth/$safe.txt"
    }

    private val KEY_REGEX = Regex("""(?i)(?:^|;)\s*OAuthSettingsLocation\s*=""")
}
