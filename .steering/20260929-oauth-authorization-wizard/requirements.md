# OAuth 認可ウィザード — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | oauth-authorization-wizard |
| 対応 Issue | [#12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12) |
| 作成日 | 2026-09-29 |
| 前提 | [#11](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/11) キャッシュパス一本化 / [#27](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/27) 既定値を保存しない — いずれも完了 |
| 目的 | コンテナのまま（ホストのターミナルを使わず）OAuth の初回認可を完了できるようにする |

---

## 1. 背景

### 1.1 現状のサポート範囲

| パターン | 状態 |
|---|---|
| トークン取得済み（リフレッシュトークン / JWT / サービスアカウント）を接続文字列に入力 | ✅ 動作する |
| 初回のブラウザ認可（`InitiateOAuth=GETANDREFRESH`） | ❌ Web UI から実行できない |

`InitiateOAuth=GETANDREFRESH` はドライバーが**自分が動いているマシンでブラウザを開き
localhost でコールバックを受ける**方式のため、ヘッドレスな `adapter-console` コンテナでは
成立せず 60 秒で `OAUTH [50001]` タイムアウトになる。

現状の案内は「ホストのターミナルで `test-connection` を実行してブラウザ認証」止まりで
（README / `web/views/HelpView.kt`）、Web UI だけで完結する体験になっていない。

### 1.2 調査で判明した事実

同梱ドライバーで実測した。

| # | 事実 | 詳細 |
|---|---|---|
| F-1 | ヘッドレス向けの 2 段階フローが**存在する** | Salesforce ドライバーに `GetOAuthAuthorizationUrl` / `GetOAuthAccessToken` / `RefreshOAuthAccessToken` |
| F-2 | プロシージャ名は `...Url`（`URL` ではない） | `GetOAuthAuthorizationUrl` |
| F-3 | 実行は `Statement.execute()` | `executeQuery` は「SELECT のみ」と拒否される |
| F-4 | 認可 URL は **ResultSet で返る** | 出力列は `PKCEVerifier` と `Url` |
| F-5 | 認可 URL の生成は**外部通信なし**でできる | ダミーの `OAuthClientId` でも URL が返る（ローカルで組み立てている） |
| F-6 | `GetOAuthAccessToken` は**外部通信する** | ダミー値では `OAUTH [30004] ... [invalid_client_id] client identifier invalid.` |
| F-7 | **ドライバーによってプロシージャが無い** | SAP Gateway には OAuth プロシージャが 0 件 |
| F-8 | ライセンス状態によって `sys_procedures` が読めない | Google Sheets は試用期限切れ、BCart は未認証でエラー |
| F-9 | 入力パラメータ（主なもの） | `GetOAuthAuthorizationUrl`: `CallbackUrl` / `Scope` / `Grant_Type` / `State` / `PKCEVerifier`<br>`GetOAuthAccessToken`: `Verifier` / `CallbackUrl` / `Scope` / `AuthMode` / `GrantType` / `State` / `PKCEVerifier` |

実測した認可 URL の例:

```
EXEC GetOAuthAuthorizationUrl CallbackUrl = 'http://localhost:8080/connections/sf/oauth/callback'
→ Url = https://login.salesforce.com/services/oauth2/authorize?client_id=dummy-client-id
        &response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2F...
```

### 1.3 課題

1. **初回認可の導線が Web UI に無い**
2. **ドライバーによって対応状況が違う**（F-7）ため、一律にウィザードを出せない
3. **認可 URL と verifier は機密**。URL にはクライアント ID、verifier は認可コードそのもの

---

## 2. ユーザーストーリー

- **セットアップ担当者として**、`docker compose up` した状態だけで OAuth の初回認可を終えたい。
  ホストに JDK を入れてターミナルで `test-connection` を叩く手順を踏みたくない。

- **セットアップ担当者として**、自分の PC のブラウザで認可したい。
  サーバー側でブラウザが開くことを期待されても困る。

- **セットアップ担当者として**、OAuth に対応していないドライバーで
  使えないウィザードを見せられたくない。

- **セットアップ担当者として**、認可に失敗したときに何をすればよいか知りたい。

---

## 3. 受け入れ条件

### 3.1 導線

- [ ] **AC-1** データソース接続の編集画面に「OAuth 認可」への導線がある
- [ ] **AC-2** 導線は **OAuth 認可が可能な接続でのみ**表示される
      （`AuthScheme` が OAuth 系かつドライバーに認可プロシージャがある）
- [ ] **AC-3** OAuth 非対応のドライバー（SAP Gateway 等）では導線が出ない

### 3.2 認可フロー

- [ ] **AC-4** ウィザードが認可 URL を生成して画面に表示する
- [ ] **AC-5** 管理者が自分の PC のブラウザでその URL を開ける（リンク + コピー可能なテキスト）
- [ ] **AC-6** 払い出された verifier を貼り戻すフォームがある
- [ ] **AC-7** verifier を送ると `OAuthSettingsLocation` のキャッシュにトークンが保存される
- [ ] **AC-8** ホストのターミナルを一切使わずに完了できる

### 3.3 取得したトークンの利用

- [ ] **AC-9** 保存先が #11 で一本化したパス（`run/oauth/<接続名>.txt`）である
- [ ] **AC-10** 接続テストが成功する（再認可が走らない）

### 3.4 機密の扱い

- [ ] **AC-11** 認可 URL がログにそのまま出ない（クライアント ID を含む）
- [ ] **AC-12** verifier がログにそのまま出ない
- [ ] **AC-13** ライブログ画面 (SSE) に認可 URL / verifier が流れない

### 3.5 エラー処理

- [ ] **AC-14** 認可プロシージャが無いドライバーで分かるメッセージが出る
- [ ] **AC-15** ライセンスエラーで分かるメッセージが出る
- [ ] **AC-16** トークン取得失敗（`OAUTH [30004]` 等）で何をすべきか分かる

### 3.6 品質

- [ ] **AC-17** 認可可否の判定とマスクにユニットテストがある
- [ ] **AC-18** `./gradlew test` が通り、本変更で lint の指摘が増えない

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | 認可は **verifier 貼り付け方式**にする | Issue の方針。コールバックを自前でホストする場合、OAuth アプリ側に新しいリダイレクト URI を登録し直す作業が発生する |
| C-2 | `OAuthSettingsLocation` は #11 の決定に従う | 接続単位のパスを使う。ウィザード独自のパスを作らない |
| C-3 | 認可 URL / verifier をログに出さない | C-3 は #10 / #16 のマスク方針の延長。クライアント ID と認可コードが漏れる |
| C-4 | ドライバー固有の分岐を入れない | プロシージャの有無で判定する。データソース名で分岐しない |
| C-5 | 接続文字列を書き換えない | トークンはキャッシュファイルに保存される。接続文字列には入れない |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| OAuth アプリ（クライアント ID / シークレット）の事前登録手順の自動化 | Issue のスコープ外。各データソース側の作業なのでドキュメント案内に留める |
| コールバック URL を自前でホストする方式 | C-1 |
| `RefreshOAuthAccessToken` の明示的な呼び出し | リフレッシュはドライバーが自動で行う（`InitiateOAuth=REFRESH`） |
| PKCE フロー | `PKCEVerifier` は任意パラメータ。まず標準の認可コードフローを通す |

---

## 6. 検証の限界（事前に明記）

**実際の認可完了は本作業では検証できない。** 以下が必要なため。

- 本物の OAuth アプリ（クライアント ID / シークレット）
- 管理者のブラウザでの認可操作
- リダイレクト URI の登録

検証できる範囲:

| 項目 | 検証可否 |
|---|---|
| 認可 URL の生成 | ✅ 実ドライバーで可能（F-5） |
| 認可可否の判定 | ✅ 実ドライバーで可能（F-7 の SAP Gateway で 0 件を確認） |
| マスク | ✅ ユニットテスト |
| トークン取得（`GetOAuthAccessToken`） | ❌ 本物の verifier が必要（F-6） |
| 取得後の接続テスト成功 | ❌ 同上 |

同梱ドライバーのうち、**実機で認可 URL 生成まで確認できるのは Salesforce のみ**
（Google Sheets は試用期限切れ、BCart は未認証、SAP Gateway は OAuth 非対応）。
