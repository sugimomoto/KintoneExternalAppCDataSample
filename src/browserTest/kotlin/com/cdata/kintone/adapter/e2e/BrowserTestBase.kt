package com.cdata.kintone.adapter.e2e

import com.cdata.kintone.adapter.web.AppContext
import com.cdata.kintone.adapter.web.WebUiServer
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

/**
 * ブラウザ E2E テストの共通基盤。
 * - クラスごとに 1 つの Web UI サーバを起動 (TempDir 上)
 * - Playwright Browser を共有
 * - 各テストで新しい Page を作成
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BrowserTestBase {

    protected lateinit var baseUrl: String
        private set

    private lateinit var tempDir: Path
    private lateinit var context: AppContext
    private lateinit var server: WebUiServer
    private lateinit var playwright: Playwright
    private lateinit var browser: Browser

    protected lateinit var page: Page

    /**
     * テンプレ環境を準備するための拡張ポイント。
     * テストクラスで `config/lib/agent` ディレクトリにファイルを置くのに使う。
     */
    protected open fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {}

    @BeforeAll
    fun startServer() {
        tempDir = Files.createTempDirectory("adapter-e2e-")
        val configDir = tempDir.resolve("config").also { Files.createDirectories(it) }
        val libDir = tempDir.resolve("lib").also { Files.createDirectories(it) }
        val agentDir = tempDir.resolve("agent").also { Files.createDirectories(it) }
        seedTestData(configDir, libDir, agentDir)

        context = AppContext.create(configDir = configDir, libDir = libDir, agentRoot = agentDir)
        val port = findFreePort()
        server = WebUiServer(context, port = port, bindAddress = "127.0.0.1")
        server.start(wait = false)
        baseUrl = "http://127.0.0.1:$port"

        playwright = Playwright.create()
        val headless = (System.getProperty("playwright.headless") ?: "true").toBoolean()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(headless),
        )
    }

    @AfterAll
    fun stopServer() {
        runCatching { browser.close() }
        runCatching { playwright.close() }
        runCatching { server.close() }
        runCatching { tempDir.toFile().deleteRecursively() }
    }

    @BeforeEach
    fun newPage() {
        page = browser.newContext().newPage()
    }

    @AfterEach
    fun closePage() {
        runCatching { page.close() }
    }

    private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
}
