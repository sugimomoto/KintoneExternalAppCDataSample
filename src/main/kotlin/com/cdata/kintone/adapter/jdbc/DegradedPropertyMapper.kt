package com.cdata.kintone.adapter.jdbc

/**
 * `Driver.getPropertyInfo` の結果から [ConnectionProperty] を組む（縮退マッピング）。
 *
 * `sys_connection_props` が取得できないドライバでも、空フォームではなく
 * 入力欄を出すためのフォールバック。`Category` / `Hierarchy` / 型情報は
 * `getPropertyInfo` が返さないため復元できない。
 *
 * 関連: [Issue #15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15)
 */
object DegradedPropertyMapper {

    fun toConnectionProperties(driverProperties: List<DriverProperty>): List<ConnectionProperty> = TODO()
}
