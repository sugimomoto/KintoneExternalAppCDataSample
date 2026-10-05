# レコード ID 列の指定 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | record-id-column |
| 作成日 | 2026-10-05 |
| Issue | [#66](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/66) |
| 目的 | 主キー制約の無いテーブル・ビューでも、レコード ID 列を指定して連携できるようにする |

---

## 1. 背景

### 1.1 課題

ウィザードは `getPrimaryKeys()` が返す主キーだけを見ている。

```kotlin
val pk = inspector.findPrimaryKey(tableName, schema)
    ?: return@use Step4Result.NoPrimaryKey(tableName, allColumns)
```

そのため **PRIMARY KEY 制約の無いテーブルやビューは、一意な列があっても連携できない。**

### 1.2 仕様上は主キー制約は不要

`kintone-external-app-spec` で確認した。kintone が要求しているのは
**「レコード番号として使える列」**であって「DB の PRIMARY KEY 制約」ではない。

| 要求元 | 内容 |
|---|---|
| `GetCapability`（必須） | `RecordIdType`（NUMBER / TEXT）の宣言 |
| `GetSchema`（必須） | `RecordIdFieldDefinition` |

```protobuf
message RecordIdFieldDefinition { string field_id = 1; }
```

`field_id` を 1 つ指定するだけで、その列が DB 上で主キーかは問わない。
**一意性が担保されていれば任意の列を使える。**

型の制約は決まっている。

| JDBC 型 | RecordIdType |
|---|---|
| `BIGINT` / `INTEGER` / `SMALLINT` / `TINYINT` | NUMBER |
| `VARCHAR` / `CHAR` / `LONGVARCHAR` / `NVARCHAR` / `NCHAR` / `LONGNVARCHAR` | TEXT |
| それ以外 | **使えない** |

既存の `FieldTypeSuggester.suggestRecordIdType` がこの対応表を持ち、対応外の型では
`error()` を投げる。**候補の絞り込みはこれを再利用できる。**

TEXT の場合は値の形式制約もある。

> `RecordId.value_text` パターン | `^[a-zA-Z0-9_-]{0,100}$`

### 1.3 #64 との関係

#64 で `Step4Result.NoPrimaryKey(tableName, candidates)` を用意し、候補列を持たせてある。
本作業はそれを使って「列を選べる画面」に発展させる。

## 2. 目的

主キーが検出できない場合に、利用者がレコード ID 列を選んで連携を作成できるようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | 主キーが検出できない場合、レコード ID 列の候補を画面に出して選ばせる |
| F-2 | 候補は kintone の制約に合う型に限る（`suggestRecordIdType` が対応する型） |
| F-3 | 選んだ列を使って step4 に進む |
| F-4 | 選んだ列を `PrimaryKeyConfig.jdbcColumn` に保存する（保存形式は変えない） |
| F-5 | 一意性が利用者の責任であることを画面で明示する |
| F-6 | 候補が 1 件も無い場合は、従来どおり連携できない旨を伝える |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| 一意性の自動検証 | `COUNT(*)` と `COUNT(DISTINCT col)` の比較は大きなテーブルで重い。既定で走らせると設定操作が待たされる。将来の任意機能とする |
| 主キーが検出できた場合に別の列を選ばせる | 現状の自動決定で足りている。スコープを広げると「どちらを正とするか」の判断が増える |
| TEXT レコード ID の値形式の検証 | `^[a-zA-Z0-9_-]{0,100}$` は実行時の値の制約。設定時には判定できない |
| 複合主キーへの対応 | `RecordIdFieldDefinition` は単一 `field_id`。仕様上成立しない |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、ビューや PK 制約の無いテーブルも連携したい。一意な列はあるのに連携できないのは制約が強すぎるため |
| US-2 | 検証を進める担当者として、選べる列が型で絞られていてほしい。kintone が受け付けない型を選んで後で失敗するのは手戻りが大きいため |
| US-3 | 検証を進める担当者として、一意でない列を選んだときのリスクを知りたい。更新・削除が意図しない行に及ぶと被害が大きいため |
| US-4 | 検証を進める担当者として、主キーがあるテーブルでは今までどおり自動で決まってほしい。手順が増えるのは困るため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | 主キーの無いテーブル／ビューで、レコード ID 列を選んで step4 に進める |
| AC-2 | 候補列が kintone の制約に合う型に絞られる（日時・真偽値・BLOB 等は出ない） |
| AC-3 | 一意性が利用者の責任であることが画面に示される |
| AC-4 | 選んだ列が `PrimaryKeyConfig.jdbcColumn` に保存され、実行時に使われる |
| AC-5 | 選んだ列の型から `RecordIdType`（NUMBER / TEXT）が決まる |
| AC-6 | 候補が 1 件も無い場合は、従来どおり連携できない旨が表示される |
| AC-7 | 主キーが検出できるテーブルは従来どおり自動で決まり、選択 UI は出ない |
| AC-8 | 候補列の絞り込みに単体テストがある |
| AC-9 | `./gradlew test` が通り、detekt が 82 件以下である |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | 候補の型判定は `FieldTypeSuggester` の対応表を再利用する。型の集合を 2 箇所に書かない |
| C-2 | 保存形式（`PrimaryKeyConfig`）は変えない。主キー由来か利用者選択かを区別して保存しない |
| C-3 | ウィザードのステップ構成（step1〜step4）は変えない |
| C-4 | 一意性の検証は行わない。行う場合も既定では走らせない（スコープ外） |
| C-5 | 選択 UI は #64 で作ったエラー表示の経路に載せる。新しい画面を作らない |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `metadata/FieldTypeSuggester.kt` | レコード ID に使える型かを判定する関数を追加 |
| `web/routes/TableWizardRoutes.kt` | `recordIdColumn` を受け取り、あればそれを使う |
| `web/views/TableWizardView.kt` | 候補列の選択 UI と注意書き |
| `config/` | **変更なし**（C-2） |
| `docs/` | 永続的ドキュメントへの影響なし（データモデルの変更を伴わない） |
