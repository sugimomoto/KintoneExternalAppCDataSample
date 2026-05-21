package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path

/** `adapter test-connection` サブコマンド。JDBC 接続を確立して情報を出力する。 */
class TestConnectionCommand : CliktCommand(name = "test-connection") {

    private val jdbcConfigPath: Path by option("--jdbc-config", help = "jdbc.yaml のパス")
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("./config/jdbc.yaml"))

    override fun run() {
        val jdbcConfig = loadJdbcConfigWithEnv(jdbcConfigPath)
        echo("Connecting to: ${JdbcConnectionProvider.maskUrl(jdbcConfig.url)}")
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

/** jdbc.yaml を読み込み、`${VAR}` 形式の環境変数を展開する。 */
internal fun loadJdbcConfigWithEnv(path: Path): JdbcConfig {
    val raw = Files.readString(path)
    val envVarRegex = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")
    val expanded = envVarRegex.replace(raw) { match ->
        System.getenv(match.groupValues[1]) ?: match.value
    }
    return Yaml.default.decodeFromString(serializer<JdbcConfig>(), expanded)
}
