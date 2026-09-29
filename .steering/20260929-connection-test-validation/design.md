# 接続テストの資格情報検証 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#45](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/45) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 検証に **`Connection.isValid(int)`** を使う | JDBC 4.0 標準。実測で誤った資格情報を唯一検出できた（1〜2 秒） | `getTables()` → Google Sheets で 90 秒。`sys_tables` → 失敗時も例外を投げず 0 件で、認証失敗と空スキーマを区別できない |
| D-2 | `isValid` の **`false` を失敗として扱う** | `isValid` は例外を投げない。例外の有無だけを見る今の実装が誤判定の原因（C-2） | — |
| D-3 | 検証結果を **`ConnectionValidation` という sealed interface** で表す | 成功・失敗をビューが取り違えられないようにする。`Boolean` + メッセージだと呼び出し側で組み合わせを誤れる | `Result<String>` を使う案 → 「例外なし＋検証失敗」という今回の状態を表現できない |
| D-4 | 検証関数は **`Connection` を引数に取る** | DB 無しでモックしてテストできる（C-5, AC-7） | `JdbcConfig` を受けて中で接続する案 → テストに実ドライバーが必要になる |
| D-5 | 失敗理由は**取得しない** | `getWarnings()` は `null`。理由が取れるのは実データへのクエリだけで、テーブル名を汎用に決められない | 実データへ `SELECT` する案 → テーブル名不明、API コール消費 |
| D-6 | タイムアウトは**固定値 10 秒** | まず正しい判定を入れることを優先。実測は 1〜2 秒で収まる | 設定可能化する案 |
| D-7 | `isValid` は**明示的に呼ぶ** | HikariCP は生成直後の接続を `aliveBypassWindow`（既定 500ms）内では検証しないため、プール任せでは検証されない | プールの `connectionTestQuery` に任せる案 |

### 1.2 なぜ現状は検証できていなかったか

```
getConnection()   → CData ドライバーは認証を遅延させるため通る
getMetaData()     → databaseProductName / driverVersion はドライバー側の静的情報。
                    サーバーへの問い合わせを伴わない
HikariCP の検証   → 生成直後は aliveBypassWindow 内でスキップされる
```

3 つが重なって「何も検証していない」状態になっていた。

### 1.3 フロー

```
POST /connections/{name}/test
  → 接続を張る
      ├─ 失敗（JAR が無い、URL 不正 …） → Invalid(マスク済み例外メッセージ)  ← AC-4
      └─ 成功
          → isValid(10)
              ├─ false → Invalid("接続できませんでした…")                    ← AC-1, AC-3
              └─ true  → Valid("<製品名> <版> / <ドライバー名> <版>")        ← AC-2
  → Valid なら success-banner、Invalid なら warning-banner (#42 の形を維持)
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/ConnectionValidation.kt` | 新規 | 検証結果の sealed interface |
| `jdbc/ConnectionValidator.kt` | 新規 | `isValid` による検証 |
| `web/routes/ConnectionsRoutes.kt` | 変更 | 接続テストが検証結果で分岐する |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/ConnectionValidatorTest.kt` | 新規 | `Connection` をモックし `isValid` の真偽で分岐すること |

---

## 3. データ構造

```kotlin
/**
 * 接続テストの結果。
 *
 * `isValid` は失敗時に例外を投げず `false` を返すだけなので、
 * 「例外は出ていないが検証に失敗した」状態を表せる型が必要になる。
 */
sealed interface ConnectionValidation {
    data class Valid(val description: String) : ConnectionValidation
    data class Invalid(val reason: String) : ConnectionValidation
}
```

---

## 4. 実装

### 4.1 検証

```kotlin
object ConnectionValidator {

    /** `isValid` のタイムアウト（秒）。実測 1〜2 秒で収まるため余裕を持たせた固定値。 */
    const val TIMEOUT_SECONDS = 10

