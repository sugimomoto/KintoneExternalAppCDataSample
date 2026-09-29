# OAuth トークンの保存 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#34](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/34) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 取得したリフレッシュトークンを**接続設定に保存する** | CData の標準フロー。`InitiateOAuth=REFRESH` + `OAuthRefreshToken` で以降ドライバーが自動更新する。#12 の C-5（接続文字列を書き換えない）を撤回する（C-1） | `OAuthSettingsLocation` に自分で書く案 → あれはドライバーが自動更新時に管理する領域で、プロシージャの戻り値を我々が書く先ではない |
| D-2 | **アクセストークンは保存しない** | 短命で、リフレッシュトークンがあればドライバーが再取得する（C-3） | 両方保存する案 → 無用な機密を増やす |
| D-3 | ResultSet の列名は**大文字小文字を無視して探す** | ドライバーによる差に備える（C-2） | 列番号で取る案 → 順序が変わると壊れる |
| D-4 | プロパティの設定・置換を **`JdbcUrlEnhancer.withProperty` に汎用化**する | `InitiateOAuth` と `OAuthRefreshToken` の 2 つを設定する必要があり、既存の `withInitiateOAuthOff` も同じ処理。3 箇所に同じ正規表現を書かない | 個別の関数を足す案 |
| D-5 | リフレッシュトークンが取れなければ**失敗として扱う** | 保存できていないのに成功表示するのが今回の不具合の本質（AC-4） | 警告付きで成功にする案 |
| D-6 | 保存は `ConfigSource.saveSharedJdbcConfig` を通す | 既存の保存経路に乗せる。`url_template` と `config_json` の整合を自分で書かない | SQL で直接更新する案 |

### 1.2 フローの変更

```
変更前:
  GetOAuthAccessToken を execute → ResultSet を捨てる → 「保存しました」と表示

変更後:
  GetOAuthAccessToken を execute
    → ResultSet から OAuthRefreshToken を読む
    → 取れなければ失敗として表示
    → 接続設定を更新 (InitiateOAuth=REFRESH, OAuthRefreshToken=<値>)
    → 「保存しました」と表示
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/OAuthTokens.kt` | 新規 | ResultSet の行からトークンを解釈する値オブジェクト |
| `jdbc/OAuthAuthorizer.kt` | 変更 | `fetchAccessToken` が `OAuthTokens` を返す |
| `jdbc/JdbcUrlEnhancer.kt` | 変更 | `withProperty` を追加し、`withInitiateOAuthOff` をそれで書き直す |
| `web/routes/ConnectionOAuthRoutes.kt` | 変更 | 取得したトークンを接続設定に保存する |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/OAuthTokensTest.kt` | 新規 | 列名の解釈（大文字小文字・欠落） |
| `jdbc/JdbcUrlEnhancerTest.kt` | 変更 | `withProperty` の追加・置換 |

---

## 3. データ構造

```kotlin
/**
 * `GetOAuthAccessToken` が ResultSet で返すトークン。
 *
 * アクセストークンは短命なので保存しない。リフレッシュトークンを
 * 接続設定に入れておけば、ドライバーが自動で更新する。
 */
data class OAuthTokens(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: String?,
) {
    companion object {
        /** 列名は大文字小文字を無視して探す。ドライバーによる差に備える。 */
        fun from(row: Map<String, String?>): OAuthTokens
    }
}
```

---

## 4. 実装

### 4.1 `JdbcUrlEnhancer.withProperty`

```kotlin
/**
 * プロパティを設定する。既にあれば値を置換し、無ければ追加する。
 *
 * 区切りは `;` だけでなく `:` も見る。CData の接続文字列は
 * `jdbc:<product>:<最初のプロパティ>=...` の形。
 */
fun withProperty(jdbcUrl: String, name: String, value: String): String
```

`withInitiateOAuthOff(url)` は `withProperty(url, "InitiateOAuth", "OFF")` になる。

### 4.2 `OAuthAuthorizer.fetchAccessToken`

```kotlin
fun fetchAccessToken(verifier: String, callbackUrl: String?): OAuthTokens {
    // execute して ResultSet を Map に読む
    // 例外メッセージからは認可コードを伏せる（既存の redactVerifier）
}
```

### 4.3 ルート側

```kotlin
val tokens = authorizer.fetchAccessToken(verifier, callbackUrl)
val refreshToken = tokens.refreshToken
    ?: return 失敗（「リフレッシュトークンを取得できませんでした」）

val updated = config.copy(
    url = JdbcUrlEnhancer
        .withProperty(config.url, "InitiateOAuth", "REFRESH")
        .let { JdbcUrlEnhancer.withProperty(it, "OAuthRefreshToken", refreshToken) },
)
ctx.configSource.saveSharedJdbcConfig(name, updated)
```

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 接続文字列 | 認可後に `InitiateOAuth` と `OAuthRefreshToken` が入る | 意図した変更（D-1） |
| 画面・ログでの表示 | `ConnectionStringMasker` が名前に `token` を含むものをマスクする | 既存の仕組みで伏せられる（AC-6） |
| `config.db` | リフレッシュトークンが平文で入る | 既存の `Password` / `PersonalAccessToken` と同じ扱い |
| 接続テスト・実行時 | `InitiateOAuth=REFRESH` で自動更新される | #11 の `OAuthSettingsLocation` 付与も継続 |
| `withInitiateOAuthOff` の呼び出し元 | 実装が `withProperty` 経由になるが振る舞いは同じ | 既存テストで担保 |

---

## 6. テスト設計

### 6.1 `OAuthTokensTest`

- 想定どおりの列名から読める
- 列名の大文字小文字が違っても読める
- リフレッシュトークンが無ければ null になる
- 空文字は null として扱う

### 6.2 `JdbcUrlEnhancerTest`（追加）

- `withProperty` が無いプロパティを追加する
- 既にあるプロパティを置換する
- 最初のプロパティ（区切りが `:`）も置換する
- 大文字小文字を無視して置換する
- 他のプロパティを壊さない
- `withInitiateOAuthOff` の既存テストが通り続ける

### 6.3 実機確認

1. 認可 → 接続設定に `OAuthRefreshToken` が入る
2. 一覧・プレビューでマスクされている
3. **接続テストが成功する**（本件のゴール）

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 | §4.3 | §6.3 |
| AC-3 | D-1 | §6.3 |
| AC-4 | D-5 | §6.3（異常系は目視） |
| AC-5 | D-3 + D-5 のメッセージ | §6.1 |
| AC-6 | 既存の `ConnectionStringMasker` | §6.3 |
| AC-7 | D-4（他プロパティを壊さない） | §6.2 |
| AC-8 | §6.1, §6.2 | — |
| AC-9 | — | `./gradlew test` |
