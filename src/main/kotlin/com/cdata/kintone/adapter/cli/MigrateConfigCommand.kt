package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigMigrator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/**
 * `adapter migrate-config` サブコマンド。
 *
 * フェーズ1 構成（config 直下に server.yaml 等）を検出した場合、
 * config/tables/<target>/ 配下に移動してフェーズ2-A 構成に揃える。
 */
class MigrateConfigCommand : CliktCommand(name = "migrate-config") {

    private val configDir: Path by option(
        "--config-dir",
        help = "設定ディレクトリ（default: ./config）",
    )
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val targetTable: String by option(
        "--target",
        help = "移行先テーブル名（default: default）",
    ).default("default")

    override fun run() {
        val migrator = ConfigMigrator(configDir)
        when (migrator.detect()) {
            ConfigMigrator.State.ALREADY_MIGRATED -> {
                echo("既に新構成です。マイグレーション不要: $configDir/tables")
                return
            }
            ConfigMigrator.State.EMPTY -> {
                echo("旧構成ファイルが見つかりません: $configDir")
                return
            }
            ConfigMigrator.State.NEEDS_MIGRATION -> {
                val result = migrator.migrate(targetTable)
                echo("移行完了: $configDir/tables/${result.targetTable}/")
                result.movedFiles.forEach { echo("  - ${it.fileName}") }
            }
        }
    }
}
