package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `adapter list-active` サブコマンド。
 *
 * `serve` / `serve-all` が出力した状態ファイル（`./run/active-adapters.json`）を
 * 読み出して、稼働中の Adapter 一覧を表示する。
 */
class ListActiveCommand : CliktCommand(name = "list-active") {

    private val statePath: Path by option(
        "--state-file",
        help = "稼働状態ファイルのパス（default: ./run/active-adapters.json）",
    )
        .path(canBeDir = false)
        .default(ActiveAdaptersFile.DEFAULT_PATH)

    override fun run() {
        val file = ActiveAdaptersFile(statePath)
        val active = file.read()
        if (active.isEmpty()) {
            echo("稼働中の Adapter はありません。（状態ファイル: $statePath）")
            return
        }
        echo("=== Active Adapters (${active.size}) ===")
        active.forEach {
            val started = if (it.startedAt > 0) {
                FORMATTER.format(Instant.ofEpochMilli(it.startedAt))
            } else {
                "-"
            }
            echo("  - ${it.tableName.padEnd(20)} port=${it.port}  status=${it.status}  started=$started")
        }
    }

    companion object {
        private val FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }
}
