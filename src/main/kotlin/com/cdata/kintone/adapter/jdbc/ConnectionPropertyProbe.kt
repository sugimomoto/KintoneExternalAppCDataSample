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

    /** 真偽値プロパティに使われがちな接頭辞。文字列ダミーを入れると型検証で落ちる。 */
    private val BOOLEAN_NAME_PREFIXES =
        listOf("use", "is", "enable", "allow", "ignore", "recurse", "include", "exclude")

    /**
     * 試行順に並んだプローブ接続文字列を返す（重複は除去済み）。
     * 呼び出し側は先頭から試し、最初に成功したものの結果を使う。
     */
    fun candidateUrls(jdbcPrefix: String, driverProperties: List<DriverProperty>): List<String> = TODO()

    /** プロパティ名から、書式検証を通しやすいダミー値を決める。 */
    fun dummyValueFor(propertyName: String): String = TODO()
}
