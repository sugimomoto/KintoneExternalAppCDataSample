package com.cdata.kintone.adapter.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * YAML ファイルベースの [ConfigSource] 実装。
 *
 * 2 つのレイアウトをサポート：
 *
 * 1. **マルチテーブル構成（フェーズ2-A 推奨）**:
 *    ```
 *    config/
 *    ├ jdbc/<name>.yaml          # 共通 JDBC 設定（オプション）
 *    └ tables/<table>/
 *       ├ server.yaml
 *       ├ jdbc.yaml              # 個別 JDBC（または jdbc-ref.yaml）
 *       ├ jdbc-ref.yaml          # 共通 JDBC を参照（name フィールドで指定）
 *       ├ table.yaml
 *       └ capability.yaml
 *    ```
 *
 * 2. **シングルテーブル構成（フェーズ1 互換）**:
 *    ```
 *    config/
 *    ├ server.yaml
 *    ├ jdbc.yaml
 *    ├ table.yaml
 *    └ capability.yaml
 *    ```
 *    この場合 [listTables] は `["default"]` を返し、[loadTableSet] で `"default"` を渡すと読み込まれる。
 */
class YamlConfigSource(
    private val configDir: Path,
    private val envResolver: (String) -> String? = System::getenv,
) : ConfigSource {

    override fun listTables(): List<String> {
        // マルチテーブル構成: config/tables/*/ を列挙
        val tablesDir = configDir.resolve("tables")
        val multi = if (tablesDir.exists() && tablesDir.isDirectory()) {
            Files.list(tablesDir).use { stream ->
                stream.filter { it.isDirectory() }
                    .map { it.fileName.toString() }
                    .sorted()
                    .toList()
            }
        } else {
            emptyList()
        }
        if (multi.isNotEmpty()) return multi

        // フェーズ1 互換: config/server.yaml 等が直下にあれば "default" として認識
        if (configDir.resolve("server.yaml").exists()) {
            return listOf(DEFAULT_TABLE_NAME)
        }
        return emptyList()
    }

    override fun loadTableSet(tableName: String): TableConfigSet {
        val tableDir = resolveTableDir(tableName)
        val server = loadOne(tableDir.resolve("server.yaml"), serializer<ServerConfig>())
        val jdbc = resolveJdbc(tableDir)
        val table = loadOne(tableDir.resolve("table.yaml"), serializer<TableConfig>())
        val capability = loadOne(tableDir.resolve("capability.yaml"), serializer<CapabilityConfig>())
        return TableConfigSet(server, jdbc, table, capability)
    }

    override fun saveTableSet(tableName: String, set: TableConfigSet) {
        val tableDir = configDir.resolve("tables").resolve(tableName)
        Files.createDirectories(tableDir)
        writeYaml(tableDir.resolve("server.yaml"), set.server, serializer<ServerConfig>())
        writeYaml(tableDir.resolve("jdbc.yaml"), set.jdbc, serializer<JdbcConfig>())
        writeYaml(tableDir.resolve("table.yaml"), set.table, serializer<TableConfig>())
        writeYaml(tableDir.resolve("capability.yaml"), set.capability, serializer<CapabilityConfig>())
    }

    override fun deleteTable(tableName: String) {
        val tableDir = configDir.resolve("tables").resolve(tableName)
        if (!tableDir.exists()) return
        Files.walk(tableDir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    override fun loadSharedJdbcConfig(name: String): JdbcConfig? {
        val path = configDir.resolve("jdbc").resolve("$name.yaml")
        if (!path.exists()) return null
        return loadOne(path, serializer<JdbcConfig>())
    }

    /**
     * 指定テーブル名のディレクトリパスを解決する。
     * "default" の場合、フェーズ1 互換で `configDir` 自体を返す可能性あり。
     */
    private fun resolveTableDir(tableName: String): Path {
        val multiPath = configDir.resolve("tables").resolve(tableName)
        if (multiPath.exists() && multiPath.isDirectory()) return multiPath

        // フェーズ1 互換: default テーブル指定時、configDir 直下が tables/default の代わり
        if (tableName == DEFAULT_TABLE_NAME && configDir.resolve("server.yaml").exists()) {
            return configDir
        }
        throw ConfigFileMissingException("テーブル設定ディレクトリが見つかりません: $tableName")
    }

    /**
     * JDBC 設定の解決：個別 `jdbc.yaml` を最優先、なければ `jdbc-ref.yaml` 経由で共通設定を参照。
     */
    private fun resolveJdbc(tableDir: Path): JdbcConfig {
        val direct = tableDir.resolve("jdbc.yaml")
        if (direct.exists()) {
            return loadOne(direct, serializer<JdbcConfig>())
        }
        val refFile = tableDir.resolve("jdbc-ref.yaml")
        if (refFile.exists()) {
            val ref = loadOne(refFile, serializer<JdbcRef>())
            return loadSharedJdbcConfig(ref.name)
                ?: throw ConfigParseException("共通 JDBC 設定が見つかりません: ${ref.name}")
        }
        throw ConfigFileMissingException(
            "jdbc.yaml も jdbc-ref.yaml も見つかりません: $tableDir",
        )
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

    private fun <T> writeYaml(path: Path, value: T, serializer: KSerializer<T>) {
        val text = Yaml(configuration = YamlConfiguration(encodeDefaults = false))
            .encodeToString(serializer, value)
        Files.createDirectories(path.parent ?: Path.of("."))
        Files.writeString(path, text)
    }

    companion object {
        const val DEFAULT_TABLE_NAME = "default"

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
