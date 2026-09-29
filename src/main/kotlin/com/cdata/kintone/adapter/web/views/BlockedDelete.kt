package com.cdata.kintone.adapter.web.views

/**
 * 参照中のため削除できなかったデータソース接続と、その参照元の連携。
 *
 * `tables.jdbc_ref` に外部キー制約が無く、参照中の接続を消すと連携が
 * `ConfigParseException` で起動できなくなる。削除を拒否した理由を画面に出すため
 * 一覧に渡す。
 *
 * 関連: [Issue #36](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/36)
 */
data class BlockedDelete(val connectionName: String, val referencingTables: List<String>)
