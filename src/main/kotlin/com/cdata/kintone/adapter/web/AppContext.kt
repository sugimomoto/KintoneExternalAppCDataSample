package com.cdata.kintone.adapter.web

import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.ConfigSourceFactory
import com.cdata.kintone.adapter.jdbc.JdbcConnectionPropertyInspector
import com.cdata.kintone.adapter.jdbc.JdbcDriverManager
import com.cdata.kintone.adapter.runtime.ActiveAdaptersFile
import com.cdata.kintone.adapter.runtime.MultiAdapterRunner
import java.nio.file.Path

/**
 * Web UI が依存するサービス群を 1 つに束ねるコンテナ。
 *
 * Ktor の起動時に 1 つ生成して各ルートに注入する。
 */
class AppContext(
    val configSource: ConfigSource,
    val configSourceMode: ConfigSourceFactory.Mode,
    val driverManager: JdbcDriverManager,
    val runner: MultiAdapterRunner,
    val configDir: Path,
    val libDir: Path,
    val connectionPropertyInspector: JdbcConnectionPropertyInspector,
) : AutoCloseable {

    override fun close() {
        runner.close()
    }

    companion object {
        fun create(
            configDir: Path = Path.of("./config"),
            libDir: Path = Path.of("./lib"),
        ): AppContext {
            val mode = ConfigSourceFactory.detectMode(configDir)
            val source = ConfigSourceFactory.create(mode, configDir)
            val runner = MultiAdapterRunner(
                configSource = source,
                activeFile = ActiveAdaptersFile(ActiveAdaptersFile.DEFAULT_PATH),
            )
            return AppContext(
                configSource = source,
                configSourceMode = mode,
                driverManager = JdbcDriverManager(libDir),
                runner = runner,
                configDir = configDir,
                libDir = libDir,
                connectionPropertyInspector = JdbcConnectionPropertyInspector(libDir),
            )
        }
    }
}
