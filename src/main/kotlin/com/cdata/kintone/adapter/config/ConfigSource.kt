package com.cdata.kintone.adapter.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 設定の永続化レイヤを抽象化するインターフェース。
 *
 * フェーズ2-A では [YamlConfigSource] が唯一の実装。
 * フェーズ2-B（Web UI 導入）で `SqliteConfigSource` を追加予定。
 * 詳細は `.steering/20260522-multi-table-support/requirements.md §11.1` 参照。
 */
interface ConfigSource {
    /** 登録されているテーブル名一覧を返す。 */
    fun listTables(): List<String>

    /** 指定テーブルの全設定セットをロードする。 */
    fun loadTableSet(tableName: String): TableConfigSet

    /** テーブル設定セットを保存する（`init-table` 等から呼ばれる）。 */
    fun saveTableSet(tableName: String, set: TableConfigSet)

    /** テーブル設定を削除する。 */
    fun deleteTable(tableName: String)

    /**
     * 共通 JDBC 設定（データソース別）を取得する。
     * `jdbc-ref` 解決時に使う。見つからなければ null。
     */
    fun loadSharedJdbcConfig(name: String): JdbcConfig?
}

/**
 * 1 テーブル分の設定セット。`AdapterConfig` と構造的に同一だが、
 * マルチテーブル文脈では「1 テーブル分の設定」という意味で `TableConfigSet` 名を使う。
 */
typealias TableConfigSet = AdapterConfig

/**
 * テーブル個別ディレクトリに置く `jdbc-ref.yaml` のスキーマ。
 * 共通 `config/jdbc/<name>.yaml` を参照する場合に使う。
 */
@Serializable
data class JdbcRef(
    @SerialName("name") val name: String,
)
