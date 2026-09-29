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
    fun fetchProperties(driverClass: String, jarFilename: String): ConnectionPropertiesResult {
        val jarPath = libDir.resolve(jarFilename)
        if (!Files.exists(jarPath)) {
            log.warn { "ドライバ JAR が見つかりません: $jarPath" }
            return ConnectionPropertiesResult.unavailable(PropertySource.NONE_JAR_MISSING)
        }
        val jdbcPrefix = cdataPrefixOrNull(driverClass)
            ?: return ConnectionPropertiesResult.unavailable(PropertySource.NONE_NOT_CDATA_DRIVER)

        val key = CacheKey(driverClass, Files.getLastModifiedTime(jarPath).toMillis())
        return cache[key]?.let { ConnectionPropertiesResult(it, PropertySource.SYS_CONNECTION_PROPS) }
            ?: probe(key, jarPath, driverClass, jdbcPrefix)
    }

    private fun cdataPrefixOrNull(driverClass: String): String? =
        runCatching { jdbcPrefixOf(driverClass) }
            .onFailure { log.debug { "CData JDBC Driver ではないため動的フォームを生成しない: $driverClass" } }
            .getOrNull()

    private fun probe(
        key: CacheKey,
        jarPath: Path,
        driverClass: String,
        jdbcPrefix: String,
    ): ConnectionPropertiesResult {
        val loaded = runCatching { metadataSource.loadDriver(jarPath, driverClass) }
        if (loaded.isFailure) {
            log.warn(loaded.exceptionOrNull()) { "ドライバのロードに失敗: $driverClass" }
            return ConnectionPropertiesResult.unavailable(PropertySource.NONE_FETCH_FAILED)
        }

        // getPropertyInfo は接続を張らないので、プローブ用の安全プロパティ・必須プロパティを
        // 知るためにまず呼ぶ。取れなくてもプローブ自体は試す価値があるので続行する。
        val driverProperties = runCatching { metadataSource.driverProperties(jdbcPrefix) }
            .onFailure { log.debug(it) { "getPropertyInfo 取得失敗: $driverClass" } }
            .getOrDefault(emptyList())

        val fetched = firstSuccessfulProbe(jdbcPrefix, driverProperties)
        if (fetched != null) {
            cache[key] = fetched
            return ConnectionPropertiesResult(fetched, PropertySource.SYS_CONNECTION_PROPS)
        }
        return degradedResult(driverClass, driverProperties)
    }

    /** 候補を順に試し、最初に成功した `sys_connection_props` の結果を返す。全滅なら null。 */
    private fun firstSuccessfulProbe(
        jdbcPrefix: String,
        driverProperties: List<DriverProperty>,
    ): List<ConnectionProperty>? =
        ConnectionPropertyProbe.candidateUrls(jdbcPrefix, driverProperties).firstNotNullOfOrNull { url ->
            runCatching { metadataSource.sysConnectionProps(url) }
                .onFailure { log.debug(it) { "プローブ失敗: ${ConnectionStringMasker.mask(url)}" } }
                .getOrNull()
        }

    private fun degradedResult(
        driverClass: String,
        driverProperties: List<DriverProperty>,
    ): ConnectionPropertiesResult = if (driverProperties.isEmpty()) {
        log.warn { "接続プロパティを取得できませんでした: $driverClass" }
        ConnectionPropertiesResult.unavailable(PropertySource.NONE_FETCH_FAILED)
    } else {
        log.warn { "sys_connection_props を取得できないため getPropertyInfo で縮退します: $driverClass" }
        ConnectionPropertiesResult(
            DegradedPropertyMapper.toConnectionProperties(driverProperties),
            PropertySource.DRIVER_PROPERTY_INFO,
        )
    }

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
            require(parts.size >= CDATA_CLASS_MIN_PARTS && parts[0] == "cdata" && parts[1] == "jdbc") {
                "Not a CData JDBC driver class: $driverClass"
            }
            return "jdbc:${parts[2]}"
        }

        /** `cdata.jdbc.<product>.<Driver>` の最小要素数。 */
        private const val CDATA_CLASS_MIN_PARTS = 3
    }
}
