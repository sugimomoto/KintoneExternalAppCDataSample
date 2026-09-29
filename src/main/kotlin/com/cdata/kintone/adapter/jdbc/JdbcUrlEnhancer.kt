package com.cdata.kintone.adapter.jdbc

import com.cdata.kintone.adapter.config.JdbcConfig

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

    /**
     * OAuth キャッシュパスを付与した設定を返す。**接続を張る経路はこれを通る。**
     *
     * `OAuthSettingsLocation` は CData ドライバー固有のプロパティなので、
     * CData 以外のドライバーには付与しない。付与すると URL の解釈が壊れる
     * （H2 は未知の `;KEY=VALUE` を接続エラーにする）。
     */
    fun applyOAuthCache(config: JdbcConfig, cacheKey: String, baseDir: String): JdbcConfig {
        if (!isCDataDriver(config.driverClass)) return config
        return config.copy(url = withOAuthCache(config.url, cachePathFor(baseDir, cacheKey)))
    }

    /**
     * `cdata.jdbc.<product>.<Driver>` 形式のドライバークラスか。
     * CData 固有プロパティを付与してよいかの判定に使う。
     */
    fun isCDataDriver(driverClass: String): Boolean {
        val parts = driverClass.split('.')
        return parts.size >= CDATA_CLASS_MIN_PARTS && parts[0] == "cdata" && parts[1] == "jdbc"
    }

    /** `cdata.jdbc.<product>.<Driver>` の最小要素数。 */
    private const val CDATA_CLASS_MIN_PARTS = 3

    /**
     * 明示指定の検出。区切りは `;` だけでなく `:` も見る。
     * CData の接続文字列は `jdbc:<product>:<最初のプロパティ>=...` の形なので、
     * 最初のプロパティに書かれた場合は直前が `:` になる。
     */
    private val KEY_REGEX = Regex("""(?i)(?:^|[;:])\s*OAuthSettingsLocation\s*=""")
}
