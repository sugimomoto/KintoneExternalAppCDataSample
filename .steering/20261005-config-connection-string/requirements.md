# 接続プロパティ取得の config 接続文字列化 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | config-connection-string |
| 作成日 | 2026-10-05 |
| Issue | [#60](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/60) |
| 目的 | データベース系ドライバーでも接続プロパティを取得できるようにし、ダミー値の推測機構をなくす |

---

## 1. 背景

### 1.1 課題

SQL Server のようなデータベース系ドライバーで接続プロパティを取得できず、縮退フォーム
（`DRIVER_PROPERTY_INFO`）になる。カテゴリー分類が失われ、既定値も落ちる。

### 1.2 原因

`ConnectionPropertyProbe` が**実接続を前提**に組まれている。`jdbc:<product>:` に
ダミー値を詰めた候補 URL を順に試す方式。

SaaS コネクタはプロパティ定義をドライバー内部に持つため接続が成立しなくても返るが、
**SQL Server のような DB 系ドライバーは `getConnection()` で実際に `Server:Port` へ
TCP 接続する**ため、名前解決されないダミー値では必ず失敗する。

```
jdbc:sql:                           → CORE The Username is missing.
jdbc:sql:Server=probe;Port=443;...  → Unable to connect to probe:443: UnknownHostException
```

### 1.3 正しいアプローチ（公式ドキュメント）

[sys_connection_props](https://cdn.cdata.com/help/RFN/jp/jdbc/pg_table-sysconnectionprops.htm) に専用の接続文字列が明記されている。

> このテーブルをクエリする際は、**config 接続文字列**を使用する必要があります。
> `jdbc:cdata:salesforce:config:`
> この接続文字列を使用すると、**有効な接続がなくてもこのテーブルをクエリできます。**

### 1.4 実測

| 取得方法 | 接続 | SQL Server | Salesforce | Category | Hierarchy | Sensitivity | Default |
|---|---|---|---|---|---|---|---|
| `getPropertyInfo` | 不要 | 96 件 | 106 件 | ❌ | ❌ | ❌ | ✅ |
| `jdbc:<product>:` + ダミー値（**現在**） | **要** | ❌ 取得不可 | 268 件 | ✅ | ✅ | ✅ | ✅ |
| **`jdbc:cdata:<product>:config:`** | **不要** | **✅ 240 件** | **✅ 268 件** | ✅ 96 | ✅ 39 | ✅ 20 | ✅ 97 |

**`cdata:` を挟んだ形が必須**。短縮形では効かない。

```
jdbc:sql:config:         → CORE The Username is missing.
jdbc:salesforce:config:  → 'server' is not a valid connection property.
```

### 1.5 `getPropertyInfo` では代替できない理由

接続文字列に `AuthScheme` を渡しても `required` は変化しなかった。

```
SQL Server  (空) / Password / AzureAD / Kerberos / AzureServicePrincipal
            → すべて required=[Server, Port, User, Password, AzureTenant] で不変
```

#14 の「認証方式次第で必須プロパティを動的に変える」は `Hierarchy` に依存しており、
`getPropertyInfo` では実現できない。SQL Server は Hierarchy が 39 件ある。

## 2. 目的

`sys_connection_props` の取得を `config:` 接続文字列に一本化し、すべての CData ドライバーで
実接続なしに完全なプロパティ定義を得る。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | `sys_connection_props` の取得を `jdbc:cdata:<product>:config:` に切り替える |
| F-2 | ダミー値推測の機構を削除する（候補 URL 列挙・`isUrlProperty` / `isBooleanProperty` / `SAFETY_PROPERTIES`） |
| F-3 | `getPropertyInfo` によるフォールバックは残す |
| F-4 | `LicenseVerifier` も `config:` に切り替える（ダミー値機構を完全に削除するため） |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| #59（手動 URL 欄が無条件優先） | 独立した不具合。縮退しなくなれば踏まなくなるが原因は別 |
| `DriverProperty` への既定値の取り込み | `config:` が使えれば `Default` が取れるため、縮退パス改善の優先度は下がる |
| #14 の動的必須プロパティの実装 | 本件は取得経路の修正に限る。Hierarchy を活かす実装は別途 |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、SQL Server でもカテゴリー分類された接続フォームを使いたい。96 個のプロパティが分類なしで並ぶと目的の項目を探せないため |
| US-2 | 検証を進める担当者として、接続プロパティの取得でサーバーに接続しにいかないでほしい。設定前の段階で到達不能なホストへ繋ごうとして待たされるため |
| US-3 | このサンプルを引き継ぐ開発者として、ダミー値を名前から推測する仕組みを保守したくない。公式に用意された手段があるなら、そちらに寄せたい |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | SQL Server 接続の編集画面が縮退フォームにならない（カテゴリー分類と選択肢が出る） |
| AC-2 | Salesforce / Google Sheets など既存の接続が従来どおり完全フォームで表示される |
| AC-3 | プロパティ取得で実サーバーへの接続が発生しない |
| AC-4 | `config:` 接続文字列の組み立てに単体テストがある |
| AC-5 | ダミー値推測の機構が削除されている |
| AC-6 | 非 CData ドライバーでは従来どおりフォールバックする |
| AC-7 | 認証済みドライバーのライセンス検証が「利用可能」になる（SQL Server を含む） |
| AC-8 | 未認証ドライバーのライセンス検証が従来どおり「要アクティベーション」になる |
| AC-9 | `./gradlew test` が通り、detekt がベースライン 84 件のままである |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | `config:` 接続文字列は `jdbc:cdata:<product>:config:` の形にする。短縮形では効かない（実測） |
| C-2 | `<product>` の導出は既存の `jdbcPrefixOf` と同じ規約（ドライバークラスの 3 番目の要素）に従う |
| C-3 | `getPropertyInfo` のフォールバックは残す。`config:` が効かないドライバーの保険 |
| C-4 | `ConnectionPropertiesResult` / `ConnectionProperty` の構造は変えない。取得経路だけを差し替える |
| C-5 | 接続文字列をログに出す場合は `ConnectionStringMasker` を通す（既存方針）。`config:` には資格情報が含まれないが経路は揃える |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `jdbc/ConnectionPropertyProbe.kt` | `config:` の組み立てに作り替え。ダミー値機構を削除 |
| `jdbc/JdbcConnectionPropertyInspector.kt` | 候補 URL の総当たりをやめ、`config:` 1 本にする |
| `jdbc/ConnectionPropertyProbeTest.kt` | ダミー値のテストを削除し、`config:` のテストに置き換え |
| `jdbc/LicenseVerifier.kt` | `candidateUrls` を使っている。シグネチャ変更の影響を受ける |
| `jdbc/DriverProperty.kt` | **変更なし**（フォールバックで引き続き使う） |
| `docs/` | 永続的ドキュメントへの影響なし |

---

## 追記: スコープを 1 点広げた (2026-10-05)

`LicenseVerifier` も `candidateUrls` を使っており、残すとダミー値機構を丸ごと維持する
ことになって AC-5 が達成できないため、本作業に含める。

実測で `config:` でもライセンスが正しく検証されることを確認した。

```
jdbc:cdata:sql:config:        → ✅ sys_procedures 0 件（成功）
jdbc:cdata:salesforce:config: → ✅ sys_procedures 40 件
jdbc:cdata:jira:config:       → ❌ 「ライセンス認証されていない状況です」
```

`sql` が 0 件なのは `config:` が接続を伴わないメタデータ専用モードのためで、
`LicenseVerifier` は件数ではなく**クエリの成否**で判定しているため問題ない。

**副産物:** 従来の SQL Server の「要アクティベーション」は接続失敗による偽陰性で、
実際には認証済みだった。
