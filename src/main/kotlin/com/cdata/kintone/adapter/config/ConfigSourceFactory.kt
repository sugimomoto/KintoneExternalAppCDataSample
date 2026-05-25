package com.cdata.kintone.adapter.config

import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Yaml / Sqlite のどちらの [ConfigSource] を使うかを判定し、生成する。
 *
 * 優先順位:
 *   1. 明示指定された Mode 引数
 *   2. 環境変数 `CONFIG_SOURCE=yaml|sqlite`
 *   3. `<configDir>/config.db` が存在すれば SQLITE
 *   4. デフォルト YAML
 */
object ConfigSourceFactory {

    enum class Mode { YAML, SQLITE }

    fun create(
        mode: Mode? = null,
        configDir: Path = Path.of("./config"),
        sqlitePath: Path = configDir.resolve("config.db"),
        envResolver: (String) -> String? = System::getenv,
    ): ConfigSource {
        val resolved = mode ?: detectMode(configDir, envResolver)
        return when (resolved) {
            Mode.YAML -> YamlConfigSource(configDir, envResolver)
            Mode.SQLITE -> SqliteConfigSource(sqlitePath, envResolver)
        }
    }

    fun detectMode(
        configDir: Path = Path.of("./config"),
        envResolver: (String) -> String? = System::getenv,
    ): Mode {
        envResolver("CONFIG_SOURCE")?.let {
            return runCatching { Mode.valueOf(it.uppercase()) }
                .getOrElse { error("Unknown CONFIG_SOURCE: $it (expected: yaml or sqlite)") }
        }
        if (configDir.resolve("config.db").exists()) return Mode.SQLITE
        return Mode.YAML
    }
}
