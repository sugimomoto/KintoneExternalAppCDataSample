package com.cdata.kintone.adapter.jdbc

/**
 * CData ドライバの接続プロパティ 1 件。Web UI の動的フォーム生成に使う。
 *
 * 取得元は `sys_connection_props` システムテーブル ([JdbcConnectionPropertyInspector]) だが、
 * 取得できない場合は `Driver.getPropertyInfo` 由来の縮退版 ([DegradedPropertyMapper]) が入る。
 * どちらで組まれたかは [ConnectionPropertiesResult.source] で判別する。
 */
data class ConnectionProperty(
    val propertyName: String,
    val displayName: String,
    val shortDescription: String,
    val type: PropertyType,
    val defaultValue: String?,
    val allowedValues: List<String>,
    val category: String,
    val required: Boolean,
    val sensitivity: Sensitivity,
    val visible: Boolean,
    /**
     * 「他のプロパティがこの値のときだけ意味を持つ」という条件。
     * 形式は `<依存プロパティ名>=<値1>,<値2>,...`（例: `AuthScheme=Basic,OAuthPassword`）。
     * 条件を持たないプロパティでは空文字。
     */
    val hierarchy: String,
    val ordinal: Int,
    val categoryOrdinal: Int,
)

enum class PropertyType { STRING, INT, BOOLEAN }

enum class Sensitivity { NONE, SENSITIVE, PASSWORD }

/**
 * 接続プロパティ取得の結果。[source] が「どこまで取れたか」を表す。
 *
 * ドライバのバージョンによって `sys_connection_props` が取得できないことがあるため
 * （26.x 系は空の接続文字列を拒否する）、完全取得・縮退・取得不能を区別して
 * UI 側で表示を出し分ける。
 *
 * 関連: [Issue #15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15)
 */
data class ConnectionPropertiesResult(
    val properties: List<ConnectionProperty>,
    val source: PropertySource,
) {
    /** `Category` / `Hierarchy` / 型情報を伴わない縮退結果か。 */
    val isDegraded: Boolean get() = source == PropertySource.DRIVER_PROPERTY_INFO

    companion object {
        fun unavailable(source: PropertySource) = ConnectionPropertiesResult(emptyList(), source)
    }
}

/** [ConnectionPropertiesResult] の取得経路。`NONE_` で始まるものはプロパティが 0 件。 */
enum class PropertySource {
    /** `sys_connection_props` から完全に取得できた（通常経路）。 */
    SYS_CONNECTION_PROPS,

    /** `Driver.getPropertyInfo` のみ取得できた。`Category` / `Hierarchy` / 型情報は欠落する。 */
    DRIVER_PROPERTY_INFO,

    /** ドライバクラスが `cdata.jdbc.*` 形式でない。 */
    NONE_NOT_CDATA_DRIVER,

    /** `lib/` 配下に JAR が無い。 */
    NONE_JAR_MISSING,

    /** CData ドライバだが、どの手段でもプロパティが取得できなかった。 */
    NONE_FETCH_FAILED,
}
