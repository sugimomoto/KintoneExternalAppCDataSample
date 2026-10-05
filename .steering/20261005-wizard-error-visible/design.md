# ウィザードの失敗を画面に出す — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#64](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/64) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | step4 の算出結果を **sealed interface** で返す | 「進める」「主キーが無い」「取得に失敗」を型で分け、ビューが分岐を書き忘れられない | 例外を投げて `try/catch` する案 → 失敗の種類を型で区別できず、メッセージの組み立てが散る |
| D-2 | 主キーが無い場合を**専用のケース**にする | 「連携できない理由」が他の失敗と違う。kintone 側の仕様に起因するもので、再試行しても直らない（AC-3） | 汎用エラーにまとめる案 → 「DB を直せばよい」のか「別のテーブルを選ぶ」のか判断できない |
| D-3 | 失敗時は **step3 の画面構造で**エラーを出す | 前のステップに戻れる状態を保つ（AC-4）。新しい画面を作ると導線が増える | 専用のエラーページを作る案 |
| D-4 | 例外メッセージは**分類せずマスクして出す** | ロケール依存のメッセージをパースしない（C-3、#19 の方針）。接続文字列が混ざる場合に備える（C-2） | 例外型で分類する案 |
| D-5 | `StatusPages` は**入れない** | 影響範囲が Web UI 全体に及ぶ。まずウィザードの失敗を見えるようにする（スコープ外） | 全体に例外ハンドラを入れる案 |
| D-6 | 結果型は **#66 で拡張できる形**にする | #66 で「列を選ぶ画面」に発展させる。`NoPrimaryKey` に候補列を持たせれば済む構造にしておく（C-5） | エラー文字列だけを返す案 → #66 で作り直しになる |

### 1.2 フロー

```
変更前:
  POST /syncs/new/step4
    findPrimaryKey が null → IllegalStateException → 未処理 → 500 → 真っ白

変更後:
  POST /syncs/new/step4
    → Step4Result を算出
        ├─ Ready(primaryKey, recordIdType, mappings) → step4 を描画（従来どおり）
        ├─ NoPrimaryKey(tableName)                   → 連携できない理由を step3 上に表示
        └─ Failed(reason)                            → マスク済みの理由を step3 上に表示
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/routes/Step4Result.kt` | 新規 | step4 の算出結果（sealed interface） |
| `web/routes/TableWizardRoutes.kt` | 変更 | 例外を投げず `Step4Result` を返して分岐 |
| `web/views/TableWizardView.kt` | 変更 | step3 に失敗表示を描画できるようにする |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/WizardErrorTest.kt` | 新規 | 失敗表示の描画（理由・テーブル名・根拠・マスク） |

ルートの分岐（DB アクセスを伴う）は実機で確認する。

---

## 3. データ構造

```kotlin
/**
 * step4 の算出結果。
 *
 * 例外ではなく型で返す。主キーが無い場合は「連携できない」という確定した結論で、
 * 再試行しても変わらない。取得の失敗とは利用者の次の行動が違う。
 */
sealed interface Step4Result {
    data class Ready(
        val primaryKey: String,
        val recordIdType: RecordIdType,
        val mappings: List<WizardMapping>,
    ) : Step4Result

    /**
     * 主キーが検出できなかった。
     *
     * [candidates] は #66 でレコード ID 列を選ばせるために使う予定の候補列。
     * 本 Issue では表示しない。
     */
    data class NoPrimaryKey(val tableName: String, val candidates: List<ColumnInfo>) : Step4Result

