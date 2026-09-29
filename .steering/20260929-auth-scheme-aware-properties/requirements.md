# 認証方式に応じた接続プロパティの動的制御 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | auth-scheme-aware-properties |
| 対応 Issue | [#14](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/14) |
| 作成日 | 2026-09-29 |
| 前提 | [#15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15) 完了済み（全ドライバーで `sys_connection_props` が取得できる） |
| 目的 | 認証方式 (`AuthScheme`) など他プロパティの選択に応じて、必須マークと表示対象を切り替える |

---

## 1. 背景

### 1.1 現状の実装

| 箇所 | 内容 |
|---|---|
| `web/views/ConnectionsView.kt` `propertyCategories` | `visible` と `category` だけでフィルタ・グルーピングする |
| `web/views/ConnectionsView.kt` `propertyField` | `prop.required` が true なら無条件に `*` を付ける |
| `jdbc/ConnectionProperty.kt` `hierarchy` | 取得しているが**どこからも参照されていない** |
| `web/routes/ConnectionsRoutes.kt` `get("/connections/properties")` | ドライバー選択時のみ呼ばれ、フォームの入力値を受け取らない |

### 1.2 課題

1. **必須マークが実態と合わない**

   `sys_connection_props` の `Required` は静的で、認証方式を変えても変わらない。
   Salesforce ドライバーで実測したところ、接続文字列に `AuthScheme` を指定しても
   `Required=true` のプロパティは常に同じだった。

   ```
   jdbc:salesforce:                   → Required=true は [User, Password, SecurityToken, UseSandbox]
   jdbc:salesforce:AuthScheme=OAuth;  → 同じ
   ```

   結果として、**OAuth 認証を選んでも `User` / `Password` / `SecurityToken` に `*` が付く**。
   逆に OAuth 系プロパティは `Required=false` なので必須マークが付かない。
   利用者は「どれを埋めれば接続できるのか」を画面から判断できない。

2. **その認証方式では使えないプロパティが並ぶ**

   Salesforce は 268 プロパティのうち 105 件が表示対象になる。
   このうち認証方式に関係するものは、選んだ方式では意味を持たないものも含めて全部出る。

3. **選択を変えてもフォームが変わらない**

   `AuthScheme` を変更しても、フォームはドライバー選択時に生成されたままになる。

### 1.3 `Hierarchy` 列に条件が入っている

`sys_connection_props` の `Hierarchy` 列が「他プロパティがこの値のときだけ意味を持つ」という
条件を持っている。形式は `<依存プロパティ名>=<値1>,<値2>,...`。

| PropertyName | Required | Hierarchy |
|---|---|---|
| `User` | true | `AuthScheme=Basic,OAuthPassword,OneLogin,PingFederate,OKTA,ADFS` |
| `SecurityToken` | true | `AuthScheme=Basic,OAuthPassword` |
| `OAuthClientId` | false | `AuthScheme=OAuthClient,AzureAD,OAuthPKCE` |
| `OAuthJWTCert` | false | `AuthScheme=OAuthJWT,AzureServicePrincipalCert` |
| `OAuthAccessToken` | false | `InitiateOAuth=REFRESH,OFF` |
| `BulkPollingInterval` | false | `UseBulkAPI=True` |

つまり **`Required=true` かつ `Hierarchy` の条件を満たす → そのときだけ必須**と解釈できる。

### 1.4 調査で判明した事実

同梱 4 ドライバー（Salesforce / Google Sheets / BCart / SAP Gateway）の全プロパティを走査した結果。

| # | 事実 | 詳細 |
|---|---|---|
| F-1 | 形式は 4 ドライバーとも統一されている | `=` はちょうど 1 個、`;` や入れ子・AND/OR 表現は存在しない |
| F-2 | 条件を持つプロパティ数 | Salesforce 37 / Google Sheets 24 / SAP Gateway 20 / BCart 13 件 |
| F-3 | 依存先プロパティは 5 種類しかない | `AuthScheme` / `InitiateOAuth` / `UseBulkAPI` / `BulkAPIVersion` / `TranslatePickListFields` |
| F-4 | 依存先はすべてプロパティ一覧に存在し `Visible=true` | 「参照先が画面に無い」ケースは実測では発生しない |
| F-5 | **条件は連鎖する** | `OAuthAccessToken` → `InitiateOAuth` → `AuthScheme` の 2 段。依存先自身が条件を持つ |
| F-6 | 依存先はすべて既定値を持つ | `AuthScheme` の既定は Salesforce / Google Sheets が `OAuth`、BCart が `PersonalAccessToken`、SAP Gateway が `Basic`。`InitiateOAuth` は全て `OFF` |
| F-7 | **値の表記が揺れる** | 同じ Salesforce ドライバー内で `User` の条件は `OKTA`、`SSOLoginURL` の条件は `Okta` |
| F-8 | 依存先は選択肢 (`Values`) を持つ | `AuthScheme` は `Basic,OAuth,OAuthClient,...` を返すため画面では `<select>` になる |

---

## 2. ユーザーストーリー

- **接続を作る利用者として**、認証方式を選んだら、その方式で必要な項目だけに `*` が付いてほしい。
  使わない項目に必須マークが付いていると、埋めるべきか判断できない。

- **接続を作る利用者として**、認証方式を切り替えたらフォームもその方式の内容に変わってほしい。
  ただし、すでに入力した他の項目の値は消えないでほしい。

- **既存の接続を編集する利用者として**、保存済みの値が画面から消えないでほしい。
  条件に合わない値が入っていたとしても、勝手に捨てられると接続が壊れる。

---

## 3. 受け入れ条件

### 3.1 必須・表示の解決

- [ ] **AC-1** Salesforce で `AuthScheme=OAuth`（既定値）のとき、
      `User` / `Password` / `SecurityToken` がフォームに出ない
- [ ] **AC-2** `AuthScheme=Basic` のとき、上記 3 つが表示され `*` が付く
- [ ] **AC-3** `AuthScheme=OAuthJWT` のとき `OAuthJWTCert` 系が表示される
- [ ] **AC-4** `AuthScheme` 以外の条件も効く（`UseBulkAPI=True` で `BulkPollingInterval` 等が出る）
- [ ] **AC-5** 条件が連鎖するプロパティが正しく解決される
      （`InitiateOAuth` が非表示なら `OAuthAccessToken` も非表示）
- [ ] **AC-6** `Hierarchy` が空のプロパティは従来どおり表示される（デグレなし）
- [ ] **AC-7** 値の大文字小文字の違いで条件判定が変わらない（F-7）

### 3.2 再レンダリング

- [ ] **AC-8** `AuthScheme` を変更するとフォームが再描画され、必須・表示が切り替わる
- [ ] **AC-9** 再描画後も、入力済みの他プロパティの値が残っている
- [ ] **AC-10** ドライバーを切り替えたときは、前のドライバーの入力値を引き継がない

### 3.3 データの保全

- [ ] **AC-11** 保存済みの値を持つプロパティは、条件を満たさなくても表示される
      （編集時に値が黙って捨てられない）
- [ ] **AC-12** 条件を満たさず値も無いプロパティは、保存時の接続文字列に含まれない

### 3.4 品質

- [ ] **AC-13** 条件のパース・再帰評価・循環参照にユニットテストがある
- [ ] **AC-14** `./gradlew test` が通り、本変更で lint の指摘が増えない

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | 条件の評価はサーバー側で行う | プロパティが 100 件超あり、再帰評価をクライアント JS と二重実装したくない。htmx の部分置換の足場が既にある |
| C-2 | 依存先が一覧に無い・形式が不正な場合は「条件を満たす」と扱う | 判定できないものを非表示にすると、ドライバー更新で画面が空になる。F-4 のとおり実測では発生しないが、想定外の値で壊れない方に倒す |
| C-3 | 循環参照でスタックを溢れさせない | ドライバー由来のデータを信用しきらない |
| C-4 | ドライバー固有・プロパティ名固有の分岐を入れない | `AuthScheme` 決め打ちにすると F-3 の他 4 種に対応できず、データソース追加で壊れる |
| C-5 | 縮退フォーム（`DRIVER_PROPERTY_INFO`）では条件制御が効かない | `getPropertyInfo` は `Hierarchy` を返さない。#15 の制約をそのまま引き継ぐ |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| 必須プロパティが未入力のときの送信ブロック | 現状も行っていない。接続テストで検出する運用を変えない |
| 認証方式ごとの入力ガイド・ヘルプ文の追加 | `shortDescription` の表示で足りている |
| `Values` の選択肢の日本語化 | 送信値そのものなので翻訳しない（開発ガイドライン 3.5.2） |
| 縮退フォームでの条件制御 | C-5 のとおり不可能 |
