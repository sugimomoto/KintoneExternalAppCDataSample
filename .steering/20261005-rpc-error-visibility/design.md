# RPC の例外を見えるようにする — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#70](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/70) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **共通のラッパ関数**で囲む | 9 メソッドに同じ try/catch を書くと、1 つ書き忘れた時点で同じ事故が再発する（C-5）。ラッパなら漏れが目で分かる | 各メソッドに try/catch を複製する案 → 今回の原因そのもの |
| D-2 | 説明文の生成を **`RpcErrors` の純粋関数**にする | gRPC を起動せずテストできる（C-3）。マスクとフォールバックの両方を 1 箇所に閉じ込める | 各所で `e.message ?: ...` を書く案 |
| D-3 | `StatusException` は**そのまま通す** | `INVALID_ARGUMENT` / `UNIMPLEMENTED` を `INTERNAL` に化けさせない（C-1） | 全部 INTERNAL にする案 |
| D-4 | 例外メッセージを **`ConnectionStringMasker` に通す** | JDBC の例外に接続文字列が混ざると kintone の画面に資格情報が出る。既存の `select` にもこの穴があった | 生のまま渡す案（現状） |
| D-5 | 例外は**分類しない** | ロケール依存のメッセージをパースしない（C-2、#19 の方針）。`INTERNAL` に統一する | 例外型でステータスを振り分ける案 |
| D-6 | ラッパは **`inline` の非 suspend 関数**にする | RPC 本体は suspend だが、内部は同期的な JDBC 呼び出し。suspend ラムダを受け取る必要がない | suspend ラッパにする案 → 不要に複雑 |

### 1.2 変更前後

```
変更前:
  select  … try/catch あり（ログ + INTERNAL 変換、マスクなし）
  他 8 つ … try/catch なし → 生の例外 → code: Unknown / メッセージ空 / ログなし

変更後:
  全 9 メソッド … withRpcErrors("Update") { ... } で統一
      StatusException   → ログ(warn) + そのまま
      それ以外          → ログ(error, スタックトレース付き) + INTERNAL(マスク済みメッセージ)
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `service/RpcErrors.kt` | 新規 | 説明文の生成（マスク + フォールバック） |
| `service/AdapterServiceImpl.kt` | 変更 | 全 RPC を `withRpcErrors` で囲む |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `service/RpcErrorsTest.kt` | 新規 | 説明文の生成（マスク・null・空文字・クラス名） |

ラッパの分岐（`StatusException` を通す／それ以外を変換する）も `RpcErrors` 側に
寄せてテストする。

---

## 3. 実装

### 3.1 `RpcErrors`

```kotlin
object RpcErrors {

    /**
     * 例外を kintone 側に返す説明文に変換する。
     *
     * **接続文字列が混ざる場合に備えてマスクを通す。** JDBC の例外メッセージには
     * 接続文字列が含まれることがあり、kintone の画面は顧客も見る。
     *
     * メッセージが無い例外（`NoWhenBranchMatchedException` 等）では例外クラス名を使う。
     * 空文字のままでは `code: Unknown` と区別がつかない。
     *
     * **分類はしない。** ロケール依存のメッセージをパースすると実行環境で壊れる
     * (#19 の教訓)。
     */
    fun descriptionOf(cause: Throwable): String {
        val raw = cause.message?.takeIf { it.isNotBlank() }
            ?: cause::class.simpleName
            ?: "不明なエラー"
        return ConnectionStringMasker.mask(raw)
    }

    /** 想定外の例外を `INTERNAL` に変換する。原因は `withCause` で保持する。 */
    fun internal(cause: Throwable): StatusException =
        Status.INTERNAL.withDescription(descriptionOf(cause)).withCause(cause).asException()
}
```

### 3.2 ラッパ

```kotlin
/**
 * RPC の本体を囲み、例外を必ずログと説明文に乗せる。
 *
 * **9 メソッドすべてがこれを通ること。** 1 つでも素の実装が残ると、そのメソッドの
 * 例外が `code: Unknown` かつメッセージ空になり、ログにも出ない (Issue #70)。
 */
private inline fun <T> withRpcErrors(operation: String, block: () -> T): T =
    try {
        block()
    } catch (e: StatusException) {
        // 意図したステータス (INVALID_ARGUMENT / UNIMPLEMENTED 等) はそのまま返す。
        log.warn { "$operation returning status error: ${e.message}" }
        throw e
    } catch (e: Exception) {
        log.error(e) { "$operation failed with unexpected exception" }
        throw RpcErrors.internal(e)
    }
```

各メソッドは本体を `withRpcErrors("Update") { ... }` で囲むだけ。`select` の既存の
try/catch はこれに置き換える（挙動は同じ、マスクが加わる）。

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| `update` / `insert` / `delete` / `count` 等 | 例外がログに出て、原因が kintone に返る | AC-1, AC-2, AC-3 |
| `select` | ログと変換は従来どおり。**マスクが加わる** | AC-7, AC-6 |
| `StatusException` を投げる経路 | **変更なし**。そのまま返る | AC-4 |
| エラーのステータスコード | `Unknown` → `INTERNAL` に変わる | 意図した変更 |
| `docs/` | 影響なし | — |

### kintone 側の表示の変化

```
変更前: code: Unknown, message:
変更後: code: Internal, message: DATA_SOURCE The SQL Error Number is 241 ... 'Conversion failed ...'
```

---

## 5. テスト設計

### 5.1 `RpcErrorsTest`

- 例外メッセージがそのまま説明文になる
- 接続文字列を含むメッセージがマスクされる
- メッセージが null の例外では例外クラス名になる
- メッセージが空文字・空白の例外でも空にならない
- `internal()` が `INTERNAL` を返し、原因を保持する
- マスク対象の値（`Password=` 等）が説明文に残らない

### 5.2 実機確認

1. `modified_date` を含む Update で、**メッセージ付きのエラー**が返る（AC-1, AC-2）
2. ログに例外とスタックトレースが出る（AC-1）
3. `select` が従来どおり動く（AC-7）
4. 主キーを省いた Update で `INVALID_ARGUMENT` がそのまま返る（AC-4）

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 / AC-3 | D-1, §3.2 | §5.2-1, §5.2-2 |
| AC-4 | D-3 | §5.2-4 |
| AC-5 | D-2, §3.1 | §5.1 |
| AC-6 | D-4 | §5.1 |
| AC-7 | §3.2 | §5.2-3 |
| AC-8 | §5.1 | — |
| AC-9 | — | `./gradlew test detekt` |
