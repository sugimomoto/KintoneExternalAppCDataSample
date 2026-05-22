package com.cdata.kintone.adapter.jdbc

import java.sql.Connection

/**
 * `AdapterServiceImpl` / `TableAdapterServer` が依存する接続プロバイダ抽象。
 * 本番は [JdbcConnectionProvider]、テストは Fake 実装で差し替える。
 */
interface ConnectionProvider : AutoCloseable {
    fun connection(): Connection
}
