package com.cdata.kintone.adapter.jdbc

/**
 * JDBC 接続文字列から認証情報を伏せる。ログ出力・画面表示の両方でこれを通す。
 *
 * CData JDBC Driver は 300+ のデータソースを扱い、認証プロパティ名もデータソースごとに違う
 * （`Password` / `PersonalAccessToken` / `APIKey` / `AWSSecretKey` ...）。
 * 名前を列挙する方式では必ず漏れが出るため、**プロパティ名に機密を示す語が含まれていれば
 * 値をマスクする**方式を採る。
 *
 * 過剰にマスクする方向へ倒している（例: `PublicKeyFile` のようなパス値もマスクされる）。
 * ログや画面に秘密が出るリスクに比べ、過剰マスクの実害は小さいため。
 *
 * 関連: [Issue #10](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/10)
 */
object ConnectionStringMasker {

    const val MASK = "***"

    /** プロパティ名にこれらのいずれかを含む場合、その値をマスクする（大文字小文字は無視）。 */
    private val SENSITIVE_NAME_PARTS = listOf(
        "password",
        "passphrase",
        "token",
        "secret",
        "key",
        "credential",
        "signature",
    )

    /** `Name=Value` を 1 組として捉える。値は次の `;` まで（`;` が無ければ末尾まで）。 */
    private val PROPERTY_REGEX = Regex("""([A-Za-z_][A-Za-z0-9_\-. ]*)=([^;]*)""")

    /** [url] 中の機密プロパティの値を [MASK] に置き換えて返す。 */
    fun mask(url: String): String =
        PROPERTY_REGEX.replace(url) { match ->
            val name = match.groupValues[1]
            if (isSensitive(name)) "$name=$MASK" else match.value
        }

    private fun isSensitive(propertyName: String): Boolean {
        val normalized = propertyName.lowercase()
        return SENSITIVE_NAME_PARTS.any { it in normalized }
    }
}
