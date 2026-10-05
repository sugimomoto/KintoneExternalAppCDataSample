package com.cdata.kintone.adapter.jdbc

import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * protobuf `Timestamp` を SQL のパラメータに渡す文字列へ変換する。
 *
 * **`java.sql.Timestamp` を渡してはいけない。** CData の SQL Server ドライバーは
 * `Timestamp` パラメータを SQL Server が解釈できない文字列に変換するため、
 * 更新と日時フィルタが次のエラーで失敗する (Issue #71)。
 *
 * ```
 * DATA_SOURCE The SQL Error Number is 241, Severity is 16,
 * Error is 'Conversion failed when converting date and/or time from character string.'
 * ```
 *
 * `setObject` / `setTimestamp` / `setObject(_, Types.TIMESTAMP)` のいずれも失敗し、
 * `QueryPassthrough` の設定にも依存しない。文字列で渡せば通る（実測）。
 * CData の SQL エンジンが日時リテラルとして受ける `'yyyy-MM-dd HH:mm:ss'` 形式に揃える。
 *
 * **変換はここに集約する。** `RowMapper`（Insert / Update の値）と
 * `FilterTranslator`（Select / Count の日時フィルタ）の両方が通る。
 */
object SqlDateTime {

    private val SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * SQL に渡す日時文字列を返す。
     *
     * [zone] は既定で JVM 既定タイムゾーン。従来 `java.sql.Timestamp` が
     * JVM 既定タイムゾーンで描画されていたため、既存連携の値がずれないよう揃える。
     * 引数で受け取るのはテストを実行環境に依存させないため。
     *
     * ミリ秒を持つ値では精度を落とさない。0 のときは `.000` を付けない
     * （SQL の見た目を従来に近く保つ）。ミリ秒未満は SQL Server の `datetime` が
     * 保持できないため切り捨てる。
     */
    fun format(ts: Timestamp, zone: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong())
        val at = instant.atZone(zone)
        val formatter = if (at.nano / NANOS_PER_MILLI == 0) SECONDS else MILLIS
        return formatter.format(at)
    }

    private const val NANOS_PER_MILLI = 1_000_000
}
