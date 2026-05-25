package com.cdata.kintone.adapter.e2e

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * E2E: kintone 接続画面の「鍵ペアを生成」ボタンが秘密鍵を画面に出さず
 * 公開鍵だけを表示するフローに切り替えることを確認する。
 */
class KeyPairGenerationE2ETest : BrowserTestBase() {

    private lateinit var agentRoot: Path

    override fun seedTestData(configDir: Path, libDir: Path, agentDir: Path) {
        // ダミーの sync 設定を 1 件作る (connect 画面のレンダリングに必要)
        val tableDir = configDir.resolve("tables/demo-sync").also { Files.createDirectories(it) }
        Files.writeString(tableDir.resolve("server.yaml"), "port: 0\nplaintext: true\n")
        Files.writeString(
            tableDir.resolve("jdbc.yaml"),
            "driver-class: org.h2.Driver\ndriver-jar: ./lib/h2.jar\nurl: jdbc:h2:mem:test\n",
        )
        Files.writeString(
            tableDir.resolve("table.yaml"),
            "table-name: demo\nfields:\n  - name: id\n    type: STRING\n",
        )
        Files.writeString(tableDir.resolve("capability.yaml"), "record-id-type: TEXT\n")
        agentRoot = agentDir
    }

    @BeforeEach
    fun clearKeys() {
        agentRoot.resolve("public-key.pem").deleteIfExists()
        agentRoot.resolve("private-key.pem").deleteIfExists()
    }

    @Test
    fun `E2E-23 鍵ペア未生成時にボタンが表示され、押下で公開鍵だけが表示される`() {
        page.navigate("$baseUrl/syncs/demo-sync/connect")
        val before = page.locator("main").innerText()
        assertTrue(before.contains("鍵ペアがまだ生成されていません"), "未生成バナーが表示される")
        assertTrue(page.locator("button:has-text('鍵ペアを生成')").count() > 0)

        page.locator("button:has-text('鍵ペアを生成')").click()
        page.waitForURL("**/connect**")

        val after = page.locator("main").innerText()
        assertTrue(after.contains("鍵ペアを生成しました"), "成功バナーが表示される")
        assertTrue(after.contains("Step 1"))
        assertTrue(after.contains("公開鍵をコピー"), "Step 1 で公開鍵 UI が出る")
        // 秘密鍵の文字列やパスは画面に出さない
        assertTrue(!after.contains("PRIVATE KEY"), "画面に秘密鍵を表示しない")

        assertTrue(agentRoot.resolve("public-key.pem").exists(), "公開鍵ファイル生成")
        assertTrue(agentRoot.resolve("private-key.pem").exists(), "秘密鍵ファイル生成")
    }

    @Test
    fun `E2E-24 鍵ペアが既に存在する場合の再生成は拒否される`() {
        // 鍵をまず生成しておく (各テストは @BeforeEach で初期化されるため独立)
        page.navigate("$baseUrl/syncs/demo-sync/connect")
        page.locator("button:has-text('鍵ペアを生成')").click()
        page.waitForURL("**/connect**")
        val originalPublic = Files.readString(agentRoot.resolve("public-key.pem"))

        // 既に鍵がある状態で POST すると、リダイレクト先で「既に存在」エラーバナーが出る
        page.evaluate(
            """async () => {
                const r = await fetch('/syncs/demo-sync/keypair/generate', {
                    method: 'POST', body: new FormData(),
                });
                return await r.text();
            }""".trimIndent(),
        )

        val afterPublic = Files.readString(agentRoot.resolve("public-key.pem"))
        assertTrue(originalPublic == afterPublic, "公開鍵は上書きされない")
    }
}
