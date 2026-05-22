package com.cdata.kintone.adapter

import com.cdata.kintone.adapter.cli.InitTableCommand
import com.cdata.kintone.adapter.cli.ListActiveCommand
import com.cdata.kintone.adapter.cli.ListTablesCommand
import com.cdata.kintone.adapter.cli.MigrateConfigCommand
import com.cdata.kintone.adapter.cli.ServeAllCommand
import com.cdata.kintone.adapter.cli.ServeCommand
import com.cdata.kintone.adapter.cli.TestConnectionCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * エントリポイント。サブコマンドをディスパッチする。
 *
 * 使い方:
 * - `adapter serve [--table NAME]` : 個別テーブルの gRPC サーバを起動
 * - `adapter serve-all` : config/tables 配下の全テーブルを一括起動
 * - `adapter list-active` : 稼働中の Adapter 一覧を表示
 * - `adapter init-table` : 対話式 table.yaml 生成
 * - `adapter list-tables` : 接続先データソースのテーブル一覧表示
 * - `adapter test-connection` : JDBC 接続テスト
 * - `adapter migrate-config` : フェーズ1 設定をフェーズ2-A 構成に移行
 */
class Application : NoOpCliktCommand(name = "adapter")

fun main(args: Array<String>) {
    Application()
        .subcommands(
            ServeCommand(),
            ServeAllCommand(),
            ListActiveCommand(),
            InitTableCommand(),
            ListTablesCommand(),
            TestConnectionCommand(),
            MigrateConfigCommand(),
        )
        .main(args)
}
