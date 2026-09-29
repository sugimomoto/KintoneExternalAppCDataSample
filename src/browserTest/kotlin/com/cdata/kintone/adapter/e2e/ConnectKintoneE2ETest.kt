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
import java.nio.file.Files
import java.nio.file.Path

/**
 * E2E-06: kintone 接続画面で公開鍵セクション・接続キーフォームが表示される。
 */
class ConnectKintoneE2ETest : BrowserTestBase() {

    override fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {
        // 公開鍵を agent ディレクトリに配置 (ダミー)
        Files.writeString(
            agentDir.resolve("public-key.pem"),
            """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAdummy_e2e_public_key
-----END PUBLIC KEY-----""",
        )
        // 連携を 1 件投入
        ConfigStore.open(configDir).saveTableSet(
            "demo",
            TableConfigSet(
                server = ServerConfig(port = 18098, plaintext = true),
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
    fun `E2E-06 connect 画面で 3 ステップが見える`() {
        page.navigate("$baseUrl/syncs/demo/connect")
        assertTrue(page.locator("text=Step 1: 公開鍵を kintone に登録").count() > 0)
        assertTrue(page.locator("text=Step 2: kintone 管理画面でコネクタを追加").count() > 0)
        assertTrue(page.locator("text=Step 3: 接続キーを入力して接続").count() > 0)
    }

    @Test
    fun `E2E-06b 公開鍵がページ内に埋め込まれている`() {
        page.navigate("$baseUrl/syncs/demo/connect")
        assertTrue(
            page.content().contains("BEGIN PUBLIC KEY"),
            "公開鍵ヘッダがページ内に存在する",
        )
    }

    @Test
    fun `E2E-06c 公開鍵ダウンロードエンドポイントが PEM を返す`() {
        val resp = page.request().get("$baseUrl/syncs/demo/public-key.pem")
        assertEquals(200, resp.status())
        val body = String(resp.body(), Charsets.UTF_8)
        assertTrue(body.contains("BEGIN PUBLIC KEY"))
    }

    @Test
    fun `E2E-06d 接続して開始 ボタンが存在`() {
        page.navigate("$baseUrl/syncs/demo/connect")
        assertTrue(page.locator("button:has-text(\"接続して開始\")").count() > 0)
    }
}
