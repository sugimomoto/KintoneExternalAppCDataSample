package com.cdata.kintone.adapter.e2e

import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.ConfigStore
import com.microsoft.playwright.options.SelectOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

/**
 * E2E: 新規 Sync 作成のフルフロー (Step1 ~ Step4 + 保存)。
 *
 * H2 in-memory を「データソース接続」として登録し、テーブル定義と
 * カラム情報を実 JDBC 経由で取得する。CData ドライバ無しでウィザードの
 * 4 ステップを完走できることを確認する。
 */
class NewSyncFlowE2ETest : BrowserTestBase() {

    /** H2 のメモリ DB 名 (JVM 全体で共有させるため -1 指定)。 */
    private val dbName = "wizard_e2e_${UUID.randomUUID().toString().replace("-", "")}"
    private val jdbcUrl =
        "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=MSSQLServer"

    override fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {
        // 1. H2 in-memory に実テーブルを作成
        DriverManager.getConnection(jdbcUrl).use { conn ->
            conn.createStatement().execute(
                """
                CREATE TABLE [Customer] (
                    [Id] BIGINT IDENTITY(1, 1) PRIMARY KEY,
                    [Name] VARCHAR(200),
                    [Email] VARCHAR(100),
                    [CreatedAt] TIMESTAMP
                )
                """.trimIndent(),
            )
            conn.createStatement().execute("INSERT INTO [Customer]([Name],[Email]) VALUES ('Alice','a@example.com')")
            conn.createStatement().execute("INSERT INTO [Customer]([Name],[Email]) VALUES ('Bob','b@example.com')")
        }

        // 2. H2 を共通 JDBC 接続として登録 (driverJar は空文字、Class.forName でクラスパスから検出される)
        ConfigStore.open(configDir).saveSharedJdbcConfig(
            "h2-local",
            JdbcConfig(
                driverClass = "org.h2.Driver",
                driverJar = "",
                url = jdbcUrl,
            ),
        )
    }

    @Test
    fun `E2E-09 ウィザードでゼロから新規 Sync を作成して保存`() {
        // === Step 1: 接続選択 ===
        page.navigate("$baseUrl/syncs/new")
        // ラジオボタンで h2-local を選択
        page.locator("input[type=\"radio\"][value=\"h2-local\"]").check()
        page.locator("button:has-text(\"次へ\")").click()

        // === Step 2: テーブル選択 ===
        page.waitForURL("**/syncs/new/step2**")
        // ラジオの value 属性を全取得して Customer を含むものを選ぶ
        val radios = page.locator("input[type=\"radio\"][name=\"table\"]").all()
        val customerValue = radios
            .mapNotNull { it.getAttribute("value") }
            .firstOrNull { it.contains("customer", ignoreCase = true) }
        assertNotNull(customerValue, "テーブル一覧に customer が含まれる")
        page.locator("input[type=\"radio\"][name=\"table\"][value=\"$customerValue\"]").check()
        page.locator("input[name=\"configName\"]").fill("e2e-customer")
        page.locator("button:has-text(\"次へ\")").click()

        // === Step 3: カラム選択 ===
        page.waitForURL("**/syncs/new/step3**")
        // 全カラムがデフォルト ON 状態。Email だけ off にしてみる
        val emailCheckbox = page.locator("input[type=\"checkbox\"][name=\"selectedColumns\"][value=\"Email\"]")
        if (emailCheckbox.count() > 0) {
            emailCheckbox.uncheck()
        }
        page.locator("button:has-text(\"次へ\")").click()

        // === Step 4: マッピング確認 + 保存 ===
        // page は POST 経由なので、HTML の確認だけ
        page.waitForLoadState()
        assertTrue(page.url().contains("/syncs/new/step4") || page.url().contains("/syncs/"),
            "Step4 に到達: ${page.url()}")
        // マッピング行が表示されている (Customer の Name 等)
        assertTrue(page.content().contains("Name"), "マッピングに Name 列が表示")

        // 保存のみクリック
        page.locator("button[name=\"andStart\"][value=\"false\"]").click()

        // === 完了 → 詳細ページに遷移 ===
        page.waitForURL("**/syncs/e2e-customer")
        assertEquals("$baseUrl/syncs/e2e-customer", page.url().substringBefore("?"))
        // 新規 Sync の主要セクションが表示
        assertTrue(page.locator("h2:has-text(\"e2e-customer\")").count() > 0)
        assertTrue(page.locator("text=Customer").count() > 0)
    }

    @Test
    fun `E2E-09b 接続未選択で「次へ」を押しても進めない (バリデーション)`() {
        page.navigate("$baseUrl/syncs/new")
        // ラジオ未選択で「次へ」を押そうとする
        page.locator("button:has-text(\"次へ\")").click()
        // required 属性により遷移しない → URL はそのまま
        assertTrue(page.url().endsWith("/syncs/new"), "URL: ${page.url()}")
    }
}
