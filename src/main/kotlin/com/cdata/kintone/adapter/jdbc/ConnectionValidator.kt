package com.cdata.kintone.adapter.jdbc

import java.sql.Connection

/**
 * 接続が実際に使えるかを検証する。
 *
 * **`getMetaData()` だけでは検証にならない。** `databaseProductName` や `driverVersion` は
 * ドライバー側の静的な情報で、サーバーへの問い合わせを伴わない。CData ドライバーは
 * 認証を遅延させるため `getConnection()` も通り、HikariCP も生成直後の接続を
 * `aliveBypassWindow` 内では検証しない。3 つが重なって、誤った資格情報でも
 * 「接続成功」になっていた (Issue #45)。
 *
 * 実測では `Connection.isValid`（JDBC 4.0 標準）だけが誤った資格情報を検出できた。
 * `getMetaData().getTables()` は Google Sheets で 90 秒かかり、`sys_tables` は
 * 失敗時も例外を投げず 0 件を返すため認証失敗と空スキーマを区別できない。
 */
object ConnectionValidator {

    /** `isValid` のタイムアウト（秒）。実測 1〜2 秒で収まるため余裕を持たせた固定値。 */
    const val TIMEOUT_SECONDS = 10

    /**
     * 接続を検証する。
     *
     * **`isValid` の戻り値を必ず見ること。** 例外の有無だけで判定すると、
     * 誤った資格情報を「成功」と誤判定する（それが #45 の不具合そのもの）。
     */
    fun validate(connection: Connection): ConnectionValidation {
        if (!connection.isValid(TIMEOUT_SECONDS)) {
            return ConnectionValidation.Invalid(INVALID_MESSAGE)
        }
        val meta = connection.metaData
        return ConnectionValidation.Valid(
            "${meta.databaseProductName} ${meta.databaseProductVersion} / " +
                "${meta.driverName} ${meta.driverVersion}",
        )
    }

    /**
     * `isValid` が false のときの説明。
     *
     * ドライバーは理由を返さない（`getWarnings()` は null）。理由が取れるのは
     * 実データへのクエリだけで、テーブル名を汎用に決められないため、
     * 何を確認すべきかだけを伝える。
     */
    private const val INVALID_MESSAGE =
        "接続は確立できましたが、データソースが応答しませんでした。" +
            "資格情報（ユーザー名・パスワード・トークン等）と接続設定を確認してください。"
}
