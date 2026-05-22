package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.YamlConfigSource
import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import com.github.ajalt.clikt.core.CliktCommand
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
 * 指定テーブルだけ gRPC サーバを起動する。
 * - `--table <name>`: 単一テーブル
 * - `--tables <a>,<b>`: カンマ区切りで複数指定
 * - 引数なし: フェーズ1 互換で `default` テーブル起動
 *
 * 全テーブルを一括起動したい場合は `adapter serve-all` を使う。
 */
class ServeCommand : CliktCommand(name = "serve") {

    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ（default: ./config）")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val singleTable: String? by option(
        "--table",
        help = "起動するテーブル名（default: default）",
    )

    private val multipleTables: List<String>? by option(
        "--tables",
        help = "起動するテーブル名（カンマ区切りで複数指定）",
    ).split(",")

    override fun run() {
        val source = YamlConfigSource(configDir)
        val targets: List<String> = when {
            !multipleTables.isNullOrEmpty() -> multipleTables!!
            singleTable != null -> listOf(singleTable!!)
            else -> listOf(YamlConfigSource.DEFAULT_TABLE_NAME)
        }
        val runner = MultiAdapterRunner(
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
