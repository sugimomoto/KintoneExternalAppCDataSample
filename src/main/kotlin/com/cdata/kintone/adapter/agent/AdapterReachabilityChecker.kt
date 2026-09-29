package com.cdata.kintone.adapter.agent

import com.cdata.kintone.adapter.runtime.PortAllocator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

private val log = KotlinLogging.logger {}

/**
 * Agent コンテナから Adapter (gRPC) に到達できるかを検証する (Issue #7)。
 *
 * Web UI がコンテナ内で動いている場合、`host.docker.internal:<port>` への経路は
 * Agent コンテナとまったく同じ (どちらも host-gateway 経由でホストの publish 済みポートに出る) ため、
 * Web UI 側から TCP 接続できるかどうかで Agent 視点の到達性を代理検証できる。
 *
 * Web UI を Docker 外 (fat jar) で動かしている場合は `host.docker.internal` を名前解決できないため、
 * `127.0.0.1` にフォールバックして「Adapter が listen しているか」だけを確認する。
 */
class AdapterReachabilityChecker(private val timeoutMs: Int = DEFAULT_TIMEOUT_MS) {

    sealed class Result {
        data class Reachable(val addr: String) : Result()

        /** [reason] はそのまま UI に出せる日本語メッセージ。 */
        data class Unreachable(val addr: String, val reason: String) : Result()
    }

    /**
     * `host:port` 形式の [adapterAddr] へ TCP 接続を試みる。
     */
    fun check(adapterAddr: String): Result {
        val host = adapterAddr.substringBeforeLast(':', "")
        val port = adapterAddr.substringAfterLast(':', "").toIntOrNull()
            ?: return Result.Unreachable(adapterAddr, "Adapter アドレスの形式が不正です: $adapterAddr")
        if (host.isEmpty()) {
            return Result.Unreachable(adapterAddr, "Adapter アドレスの形式が不正です: $adapterAddr")
        }

        return if (canConnect(host, port) || canConnectViaLoopback(host, port)) {
            Result.Reachable(adapterAddr)
        } else {
            Result.Unreachable(adapterAddr, buildReason(port))
        }
    }

    /**
     * Web UI を Docker 外 (fat jar) で動かしている場合、`host.docker.internal` は名前解決できない。
     * その場合に限り loopback で「Adapter が listen しているか」だけを確認する。
     */
    private fun canConnectViaLoopback(host: String, port: Int): Boolean {
        if (host != DOCKER_INTERNAL_HOST || isResolvable(host)) return false
        return canConnect(LOOPBACK_HOST, port).also {
            if (it) log.debug { "$DOCKER_INTERNAL_HOST を解決できないため $LOOPBACK_HOST:$port で確認しました" }
        }
    }

    /** 到達不可の理由メッセージを組み立てる (テストから直接検証するため internal)。 */
    internal fun buildReason(port: Int): String = if (!PortAllocator.isPublished(port)) {
        "Adapter (ポート $port) に到達できません。ポート $port は Docker で公開されていないため、" +
            "Agent コンテナからは接続できません。" +
            "Sync の設定でポートを ${PortAllocator.publishedRangeLabel} の範囲に変更してください。"
    } else {
        "Adapter (ポート $port) に到達できません。Adapter が起動していないか、" +
            "docker-compose.yml でポート ${PortAllocator.publishedRangeLabel} が公開されていない可能性があります。"
    }

    private fun canConnect(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (e: IOException) {
        log.debug { "Adapter へ接続できませんでした: $host:$port (${e.message})" }
        false
    }

    private fun isResolvable(host: String): Boolean = try {
        java.net.InetAddress.getByName(host)
        true
    } catch (e: UnknownHostException) {
        log.debug(e) { "ホスト名を解決できません: $host" }
        false
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000
        private const val DOCKER_INTERNAL_HOST = "host.docker.internal"
        private const val LOOPBACK_HOST = "127.0.0.1"
    }
}
