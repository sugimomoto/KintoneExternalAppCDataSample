package com.cdata.kintone.adapter.jdbc

import com.cdata.kintone.adapter.config.JdbcConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager

private val log = KotlinLogging.logger {}

/**
 * HikariCP + 動的に読み込んだ JDBC Driver で接続を提供する。
 * `jdbc.yaml` の `driver-jar` に CData JDBC Driver の JAR パスを指定する想定。
 */
class JdbcConnectionProvider(private val config: JdbcConfig) : ConnectionProvider {

    private val dataSource: HikariDataSource

    init {
        loadDriver(config.driverJar, config.driverClass)
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            // driverClassName は指定しない（HikariCP がコンテキストクラスローダーで探そうとして
            // URLClassLoader 経由でロードしたドライバが見えない問題を回避するため）。
            // DriverManager は registerDriver() 済みの DriverShim を URL マッチで見つける。
            maximumPoolSize = config.pool.maximumPoolSize
            connectionTimeout = config.pool.connectionTimeout
            poolName = "CDataKintoneAdapterPool"
        }
        log.info { "JDBC 接続プールを初期化: ${maskUrl(config.url)} (pool=${config.pool.maximumPoolSize})" }
        dataSource = HikariDataSource(hikariConfig)
    }

    /** プールから接続を1つ借りる。`use {}` で自動返却。 */
    override fun connection(): Connection = dataSource.connection

    override fun close() {
        log.info { "JDBC 接続プールを閉じる" }
        dataSource.close()
    }

    companion object {
        /**
         * JAR ファイルから JDBC ドライバを動的に読み込み、`DriverManager` に登録する。
         * JAR パスが存在しない、または既に classpath にあるドライバの場合はスキップする。
         */
        fun loadDriver(jarPath: String, driverClass: String) {
            // まず classpath に既にあるかをチェック
            try {
                Class.forName(driverClass)
                log.debug { "ドライバは既に classpath にあるため動的ロードをスキップ: $driverClass" }
                return
            } catch (_: ClassNotFoundException) {
                // 動的ロードに進む
            }

            val path = Path.of(jarPath)
            if (!Files.exists(path)) {
                throw JdbcDriverLoadException("ドライバ JAR が見つかりません: $jarPath")
            }

            try {
                val classLoader = URLClassLoader(arrayOf(path.toUri().toURL()), JdbcConnectionProvider::class.java.classLoader)
                val driverClassRef = Class.forName(driverClass, true, classLoader)
                val driverInstance = driverClassRef.getDeclaredConstructor().newInstance() as Driver
                DriverManager.registerDriver(DriverShim(driverInstance))
                log.info { "JDBC Driver をロード: $driverClass from $jarPath" }
            } catch (e: Exception) {
                throw JdbcDriverLoadException("JDBC Driver のロードに失敗: $driverClass from $jarPath", e)
            }
        }

        /** 接続文字列の `Password=xxx` 部分をマスキングする（ログ出力用）。 */
        fun maskUrl(url: String): String {
            return url.replace(Regex("(?i)(password=)([^;]*)"), "$1***")
        }
    }
}

/**
 * URLClassLoader 経由でロードされた Driver を DriverManager から見えるようにするためのラッパ。
 * DriverManager は親クラスローダー以外の Driver を弾くため、shim が必要。
 */
private class DriverShim(private val delegate: Driver) : Driver by delegate {
    override fun connect(url: String?, info: java.util.Properties?) = delegate.connect(url, info)
    override fun acceptsURL(url: String?): Boolean = delegate.acceptsURL(url)
}

class JdbcDriverLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
