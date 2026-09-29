package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.JdbcConfig
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 一覧 1 行分の設定読み込み結果 [ConnectionRow] のテスト。
 *
 * `config_json` が壊れていると `loadSharedJdbcConfig` が例外を投げ、
 * そのままでは一覧画面全体が 500 になる。行単位で失敗を閉じ込める。
 *
 * 関連: [Issue #39](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/39)
 */
class ConnectionRowTest {

    private val config = JdbcConfig(
        driverClass = "cdata.jdbc.salesforce.SalesforceDriver",
        driverJar = "./lib/cdata.jdbc.salesforce.jar",
        url = "jdbc:salesforce:AuthScheme=OAuth;",
    )

    @Test
    fun `load - 設定が読めれば Loaded になる`() {
        val row = ConnectionRow.load { config }

        val loaded = assertInstanceOf(ConnectionRow.Loaded::class.java, row)
        assertEquals(config, loaded.config)
    }

    @Test
    fun `load - 例外が出れば Unreadable になり理由に例外メッセージが入る`() {
        val row = ConnectionRow.load {
            throw SerializationException("Fields [driver-class, driver-jar] are required")
        }

        val unreadable = assertInstanceOf(ConnectionRow.Unreadable::class.java, row)
        assertTrue(unreadable.reason.contains("driver-class"), "実際: ${unreadable.reason}")
    }

    @Test
    fun `load - メッセージが無い例外でも理由が空にならない`() {
        val row = ConnectionRow.load { throw IllegalStateException() }

        val unreadable = assertInstanceOf(ConnectionRow.Unreadable::class.java, row)
        assertFalse(unreadable.reason.isBlank(), "理由が空: '${unreadable.reason}'")
    }

    @Test
    fun `load - null が返れば Unreadable になる`() {
        // 一覧に名前が出ている以上、設定が引けないのは異常。
        // 利用者にとっては「読めない」と同じ扱いでよい。
        val row = ConnectionRow.load { null }

        assertInstanceOf(ConnectionRow.Unreadable::class.java, row)
    }

    @Test
    fun `load - 例外の型で分岐しない`() {
        // 壊れ方は JSON の構文エラー・必須フィールド欠落など一通りではなく、
        // いずれも利用者の対処は同じ（作り直すか削除する）。
        val serialization = ConnectionRow.load { throw SerializationException("boom") }
        val illegalState = ConnectionRow.load { throw IllegalStateException("boom") }

        assertInstanceOf(ConnectionRow.Unreadable::class.java, serialization)
        assertInstanceOf(ConnectionRow.Unreadable::class.java, illegalState)
        assertEquals(
            (serialization as ConnectionRow.Unreadable).reason,
            (illegalState as ConnectionRow.Unreadable).reason,
        )
    }
}
