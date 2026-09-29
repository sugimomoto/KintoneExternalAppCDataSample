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
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `E2E-03d 一覧の各行に削除ボタンが表示される`() {
        page.navigate("$baseUrl/syncs")

        val row = page.locator("tr[data-table=\"demo-account\"]")
        assertTrue(row.count() > 0, "demo-account の行が見つからない")
        // has-text は部分一致のため、別機能の「agent.json を削除」を拾わないよう
        // フォームの action で特定する。
        assertTrue(
            row.locator("form[action=\"/syncs/demo-account/delete\"] button.danger").count() > 0,
            "行の操作列に削除ボタンが無い",
        )
    }

    @Test
    fun `E2E-03e 確認ダイアログでキャンセルすると削除されない`() {
        page.navigate("$baseUrl/syncs")
        // confirm を拒否する。ブラウザ標準の confirm なので Playwright 側で dismiss する。
        page.onDialog { it.dismiss() }

        page.locator("form[action=\"/syncs/demo-account/delete\"] button.danger").click()
        page.waitForTimeout(500.0)

        assertTrue(
            page.locator("tr[data-table=\"demo-account\"]").count() > 0,
            "キャンセルしたのに連携が消えている",
        )
    }

    @Test
    fun `E2E-03f 詳細画面の連携削除フォームは 1 つだけ`() {
        // 一覧と詳細で tableActions() を共用しているため、詳細画面に独立した
        // 削除フォームを残すとボタンが 2 つ並ぶ (Issue #8)。
        // 「agent.json を削除」は別機能なので action で区別する。
        page.navigate("$baseUrl/syncs/demo-account")

        assertEquals(1, page.locator("form[action=\"/syncs/demo-account/delete\"]").count())
    }
}
