package com.cdata.kintone.adapter

import com.cdata.kintone.adapter.cli.InitTableCommand
import com.cdata.kintone.adapter.cli.ListTablesCommand
import com.cdata.kintone.adapter.cli.ServeCommand
import com.cdata.kintone.adapter.cli.TestConnectionCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * エントリポイント。サブコマンドをディスパッチする。
 *
 * 使い方:
 * - `adapter serve` : gRPC サーバを起動（kintone Agent から接続される）
 * - `adapter init-table` : 対話式 table.yaml 生成
 * - `adapter list-tables` : 接続先のテーブル一覧表示
 * - `adapter test-connection` : JDBC 接続テスト
 */
class Application : NoOpCliktCommand(name = "adapter")

fun main(args: Array<String>) {
    Application()
        .subcommands(
            ServeCommand(),
            InitTableCommand(),
            ListTablesCommand(),
            TestConnectionCommand(),
        )
        .main(args)
}
