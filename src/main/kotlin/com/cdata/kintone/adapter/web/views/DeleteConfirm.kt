package com.cdata.kintone.adapter.web.views

/**
 * 削除操作の確認ダイアログ (`onsubmit` 属性の値) を組み立てる。
 *
 * 連携名は利用者が入力した値をそのまま JS 文字列リテラルに埋め込むため、
 * `'` や `\` を含む名前では式が壊れて削除ボタンが動かなくなる。
 * 生成をここに閉じ込めてエスケープを保証する。
 *
 * 関連: [Issue #8](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/8)
 */
internal object DeleteConfirm {

    /**
     * 連携削除の確認。
     * Adapter / Agent コンテナ / `agent.json` も消えることを明示する。
     */
    fun syncDeleteOnSubmit(syncName: String): String = onSubmit(
        "連携 \"${escapeJsString(syncName)}\" を削除しますか？" +
            "稼働中の Adapter と Agent コンテナを停止・削除し、agent.json も削除します。" +
            "この操作は取り消せません。",
    )

    private fun onSubmit(message: String): String = "return confirm('$message')"

    /**
     * JS の単一引用符文字列として安全な形に変換する。
     * `\` を最初に置換する（後にすると二重エスケープになる）。
     */
    fun escapeJsString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
