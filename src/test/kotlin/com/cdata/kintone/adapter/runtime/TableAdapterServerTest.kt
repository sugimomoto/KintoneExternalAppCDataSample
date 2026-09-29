package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import io.grpc.ManagedChannelBuilder
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TableAdapterServerTest {

    private val servers = mutableListOf<TableAdapterServer>()

    @AfterEach
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        servers.clear()
    }

    private fun configFor(port: Int): TableConfigSet = TableConfigSet(
        server = ServerConfig(port = port, bindAddress = "127.0.0.1", plaintext = true),
        // 実 JDBC 接続は行わないダミー（接続プールの初期化のみ走る）。
        // Salesforce JDBC ドライバが lib に無い CI 環境では JdbcConnectionProvider が
        // ロード失敗するため、ここでは `connectionProviderFactory` をテスト用に差し替える。
        jdbc = JdbcConfig(
            driverClass = "com.cdata.kintone.adapter.runtime.FakeDriver",
            driverJar = "/dev/null",
            url = "jdbc:fake:dummy",
        ),
        table = TableConfig(
            name = "Account",
            primaryKey = PrimaryKeyConfig("id", "Id"),
            columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
        ),
        capability = CapabilityConfig(
            recordIdType = RecordIdType.TEXT,
        ),
    )

    private fun startWith(port: Int): TableAdapterServer {
        val server = TableAdapterServer(
            tableName = "test-$port",
            config = configFor(port),
            connectionProviderFactory = { _, _ -> FakeConnectionProvider() },
        ).also { it.start() }
        servers.add(server)
        return server
    }

    @Test
    fun `start - 固定ポートで起動できる`() {
        val server = startWith(port = freePort())
        assertTrue(server.actualPort > 0)
        // ヘルスチェック RPC が応答する
        val channel = ManagedChannelBuilder
            .forAddress("127.0.0.1", server.actualPort)
            .usePlaintext()
            .build()
        try {
            val stub = HealthGrpc.newBlockingStub(channel)
            val resp = stub.check(HealthCheckRequest.newBuilder().setService("").build())
            assertEquals(HealthCheckResponse.ServingStatus.SERVING, resp.status)
        } finally {
            channel.shutdownNow()
        }
    }

    @Test
    fun `start - port=0 なら OS が自動割り当て`() {
        val server = TableAdapterServer(
            tableName = "auto",
            config = configFor(0),
            connectionProviderFactory = { _, _ -> FakeConnectionProvider() },
        ).also { it.start() }
        servers.add(server)
        assertNotEquals(0, server.actualPort)
        assertTrue(server.actualPort > 0)
    }

    @Test
    fun `複数 TableAdapterServer を同一 JVM で起動可能`() {
        val a = startWith(port = 0)
        val b = startWith(port = 0)
        assertNotEquals(a.actualPort, b.actualPort)
    }

    @Test
    fun `close - サーバが停止する`() {
        val server = startWith(port = 0)
        val port = server.actualPort
        server.close()
        // 同じポートで再 bind できる（厳密にはタイミング依存だが、OS割り当てなので空いていればOK）
        assertTrue(port > 0)
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    @Test
    fun `OAuth キャッシュキーをプロバイダに渡す`() {
        // キャッシュパスの決定は JdbcConnectionProvider 側に一本化した (Issue #11)。
        // TableAdapterServer はキーを渡すだけで、URL を組み立てない。
        val passedKeys = mutableListOf<String>()
        val server = TableAdapterServer(
            tableName = "Account",
            config = configFor(0),
            oauthCacheKey = "sf-main",
            connectionProviderFactory = { _, key ->
                passedKeys += key
                FakeConnectionProvider()
            },
        )

        server.use { it.start() }

        assertEquals(listOf("sf-main"), passedKeys)
    }
}
