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
 * 共有 JDBC 設定の `driver-jar` に CData JDBC Driver の JAR パスを指定する想定。
 *
 * OAuth トークンキャッシュの保存先 (`OAuthSettingsLocation`) はここで付与する。
 * 接続を張る経路はすべてこのクラスを通るため、[oauthCacheKey] を**必須引数**に
 * することで付与忘れをコンパイルエラーにしている。接続文字列のマスク処理は
 * 「呼び出し側で必ず通す」規約にしていたため 2 度漏れた (Issue #10 / #16)。
 *
 * 関連: [Issue #11](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/11)
 *
 * @param oauthCacheKey キャッシュの識別子。**データソース接続名**を渡す。
 *   共有接続を参照しない連携では連携名を使う。
 */
class JdbcConnectionProvider(
    config: JdbcConfig,
    oauthCacheKey: String,
    oauthCacheBaseDir: String = DEFAULT_OAUTH_CACHE_DIR,
) : ConnectionProvider {

    /** OAuth キャッシュパスを付与した実効設定。保存済みの設定は書き換えない。 */
    private val config: JdbcConfig = JdbcUrlEnhancer.applyOAuthCache(config, oauthCacheKey, oauthCacheBaseDir)

    private val dataSource: HikariDataSource

    init {
        loadDriver(config.driverJar, config.driverClass)
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = this@JdbcConnectionProvider.config.url
            // driverClassName は指定しない（HikariCP がコンテキストクラスローダーで探そうとして
            // URLClassLoader 経由でロードしたドライバが見えない問題を回避するため）。
            // DriverManager は registerDriver() 済みの DriverShim を URL マッチで見つける。
            maximumPoolSize = config.pool.maximumPoolSize
            connectionTimeout = config.pool.connectionTimeout
            poolName = "CDataKintoneAdapterPool"
        }
        log.info {
            "JDBC 接続プールを初期化: ${ConnectionStringMasker.mask(this@JdbcConnectionProvider.config.url)} " +
                "(pool=${config.pool.maximumPoolSize})"
        }
        dataSource = HikariDataSource(hikariConfig)
    }

    /** プールから接続を1つ借りる。`use {}` で自動返却。 */
    override fun connection(): Connection = dataSource.connection

    override fun close() {
        log.info { "JDBC 接続プールを閉じる" }
        dataSource.close()
    }

    companion object {
        /** OAuth キャッシュの親ディレクトリ。`<dir>/oauth/<key>.txt` を割り当てる。 */
        const val DEFAULT_OAUTH_CACHE_DIR = "./run"

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
