package com.cdata.kintone.adapter

import com.cdata.kintone.adapter.cli.ListActiveCommand
import com.cdata.kintone.adapter.cli.ListTablesCommand
import com.cdata.kintone.adapter.cli.ServeAllCommand
import com.cdata.kintone.adapter.cli.ServeCommand
import com.cdata.kintone.adapter.cli.TestConnectionCommand
import com.cdata.kintone.adapter.cli.WebUiCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * エントリポイント。サブコマンドをディスパッチする。
 *
 * 設定は `<config-dir>/config.db` (SQLite) に保存される。
 * 連携の作成・編集は `adapter web-ui` から行う。
 *
 * 使い方:
 * - `adapter web-ui` : ブラウザ管理コンソールを起動
 * - `adapter serve --table <name>` : 指定連携の gRPC サーバを起動
 * - `adapter serve-all` : 登録済みの全連携を一括起動
 * - `adapter list-active` : 稼働中の Adapter 一覧を表示
 * - `adapter list-tables` : 接続先データソースのテーブル一覧表示
 * - `adapter test-connection` : JDBC 接続テスト
 */
class Application : NoOpCliktCommand(name = "adapter")

fun main(args: Array<String>) {
    Application()
        .subcommands(
            WebUiCommand(),
            ServeCommand(),
            ServeAllCommand(),
            ListActiveCommand(),
            ListTablesCommand(),
            TestConnectionCommand(),
        ).main(args)
}
