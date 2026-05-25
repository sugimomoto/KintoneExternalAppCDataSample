package com.cdata.kintone.adapter.config

import java.sql.Connection

/**
 * SqliteConfigSource が使用するスキーマ定義と初期化ヘルパ。
 *
 * 設計詳細は `.steering/20260522-web-ui-and-sqlite/design.md §2.2` を参照。
 */
object SqliteSchema {

    const val CURRENT_VERSION = 1

    /** 共通 JDBC 設定。config 配下 jdbc サブフォルダの YAML ファイル相当。 */
    val DDL_SHARED_JDBCS = """
        CREATE TABLE IF NOT EXISTS shared_jdbcs (
            name TEXT PRIMARY KEY NOT NULL,
            driver_class TEXT NOT NULL,
            url_template TEXT NOT NULL,
            config_json TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """.trimIndent()

    val DDL_SHARED_JDBCS_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_shared_jdbcs_updated_at
            ON shared_jdbcs(updated_at)
    """.trimIndent()

    /**
     * テーブル定義。config 配下 tables/サブフォルダの 4 YAML ファイル相当。
     *
     * jdbc_ref は shared_jdbcs.name を参照するが、外部キー制約は設けない。
     * 理由: Web UI 経由で共通 JDBC を削除する際の挙動をアプリ層で制御したいため
     * (削除前に「X テーブルが参照中」と警告するなど)。
     * orphan ref は loadTableSet 時に ConfigParseException で検出。
     */
    val DDL_TABLES = """
        CREATE TABLE IF NOT EXISTS tables (
            name TEXT PRIMARY KEY NOT NULL,
            db_table_name TEXT NOT NULL,
            jdbc_ref TEXT,
            server_json TEXT NOT NULL,
            inline_jdbc_json TEXT,
            table_json TEXT NOT NULL,
            capability_json TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """.trimIndent()

    val DDL_TABLES_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_tables_jdbc_ref ON tables(jdbc_ref)
    """.trimIndent()

    /** スキーマバージョン管理。 */
    val DDL_SCHEMA_META = """
        CREATE TABLE IF NOT EXISTS schema_meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """.trimIndent()

    /** SQLite 接続設定: WAL モード + 外部キー有効化。 */
    val PRAGMAS = listOf(
        "PRAGMA journal_mode = WAL",
        "PRAGMA foreign_keys = ON",
        "PRAGMA synchronous = NORMAL",
    )

    /**
     * スキーマ初期化（CREATE IF NOT EXISTS 系なので冪等）。
     * 初回起動時に `schema_meta(version, "1")` を INSERT。
     */
    fun initialize(conn: Connection) {
        conn.createStatement().use { st ->
            PRAGMAS.forEach { st.execute(it) }
            st.execute(DDL_SHARED_JDBCS)
            st.execute(DDL_SHARED_JDBCS_INDEX)
            st.execute(DDL_TABLES)
            st.execute(DDL_TABLES_INDEX)
            st.execute(DDL_SCHEMA_META)
            st.execute(
                "INSERT OR IGNORE INTO schema_meta(key, value) VALUES ('version', '$CURRENT_VERSION')",
            )
        }
    }
}
