package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.config.JdbcConfig

/**
 * 一覧 1 行分の設定読み込み結果。
 *
 * `config_json` が壊れていると `loadSharedJdbcConfig` が例外を投げ、
 * そのままでは**一覧画面全体が 500** になって正常な接続も表示されなくなる。
 * 行単位で失敗を閉じ込め、画面から復旧できる導線を残すためのもの。
 *
 * `loadSharedJdbcConfig` 自体は変更しない。設定の読み込みが静かに失敗するのは危険で、
 * 捕捉するのは画面描画という「落ちてはいけない」文脈に限る。
 *
 * 関連: [Issue #39](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/39)
 */
sealed interface ConnectionRow {

    /** 設定が読めた行。従来どおり全ての情報と操作を出す。 */
    data class Loaded(val config: JdbcConfig) : ConnectionRow

    /** 設定が読めなかった行。削除だけできるようにする。 */
    data class Unreadable(val reason: String) : ConnectionRow

    companion object {

        /** 理由が取れなかった場合の表示。空欄にすると原因不明のまま何も出ない。 */
        private const val UNKNOWN_REASON = "設定を読み込めません"

        /**
         * [load] の失敗を [Unreadable] に畳み込む。
         *
         * 読み込みをラムダで受け取るのは、DB に触らずテストできるようにするため。
         *
         * **例外の型で分類しない。** 壊れ方は JSON の構文エラー・必須フィールド欠落など
         * 一通りではなく、いずれも利用者の対処は同じ（作り直すか削除する）。
         *
         * 名前はあるのに `null` が返る場合も [Unreadable] にまとめる。一覧に名前が
         * 出ている以上、設定が引けないのは異常で、利用者にとっては「読めない」と同じ。
         */
        fun load(load: () -> JdbcConfig?): ConnectionRow =
            runCatching { load() }.fold(
                onSuccess = { config -> config?.let { Loaded(it) } ?: Unreadable(UNKNOWN_REASON) },
                onFailure = { cause -> Unreadable(reasonOf(cause)) },
            )

        private fun reasonOf(cause: Throwable): String =
            cause.message?.takeIf { it.isNotBlank() } ?: UNKNOWN_REASON
    }
}
