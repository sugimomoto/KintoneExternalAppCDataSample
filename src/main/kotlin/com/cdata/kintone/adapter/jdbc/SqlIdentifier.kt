package com.cdata.kintone.adapter.jdbc

/**
 * SQL 識別子（テーブル名・カラム名）をクォートするユーティリティ。
 *
 * CData JDBC Driver は SQL Server 形式の `[name]` クォートを全データソースで受け付ける。
 * 空白・予約語・特殊文字を含む識別子でも安全に SQL に埋め込めるようにする。
 *
 * 既に `[name]` 形式でクォート済みの識別子は二重クォートしない（idempotent）。
 * これによりユーザが table.yaml に手動で `name: "[CRM Data]"` のように書いていた
 * 旧構成との後方互換を保つ。
 */
object SqlIdentifier {

    /**
     * 識別子を `[name]` でクォートする。
     * - 既に `[...]` で囲まれていれば素通し
     * - 内部に `]` が含まれる場合は `]]` でエスケープ（SQL Server 慣習）
     */
    fun quote(name: String): String {
        if (name.length >= 2 && name.startsWith('[') && name.endsWith(']')) {
            return name
        }
        val escaped = name.replace("]", "]]")
        return "[$escaped]"
    }

    /**
     * スキーマで修飾した識別子を返す。[schema] が null / 空なら修飾しない。
     *
     * **[quote] にドット分割を任せない。** `quote` は識別子 1 つをクォートする責務で、
     * 正規のテーブル名にドットが含まれる場合に壊れる。スキーマは独立した値として
     * 受け取る (Issue #63)。
     *
     * スキーマを持たないデータソース、およびスキーマ項目の無い既存設定では
     * 修飾しないため、従来と同じ SQL になる。
     */
    fun qualified(schema: String?, name: String): String =
        if (schema.isNullOrBlank()) quote(name) else "${quote(schema)}.${quote(name)}"
}
