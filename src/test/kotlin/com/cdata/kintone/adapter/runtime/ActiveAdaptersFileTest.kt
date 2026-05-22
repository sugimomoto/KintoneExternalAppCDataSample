package com.cdata.kintone.adapter.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * 稼働中 Adapter 状態をローカルファイルに書き出すヘルパ [ActiveAdaptersFile] のテスト。
 *
 * M4-C の管理用 gRPC ポートは proto 追加が必要で重いため、ファイルベース状態管理に
 * 置き換える設計判断（design.md §7.4 案 A → 案 B 相当）。
 * 同一ホストで `serve-all` と `list-active` が別プロセスで動くケースに十分。
 */
class ActiveAdaptersFileTest {

    @Test
    fun `write して read で同じ内容を取得`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("active.json")
        val file = ActiveAdaptersFile(path)
        val statuses = listOf(
            AdapterStatus(tableName = "account", port = 8083, startedAt = 1000L),
            AdapterStatus(tableName = "contact", port = 8084, startedAt = 2000L),
        )
        file.write(statuses)

        val loaded = file.read()
        assertEquals(2, loaded.size)
        assertEquals("account", loaded[0].tableName)
        assertEquals(8083, loaded[0].port)
        assertEquals("contact", loaded[1].tableName)
    }

    @Test
    fun `read - ファイルがなければ空リスト`(@TempDir tempDir: Path) {
        val file = ActiveAdaptersFile(tempDir.resolve("missing.json"))
        assertTrue(file.read().isEmpty())
    }

    @Test
    fun `write - 親ディレクトリがなければ作成`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("nested/run/active.json")
        val file = ActiveAdaptersFile(path)
        file.write(emptyList())
        assertTrue(Files.exists(path))
    }

    @Test
    fun `delete - ファイル削除`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("active.json")
        Files.writeString(path, "[]")
        val file = ActiveAdaptersFile(path)
        file.delete()
        assertFalse(Files.exists(path))
    }

    @Test
    fun `delete - 存在しなくてもエラーにならない`(@TempDir tempDir: Path) {
        val file = ActiveAdaptersFile(tempDir.resolve("absent.json"))
        file.delete()
    }
}
