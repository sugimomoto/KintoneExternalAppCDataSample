package com.cdata.kintone.adapter.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [ServerConfig] のパース挙動。
 *
 * `port: 0` または `port: "auto"` を「MultiAdapterRunner が空きポートを
 * 自動割り当て」のシグナルとして扱う（実体としては Int 0）。
 * 設定は SQLite に JSON として格納されるため、JSON 経由で検証する。
 */
class ServerConfigTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String): ServerConfig =
        json.decodeFromString(serializer<ServerConfig>(), body)

    @Test
    fun `port=0 は auto-allocate として受け入れる`() {
        val cfg = parse("""{"port":"0","bind-address":"0.0.0.0","plaintext":true}""")
        assertEquals(0, cfg.port)
        assertTrue(cfg.isAutoPort())
    }

    @Test
    fun `port=auto 文字列は 0 (auto) に解釈される`() {
        val cfg = parse("""{"port":"auto","bind-address":"0.0.0.0","plaintext":true}""")
        assertEquals(0, cfg.port)
        assertTrue(cfg.isAutoPort())
    }

    @Test
    fun `port=8083 は具体値として保持`() {
        val cfg = parse("""{"port":"8083"}""")
        assertEquals(8083, cfg.port)
        assertFalse(cfg.isAutoPort())
    }

    @Test
    fun `port 省略時は ServerConfig DEFAULT_PORT`() {
        val cfg = parse("""{"plaintext":true}""")
        assertEquals(ServerConfig.DEFAULT_PORT, cfg.port)
        assertFalse(cfg.isAutoPort())
    }

    @Test
    fun `port=auto 以外の文字列はパース失敗`() {
        assertThrows<Exception> { parse("""{"port":"random-string"}""") }
    }

    @Test
    fun `ラウンドトリップで port が保持される`() {
        val original = ServerConfig(port = 18042, bindAddress = "0.0.0.0", plaintext = true)
        val encoded = json.encodeToString(serializer<ServerConfig>(), original)
        assertEquals(original, parse(encoded))
    }
}
