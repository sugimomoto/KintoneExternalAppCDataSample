package com.cdata.kintone.adapter.agent

import kotlinx.serialization.Serializable

/**
 * Agent の接続が恒久的に失敗している記録。
 *
 * **接続キーは含めない。** 画面・ログに平文で出さない方針
 * ([Issue #10](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/10) /
 * [#16](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/16)) を踏襲する。
 *
 * 関連: [Issue #21](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/21)
 */
@Serializable
data class AgentConnectionStatus(
    val syncName: String,
    val state: State,
    /** 画面に出す理由。接続キーそのものは含めない。 */
    val reason: String,
    /** 検知時刻 (epoch millis)。 */
    val detectedAt: Long,
) {
    /**
     * 恒久的な失敗の種類。
     * 将来ほかの「再試行では直らない失敗」を足せるよう enum にしている。
     */
    enum class State {
        /** 接続キーが kintone に拒否された。接続キーの再発行が必要。 */
        AUTH_REJECTED,
    }
}
