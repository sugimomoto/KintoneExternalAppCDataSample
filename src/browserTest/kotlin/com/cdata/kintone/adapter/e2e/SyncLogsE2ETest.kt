package com.cdata.kintone.adapter.e2e

import com.cdata.kintone.adapter.config.CapabilityConfig
import com.cdata.kintone.adapter.config.ColumnConfig
import com.cdata.kintone.adapter.config.JdbcConfig
import com.cdata.kintone.adapter.config.PrimaryKeyConfig
import com.cdata.kintone.adapter.config.ServerConfig
import com.cdata.kintone.adapter.config.TableConfig
import com.cdata.kintone.adapter.config.TableConfigSet
import com.cdata.kintone.adapter.config.ConfigStore
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * E2E-07: ログ画面が表示され、Adapter / Agent ペインが存在する。
 */
class SyncLogsE2ETest : BrowserTestBase() {

    override fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {
        ConfigStore.open(configDir).saveTableSet(
            "demo",
            TableConfigSet(
                server = ServerConfig(port = 18097, plaintext = true),
                jdbc = JdbcConfig(
                    driverClass = "cdata.jdbc.salesforce.SalesforceDriver",
                    driverJar = "./lib/x.jar",
                    url = "jdbc:salesforce:",
                ),
                table = TableConfig(
                    name = "X",
                    primaryKey = PrimaryKeyConfig("id", "Id"),
                    columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
                ),
                capability = CapabilityConfig(recordIdType = RecordIdType.TEXT),
            ),
        )
    }

    @Test
    fun `E2E-07 ログ画面に Adapter Agent ペイン + フィルタが表示される`() {
        page.navigate("$baseUrl/syncs/demo/logs")
        assertTrue(page.locator("text=Adapter").count() > 0)
        assertTrue(page.locator("text=Agent (kintone コンテナ)").count() > 0)
        // レベルフィルタ
        assertTrue(page.locator("input.lvl[data-level=\"INFO\"]").count() > 0)
        assertTrue(page.locator("input.lvl[data-level=\"WARN\"]").count() > 0)
        assertTrue(page.locator("input.lvl[data-level=\"ERROR\"]").count() > 0)
        // 検索 + 自動スクロール
        assertTrue(page.locator("#log-search").count() > 0)
        assertTrue(page.locator("#log-autoscroll").count() > 0)
    }
}
