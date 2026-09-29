package com.cdata.kintone.adapter.config

import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Adapter 全体の設定。設定ストア (SQLite) の 1 連携分の行から組み立てた実行時設定を表す。
 * server / jdbc / table / capability の 4 つの設定を 1 つに束ねたもの。
 */
data class AdapterConfig(
    val server: ServerConfig,
    val jdbc: JdbcConfig,
    val table: TableConfig,
    val capability: CapabilityConfig,
)

@Serializable
data class ServerConfig(
    @Serializable(with = PortSerializer::class)
    val port: Int = DEFAULT_PORT,
    @SerialName("bind-address") val bindAddress: String = "127.0.0.1",
    val plaintext: Boolean = true,
) {
    /** `port == 0` の場合、`MultiAdapterRunner` が空きポートを自動割り当てる。 */
    fun isAutoPort(): Boolean = port == AUTO_PORT

    companion object {
        const val DEFAULT_PORT = 8083
        const val AUTO_PORT = 0
        const val AUTO_PORT_KEYWORD = "auto"
    }
}

/**
 * `port` フィールドを Int / String どちらでも受け入れるシリアライザ。
 * - 整数 → そのまま
 * - "auto" → 0 (AUTO_PORT)
 * - その他の文字列 → IllegalArgumentException
 */
private object PortSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ServerConfig.port", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Int {
        val raw = decoder.decodeString().trim()
        if (raw.equals(ServerConfig.AUTO_PORT_KEYWORD, ignoreCase = true)) {
            return ServerConfig.AUTO_PORT
        }
        return raw.toIntOrNull()
            ?: throw IllegalArgumentException(
                "port は整数または \"auto\" を指定してください: '$raw'",
            )
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
data class JdbcConfig(
    @SerialName("driver-class") val driverClass: String,
    @SerialName("driver-jar") val driverJar: String,
    val url: String,
    val pool: PoolConfig = PoolConfig(),
)

@Serializable
data class PoolConfig(
    @SerialName("maximum-pool-size") val maximumPoolSize: Int = DEFAULT_POOL_SIZE,
    @SerialName("connection-timeout") val connectionTimeout: Long = DEFAULT_CONNECTION_TIMEOUT,
) {
    companion object {
        const val DEFAULT_POOL_SIZE = 10
        const val DEFAULT_CONNECTION_TIMEOUT = 30_000L
    }
}

enum class CountStrategy { ACTUAL, ALWAYS_ZERO }

@Serializable
data class CapabilityConfig(
    @SerialName("select-supported") val selectSupported: Boolean = true,
    @SerialName("insert-supported") val insertSupported: Boolean = true,
    @SerialName("update-supported") val updateSupported: Boolean = true,
    @SerialName("delete-supported") val deleteSupported: Boolean = true,
    @SerialName("count-supported") val countSupported: Boolean = true,
    @SerialName("count-strategy") val countStrategy: CountStrategy = CountStrategy.ACTUAL,
    @SerialName("search-supported") val searchSupported: Boolean = false,
    @SerialName("aggregate-supported") val aggregateSupported: Boolean = false,
    @SerialName("record-id-type") val recordIdType: RecordIdType,
    @SerialName("filterable-fields") val filterableFields: List<String> = emptyList(),
    @SerialName("sortable-fields") val sortableFields: List<String> = emptyList(),
)

@Serializable
data class TableConfig(
    val name: String,
    @SerialName("primary-key") val primaryKey: PrimaryKeyConfig,
    val columns: List<ColumnConfig>,
) {
    /** kintone field_id から JDBC カラム名を解決する。主キーまたはカラムリストの両方を見る。 */
    fun toJdbcColumn(kintoneFieldId: String): String {
        if (kintoneFieldId == primaryKey.kintoneFieldId) return primaryKey.jdbcColumn
        return columns.firstOrNull { it.kintoneFieldId == kintoneFieldId }?.jdbcColumn
            ?: error("未定義の kintone field_id: $kintoneFieldId")
    }

    /** 全 JDBC カラム名（主キー + その他）を返す。SELECT * の代わりに使う。 */
    fun allJdbcColumns(): List<String> = listOf(primaryKey.jdbcColumn) + columns.map { it.jdbcColumn }
}

@Serializable
data class PrimaryKeyConfig(
    @SerialName("kintone-field-id") val kintoneFieldId: String,
    @SerialName("jdbc-column") val jdbcColumn: String,
)

@Serializable
data class ColumnConfig(
    @SerialName("kintone-field-id") val kintoneFieldId: String,
    @SerialName("jdbc-column") val jdbcColumn: String,
    val type: ColumnType,
    val options: List<String>? = null,
)
