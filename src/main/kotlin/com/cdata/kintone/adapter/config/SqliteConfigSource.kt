package com.cdata.kintone.adapter.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.io.path.exists

/**
 * SQLite ベースの [ConfigSource] 実装。
 *
 * 2 テーブル + JSON カラム構造で YAML と同等の表現力を持つ。
 * 設計詳細は `.steering/20260522-web-ui-and-sqlite/design.md §2` 参照。
 *
 * @param dbPath SQLite ファイルのパス。親ディレクトリは自動作成
 * @param envResolver 環境変数展開関数。`${VAR}` を解決する
 */
class SqliteConfigSource(
    private val dbPath: Path,
    private val envResolver: (String) -> String? = System::getenv,
) : ConfigSource {

    private val jdbcUrl: String = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

    init {
        dbPath.parent?.let { Files.createDirectories(it) }
        connect().use { conn -> SqliteSchema.initialize(conn) }
    }

    // ===== ConfigSource API =====

    override fun listTables(): List<String> = connect().use { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("SELECT name FROM tables ORDER BY name").toStringList()
        }
    }

    override fun loadTableSet(tableName: String): TableConfigSet {
        val row = fetchTableRow(tableName)
            ?: throw ConfigFileMissingException("テーブルが見つかりません: $tableName")
        val server = decode<ServerConfig>(row.serverJson)
        val table = decode<TableConfig>(row.tableJson)
        val capability = decode<CapabilityConfig>(row.capabilityJson)
        val jdbc = resolveJdbc(row)
        return TableConfigSet(server, jdbc, table, capability)
    }

    override fun saveTableSet(tableName: String, set: TableConfigSet) {
        saveTableInternal(tableName, set, jdbcRef = null, inlineJdbc = set.jdbc)
    }

    /**
     * 共通 JDBC を参照する形でテーブルを保存する。
     * Web UI が「Use shared JDBC reference」を選んだ時に使う。
     */
    override fun saveTableSetWithRef(tableName: String, set: TableConfigSet, jdbcRef: String) {
        saveTableInternal(tableName, set, jdbcRef = jdbcRef, inlineJdbc = null)
    }

    override fun deleteTable(tableName: String) = connect().use { conn ->
        conn.prepareStatement("DELETE FROM tables WHERE name = ?").use { ps ->
            ps.setString(1, tableName)
            ps.executeUpdate()
        }
        Unit
    }

    override fun sharedJdbcRefOf(tableName: String): String? =
        fetchTableRow(tableName)?.jdbcRef

    override fun loadSharedJdbcConfig(name: String): JdbcConfig? = connect().use { conn ->
        conn.prepareStatement("SELECT config_json FROM shared_jdbcs WHERE name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                if (rs.next()) decode<JdbcConfig>(rs.getString(1)) else null
            }
        }
    }

    override fun listSharedJdbcConfigs(): List<String> = connect().use { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("SELECT name FROM shared_jdbcs ORDER BY name").toStringList()
        }
    }

    override fun saveSharedJdbcConfig(name: String, config: JdbcConfig) {
        val json = JSON.encodeToString(serializer<JdbcConfig>(), config)
        connect().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO shared_jdbcs(name, driver_class, url_template, config_json, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                    driver_class = excluded.driver_class,
                    url_template = excluded.url_template,
                    config_json = excluded.config_json,
                    updated_at = excluded.updated_at
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, name)
                ps.setString(2, config.driverClass)
                ps.setString(3, config.url)
                ps.setString(4, json)
                ps.setLong(5, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    override fun deleteSharedJdbcConfig(name: String) = connect().use { conn ->
        conn.prepareStatement("DELETE FROM shared_jdbcs WHERE name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeUpdate()
        }
        Unit
    }

    // ===== internals =====

    private data class TableRow(
        val name: String,
        val dbTableName: String,
        val jdbcRef: String?,
        val serverJson: String,
        val inlineJdbcJson: String?,
        val tableJson: String,
        val capabilityJson: String,
    )

    private fun fetchTableRow(name: String): TableRow? = connect().use { conn ->
        conn.prepareStatement(
            """
            SELECT name, db_table_name, jdbc_ref, server_json, inline_jdbc_json,
                   table_json, capability_json
            FROM tables WHERE name = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                TableRow(
                    name = rs.getString("name"),
                    dbTableName = rs.getString("db_table_name"),
                    jdbcRef = rs.getString("jdbc_ref"),
                    serverJson = rs.getString("server_json"),
                    inlineJdbcJson = rs.getString("inline_jdbc_json"),
                    tableJson = rs.getString("table_json"),
                    capabilityJson = rs.getString("capability_json"),
                )
            }
        }
    }

    private fun resolveJdbc(row: TableRow): JdbcConfig {
        if (row.jdbcRef != null) {
            return loadSharedJdbcConfig(row.jdbcRef)
                ?: throw ConfigParseException("共通 JDBC 設定が見つかりません: ${row.jdbcRef}")
        }
        if (row.inlineJdbcJson != null) {
            return decode<JdbcConfig>(row.inlineJdbcJson)
        }
        throw ConfigParseException("テーブル ${row.name} に jdbc_ref も inline_jdbc も設定されていません")
    }

    private fun saveTableInternal(
        tableName: String,
        set: TableConfigSet,
        jdbcRef: String?,
        inlineJdbc: JdbcConfig?,
    ) {
        // 環境変数展開「前」の JSON を保存する。loadTableSet で読み出し時に展開する設計。
        val serverJson = JSON.encodeToString(serializer<ServerConfig>(), set.server)
        val tableJson = JSON.encodeToString(serializer<TableConfig>(), set.table)
        val capabilityJson = JSON.encodeToString(serializer<CapabilityConfig>(), set.capability)
        val inlineJdbcJson = inlineJdbc?.let { JSON.encodeToString(serializer<JdbcConfig>(), it) }

        connect().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO tables(
                    name, db_table_name, jdbc_ref, server_json, inline_jdbc_json,
                    table_json, capability_json, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                    db_table_name = excluded.db_table_name,
                    jdbc_ref = excluded.jdbc_ref,
                    server_json = excluded.server_json,
                    inline_jdbc_json = excluded.inline_jdbc_json,
                    table_json = excluded.table_json,
                    capability_json = excluded.capability_json,
                    updated_at = excluded.updated_at
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, tableName)
                ps.setString(2, set.table.name)
                ps.setString(3, jdbcRef)
                ps.setString(4, serverJson)
                ps.setString(5, inlineJdbcJson)
                ps.setString(6, tableJson)
                ps.setString(7, capabilityJson)
                ps.setLong(8, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    private inline fun <reified T> decode(json: String): T {
        val expanded = EnvVarExpander.expand(json, envResolver)
        return JSON.decodeFromString(serializer<T>(), expanded)
    }

    private fun ResultSet.toStringList(): List<String> {
        val list = mutableListOf<String>()
        while (next()) list.add(getString(1))
        return list
    }

    private fun connect(): Connection {
        val conn = DriverManager.getConnection(jdbcUrl)
        conn.createStatement().use { st ->
            SqliteSchema.PRAGMAS.forEach { st.execute(it) }
        }
        return conn
    }

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
