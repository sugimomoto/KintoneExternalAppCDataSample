package com.cdata.kintone.adapter.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * 認可コード入力の解釈 [AuthorizationCodeExtractor] のテスト。
 *
 * リダイレクト先 URL をそのまま貼れるようにする。`code=` は URL の途中にあり
 * 前後に他のパラメータが付くため、手作業での切り出しは手間で、範囲を誤ると
 * 認可コードが 1 回しか使えないためやり直しになる。
 *
 * 関連: [Issue #57](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/57)
 */
class AuthorizationCodeExtractorTest {

    /** 実際に観測された Google のリダイレクト URL（`iss=` が前、`scope=` が後ろ）。 */
    private val googleRedirect =
        "http://localhost:33333/?iss=https://accounts.google.com" +
            "&code=4/0AXlqoi7hDZMDQCLQs-ZalS0Oe94xyiwXwsiF1hLVULlJbnO7cAJdYHJKILGXZ100fUdeeg" +
            "&scope=https://www.googleapis.com/auth/spreadsheets%20https://www.googleapis.com/auth/drive"

    private fun codeOf(input: String): String =
        assertInstanceOf(AuthorizationCodeInput.Code::class.java, AuthorizationCodeExtractor.extract(input)).value

    @Test
    fun `extract - 実際の Google リダイレクト URL から抽出できる`() {
        assertEquals(
            "4/0AXlqoi7hDZMDQCLQs-ZalS0Oe94xyiwXwsiF1hLVULlJbnO7cAJdYHJKILGXZ100fUdeeg",
            codeOf(googleRedirect),
        )
    }

    @Test
    fun `extract - code が末尾にある URL から抽出できる`() {
        assertEquals("abc123", codeOf("http://localhost:33333/?state=xyz&code=abc123"))
    }

    @Test
    fun `extract - 最初のパラメータが code でも抽出できる`() {
        assertEquals("abc123", codeOf("http://localhost:33333/?code=abc123&state=xyz"))
    }

    @Test
    fun `extract - 素の認可コードはそのまま返る`() {
        // 既に手元にコードがある使い方を壊さない (AC-3)。
        assertEquals("aPrxsmIEeqM9PiQroGEWP1UiE", codeOf("aPrxsmIEeqM9PiQroGEWP1UiE"))
    }

    @Test
    fun `extract - パーセントエンコードを解く`() {
        assertEquals("4/0Abc_def", codeOf("http://localhost:33333/?code=4%2F0Abc_def"))
    }

    @Test
    fun `extract - プラス記号を空白に変換しない`() {
        // URLDecoder はフォームエンコードの規則で + を空白にする。
        // 認可コードに + が含まれる場合に壊れるため退避する (AC-5)。
        assertEquals("ab+cd", codeOf("http://localhost:33333/?code=ab+cd"))
    }

    @Test
    fun `extract - 前後の空白と引用符を取り除く`() {
        assertEquals("abc123", codeOf("  \"abc123\"  "))
    }

    @Test
    fun `extract - error を含む URL は Error になる`() {
        val result = AuthorizationCodeExtractor.extract(
            "http://localhost:33333/?error=access_denied&state=xyz",
        )

        val error = assertInstanceOf(AuthorizationCodeInput.Error::class.java, result)
        assertEquals("access_denied", error.value)
    }

    @Test
    fun `extract - code と error が両方あれば code を優先する`() {
        assertEquals("abc123", codeOf("http://localhost:33333/?code=abc123&error=whatever"))
    }

    @Test
    fun `extract - 空文字は Empty`() {
        assertInstanceOf(AuthorizationCodeInput.Empty::class.java, AuthorizationCodeExtractor.extract(""))
    }

    @Test
    fun `extract - 空白のみは Empty`() {
        assertInstanceOf(AuthorizationCodeInput.Empty::class.java, AuthorizationCodeExtractor.extract("   "))
    }

    @Test
    fun `extract - code という文字列を含む素のコードを誤検出しない`() {
        // 区切り (? か &) に続く code= だけを見る。
        assertEquals("mycode=notaquery", codeOf("mycode=notaquery"))
    }

    @Test
    fun `extract - code が空の URL はコードとして扱わない`() {
        val result = AuthorizationCodeExtractor.extract("http://localhost:33333/?code=&state=xyz")

        // 空のコードを渡してプロシージャを叩いても無駄なので、入力全体扱いになる。
        assertInstanceOf(AuthorizationCodeInput.Code::class.java, result)
    }
}
