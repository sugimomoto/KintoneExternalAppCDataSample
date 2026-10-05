package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.jdbc.ConnectionPropertiesResult
import com.cdata.kintone.adapter.jdbc.ConnectionProperty
import com.cdata.kintone.adapter.jdbc.PropertySource
import com.cdata.kintone.adapter.jdbc.PropertyType
import com.cdata.kintone.adapter.jdbc.Sensitivity
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 接続プロパティフォーム [propertiesFormContent] の描画テスト。
 *
 * 手動 URL 欄を既存の接続文字列で事前入力すると、ブラウザがその値をそのまま送り返し、
 * `ConnectionFormUrl.build` が無条件に優先するため**プロパティ側の編集が黙って
 * 捨てられる**。事前入力してよいのは、プロパティフォームが併記されない場合だけ。
 *
 * #60 で config 接続文字列に切り替えて縮退がほぼ起きなくなったため、この分岐は
 * 実行時に再現しにくい。描画を直接検証する。
 *
 * 関連: [Issue #59](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/59)
 */
class PropertiesFormContentTest {

    private val existingUrl = "jdbc:sql:AuthScheme=Password;Server=sqlserver-sample;Password=secret123;"
    private val existingValues = mapOf("__url__" to existingUrl, "Server" to "sqlserver-sample")

    private fun property(name: String) = ConnectionProperty(
        propertyName = name,
        displayName = name,
        shortDescription = "",
        type = PropertyType.STRING,
        defaultValue = null,
        allowedValues = emptyList(),
        category = "Authentication",
        required = true,
        sensitivity = Sensitivity.NONE,
        visible = true,
        hierarchy = "",
        ordinal = 0,
        categoryOrdinal = 0,
    )

    private fun render(result: ConnectionPropertiesResult): String =
        createHTML().div { propertiesFormContent(result, existingValues) }

    private fun manualUrlValue(html: String): String? =
        Regex("""<textarea[^>]*name="jdbc\.url\.manual"[^>]*>(.*?)</textarea>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.groupValues
            ?.get(1)

    // --- 縮退フォーム（プロパティ欄と手動 URL 欄が併記される） ---

    private val degraded = ConnectionPropertiesResult(
        listOf(property("Server")),
        PropertySource.DRIVER_PROPERTY_INFO,
    )

    @Test
    fun `併記時は手動 URL 欄を事前入力しない`() {
        val html = render(degraded)

        val value = manualUrlValue(html)
        assertTrue(value != null, "手動 URL 欄が描画されていない")
        assertTrue(value!!.isBlank(), "事前入力されている: ${value.trim()}")
    }

    @Test
    fun `併記時は空欄の意味を案内する`() {
        val html = render(degraded)

        assertTrue(
            html.contains("空欄のままなら上のプロパティから組み立てます"),
            "案内文が出ていない",
        )
    }

    @Test
    fun `併記時は現在の値をマスクして参照できる`() {
        val html = render(degraded)

        assertTrue(html.contains("現在の値"), "現在の値の表示が無い")
        assertTrue(html.contains("Server=sqlserver-sample"), "参照用の値が出ていない")
        assertFalse(html.contains("secret123"), "資格情報が平文で出ている")
    }

    // --- プロパティフォームが作れない（手動入力が唯一の編集手段） ---

    @Test
    fun `プロパティが無いときは従来どおり事前入力する`() {
        val html = render(ConnectionPropertiesResult(emptyList(), PropertySource.NONE_NOT_CDATA_DRIVER))

        assertTrue(
            manualUrlValue(html)?.contains("Server=sqlserver-sample") == true,
            "事前入力されていない。手で打ち直すことになる",
        )
    }

    // --- 完全取得（手動 URL 欄は出ない） ---

    @Test
    fun `完全取得のときは手動 URL 欄を描画しない`() {
        val html = render(
            ConnectionPropertiesResult(listOf(property("Server")), PropertySource.SYS_CONNECTION_PROPS),
        )

        assertTrue(manualUrlValue(html) == null, "手動 URL 欄が描画されている")
    }
}
