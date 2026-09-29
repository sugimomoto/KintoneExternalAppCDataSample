# 連携一覧画面への削除ボタン追加 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | syncs-list-delete-button |
| 対応 Issue | [#8](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/8) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 削除ボタンは **`tableActions()` に追加**する | 一覧と詳細の両方がこの関数を使っているため、1 箇所の変更で両画面に出る | 一覧のテーブル描画側 (`syncTable`) にだけ追加する案 → 詳細画面との文言・挙動がずれる |
| D-2 | **詳細画面の独立した削除フォームを削除**する | D-1 により `tableActions()` が削除ボタンを持つため、詳細画面では**ボタンが 2 つ並ぶ**。重複を残すと確認文言の二重管理になる | 詳細画面側を残す案 → 同じ操作のボタンが 2 つ並ぶ |
| D-3 | 確認ダイアログの JS 式生成を **`DeleteConfirm` に切り出す** | 連携名を JS 文字列に埋め込むため、`'` や `\` を含む名前で式が壊れる（C-4）。現行の実装は素の文字列補間で**エスケープしていない**。純粋関数にしてテストで固定する | `TablesView` 内に private 関数で置く案 → ユニットテストできない |
| D-4 | 稼働状態で出し分けない | SSE は `.status` セルしか更新しないため、出し分けると表示が実態とずれる（F-3, AC-2） | 稼働中は隠す案 |
| D-5 | ボタンの並びは **開始/停止 → 削除** の順 | 破壊的な操作を右端に置き、誤クリックを減らす | 削除を左に置く案 |
| D-6 | `button.danger` の CSS は**変更しない** | 要求定義では「hover 状態を追加する」としていたが、実装前に確認したところ **`:hover` / `:active` とも既に定義済み**だった（別作業「Web UI の表示を統一する」= コミット `dff272b` で実装済み）。要求定義の前提が古くなっていた | 重複して定義する案 |

### 1.2 現行コードの構造

```
tablesListView (38)
  └─ syncTable (一覧テーブル)
       └─ tableActions(name, isActive)   ← ここに削除を追加 (D-1)

tableDetailView (128)
  └─ action-bar
       ├─ tableActions(name, active != null)   ← 同じ関数
       ├─ a "kintone と接続 →"
       ├─ a "ログを見る"
       ├─ a "編集"
       └─ form POST /syncs/{name}/delete       ← 削除する (D-2)
            onsubmit = "return confirm('連携 \"$name\" を...')"   ← エスケープなし (D-3)
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/DeleteConfirm.kt` | 新規 | 確認ダイアログの JS 式生成（internal・テスト可能） |
| `web/views/TablesView.kt` | 変更 | `tableActions()` に削除ボタン追加。詳細画面の独立削除フォームを削除 |
| `resources/static/app.css` | **変更なし** | `:hover` / `:active` は既に定義済み（D-6） |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/DeleteConfirmTest.kt` | 新規 | 文言・JS エスケープ |
| `browserTest/e2e/SyncsListE2ETest.kt` | 変更 | 一覧の削除ボタン表示とキャンセル挙動 |

---

## 3. 実装

### 3.1 `DeleteConfirm`

```kotlin
/**
 * 削除操作の確認ダイアログ (`onsubmit` 属性) を組み立てる。
 *
 * 連携名は利用者が入力した値をそのまま JS 文字列に埋め込むため、
 * `'` や `\` を含む名前で式が壊れる。生成をここに閉じ込めてエスケープを保証する。
 */
internal object DeleteConfirm {

    /** 連携削除の確認。Adapter / Agent コンテナ / agent.json も消えることを明示する。 */
    fun syncDeleteOnSubmit(syncName: String): String =
        onSubmit(
            "連携 \"${escapeJsString(syncName)}\" を削除しますか？" +
                "稼働中の Adapter と Agent コンテナを停止・削除し、agent.json も削除します。" +
                "この操作は取り消せません。",
        )

    private fun onSubmit(message: String): String = "return confirm('$message')"

    /** JS の単一引用符文字列として安全な形に変換する。 */
    internal fun escapeJsString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
```

`\` を先に置換する（後にすると二重エスケープになる）。

### 3.2 `tableActions`

```kotlin
private fun kotlinx.html.FlowContent.tableActions(name: String, isActive: Boolean) {
    if (isActive) { ... 停止 ... } else { ... 開始 ... }

    // 稼働状態で出し分けない。SSE は .status セルしか更新しないため、
    // 出し分けると表示が実態とずれる (Issue #8 F-3)。
    form(action = "/syncs/$name/delete", method = FormMethod.post, classes = "inline-form") {
        attributes["onsubmit"] = DeleteConfirm.syncDeleteOnSubmit(name)
        button(type = ButtonType.submit, classes = "danger") { +"削除" }
    }
}
```

### 3.3 CSS（変更なし）

`app.css` に以下が既に定義されている（`dff272b`）。本作業では触らない。

```css
/* Danger: 破壊的操作。hover でも赤系を保ち、白文字のコントラストを維持する */
button.danger, .button.danger, a.button.danger { background: var(--color-danger); ... }
button.danger:hover, ... { background: var(--color-danger-hover); ... }
button.danger:active, ... { background: var(--color-danger-hover); ... }
```

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 連携一覧の操作列 | ボタンが 1 → 2 個に増える | `inline-form` は横並びなので崩れない。実機で確認（C-5） |
| 連携詳細の action-bar | 独立削除フォームを消し、`tableActions` の削除ボタンに一本化 | ボタンの見え方は変わらない（同じ `danger` クラス） |
| 確認文言 | 「agent.json も削除」「取り消せません」を追記して具体化 | 詳細画面の文言も同時に変わる（一本化の結果） |
| `POST /syncs/{name}/delete` | 変更なし（C-1） | 変更なし |
| `app.js` の SSE | 操作列は更新しない。削除ボタンは出し分けないので影響なし | 変更なし |
| 既存 E2E | `E2E-03c` は詳細画面のリンクを見ており削除ボタンは見ていない | 影響なし |

---

## 5. テスト設計

### 5.1 `DeleteConfirmTest`

- `return confirm(...)` の形を返す
- メッセージに連携名が含まれる
- メッセージに Adapter / Agent コンテナ / agent.json の記載が含まれる（AC-3）
- シングルクォートを含む連携名でエスケープされる（C-4）
- バックスラッシュを含む連携名でエスケープされる
- 改行を含む連携名で式が壊れない
- エスケープの順序が正しい（`\'` が `\\'` にならない）

### 5.2 `SyncsListE2ETest`（追加）

- 一覧の行に削除ボタンが表示される（AC-1）
- 確認ダイアログでキャンセルすると削除されない（AC-4）

### 5.3 手動確認

| 確認項目 | 期待 |
|---|---|
| 一覧の操作列 | 「▶ 開始」/「⏸ 停止」＋「削除」が横並び |
| 削除ボタンの色 | 赤。hover でも赤が保たれる |
| 詳細画面 | 削除ボタンが 1 つだけ |
| 確認ダイアログ | 連携名と削除対象が読める |
| 稼働中の連携 | 削除ボタンが出る |

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1 §3.2 | §5.2, §5.3 |
| AC-2 | D-4 | §5.3 |
| AC-3 | §3.1 の文言 | §5.1 |
| AC-4 | ブラウザ標準の `confirm` | §5.2 |
| AC-5 | C-1（既存ハンドラ） | §5.3 |
| AC-6 | C-1（既存ハンドラ） | 既存挙動 |
| AC-7 | 既存の CSS（D-6） | §5.3 |
| AC-8 | 変更なし（`empty-state` は `syncTable` の外） | §5.3 |
| AC-9 | D-2（一本化後も同じ動作） | §5.3 |
| AC-10 | — | `./gradlew detekt test` |
