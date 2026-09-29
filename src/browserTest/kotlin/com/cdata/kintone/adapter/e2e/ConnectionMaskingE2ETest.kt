package com.cdata.kintone.adapter.e2e

import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.config.JdbcConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * E2E-25: 接続文字列の資格情報が画面に平文で出ないこと。
 *
 * 一覧画面は Issue #10、編集画面のプレビューは Issue #16 の回帰テスト。
 * 表示系は経路が増えやすく漏れやすいため、画面ごとに DOM 全体を検査する。
 */
class ConnectionMaskingE2ETest : BrowserTestBase() {
    private val secret = "sUp3r-s3cr3t-p@ssw0rd"
    private val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.TESTPAYLOAD.TESTSIG"

    override fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {
        // 接続編集フォームは lib にドライバが 1 つも無いと警告バナーだけ出して
        // プレビューを描画しない。ドライバ判定は jar 内の services ファイルしか
        // 見ないため、その 1 エントリだけを持つ最小の jar を置く。
        writeDriverStubJar(libDir.resolve("cdata.jdbc.stub.jar"), "org.h2.Driver")

        ConfigStore.open(configDir).saveSharedJdbcConfig(
            "masking-target",
            JdbcConfig(
                driverClass = "org.h2.Driver",
                driverJar = "",
                url = "jdbc:h2:mem:test;User=alice;Password=$secret;PersonalAccessToken=$token;Timeout=60",
            ),
        )
    }

    private fun writeDriverStubJar(path: Path, driverClass: String) {
        Files.newOutputStream(path).use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/services/java.sql.Driver"))
                zip.write(driverClass.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `E2E-25 接続一覧に平文の資格情報が出ない`() {
        page.navigate("$baseUrl/connections")
        val content = page.content()
        assertTrue(content.contains("masking-target"), "対象の接続が一覧に出ていること")
        assertTrue(content.contains("Password=***"), "マスク済みの接続文字列が出ていること")
        assertNoSecrets(content, "/connections")
    }

    @Test
    fun `E2E-25 接続編集画面のプレビューに平文の資格情報が出ない`() {
        page.navigate("$baseUrl/connections/masking-target")

        val preview = page.locator("#url-preview-container")
        assertTrue(preview.count() > 0, "プレビュー欄が描画されていること（描画されないと空振りする）")

        // 検査対象はプレビュー欄に限定する。
        // 編集フォームの入力欄は、編集できる必要がある以上、実値を持たざるを得ない
        // （機微プロパティは type=password で描画される）。
        val previewHtml = preview.innerHTML()
        assertTrue(previewHtml.contains("Password=***"), "プレビューがマスク済みであること: $previewHtml")
        assertTrue(previewHtml.contains("PersonalAccessToken=***"), "トークンもマスクされること")
        assertNoSecrets(previewHtml, "接続編集画面のプレビュー欄")
    }

    private fun assertNoSecrets(content: String, where: String) {
        assertFalse(content.contains(secret), "$where に平文パスワードが出ている")
        assertFalse(content.contains(token), "$where に平文トークンが出ている")
        assertFalse(content.contains("TESTPAYLOAD"), "$where に JWT 断片が出ている")
    }
}
