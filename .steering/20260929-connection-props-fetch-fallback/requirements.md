# 接続プロパティ取得のフォールバック — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | connection-props-fetch-fallback |
| 対応 Issue | [#15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15) |
| 作成日 | 2026-09-29 |
| 前提 | Web UI の接続作成画面（動的プロパティフォーム）実装済み |
| 目的 | ドライバのバージョンに依らず `sys_connection_props` を取得し、動的プロパティフォームを表示できるようにする |
| 後続 | [#14](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/14) 認証方式に応じた必須・表示の動的切り替え（本件が前提） |

---

## 1. 背景

### 1.1 現状の実装

接続作成画面の動的フォームは、CData ドライバの `sys_connection_props` システムテーブルから
プロパティ一覧を取得して生成している。

| 箇所 | 内容 |
|---|---|
| `jdbc/JdbcConnectionPropertyInspector.kt:40` | `DriverManager.getConnection("$jdbcPrefix:")` — **空の接続文字列**で接続してクエリする |
| `jdbc/JdbcConnectionPropertyInspector.kt:52-56` | `catch (Exception)` → `warn` ログ + `emptyList()` を返す |
| `web/views/ConnectionsView.kt:226-238` | 空リストのとき警告バナー + JDBC 接続文字列の直接入力にフォールバック |
| `web/views/ConnectionsView.kt:229` | バナー文言「プロパティ取得に失敗しました。CData ドライバではない可能性があります。」 |

### 1.2 課題

1. **26.x 系ドライバでプロパティ一覧が取得できない**

   26.x 系のドライバは `getConnection` の時点でプロパティ検証を行うため、
   空の接続文字列では接続が確立できず例外になる。

   ```
   CORE [50003]
   Validation error for property 'Namespace':
   • Property is required to have a value to establish a connection.
   Validation errors for property 'URL':
   • Property is required to have a value to establish a connection.
   • Value '' does not match required pattern: '^(http|https):\/\/.*'
   ```

   同梱ドライバでの実測値（`lib/` 配下）:

   | ドライバ | Implementation-Version | `jdbc:<product>:` での取得 |
   |---|---|---|
   | `cdata.jdbc.salesforce.jar` | 25.0.9540.0 | OK (268 props) |
   | `cdata.jdbc.googlesheets.jar` | 25.0.9540.0 | OK (238 props) |
   | `cdata.jdbc.bcart.jar` | 25.0.9540.0 | OK (193 props) |
   | `cdata.jdbc.sapgateway.jar` | **26.0.9655.0** | **FAIL** |

   SAP Gateway 固有の問題ではなく、**ドライバを 26.x に更新した時点で全データソースに波及する**。

2. **失敗時の案内が誤っている**

   `lib/cdata.jdbc.sapgateway.jar` は正規の CData ドライバだが、
   「CData ドライバではない可能性があります」と表示される。利用者からは
   「このドライバは非対応」と読めてしまい、原因にも回避策にも辿り着けない。

3. **フォールバック先が実質的に使えない**

   URL 直接入力は「300+ データソースそれぞれの接続プロパティ名を利用者が知っている」ことを
   前提にしており、動的フォームを用意した目的（プロパティ名を知らなくても接続を組める）を満たさない。

### 1.3 調査で判明した事実

| # | 事実 | 根拠 |
|---|---|---|
| F-1 | `Offline=true` では回避できない | `jdbc:sapgateway:Offline=true;` → 同じ検証エラー |
| F-2 | 必須プロパティにダミー値を入れれば取得できる | `jdbc:sapgateway:URL=http://cdata-probe.invalid;Namespace=probe;Service=probe;User=probe;Password=probe;` → OK (207 props) |
| F-3 | **ダミー値には書式制約がある** | `URL=x` は `^(http\|https):\/\/.*` に不一致で FAIL。`URL=http://...` なら OK |
| F-4 | 書式（パターン）を持つ列は `sys_connection_props` に存在しない | 全 18 列を確認: `Name, ShortDescription, Type, Values, Default, Category, Required, Value, IsSessionProperty, Sensitivity, PropertyName, Ordinal, CatOrdinal, Hierarchy, Visible, ETC, alias, usercredential` |
| F-5 | `Driver.getPropertyInfo` は**接続不要**で、26.x でも必須プロパティ名が取れる | SAP Gateway: `URL / Namespace / Service / User / Password` |
| F-6 | ただし `getPropertyInfo` は `Category` / `Hierarchy` / `Sensitivity` / `Ordinal` / `Visible` を返さない。件数も少ない | SAP Gateway: 73 件 vs `sys_connection_props` 207 件 |
| F-7 | `getPropertyInfo` の `name` に**末尾空白が混じる**ことがある | Salesforce / SAP Gateway で `"User "` `"Password "` |
| F-8 | `getPropertyInfo` の `choices` は常に `null` だった | 同梱 4 ドライバすべて |
| F-9 | 未知のプロパティを接続文字列に含めると接続が失敗する | `jdbc:salesforce:NoSuchProperty=1;` → `'nosuchproperty' is not a valid connection property.` |
| F-10 | `Offline` / `InitiateOAuth` は同梱 4 ドライバすべてに存在し、付与しても取得結果は同一 | `Required` / `Visible` / `Hierarchy` / `Category` 全件比較で一致 |
| F-11 | **検証エラーメッセージはロケール依存** | 同じドライバで英語・日本語の両方のメッセージを観測 |

---

## 2. ユーザーストーリー

- **接続を作る利用者として**、ドライバのバージョンを気にせず、
  ドライバを選んだらプロパティ入力フォームが出てほしい。
  接続文字列の書式を自分で調べたくない。

- **接続を作る利用者として**、フォームが出せない場合は
  「なぜ出せないのか」「何をすればよいのか」が分かるメッセージが欲しい。
  「CData ドライバではない可能性があります」では判断できない。

- **運用者として**、プロパティ一覧を取得するだけの操作で
  外部システムへの実通信や OAuth 認可が発生しないことを保証してほしい。

---

## 3. 受け入れ条件

### 3.1 機能

- [ ] **AC-1** 接続作成画面で SAP Gateway (`cdata.jdbc.sapgateway.jar`, 26.0.9655.0) を選ぶと
      動的プロパティフォームが表示される
- [ ] **AC-2** 25.x 系ドライバ (Salesforce / Google Sheets / BCart) の取得結果が従来と変わらない
      （プロパティ件数・`Required` / `Visible` / `Hierarchy` / `Category` が一致）
- [ ] **AC-3** `sys_connection_props` がどうしても取得できない場合、
      `getPropertyInfo` 由来の**縮退フォーム**を表示する（空リストで諦めない）
- [ ] **AC-4** 縮退フォームであることが画面上で利用者に分かる
- [ ] **AC-5** `cdata.jdbc.*` でないドライバクラスを渡した場合は、
      従来どおり URL 直接入力にフォールバックする

### 3.2 メッセージ

- [ ] **AC-6** 「CData ドライバでない」と断定するメッセージは、
      実際に `cdata.jdbc.*` 形式でない場合にのみ表示される
- [ ] **AC-7** 取得失敗・縮退・非 CData ドライバの 3 ケースで表示が出し分けられる

### 3.3 安全性

- [ ] **AC-8** メタデータ取得のためのダミー接続で、外部への実通信や
      OAuth トークン取得（ブラウザ起動）が発生しない
- [ ] **AC-9** ダミー値がフォームの初期値として表示されない
      （`sys_connection_props` の `Value` 列を読まない）
- [ ] **AC-10** 縮退フォームで、認証情報に相当するプロパティが
      平文入力欄にならない（`Sensitivity` 相当の判定が効く）

### 3.4 品質

- [ ] **AC-11** ダミー値生成・縮退マッピング・取得戦略の分岐にユニットテストがある
- [ ] **AC-12** `./gradlew ktlintCheck detekt test` が通る

---

## 4. 制約事項

| # | 制約 | 理由 |
|---|---|---|
| C-1 | 検証エラーメッセージをパースして書式を推測しない | F-11 のとおりロケール依存で、実行環境によって壊れる |
| C-2 | ダミー接続に未知のプロパティを含めない | F-9 のとおり接続自体が失敗する。付与する前に `getPropertyInfo` で存在を確認する |
| C-3 | ドライバごとのハードコード（`if (driverClass == "...")`）を入れない | 300+ データソースに対して維持できない。`ConnectionStringMasker` と同じ「名前ベースの汎用ルール」方式を踏襲する |
| C-4 | プロパティ取得は接続作成画面の表示パスにあるため、試行回数を抑える | 画面表示の体感速度に直結する。キャッシュ前提で最大試行回数を決める |
| C-5 | `ConnectionProperty` の既存フィールドは削らない | `#14` で `hierarchy` を使うため |

---

## 5. スコープ外

| 項目 | 扱い |
|---|---|
| 認証方式に応じた必須・表示の動的切り替え | [#14](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/14) で対応。本件はその前提を整えるだけ |
| ダミー値の書式をパターンから自動生成する | F-4 / F-11 より実現手段がない。縮退フォームで受ける |
| 縮退フォームで `Category` / `Hierarchy` を復元する | `getPropertyInfo` が返さないため不可能 |
| ドライバの自動更新・バージョン警告 | 別件 |
