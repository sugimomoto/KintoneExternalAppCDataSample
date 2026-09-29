package com.cdata.kintone.adapter.web.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteConfirmTest {

    @Test
    fun `confirm を返す JS 式になっている`() {
        val onSubmit = DeleteConfirm.syncDeleteOnSubmit("demo-account")

        assertTrue(onSubmit.startsWith("return confirm('"), "実際: $onSubmit")
        assertTrue(onSubmit.endsWith("')"), "実際: $onSubmit")
    }

    @Test
    fun `連携名がメッセージに含まれる`() {
        assertTrue(DeleteConfirm.syncDeleteOnSubmit("demo-account").contains("demo-account"))
    }

    @Test
    fun `削除される対象がメッセージに含まれる`() {
        // 連携の削除は非可逆で、Agent コンテナと agent.json まで消える (AC-3)。
        val message = DeleteConfirm.syncDeleteOnSubmit("demo-account")

        assertTrue(message.contains("Adapter"), "実際: $message")
        assertTrue(message.contains("Agent"), "実際: $message")
        assertTrue(message.contains("agent.json"), "実際: $message")
        assertTrue(message.contains("取り消せません"), "実際: $message")
    }

    @Test
    fun `シングルクォートを含む連携名でも式が壊れない`() {
        // エスケープしないと confirm(' ... ' ... ') となり JS 構文エラーになる。
        val onSubmit = DeleteConfirm.syncDeleteOnSubmit("it's-a-sync")

        assertTrue(onSubmit.contains("it\\'s-a-sync"), "実際: $onSubmit")
        assertFalse(onSubmit.contains("it's-a-sync"), "未エスケープの ' が残っている: $onSubmit")
    }

    @Test
    fun `バックスラッシュを含む連携名でもエスケープされる`() {
        val onSubmit = DeleteConfirm.syncDeleteOnSubmit("path\\to\\sync")

        assertTrue(onSubmit.contains("path\\\\to\\\\sync"), "実際: $onSubmit")
    }

    @Test
    fun `改行を含む連携名でも式が壊れない`() {
        val onSubmit = DeleteConfirm.syncDeleteOnSubmit("line1\nline2")

        assertTrue(onSubmit.contains("line1\\nline2"), "実際: $onSubmit")
        assertFalse(onSubmit.contains("\n"), "生の改行が残っている: $onSubmit")
    }

    @Test
    fun `バックスラッシュとシングルクォートの併用で二重エスケープしない`() {
        // '\' を後に置換すると \' が \\' になり、意味が変わる。
        assertEquals("""\\\'""", DeleteConfirm.escapeJsString("""\'"""))
    }

    @Test
    fun `エスケープ対象を含まない値はそのまま返す`() {
        assertEquals("demo-account", DeleteConfirm.escapeJsString("demo-account"))
    }
}
