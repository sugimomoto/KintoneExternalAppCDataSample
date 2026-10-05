package com.cdata.kintone.adapter.jdbc

import com.google.protobuf.Timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * protobuf `Timestamp` → SQL 用文字列の変換 [SqlDateTime] のテスト。
 *
 * CData の SQL Server ドライバーは `java.sql.Timestamp` のパラメータを SQL Server が
 * 解釈できない文字列に変換するため、更新と日時フィルタが
 * `Conversion failed when converting date and/or time from character string.` で失敗する。
 * 文字列で渡せば通るため、束縛を文字列に統一する。
 *
 * タイムゾーンは引数で受け取る。テストが実行環境に依存しないようにするため。
 *
 * 関連: [Issue #71](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/71)
 */
class SqlDateTimeTest {

    private val utc = ZoneId.of("UTC")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun ts(seconds: Long, nanos: Int = 0): Timestamp =
        Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build()

    @Test
    fun `エポックを UTC で描画する`() {
        assertEquals("1970-01-01 00:00:00", SqlDateTime.format(ts(0), utc))
    }

    @Test
    fun `タイムゾーンに応じて描画が変わる`() {
        // 既存の挙動（java.sql.Timestamp が JVM 既定タイムゾーンで描画される）を保つ。
        assertEquals("1970-01-01 09:00:00", SqlDateTime.format(ts(0), tokyo))
    }

    @Test
    fun `秒まで描画する`() {
        // 2026-10-05T12:34:56Z
        assertEquals("2026-10-05 12:34:56", SqlDateTime.format(ts(1791203696), utc))
    }

    @Test
    fun `ミリ秒を持つ値では精度を落とさない`() {
        assertEquals("1970-01-01 00:00:00.123", SqlDateTime.format(ts(0, 123_000_000), utc))
    }

    @Test
    fun `ミリ秒が 0 なら小数部を付けない`() {
        // SQL の見た目を従来に近く保つ。
        assertEquals("1970-01-01 00:00:00", SqlDateTime.format(ts(0, 0), utc))
    }

    @Test
    fun `ミリ秒未満は切り捨てる`() {
        // SQL Server の datetime は約 3.33ms 精度。ナノ秒は保持できない。
        assertEquals("1970-01-01 00:00:00.001", SqlDateTime.format(ts(0, 1_999_999), utc))
    }

    @Test
    fun `エポック以前の値も描画できる`() {
        assertEquals("1969-12-31 23:59:59", SqlDateTime.format(ts(-1), utc))
    }

    @Test
    fun `既定のタイムゾーンは JVM 既定`() {
        // 引数を省いた場合に既存の挙動と一致すること。
        val expected = SqlDateTime.format(ts(1791203696), ZoneId.systemDefault())

        assertEquals(expected, SqlDateTime.format(ts(1791203696)))
    }
}
