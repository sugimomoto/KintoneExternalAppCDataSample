# データソース一覧への削除ボタン追加 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#36](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/36) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **参照中なら削除を拒否する** | 削除ボタンを足すだけでは連携を壊す動線を UI に増やすことになる。参照整合性をアプリ層で守る | 警告だけ出して削除する案 → 壊れた連携が残り、原因が画面から分からない |
| D-2 | 参照チェックを **`JdbcReferenceIndex` という純粋な関数群**に切り出す | DB に触らずにテストできる（C-2）。`ConfigSource` は変更しない（C-1） | ルートハンドラに直接ループを書く案 → テストに DB が必要になる |
| D-3 | 参照元は **`ConfigSource.listTables` + `sharedJdbcRefOf`** で集める | 既存 API の組み合わせで足りる。インターフェースを増やさない | `ConfigSource` に `tablesReferencing(name)` を追加する案 |
| D-4 | 削除拒否は**一覧画面にエラーを出して返す** | 既存の削除ハンドラはリダイレクトのみで、エラーを見せる導線が無い。一覧に戻りつつ理由を出す | `respondText` でプレーンテキストを返す案 → 画面から外れて戻れない |
| D-5 | 確認メッセージは **`DeleteConfirm` に追加**する | #8 で JS エスケープをここに閉じ込めた。2 箇所に書かない（C-4） | ビューに直接書く案 → #8 で直した欠陥を再導入する |
| D-6 | 削除ボタンは**常に表示**する | 参照中かどうかを一覧描画時に判定すると、全連携の `jdbc_ref` を毎回引くことになる。押したときに判定する | 参照中は削除ボタンを出さない案 |

### 1.2 フロー

```
[一覧] 削除ボタン
  → JS confirm （キャンセルなら何もしない: AC-3）
  → POST /connections/{name}/delete
      → 全連携の jdbc_ref を走査
          ├─ 参照あり → 削除せず一覧を再描画 + 参照元の連携名を表示 (AC-5, AC-6, AC-7)
          └─ 参照なし → deleteSharedJdbcConfig → /connections へリダイレクト (AC-4)
```

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `config/JdbcReferenceIndex.kt` | 新規 | 連携名 → `jdbc_ref` の対応から、参照元を引く純粋な関数 |
| `web/views/DeleteConfirm.kt` | 変更 | `connectionDeleteOnSubmit` を追加 |
| `web/views/ConnectionsView.kt` | 変更 | 操作列に削除ボタン。削除拒否時のエラー表示 |
| `web/routes/ConnectionsRoutes.kt` | 変更 | 削除ハンドラに参照チェック |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `config/JdbcReferenceIndexTest.kt` | 新規 | 参照の有無・複数参照・大文字小文字・null の扱い |
| `web/views/DeleteConfirmTest.kt` | 変更 | データソース用メッセージのエスケープ |

---

## 3. データ構造

```kotlin
/**
 * 連携がどのデータソース接続を参照しているかの索引。
 *
 * `tables.jdbc_ref` に外部キー制約が無いため、削除前の参照チェックを
 * アプリ層で行う。DB に触らずテストできるよう純粋な関数にしている。
 */
object JdbcReferenceIndex {
    /** [refsByTable] （連携名 → 参照先、未参照は null）から [connectionName] の参照元を返す。 */
    fun tablesReferencing(refsByTable: Map<String, String?>, connectionName: String): List<String>
}
```

参照元が空なら削除可。そうでなければ拒否する。

---

## 4. 実装

### 4.1 参照チェック

```kotlin
fun tablesReferencing(refsByTable: Map<String, String?>, connectionName: String): List<String> =
    refsByTable
        .filter { (_, ref) -> ref != null && ref.equals(connectionName, ignoreCase = true) }
        .keys
        .sorted()
```

- **大文字小文字を無視する。** 接続名は利用者が入力する識別子で、`jdbc_ref` との
  突き合わせを厳密一致にすると、表記違いの参照を見落として削除を許してしまう
- 戻り値はソートする。画面表示の順序を安定させ、テストを書きやすくする

### 4.2 削除ハンドラ

```kotlin
post("/connections/{name}/delete") {
    val name = call.parameters["name"]!!
    val refs = JdbcReferenceIndex.tablesReferencing(referenceMapOf(ctx), name)
    if (refs.isNotEmpty()) {
        return@post call.respondHtml { connectionsListView(ctx, blockedDelete = BlockedDelete(name, refs)) }
    }
    ctx.configSource.deleteSharedJdbcConfig(name)
    call.respondRedirect("/connections")
}

/** 連携名 → 参照先データソース名。 */
private fun referenceMapOf(ctx: AppContext): Map<String, String?> =
    ctx.configSource.listTables().associateWith { ctx.configSource.sharedJdbcRefOf(it) }
```

### 4.3 ビュー

`connectionsListView` に `blockedDelete: BlockedDelete? = null` を足す。
既定値付きなので既存の呼び出し (`get("/connections")`) は変更不要。

```kotlin
data class BlockedDelete(val connectionName: String, val referencingTables: List<String>)
```

表示は `warning-banner` （#31 の有効化結果画面と同じクラス）を流用する。

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| `POST /connections/{name}/delete` | 参照中は削除されなくなる | 意図した変更（D-1）。現在 `jdbc_ref` を持つ連携は 0 件なので既存環境の挙動は変わらない |
| `connectionsListView` | 引数が 1 つ増える（既定値あり） | 既存呼び出しはそのまま動く |
| `ConfigSource` とその実装 | **変更なし** | C-1 |
| inline JDBC の連携 | `sharedJdbcRefOf` が `null` を返すため参照元に挙がらない | 正しい。inline は接続設定を参照していない |
| `docs/` | 影響なし | 基本設計・データモデルの変更を伴わない |

---

## 6. テスト設計

### 6.1 `JdbcReferenceIndexTest`

- 参照している連携が 1 件あれば、その名前が返る
- 複数参照していれば全部返る（AC-7）、順序はソート済み
- 参照が無ければ空リスト
- `jdbc_ref` が null の連携（inline JDBC）は挙がらない
- 大文字小文字が違っても参照として扱う
- 連携が 0 件なら空リスト

### 6.2 `DeleteConfirmTest`（追加）

- データソース用メッセージに接続名が含まれる
- `'` を含む接続名がエスケープされる（AC-8）

### 6.3 実機確認

1. 一覧に削除ボタンが出る（AC-1, AC-9）
2. 参照の無い接続が削除できる（AC-4）
3. `jdbc_ref` を持つ連携を仕込み、参照中の接続が削除されず参照元が出る（AC-5, AC-6）

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-9 | §4.3 | §6.3 |
| AC-2 / AC-8 | D-5 | §6.2 |
| AC-3 | JS `confirm` | §6.3（目視） |
| AC-4 | §4.2 | §6.3 |
| AC-5 / AC-6 / AC-7 | D-1, §4.1 | §6.1, §6.3 |
| AC-10 | §6.1 | — |
| AC-11 | — | `./gradlew test detekt` |
