package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException

class LicenseVerifierTest {

    @TempDir
    lateinit var libDir: Path

    private val driverClass = "cdata.jdbc.salesforce.SalesforceDriver"
    private val jarFilename = "cdata.jdbc.salesforce.jar"

    @BeforeEach
    fun createJar() {
        Files.writeString(libDir.resolve(jarFilename), "dummy")
    }

    /**
     * `sys_procedures` の成否を接続文字列ごとに指定できる [DriverMetadataSource]。
     * ライセンス状態に依存せずに判定ロジックを検証するために使う。
     */
    private class FakeMetadataSource(
        private val succeedingUrls: Set<String> = emptySet(),
        private val errorMessage: String = "ライセンス認証されていない状況です",
        private val driverProperties: List<DriverProperty> = emptyList(),
    ) : DriverMetadataSource {

        val attemptedUrls = mutableListOf<String>()

        override fun loadDriver(jarPath: Path, driverClass: String) = Unit

        override fun driverProperties(jdbcPrefix: String): List<DriverProperty> = driverProperties

        override fun sysConnectionProps(url: String): List<ConnectionProperty> = emptyList()

        override fun sysProcedureNames(url: String): List<String> {
            attemptedUrls += url
            if (url !in succeedingUrls) throw SQLException(errorMessage)
            return listOf("GetOAuthAuthorizationUrl", "GetOAuthAccessToken")
        }
    }

    private fun verifier(source: DriverMetadataSource) = LicenseVerifier(libDir, source)

    @Test
    fun `sys_procedures が読めたら有効と判定する`() {
        val source = FakeMetadataSource(succeedingUrls = setOf("jdbc:salesforce:"))

        val result = verifier(source).verify(driverClass, jarFilename)

        assertEquals(LicenseVerification.Valid, result)
    }

    @Test
    fun `ライセンスエラーなら無効と判定し生メッセージを保持する`() {
        // メッセージはロケール依存なのでパースせず、そのまま利用者に見せる。
        val source = FakeMetadataSource(errorMessage = "ライセンス認証されていない状況です [コード：I]")

        val result = verifier(source).verify(driverClass, jarFilename)

        val invalid = assertInstanceOf(LicenseVerification.Invalid::class.java, result)
        assertTrue(invalid.rawMessage.contains("ライセンス認証されていない"), "実際: ${invalid.rawMessage}")
    }

    @Test
    fun `英語のライセンスエラーでも無効と判定する`() {
        // 分類のパースをしていないので言語に依存しない。
        val source = FakeMetadataSource(errorMessage = "The license has not been activated")

        val result = verifier(source).verify(driverClass, jarFilename)

        assertInstanceOf(LicenseVerification.Invalid::class.java, result)
    }

    @Test
    fun `空の接続文字列を拒否するドライバーでも次の候補で判定できる`() {
        // 26.x 系は空の接続文字列を検証で弾く (Issue #15)。
        val withDummies = "jdbc:salesforce:Offline=true;InitiateOAuth=OFF;URL=${ConnectionPropertyProbe.PROBE_URL};"
        val source = FakeMetadataSource(
            succeedingUrls = setOf(withDummies),
            driverProperties = listOf(
                DriverProperty("Offline", "", false, emptyList()),
                DriverProperty("InitiateOAuth", "", false, emptyList()),
                DriverProperty("URL", "", true, emptyList()),
            ),
        )

        val result = verifier(source).verify(driverClass, jarFilename)

        assertEquals(LicenseVerification.Valid, result)
        assertTrue(source.attemptedUrls.size > 1, "候補を切り替えていない: ${source.attemptedUrls}")
    }

    @Test
    fun `JAR が無ければ無効と判定する`() {
        val source = FakeMetadataSource(succeedingUrls = setOf("jdbc:salesforce:"))

        val result = verifier(source).verify(driverClass, "missing.jar")

        val invalid = assertInstanceOf(LicenseVerification.Invalid::class.java, result)
        assertTrue(invalid.rawMessage.contains("見つかりません"), "実際: ${invalid.rawMessage}")
        assertTrue(source.attemptedUrls.isEmpty(), "JAR が無いのに接続を試みている")
    }

    @Test
    fun `CData ドライバーでなければ無効と判定する`() {
        Files.writeString(libDir.resolve("postgresql.jar"), "dummy")
        val source = FakeMetadataSource()

        val result = verifier(source).verify("org.postgresql.Driver", "postgresql.jar")

        val invalid = assertInstanceOf(LicenseVerification.Invalid::class.java, result)
        assertTrue(invalid.rawMessage.contains("CData"), "実際: ${invalid.rawMessage}")
    }
}
