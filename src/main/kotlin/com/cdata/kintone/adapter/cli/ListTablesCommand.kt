package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.metadata.JdbcMetadataInspector
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/** `adapter list-tables` サブコマンド。接続先のテーブル一覧を表示する。 */
class ListTablesCommand : CliktCommand(name = "list-tables") {
    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ（default: ./config）")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val sqlitePath: Path? by option(
        "--sqlite-path",
        help = "設定 SQLite のパス（default: <config-dir>/config.db）",
    ).path(canBeDir = false)

    private val jdbcName: String? by option(
        "--jdbc-name",
        help = "共有 JDBC 設定の名前（省略時は登録が 1 件ならそれを使う）",
    )

    override fun run() {
        val source = ConfigStore.open(configDir, sqlitePath)
        val (name, jdbcConfig) = SharedJdbcResolver.resolve(source, jdbcName)
        echo("Connection: $name")

        JdbcConnectionProvider(jdbcConfig, oauthCacheKey = name).use { provider ->
            provider.connection().use { conn ->
                val tables = JdbcMetadataInspector(conn).listTables()
                if (tables.isEmpty()) {
                    echo("テーブルが見つかりません")
                    return@use
                }
                echo("=== Tables (${tables.size}) ===")
                tables.forEachIndexed { i, t ->
                    val schemaPrefix = if (t.schema != null) "${t.schema}." else ""
                    echo("[${i + 1}] $schemaPrefix${t.name}")
                }
            }
        }
    }
}
