package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.metadata.JdbcMetadataInspector
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/** `adapter list-tables` サブコマンド。接続先のテーブル一覧を表示する。 */
class ListTablesCommand : CliktCommand(name = "list-tables") {

    private val jdbcConfigPath: Path by option("--jdbc-config", help = "jdbc.yaml のパス")
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("./config/jdbc.yaml"))

    override fun run() {
        val jdbcConfig = loadJdbcConfigWithEnv(jdbcConfigPath)
        JdbcConnectionProvider(jdbcConfig).use { provider ->
            provider.connection().use { conn ->
                val inspector = JdbcMetadataInspector(conn)
                val tables = inspector.listTables()
                if (tables.isEmpty()) {
                    echo("テーブルが見つかりません")
                    return@use
                }
                echo("=== Tables ===")
                tables.forEachIndexed { i, t ->
                    val schemaPrefix = if (t.schema != null) "${t.schema}." else ""
                    echo("[${i + 1}] $schemaPrefix${t.name}")
                }
            }
        }
    }
}
