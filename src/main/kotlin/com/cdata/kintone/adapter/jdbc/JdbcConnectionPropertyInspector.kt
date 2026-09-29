package com.cdata.kintone.adapter.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * CData ドライバの接続プロパティ一覧を取得する。Web UI の動的フォーム生成に使う。
 *
 * 第一の取得元は `sys_connection_props` システムテーブルだが、読むには接続の確立が必要で、
 * 26.x 系のドライバは空の接続文字列を検証で弾く。そのため
 * [ConnectionPropertyProbe] が生成した候補を順に試し、いずれも失敗した場合は
 * `Driver.getPropertyInfo` 由来の縮退結果へ落とす。
 *
 * 設計詳細: `.steering/20260929-connection-props-fetch-fallback/design.md`
 * 関連: [Issue #15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15)
 */
class JdbcConnectionPropertyInspector(
    private val libDir: Path = Path.of("./lib"),
    private val metadataSource: DriverMetadataSource = JdbcDriverMetadataSource(),
) {

    private data class CacheKey(val driverClass: String, val jarLastModified: Long)

    /**
     * 完全取得 (`sys_connection_props`) の結果のみを保持する。
     * 縮退結果をキャッシュすると、原因を解消しても画面が回復しなくなるため。
     */
    private val cache = mutableMapOf<CacheKey, List<ConnectionProperty>>()

    /** 指定ドライバの接続プロパティを、取得経路の情報付きで返す。 */
    fun fetchProperties(driverClass: String, jarFilename: String): ConnectionPropertiesResult = TODO()

    fun invalidateCache() {
        cache.clear()
    }

    companion object {
        /**
         * `cdata.jdbc.salesforce.SalesforceDriver` から `jdbc:salesforce` を導出。
         * CData 全製品共通のパッケージ命名規約に依存。
         */
        fun jdbcPrefixOf(driverClass: String): String {
            val parts = driverClass.split('.')
            require(parts.size >= 3 && parts[0] == "cdata" && parts[1] == "jdbc") {
                "Not a CData JDBC driver class: $driverClass"
            }
            return "jdbc:${parts[2]}"
        }
    }
}
