package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.WebUiServer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * `adapter web-ui` サブコマンド。
 *
 * Ktor サーバを起動して、ブラウザから設定管理・Adapter 起動/停止を行う。
 * 同一 JVM 内で MultiAdapterRunner も保持し、テーブル別 gRPC サーバを動かす。
 */
class WebUiCommand : CliktCommand(name = "web-ui") {

    private val port: Int by option("--port", help = "Web UI のポート (default: 8080)")
        .int().default(8080)

    private val bindAddress: String by option(
        "--bind-address",
        help = "Web UI の bind address (default: 127.0.0.1)",
    ).default("127.0.0.1")

    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ (default: ./config)")
        .path()
        .default(Path.of("./config"))

    private val libDir: Path by option("--lib-dir", help = "JDBC Driver 配置ディレクトリ (default: ./lib)")
        .path()
        .default(Path.of("./lib"))

    private val sqlitePath: Path? by option(
        "--sqlite-path",
        help = "設定 SQLite のパス (default: <config-dir>/config.db)",
    ).path(canBeDir = false)

    override fun run() {
        val context = AppContext.create(configDir = configDir, libDir = libDir, sqlitePath = sqlitePath)
        val server = WebUiServer(context, port = port, bindAddress = bindAddress)

        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutdown フック実行: web-ui 停止中..." }
                server.close()
            },
        )

        echo("=== Adapter Web Console ===")
        echo("  URL:           http://$bindAddress:$port")
        echo("  Config DB:     ${ConfigStore.resolveDbPath(configDir, sqlitePath)}")
        echo("  Lib dir:       $libDir")
        if (context.migratedPorts.isNotEmpty()) {
            echo("  Port 移行:     ${context.migratedPorts.size} 件 " +
                context.migratedPorts.joinToString(", ") { "${it.syncName}→${it.newPort}" })
        }
        echo("  Adapter 起動:  ${context.startedAdapters.size} 件" +
            if (context.startedAdapters.isEmpty()) "" else " (${context.startedAdapters.joinToString(", ")})")
        echo("")
        echo("Ctrl-C で停止")

        server.start(wait = true)
    }
}
