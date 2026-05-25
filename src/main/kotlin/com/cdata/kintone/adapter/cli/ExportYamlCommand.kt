package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigMigrator
import com.cdata.kintone.adapter.config.SqliteConfigSource
import com.cdata.kintone.adapter.config.YamlConfigSource
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path

/**
 * `adapter export-yaml` サブコマンド。
 *
 * SQLite データベースの内容を YAML ファイル群に書き出す。
 * バックアップや人間レビュー、設定差分管理に使う。
 */
class ExportYamlCommand : CliktCommand(name = "export-yaml") {

    private val sqlitePath: Path by option(
        "--sqlite-path",
        help = "入力 SQLite ファイル（default: ./config/config.db）",
    )
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("./config/config.db"))

    private val outDir: Path by option("--out-dir", help = "出力 YAML ディレクトリ（default: ./config-export）")
        .path()
        .default(Path.of("./config-export"))

    override fun run() {
        Files.createDirectories(outDir)
        val sqlite = SqliteConfigSource(sqlitePath)
        val yaml = YamlConfigSource(outDir)
        ConfigMigrator.copyAll(from = sqlite, to = yaml)

        echo("エクスポート完了: $outDir")
        echo("  - shared JDBC: ${yaml.listSharedJdbcConfigs().size} 件")
        echo("  - tables: ${yaml.listTables().size} 件")
    }
}
