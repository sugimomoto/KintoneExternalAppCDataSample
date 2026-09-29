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

    // --- OAuth 認可ウィザード (Issue #12) ---

    @Test
    fun `OAuth 非対応ドライバーのエラーを案内に変換する`() {
        val message = ErrorMessageTranslator.translate("RSB <EXEC> is not a valid stored procedure.")

        assertTrue(message.text.contains("OAuth 認可に対応していません"), "実際: ${message.text}")
        assertEquals(ErrorMessageTranslator.Severity.ERROR, message.severity)
    }

    @Test
    fun `クライアント ID の誤りを案内に変換する`() {
        val raw = "OAUTH [30004] Failed to retrieve OAuth token information. " +
            "[invalid_client_id] client identifier invalid."

        val message = ErrorMessageTranslator.translate(raw)

        assertTrue(message.text.contains("クライアント ID"), "実際: ${message.text}")
    }

    @Test
    fun `認可コードの期限切れを案内に変換する`() {
        val message = ErrorMessageTranslator.translate("OAUTH [30003] invalid_grant: expired authorization code")

        assertTrue(message.text.contains("認可コード"), "実際: ${message.text}")
    }

    @Test
    fun `初回認可が未完了であることを案内に変換する`() {
        val message = ErrorMessageTranslator.translate("OAUTH [50001] タイムアウトしました")

        assertTrue(message.text.contains("OAuth 認可"), "実際: ${message.text}")
    }

    @Test
    fun `OAuth エラーの生メッセージを保持する`() {
        // 障害調査のため元のメッセージは残す。
        val raw = "OAUTH [30004] invalid_client_id"

        assertEquals(raw, ErrorMessageTranslator.translate(raw).rawMessage)
    }
}
