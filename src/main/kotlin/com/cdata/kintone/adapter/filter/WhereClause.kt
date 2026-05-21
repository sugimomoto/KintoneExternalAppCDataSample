package com.cdata.kintone.adapter.filter

/**
 * SQL WHERE 句と PreparedStatement にバインドするパラメータをセットで保持する。
 *
 * @property sql `?` プレースホルダ含む WHERE 句（先頭 `WHERE` は含まない）
 * @property params `?` の順番に対応するバインド値リスト（null 可）
 */
data class WhereClause(
    val sql: String,
    val params: List<Any?>,
) {
    companion object {
        /** 「全件」「条件なし」を表す。SQL の WHERE 句として常に真を返す。 */
        val ALL_RECORDS = WhereClause("1=1", emptyList())
    }
}
