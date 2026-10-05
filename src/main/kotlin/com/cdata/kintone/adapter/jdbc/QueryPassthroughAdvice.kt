package com.cdata.kintone.adapter.jdbc

/**
 * `QueryPassthrough` が既定で有効なコネクタに出す注意書き。
 *
 * SQL Server ドライバーは既定で `QueryPassthrough=true`、つまりクエリをそのまま
 * サーバーへ渡す。すると T-SQL の文法が適用され、[QueryBuilder] が生成する
 * `LIMIT ? OFFSET ?` が通らない。
 *
 * ```
 * DATA_SOURCE The SQL Error Number is 102, Severity is 15,
 * Error is 'Incorrect syntax near 'LIMIT'.'
 * ```
 *
 * **接続テストもウィザードも成功する。** kintone からレコードを読んだ時点で初めて
 * 失敗するため、原因から遠い場所でエラーに遭遇する。`QueryPassthrough` は 100 近い
 * プロパティのうちの 1 つで、カテゴリー分類の中に埋もれている。
 *
 * **値は自動で書き換えない。** 設定値の変更は利用者の運用に委ねる (Issue #69)。
 */
object QueryPassthroughAdvice {

    /** 接続プロパティ名。`sys_connection_props` の `PropertyName` 列の値。 */
    const val PROPERTY = "QueryPassthrough"

    /**
     * 注意書きの本文。
     *
     * 設定方法だけでなく**放置した結果**まで書く。「設定してください」だけでは
     * 重要度が伝わらず、接続テストが通るため後回しにされる。
     */
    val MESSAGE = "このデータソースは $PROPERTY が既定で有効です。" +
        "クエリがそのままデータソースへ渡されるため、レコードの参照時に " +
        "\"Incorrect syntax near 'LIMIT'\" のようなエラーになります。" +
        "接続テストやウィザードは成功するため、kintone からレコードを読んだ時点で" +
        "初めて失敗します。下のプロパティで $PROPERTY を False に設定してください。"

    /**
     * 注意書きが必要か。
     *
     * 既定値が `true` で、かつ接続文字列に明示設定が無い場合だけ true。
     * 明示設定済みなら出さない。対処済みの警告を出し続けると他の警告を見落とすうえ、
     * 意図して `True` を選んだ場合にも不要。
     *
     * 判定は `propertyName` で行う。`Name` 列は表示名（`Query Passthrough`、
     * スペース入り）で一致しない。
     */
    fun isNeeded(properties: List<ConnectionProperty>, jdbcUrl: String): Boolean {
        val defaultsToTrue = properties
            .firstOrNull { it.propertyName.equals(PROPERTY, ignoreCase = true) }
            ?.defaultValue
            ?.equals("true", ignoreCase = true) == true
        if (!defaultsToTrue) return false
        return JdbcUrlEnhancer.propertyOf(jdbcUrl, PROPERTY) == null
    }
}
