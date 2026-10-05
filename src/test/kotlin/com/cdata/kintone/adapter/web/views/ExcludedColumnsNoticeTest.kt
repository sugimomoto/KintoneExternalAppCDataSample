package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.metadata.ColumnInfo
import com.cdata.kintone.adapter.metadata.ExcludedColumn
import com.cdata.kintone.adapter.metadata.ExclusionReason
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

/**
 * 除外した自動生成列の表示 [excludedColumnsNotice] のテスト。
 *
 * 黙って候補から消すと「列が足りない」と誤解される。何を外したのか、なぜ外したのかを
 * 必ず示す。SaaS 系ドライバーが想定外に既定値を申告した場合も、この表で気づける。
 *
 * 関連: [Issue #75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75)
 */
class ExcludedColumnsNoticeTest {

    private fun excluded(
        name: String,
        reason: ExclusionReason,
        defaultValue: String? = null,
        typeName: String = "uniqueidentifier",
    ) = ExcludedColumn(
        column = ColumnInfo(
            name = name,
            jdbcType = Types.VARCHAR,
            typeName = typeName,
            nullable = false,
            defaultValue = defaultValue,
        ),
        reason = reason,
    )

    private fun render(columns: List<ExcludedColumn>): String =
        createHTML().div { excludedColumnsNotice(columns) }

    @Test
    fun `除外した列名を表示する`() {
        val html = render(listOf(excluded("rowguid", ExclusionReason.DEFAULT_VALUE, "(newid())")))

        assertTrue(html.contains("rowguid"), "列名がない: $html")
    }

    @Test
    fun `除外の理由を表示する`() {
        val html = render(listOf(excluded("CustomerID", ExclusionReason.AUTO_INCREMENT, typeName = "int identity")))

        assertTrue(html.contains(ExclusionReason.AUTO_INCREMENT.label), "理由がない: $html")
    }

    @Test
    fun `既定値の式を表示する`() {
        // 何が入るのか分からないと、省いて良い列なのか判断できない。
        val html = render(listOf(excluded("ModifiedDate", ExclusionReason.DEFAULT_VALUE, "(getdate())")))

        assertTrue(html.contains("getdate()"), "既定値の式がない: $html")
    }

    @Test
    fun `列の型を表示する`() {
        val html = render(listOf(excluded("rowguid", ExclusionReason.DEFAULT_VALUE, "(newid())")))

        assertTrue(html.contains("uniqueidentifier"), "型がない: $html")
    }

    @Test
    fun `空のまま登録すると失敗することを説明する`() {
        val html = render(listOf(excluded("rowguid", ExclusionReason.DEFAULT_VALUE, "(newid())")))

        assertTrue(html.contains("失敗"), "失敗する旨の説明がない: $html")
    }

    @Test
    fun `計算列の理由も表示する`() {
        val html = render(listOf(excluded("Total", ExclusionReason.GENERATED, typeName = "int")))

        assertTrue(html.contains(ExclusionReason.GENERATED.label), "理由がない: $html")
    }

    @Test
    fun `複数の列をすべて表示する`() {
        val html = render(
            listOf(
                excluded("CustomerID", ExclusionReason.AUTO_INCREMENT),
                excluded("rowguid", ExclusionReason.DEFAULT_VALUE, "(newid())"),
                excluded("ModifiedDate", ExclusionReason.DEFAULT_VALUE, "(getdate())"),
            ),
        )

        assertTrue(html.contains("CustomerID"), "実際: $html")
        assertTrue(html.contains("rowguid"), "実際: $html")
        assertTrue(html.contains("ModifiedDate"), "実際: $html")
    }

    @Test
    fun `除外列が無ければ何も描画しない`() {
        val html = render(emptyList())

        assertFalse(html.contains("自動"), "除外が無いのに案内が出ている: $html")
    }
}
