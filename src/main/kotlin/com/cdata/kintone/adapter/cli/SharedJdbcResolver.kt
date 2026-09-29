package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigSource
import com.cdata.kintone.adapter.config.JdbcConfig
import com.github.ajalt.clikt.core.CliktError

/**
 * CLI から共有 JDBC 設定を名前で引くための共通処理。
 *
 * 名前を省略した場合、登録が 1 件だけならそれを使う。
 * 複数ある場合・0 件の場合は、例外スタックではなく**何をすればよいか**を示して終了する。
 */
object SharedJdbcResolver {
    fun resolve(
        source: ConfigSource,
        name: String?,
    ): Pair<String, JdbcConfig> {
        val available = source.listSharedJdbcConfigs()
        val target = name ?: soleCandidate(available)
        val config =
            source.loadSharedJdbcConfig(target)
                ?: throw CliktError("共有 JDBC 設定が見つかりません: $target\n" + availableHint(available))
        return target to config
    }

    /** 名前が省略されたときに一意に決まる候補を返す。決まらなければ案内して終了する。 */
    private fun soleCandidate(available: List<String>): String {
        if (available.size == 1) return available.first()
        val message =
            if (available.isEmpty()) {
                "共有 JDBC 設定が 1 件も登録されていません。\nWeb UI の /connections から登録してください。"
            } else {
                "共有 JDBC 設定が複数あります。--jdbc-name で指定してください。\n" + availableHint(available)
            }
        throw CliktError(message)
    }

    private fun availableHint(available: List<String>): String =
        if (available.isEmpty()) {
            "登録済みの共有 JDBC 設定: (なし)"
        } else {
            "登録済みの共有 JDBC 設定: " + available.joinToString(", ")
        }
}
