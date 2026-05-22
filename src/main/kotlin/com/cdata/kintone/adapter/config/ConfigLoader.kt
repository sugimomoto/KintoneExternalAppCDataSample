package com.cdata.kintone.adapter.config

import com.cdata.kintone.adapter.config.YamlConfigSource.Companion.DEFAULT_TABLE_NAME
import java.nio.file.Path

/**
 * 後方互換用のシン薄ラッパ。
 *
 * フェーズ2-A 以降の正規 API は [ConfigSource] / [YamlConfigSource]。
 * 既存コード（フェーズ1 で書かれたフォーマット）が
 * `ConfigLoader(configDir).load()` で使えるよう、`default` テーブルを返す形で残す。
 */
class ConfigLoader(
    private val configDir: Path,
    private val envResolver: (String) -> String? = System::getenv,
) {
    fun load(): AdapterConfig {
        return YamlConfigSource(configDir, envResolver).loadTableSet(DEFAULT_TABLE_NAME)
    }

    companion object {
        /** `${VAR_NAME}` 形式のプレースホルダを環境変数で展開する。未定義の変数はそのまま残す。 */
        fun expandEnvVars(text: String, envResolver: (String) -> String?): String {
            return YamlConfigSource.expandEnvVars(text, envResolver)
        }
    }
}

class ConfigFileMissingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class ConfigParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
