# OAuth トークンの保存 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | oauth-token-persistence |
| 対応 Issue | [#34](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/34) |
| 作成日 | 2026-09-29 |
| 経緯 | [#12](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/12) を実機で通したところ判明 |
| 目的 | 認可で取得したトークンを保存し、接続テストと実行時に使えるようにする |

---

## 1. 背景

### 1.1 事象

ウィザードが「OAuth トークンを取得して保存しました」と表示するが、
**トークンがどこにも保存されていない**。

```
1. 認可 URL を生成 → ブラウザで認可 → code を貼り付け
2. 画面: ✅ OAuth トークンを取得して保存しました
3. run/oauth/GoogleSheetsOAuth.txt は作られていない（コンテナ内を find しても無い）
4. 接続テスト: ●Failed: OAUTH [30004] The DISPLAY environment is need for OAuth process.
```

### 1.2 原因

`OAuthAuthorizer.fetchAccessToken` が **ResultSet を読み捨てている**。

`GetOAuthAccessToken` は `OAuthAccessToken` / `OAuthRefreshToken` / `ExpiresIn` を
ResultSet で返すプロシージャで、`OAuthSettingsLocation` に書き込むものではない。
ウィザードは `InitiateOAuth=OFF` で接続するため自動保存も行われない。

### 1.3 設計の誤り

#12 の設計で制約 C-5 に「接続文字列を書き換えない」と定めたのが誤りだった。
CData の標準フローは取得したトークンを接続設定に保存して以降の自動更新に使う。

```
InitiateOAuth=REFRESH;OAuthRefreshToken=<取得した値>
```

**取得したトークンをどこにも保存しない設計では成立しない。**
#12 の PR で「トークン取得は未検証」としていたが、検証できていれば設計段階で気付けた。

### 1.4 実機で確認できた事実

| # | 事実 |
|---|---|
| F-1 | 認可 URL の生成は動く（Google / Salesforce 両方） |
| F-2 | `GetOAuthAccessToken` は例外なく完了する（成功している） |
| F-3 | しかし `OAuthSettingsLocation` のパスにファイルが作られない |
| F-4 | 接続テストは `OAUTH [30004] The DISPLAY environment is need` で失敗する |
| F-5 | Google の組み込みアプリは `CallbackURL` 指定値をそのまま `redirect_uri` にする |
| F-6 | `CallbackURL=http://localhost:33333` で Google の認可が通った |
| F-7 | `AuthMode` を指定しなくてもトークン取得は成功した |

---

## 2. ユーザーストーリー

- **セットアップ担当者として**、認可が終わったら接続テストが通ってほしい。
  「保存しました」と出たのに使えないのは困る。

- **セットアップ担当者として**、保存に失敗したならそう表示してほしい。

- **運用者として**、リフレッシュトークンが画面やログに平文で出ないでほしい。

---

## 3. 受け入れ条件

- [ ] **AC-1** 認可後、取得したリフレッシュトークンが接続設定に保存される
- [ ] **AC-2** `InitiateOAuth` が `REFRESH` に設定される
- [ ] **AC-3** 保存後の接続テストが成功する（再認可が走らない）
- [ ] **AC-4** トークンが取得できなかった場合は成功表示にしない
- [ ] **AC-5** ResultSet の列名が想定と違う場合に分かるメッセージが出る
- [ ] **AC-6** リフレッシュトークンが画面・ログに平文で出ない
- [ ] **AC-7** 他のプロパティ（`CallbackURL` など）が消えない
- [ ] **AC-8** ユニットテストがある
- [ ] **AC-9** `./gradlew test` が通り、本変更で lint の指摘が増えない

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | #12 の制約「接続文字列を書き換えない」を**撤回する** | 1.3 のとおり、それでは動かない。撤回の経緯を設計文書に残す |
| C-2 | 列名の解釈は大文字小文字を無視する | ドライバーによる差に備える |
| C-3 | アクセストークンは保存しない | 短命。リフレッシュトークンがあればドライバーが再取得する |
| C-4 | `OAuthSettingsLocation` の付与は続ける | #11 の一本化を壊さない。ドライバーが自動更新の記録に使う |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| `AuthMode=WEB` の指定 | F-7 のとおり指定なしで成功した。必要になれば別途 |
| トークンの暗号化保存 | `config.db` は既に `Password` 等を平文で持つ。方針変更は別作業 |
| Salesforce の `redirect_uri` 問題 | 組み込みアプリでは `oauth.cdata.com` 固定で、org 側の登録が必要。別課題 |
