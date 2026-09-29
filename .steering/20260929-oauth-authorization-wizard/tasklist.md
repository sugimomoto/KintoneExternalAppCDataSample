# OAuth 認可ウィザード — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 純粋関数（TDD の主戦場）

- [x] **T-10** 🔴🟢 `OAuthCapability.isOAuthAuthScheme`
- [x] **T-11** 🔴🟢 `OAuthUrlMasker.maskQuery`
- [x] **T-12** 🔴🟢 `JdbcUrlEnhancer.withInitiateOAuthOff`
- [x] **T-13** 🔴🟢 `ErrorMessageTranslator` の OAuth ルール

## Phase 2. プロシージャ実行

- [x] **T-20** `OAuthAuthorizer` を実装（`Statement.execute()` + `Url` 列の取得）
- [x] **T-21** SQL リテラルのエスケープ（verifier は外部由来）
- [x] **T-22** `OAuthCapability.hasAuthorizationProcedure`

## Phase 3. 画面とルーティング

- [x] **T-30** `ConnectionOAuthView` を実装（Step 1 / Step 2 / 完了 / 失敗）
- [x] **T-31** `ConnectionOAuthRoutes` を実装
- [x] **T-32** `WebUiServer` にルート登録
- [x] **T-33** 編集画面に導線を追加（`AuthScheme` が OAuth 系のときのみ）

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt
- [x] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`
- [x] **T-42** 認可 URL 生成を確認（**ホストでは成功、コンテナ内はライセンス未認証で失敗**。下記参照）
- [x] **T-43** SAP Gateway で導線が出ないことを実機確認
- [x] **T-44** ログに認可 URL のクエリ / verifier が出ないことを確認

## Phase 5. ドキュメント

- [x] **T-50** `docs/architecture.md` §3.2 に OAuth の対応状況を明記
- [x] **T-51** README の制約表・トラブルシューティングを更新
- [x] **T-52** `docs/extending.md` の R-10 を更新
- [x] **T-53** `HelpView` の「初回ブラウザ認可が必要」を実手順に差し替え

## Phase 6. 仕上げ

- [x] **T-60** コミット
- [x] **T-61** PR 作成・マージ、Issue #12 に結果を記録（検証の限界を明記）

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 編集画面に導線 | ✅ | 実機 |
| AC-2 OAuth 認可が可能な接続のみ表示 | ✅ | 実機: `AuthScheme=OAuth` の 2 件だけ導線あり |
| AC-3 OAuth 非対応で導線が出ない | ✅ | 実機: `Basic` / `PersonalAccessToken` では出ない |
| AC-4 認可 URL を生成して表示 | ⚠️ | **ホストでは実証済み。コンテナ内はライセンス未認証で失敗**（下記） |
| AC-5 ブラウザで開ける | ✅ | リンク + コピー用 textarea を実装 |
| AC-6 verifier 貼り付けフォーム | ✅ | 実装済み |
| AC-7 トークンがキャッシュに保存される | ❌ | 本物の OAuth アプリと認可操作が必要（要求定義 §6 の限界） |
| AC-8 ホストのターミナル不要 | ❌ | **ライセンス認証をコンテナ内で行う必要がある**（下記） |
| AC-9 #11 のパスに保存 | ✅ | `JdbcConnectionProvider(config, oauthCacheKey = name)` を使う |
| AC-10 接続テスト成功 | ❌ | AC-7 と同じ理由 |
| AC-11 認可 URL がログに出ない | ✅ | `OAuthUrlMaskerTest` + `maskQuery` を通してログ出力 |
| AC-12 verifier がログに出ない | ✅ | 実機: `SECRET-CODE-abc123xyz` を POST → ログに 0 件 |
| AC-13 SSE に流れない | ✅ | ライブログは Adapter / Agent のログで、ウィザードは通らない |
| AC-14 非対応ドライバーの案内 | ✅ | `hasAuthorizationProcedure` が false のとき専用メッセージ |
| AC-15 ライセンスエラーの案内 | ✅ | 実機で確認（既存のライセンスルールが機能） |
| AC-16 トークン取得失敗の案内 | ✅ | `ErrorMessageTranslatorTest` |
| AC-17 ユニットテスト | ✅ | 26 件追加 |
| AC-18 テストと lint | ✅ | `test` 成功。detekt は origin/main と同じ 84 件 |

### 実機での確認

導線の出し分け:

```
salesforce     AuthScheme=OAuth                → 導線: あり
googlesheets   AuthScheme=OAuth                → 導線: あり
SAP            AuthScheme=BASIC                → 導線: なし
Bart           AuthScheme=PersonalAccessToken  → 導線: なし
Salesforce1    AuthScheme=Basic                → 導線: なし
```

認可コードの漏洩なし:

```
POST /connections/salesforce/oauth/token  verifier=SECRET-CODE-abc123xyz
→ docker logs | grep -c "SECRET-CODE-abc123xyz"  →  0
```

### ⚠️ 判明した前提条件: コンテナ内のライセンス認証

**コンテナ内ではストアドプロシージャを実行できなかった。**

```
このシステム上には、CData JDBC Driver for Salesforce 2025J用のライセンスが
インストールされていますが、ライセンス認証されていない状況です。
```

ホスト（`java -cp lib/cdata.jdbc.salesforce.jar ...`）では認可 URL の生成に成功する。

```
EXEC GetOAuthAuthorizationUrl CallbackUrl = 'http://localhost:33333'
→ Url = https://login.salesforce.com/services/oauth2/authorize?client_id=...&response_type=code&redirect_uri=...
```

CData のライセンスはマシン（ノード）に紐づくため、コンテナは別マシンと見なされる。
`sys_connection_props` はライセンス未認証でも読めるが、プロシージャは実行できない。

つまり **AC-8（ホストのターミナルを一切使わない）を満たすには、コンテナ内で
ライセンス認証を済ませておく必要がある**。これは本ウィザードの実装の問題ではなく
環境セットアップの前提条件で、ウィザード自体はライセンスエラーを適切に案内している（AC-15）。

別 Issue として起票する。

### 実装で変わった判断

- **判定対象を絞った**。設計では「OAuth 系の `AuthScheme`」としていたが、
  `OAuthPassword` / `OAuthJWT` / `GCPInstanceAccount` はプロパティ入力だけで完結し
  ブラウザ認可を必要としない。`requiresBrowserAuthorization` という名前にして
  対話的な方式だけを対象にした
- **認可コードの漏洩防御を追加した**。実機でログに出ないことは確認できたが、
  引数を SQL リテラルに埋め込む以上、**ドライバーの例外メッセージに SQL が
  含まれると漏れる**。`OAuthUrlMasker.redactVerifier` で伏せてから投げ直す形にした
- 動的プロパティの取得を `propertiesResultFor` に切り出した。導線の判定とフォーム
  描画の両方で使うため 1 回にまとめる必要があり、そのまま書くと
  `connectionFormView` が複雑度の閾値を超えた
