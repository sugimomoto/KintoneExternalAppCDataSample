package com.cdata.kintone.adapter.jdbc

import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Properties

/**
 * ドライバメタデータ取得の IO 境界。
 *
 * [JdbcConnectionPropertyInspector] の段階的プローブをユニットテストできるように切り出している。
 * 本番では [JdbcDriverMetadataSource] が `DriverManager` を直接叩く。
 */
interface DriverMetadataSource {

    /** JAR を動的ロードして `DriverManager` に登録する。 */
    fun loadDriver(jarPath: Path, driverClass: String)

    /**
     * JDBC 標準 `Driver.getPropertyInfo` の結果。**接続は張らない**ため、
     * `sys_connection_props` が読めないドライバでも取得できる。
     */
    fun driverProperties(jdbcPrefix: String): List<DriverProperty>

    /** [url] で接続して `sys_connection_props` を読む。接続できない場合は例外を投げる。 */
    fun sysConnectionProps(url: String): List<ConnectionProperty>
}

/** `DriverManager` を使う [DriverMetadataSource] の実装。 */
class JdbcDriverMetadataSource : DriverMetadataSource {

    override fun loadDriver(jarPath: Path, driverClass: String) {
        JdbcConnectionProvider.loadDriver(jarPath.toString(), driverClass)
    }

    override fun driverProperties(jdbcPrefix: String): List<DriverProperty> =
        DriverManager.getDriver("$jdbcPrefix:")
            .getPropertyInfo("$jdbcPrefix:", Properties())
            .map { DriverProperty.from(it) }

    override fun sysConnectionProps(url: String): List<ConnectionProperty> =
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery(QUERY).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(parseRow(rs))
                        }
                    }
                }
            }
        }

    private fun parseRow(rs: ResultSet): ConnectionProperty {
        val valuesStr = rs.getString("Values")
        val allowedValues = if (valuesStr.isNullOrBlank()) emptyList() else valuesStr.split(",").map { it.trim() }
        return ConnectionProperty(
            propertyName = rs.getString("PropertyName") ?: "",
            displayName = rs.getString("Name") ?: rs.getString("PropertyName") ?: "",
            shortDescription = rs.getString("ShortDescription") ?: "",
            type = parseType(rs.getString("Type")),
            // Value 列は読まない。プローブで渡したダミー値が入り得るため (Issue #15)。
            defaultValue = rs.getString("Default"),
            allowedValues = allowedValues,
            category = rs.getString("Category") ?: "",
            required = rs.getBoolean("Required"),
            sensitivity = parseSensitivity(rs.getString("Sensitivity")),
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

    private companion object {
        val QUERY = """
            SELECT PropertyName, Name, ShortDescription, Type, Values, Default,
                   Category, Required, Sensitivity, Visible, Hierarchy,
                   Ordinal, CatOrdinal
            FROM sys_connection_props
            ORDER BY CatOrdinal, Ordinal
        """.trimIndent()
    }
}
