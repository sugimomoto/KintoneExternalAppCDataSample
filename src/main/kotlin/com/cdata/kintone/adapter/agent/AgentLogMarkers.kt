package com.cdata.kintone.adapter.agent

/**
 * Agent のログから接続状態を判定するマーカー。
 *
 * 接続操作時 ([SyncConnectionService]) と console 起動時の棚卸し
 * ([AgentAuthFailureSweeper]) の両方から使う。文言がずれると片方だけ
 * 検知漏れするため 1 箇所に集約している。
 *
 * 関連: [Issue #19](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/19)
 */
object AgentLogMarkers {

    /** kintone 接続が成立したことを示す出力。 */
    const val CONNECTED = "successfully connected to kintone"

    /**
     * 接続キーが拒否されたことを示す出力。
     *
     * この失敗は**再試行では直らない**。接続キーは kintone 側の接続インスタンスに
     * 紐づくため、kintone 側で接続を作り直すと、署名も有効期限も正常なまま拒否される。
     */
    private val AUTH_FAILURES = listOf(
        "token has been revoked",
        "Unauthenticated",
        "invalid token",
    )

    fun indicatesConnected(logs: String): Boolean = logs.contains(CONNECTED, ignoreCase = true)

    fun indicatesAuthFailure(logs: String): Boolean =
        AUTH_FAILURES.any { logs.contains(it, ignoreCase = true) }
}
