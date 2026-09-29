package com.cdata.kintone.adapter.config

/**
 * 連携がどのデータソース接続を参照しているかを引く。
 *
 * `tables.jdbc_ref` には外部キー制約が無く、参照中の接続を削除すると
 * その連携の [SqliteConfigSource.loadTableSet] が `ConfigParseException` で失敗して
 * 起動できなくなる。削除前の参照チェックをアプリ層で行うためのもの。
 *
 * DB に触らずテストできるよう純粋な関数にしている。
 *
 * 関連: [Issue #36](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/36)
 */
object JdbcReferenceIndex {

    /**
     * [connectionName] を参照している連携名を返す。空なら削除してよい。
     *
     * [refsByTable] は連携名 → 参照先データソース名。inline JDBC の連携は `null`。
     *
     * 突き合わせは**大文字小文字を無視**する。接続名は利用者が入力する識別子で、
     * 厳密一致にすると表記違いの参照を見落として削除を許してしまう。
     * 戻り値はソートして返し、画面表示の順序を安定させる。
     */
    fun tablesReferencing(refsByTable: Map<String, String?>, connectionName: String): List<String> =
        refsByTable
            .filter { (_, ref) -> ref != null && ref.equals(connectionName, ignoreCase = true) }
            .keys
            .sorted()
}
