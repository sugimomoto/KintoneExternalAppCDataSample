package com.cdata.kintone.adapter.agent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentLogMarkersTest {

    @Test
    fun `invalid token を認証失敗と判定する`() {
        val logs = """{"level":"ERROR","msg":"failed to connect to kintone",""" +
            """"err":"rpc error: code = Unauthenticated desc = invalid token"}"""
        assertTrue(AgentLogMarkers.indicatesAuthFailure(logs))
    }

    @Test
    fun `token has been revoked を認証失敗と判定する`() {
        val logs = """{"level":"ERROR","msg":"failed to receive request","err":"token has been revoked"}"""
        assertTrue(AgentLogMarkers.indicatesAuthFailure(logs))
    }

    @Test
    fun `Unauthenticated を認証失敗と判定する`() {
        assertTrue(AgentLogMarkers.indicatesAuthFailure("rpc error: code = Unauthenticated"))
    }

    @Test
    fun `大文字小文字が違っても認証失敗と判定する`() {
        assertTrue(AgentLogMarkers.indicatesAuthFailure("INVALID TOKEN"))
        assertTrue(AgentLogMarkers.indicatesAuthFailure("unauthenticated"))
    }

    @Test
    fun `正常な接続ログを認証失敗と判定しない`() {
        val logs = """{"level":"INFO","msg":"successfully connected to kintone"}"""
        assertFalse(AgentLogMarkers.indicatesAuthFailure(logs))
    }

    @Test
    fun `空文字を認証失敗と判定しない`() {
        // ログ取得に失敗したときも空文字になる。停止の根拠にしてはいけない。
        assertFalse(AgentLogMarkers.indicatesAuthFailure(""))
    }

    @Test
    fun `認証以外のエラーを認証失敗と判定しない`() {
        val logs = """{"level":"ERROR","msg":"failed to connect to adapter","err":"connection refused"}"""
        assertFalse(AgentLogMarkers.indicatesAuthFailure(logs))
    }

    @Test
    fun `接続成立マーカーを判定する`() {
        assertTrue(AgentLogMarkers.indicatesConnected("""{"msg":"successfully connected to kintone"}"""))
        assertFalse(AgentLogMarkers.indicatesConnected("starting agent"))
    }
}
