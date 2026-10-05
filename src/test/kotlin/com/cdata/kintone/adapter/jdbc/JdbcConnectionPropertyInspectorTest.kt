package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException

class JdbcConnectionPropertyInspectorTest {

    @TempDir
    lateinit var libDir: Path

    private val driverClass = "cdata.jdbc.sapgateway.SAPGatewayDriver"
    private val jarFilename = "cdata.jdbc.sapgateway.jar"

    @BeforeEach
    fun createJar() {
        Files.writeString(libDir.resolve(jarFilename), "dummy")
    }

    /**
     * `sys_connection_props` の取得成否を接続文字列ごとに指定できる [DriverMetadataSource]。
     * `DriverManager` に触れずに段階的プローブの分岐を検証するために使う。
     */
    private class FakeMetadataSource(
        private val driverProperties: List<DriverProperty> = emptyList(),
        private val succeedingUrls: Set<String> = emptySet(),
        private val propsOnSuccess: List<ConnectionProperty> = listOf(connectionProperty("URL")),
        private val driverPropertiesFails: Boolean = false,
    ) : DriverMetadataSource {

        val attemptedUrls = mutableListOf<String>()
        var loadDriverCount = 0

        override fun loadDriver(jarPath: Path, driverClass: String) {
            loadDriverCount++
        }

        override fun driverProperties(jdbcPrefix: String): List<DriverProperty> {
            if (driverPropertiesFails) throw SQLException("getPropertyInfo failed")
            return driverProperties
        }

        override fun sysProcedureNames(url: String): List<String> = emptyList()

        override fun sysConnectionProps(url: String): List<ConnectionProperty> {
            attemptedUrls += url
            if (url !in succeedingUrls) throw SQLException("CORE [50003] Validation error")
            return propsOnSuccess
        }
    }

    private fun inspector(source: DriverMetadataSource) = JdbcConnectionPropertyInspector(libDir, source)

    private fun driverProperty(name: String, required: Boolean = false) =
        DriverProperty(name, "", required, emptyList())

    // --- config 接続でのプロパティ取得 (Issue #60) ---

    private val configUrl = "jdbc:cdata:sapgateway:config:"

    @Test
    fun `config 接続で取得できたとき完全取得として返す`() {
        val source = FakeMetadataSource(succeedingUrls = setOf(configUrl))
        val result = inspector(source).fetchProperties(driverClass, jarFilename)

        assertEquals(PropertySource.SYS_CONNECTION_PROPS, result.source)
        assertEquals(listOf("URL"), result.properties.map { it.propertyName })
        assertEquals(listOf(configUrl), source.attemptedUrls, "config 接続だけを試す")
    }

    @Test
    fun `接続値を含む接続文字列は試さない`() {
        // 以前はダミー値を詰めた候補を総当たりしていたが、データベース系ドライバーは
        // 実サーバーへ接続するため必ず失敗していた (Issue #60)。
        val source = FakeMetadataSource(
            driverProperties = listOf(
                driverProperty("URL", required = true),
                driverProperty("Namespace", required = true),
            ),
            succeedingUrls = setOf(configUrl),
        )
        inspector(source).fetchProperties(driverClass, jarFilename)

        assertEquals(listOf(configUrl), source.attemptedUrls)
        assertTrue(
            source.attemptedUrls.none { it.contains("=") },
            "接続値を渡してはいけない: ${source.attemptedUrls}",
        )
    }

    @Test
    fun `全候補が失敗したとき getPropertyInfo 由来の縮退結果を返す`() {
        val source = FakeMetadataSource(
            driverProperties = listOf(driverProperty("URL", required = true), driverProperty("Password")),
        )
        val result = inspector(source).fetchProperties(driverClass, jarFilename)

        assertEquals(PropertySource.DRIVER_PROPERTY_INFO, result.source)
        assertTrue(result.isDegraded)
        assertEquals(listOf("URL", "Password"), result.properties.map { it.propertyName })
        assertEquals(Sensitivity.PASSWORD, result.properties.last().sensitivity)
    }

