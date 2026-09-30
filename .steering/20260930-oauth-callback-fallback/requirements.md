# OAuth コールバック URL のフォールバック — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | oauth-callback-fallback |
| 作成日 | 2026-09-30 |
| Issue | [#55](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/55) |
| 目的 | `CallbackURL` 未設定の OAuth 接続で認可 URL が OOB にならないようにする |

---

## 1. 背景

### 1.1 課題

接続を保存した後に表示される認可ページで、リダイレクト URL が OOB になる。

```
redirect_uri=urn%3Aietf%3Awg%3Aoauth%3A2.0%3Aoob
```

Google は OOB フローを廃止しているため認可できない。

```
アクセスをブロック: CData Sheets Connector のリクエストは無効です
エラー 400: invalid_request
```

#43 で「OAuth 接続を新規保存したらそのまま認可ウィザードへ進む」ようにしたため、
**新規作成した Google 系接続は既定でこの状態になる**。

### 1.2 原因

`CallbackURL` が接続文字列に無いと `callbackUrlOf` が `null` を返し、
`GetOAuthAuthorizationUrl` に `CallbackUrl` パラメータを渡さない。
結果としてドライバーの既定値が使われるが、**既定値はプロバイダごとに違う**。

実機で確認した結果:

| 接続 | `CallbackURL` | 生成される `redirect_uri` | 結果 |
|---|---|---|---|
| `googlesheets` | 未設定 | `urn:ietf:wg:oauth:2.0:oob` | ❌ Google が廃止済み |
| `SalesforceOAuth` | 未設定 | `https://oauth.cdata.com/oauth/` | ✅ Salesforce では動く |
| `GoogleSheetsOAuth` | `http://localhost:33333` | `http://localhost:33333` | ✅ 認可成功（確認済み） |

### 1.3 実装上の落とし穴

#### 落とし穴 1: 一律に localhost を強制すると Salesforce が壊れる

CData の組み込み OAuth アプリは、プロバイダごとに登録済みのリダイレクト URI が違う。
Salesforce は `https://oauth.cdata.com/oauth/` が登録されているため、localhost を
強制すると `redirect_uri_mismatch` になる。

逆に `oauth.cdata.com` を Google に指定するのも不可（セッション中に確認）。

```
OAUTH [30003] Invalid URL. When using oauth.cdata.com, the state parameter
must be specified as the actual callback and must be a localhost address.
```

#### 落とし穴 2: トークン交換にも同じ CallbackUrl が要る

`GetOAuthAccessToken` は認可時と同じ `CallbackUrl` を要求する。
現在の実装はトークン交換時にも接続文字列から読み直している。

```kotlin
// web/routes/ConnectionOAuthRoutes.kt:87
authorizer.fetchAccessToken(verifier, OAuthCapability.callbackUrlOf(config.url))
```

**認可 URL 生成時だけフォールバックしても、トークン交換で不一致になり失敗する。**
フォールバックした値は接続設定に保存する必要がある（#34 でリフレッシュトークンを
保存しているのと同じ考え方）。

## 2. 目的

ドライバーが OOB を使おうとした場合に限り localhost にフォールバックし、
Google 系接続でも初回認可を完結できるようにする。動いているものは変えない。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | 生成された認可 URL の `redirect_uri` が OOB かを判定する |
| F-2 | OOB の場合、`CallbackURL=http://localhost:33333` を接続設定に保存する |
| F-3 | 保存した値で認可 URL を生成し直す |
| F-4 | トークン交換は保存済みの値を読むため自動的に一致する（既存動作） |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| プロバイダごとの既定 `CallbackURL` テーブル | 300+ のデータソースがあり列挙は破綻する。生成結果から判断する |
| ポート 33333 の設定可能化 | CData の組み込みアプリが登録しているポート。変更する自由度がない |
| カスタム OAuth アプリの案内 | 公式ドキュメントは Web アプリにカスタムアプリを推奨しているが、本サンプルの導線とは別の話 |
| `oauth.cdata.com` + `state` 方式への対応 | Salesforce では既定で動いており、手を入れる理由がない |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | デモ環境を構築する担当者として、Google 系の接続を作ったらそのまま認可まで進みたい。今は認可ページで Google にブロックされ、手作業で `CallbackURL` を足す必要があるため |
| US-2 | デモ環境を構築する担当者として、Salesforce の認可が今までどおり動いてほしい。リダイレクト先を変えられると認可できなくなるため |
| US-3 | デモ環境を構築する担当者として、`CallbackURL` を自分で設定した場合はその値を使ってほしい。カスタム OAuth アプリを使う場合に上書きされると困るため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | `CallbackURL` 未設定の Google 系接続で、認可 URL の `redirect_uri` が OOB にならない |
| AC-2 | フォールバックした `CallbackURL` が接続設定に保存される |
| AC-3 | 保存後、トークン交換が同じ CallbackUrl で行われる |
| AC-4 | `CallbackURL` 未設定の Salesforce 系接続は従来どおり `oauth.cdata.com` のまま（回帰なし） |
| AC-5 | 明示的に `CallbackURL` を設定している接続はその値が尊重される |
| AC-6 | OOB 判定に単体テストがある |
| AC-7 | フォールバック後も他の接続プロパティが失われない |
| AC-8 | `./gradlew test` が通り、detekt がベースライン 84 件のままである |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | プロバイダ名で分岐しない。生成された URL を見て判断する |
| C-2 | 明示された `CallbackURL` は上書きしない（AC-5） |
| C-3 | 接続設定への書き込みは `JdbcUrlEnhancer.withProperty` を通す（#34 で用意した経路） |
| C-4 | 認可 URL はクエリにクライアント ID を含むため、ログには `OAuthUrlMasker` を通す（既存方針） |
| C-5 | `OAuthProcedureSql` / `OAuthAuthorizer` のインターフェースは変えない。フォールバックは呼び出し側の判断 |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `jdbc/OAuthCapability.kt` | OOB 判定と既定コールバックの定数を追加 |
| `web/routes/ConnectionOAuthRoutes.kt` | 認可 URL 生成にフォールバックを追加 |
| `jdbc/OAuthAuthorizer.kt` | **変更なし**（C-5） |
| `config/` | **変更なし** |
| `docs/` | 永続的ドキュメントへの影響なし |
