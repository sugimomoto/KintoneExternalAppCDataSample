package com.cdata.kintone.adapter.agent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket

/**
 * [AdapterReachabilityChecker] の検証 (Issue #7)。
 */
class AdapterReachabilityCheckerTest {

    private val checker = AdapterReachabilityChecker(timeoutMs = 500)

    @Test
    fun `listen しているポートは Reachable`() {
        ServerSocket(0).use { server ->
            val result = checker.check("127.0.0.1:${server.localPort}")
            assertInstanceOf(AdapterReachabilityChecker.Result.Reachable::class.java, result)
        }
    }

    @Test
    fun `listen していないポートは Unreachable`() {
        val freePort = ServerSocket(0).use { it.localPort } // close 済み = 誰も listen していない
        val result = checker.check("127.0.0.1:$freePort")
        assertInstanceOf(AdapterReachabilityChecker.Result.Unreachable::class.java, result)
    }

    @Test
    fun `publish 範囲外のポートは公開範囲の指摘を含む`() {
        // 43343 = 実際に踏んだ ephemeral port (Issue #3 / #7)
        val reason = checker.buildReason(43_343)

        assertTrue(reason.contains("公開されていない"), "実際のメッセージ: $reason")
        assertTrue(reason.contains("18000-18099"), "実際のメッセージ: $reason")
    }

    @Test
    fun `publish 範囲内のポートでは公開範囲外の指摘をしない`() {
        val reason = checker.buildReason(18_010)

        assertFalse(reason.contains("ポート 18010 は Docker で公開されていない"), "実際のメッセージ: $reason")
        assertTrue(reason.contains("Adapter が起動していない"), "実際のメッセージ: $reason")
    }

    @Test
    fun `アドレス形式が不正なら Unreachable`() {
        val result = checker.check("not-an-address")
        assertInstanceOf(AdapterReachabilityChecker.Result.Unreachable::class.java, result)
    }
}
