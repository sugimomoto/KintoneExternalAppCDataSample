package com.cdata.kintone.adapter.config

/** 要求された設定（連携・共有 JDBC 設定）が設定ストアに存在しない。 */
class ConfigFileMissingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** 設定は存在するが、内容が壊れている・参照先が解決できないなどで読み込めない。 */
class ConfigParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