    /**
     * 接続が実際に使えるかを検証する。
     *
     * **`isValid` の戻り値を必ず見ること。** 例外の有無だけで判定すると、
     * 誤った資格情報を「成功」と誤判定する。CData ドライバーは認証を遅延させ、
     * `getConnection()` も `getMetaData()` も通ってしまう (Issue #45)。
     */
    fun validate(connection: Connection): ConnectionValidation {
        if (!connection.isValid(TIMEOUT_SECONDS)) {
            return ConnectionValidation.Invalid(INVALID_MESSAGE)
        }
        val meta = connection.metaData
        return ConnectionValidation.Valid(
            "${meta.databaseProductName} ${meta.databaseProductVersion} / " +
                "${meta.driverName} ${meta.driverVersion}",
        )
    }

    /**
     * `isValid` が false のときの説明。
     *
     * ドライバーは理由を返さない（`getWarnings()` は null）。理由が取れるのは
     * 実データへのクエリだけで、テーブル名を汎用に決められないため、
     * 何を確認すべきかだけを伝える。
     */
    private const val INVALID_MESSAGE =
        "接続を確立できましたが、データソースが応答しませんでした。" +
            "資格情報（ユーザー名・パスワード・トークン等）と接続設定を確認してください。"
}
```

### 4.2 ルート側

```kotlin
post("/connections/{name}/test") {
    val name = call.parameters["name"]!!
    val config = ctx.configSource.loadSharedJdbcConfig(name)
        ?: return@post call.respondText("Not found: $name", status = HttpStatusCode.NotFound)

    val validation = validateConnection(config, name)
    call.respondText(connectionTestFragment(name, validation), ContentType.Text.Html)
}

/** 接続を張って検証する。接続自体に失敗した場合も Invalid に畳み込む。 */
private fun validateConnection(config: JdbcConfig, name: String): ConnectionValidation =
    runCatching {
        JdbcConnectionProvider(config, oauthCacheKey = name).use { provider ->
            provider.connection().use { ConnectionValidator.validate(it) }
        }
    }.getOrElse { cause ->
        // 例外メッセージに接続文字列が含まれる場合があるため必ずマスクを通す。
        ConnectionValidation.Invalid(ConnectionStringMasker.mask(cause.message ?: "接続に失敗しました"))
    }
```

断片の組み立ては #42 の形（`success-banner` / `warning-banner` + 接続名）を維持する。

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 接続テストの判定 | 誤った資格情報が失敗になる | 意図した変更（AC-1） |
| 接続テストの応答時間 | 0.006 秒 → 1〜2 秒程度 | 検証していなかったものを検証するので必要なコスト（AC-5） |
| 成功時の表示 | **変更なし**（製品名・バージョン） | C-4 |
| 接続確立に失敗する場合 | 従来どおり失敗。メッセージもマスク済み | AC-4, AC-6 |
| `JdbcConnectionProvider` | **変更なし** | C-6 |
| `cli/TestConnectionCommand.kt` | **変更なし**（同じ問題が残る） | スコープ外 |
| `docs/` | 影響なし | — |

---

## 6. テスト設計

### 6.1 `ConnectionValidatorTest`（MockK）

- `isValid` が `true` なら `Valid` になり、説明に製品名・バージョン・ドライバー名が入る
- `isValid` が `false` なら `Invalid` になる（**例外が出ていなくても**）
- `Invalid` の理由に確認すべきものが示される
- `isValid` に渡すタイムアウトが `TIMEOUT_SECONDS` である
- `isValid` が `false` のとき `metaData` を呼ばない（無駄な往復をしない）

### 6.2 実機確認

1. 誤った資格情報の接続で失敗表示になる（AC-1）
2. 正しい資格情報の接続で成功表示になり、製品名・バージョンが出る（AC-2）
3. ドライバー JAR が無い接続で失敗表示になる（AC-4）
4. 応答が数秒以内（AC-5）
5. 失敗メッセージがマスクされている（AC-6）
6. 検証用の接続を片付け、`config.db` をバックアップと突き合わせる

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-3 | D-1, D-2, §4.1 | §6.1, §6.2-1 |
| AC-2 | §4.1 | §6.1, §6.2-2 |
| AC-4 | §4.2 | §6.2-3 |
| AC-5 | D-6 | §6.2-4 |
| AC-6 | §4.2 | §6.2-5 |
| AC-7 | D-4, §6.1 | — |
| AC-8 | — | `./gradlew test detekt` |
