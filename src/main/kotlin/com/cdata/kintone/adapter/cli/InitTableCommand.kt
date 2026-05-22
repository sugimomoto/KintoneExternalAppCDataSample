package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.JdbcRef
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.metadata.ColumnInfo
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.FieldTypeSuggester
import com.cdata.kintone.adapter.metadata.JdbcMetadataInspector
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * `adapter init-table` サブコマンド。
 * `jdbc.yaml` を読み込み、対話式に `table.yaml` を生成する。
 */
class InitTableCommand : CliktCommand(name = "init-table") {

    private val jdbcConfigPath: Path by option("--jdbc-config", help = "jdbc.yaml のパス")
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("./config/jdbc.yaml"))

    private val output: Path by option("--output", help = "出力先 table.yaml（旧構成用）")
        .path()
        .default(Path.of("./config/table.yaml"))

    private val nonInteractive: Boolean by option("--non-interactive", help = "対話なしで推奨値を採用").flag()

    private val targetTable: String? by option("--table", help = "対象テーブル名を直接指定（指定するとテーブル選択プロンプトをスキップ）")

    private val configName: String? by option(
        "--name",
        help = "フェーズ2-A 構成で生成: config/tables/<name>/ 配下に 4 ファイル + jdbc-ref を生成",
    )

    private val jdbcRefName: String? by option(
        "--jdbc-ref",
        help = "共通 jdbc 設定を参照する名前（例: salesforce）。`--name` 指定時のみ有効",
    )

    private val configDir: Path by option(
        "--config-dir",
        help = "ベースの設定ディレクトリ（default: ./config）。`--name` 指定時の出力ベース",
    )
        .path()
        .default(Path.of("./config"))

    override fun run() {
        val jdbcConfig = loadJdbcConfigWithEnv(jdbcConfigPath)
        JdbcConnectionProvider(jdbcConfig).use { provider ->
            provider.connection().use { conn ->
                val inspector = JdbcMetadataInspector(conn)
                val tables = inspector.listTables()
                if (tables.isEmpty()) {
                    echo("テーブルが見つかりません")
                    return@use
                }

                val selectedTable = when {
                    targetTable != null -> {
                        val match = tables.firstOrNull { it.name.equals(targetTable, ignoreCase = true) }
                            ?: throw IllegalArgumentException("指定テーブルが見つかりません: $targetTable")
                        match.name
                    }
                    nonInteractive -> tables.first().name
                    else -> promptTableSelection(tables.map { it.name })
                }

                val columns = inspector.listColumns(selectedTable)
                val pk = inspector.findPrimaryKey(selectedTable)
                    ?: throw IllegalStateException("主キーが定義されていません: $selectedTable")

                val pkConfig = PrimaryKeyConfig(
                    kintoneFieldId = toSnakeCase(pk.column),
                    jdbcColumn = pk.column,
                )

                val recordIdType = FieldTypeSuggester.suggestRecordIdType(pk.jdbcType)
                if (!nonInteractive) {
                    echo("主キー: ${pk.column} (JDBC type=${pk.jdbcType})")
                    echo("RecordIdType 推奨: $recordIdType")
                }

                val columnConfigs = columns
                    .filter { it.name != pk.column }
                    .map { buildColumnConfig(it, inspector, selectedTable) }

                val tableConfig = TableConfig(
                    name = selectedTable,
                    primaryKey = pkConfig,
                    columns = columnConfigs,
                )

                if (configName != null) {
                    writeMultiTableLayout(tableConfig, recordIdType)
                } else {
                    writeTableConfig(tableConfig, output)
                    echo("生成完了: $output")
                    echo("RecordIdType=${recordIdType} を capability.yaml で record-id-type に設定してください")
                }
            }
        }
    }

    /** フェーズ2-A 構成: config/tables/<name>/ 配下に 4 ファイル + (任意で) jdbc-ref を出力。 */
    private fun writeMultiTableLayout(
        tableConfig: TableConfig,
        recordIdType: RecordIdType,
    ) {
        val name = configName!!
        val targetDir = configDir.resolve("tables").resolve(name)
        Files.createDirectories(targetDir)

        val yaml = Yaml(configuration = YamlConfiguration(encodeDefaults = false))

        val tablePath = targetDir.resolve("table.yaml")
        Files.writeString(tablePath, yaml.encodeToString(serializer<TableConfig>(), tableConfig))

        val serverPath = targetDir.resolve("server.yaml")
        if (!serverPath.exists()) {
            val server = ServerConfig(port = ServerConfig.AUTO_PORT, bindAddress = "0.0.0.0", plaintext = true)
            Files.writeString(serverPath, yaml.encodeToString(serializer<ServerConfig>(), server))
        }

        val capabilityPath = targetDir.resolve("capability.yaml")
        if (!capabilityPath.exists()) {
            val capability = CapabilityConfig(
                recordIdType = recordIdType,
                filterableFields = listOf(tableConfig.primaryKey.kintoneFieldId),
                sortableFields = listOf(tableConfig.primaryKey.kintoneFieldId),
            )
            Files.writeString(capabilityPath, yaml.encodeToString(serializer<CapabilityConfig>(), capability))
        }

        // jdbc 参照（または個別 jdbc.yaml）。
        if (jdbcRefName != null) {
            val refPath = targetDir.resolve("jdbc-ref.yaml")
            if (!refPath.exists()) {
                Files.writeString(refPath, yaml.encodeToString(serializer<JdbcRef>(), JdbcRef(jdbcRefName!!)))
            }
        }

        echo("=== フェーズ2-A 構成を生成 ===")
        echo("  - $tablePath")
        if (Files.exists(serverPath)) echo("  - $serverPath")
        if (Files.exists(capabilityPath)) echo("  - $capabilityPath")
        if (jdbcRefName != null) echo("  - ${targetDir.resolve("jdbc-ref.yaml")}")
        if (jdbcRefName == null) {
            echo("注: 個別 jdbc.yaml は別途用意してください。共通 jdbc を使う場合は --jdbc-ref を指定。")
        }
    }

    private fun promptTableSelection(tableNames: List<String>): String {
        echo("=== テーブル一覧 ===")
        tableNames.forEachIndexed { i, name -> echo("[${i + 1}] $name") }
        val input = prompt("対象テーブルの番号を入力", default = "1") ?: "1"
        val index = input.trim().toIntOrNull()?.minus(1)
            ?: throw IllegalArgumentException("不正な入力: $input")
        if (index !in tableNames.indices) {
            throw IllegalArgumentException("番号が範囲外: $input")
        }
        return tableNames[index]
    }

    private fun buildColumnConfig(
        col: ColumnInfo,
        inspector: JdbcMetadataInspector,
        tableName: String,
    ): ColumnConfig {
        val recommended = FieldTypeSuggester.suggest(col.jdbcType)
        val type = if (nonInteractive) {
            recommended
        } else {
            promptColumnType(col, recommended)
        }
        val options = if (type == ColumnType.SELECTION && !nonInteractive) {
            promptSelectionOptions(col, inspector, tableName)
        } else {
            null
        }
        return ColumnConfig(
            kintoneFieldId = toSnakeCase(col.name),
            jdbcColumn = col.name,
            type = type,
            options = options,
        )
    }

    private fun promptColumnType(col: ColumnInfo, recommended: ColumnType): ColumnType {
        echo("--- カラム: ${col.name} (JDBC type=${col.typeName}) ---")
        echo("推奨: $recommended  [1=TEXT 2=NUMBER 3=DATETIME 4=SELECTION] (Enter で推奨採用)")
        val input = prompt("番号を入力", default = "")?.trim().orEmpty()
        return when (input) {
            "" -> recommended
            "1" -> ColumnType.TEXT
            "2" -> ColumnType.NUMBER
            "3" -> ColumnType.DATETIME
            "4" -> ColumnType.SELECTION
            else -> {
                echo("不正な入力。推奨を採用: $recommended")
                recommended
            }
        }
    }

    private fun promptSelectionOptions(
        col: ColumnInfo,
        inspector: JdbcMetadataInspector,
        tableName: String,
    ): List<String> {
        echo("選択肢の入力方法 [1=手動入力（カンマ区切り） 2=DB から自動検出]")
        val mode = prompt("番号", default = "2")?.trim() ?: "2"
        return when (mode) {
            "1" -> {
                val raw = prompt("選択肢（カンマ区切り）") ?: ""
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            else -> inspector.distinctValues(tableName, col.name)
        }
    }

    private fun writeTableConfig(config: TableConfig, path: Path) {
        if (path.exists()) {
            val overwrite = prompt("$path は既に存在します。上書きしますか？ [y/N]", default = "N") ?: "N"
            if (!overwrite.trim().equals("y", ignoreCase = true)) {
                echo("中止しました")
                return
            }
        }
        val yamlConfig = YamlConfiguration(encodeDefaults = false)
        val yaml = Yaml(configuration = yamlConfig)
        val text = yaml.encodeToString(serializer<TableConfig>(), config)
        Files.createDirectories(path.parent ?: Path.of("."))
        Files.writeString(path, text)
    }

    /** 識別子を snake_case に変換する（"AnnualRevenue" → "annual_revenue"）。 */
    private fun toSnakeCase(name: String): String {
        if (name.isEmpty()) return name
        // 既に snake_case なら小文字化のみ
        if (name.contains('_')) return name.lowercase()
        val result = StringBuilder()
        for ((i, c) in name.withIndex()) {
            if (i > 0 && c.isUpperCase()) result.append('_')
            result.append(c.lowercaseChar())
        }
        return result.toString()
    }
}
