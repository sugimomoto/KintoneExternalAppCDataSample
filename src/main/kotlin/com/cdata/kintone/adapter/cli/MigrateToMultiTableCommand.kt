package com.cdata.kintone.adapter.cli

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.cdata.kintone.adapter.config.JdbcRef
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

/**
 * `adapter migrate-to-multi-table` サブコマンド。
 *
 * フェーズ1 の `config/{server,jdbc,table,capability}.yaml` (直下) を、
 * フェーズ2-A 推奨の `config/tables/<table-name>/` + `config/jdbc/<shared-name>.yaml`
 * + `jdbc-ref.yaml` 形式に再配置する。
 *
 * `migrate-config` がフェーズ1 → `tables/default/` への単純移動だったのに対し、
 * こちらは jdbc を共通化して shared 参照に切り替える。
 */
class MigrateToMultiTableCommand : CliktCommand(name = "migrate-to-multi-table") {

    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ (default: ./config)")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    private val tableName: String by option(
        "--table-name",
        help = "新階層のテーブル識別子 (default: account)",
    ).default("account")

    private val sharedJdbcName: String by option(
        "--shared-jdbc-name",
        help = "共通 JDBC 設定の名前 (default: salesforce)",
    ).default("salesforce")

    override fun run() {
        val src = mapOf(
            "server" to configDir.resolve("server.yaml"),
            "jdbc" to configDir.resolve("jdbc.yaml"),
            "table" to configDir.resolve("table.yaml"),
            "capability" to configDir.resolve("capability.yaml"),
        )
        val missing = src.filterValues { !it.exists() }.keys
        if (missing.isNotEmpty()) {
            echo("フェーズ1 構成が見つかりません (欠落: ${missing.joinToString(", ")})")
            return
        }

        val tablesDir = configDir.resolve("tables").resolve(tableName)
        val jdbcSharedDir = configDir.resolve("jdbc")
        Files.createDirectories(tablesDir)
        Files.createDirectories(jdbcSharedDir)

        // 1. jdbc.yaml → jdbc/<sharedJdbcName>.yaml
        val sharedJdbcPath = jdbcSharedDir.resolve("$sharedJdbcName.yaml")
        if (sharedJdbcPath.exists()) {
            echo("共通 JDBC 設定が既に存在します: $sharedJdbcPath")
            echo("中止しました")
            return
        }
        Files.move(src["jdbc"]!!, sharedJdbcPath, StandardCopyOption.ATOMIC_MOVE)

        // 2. server.yaml / table.yaml / capability.yaml → tables/<tableName>/
        Files.move(src["server"]!!, tablesDir.resolve("server.yaml"), StandardCopyOption.ATOMIC_MOVE)
        Files.move(src["table"]!!, tablesDir.resolve("table.yaml"), StandardCopyOption.ATOMIC_MOVE)
        Files.move(src["capability"]!!, tablesDir.resolve("capability.yaml"), StandardCopyOption.ATOMIC_MOVE)

        // 3. jdbc-ref.yaml 新規作成
        val yaml = Yaml(configuration = YamlConfiguration(encodeDefaults = false))
        val refText = yaml.encodeToString(serializer<JdbcRef>(), JdbcRef(name = sharedJdbcName))
        Files.writeString(tablesDir.resolve("jdbc-ref.yaml"), refText)

        echo("=== 移行完了 ===")
        echo("  共通 JDBC: $sharedJdbcPath")
        echo("  テーブル設定: $tablesDir/")
        echo("    - server.yaml")
        echo("    - jdbc-ref.yaml (name: $sharedJdbcName)")
        echo("    - table.yaml")
        echo("    - capability.yaml")
        echo("")
        echo("注: server.yaml の port が 8083 のままなら、必要に応じて 18001 等に変更し、")
        echo("    Agent 側の adapter_addr も更新してください。")
    }
}
