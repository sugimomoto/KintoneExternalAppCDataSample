package com.cdata.kintone.adapter.jdbc

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PoolConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JdbcConnectionProviderTest {

    private fun h2Config(): JdbcConfig = JdbcConfig(
        driverClass = "org.h2.Driver",
        driverJar = "", // 既に classpath にあるため未使用
        url = "jdbc:h2:mem:test_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1",
        pool = PoolConfig(maximumPoolSize = 3, connectionTimeout = 5000),
    )

    @Test
    fun `H2 で接続が取得できる`() {
        JdbcConnectionProvider(h2Config()).use { provider ->
            provider.connection().use { conn ->
                assertNotNull(conn)
                val rs = conn.createStatement().executeQuery("SELECT 1")
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
    }

    @Test
    fun `close でプールが閉じる`() {
        val provider = JdbcConnectionProvider(h2Config())
        provider.connection().use { /* 取得確認のみ */ }
        provider.close()
        assertThrows<Exception> {
            provider.connection()
        }
    }

    @Test
    fun `classpath に既にあるドライバは動的ロードをスキップ`() {
        // 存在しない JAR パスでも、classpath に Driver があれば成功する
        val config = h2Config().copy(driverJar = "/path/to/nowhere.jar")
        JdbcConnectionProvider(config).use { provider ->
            provider.connection().use { conn ->
                assertNotNull(conn)
            }
        }
    }

    @Test
    fun `存在しないドライバ JAR ロードでエラー`() {
        val config = JdbcConfig(
            driverClass = "com.nonexistent.driver.NotARealClass",
            driverJar = "/path/to/nowhere.jar",
            url = "jdbc:nonexistent://",
        )
        assertThrows<JdbcDriverLoadException> {
            JdbcConnectionProvider(config)
        }
    }

    @Test
    fun `maskUrl で Password を伏せる`() {
        val url = "jdbc:salesforce:User=alice;Password=secret;SecurityToken=token123;"
        val masked = JdbcConnectionProvider.maskUrl(url)
        assertEquals("jdbc:salesforce:User=alice;Password=***;SecurityToken=token123;", masked)
    }

    @Test
    fun `maskUrl で password 小文字も対応`() {
        val url = "jdbc:postgresql://host:5432/db?user=u&password=s3cr3t"
        val masked = JdbcConnectionProvider.maskUrl(url)
        assertEquals("jdbc:postgresql://host:5432/db?user=u&password=***", masked)
    }
}
