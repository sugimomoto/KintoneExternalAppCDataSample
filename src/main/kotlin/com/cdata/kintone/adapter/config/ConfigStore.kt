package com.cdata.kintone.adapter.config

import java.nio.file.Path

/**
 * 設定ストア（SQLite）を開くためのエントリポイント。
 *
 * 設定の実体は `<configDir>/config.db` に置く。ファイルが存在しない場合は
 * [SqliteConfigSource] の初期化でスキーマが自動生成されるため、
 * 空の状態からでもそのまま起動できる。
 */
object ConfigStore {
    const val DB_FILE_NAME = "config.db"

    /** SQLite ファイルのパスを解決する。[sqlitePath] が指定されていればそれを優先する。 */
    fun resolveDbPath(
        configDir: Path,
        sqlitePath: Path? = null,
    ): Path = sqlitePath ?: configDir.resolve(DB_FILE_NAME)

    /** 設定ストアを開く。DB ファイルとスキーマは必要に応じて自動生成される。 */
    fun open(
        configDir: Path,
        sqlitePath: Path? = null,
        envResolver: (String) -> String? = System::getenv,
    ): ConfigSource = SqliteConfigSource(resolveDbPath(configDir, sqlitePath), envResolver)
}
