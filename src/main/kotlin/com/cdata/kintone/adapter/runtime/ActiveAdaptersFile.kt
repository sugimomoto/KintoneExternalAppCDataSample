package com.cdata.kintone.adapter.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists

/**
 * 稼働中 Adapter の状態を JSON ファイルとして書き出す/読み出すヘルパ。
 *
 * `adapter serve-all` 側が `start/stop` のたびに [write] でスナップショットを更新し、
 * `adapter list-active` 側が [read] で取得する。
 *
 * 設計判断: design.md §7.4 では「管理用 gRPC ポート」案 A を採用していたが、
 * 新 proto 定義が必要となり、フェーズ2-A の本筋から外れる。同一ホスト前提なら
 * 案 B 相当（ファイルベース）で十分かつシンプルなため、こちらに切り替えた。
 */
class ActiveAdaptersFile(private val path: Path) {

    fun write(statuses: List<AdapterStatus>) {
        path.parent?.let { Files.createDirectories(it) }
        val payload = ActiveAdaptersPayload(
            adapters = statuses.map { it.toRecord() },
            updatedAt = System.currentTimeMillis(),
        )
        val text = JSON.encodeToString(ActiveAdaptersPayload.serializer(), payload)
        Files.writeString(
            path,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    fun read(): List<AdapterStatus> {
        if (!path.exists()) return emptyList()
        val text = Files.readString(path)
        val payload = JSON.decodeFromString(ActiveAdaptersPayload.serializer(), text)
        return payload.adapters.map { it.toStatus() }
    }

    fun delete() {
        Files.deleteIfExists(path)
    }

    companion object {
        /** デフォルトの状態ファイルパス。 */
        val DEFAULT_PATH: Path = Path.of("./run/active-adapters.json")

        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
private data class ActiveAdaptersPayload(
    val adapters: List<AdapterStatusRecord>,
    @SerialName("updated-at") val updatedAt: Long,
)

@Serializable
private data class AdapterStatusRecord(
    @SerialName("table-name") val tableName: String,
    val port: Int,
    @SerialName("started-at") val startedAt: Long,
    val status: String,
)

private fun AdapterStatus.toRecord(): AdapterStatusRecord = AdapterStatusRecord(
    tableName = tableName, port = port, startedAt = startedAt, status = status,
)

private fun AdapterStatusRecord.toStatus(): AdapterStatus = AdapterStatus(
    tableName = tableName, port = port, startedAt = startedAt, status = status,
)
