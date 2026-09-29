package com.cdata.kintone.adapter.jdbc

/**
 * `sys_connection_props` を読むための「プローブ接続文字列」を組み立てる。
 *
 * 26.x 系のドライバは `getConnection` の時点でプロパティ検証を行うため、
 * 空の接続文字列 (`jdbc:sapgateway:`) では接続できずプロパティ一覧が取れない。
 * 一方 25.x 系は空でも取得できる。ドライバ固有の分岐を持たずに両方を通すため、
 * **候補を順に試して最初に成功したものを使う**方式を採る。
 *
 * ダミー値には書式検証があり（例: SAP Gateway の `URL` は `^(http|https):\/\/.*`）、
 * 書式情報を得る API は存在しない。そのためプロパティ名からの推測に留め、
 * 通らなければ呼び出し側が縮退フォームへ落とす。
 *
 * 関連: [Issue #15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15)
 */
object ConnectionPropertyProbe {

    /**
     * ダミーの URL 値。`.invalid` は RFC 2606 の予約 TLD で名前解決されないことが保証される。
     * `localhost` を使うと手元で動いているサービスに接続しかねないため避けている。
     */
    const val PROBE_URL = "http://cdata-probe.invalid"

    /** URL 以外の文字列プロパティに入れる汎用ダミー値。 */
    const val PROBE_VALUE = "probe"

    /**
     * メタデータ取得中に実通信・OAuth 認可を起こさないためのプロパティ。
     * ドライバが持たないプロパティを指定すると接続自体が失敗するため、
     * 付与は [DriverProperty] に存在するものだけに限る。
     */
    private val SAFETY_PROPERTIES = listOf("Offline" to "true", "InitiateOAuth" to "OFF")

    /**
     * 真偽値プロパティに使われがちな接頭辞。文字列ダミーを入れると型検証で落ちる。
     * 必須プロパティに真偽値が含まれる実例がある（Salesforce の `UseSandbox`、
     * Google Sheets の `RecurseFolders` / `IgnoreErrorValues`）。
     */
    private val BOOLEAN_NAME_PREFIXES =
        listOf("use", "is", "enable", "allow", "ignore", "recurse", "include", "exclude")

    /**
     * URL 形式を要求するプロパティ名の判定。部分一致では判定しない。
     * `security` に `uri` が含まれるため、`SecurityToken` のようなプロパティを
     * 巻き込んでしまう。
     */
    private fun isUrlProperty(normalizedName: String): Boolean =
        normalizedName.endsWith("url") ||
            normalizedName.endsWith("uri") ||
            normalizedName.startsWith("url") ||
            normalizedName.contains("endpoint")

    /**
     * 真偽値プロパティの判定。接頭辞は **camelCase の語境界**でのみ一致とみなす。
     * 単純な前方一致だと `User` が `Use` + `r` として真偽値扱いされ、
     * 多くのデータソースで必須の文字列プロパティを壊す。
     */
    private fun isBooleanProperty(name: String): Boolean =
        BOOLEAN_NAME_PREFIXES.any { prefix ->
            name.length > prefix.length &&
                name.startsWith(prefix, ignoreCase = true) &&
                name[prefix.length].isUpperCase()
        }

    /**
     * 試行順に並んだプローブ接続文字列を返す（重複は除去済み）。
     * 呼び出し側は先頭から試し、最初に成功したものの結果を使う。
     */
    fun candidateUrls(jdbcPrefix: String, driverProperties: List<DriverProperty>): List<String> {
        val availableNames = driverProperties.map { it.name.trim().lowercase() }.toSet()
        val safety = SAFETY_PROPERTIES
            .filter { (name, _) -> name.lowercase() in availableNames }
            .joinToString("") { (name, value) -> "$name=$value;" }
        val dummies = driverProperties
            .filter { it.required }
            .joinToString("") { "${it.name.trim()}=${dummyValueFor(it.name)};" }

        // 安全プロパティ・ダミー値がいずれも空になると候補が重複するため distinct で畳む。
        return listOf(
            "$jdbcPrefix:$safety",
            "$jdbcPrefix:",
            "$jdbcPrefix:$safety$dummies",
            "$jdbcPrefix:$dummies",
        ).distinct()
    }

    /** プロパティ名から、書式検証を通しやすいダミー値を決める。 */
    fun dummyValueFor(propertyName: String): String {
        val name = propertyName.trim()
        val normalized = name.lowercase()
        return when {
            isUrlProperty(normalized) -> PROBE_URL
            normalized.contains("port") -> "443"
            isBooleanProperty(name) -> "False"
            else -> PROBE_VALUE
        }
    }
}
