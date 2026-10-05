package com.cdata.kintone.adapter.jdbc

/**
 * `sys_connection_props` / `sys_procedures` を読むための **config 接続文字列**を組み立てる。
 *
 * CData の公式ドキュメントが指定する形式で、**有効な接続がなくてもメタデータを
 * クエリできる**。
 *
 * 以前はダミー値を詰めた候補接続文字列を総当たりしていた。SaaS コネクタは
 * プロパティ定義をドライバー内部に持つため接続が成立しなくても返るが、
 * **SQL Server のようなデータベース系ドライバーは `getConnection()` で実際に
 * `Server:Port` へ TCP 接続する**ため、名前解決されないダミー値では必ず失敗していた。
 * ダミー値を名前から推測する仕組み自体が不要になったため削除した (Issue #60)。
 *
 * 関連: [Issue #15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15),
 * [Issue #60](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/60)
 */
object ConnectionPropertyProbe {

    /**
     * ドライバークラスから config 接続文字列を組み立てる。
     *
     * 例: `cdata.jdbc.sql.SQLDriver` → `jdbc:cdata:sql:config:`
     *
     * **`cdata:` を挟んだ形が必須。** 短縮形では効かない（実測）。
     * - `jdbc:sql:config:` → `CORE The Username is missing.`
     * - `jdbc:salesforce:config:` → `'server' is not a valid connection property.`
     *
     * 接続値は一切渡さない。config 接続文字列は有効な接続を必要としないため、
     * 資格情報もダミー値も不要。
     *
     * @throws IllegalArgumentException CData JDBC Driver のクラスでない場合。
     *   呼び出し側はこれを捕まえて `getPropertyInfo` のフォールバックに落とす。
     */
    fun configUrl(driverClass: String): String = "jdbc:cdata:${productOf(driverClass)}:config:"

    /**
     * `cdata.jdbc.<product>.<Driver>` から製品名を取り出す。
     * CData 全製品共通のパッケージ命名規約に依存する
     * （[JdbcConnectionPropertyInspector.jdbcPrefixOf] と同じ規約）。
     */
    private fun productOf(driverClass: String): String {
        require(JdbcUrlEnhancer.isCDataDriver(driverClass)) {
            "Not a CData JDBC driver class: $driverClass"
        }
        return driverClass.split('.')[2]
    }
}
