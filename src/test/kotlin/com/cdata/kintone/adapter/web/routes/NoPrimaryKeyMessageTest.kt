package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 主キーが無い場合の案内 [noPrimaryKeyMessage] のテスト。
 *
 * 「主キーが見つかりません」だけでは、DB を直せばよいのか別のテーブルを選ぶのかが
 * 分からない。kintone 側の仕様に起因し再試行しても直らないことを伝える必要がある。
 *
 * 関連: [Issue #64](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/64)
 */
class NoPrimaryKeyMessageTest {

    @Test
    fun `どのテーブルで起きたかを含む`() {
        val message = noPrimaryKeyMessage("vGetAllCategories")

        assertTrue(message.contains("vGetAllCategories"), "実際: $message")
    }

    @Test
    fun `レコード番号が必須である旨を含む`() {
        // kintone は GetCapability で RecordIdType、GetSchema で
        // RecordIdFieldDefinition を要求する。どちらも必須メソッド。
        val message = noPrimaryKeyMessage("v")

        assertTrue(message.contains("レコード番号"), "実際: $message")
    }

    @Test
    fun `読み取り専用でも連携できない旨を含む`() {
        // Select だけなら ID は不要だが、その前段の GetSchema で成立しない。
        val message = noPrimaryKeyMessage("v")

        assertTrue(message.contains("読み取り専用"), "実際: $message")
    }

    @Test
    fun `次に取るべき行動を含む`() {
        val message = noPrimaryKeyMessage("v")

        assertTrue(message.contains("選び直して"), "実際: $message")
    }

    @Test
    fun `取得失敗の理由はマスクされる`() {
        // Failed に畳み込むときは必ず ConnectionStringMasker を通す。
        val raw = "Failed to connect: jdbc:sql:User=sa;Password=secret123;"

        val masked = ConnectionStringMasker.mask(raw)

        assertFalse(masked.contains("secret123"), "実際: $masked")
        assertTrue(masked.contains("User=sa"), "機密でない値は残る: $masked")
    }
}
