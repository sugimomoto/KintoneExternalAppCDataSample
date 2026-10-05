# 自動生成列の除外 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | exclude-generated-columns |
| 作成日 | 2026-10-05 |
| Issue | [#75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75) |
| 目的 | 自動生成列をマッピング対象から除外し、kintone からの登録・更新が失敗しないようにする |

---

## 1. 背景

### 1.1 課題

kintone からレコードを登録・更新すると失敗する。

```
DATA_SOURCE The SQL Error Number is 8,169, State is 2, Severity is 16,
Error is 'Conversion failed when converting from a character string to uniqueidentifier.'
```

SQL Server の `rowguid`（`uniqueidentifier`、既定値 `newid()`）がウィザードで選べてしまい、
kintone 側で**入力項目として表示**される。未入力のまま送ると空文字が届き、
`INSERT ... rowguid = ''` が発行されて型変換に失敗する。

### 1.2 JDBC が自動生成列を申告している

`getColumns` の実測結果。

| 列 | 型 | `COLUMN_DEF` | `IS_AUTOINCREMENT` |
|---|---|---|---|
| `CustomerID` | `int identity` | (null) | **YES** |
| `rowguid` | `uniqueidentifier` | **`(newid())`** | NO |
| `ModifiedDate` | `TIMESTAMP` | **`(getdate())`** | NO |
| `NameStyle` | `NameStyle` | `((0))` | NO |
| `LastName` | `Name` | (null) | NO |

**列を送らなければ既定値が正しく効く**（実機で確認）。

```
rowguid を空文字で送る → ❌ Conversion failed ... to uniqueidentifier
rowguid を送らない     → ✅ ID 30130 発行、rowguid = 1B001557-9E7C-... が自動生成
                          ModifiedDate も (getdate()) が入る
```

### 1.3 DATETIME でも同じ問題が起きる

`ModifiedDate` は `NOT NULL` で既定値を持つ。kintone が日時を空で送ると
`DatetimeField.hasValue()` が false になり `NULL` が束縛され、別のエラーになる。

```
setNull(TIMESTAMP) → DATA_SOURCE The SQL Error Number is 515（NULL を挿入できない）
```

空文字の扱いの話ではなく、**列を送ること自体が間違い**。

### 1.4 「空文字を送らない」だけでは解決しない

`TextField.value` には `optional` が付いていない。

```protobuf
message TextField { string field_id = 1; string value = 2; }          // presence なし
message NumberField { string field_id = 1; optional double value = 2; } // presence あり
```

proto3 では presence を持たないため、**「空文字を送った」と「値を送っていない」を
区別できない。** 空文字を一律で省くと**テキスト項目を空にする操作ができなくなる**。

そのため自動生成列を入力項目に出さないことが本筋。

## 2. 目的

自動生成列をウィザードのマッピング対象から除外し、kintone 側で入力項目として
表示されないようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | `ColumnInfo` に既定値・自動採番・計算列の情報を持たせる |
| F-2 | 既定値を持つ列・自動採番列・計算列を step3 の選択候補から外す |
| F-3 | 除外した列と理由を画面に示す |
| F-4 | 主キーの検出に影響させない（`CustomerID` は主キーとして使う） |
| F-5 | レコード ID 列の候補（#66）に影響させない |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| 空文字を Insert から省く | [#76](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/76) で別途。既存連携の救済として入れる |
| 既存連携のカラム構成の編集 | 現状 `/syncs/{name}/edit` はポート・接続・ケーパビリティのみで、カラムは編集できない。別途検討 |
| 自動生成列を読み取り専用で表示 | kintone 側に読み取り専用フィールドの概念が無い（表示すれば入力項目になる） |
| 除外を利用者が覆せるようにする | 選べば必ず失敗する設定を許すことになる。必要になった時点で検討 |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、kintone からレコードを登録したい。自動生成列が入力項目になっていると、未入力のまま送って必ず失敗するため |
| US-2 | 検証を進める担当者として、なぜその列が選べないのか知りたい。黙って消えていると「列が足りない」と誤解するため |
| US-3 | 検証を進める担当者として、主キーは従来どおり自動で決まってほしい。`CustomerID` は自動採番だがレコード番号として必要なため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | 既定値を持つ列（`rowguid` / `ModifiedDate` / `NameStyle`）が step3 の候補に出ない |
| AC-2 | 自動採番列（`CustomerID`）が候補に出ない |
| AC-3 | 除外した列と理由が画面に示される |
| AC-4 | 主キーの検出に影響しない（`CustomerID` が主キーとして決まる） |
| AC-5 | レコード ID 列の候補（#66）に影響しない |
| AC-6 | 既定値を持たない通常の列は従来どおり選べる |
| AC-7 | 自動生成列を申告しないデータソースで候補が減らない |
| AC-8 | 判定ロジックに単体テストがある |
| AC-9 | `./gradlew test` が通り、detekt が 78 件以下である |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | 判定は JDBC の `getColumns` が返す情報だけで行う。ドライバーごとの分岐をしない |
| C-2 | 主キー検出（`findPrimaryKey`）とレコード ID 列の候補（#66）には除外を適用しない |
| C-3 | 判定は純粋関数として切り出し、DB 無しでテストできること |
| C-4 | `ColumnInfo` へのフィールド追加は既定値付きにし、既存の呼び出しを壊さない |
| C-5 | 除外理由の表示は既存の `warning-banner` / `muted` を使う |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `metadata/JdbcMetadataInspector.kt` | `ColumnInfo` に情報を追加し、`getColumns` から読む |
| `metadata/GeneratedColumns.kt` | 新規。自動生成列の判定 |
| `web/views/TableWizardView.kt` | 候補から除外し、理由を表示 |
| `web/routes/TableWizardRoutes.kt` | 除外した列を渡す |
| `config/` | **変更なし** |
| `docs/` | 永続的ドキュメントへの影響なし |

## 8. 実機で分かったこと（AC-7 の補足）

SaaS 系は「自動生成列を持たない」という当初の想定が**誤り**だった。

| データソース | 総列数 | 除外 | 内容 |
|---|---|---|---|
| SQL Server `SalesLT.Customer` | 15 | 4 | `CustomerID`（自動採番）、`rowguid` / `ModifiedDate` / `NameStyle`（既定値） |
| Salesforce `Account` | 68 | 8 | `Id` / `IsDeleted` / `OwnerId` / `CreatedDate` / `CreatedById` / `LastModifiedById` 等を**計算列**として申告 |
| Salesforce `Contact` | 61 | 10 | 同上 |
| B-Cart `Customers` | 52 | 0 | 申告なし |

Salesforce ドライバーは**書き込めない項目**を `IS_GENERATEDCOLUMN = YES` で申告する。
これらは元々マッピングしても登録・更新で失敗していた列なので、除外は改善であり後退ではない。
B-Cart のように申告しないデータソースでは 1 列も減らない。

主キーは両者で維持される（`CustomerID` / `Id`）。除外はマッピング候補にだけ効いている。
