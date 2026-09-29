package com.cdata.kintone.adapter.jdbc

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DatabaseMetaData

/**
 * 接続テストの検証 [ConnectionValidator] のテスト。
 *
 * CData ドライバーは認証を遅延させるため、`getConnection()` も `getMetaData()` も
 * 誤った資格情報で通ってしまう。`isValid` の**戻り値**を見ないと検証にならない。
 *
 * 関連: [Issue #45](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/45)
 */
class ConnectionValidatorTest {

    private fun connectionWith(valid: Boolean): Connection {
        val meta = mockk<DatabaseMetaData> {
            every { databaseProductName } returns "Salesforce"
            every { databaseProductVersion } returns "25.0.9540"
            every { driverName } returns "CData JDBC Driver for Salesforce 2025J"
            every { driverVersion } returns "25.0.9540.0"
        }
        return mockk {
            every { isValid(any()) } returns valid
            every { metaData } returns meta
        }
    }

    @Test
    fun `validate - isValid が true なら Valid になる`() {
        val result = ConnectionValidator.validate(connectionWith(valid = true))

        val valid = assertInstanceOf(ConnectionValidation.Valid::class.java, result)
        assertTrue(valid.description.contains("Salesforce"), "実際: ${valid.description}")
        assertTrue(valid.description.contains("25.0.9540"), "実際: ${valid.description}")
        assertTrue(valid.description.contains("CData JDBC Driver"), "実際: ${valid.description}")
    }

    @Test
    fun `validate - isValid が false なら例外が出ていなくても Invalid になる`() {
        // これが本件の core。isValid は失敗時に例外を投げず false を返すだけなので、
        // 例外の有無だけを見ると誤った資格情報を「成功」と誤判定する。
        val result = ConnectionValidator.validate(connectionWith(valid = false))

        assertInstanceOf(ConnectionValidation.Invalid::class.java, result)
    }

    @Test
    fun `validate - Invalid の理由に確認すべきものが示される`() {
        val result = ConnectionValidator.validate(connectionWith(valid = false))

        val invalid = assertInstanceOf(ConnectionValidation.Invalid::class.java, result)
        // ドライバーは理由を返さない (getWarnings() は null) ため、
        // 何を確認すべきかだけを伝える。
        assertTrue(invalid.reason.contains("資格情報"), "実際: ${invalid.reason}")
    }

    @Test
    fun `validate - isValid にタイムアウトを渡す`() {
        val connection = connectionWith(valid = true)

        ConnectionValidator.validate(connection)

        verify { connection.isValid(ConnectionValidator.TIMEOUT_SECONDS) }
    }

    @Test
    fun `validate - isValid が false のときは metaData を引かない`() {
        // 使えない接続に対して無駄な往復をしない。
        val connection = connectionWith(valid = false)

        ConnectionValidator.validate(connection)

        verify(exactly = 0) { connection.metaData }
    }
}
