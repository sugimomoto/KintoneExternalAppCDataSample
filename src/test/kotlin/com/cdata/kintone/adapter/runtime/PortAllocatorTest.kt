package com.cdata.kintone.adapter.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ServerSocket

/**
 * [PortAllocator] の単体テスト。
 * - レンジ内で順番に空きポートを返す
 * - 既使用ポートを記録/回避
 * - OS バインド確認による実 listen 検証
 */
class PortAllocatorTest {

    @Test
    fun `next - 範囲の先頭から順番に返す`() {
        val allocator = PortAllocator(start = 19_000, end = 19_010)
        assertEquals(19_000, allocator.next())
        assertEquals(19_001, allocator.next())
        assertEquals(19_002, allocator.next())
    }

    @Test
    fun `reserve - 予約済みポートはスキップ`() {
        val allocator = PortAllocator(start = 19_100, end = 19_110)
        allocator.reserve(19_100)
        allocator.reserve(19_102)
        assertEquals(19_101, allocator.next())
        assertEquals(19_103, allocator.next())
    }

    @Test
    fun `next - レンジ外は IllegalStateException`() {
        val allocator = PortAllocator(start = 19_200, end = 19_201)
        allocator.next() // 19_200
        allocator.next() // 19_201
        assertThrows<IllegalStateException> { allocator.next() }
    }

    @Test
    fun `findAvailable - 実際に listen 可能なポートを返す`() {
        val allocator = PortAllocator(start = 19_300, end = 19_400)
        val port = allocator.findAvailable()
        // 返却されたポートで実際に bind できることを確認
        ServerSocket(port).use { socket ->
            assertEquals(port, socket.localPort)
        }
    }

    @Test
    fun `findAvailable - 既に listen 中のポートはスキップ`() {
        val occupiedPort = 19_500
        ServerSocket(occupiedPort).use {
            val allocator = PortAllocator(start = occupiedPort, end = occupiedPort + 5)
            val port = allocator.findAvailable()
            assertNotEquals(occupiedPort, port)
            assertTrue(port in (occupiedPort + 1)..(occupiedPort + 5))
        }
    }

    @Test
    fun `findAvailable - OS 任意ポート (range null) で動作`() {
        val allocator = PortAllocator()
        val port = allocator.findAvailable()
        assertNotNull(port)
        assertTrue(port > 0)
        ServerSocket(port).use { /* bind 可能性を担保 */ }
    }

    @Test
    fun `スレッドセーフ - 並行 next() で重複が出ない`() {
        val allocator = PortAllocator(start = 19_600, end = 19_699) // 100 ポート
        val obtained = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        val threads = (1..10).map {
            Thread {
                repeat(10) { obtained.add(allocator.next()) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(100, obtained.size, "重複なく 100 ポート割り当てられる")
        assertThrows<IllegalStateException> { allocator.next() }
    }
}
