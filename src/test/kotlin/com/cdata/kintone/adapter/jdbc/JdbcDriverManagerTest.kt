package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

class JdbcDriverManagerTest {

    /** ダミーの JAR を作成。META-INF/services/java.sql.Driver にドライバ名を書き込む。 */
    private fun makeFakeDriverJar(libDir: Path, jarName: String, driverClass: String?): Path {
        val jarPath = libDir.resolve(jarName)
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        }
        JarOutputStream(Files.newOutputStream(jarPath), manifest).use { jar ->
            if (driverClass != null) {
                jar.putNextEntry(ZipEntry("META-INF/services/java.sql.Driver"))
                jar.write(driverClass.toByteArray())
                jar.closeEntry()
            }
            // ダミーの内容
            jar.putNextEntry(ZipEntry("dummy.txt"))
            jar.write("test".toByteArray())
            jar.closeEntry()
        }
        return jarPath
    }

    @Test
    fun `listDrivers - 空ディレクトリで empty`(@TempDir tempDir: Path) {
        val mgr = JdbcDriverManager(tempDir)
        assertEquals(emptyList<JdbcDriverInfo>(), mgr.listDrivers())
    }

    @Test
    fun `listDrivers - jar を 1 つ配置すると 1 件返る`(@TempDir tempDir: Path) {
        makeFakeDriverJar(tempDir, "cdata.jdbc.test.jar", "cdata.jdbc.test.TestDriver")
        val mgr = JdbcDriverManager(tempDir)
        val drivers = mgr.listDrivers()
        assertEquals(1, drivers.size)
        assertEquals("cdata.jdbc.test.jar", drivers[0].filename)
        assertEquals("cdata.jdbc.test.TestDriver", drivers[0].driverClass)
    }

    @Test
    fun `listDrivers - lic ファイル有無で licenseStatus が変わる`(@TempDir tempDir: Path) {
        makeFakeDriverJar(tempDir, "cdata.jdbc.test.jar", "cdata.jdbc.test.TestDriver")
        val noLic = JdbcDriverManager(tempDir).listDrivers().first()
        assertEquals(LicenseStatus.NOT_ACTIVATED, noLic.licenseStatus)

        Files.writeString(tempDir.resolve("cdata.jdbc.test.lic"), "license-bytes")
        val activated = JdbcDriverManager(tempDir).listDrivers().first()
        assertEquals(LicenseStatus.ACTIVATED, activated.licenseStatus)
    }

    @Test
    fun `listDrivers - sizeBytes が含まれる`(@TempDir tempDir: Path) {
        val jar = makeFakeDriverJar(tempDir, "x.jar", "X")
        val expected = Files.size(jar)
        val info = JdbcDriverManager(tempDir).listDrivers().first()
        assertEquals(expected, info.sizeBytes)
    }

    @Test
    fun `detectDriverClass - META-INF からドライバ名を取得`(@TempDir tempDir: Path) {
        val jar = makeFakeDriverJar(tempDir, "x.jar", "  com.example.MyDriver  ")
        val detected = JdbcDriverManager(tempDir).detectDriverClass(jar)
        assertEquals("com.example.MyDriver", detected)
    }

    @Test
    fun `detectDriverClass - services エントリがなければ null`(@TempDir tempDir: Path) {
        val jar = makeFakeDriverJar(tempDir, "x.jar", null)
        assertEquals(null, JdbcDriverManager(tempDir).detectDriverClass(jar))
    }

    @Test
    fun `upload - jar を保存し info を返す`(@TempDir tempDir: Path) {
        val mgr = JdbcDriverManager(tempDir)
        // 別ディレクトリで作ったダミー jar を InputStream として渡す
        val src = Files.createTempDirectory("src")
        try {
            val jar = makeFakeDriverJar(src, "uploaded.jar", "com.test.X")
            val info = mgr.upload("uploaded.jar", Files.newInputStream(jar))
            assertEquals("uploaded.jar", info.filename)
            assertTrue(Files.exists(tempDir.resolve("uploaded.jar")))
            assertEquals("com.test.X", info.driverClass)
        } finally {
            Files.walk(src).sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    @Test
    fun `upload - 拡張子が jar 以外は拒否`(@TempDir tempDir: Path) {
        val mgr = JdbcDriverManager(tempDir)
        assertThrows<IllegalArgumentException> {
            mgr.upload("evil.exe", ByteArrayInputStream("hello".toByteArray()))
        }
    }

    @Test
    fun `upload - Magic Number 不一致は拒否`(@TempDir tempDir: Path) {
        val mgr = JdbcDriverManager(tempDir)
        assertThrows<IllegalArgumentException> {
            mgr.upload("fake.jar", ByteArrayInputStream("not a real jar".toByteArray()))
        }
    }

    @Test
    fun `delete - jar と lic を削除`(@TempDir tempDir: Path) {
        makeFakeDriverJar(tempDir, "cdata.jdbc.test.jar", "X")
        Files.writeString(tempDir.resolve("cdata.jdbc.test.lic"), "license")

        val mgr = JdbcDriverManager(tempDir)
        mgr.delete("cdata.jdbc.test.jar")

        assertFalse(Files.exists(tempDir.resolve("cdata.jdbc.test.jar")))
        assertFalse(Files.exists(tempDir.resolve("cdata.jdbc.test.lic")))
    }

    @Test
    fun `delete - 存在しないファイルは no-op`(@TempDir tempDir: Path) {
        JdbcDriverManager(tempDir).delete("nope.jar")
    }
}
