package com.cdata.kintone.adapter.metadata

/** 自動生成列としてマッピング対象から外す理由。 */
enum class ExclusionReason(val label: String) {
    /** 自動採番列（`IS_AUTOINCREMENT`）。 */
    AUTO_INCREMENT("自動採番列"),

    /** 計算列（`IS_GENERATEDCOLUMN`）。DB が式から求めるため書き込めない。 */
    GENERATED("計算列"),

    /** 既定値を持つ列（`COLUMN_DEF`）。省けば DB 側が埋める。 */
    DEFAULT_VALUE("既定値あり"),
}

/** 除外した列と、その理由。 */
data class ExcludedColumn(val column: ColumnInfo, val reason: ExclusionReason)

/** 選択できる列と、除外した列。 */
data class ColumnPartition(
    val selectable: List<ColumnInfo>,
    val excluded: List<ExcludedColumn>,
)

/**
 * DB 側で値が決まる列を判定する。
 *
 * この種の列を kintone の入力項目として出すと、**空のまま登録されて必ず失敗する**。
 * テキストなら空文字が届いて `Conversion failed when converting ... to uniqueidentifier`、
 * 数値・日時なら presence が無く NULL が束縛されて `NOT NULL` 制約違反になる。
 * 列自体を送らなければ既定値が効くため、マッピング対象から外す。
 *
 * 判定は JDBC の `DatabaseMetaData.getColumns` が返す情報だけで行い、
 * ドライバーごとの分岐は持たない。
 *
 * 関連: [Issue #75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75)
 */
object GeneratedColumns {

    /**
     * 自動生成列なら除外の理由を返す。通常の列なら null。
     *
     * 既定値を持つだけの列（`((0))` のようなリテラル既定値）も除外する。
     * `NOT NULL` かつ既定値の列は kintone から空で送られると失敗するためで、
     * 関数呼び出しかリテラルかを文字列解析で区別する判定はドライバー依存で脆い割に、
     * 得られるのは「リテラル既定値の列を kintone から設定できる」という小さな利益しかない。
     *
     * 自動採番列は既定値も持ち得るため、より具体的な理由を先に見る。
     */
    fun reasonFor(column: ColumnInfo): ExclusionReason? = when {
        column.autoIncrement -> ExclusionReason.AUTO_INCREMENT
        column.generated -> ExclusionReason.GENERATED
        // 既定値が無いことを空文字で表すドライバーがあるため、空白だけの値は無しとみなす。
        !column.defaultValue.isNullOrBlank() -> ExclusionReason.DEFAULT_VALUE
        else -> null
    }

    /** 選択候補と除外列に分ける。入力の順序は保つ。 */
    fun partition(columns: List<ColumnInfo>): ColumnPartition {
        val selectable = mutableListOf<ColumnInfo>()
        val excluded = mutableListOf<ExcludedColumn>()
        columns.forEach { column ->
            val reason = reasonFor(column)
            if (reason == null) selectable += column else excluded += ExcludedColumn(column, reason)
        }
        return ColumnPartition(selectable = selectable, excluded = excluded)
    }
}
