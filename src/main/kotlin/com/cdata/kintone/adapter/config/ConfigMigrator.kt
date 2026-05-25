package com.cdata.kintone.adapter.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * フェーズ1 構成（config 直下の server.yaml / jdbc.yaml / table.yaml / capability.yaml）から
 * フェーズ2-A 構成（config/tables/<name>/ 配下に同名 4 ファイル）への移行ヘルパ。
 *
 * `adapter migrate-config` サブコマンドの実装本体として使う。
 */
class ConfigMigrator(private val configDir: Path) {

    enum class State {
        /** `tables/` ディレクトリが存在し、すでに新構成。 */
        ALREADY_MIGRATED,

        /** 旧構成（`server.yaml` 等が `configDir` 直下にある）。 */
        NEEDS_MIGRATION,

        /** 何も設定ファイルがない。 */
        EMPTY,
    }

    data class Result(
        val targetTable: String,
        val movedFiles: List<Path>,
    )

    fun detect(): State {
        val tablesDir = configDir.resolve("tables")
        if (tablesDir.exists() && tablesDir.isDirectory()) {
            val hasAny = Files.list(tablesDir).use { stream ->
                stream.anyMatch { it.isDirectory() }
            }
            if (hasAny) return State.ALREADY_MIGRATED
        }
        val hasPhase1 = PHASE1_FILES.any { configDir.resolve(it).exists() }
        return if (hasPhase1) State.NEEDS_MIGRATION else State.EMPTY
    }

    /**
     * 旧構成を `tables/<targetTableName>/` 配下に移動する。
     * - `tables/<targetTableName>` がすでに存在する場合は失敗（破壊を避ける）。
     * - 旧構成が見当たらない場合も失敗。
     * - `*.example` は対象外（フェーズ1 例ファイルは残す）。
     */
    fun migrate(targetTableName: String = YamlConfigSource.DEFAULT_TABLE_NAME): Result {
        val targetDir = configDir.resolve("tables").resolve(targetTableName)
        check(!targetDir.exists()) {
            "移行先ディレクトリがすでに存在します: $targetDir"
        }

        val sources = PHASE1_FILES
            .map { configDir.resolve(it) }
            .filter { it.exists() }
        check(sources.isNotEmpty()) {
            "旧構成ファイルが $configDir 直下に見つかりません"
        }

        Files.createDirectories(targetDir)
        val moved = sources.map { src ->
            val dest = targetDir.resolve(src.fileName)
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE)
            dest
        }
        return Result(targetTable = targetTableName, movedFiles = moved)
    }

    companion object {
        /** フェーズ1 で `configDir` 直下に置かれる正規ファイル名。 */
        private val PHASE1_FILES = listOf(
            "server.yaml",
            "jdbc.yaml",
            "table.yaml",
            "capability.yaml",
        )

        /**
         * 任意の [ConfigSource] から別の [ConfigSource] へ、共通 JDBC とテーブル設定を
         * 一括コピーする。`migrate-to-sqlite` / `export-yaml` の核となる処理。
         *
         * 注意: jdbc-ref 構造は維持されず、コピー先では「共通 JDBC + テーブル毎の解決済 JDBC」
         * として保存される（TableConfigSet が ref/inline の区別を持たないため）。
         * 実行時の挙動は等価。
         */
        fun copyAll(from: ConfigSource, to: ConfigSource) {
            for (name in from.listSharedJdbcConfigs()) {
                from.loadSharedJdbcConfig(name)?.let { to.saveSharedJdbcConfig(name, it) }
            }
            for (name in from.listTables()) {
                to.saveTableSet(name, from.loadTableSet(name))
            }
        }
    }
}
