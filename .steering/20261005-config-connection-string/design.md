# 接続プロパティ取得の config 接続文字列化 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#60](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/60) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | `sys_connection_props` / `sys_procedures` の取得を **`jdbc:cdata:<product>:config:` 1 本**にする | 公式に用意された手段。接続不要で、DB 系ドライバーでも全メタデータが取れる（実測） | 候補 URL を総当たりする現行方式 → DB 系で必ず失敗する |
| D-2 | ダミー値推測の機構を**削除**する | `config:` では値を一切渡さないため不要。`isUrlProperty` / `isBooleanProperty` / `BOOLEAN_NAME_PREFIXES` / `SAFETY_PROPERTIES` / `PROBE_URL` / `PROBE_VALUE` がすべて不要になる | 残す案 → 使われないコードが残り、将来誤って使われる |
| D-3 | `LicenseVerifier` も `config:` に移す | 残すとダミー値機構を維持せざるを得ない。`config:` でもライセンスは正しく検証される（実測） | ライセンス検証だけ旧方式を残す案 |
| D-4 | `getPropertyInfo` のフォールバックは**残す** | `config:` が効かないドライバーの保険（C-3）。非 CData ドライバーの判定も既存のまま | フォールバックも削る案 → 退避路がなくなる |
| D-5 | `ConnectionPropertyProbe` は**関数を差し替えて残す** | 呼び出し側の構造を変えずに済む。「プローブ用接続文字列を組み立てる」という責務自体は維持される | ファイルを削除して各所に直接書く案 → 組み立てが散る |
| D-6 | `driverProperties` 引数を**落とす** | `config:` は必須プロパティを知る必要がない。引数を残すと「使っていないのに渡す」ことになる | 互換のため残す案 |

### 1.2 config 接続文字列の形

**`cdata:` を挟んだ形が必須。** 短縮形では効かない（実測）。

```
✅ jdbc:cdata:sql:config:          → 240 件
❌ jdbc:sql:config:                → CORE The Username is missing.
❌ jdbc:salesforce:config:         → 'server' is not a valid connection property.
```

既存の `jdbcPrefixOf` は `cdata.jdbc.sql.SQLDriver` → `jdbc:sql` を返す。
`config:` 用には同じ規約で**製品名だけ**を取り出して組み立てる。

### 1.3 フロー

```
変更前:
  getPropertyInfo で必須プロパティを得る
    → ダミー値を名前から推測
    → 候補 URL を 4 通り組み立て
    → 順に getConnection して sys_connection_props を試す
        ├─ SaaS: どれかが通る
        └─ DB 系: 全滅 → 縮退フォーム

変更後:
  jdbc:cdata:<product>:config: で sys_connection_props を引く
    ├─ 成功 → 完全なプロパティ定義（Category / Hierarchy / Sensitivity / Default）
    └─ 失敗 → getPropertyInfo で縮退（保険）
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/ConnectionPropertyProbe.kt` | 変更 | `candidateUrls` を `configUrl` に差し替え。ダミー値機構を削除 |
| `jdbc/JdbcConnectionPropertyInspector.kt` | 変更 | 総当たりをやめ `configUrl` 1 本にする |
| `jdbc/LicenseVerifier.kt` | 変更 | `configUrl` を使う |
| `jdbc/ConnectionPropertyProbeTest.kt` | 変更 | ダミー値のテストを削除し `configUrl` のテストに置き換え |
| `jdbc/LicenseVerifierTest.kt` | 変更 | 候補 URL 前提のテストがあれば追従 |

---

## 3. 実装

### 3.1 `ConnectionPropertyProbe`

