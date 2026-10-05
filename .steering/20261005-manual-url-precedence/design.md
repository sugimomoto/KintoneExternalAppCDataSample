# 手動 URL 欄の優先順位の修正 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#59](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/59) |
| 作成日 | 2026-10-05 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **併記時は事前入力しない** | 原因は「埋めた値がそのまま送り返されて黙って勝つ」こと。埋めるのをやめれば、プロパティ編集が素通りする | `build` の優先規則を変える案 → 逃げ道（意図した直接入力）が機能しなくなる（C-1） |
| D-2 | 優先規則 `build` は**変えない** | 「手動 URL が非空なら優先」は意図どおり。問題は「非空になってしまう」側 | ハンドラ側で既存 URL と比較する案 → 「同じ値を意図して入れた」場合に無視され、挙動が読めない |
| D-3 | 判断は**ビュー側**で行う | 事前入力するかはフォームの構成の問題。ハンドラはフォームの値だけを見る（C-2） | `build` に「事前入力かどうか」を伝える案 → ハンドラが画面構成を知ることになる |
| D-4 | `properties.isEmpty()` の経路は**従来どおり事前入力** | プロパティフォームが無い場合、手動 URL 欄が唯一の編集手段。空にすると既存の接続文字列を手で打ち直すことになる（C-4, AC-4） | 一律で空にする案 → 回帰になる |
| D-5 | 併記時は**現在の接続文字列をマスクして併記**し、空欄の意味を案内する | 空欄を見て「消えるのでは」と不安になる。参照用に見せれば必要な部分をコピーできる（AC-6） | 何も出さない案 |
| D-6 | `manualUrlField` に **`prefill: Boolean`** を渡す | 呼び出し 2 箇所の意図を引数で明示する。関数を 2 つに分けるより差分が小さい | 関数を分ける案 |

### 1.2 変更前後

```
変更前（併記時）:
  [プロパティ欄 ... Server=sqlserver-sample を NEW-SERVER に変更]
  JDBC 接続文字列 (直接入力): [jdbc:sql:...Server=sqlserver-sample;...]  ← 事前入力
  → 保存すると手動 URL が勝ち、Server の変更が消える

変更後（併記時）:
  [プロパティ欄 ... Server を NEW-SERVER に変更]
  JDBC 接続文字列 (直接入力、任意): [                    ]  ← 空
    空欄のままなら上のプロパティから組み立てます。
    入力した場合はその値が優先されます。
    現在の値: jdbc:sql:AuthScheme=Password;Server=sqlserver-sample;...Password=***;
  → 保存するとプロパティの変更が反映される
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/ConnectionsView.kt` | 変更 | `manualUrlField(existingValues, prefill)` に。併記時は `prefill = false` + 案内と現在値（マスク済み） |
| `web/routes/ConnectionFormUrl.kt` | **変更なし** | C-1 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `routes/ConnectionFormUrlTest.kt` | 変更 | 優先順位を明示するテストを追加（空なら プロパティ / 非空なら手動が勝つ） |

| `web/views/PropertiesFormContentTest.kt` | 新規 | 描画の検証（事前入力の有無・案内文・マスク） |

**当初は「ビューの描画は実機で確認する」としていたが撤回した。** #60 で config 接続文字列に
切り替えた結果、縮退フォームが実行時にほぼ発生しなくなり（手元の全ドライバーで `config:` が
成功する）、併記パスを実機で再現できない。`propertiesFormContent` は `FlowContent` の
public な拡張関数なので `createHTML()` で描画を直接検証できる。

---

## 3. 実装

### 3.1 `propertiesFormContent`

```kotlin
if (result.properties.isEmpty()) {
    // プロパティフォームが作れないため、手動入力が唯一の編集手段。事前入力する。
    manualUrlField(existingValues, prefill = true)
    return
}

propertyCategories(result.properties, existingValues)

// 縮退時は取得漏れのプロパティを補えるよう、直接入力も併記する。
// **事前入力はしない。** 埋めた値はブラウザがそのまま送り返し、
// ConnectionFormUrl.build で無条件に優先されるため、プロパティ側の編集が
// 黙って捨てられていた (Issue #59)。
if (result.isDegraded) {
    manualUrlField(existingValues, prefill = false)
}
```

### 3.2 `manualUrlField`

```kotlin
private fun kotlinx.html.FlowContent.manualUrlField(
    existingValues: Map<String, String>,
    prefill: Boolean,
) {
    val current = existingValues[URL_VALUE_KEY].orEmpty()
    label {
        +if (prefill) "JDBC 接続文字列 (直接入力):" else "JDBC 接続文字列 (直接入力、任意):"
        textArea {
            name = "jdbc.url.manual"
            rows = "3"
            if (prefill) +current
        }
    }
    if (!prefill) {
        small(classes = "muted") {
            +"空欄のままなら上のプロパティから組み立てます。入力した場合はその値が優先されます。"
        }
        if (current.isNotBlank()) {
            // 参照用。マスク済みの値のみを出すこと (既存方針)。
            p { small(classes = "muted") { +"現在の値: "; code { +ConnectionStringMasker.mask(current) } } }
        }
    }
}
```

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 併記時（縮退フォーム）の編集 | プロパティの変更が反映される | AC-1 |
| 併記時の手動 URL 欄 | 空欄になる。案内と現在値（マスク済み）が出る | AC-2, AC-6 |
| 手動 URL 欄に入力した場合 | **変更なし**。その値が優先される | AC-3 |
| `properties.isEmpty()` の経路 | **変更なし**。従来どおり事前入力 | AC-4 |
| 完全なプロパティフォーム | 手動 URL 欄が描画されないため**変更なし** | AC-5 |
| `ConnectionFormUrl.build` | **変更なし** | C-1 |
| `docs/` | 影響なし | — |

---

## 5. テスト設計

### 5.1 `ConnectionFormUrlTest`（追加）

優先順位を仕様として固定する。

- 手動 URL が空なら `prop.*` から組み立てる
- 手動 URL が非空ならそれを返し、`prop.*` は無視される（意図した挙動であることを明示）
- 手動 URL が空白のみならプロパティから組み立てる

### 5.2 ビュー描画テスト（`PropertiesFormContentTest`）

- 併記時は手動 URL 欄を事前入力しない（AC-2）
- 併記時は空欄の意味を案内する（AC-6）
- 併記時は現在の値をマスクして参照できる（AC-6）
- プロパティが無いときは従来どおり事前入力する（AC-4）
- 完全取得のときは手動 URL 欄を描画しない（AC-5）

### 5.3 実機確認

1. 完全取得の接続（SQL Server / Salesforce）で手動 URL 欄が出ないこと（AC-5）
2. `config.db` をバックアップと突き合わせる

AC-1（プロパティ変更が反映される）は `ConnectionFormUrlTest` の優先順位テストと
§5.2 の「事前入力しない」の組み合わせで担保する。両方が成り立てば、ブラウザが
空欄を送り返してもプロパティから組み立てられる。

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1, §3.1 | §5.2-2 |
| AC-2 | D-1 | §5.2-1 |
| AC-3 | D-2 | §5.1, §5.2-3 |
| AC-4 | D-4 | §5.2 |
| AC-5 | §4 | §5.2-5 |
| AC-6 | D-5, §3.2 | §5.2-4 |
| AC-7 | §5.1 | — |
| AC-8 | — | `./gradlew test detekt` |
