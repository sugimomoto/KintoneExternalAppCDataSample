package com.cdata.kintone.adapter.jdbc

import java.sql.DriverPropertyInfo

/**
 * JDBC 標準 `Driver.getPropertyInfo` が返すプロパティ定義。
 *
 * [DriverPropertyInfo] を直接持ち回さないのは以下の理由。
 * - `name` に末尾空白が混じるドライバがあり（Salesforce の `"User "` 等）、境界で正規化したい
 * - 可変クラスのためテストデータの組み立てが読みにくい
 *
 * `getPropertyInfo` は接続を張らずに呼べるため、`sys_connection_props` が
 * 取得できないドライバでも必須プロパティ名だけは分かる。
 */
data class DriverProperty(
    val name: String,
    val description: String,
    val required: Boolean,
    val allowedValues: List<String>,
) {
    companion object {
        fun from(info: DriverPropertyInfo): DriverProperty = DriverProperty(
            name = info.name.trim(),
            description = info.description ?: "",
            required = info.required,
            allowedValues = info.choices?.toList() ?: emptyList(),
        )
    }
}
