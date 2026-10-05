package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

/**
 * メタデータ取得用の config 接続文字列を組み立てる [ConnectionPropertyProbe] のテスト。
 *
 * 以前はダミー値を詰めた候補 URL を総当たりしていたが、SQL Server のような
 * データベース系ドライバーは `getConnection()` で実際にサーバーへ接続するため
 * 必ず失敗していた。CData 公式が指定する config 接続文字列に切り替えた。
 *
 * 関連: [Issue #60](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/60)
 */
class ConnectionPropertyProbeTest {

    @Test
    fun `configUrl - データベース系ドライバー`() {
        assertEquals(
            "jdbc:cdata:sql:config:",
            ConnectionPropertyProbe.configUrl("cdata.jdbc.sql.SQLDriver"),
        )
    }

    @Test
    fun `configUrl - SaaS コネクタ`() {
        assertEquals(
            "jdbc:cdata:salesforce:config:",
            ConnectionPropertyProbe.configUrl("cdata.jdbc.salesforce.SalesforceDriver"),
        )
        assertEquals(
            "jdbc:cdata:googlesheets:config:",
            ConnectionPropertyProbe.configUrl("cdata.jdbc.googlesheets.GoogleSheetsDriver"),
        )
    }

    @Test
    fun `configUrl - cdata を挟んだ形になる（短縮形では効かない）`() {
        // 実測: jdbc:sql:config: は "CORE The Username is missing." で失敗し、
        // jdbc:salesforce:config: は "'server' is not a valid connection property." になる。
        val url = ConnectionPropertyProbe.configUrl("cdata.jdbc.sql.SQLDriver")

        assertTrue(url.startsWith("jdbc:cdata:"), "実際: $url")
        assertFalse(url.startsWith("jdbc:sql:"), "短縮形を生成してはいけない: $url")
    }

    @Test
    fun `configUrl - 接続値を一切含まない`() {
        // config 接続文字列は有効な接続を必要としない。資格情報もダミー値も渡さない。
        val url = ConnectionPropertyProbe.configUrl("cdata.jdbc.sql.SQLDriver")

        assertEquals("jdbc:cdata:sql:config:", url)
        assertFalse(url.contains("="), "プロパティを含めてはいけない: $url")
    }

    @Test
    fun `configUrl - 非 CData ドライバーは拒否する`() {
        // 呼び出し側は例外を捕まえて getPropertyInfo のフォールバックに落とす。
        assertThrows<IllegalArgumentException> {
            ConnectionPropertyProbe.configUrl("org.postgresql.Driver")
        }
    }
}
