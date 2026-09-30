# OAuth コールバック URL の既定値 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#55](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/55) |
| 作成日 | 2026-09-30 |
| 要求定義 | [requirements.md](requirements.md) |

> **注:** 当初は「生成結果が OOB なら差し替えて接続設定に保存し直す」設計だったが、
> 実機検証で `http://localhost:33333` を一律に渡せばよいと分かったため書き直した。
> 経緯は §1.2。

---

## 1. 設計方針

### 1.1 実機検証の結果

`GetOAuthAuthorizationUrl` に渡す `CallbackUrl` を変えて比較した（コンテナ内、JDBC 直叩き）。

#### `oauth.cdata.com` を明示した場合

| ドライバー | `State` なし | `State='http://localhost:33333'` あり |
|---|---|---|
| Google Sheets | ❌ `OAUTH [30003]` | ✅ 生成できる |
| Salesforce | ❌ `OAUTH [30006]` | ❌ **`State` を付けても拒否** |

`State` パラメータは存在するが、Salesforce は `oauth.cdata.com` の明示を受け付けない。

#### `localhost:33333` を渡した場合

| ドライバー | 未指定（現状） | `localhost:33333` を指定 | 一致 |
|---|---|---|---|
| Salesforce | `redirect_uri=https://oauth.cdata.com/oauth/`<br>`state=aHR0cDovL2xvY2FsaG9zdDozMzMzMw==` | **同一** | ✅ |
| Google Sheets | `redirect_uri=urn:ietf:wg:oauth:2.0:oob` | `redirect_uri=http://localhost:33333` | ❌（意図した改善） |

**Salesforce は localhost を渡すとドライバーが自動的に `oauth.cdata.com` + `state=base64(localhost)`
に変換する。** 既定値の結果と 1 バイトも変わらない。

### 1.2 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | `CallbackURL` 未設定なら**常に `http://localhost:33333` を渡す** | ドライバーがプロバイダごとに適切な形へ変換する。Salesforce は結果が完全に同一で回帰リスクがゼロ、Google は OOB が解消される | 生成結果が OOB なら差し替える案（当初案）→ 生成が 2 回必要で、接続設定を書き換える副作用がある |
| D-2 | **接続設定に書き込まない** | 認可ページを開いただけで設定が変わる副作用を避ける。#34 のトークン保存と違い、この値は導出できる | フォールバック値を保存する案（当初案） |
| D-3 | 実効コールバックを **`OAuthCapability` の 1 関数**に集約する | 認可とトークン交換の両方が同じ関数を通るため、`CallbackUrl` の不一致が**構造的に起きない**。保存による同期が不要になる | 各所で `?: DEFAULT` を書く案 → 片方だけ直す事故が起きる |
| D-4 | 明示された `CallbackURL` は**尊重する** | カスタム OAuth アプリを使う場合に壊さない（C-2, AC-5） | 常に上書きする案 |
| D-5 | `oauth.cdata.com` は**使わない** | 明示すると両プロバイダとも拒否される。Salesforce では `State` を足しても通らない | 一律 `oauth.cdata.com` + `State` 案 |
| D-6 | `OAuthAuthorizer` / `OAuthProcedureSql` は変えない | 「どの値を渡すか」の判断で、プロシージャ実行の責務ではない（C-5） | Authorizer の中で既定値を入れる案 |

### 1.3 フロー

```
GET /connections/{name}/oauth
  → effectiveCallbackUrl(config.url) = 設定値 ?: http://localhost:33333
  → 認可 URL を 1 回生成して表示
      Salesforce → redirect_uri=oauth.cdata.com + state=base64(localhost)  （既定と同一）
      Google     → redirect_uri=http://localhost:33333                      （OOB を回避）

POST /connections/{name}/oauth/token
  → effectiveCallbackUrl(config.url) ← 同じ関数なので必ず一致 (AC-3)
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/OAuthCapability.kt` | 変更 | `DEFAULT_CALLBACK_URL` と `effectiveCallbackUrl()` を追加 |
| `web/routes/ConnectionOAuthRoutes.kt` | 変更 | 認可・トークン交換の両方で `effectiveCallbackUrl()` を使う |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/OAuthCapabilityTest.kt` | 変更 | 実効コールバックの解決（設定値優先 / 未設定で既定 / 空白の扱い） |

---

## 3. 実装

### 3.1 実効コールバック

```kotlin
/**
 * CData の組み込み OAuth アプリが受け付けるローカルコールバック。
 *
 * ポートは組み込みアプリ側に登録されている値なので変更できない。
 * 認可後この URL に飛ぶとブラウザは接続エラーになるが、クエリに `code=` が
 * 付くので値をコピーできる。
 */
