package com.cdata.kintone.adapter.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * ライセンスが実際に使える状態かを検証する。
 *
 * `.lic` ファイルの有無では分からない。CData のライセンスはマシン (nodeid) に
 * 紐づくため、**別マシンで認証された `.lic` があっても使えない**。
 * ホストで認証した `.lic` をコンテナにマウントしている構成がこれに当たる。
 *
 * 検証には `sys_procedures` の SELECT を使う。ライセンスチェックが走る操作のうち
 * 最も軽く、`Offline=true` で外部通信なしに実行でき、接続先の資格情報も要らない。
 * `sys_connection_props` はライセンス未認証でも読めるため検証には使えない。
 *
 * 関連: [Issue #31](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/31)
 */
class LicenseVerifier(
    private val libDir: Path = Path.of("./lib"),
    private val metadataSource: DriverMetadataSource = JdbcDriverMetadataSource(),
) {

    fun verify(driverClass: String, jarFilename: String): LicenseVerification {
        val jarPath = libDir.resolve(jarFilename)
        if (!Files.exists(jarPath)) {
            return LicenseVerification.Invalid("ドライバー JAR が見つかりません: $jarPath")
        }
        val jdbcPrefix = runCatching { JdbcConnectionPropertyInspector.jdbcPrefixOf(driverClass) }.getOrNull()
            ?: return LicenseVerification.Invalid("CData JDBC Driver ではありません: $driverClass")

        return loadAndProbe(jarPath, jdbcPrefix, driverClass)
    }

    private fun loadAndProbe(jarPath: Path, jdbcPrefix: String, driverClass: String): LicenseVerification {
        val loaded = runCatching { metadataSource.loadDriver(jarPath, driverClass) }
        if (loaded.isFailure) {
            return LicenseVerification.Invalid(
                loaded.exceptionOrNull()?.message ?: "ドライバーのロードに失敗しました",
            )
        }
        return probe(jdbcPrefix, driverClass)
    }

    /**
     * プローブ接続で `sys_procedures` を読む。
     *
     * 候補の生成は #15 の仕組みを再利用する。26.x 系ドライバーは空の接続文字列を
     * 検証で弾くため、ダミー値付きの候補が必要になる。
     */
    private fun probe(jdbcPrefix: String, driverClass: String): LicenseVerification {
        val driverProperties = runCatching { metadataSource.driverProperties(jdbcPrefix) }
            .getOrDefault(emptyList())

        var lastError: String? = null
        ConnectionPropertyProbe.candidateUrls(jdbcPrefix, driverProperties).forEach { url ->
            val result = runCatching { metadataSource.sysProcedureNames(url) }
            if (result.isSuccess) return LicenseVerification.Valid
            lastError = result.exceptionOrNull()?.message
        }
        log.info { "ライセンス検証に失敗しました: $driverClass" }
        // メッセージはロケール依存なのでパースせず、そのまま利用者に見せる (Issue #19 の教訓)。
        return LicenseVerification.Invalid(lastError ?: "ライセンスを検証できませんでした")
    }
}

/**
 * ライセンス検証の結果。
 *
 * 有効 / 無効の 2 値にして分類しないのは、ライセンスエラーのメッセージが
 * **ロケール依存**のため。パースで分類すると実行環境によって壊れる。
 */
sealed class LicenseVerification {
    data object Valid : LicenseVerification()

    /** 使えない。[rawMessage] はドライバーが返したメッセージ。 */
    data class Invalid(val rawMessage: String) : LicenseVerification()
}
