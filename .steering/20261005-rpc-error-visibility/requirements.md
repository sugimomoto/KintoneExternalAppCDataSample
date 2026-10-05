# RPC の例外を見えるようにする — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | rpc-error-visibility |
| 作成日 | 2026-10-05 |
| Issue | [#70](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/70) |
| 目的 | Adapter の全 RPC で例外の原因が kintone 側とログに出るようにする |

---

## 1. 背景

### 1.1 課題

kintone からレコードを更新すると、原因が一切分からないエラーが返る。

```
Adapterでエラーが発生しました: code: Unknown, message:  (operation_id: 3155cc39-...)
```

**メッセージが空で、ログにも何も出ない。**

### 1.2 原因

**9 つの RPC メソッドのうち `select` だけ**が例外を捕まえている。

```kotlin
// service/AdapterServiceImpl.kt:156
} catch (e: StatusException) {
    log.warn { "Select returning status error: ${e.message}" }
    throw e
} catch (e: Exception) {
    log.error(e) { "Select failed with unexpected exception" }
    throw Status.INTERNAL.withDescription(e.message ?: e::class.simpleName).withCause(e).asException()
}
```

`getCapability` / `getSchema` / `insert` / `update` / `delete` / `count` / `search` /
`aggregate` にはこれがない。生の例外が grpc-kotlin に渡るため
**`code: Unknown` かつメッセージ空**になり、ログにも出ない。

### 1.3 実害

実際にはドライバーが有用なメッセージを返していた。

```
java.sql.SQLException: DATA_SOURCE The SQL Error Number is 241, Severity is 16,
Error is 'Conversion failed when converting date and/or time from character string.'
```

**この情報が捨てられていた。** 原因を特定するには `update` が発行する SQL を手作業で
再現する必要があった（`UPDATE [SalesLT].[Customer] SET [ModifiedDate] = ?` に
`java.sql.Timestamp` を束縛）。

### 1.4 既存実装の問題点

`select` は `e.message` をそのまま `withDescription` に渡している。JDBC の例外
メッセージに接続文字列が含まれる場合、**kintone 側の画面に資格情報が出る**。
全メソッドに広げる際にマスクを通す形へ揃える必要がある。

## 2. 目的

全 RPC メソッドで例外の原因が kintone 側とログに出るようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | 全 RPC メソッドで例外を捕まえてログに出す |
| F-2 | `StatusException` はそのまま通す（意図したステータスを壊さない） |
| F-3 | それ以外は `Status.INTERNAL` にメッセージを添えて返す |
| F-4 | メッセージが null の例外では例外クラス名を使う |
| F-5 | 例外メッセージを `ConnectionStringMasker` に通す（`select` の既存実装も含めて） |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| DATETIME の更新が失敗する不具合 | [#71](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/71) で別途。本作業はそれが見えるようにするもの |
| エラーメッセージの分類・対訳 | ロケール依存のメッセージは分類しない方針（#19） |
| `operation_id` との紐付け | kintone 側が生成する ID で、Adapter に渡ってきていない |
| リトライ可否の判定 | ステータスコードで表現するには例外の分類が必要。#19 の方針に反する |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、更新が失敗した理由を知りたい。`code: Unknown` だけでは手の打ちようがないため |
| US-2 | このサンプルを引き継ぐ開発者として、失敗がログに残ってほしい。再現手順を組み立て直さずに原因へ辿り着きたいため |
| US-3 | 環境を人に見せる担当者として、エラーに資格情報が出ないでほしい。kintone の画面は顧客も見るため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | `update` で例外が発生したとき、ログに例外とスタックトレースが出る |
| AC-2 | kintone 側に返るエラーに原因のメッセージが含まれる（空でない） |
| AC-3 | `insert` / `delete` / `count` / `getSchema` / `getCapability` / `search` / `aggregate` も同様に扱われる |
| AC-4 | 意図した `StatusException`（`INVALID_ARGUMENT` 等）はそのまま返る |
| AC-5 | メッセージが null の例外でも例外クラス名が返る |
| AC-6 | 例外メッセージに接続文字列が含まれる場合はマスクされる |
| AC-7 | `select` の既存の挙動（ログ + INTERNAL 変換）は維持される |
| AC-8 | 変換ロジックに単体テストがある |
| AC-9 | `./gradlew test` が通り、detekt が 82 件以下である |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | `StatusException` は変換しない。意図したステータスコードを壊さない |
| C-2 | 例外メッセージは分類しない（#19 の方針）。マスクしてそのまま渡す |
| C-3 | 変換ロジックは純粋関数として切り出し、gRPC の起動なしでテストできること |
| C-4 | 既存の `select` の挙動を変えない（マスクの追加を除く） |
| C-5 | 9 メソッドすべてに同じ扱いを適用する。一部だけ残すと同じ事故が再発する |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `service/AdapterServiceImpl.kt` | 全 RPC メソッドに例外ハンドリングを適用 |
| `service/RpcErrors.kt` | 新規。例外 → `StatusException` の変換 |
| `docs/` | 永続的ドキュメントへの影響なし |
