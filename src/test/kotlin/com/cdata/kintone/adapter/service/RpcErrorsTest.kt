package com.cdata.kintone.adapter.service

import io.grpc.Status
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * RPC の例外変換 [RpcErrors] のテスト。
 *
 * `select` 以外の 8 メソッドは例外を捕まえておらず、生の例外が grpc-kotlin に渡って
 * **`code: Unknown` かつメッセージ空**になっていた。ドライバーが返していた有用な
 * メッセージが捨てられ、ログにも何も出なかった。
 *
 * 関連: [Issue #70](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/70)
 */
class RpcErrorsTest {

    @Test
    fun `descriptionOf - 例外メッセージをそのまま使う`() {
        val cause = SQLException(
            "DATA_SOURCE The SQL Error Number is 241, Error is 'Conversion failed'.",
        )

        assertTrue(
            RpcErrors.descriptionOf(cause).contains("Conversion failed"),
            "実際: ${RpcErrors.descriptionOf(cause)}",
        )
    }

    @Test
    fun `descriptionOf - 接続文字列を含むメッセージをマスクする`() {
        // kintone の画面は顧客も見る。JDBC の例外に接続文字列が混ざることがある。
        val cause = SQLException("Failed: jdbc:sql:Server=x;User=sa;Password=secret123;")

        val description = RpcErrors.descriptionOf(cause)

        assertFalse(description.contains("secret123"), "実際: $description")
        assertTrue(description.contains("User=sa"), "機密でない値は残る: $description")
    }

    @Test
    fun `descriptionOf - メッセージが null なら例外クラス名を使う`() {
        // NoWhenBranchMatchedException 等は message が null。空のままでは
        // code Unknown と区別がつかない。
        val description = RpcErrors.descriptionOf(IllegalStateException())

        assertEquals("IllegalStateException", description)
    }

    @Test
    fun `descriptionOf - メッセージが空文字なら例外クラス名を使う`() {
        assertEquals("SerializationException", RpcErrors.descriptionOf(SerializationException("")))
    }

    @Test
    fun `descriptionOf - メッセージが空白のみでも空にならない`() {
        assertFalse(RpcErrors.descriptionOf(IllegalStateException("   ")).isBlank())
    }

    @Test
    fun `internal - INTERNAL ステータスになる`() {
        val status = Status.fromThrowable(RpcErrors.internal(SQLException("boom")))

        assertEquals(Status.Code.INTERNAL, status?.code)
    }

    @Test
    fun `internal - 説明文が付く`() {
        val status = Status.fromThrowable(RpcErrors.internal(SQLException("boom")))

        assertEquals("boom", status?.description)
    }

    @Test
    fun `internal - 原因の例外を保持する`() {
        // ログと調査のために原因を失わない。
        val cause = SQLException("boom")

        assertSame(cause, Status.fromThrowable(RpcErrors.internal(cause))?.cause)
    }
}
