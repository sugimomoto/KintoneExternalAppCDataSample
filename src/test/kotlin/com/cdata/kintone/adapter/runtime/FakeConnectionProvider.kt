package com.cdata.kintone.adapter.runtime

import com.cdata.kintone.adapter.jdbc.ConnectionProvider
import java.sql.Connection

/**
 * テスト用: JDBC 接続を一切確立しない `ConnectionProvider`。
 * gRPC サーバ起動の単体テストでドライバ JAR が無くても動くようにする。
 */
class FakeConnectionProvider : ConnectionProvider {
    override fun connection(): Connection {
        throw UnsupportedOperationException("FakeConnectionProvider はテスト専用です")
    }

    override fun close() {
        // no-op
    }
}
