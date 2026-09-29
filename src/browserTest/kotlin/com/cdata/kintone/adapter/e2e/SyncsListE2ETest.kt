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
 * E2E-03: 連携一覧 + 詳細表示 + ナビゲーション。
 */
class SyncsListE2ETest : BrowserTestBase() {

    override fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {
        // テスト用に 1 件サンプル連携を投入
        val source = ConfigStore.open(configDir)
        source.saveTableSet(
            "demo-account",
            TableConfigSet(
                server = ServerConfig(port = 18099, bindAddress = "0.0.0.0", plaintext = true),
                jdbc = JdbcConfig(
                    driverClass = "cdata.jdbc.salesforce.SalesforceDriver",
                    driverJar = "./lib/cdata.jdbc.salesforce.jar",
                    url = "jdbc:salesforce:User=u;Password=p;",
                ),
                table = TableConfig(
                    name = "Account",
                    primaryKey = PrimaryKeyConfig("id", "Id"),
                    columns = listOf(ColumnConfig("name", "Name", ColumnType.TEXT)),
                ),
                capability = CapabilityConfig(
                    recordIdType = RecordIdType.TEXT,
                    filterableFields = listOf("id"),
                    sortableFields = listOf("id"),
                ),
            ),
        )
    }

    @Test
    fun `E2E-03 連携一覧に投入済みデータが表示される`() {
        page.navigate("$baseUrl/syncs")
        assertTrue(page.locator("h2").innerText().contains("連携"), "h2 に '連携' が含まれる")
        assertTrue(page.locator("text=demo-account").count() > 0, "demo-account 行が表示される")
    }

    @Test
    fun `E2E-03b 詳細画面で kintone Agent セクションが見える`() {
        page.navigate("$baseUrl/syncs/demo-account")
        assertTrue(page.locator("text=kintone Agent").count() > 0)
        assertTrue(page.locator("text=Token").count() > 0)
    }

    @Test
    fun `E2E-03c 詳細画面の主要アクションボタン`() {
        page.navigate("$baseUrl/syncs/demo-account")
        assertTrue(page.locator("a:has-text(\"kintone と接続\")").count() > 0)
        assertTrue(page.locator("a:has-text(\"ログを見る\")").count() > 0)
        assertTrue(page.locator("a:has-text(\"編集\")").count() > 0)
    }
}