const val DEFAULT_CALLBACK_URL = "http://localhost:33333"

/**
 * 認可とトークン交換に渡す実効コールバック。設定値があればそれ、無ければ既定値。
 *
 * **未設定でもドライバー既定値に委ねない。** 既定値はプロバイダごとに違い、
 * Google 系は Google が廃止した OOB (`urn:ietf:wg:oauth:2.0:oob`) になるため
 * `invalid_request` で認可できない (Issue #55)。
 *
 * [DEFAULT_CALLBACK_URL] を渡せばドライバーがプロバイダごとに適切な形へ変換する。
 * Salesforce 系は `oauth.cdata.com` + `state=base64(localhost)` になり、
 * **未指定時の結果と完全に一致する**ため回帰しない（実機で確認済み）。
 *
 * `GetOAuthAccessToken` は認可時と同じ `CallbackUrl` を要求するため、
 * **両方の経路がこの関数を通ること。**
 */
fun effectiveCallbackUrl(jdbcUrl: String): String =
    callbackUrlOf(jdbcUrl) ?: DEFAULT_CALLBACK_URL
```

### 3.2 ルート側

```kotlin
// 認可 URL 生成
val callbackUrl = OAuthCapability.effectiveCallbackUrl(config.url)
OAuthNotice(
    authorizationUrl = authorizer.authorizationUrl(callbackUrl),
    callbackUrl = callbackUrl,
)

// トークン交換
authorizer.fetchAccessToken(verifier, OAuthCapability.effectiveCallbackUrl(config.url))
```

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| Google 系（`CallbackURL` 未設定） | `redirect_uri` が localhost になり認可できるようになる | AC-1 |
| Salesforce 系（`CallbackURL` 未設定） | 生成結果が**完全に同一**。実質変更なし | AC-4（実機で確認） |
| `CallbackURL` 明示済みの接続 | その値が使われる。**変更なし** | AC-5 |
| トークン交換 | 認可と同じ関数を通るため必ず一致 | AC-3 |
| 接続設定 | **書き換えない**（当初案との最大の違い） | D-2 |
| `OAuthAuthorizer` / `OAuthProcedureSql` | **変更なし** | C-5 |
| 認可ページの表示 | `callbackUrl` に実効値が出る（従来は未設定時に空だった） | — |

---

## 5. テスト設計

### 5.1 `OAuthCapabilityTest`（追加）

- `CallbackURL` が設定されていればその値を返す
- 未設定なら `DEFAULT_CALLBACK_URL` を返す
- 空文字・空白なら `DEFAULT_CALLBACK_URL` を返す
- 最初のプロパティ（区切りが `:`）に書かれていても拾う
- 大文字小文字が違っても拾う
- `DEFAULT_CALLBACK_URL` の値が `http://localhost:33333` である

### 5.2 実機確認

1. Google 系（未設定）で `redirect_uri` が localhost（AC-1）
2. Salesforce 系（未設定）で `redirect_uri` と `state` が**変更前と同一**（AC-4）
3. `CallbackURL` 明示済みの接続がその値のまま（AC-5）
4. 接続設定が**書き換わっていない**（D-2）
5. 検証データを片付け、`config.db` をバックアップと突き合わせる

トークン交換（AC-3）はブラウザでの認可が必要なため、同じ関数を通るコード経路で担保する。

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1, §3.1 | §5.2-1 |
| AC-2（保存 → 一致に読み替え） | D-3 | §5.2-4, コード経路 |
| AC-3 | D-3, §3.2 | コード経路 |
| AC-4 | D-1（結果が同一） | §5.2-2 |
| AC-5 | D-4 | §5.1, §5.2-3 |
| AC-6 | §5.1 | — |
| AC-7（他プロパティ） | D-2（そもそも書き換えない） | §5.2-4 |
| AC-8 | — | `./gradlew test detekt` |
