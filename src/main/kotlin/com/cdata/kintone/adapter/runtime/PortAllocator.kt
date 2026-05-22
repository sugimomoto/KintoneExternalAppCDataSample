package com.cdata.kintone.adapter.runtime

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * MultiAdapterRunner が複数 gRPC サーバを並行起動する際の
 * ポート割り当てヘルパ。
 *
 * - [next] は [start]..[end] のレンジで「論理的な」空きポートを順番に返す
 *   （実 listen 可否は検証しない。重複を避けたいだけのケース向け）
 * - [findAvailable] は実際に [ServerSocket] を bind 試行し、listen 可能な
 *   ポートを返す。
 *
 * `port: 0` 指定（auto-allocate）は呼び出し側で [findAvailable] を使う。
 */
class PortAllocator(
    private val start: Int = DEFAULT_START,
    private val end: Int = DEFAULT_END,
) {
    private val lock = ReentrantLock()
    private val used = mutableSetOf<Int>()
    private var cursor = start

    /** ポート [port] を予約済みとして記録する。レンジ外でも記録は許可。 */
    fun reserve(port: Int) {
        lock.withLock { used.add(port) }
    }

    /**
     * レンジ内で次の未使用ポートを返す。
     * 使い切った場合は [IllegalStateException]。
     */
    fun next(): Int = lock.withLock {
        while (cursor <= end) {
            val candidate = cursor++
            if (candidate in used) continue
            used.add(candidate)
            return candidate
        }
        throw IllegalStateException(
            "PortAllocator: 利用可能なポートを使い切りました [start=$start, end=$end]",
        )
    }

    /**
     * 実際に [ServerSocket] で bind 試行し、listen 可能なポートを返す。
     * レンジが指定されていればその中で順に試す。範囲外でも OS 任意 (port=0) で取得する。
     */
    fun findAvailable(): Int = lock.withLock {
        // 1) 指定レンジ内を探す
        while (cursor <= end) {
            val candidate = cursor++
            if (candidate in used) continue
            if (canBind(candidate)) {
                used.add(candidate)
                return candidate
            }
            // bind できないポートは used に追加して以降スキップ
            used.add(candidate)
        }
        // 2) レンジ外: OS 任意ポート
        val osPort = pickOsPort()
        used.add(osPort)
        return osPort
    }

    private fun canBind(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    } catch (e: IOException) {
        false
    }

    private fun pickOsPort(): Int = ServerSocket(0).use { it.localPort }

    companion object {
        /** デフォルト範囲。フェーズ1 default の 8083 と被らないよう少し上の帯域を予約する想定。 */
        const val DEFAULT_START = 18_000
        const val DEFAULT_END = 19_000
    }
}
