package com.cdata.kintone.adapter.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [ServerConfig] の YAML パース挙動。
 *
 * フェーズ2-A から `port: 0` または `port: auto` を「MultiAdapterRunner が
 * 空きポートを自動割り当て」のシグナルとして扱う（実体としては Int 0）。
 */
class ServerConfigTest {

    private fun parse(yaml: String): ServerConfig =
        Yaml.default.decodeFromString(serializer<ServerConfig>(), yaml.trimIndent())

    @Test
    fun `port=0 は auto-allocate として受け入れる`() {
        val cfg = parse(
            """
            port: 0
            bind-address: 0.0.0.0
            plaintext: true
            """,
        )
        assertEquals(0, cfg.port)
        assertTrue(cfg.isAutoPort())
    }

    @Test
    fun `port=auto 文字列は 0 (auto) に解釈される`() {
        val cfg = parse(
            """
            port: auto
            bind-address: 0.0.0.0
            plaintext: true
            """,
        )
        assertEquals(0, cfg.port)
        assertTrue(cfg.isAutoPort())
    }

    @Test
    fun `port=8083 は具体値として保持`() {
        val cfg = parse("port: 8083")
        assertEquals(8083, cfg.port)
        assertFalse(cfg.isAutoPort())
    }

    @Test
    fun `port 省略時は ServerConfig DEFAULT_PORT`() {
        val cfg = parse("plaintext: true")
        assertEquals(ServerConfig.DEFAULT_PORT, cfg.port)
        assertFalse(cfg.isAutoPort())
    }

    @Test
    fun `port=auto 以外の文字列はパース失敗`() {
        assertThrows<Exception> {
            parse("port: random-string")
        }
    }
}
