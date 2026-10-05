# Insert で空文字のテキスト値を送らない — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | omit-empty-text-on-insert |
| 作成日 | 2026-10-05 |
| Issue | [#76](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/76) |
| 目的 | 既存の連携でも kintone からのレコード登録が成功するようにする |

---

## 1. 背景

### 1.1 #75 では既存の連携が救われない

自動生成列をマッピングした連携では、kintone が空文字を送るため登録が失敗する。

```
DATA_SOURCE The SQL Error Number is 8,169,
Error is 'Conversion failed when converting from a character string to uniqueidentifier.'
```

根本原因は「自動生成列が入力項目になっていること」で、[#75](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/75)
でウィザードの候補から外した（PR #77、マージ済み）。

**ただし #75 はこれから作る連携にしか効かない。** `/syncs/{name}/edit` はポート・接続・
ケーパビリティのみを編集でき、**カラム構成は編集できない**ため、既存の連携は
作り直しが必要になる。

### 1.2 Update では同じ扱いにできない

`TextField.value` には `optional` が付いておらず、proto3 の presence を持たない。

```protobuf
message TextField {
  string field_id = 1;
  string value = 2;     // ← presence なし
}
```

そのため**「空文字を送った」と「値を送っていない」を区別できない。**

| 操作 | 空文字を省いた場合 |
|---|---|
| **Insert** で未入力 | ✅ 既定値が効く。「クリア」という概念が無いので副作用なし |
| **Update** で値を消す | ❌ 何も起きない（元の値が残る）→ クリアできなくなる |

Insert には「既存の値を空にする」という操作が存在しないため、空文字を省いても
失うものがない。Update は意味が変わるので触らない。

## 2. 目的

Insert に限り、空文字のテキスト値を SQL に含めない。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | Insert で**値が入っていないフィールド**を SQL に含めない（空文字のテキスト、未入力の数値・日時・選択） |
| F-2 | Update の挙動は変えない（空文字でクリアできる） |
| F-3 | 省いた結果 Insert 対象が 0 列になる場合は省かない |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| Update でも空文字を省く | テキスト項目を空にできなくなる。プロトコルで区別できないため両立しない |
| ~~空の DATETIME / NUMBER を省く~~ | **実機確認の結果、Insert でも省くことにした。§8 参照** |
| 既存連携のカラム構成の編集 | 別途検討 |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、既存の連携を作り直さずにレコードを登録したい。カラム構成は画面から編集できないため |
| US-2 | 検証を進める担当者として、テキスト項目を空にする操作は従来どおり使いたい |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | Insert で値が入っていないフィールドが SQL に含まれない |
| AC-2 | Insert で値のあるテキストは従来どおり送られる |
| AC-3 | Update の挙動は変わらない（空文字でクリアできる） |
| AC-4 | Update での空の NUMBER / DATETIME の扱いは変わらない（`null`） |
| AC-5 | 空文字しか無い場合でも Insert が組み立てられる（0 列にならない） |
| AC-6 | Insert に主キーが含まれない（従来どおり） |
| AC-7 | 判定ロジックに単体テストがある |
| AC-8 | `./gradlew test` が通り、detekt が 78 件以下である |
| AC-9 | `rowguid` をマッピングした連携で Insert が成功し、既定値が入ることを実機で確認する |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | Update の経路には一切手を入れない |
| C-2 | 判定はフィールド型を知っている `RowMapper` に置く。サービス層で型を見ない |
| C-3 | 空文字の省略は Insert 専用のメソッドとして明示する。既定引数で切り替えない |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `jdbc/RowMapper.kt` | Insert 用の変換メソッドを追加 |
| `service/AdapterServiceImpl.kt` | Insert の呼び出しを差し替え |
| `jdbc/QueryBuilder.kt` | **変更なし** |
| `docs/` | 永続的ドキュメントへの影響なし |

## 8. 実機確認で分かったこと（スコープの変更）

当初「空の DATETIME / NUMBER は presence があるため `null` として正しく扱えている」として
スコープ外にしていたが、**これは `NOT NULL` かつ既定値を持つ列では誤りだった。**

`rowguid` と `modified_date` をマッピングした既存連携（`SQLServerCustomer`）で Insert を
試したところ、テキストだけを省いた段階では次のエラーになった。

```
DATA_SOURCE The SQL Error Number is 515, ...
Error is 'Cannot insert the value NULL into column 'ModifiedDate',
table 'AdventureWorksLT.SalesLT.Customer'; column does not allow nulls. INSERT fails.'
```

`rowguid` のエラー（8169）は解消したが、`ModifiedDate` が NULL で失敗する。
Issue #76 の受け入れ条件「`rowguid` をマッピングした既存連携でも Insert が成功し、
既定値が入る」を満たすには、**未入力の数値・日時・選択も省く必要がある。**

Insert には「既存の値を空にする」という操作が無いため、省いても失うものがない点は
テキストと同じ。そのため判定を「値が入っていないフィールド」に広げた。

| フィールド | 未入力の判定 |
|---|---|
| TEXT | `value` が空文字（presence が無いため空文字を未入力とみなす） |
| NUMBER | `hasValue()` が false |
| DATETIME | `hasValue()` が false |
| SELECTION | `hasValue()` が false（`Option` はメッセージ型なので presence を持つ） |

拡張後、同じ Insert が成功し既定値が入った（CustomerID 30132）。

| 列 | 結果 |
|---|---|
| `rowguid` | `A178B8E3-0F89-404C-A67A-B853BB2EAF03`（自動生成） |
| `ModifiedDate` | `2026-10-05 14:58:55.540`（`getdate()`） |
| `NameStyle` | `0`（`((0))`） |
| `Title`（NULL 許容・既定値なし） | `NULL` |

確認後、投入した行は削除した。

## 9. 代償（記録）

**`NOT NULL` で既定値も持たないテキスト列は、空のまま登録すると失敗するようになる。**

| 変更前 | 変更後 |
|---|---|
| 空文字を挿入して成功 | 列を省くため NULL 制約違反 |

`TextField.value` に presence が無いため「空文字を入れたい」と「未入力」を区別できず、
どちらかを選ぶしかない。自動生成列をマッピングした連携を救うことを優先した。

数値・日時・選択には代償がない。従来も NULL を送っていたため、`NOT NULL` 列では
同じく失敗していた。NULL 許容かつ既定値を持つ列では、NULL ではなく既定値が入る
ようになる（挙動の変化だが、未入力に対しては DB の意図に沿う）。
