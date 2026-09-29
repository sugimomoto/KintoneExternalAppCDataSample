package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * `adapter serve-all` サブコマンド。
 *
 * `ConfigSource.listTables()` で見つかった全テーブル分の gRPC サーバを 1 JVM 内で並行起動する。
 * 個別テーブルだけ起動したい場合は `adapter serve --table <name>` を使う。
 */
class ServeAllCommand : CliktCommand(name = "serve-all") {

    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ（default: ./config）")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val sqlitePath: Path? by option(
        "--sqlite-path",
        help = "設定 SQLite のパス（default: <config-dir>/config.db）",
    ).path(canBeDir = false)

    override fun run() {
        val source = ConfigStore.open(configDir, sqlitePath)
        val runner = MultiAdapterRunner(
            configSource = source,
            activeFile = ActiveAdaptersFile(ActiveAdaptersFile.DEFAULT_PATH),
        )

        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutdown フック実行: serve-all 停止中..." }
                runner.close()
            },
        )

        val started = runner.startAll()
        if (started.isEmpty()) {
            echo("起動できる連携がありません。Web UI から連携を作成してください。")
            return
        }

        echo("=== Active Adapters (${started.size}) ===")
        runner.listActive().forEach { echo("  - ${it.tableName} @ port=${it.port}") }

        // 全サーバの停止を待つ。最初の 1 つで待っておき、shutdownHook が他も止める。
        runner.get(started.first())?.awaitTermination()
    }
}
