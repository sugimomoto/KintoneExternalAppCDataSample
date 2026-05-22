package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.jdbc.ConnectionProvider
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.jdbc.JdbcUrlEnhancer
import com.cdata.kintone.adapter.service.AdapterServiceImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.protobuf.services.ProtoReflectionServiceV1

private val log = KotlinLogging.logger {}

/**
 * 1 テーブル分の gRPC サーバを管理する。
 *
 * フェーズ1 の `ServeCommand` 内のロジックを抽出し、`MultiAdapterRunner` から
 * 複数インスタンス同居できるよう [AutoCloseable] として独立した責務にした。
 *
 * - 各サーバは独立した [HealthStatusManager] を持つ。
 * - `port == 0` の場合、OS が自動的に空きポートを割り当てる ([actualPort] で取得)。
 *
 * @param tableName テーブル識別名（ログ・ヘルスチェックサービス名用）。
 * @param config 1 テーブル分の設定セット。
 * @param connectionProviderFactory JDBC 接続プロバイダの生成関数（テストでは Fake を注入）。
 */
class TableAdapterServer(
    val tableName: String,
    private val config: TableConfigSet,
    private val connectionProviderFactory: (JdbcConfig) -> ConnectionProvider = ::JdbcConnectionProvider,
    /** OAuth キャッシュの親ディレクトリ。tableName ごとに `<dir>/oauth/<table>.txt` を割り当てる。 */
    private val oauthCacheBaseDir: String = "./run",
) : AutoCloseable {

    private var grpcServer: Server? = null
    private var connectionProvider: ConnectionProvider? = null
    private val healthManager = HealthStatusManager()

    /** 起動後の実ポート（`port: 0` で auto-allocate された場合の解決値）。 */
    val actualPort: Int
        get() = grpcServer?.port
            ?: throw IllegalStateException("サーバ未起動: $tableName")

    fun start(): TableAdapterServer {
        check(grpcServer == null) { "$tableName は既に起動済み" }

        // OAuth キャッシュをテーブル別に分離（ユーザが明示指定済みなら尊重）
        val cachePath = JdbcUrlEnhancer.cachePathFor(oauthCacheBaseDir, tableName)
        val effectiveJdbc = config.jdbc.copy(
            url = JdbcUrlEnhancer.withOAuthCachePerTable(config.jdbc.url, cachePath),
        )
        val provider = connectionProviderFactory(effectiveJdbc)
        connectionProvider = provider
        val effectiveConfig = config.copy(jdbc = effectiveJdbc)
        val service = AdapterServiceImpl(effectiveConfig, provider)

        healthManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING)
        healthManager.setStatus(SERVICE_NAME, HealthCheckResponse.ServingStatus.SERVING)

        val server = ServerBuilder
            .forPort(config.server.port)
            .addService(service)
            .addService(ProtoReflectionServiceV1.newInstance())
            .addService(healthManager.healthService)
            .build()
            .start()

        grpcServer = server
        log.info {
            "TableAdapterServer 起動: table=$tableName port=${server.port} " +
                "(configured=${config.server.port})"
        }
        return this
    }

    /** サーバ停止リクエスト（停止完了は [awaitTermination] で待つ）。 */
    fun shutdown() {
        healthManager.setStatus("", HealthCheckResponse.ServingStatus.NOT_SERVING)
        grpcServer?.shutdown()
    }

    /** サーバ停止完了まで待機。テスト用。 */
    fun awaitTermination() {
        grpcServer?.awaitTermination()
    }

    override fun close() {
        log.info { "TableAdapterServer 停止: table=$tableName" }
        shutdown()
        grpcServer?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        runCatching { connectionProvider?.close() }
        grpcServer = null
        connectionProvider = null
    }

    companion object {
        const val SERVICE_NAME = "cybozu.data_connector.adapter.v1.AdapterService"
    }
}
