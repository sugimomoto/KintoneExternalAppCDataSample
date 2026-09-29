package com.cdata.kintone.adapter.jdbc

/**
 * 現在の入力値をもとに、接続プロパティの表示対象と必須を解決する。
 *
 * `sys_connection_props` の `Required` は静的で、認証方式を変えても変わらない。
 * 認証方式ごとの条件は `Hierarchy` 列にあるため、こちらを評価して
 * 「いま必要なプロパティ」だけを残す。
 *
 * 関連: [Issue #14](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/14)
 */
object PropertyHierarchyResolver {

    /**
     * [values] のもとで画面に出すプロパティを返す。
     *
     * 条件を満たさないプロパティは除外するが、**値を持つものは残す**。
     * 編集時に保存済みの値が画面から消え、保存で黙って捨てられるのを防ぐため。
     * 残す場合の `required` は false にする（その状態では必須ではないため）。
     */
    fun resolve(properties: List<ConnectionProperty>, values: Map<String, String>): List<ConnectionProperty> = TODO()

    /** 他プロパティの条件から依存先として参照されている名前。変更時の再描画トリガーに使う。 */
    fun dependencyNames(properties: List<ConnectionProperty>): Set<String> = TODO()
}
