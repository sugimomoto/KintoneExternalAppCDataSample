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
    fun resolve(properties: List<ConnectionProperty>, values: Map<String, String>): List<ConnectionProperty> {
        val evaluation = Evaluation(properties, values)
        return properties.mapNotNull { property ->
            when {
                evaluation.isSatisfied(property) -> property
                evaluation.hasValue(property) -> property.copy(required = false)
                else -> null
            }
        }
    }

    /** 他プロパティの条件から依存先として参照されている名前。変更時の再描画トリガーに使う。 */
    fun dependencyNames(properties: List<ConnectionProperty>): Set<String> =
        properties.mapNotNull { PropertyHierarchy.parse(it.hierarchy)?.dependsOn }.toSet()

    /**
     * 1 回の解決で使う評価器。
     *
     * プロパティ名の照合は大文字小文字を無視する。フォームの `name` (`prop.<PropertyName>`) と
     * `Hierarchy` 側の表記が揺れても引き当てられるようにするため。
     *
     * 判定結果をメモ化していないのは、循環ガードの状態によって同じプロパティの結果が
     * 変わり得るため。条件を持つプロパティは実測で最大 37 件・連鎖は 2 段なので、
     * 素直に辿っても十分に速い。
     */
    private class Evaluation(
        properties: List<ConnectionProperty>,
        values: Map<String, String>,
    ) {
        private val byName = properties.associateBy { it.propertyName.lowercase() }
        private val values = values.mapKeys { (key, _) -> key.lowercase() }

        fun hasValue(property: ConnectionProperty): Boolean =
            !values[property.propertyName.lowercase()].isNullOrBlank()

        fun isSatisfied(property: ConnectionProperty): Boolean = isSatisfied(property, emptySet())

        private fun isSatisfied(property: ConnectionProperty, visiting: Set<String>): Boolean {
            val key = property.propertyName.lowercase()
            val hierarchy = PropertyHierarchy.parse(property.hierarchy)
            val dependency = hierarchy?.let { byName[it.dependsOn.lowercase()] }

            // 条件なし / 依存先が一覧に無い / 循環参照は「条件を満たす」に倒す。
            // 判定できないものを非表示にすると、ドライバー更新で画面が空になり得る。
            if (hierarchy == null || dependency == null || key in visiting) return true

            return hierarchy.accepts(effectiveValue(dependency)) &&
                isSatisfied(dependency, visiting + key)
        }

        /** 依存先の実効値。入力値が優先され、無ければ既定値で判定する。 */
        private fun effectiveValue(property: ConnectionProperty): String =
            values[property.propertyName.lowercase()]?.takeIf { it.isNotBlank() }
                ?: property.defaultValue.orEmpty()
    }
}
