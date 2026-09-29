package com.cdata.kintone.adapter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnvVarExpanderTest {
    @Test
    fun `定義済みの環境変数を展開する`() {
        val env = mapOf("SF_USER" to "alice@example.com")
        val actual = EnvVarExpander.expand("User=\${SF_USER};", env::get)
        assertEquals("User=alice@example.com;", actual)
    }

    @Test
    fun `複数のプレースホルダを展開する`() {
        val env = mapOf("A" to "1", "B" to "2")
        assertEquals("1-2", EnvVarExpander.expand("\${A}-\${B}", env::get))
    }

    @Test
    fun `未定義の変数はプレースホルダのまま残す`() {
        val actual = EnvVarExpander.expand("User=\${UNDEFINED};", { null })
        assertEquals("User=\${UNDEFINED};", actual)
    }

    @Test
    fun `プレースホルダを含まない文字列はそのまま返す`() {
        assertEquals("plain text", EnvVarExpander.expand("plain text", { "x" }))
    }

    @Test
    fun `変数名として不正な形式は展開しない`() {
        // 先頭が数字、ハイフン入りは対象外
        val env = mapOf("1BAD" to "x", "BAD-NAME" to "y")
        val text = "\${1BAD} \${BAD-NAME}"
        assertEquals(text, EnvVarExpander.expand(text, env::get))
    }

    @Test
    fun `アンダースコア始まりの変数名を展開する`() {
        val env = mapOf("_PRIVATE" to "ok")
        assertEquals("ok", EnvVarExpander.expand("\${_PRIVATE}", env::get))
    }
}
