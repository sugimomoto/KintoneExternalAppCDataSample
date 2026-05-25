package com.cdata.kintone.adapter.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists

/**
 * kintone Agent コンテナ用の `agent.json` を管理するヘルパ。
 *
 * `agent/tables/<tableName>/agent.json` を CRUD する。
 * Web UI のテーブル詳細画面から呼ばれ、token / adapter_addr の編集を提供する。
 */
class AgentConfigManager(private val agentRoot: Path) {

    /** 指定テーブルの agent.json を読み出す。存在しない場合は null。 */
    fun load(tableName: String): AgentConfig? {
        val path = pathFor(tableName)
        if (!path.exists()) return null
        return runCatching {
            JSON.decodeFromString(serializer<AgentConfig>(), Files.readString(path))
        }.getOrNull()
    }

    /** agent.json を保存。親ディレクトリは自動作成。 */
    fun save(tableName: String, config: AgentConfig) {
        val path = pathFor(tableName)
        Files.createDirectories(path.parent)
        val text = JSON.encodeToString(serializer<AgentConfig>(), config)
        Files.writeString(
            path,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    fun delete(tableName: String) {
        Files.deleteIfExists(pathFor(tableName))
    }

    fun pathFor(tableName: String): Path = agentRoot.resolve("tables/$tableName/agent.json")

    /**
     * docker-compose.multi.yml に追加するサービス定義スニペットを生成。
     * ユーザはこれをコピーして compose ファイルに追記する。
     */
    fun composeSnippet(tableName: String): String {
        return """
            kintone-agent-$tableName:
              <<: *agent-defaults
              container_name: kintone-agent-$tableName
              volumes:
                - ./tables/$tableName/agent.json:/opt/agent/agent.json:ro
                - ./private-key.pem:/opt/agent/private-key.pem:ro
        """.trimIndent()
    }

    companion object {
        val DEFAULT_ROOT: Path = Path.of("./agent")

        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * `agent.json` のスキーマ。kintone-data-connector-agent が期待する形式。
 */
@Serializable
data class AgentConfig(
    val token: String,
    @SerialName("adapter_addr") val adapterAddr: String,
    @SerialName("adapter_plaintext") val adapterPlaintext: Boolean = true,
    @SerialName("private_key_path") val privateKeyPath: String = "/opt/agent/private-key.pem",
)