```kotlin
/**
 * `sys_connection_props` / `sys_procedures` を読むための **config 接続文字列**を組み立てる。
 *
 * CData の公式ドキュメントが指定する形式で、**有効な接続がなくてもメタデータを
 * クエリできる**。
 *
 * 以前はダミー値を詰めた候補 URL を総当たりしていたが、SQL Server のような
 * データベース系ドライバーは `getConnection()` で実際に `Server:Port` へ接続するため
 * 必ず失敗していた (Issue #60)。
 *
 * **`cdata:` を挟んだ形が必須。** 短縮形 (`jdbc:sql:config:`) では効かない。
 */
object ConnectionPropertyProbe {

    /**
     * ドライバークラスから config 接続文字列を組み立てる。
     *
     * 例: `cdata.jdbc.sql.SQLDriver` → `jdbc:cdata:sql:config:`
     */
    fun configUrl(driverClass: String): String = "jdbc:cdata:${productOf(driverClass)}:config:"

    private fun productOf(driverClass: String): String { ... }
}
```

製品名の取り出しは `jdbcPrefixOf` と同じ規約（`require(isCDataDriver)` + 3 番目の要素）。

### 3.2 `JdbcConnectionPropertyInspector`

```kotlin
private fun fetchFromConfig(driverClass: String): List<ConnectionProperty>? =
    runCatching { metadataSource.sysConnectionProps(ConnectionPropertyProbe.configUrl(driverClass)) }
        .onFailure { log.debug(it) { "config 接続でのプロパティ取得に失敗: $driverClass" } }
        .getOrNull()
```

総当たりの `firstNotNullOfOrNull` は不要になる。

### 3.3 `LicenseVerifier`

```kotlin
private fun probe(driverClass: String): LicenseVerification =
    runCatching { metadataSource.sysProcedureNames(ConnectionPropertyProbe.configUrl(driverClass)) }
        .fold(
            onSuccess = { LicenseVerification.Valid },
            // 件数では判定しない。config: は接続を伴わないため、ストアドプロシージャを
            // 列挙しないドライバーがある（SQL Server は 0 件）。未認証なら例外になる。
            onFailure = { LicenseVerification.Invalid(it.message ?: "ライセンスを検証できませんでした") },
        )
```

ReturnCount 対策で分割していた `loadAndProbe` の構造は維持する。

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| SQL Server の編集画面 | 縮退しなくなり、カテゴリー分類と選択肢が出る | AC-1 |
| SaaS コネクタの編集画面 | `config:` でも同じ件数が取れる（Salesforce 268、Google Sheets 238 → 実測で確認） | AC-2 |
| プロパティ取得時の通信 | **実サーバーへの接続が発生しなくなる** | AC-3 |
| ライセンス検証 | 偽陰性が解消。SQL Server が「利用可能」になる | AC-7, AC-8 |
| 非 CData ドライバー | `configUrl` が `require` で弾く → 従来どおりフォールバック | AC-6 |
| #59（手動 URL 欄） | 縮退しなくなるため**踏まなくなる**が、原因は別で未修正 | 別 Issue |
| `#14` の動的必須プロパティ | `Hierarchy` が取れるようになる（SQL Server 39 件）。実装は別途 | スコープ外 |

---

## 5. テスト設計

### 5.1 `ConnectionPropertyProbeTest`

- `cdata.jdbc.sql.SQLDriver` → `jdbc:cdata:sql:config:`
- `cdata.jdbc.salesforce.SalesforceDriver` → `jdbc:cdata:salesforce:config:`
- 非 CData ドライバークラスでは例外になる
- 短縮形 (`jdbc:<product>:config:`) を**生成しない**ことを明示するテスト

### 5.2 実機確認

1. SQL Server の編集画面が縮退バナーを出さず、カテゴリーが出る（AC-1）
2. Salesforce / Google Sheets が従来どおり（AC-2）
3. ライセンス検証が SQL Server で「利用可能」になる（AC-7）
4. 未認証の Jira が「要アクティベーション」のまま（AC-8）
5. プロパティ取得でサーバーへの接続が起きない（到達不能なホストでも待たされない）

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1, §3.1, §3.2 | §5.2-1 |
| AC-2 | D-1 | §5.2-2 |
| AC-3 | D-1（接続を伴わない） | §5.2-5 |
| AC-4 | §5.1 | — |
| AC-5 | D-2 | コード差分 |
| AC-6 | D-4, D-5 | §5.1 |
| AC-7 / AC-8 | D-3, §3.3 | §5.2-3, §5.2-4 |
| AC-9 | — | `./gradlew test detekt` |
