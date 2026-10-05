package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.metadata.ColumnInfo
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

/**
 * レコード ID 列の選択 UI [recordIdSelection] の描画テスト。
 *
 * 主キー制約の無いテーブル・ビューでも、一意な列があれば連携できる。kintone の
 * `RecordIdFieldDefinition` は列が DB 上で主キーかを問わないため。
 *
 * **一意性は利用者の責任**になるので、その旨を必ず画面に出す。重複がある列を選ぶと
 * 更新・削除が意図しない行に及ぶ。
 *
 * 関連: [Issue #66](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/66)
 */
class RecordIdSelectionTest {

    private fun column(name: String, type: Int = Types.VARCHAR) =
        ColumnInfo(name = name, jdbcType = type, typeName = "x", nullable = false)

    private fun render(candidates: List<ColumnInfo>): String =
        createHTML().div { recordIdSelection(candidates) }

    @Test
    fun `候補があれば選択 UI を描画する`() {
        val html = render(listOf(column("ProductCategoryID", Types.INTEGER)))

        assertTrue(html.contains("""name="recordIdColumn""""), "選択欄がない: $html")
    }

    @Test
    fun `候補の列名が選択肢に出る`() {
        val html = render(listOf(column("Id"), column("Code")))

        assertTrue(html.contains("Id"), "実際: $html")
        assertTrue(html.contains("Code"), "実際: $html")
    }

    @Test
    fun `一意性が利用者の責任である旨を表示する`() {
        val html = render(listOf(column("Id")))

        assertTrue(html.contains("一意"), "一意性への言及がない: $html")
        assertTrue(html.contains("責任"), "責任の所在が示されていない: $html")
    }

    @Test
    fun `重複した場合のリスクを表示する`() {
        // 「一意にしてください」だけでは、守らなかった場合にどうなるか分からない。
        val html = render(listOf(column("Id")))

        assertTrue(html.contains("更新") || html.contains("削除"), "リスクの説明がない: $html")
    }

    @Test
    fun `候補が空なら何も描画しない`() {
        val html = render(emptyList())

        assertFalse(html.contains("recordIdColumn"), "候補が無いのに選択欄がある: $html")
    }
}
