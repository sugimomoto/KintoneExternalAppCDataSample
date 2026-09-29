package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.path
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * `adapter serve` サブコマンド。
 *
 * 指定した連携だけ gRPC サーバを起動する。
 * - `--table <name>`: 単一
 * - `--tables <a>,<b>`: カンマ区切りで複数
 *
 * 全連携を一括起動したい場合は `adapter serve-all` を使う。
 */
class ServeCommand : CliktCommand(name = "serve") {
    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ（default: ./config）")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val sqlitePath: Path? by option(
        "--sqlite-path",
        help = "設定 SQLite のパス（default: <config-dir>/config.db）",
    ).path(canBeDir = false)

    private val singleTable: String? by option("--table", help = "起動する連携名")

    private val multipleTables: List<String>? by option(
        "--tables",
        help = "起動する連携名（カンマ区切りで複数指定）",
    ).split(",")

    override fun run() {
        val targets: List<String> =
            when {
                !multipleTables.isNullOrEmpty() -> multipleTables!!
                singleTable != null -> listOf(singleTable!!)
                else -> throw UsageError("--table または --tables で起動する連携名を指定してください")
            }

        val source = ConfigStore.open(configDir, sqlitePath)
        val known = source.listTables()
        val unknown = targets.filterNot { it in known }
        if (unknown.isNotEmpty()) {
            echo("設定が見つかりません: ${unknown.joinToString(", ")}", err = true)
            echo("登録済みの連携: ${if (known.isEmpty()) "(なし)" else known.joinToString(", ")}", err = true)
            throw UsageError("連携名を確認してください")
        }

        val runner =
            MultiAdapterRunner(
                configSource = source,
                activeFile = ActiveAdaptersFile(ActiveAdaptersFile.DEFAULT_PATH),
            )

        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutdown フック実行: serve 停止中..." }
                runner.close()
            },
        )

        targets.forEach { runner.startOne(it) }
        echo("=== Active Adapters ===")
        runner.listActive().forEach { echo("  - ${it.tableName} @ port=${it.port}") }

        // 起動した個別サーバを待機（最初の1つを wait）。複数あっても JVM が停止するまで待つ。
        runner.get(targets.first())?.awaitTermination()
    }
}
