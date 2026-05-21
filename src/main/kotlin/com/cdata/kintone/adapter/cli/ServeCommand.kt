package com.cdata.kintone.adapter.cli

import com.cdata.kintone.adapter.config.ConfigLoader
import com.cdata.kintone.adapter.jdbc.JdbcConnectionProvider
import com.cdata.kintone.adapter.service.AdapterServiceImpl
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ServerBuilder
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * `adapter serve` サブコマンド。
 * 設定を読み込んで gRPC Netty サーバを起動する。
 */
class ServeCommand : CliktCommand(name = "serve") {

    private val configDir: Path by option("--config-dir", help = "設定ディレクトリ（default: ./config）")
        .path(mustExist = true, canBeFile = false)
        .default(Path.of("./config"))

    override fun run() {
        val config = ConfigLoader(configDir).load()
        val connectionProvider = JdbcConnectionProvider(config.jdbc)
        val service = AdapterServiceImpl(config, connectionProvider)

        // gRPC 標準ヘルスチェック (grpc.health.v1.Health)
        // - "" (空文字列): サーバ全体の状態
        // - "cybozu.data_connector.adapter.v1.AdapterService": AdapterService の状態
        val healthManager = HealthStatusManager()
        healthManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING)
        healthManager.setStatus(
            "cybozu.data_connector.adapter.v1.AdapterService",
            HealthCheckResponse.ServingStatus.SERVING,
        )

        val server = ServerBuilder
            .forPort(config.server.port)
            .addService(service)
            // gRPC server reflection を有効化（grpcurl から proto ファイル不要でアクセス可能）
            .addService(ProtoReflectionServiceV1.newInstance())
            // 標準ヘルスチェックサービス
            .addService(healthManager.healthService)
            .build()
            .start()

        log.info { "Adapter サーバ起動: port=${config.server.port}" }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutdown フック実行: サーバ停止中..." }
                healthManager.setStatus("", HealthCheckResponse.ServingStatus.NOT_SERVING)
                server.shutdown()
                connectionProvider.close()
            },
        )

        server.awaitTermination()
    }
}
