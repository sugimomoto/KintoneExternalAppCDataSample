package com.cdata.kintone.adapter.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

private val log = KotlinLogging.logger {}

/**
 * CData ドライバの `sys_connection_props` システムテーブルから接続プロパティ一覧を取得する。
 *
 * 取得結果は Web UI の Connection 新規/編集画面で動的フォームを生成するのに使う。
 * 設計詳細: `.steering/20260522-web-ui-and-sqlite/design.md §S-10`
 */
class JdbcConnectionPropertyInspector(
    private val libDir: Path = Path.of("./lib"),
) {

    private data class CacheKey(val driverClass: String, val jarLastModified: Long)

    private val cache = mutableMapOf<CacheKey, List<ConnectionProperty>>()

    /**
     * 指定ドライバの接続プロパティを取得。
     * 失敗時は空リスト + ログ警告 (非 CData ドライバ等のフォールバック)。
     */
    fun listProperties(driverClass: String, jarFilename: String): List<ConnectionProperty> {
        val jarPath = libDir.resolve(jarFilename)
        if (!Files.exists(jarPath)) {
            log.warn { "JAR not found: $jarPath" }
            return emptyList()
        }
        val key = CacheKey(driverClass, Files.getLastModifiedTime(jarPath).toMillis())
        cache[key]?.let { return it }

        return try {
            JdbcConnectionProvider.loadDriver(jarPath.toString(), driverClass)
            val jdbcPrefix = jdbcPrefixOf(driverClass)
            DriverManager.getConnection("$jdbcPrefix:").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(QUERY).use { rs ->
                        val list = mutableListOf<ConnectionProperty>()
                        while (rs.next()) {
                            list.add(parseRow(rs))
                        }
                        cache[key] = list
                        list
                    }
                }
            }
        } catch (e: Exception) {
            log.warn(e) { "sys_connection_props 取得失敗: $driverClass" }
            emptyList()
        }
    }

    private fun parseRow(rs: java.sql.ResultSet): ConnectionProperty {
        val type = parseType(rs.getString("Type"))
        val valuesStr = rs.getString("Values")
        val allowedValues = if (valuesStr.isNullOrBlank()) emptyList() else valuesStr.split(",").map { it.trim() }
        val sensitivity = parseSensitivity(rs.getString("Sensitivity"))
        return ConnectionProperty(
            propertyName = rs.getString("PropertyName") ?: "",
            displayName = rs.getString("Name") ?: rs.getString("PropertyName") ?: "",
            shortDescription = rs.getString("ShortDescription") ?: "",
            type = type,
            defaultValue = rs.getString("Default"),
            allowedValues = allowedValues,
            category = rs.getString("Category") ?: "",
            required = rs.getBoolean("Required"),
            sensitivity = sensitivity,
            visible = rs.getBoolean("Visible"),
            hierarchy = rs.getString("Hierarchy") ?: "",
            ordinal = rs.getInt("Ordinal"),
            categoryOrdinal = rs.getInt("CatOrdinal"),
        )
    }

    private fun parseType(s: String?): PropertyType = when (s?.lowercase()) {
        "boolean" -> PropertyType.BOOLEAN
        "int" -> PropertyType.INT
        else -> PropertyType.STRING
    }

    private fun parseSensitivity(s: String?): Sensitivity = when (s?.uppercase()) {
        "PASSWORD" -> Sensitivity.PASSWORD
        "SENSITIVE" -> Sensitivity.SENSITIVE
        else -> Sensitivity.NONE
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
            require(parts.size >= 3 && parts[0] == "cdata" && parts[1] == "jdbc") {
                "Not a CData JDBC driver class: $driverClass"
            }
            return "jdbc:${parts[2]}"
        }

        private val QUERY = """
            SELECT PropertyName, Name, ShortDescription, Type, Values, Default,
                   Category, Required, Sensitivity, Visible, Hierarchy,
                   Ordinal, CatOrdinal
            FROM sys_connection_props
            ORDER BY CatOrdinal, Ordinal
        """.trimIndent()
    }
}

data class ConnectionProperty(
    val propertyName: String,
    val displayName: String,
    val shortDescription: String,
    val type: PropertyType,
    val defaultValue: String?,
    val allowedValues: List<String>,
    val category: String,
    val required: Boolean,
    val sensitivity: Sensitivity,
    val visible: Boolean,
    val hierarchy: String,
    val ordinal: Int,
    val categoryOrdinal: Int,
)

enum class PropertyType { STRING, INT, BOOLEAN }
enum class Sensitivity { NONE, SENSITIVE, PASSWORD }
