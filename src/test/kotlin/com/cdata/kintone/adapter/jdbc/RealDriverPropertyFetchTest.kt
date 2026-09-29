package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * `lib/` に置かれた実ドライバーに対して接続プロパティ取得を通しで確認する。
 *
 * 実 JAR とライセンスファイルが必要なため、既定では実行しない。
 * `-DrealDrivers=true` を付けたときだけ動く（`scripts/check-connection-props.sh`）。
 *
 * ドライバーのバージョンによって件数は変わるので、取得できたこと自体を検証し、
 * 件数は出力して目視比較する（期待値は
 * `.steering/20260929-connection-props-fetch-fallback/design.md §8.4`）。
 */
class RealDriverPropertyFetchTest {

    private val libDir: Path = Path.of("./lib")

    @Test
    fun `lib 配下の全ドライバーから sys_connection_props を取得できる`() {
        assumeTrue(System.getProperty("realDrivers") == "true", "実ドライバー検証は -DrealDrivers=true のときだけ実行する")
        assumeTrue(Files.isDirectory(libDir), "lib ディレクトリが無い")

        val driverManager = JdbcDriverManager(libDir)
        val drivers = driverManager.listDrivers().filter { it.driverClass != null }
        assumeTrue(drivers.isNotEmpty(), "lib 配下にドライバー JAR が無い")

        val inspector = JdbcConnectionPropertyInspector(libDir)
        val failures = mutableListOf<String>()

        drivers.forEach { driver ->
            val result = inspector.fetchProperties(driver.driverClass!!, driver.filename)
            println("${driver.filename}: source=${result.source} count=${result.properties.size}")
            if (result.source != PropertySource.SYS_CONNECTION_PROPS) {
                failures += "${driver.filename} -> ${result.source}"
            }
        }

        assertTrue(failures.isEmpty(), "完全取得できなかったドライバー: $failures")
    }
}
