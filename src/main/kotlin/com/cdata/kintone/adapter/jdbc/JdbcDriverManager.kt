package com.cdata.kintone.adapter.jdbc

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

/**
 * `lib/` 配下の JDBC Driver JAR を管理するヘルパ。
 *
 * 主な責務:
 * - 配置済み JAR の一覧 (`listDrivers`)
 * - JAR ファイルの追加 (`upload`) と削除 (`delete`)
 * - JAR から ドライバクラス名を自動検出 (`detectDriverClass`)
 *
 * トライアルアクティベーションは [DriverActivator] に分離。
 *
 * 設計詳細: `.steering/20260522-web-ui-and-sqlite/design.md §4`
 */
class JdbcDriverManager(private val libDir: Path) {

    init {
        Files.createDirectories(libDir)
    }

    fun listDrivers(): List<JdbcDriverInfo> {
        if (!libDir.exists()) return emptyList()
        return Files.list(libDir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".jar") }
                .map { jar -> buildInfo(jar) }
                .sorted(Comparator.comparing(JdbcDriverInfo::filename))
                .toList()
        }
    }

    fun detectDriverClass(jar: Path): String? {
        if (!jar.exists()) return null
        return Files.newInputStream(jar).use { fis ->
            ZipInputStream(fis).use { zis ->
                generateSequence { zis.nextEntry }
                    .firstOrNull { it.name == SERVICES_PATH }
                    ?.let { zis.readAllBytes().toString(Charsets.UTF_8) }
                    ?.lines()
                    ?.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?.trim()
            }
        }
    }

    /**
     * JAR をアップロードして lib/ に保存する。
     * - 拡張子 `.jar` 以外は拒否
     * - Magic Number (PK 0x03 0x04) を検証
     */
    fun upload(filename: String, content: InputStream): JdbcDriverInfo {
        require(filename.endsWith(".jar")) {
            "拡張子は .jar のみ受け付けます: $filename"
        }
        val bytes = content.readAllBytes()
        require(bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        ) { "JAR ファイルとして認識できません (Magic Number 不一致)" }

        val target = libDir.resolve(filename)
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return buildInfo(target)
    }

    /** JAR と対応する `.lic` を削除。存在しない場合は no-op。 */
    fun delete(filename: String) {
        require(filename.endsWith(".jar")) { "filename は .jar 拡張子: $filename" }
        Files.deleteIfExists(libDir.resolve(filename))
        Files.deleteIfExists(libDir.resolve(filename.removeSuffix(".jar") + ".lic"))
    }

    private fun buildInfo(jar: Path): JdbcDriverInfo {
        val licPath = libDir.resolve(jar.fileName.toString().removeSuffix(".jar") + ".lic")
        return JdbcDriverInfo(
            filename = jar.fileName.toString(),
            driverClass = detectDriverClass(jar),
            licenseStatus = if (licPath.exists()) LicenseStatus.ACTIVATED else LicenseStatus.NOT_ACTIVATED,
            sizeBytes = Files.size(jar),
        )
    }

    companion object {
        private const val SERVICES_PATH = "META-INF/services/java.sql.Driver"
    }
}

data class JdbcDriverInfo(
    val filename: String,
    val driverClass: String?,
    val licenseStatus: LicenseStatus,
    val sizeBytes: Long,
)

enum class LicenseStatus { ACTIVATED, NOT_ACTIVATED, UNKNOWN }