    /** メタデータ取得などに失敗した。[reason] はマスク済み。 */
    data class Failed(val reason: String) : Step4Result
}
```

`candidates` を最初から持たせるのは、#66 で `NoPrimaryKey` の中身だけ使えばよくなるため
（D-6）。本 Issue では描画しない。

---

## 4. 実装

### 4.1 ルート

```kotlin
val result = runCatching { computeStep4(jdbc, connectionName, tableName, schema, selectedColumns) }
    .getOrElse { cause ->
        log.warn(cause) { "step4 の算出に失敗しました: $tableName" }
        // 例外メッセージに接続文字列が含まれる場合に備えてマスクする。
        // ロケール依存のメッセージは分類しない (#19 の方針)。
        Step4Result.Failed(ConnectionStringMasker.mask(cause.message ?: "メタデータの取得に失敗しました"))
    }

when (result) {
    is Step4Result.Ready -> call.respondHtml { wizardStep4View(...) }
    is Step4Result.NoPrimaryKey, is Step4Result.Failed ->
        call.respondHtml { wizardStep3View(..., error = wizardErrorOf(result)) }
}
```

`respondHtml` は既定で 200 を返す。利用者が操作を続けられる状態を保つため、
失敗でも 200 にする（AC-1）。

### 4.2 失敗メッセージ

```kotlin
/**
 * 主キーが無い場合の案内。
 *
 * kintone の「外部システムのアプリ化」は `GetCapability` で `RecordIdType` を、
 * `GetSchema` で `RecordIdFieldDefinition` を要求する。どちらも必須メソッドなので、
 * レコード番号に使える列が無いテーブルは**読み取り専用であっても連携できない**。
 */
private fun noPrimaryKeyMessage(tableName: String) =
    "テーブル \"$tableName\" には主キーが定義されていないため、連携を作成できません。" +
        "kintone の「外部システムのアプリ化」はレコード番号を必須とするため、" +
        "レコード番号に使える列が無いテーブル・ビューは読み取り専用でも連携できません。" +
        "主キーを持つ別のテーブルを選び直してください。"
```

### 4.3 ビュー

`wizardStep3View` に `error: String? = null` を足し、あれば `warning-banner` で出す。
既定値付きなので既存の呼び出しは変更不要。

`wizardStep3View` は現在 5 引数（+レシーバ 1）なので、1 つ足すと `LongParameterList`
（閾値 6、レシーバを含めて数える）に引っかかる。**`WizardStep3Content` のような
データクラスで束ねる**か、`error` を `TableInfo` と同様にまとめる必要がある。
→ `columns` と `error` を `Step3Content(columns, error)` に束ねる。

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| step4 の失敗 | 500 → 200 + 画面に理由 | AC-1, AC-4 |
| 主キーが無いテーブル | 連携できない理由と根拠が出る | AC-2, AC-3 |
| メタデータ取得の失敗 | マスク済みの理由が出る | AC-5, AC-6 |
| 正常なテーブル | **変更なし** | AC-7 |
| `wizardStep3View` の引数 | `Step3Content` で束ねる（detekt 対策も兼ねる） | — |
| #66 | `NoPrimaryKey.candidates` をそのまま使える | D-6 |
| `docs/` | 影響なし | — |

---

## 6. テスト設計

### 6.1 `WizardErrorTest`（描画）

- 主キーが無い場合の案内にテーブル名が含まれる
- 案内に「レコード番号」が必須である旨が含まれる
- 取得失敗の理由が表示される
- 接続文字列を含む理由がマスクされる
- エラーが無ければバナーを描画しない

### 6.2 実機確認

1. 主キーの無いビュー／テーブルで step4 を叩き、200 で理由が出る（AC-1〜AC-4）
2. `SalesLT.Customer` は従来どおり step4 に進む（AC-7）
3. 存在しないテーブル名で失敗理由が出る（AC-5）

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-4 | D-3, §4.1 | §6.2-1 |
| AC-2 / AC-3 | D-2, §4.2 | §6.1, §6.2-1 |
| AC-5 / AC-6 | D-4, §4.1 | §6.1, §6.2-3 |
| AC-7 | §4.1 | §6.2-2 |
| AC-8 | — | `./gradlew test detekt` |
