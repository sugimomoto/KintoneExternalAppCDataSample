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

    fun toConnectionProperties(driverProperties: List<DriverProperty>): List<ConnectionProperty> =
        driverProperties.mapIndexed { index, property ->
            val name = property.name.trim()
            ConnectionProperty(
                propertyName = name,
                displayName = name,
                shortDescription = property.description,
                // getPropertyInfo は型を返さない。choices も実測では常に空のため真偽値も判別できない。
                type = PropertyType.STRING,
                // DriverPropertyInfo.value にはプローブで渡したダミー値が入り得るので採らない。
                defaultValue = null,
                allowedValues = property.allowedValues,
                category = "",
                required = property.required,
                // 判定ルールを二重に持たないようマスク処理と同じ基準を使う。
                sensitivity = if (ConnectionStringMasker.isSensitive(name)) Sensitivity.PASSWORD else Sensitivity.NONE,
                visible = true,
                hierarchy = "",
                ordinal = index,
                categoryOrdinal = 0,
            )
        }
}
