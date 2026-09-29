package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/** `adapter test-connection` サブコマンド。JDBC 接続を確立して情報を出力する。 */
class TestConnectionCommand : CliktCommand(name = "test-connection") {
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

        echo("Connection:  $name")
        echo("Connecting to: ${ConnectionStringMasker.mask(jdbcConfig.url)}")
        JdbcConnectionProvider(jdbcConfig).use { provider ->
            provider.connection().use { conn ->
                val meta = conn.metaData
                echo("Database: ${meta.databaseProductName} ${meta.databaseProductVersion}")
                echo("Driver:   ${meta.driverName} ${meta.driverVersion}")
                echo("Catalog:  ${conn.catalog}")
                echo("接続成功")
            }
        }
    }
}
