package com.cdata.kintone.adapter.service

import build.buf.gen.cybozu.data_connector.adapter.v1.AggregateRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.CountRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.CountRequestPayload
import build.buf.gen.cybozu.data_connector.adapter.v1.GetCapabilityRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.GetSchemaRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.MatchOperator
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordIdType as ProtoRecordIdType
import build.buf.gen.cybozu.data_connector.adapter.v1.SearchRequest
import com.cdata.kintone.adapter.config.AdapterConfig
import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.CountStrategy
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.DriverManager
import java.util.UUID

class AdapterServiceImplTest {

    private val tableConfig = TableConfig(
        name = "Account",
        primaryKey = PrimaryKeyConfig("id", "Id"),
        columns = listOf(
            ColumnConfig("name", "Name", ColumnType.TEXT),
            ColumnConfig("revenue", "Revenue", ColumnType.NUMBER),
        ),
    )

    private val capability = CapabilityConfig(
        recordIdType = RecordIdType.NUMBER,
        filterableFields = listOf("id", "name"),
        sortableFields = listOf("id"),
    )

    private fun buildConfig(
        countStrategy: CountStrategy = CountStrategy.ACTUAL,
        searchSupported: Boolean = false,
    ): AdapterConfig {
        val dbUrl = "jdbc:h2:mem:svcTest_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        // 接続を事前に開いて DDL を流す
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.createStatement().execute(
                """
                CREATE TABLE Account (
                    Id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    Name VARCHAR(200),
                    Revenue DOUBLE
                )
                """.trimIndent(),
            )
            conn.createStatement().execute("INSERT INTO Account(Name, Revenue) VALUES ('Acme', 100.0)")
            conn.createStatement().execute("INSERT INTO Account(Name, Revenue) VALUES ('Beta', 200.0)")
        }

        return AdapterConfig(
            server = ServerConfig(port = 0),
            jdbc = JdbcConfig(
                driverClass = "org.h2.Driver",
                driverJar = "",
                url = dbUrl,
            ),
            table = tableConfig,
            capability = capability.copy(countStrategy = countStrategy, searchSupported = searchSupported),
        )
    }

    private lateinit var provider: JdbcConnectionProvider

    @AfterEach
    fun tearDown() {
        if (::provider.isInitialized) {
            try {
                provider.close()
            } catch (_: Exception) {
                // 既にクローズされている場合は無視
            }
        }
    }

    @Test
    fun `getCapability で設定値を返す`() = runBlocking {
        val config = buildConfig()
        provider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, provider)
        val response = service.getCapability(GetCapabilityRequest.getDefaultInstance())
        assertEquals(true, response.payload.selectOperationSupported)
        assertEquals(ProtoRecordIdType.RECORD_ID_TYPE_NUMBER, response.payload.recordIdType)
        assertEquals(listOf("id", "name"), response.payload.filterableFieldsList)
    }

    @Test
    fun `getSchema でフィールド定義を返す`() = runBlocking {
        val config = buildConfig()
        provider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, provider)
        val response = service.getSchema(GetSchemaRequest.getDefaultInstance())
        val schema = response.payload.schemaMap
        assertTrue(schema.containsKey("id"))
        assertTrue(schema["id"]!!.hasRecordIdFieldDefinition())
        assertTrue(schema["name"]!!.hasTextFieldDefinition())
        assertTrue(schema["revenue"]!!.hasNumberFieldDefinition())
    }

    @Test
    fun `count ACTUAL は実件数を返す`() = runBlocking {
        val config = buildConfig(countStrategy = CountStrategy.ACTUAL)
        provider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, provider)
        val request = CountRequest.newBuilder()
            .setPayload(
                CountRequestPayload.newBuilder()
                    .setMatchOperator(MatchOperator.MATCH_OPERATOR_ALL),
            ).build()
        val response = service.count(request)
        assertEquals(2L, response.payload.count)
    }

    @Test
    fun `count ALWAYS_ZERO は常に 0`() = runBlocking {
        val config = buildConfig(countStrategy = CountStrategy.ALWAYS_ZERO)
        provider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, provider)
        val request = CountRequest.newBuilder()
            .setPayload(
                CountRequestPayload.newBuilder()
                    .setMatchOperator(MatchOperator.MATCH_OPERATOR_ALL),
            ).build()
        val response = service.count(request)
        assertEquals(0L, response.payload.count)
    }

    @Test
    fun `search 未サポート時は UNIMPLEMENTED`() = runBlocking {
        val config = buildConfig(searchSupported = false)
        provider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, provider)
        val ex = assertThrows<StatusException> {
            service.search(SearchRequest.getDefaultInstance())
        }
        assertEquals(Status.UNIMPLEMENTED.code, ex.status.code)
    }

    @Test
    fun `aggregate 未サポート時は UNIMPLEMENTED`() = runBlocking {
        val config = buildConfig()
        provider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, provider)
        val ex = assertThrows<StatusException> {
            service.aggregate(AggregateRequest.getDefaultInstance())
        }
        assertEquals(Status.UNIMPLEMENTED.code, ex.status.code)
    }
}
