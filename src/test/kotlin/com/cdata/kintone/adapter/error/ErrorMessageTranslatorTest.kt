package com.cdata.kintone.adapter.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorMessageTranslatorTest {

    @Test
    fun `rpc keepalive エラー → ユーザー文言`() {
        val result = ErrorMessageTranslator.translate(
            "rpc error: code = Unavailable desc = keepalive ping failed to receive ACK"
        )
        assertEquals(ErrorMessageTranslator.Severity.WARN, result.severity)
        assertTrue(result.text.contains("kintone との接続"))
        assertTrue(result.actions.contains(ErrorMessageTranslator.UserAction.RECONNECT))
    }

    @Test
    fun `Connection refused → 接続設定へのアクション`() {
        val result = ErrorMessageTranslator.translate("Connection refused: tcp host:5432")
        assertEquals(ErrorMessageTranslator.Severity.ERROR, result.severity)
        assertTrue(result.actions.contains(ErrorMessageTranslator.UserAction.GO_TO_CONNECTIONS))
    }

    @Test
    fun `JAR not found → ドライバー画面へ`() {
        val result = ErrorMessageTranslator.translate("JAR not found: lib/x.jar")
        assertTrue(result.actions.contains(ErrorMessageTranslator.UserAction.GO_TO_DRIVERS))
    }

    @Test
    fun `INVALID_LOGIN → 認証失敗メッセージ`() {
        val result = ErrorMessageTranslator.translate("INVALID_LOGIN: bad credentials")
        assertTrue(result.text.contains("認証に失敗"))
    }

    @Test
    fun `docker socket エラー → Docker メッセージ`() {
        val result = ErrorMessageTranslator.translate("docker socket /var/run/docker.sock not found")
        assertTrue(result.text.contains("Docker"))
    }

    @Test
    fun `不明なエラーは生メッセージをそのまま返す`() {
        val raw = "some unknown failure"
        val result = ErrorMessageTranslator.translate(raw)
        assertEquals(raw, result.text)
        assertEquals(ErrorMessageTranslator.Severity.ERROR, result.severity)
    }

    @Test
    fun `null は汎用文言`() {
        val result = ErrorMessageTranslator.translate(null)
        assertTrue(result.text.contains("不明なエラー"))
    }

    @Test
    fun `NoClassDefFoundError → 再起動を促す`() {
        val result = ErrorMessageTranslator.translate(
            "java.lang.NoClassDefFoundError: io/netty/util/concurrent/X"
        )
        assertTrue(result.text.contains("再起動"))
    }
}