    @Test
    fun `全候補が失敗し getPropertyInfo も空のとき取得失敗として返す`() {
        val result = inspector(FakeMetadataSource()).fetchProperties(driverClass, jarFilename)

        assertEquals(PropertySource.NONE_FETCH_FAILED, result.source)
        assertEquals(emptyList<ConnectionProperty>(), result.properties)
    }

    @Test
    fun `getPropertyInfo が例外でも素の接続文字列で取得できれば完全取得になる`() {
        val source = FakeMetadataSource(
            succeedingUrls = setOf("jdbc:cdata:sapgateway:config:"),
            driverPropertiesFails = true,
        )
        val result = inspector(source).fetchProperties(driverClass, jarFilename)

        assertEquals(PropertySource.SYS_CONNECTION_PROPS, result.source)
    }

    // --- 取得不能のケース ---

    @Test
    fun `JAR が無いときは取得を試みない`() {
        val source = FakeMetadataSource(succeedingUrls = setOf("jdbc:cdata:sapgateway:config:"))
        val result = inspector(source).fetchProperties(driverClass, "missing.jar")

        assertEquals(PropertySource.NONE_JAR_MISSING, result.source)
        assertEquals(0, source.loadDriverCount)
        assertEquals(emptyList<String>(), source.attemptedUrls)
    }

    @Test
    fun `CData ドライバでないクラス名は専用の理由を返す`() {
        Files.writeString(libDir.resolve("postgresql.jar"), "dummy")
        val source = FakeMetadataSource()
        val result = inspector(source).fetchProperties("org.postgresql.Driver", "postgresql.jar")

        assertEquals(PropertySource.NONE_NOT_CDATA_DRIVER, result.source)
        assertEquals(0, source.loadDriverCount)
    }

    // --- キャッシュ ---

    @Test
    fun `完全取得はキャッシュされ二度目は取得しない`() {
        val source = FakeMetadataSource(succeedingUrls = setOf("jdbc:cdata:sapgateway:config:"))
        val inspector = inspector(source)

        inspector.fetchProperties(driverClass, jarFilename)
        val second = inspector.fetchProperties(driverClass, jarFilename)

        assertEquals(PropertySource.SYS_CONNECTION_PROPS, second.source)
        assertEquals(1, source.attemptedUrls.size, "キャッシュが効いていない: ${source.attemptedUrls}")
        assertEquals(1, source.loadDriverCount)
    }

    @Test
    fun `縮退結果はキャッシュせず二度目も取得を試みる`() {
        // 原因 (JAR 差し替え・ドライバ更新) が解消されたときに完全取得へ戻れるようにする。
        val source = FakeMetadataSource(driverProperties = listOf(driverProperty("URL", required = true)))
        val inspector = inspector(source)

        inspector.fetchProperties(driverClass, jarFilename)
        val attemptsAfterFirst = source.attemptedUrls.size
        inspector.fetchProperties(driverClass, jarFilename)

        assertTrue(source.attemptedUrls.size > attemptsAfterFirst, "縮退をキャッシュしてはいけない")
    }

    @Test
    fun `invalidateCache 後は再取得する`() {
        val source = FakeMetadataSource(succeedingUrls = setOf("jdbc:cdata:sapgateway:config:"))
        val inspector = inspector(source)

        inspector.fetchProperties(driverClass, jarFilename)
        inspector.invalidateCache()
        inspector.fetchProperties(driverClass, jarFilename)

        assertEquals(2, source.attemptedUrls.size)
    }

    private companion object {
        fun connectionProperty(name: String) = ConnectionProperty(
            propertyName = name,
            displayName = name,
            shortDescription = "",
            type = PropertyType.STRING,
            defaultValue = null,
            allowedValues = emptyList(),
            category = "Authentication",
            required = true,
            sensitivity = Sensitivity.NONE,
            visible = true,
            hierarchy = "",
            ordinal = 0,
            categoryOrdinal = 1,
        )
    }
}
