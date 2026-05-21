package com.cdata.kintone.adapter.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * 設定ディレクトリ配下の 4 ファイル (`server.yaml` / `jdbc.yaml` / `table.yaml` / `capability.yaml`) を
 * 読み込んで `AdapterConfig` に統合する。
 *
 * YAML 内の `${VAR_NAME}` 形式の文字列は環境変数で展開される（接続情報の機密管理に利用）。
 */
class ConfigLoader(
    private val configDir: Path,
    private val envResolver: (String) -> String? = System::getenv,
) {
    fun load(): AdapterConfig {
        val server = loadOne(configDir.resolve("server.yaml"), serializer<ServerConfig>())
        val jdbc = loadOne(configDir.resolve("jdbc.yaml"), serializer<JdbcConfig>())
        val table = loadOne(configDir.resolve("table.yaml"), serializer<TableConfig>())
        val capability = loadOne(configDir.resolve("capability.yaml"), serializer<CapabilityConfig>())
        return AdapterConfig(server, jdbc, table, capability)
    }

    private fun <T> loadOne(path: Path, deserializer: KSerializer<T>): T {
        val raw = try {
            Files.readString(path)
        } catch (e: NoSuchFileException) {
            throw ConfigFileMissingException("設定ファイルが見つかりません: $path", e)
        }
        val expanded = expandEnvVars(raw, envResolver)
        return try {
            Yaml.default.decodeFromString(deserializer, expanded)
        } catch (e: Exception) {
            throw ConfigParseException("設定ファイルのパースに失敗: $path", e)
        }
    }

    companion object {
        private val ENV_VAR_REGEX = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")

        /** `${VAR_NAME}` 形式のプレースホルダを環境変数で展開する。未定義の変数はそのまま残す。 */
        fun expandEnvVars(text: String, envResolver: (String) -> String?): String {
            return ENV_VAR_REGEX.replace(text) { match ->
                val varName = match.groupValues[1]
                envResolver(varName) ?: match.value
            }
        }
    }
}

class ConfigFileMissingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class ConfigParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
