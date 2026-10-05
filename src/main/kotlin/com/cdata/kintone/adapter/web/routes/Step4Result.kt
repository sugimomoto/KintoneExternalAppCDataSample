package com.cdata.kintone.adapter.web.routes

import com.cdata.kintone.adapter.metadata.ColumnInfo
import com.cdata.kintone.adapter.metadata.RecordIdType
import com.cdata.kintone.adapter.web.views.WizardMapping

/**
 * 連携追加ウィザード step4 の算出結果。
 *
 * **例外ではなく型で返す。** 以前は主キーが無いと `IllegalStateException` を投げ、
 * ハンドリングされずに 500 となって画面が真っ白になっていた (Issue #64)。
 *
 * 主キーが無い場合と取得に失敗した場合を分けているのは、利用者の次の行動が違うため。
 * 前者は「連携できない」という確定した結論で、再試行しても変わらない。
 */
sealed interface Step4Result {

    /** step4 に進める。 */
    data class Ready(
        val primaryKey: String,
        val recordIdType: RecordIdType,
        val mappings: List<WizardMapping>,
    ) : Step4Result

    /**
     * 主キーが検出できなかった。
     *
     * kintone の「外部システムのアプリ化」は `GetCapability` で `RecordIdType` を、
     * `GetSchema` で `RecordIdFieldDefinition` を要求する。どちらも必須メソッドなので、
     * レコード番号に使える列が無いテーブル・ビューは**読み取り専用であっても連携できない**。
     *
     * [candidates] はレコード ID 列の候補。
     * [Issue #66](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/66) で
     * 利用者に選ばせるために持たせている。本 Issue では表示しない。
     */
    data class NoPrimaryKey(val tableName: String, val candidates: List<ColumnInfo>) : Step4Result

    /** メタデータ取得などに失敗した。[reason] はマスク済み。 */
    data class Failed(val reason: String) : Step4Result
}
