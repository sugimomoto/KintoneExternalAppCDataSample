package com.cdata.kintone.adapter.service

import com.cdata.kintone.adapter.jdbc.ConnectionStringMasker
import io.grpc.Status
import io.grpc.StatusException

/**
 * RPC の例外を kintone 側に返す形へ変換する。
 *
 * 以前は `select` だけが例外を捕まえており、他の 8 メソッドは生の例外を
 * grpc-kotlin に渡していた。その結果 **`code: Unknown` かつメッセージ空**になり、
 * ログにも何も出なかった。ドライバーが返していた有用なメッセージ
 * （`Conversion failed when converting date and/or time from character string.`）が
 * 捨てられ、原因の特定に SQL の手作業での再現が必要だった (Issue #70)。
 */
object RpcErrors {

    /** 説明文が組めなかった場合の最後の手段。空文字を返すと `code: Unknown` と区別がつかない。 */
    private const val FALLBACK = "不明なエラー"

    /**
     * 例外を kintone 側に返す説明文に変換する。
     *
     * **接続文字列が混ざる場合に備えてマスクを通す。** JDBC の例外メッセージには
     * 接続文字列が含まれることがあり、kintone の画面は顧客も見る。
     *
     * メッセージが無い例外（`NoWhenBranchMatchedException` 等）では例外クラス名を使う。
     *
     * **分類はしない。** ロケール依存のメッセージをパースすると実行環境によって
     * 壊れる (Issue #19 の教訓)。
     */
    fun descriptionOf(cause: Throwable): String {
        val raw = cause.message?.takeIf { it.isNotBlank() }
            ?: cause::class.simpleName
            ?: FALLBACK
        return ConnectionStringMasker.mask(raw)
    }

    /** 想定外の例外を `INTERNAL` に変換する。原因は `withCause` で保持する。 */
    fun internal(cause: Throwable): StatusException =
        Status.INTERNAL.withDescription(descriptionOf(cause)).withCause(cause).asException()
}
