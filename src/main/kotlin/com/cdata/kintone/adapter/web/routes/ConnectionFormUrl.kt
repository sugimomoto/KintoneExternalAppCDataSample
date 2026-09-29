package com.cdata.kintone.adapter.web.routes

import io.ktor.http.Parameters

/**
 * 接続フォームの送信値から JDBC 接続文字列を組み立てる。
 *
 * **未入力のプロパティは接続文字列に含めない。** フォームは既定値を入力欄に
 * 埋めないため、送られてこないプロパティ = 利用者が触っていないプロパティになる。
 *
 * 既定値まで保存すると次の問題が起きる (Issue #27)。
 * - Windows 用の既定値 (`%APPDATA%\...`) が Linux コンテナに持ち込まれる
 * - OAuth キャッシュパスの一本化 (Issue #11) が効かなくなる
 *   （利用者の明示指定として扱われるため）
 * - ドライバーを更新しても保存時点の既定値を使い続ける
 */
internal object ConnectionFormUrl {

    private const val MANUAL_URL_FIELD = "jdbc.url.manual"
    private const val PROPERTY_PREFIX = "prop."

    /**
     * 接続文字列を組み立てる。
     *
     * 直接入力 ([MANUAL_URL_FIELD]) があれば最優先で採用する。プロパティ定義が
     * 取れないドライバーの逃げ道のため (Issue #15)。
     */
    fun build(jdbcPrefix: String, form: Parameters, fallbackUrl: String? = null): String {
        form[MANUAL_URL_FIELD]?.takeIf { it.isNotBlank() }?.let { return it }

        val pairs = propertyValues(form)
            .filterValues { it.isNotBlank() }
            .entries
            .joinToString(";") { "${it.key}=${it.value}" }

        return if (pairs.isEmpty()) fallbackUrl ?: "$jdbcPrefix:" else "$jdbcPrefix:$pairs;"
    }

    /** フォームの `prop.<PropertyName>` を プロパティ名 -> 値 のマップにする。 */
    fun propertyValues(form: Parameters): Map<String, String> =
        form.entries()
            .asSequence()
            .filter { it.key.startsWith(PROPERTY_PREFIX) }
            .mapNotNull { (key, values) ->
                val value = values.firstOrNull() ?: return@mapNotNull null
                key.removePrefix(PROPERTY_PREFIX) to value
            }
            .toMap()
}
