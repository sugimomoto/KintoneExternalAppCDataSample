package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigMigrator
import com.cdata.kintone.adapter.config.SqliteConfigSource
import com.cdata.kintone.adapter.config.YamlConfigSource
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * `adapter migrate-to-sqlite` サブコマンド。
 *
 * `config/` 配下の YAML 設定群を SQLite データベースに一括移行する。
 * 移行後は `CONFIG_SOURCE=sqlite` または `config.db` の自動検出で SQLite モード起動。
 */
class MigrateToSqliteCommand : CliktCommand(name = "migrate-to-sqlite") {

    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ（default: ./config）")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val sqlitePath: Path by option(
        "--sqlite-path",
        help = "出力 SQLite ファイル（default: <config-dir>/config.db）",
    ).path().default(Path.of(""))

    private val force: Boolean by option("--force", help = "既存の SQLite ファイルを上書き").flag()

    override fun run() {
        val effectiveSqlitePath = if (sqlitePath.toString().isEmpty()) {
            configDir.resolve("config.db")
        } else {
            sqlitePath
        }

        if (effectiveSqlitePath.exists() && !force) {
            echo("既に SQLite ファイルが存在します: $effectiveSqlitePath")
            echo("上書きする場合は --force を指定してください")
            return
        }
        if (effectiveSqlitePath.exists()) {
            Files.delete(effectiveSqlitePath)
        }

        val yaml = YamlConfigSource(configDir)
        val sqlite = SqliteConfigSource(effectiveSqlitePath)
        ConfigMigrator.copyAll(from = yaml, to = sqlite)

        val tableCount = sqlite.listTables().size
        val jdbcCount = sqlite.listSharedJdbcConfigs().size
        echo("移行完了: $effectiveSqlitePath")
        echo("  - shared JDBC: $jdbcCount 件")
        echo("  - tables: $tableCount 件")
        echo("以降 `CONFIG_SOURCE=sqlite` で SQLite モード起動できます")
    }
}
