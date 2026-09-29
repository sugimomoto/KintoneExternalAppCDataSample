# 既定値を保存しない接続フォーム — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | no-default-prefill |
| 対応 Issue | [#27](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/27) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | **入力欄に既定値を埋めない。** 既定値は `placeholder` で見せるだけ | 空値は既に保存対象外（F-1）なので、埋めるのをやめるだけで解決する。比較処理が不要 | Issue 記載の「既定値と一致する値を除外する」案 → `true` / `on` のような表記揺れの正規化が必要で、ドライバーごとの差異に追従できない（C-1） |
| D-2 | 選択肢つきプロパティの「未指定」に**既定値を併記**する | 未選択のまま送らない代わりに、何が使われるか画面で分かるようにする（AC-5） | 単に「-- 未指定 --」のままにする案 → 既定値が分からない |
| D-3 | 真偽値を**チェックボックスから 3 択セレクトに変える** | チェックボックスは「未指定」を表現できない（F-3）。`未指定 / True / False` にする（AC-6） | チェックボックスのままにする案 → 未チェックが「False を明示」なのか「未指定」なのか区別できない |
| D-4 | `buildUrlFromForm` を**テスト可能な場所に移す** | 「未指定は保存しない」は本件の核心なので、ユニットテストで固定したい（AC-12）。現状は `ConnectionsRoutes` の private 関数 | private のまま実機確認だけで済ませる案 |
| D-5 | 保存済みの値は従来どおり入力欄に入れる | 編集時のデグレを避ける（AC-3, AC-7）。`existingValuesOf` の値は「利用者が設定した値」なので埋めてよい | 編集時も空にする案 → 保存済みの値が消える |
| D-6 | プール設定 (`pool.*`) は変更しない | ドライバー由来ではなく本アプリの設定（F-6, AC-10） | 一緒に空にする案 |

### 1.2 値の埋め方の変更

```
変更前: value = currentValue ?: prop.defaultValue ?: ""
変更後: value = currentValue ?: ""      /  placeholder = prop.defaultValue
```

| 入力種別 | 変更前 | 変更後 |
|---|---|---|
| テキスト / パスワード / 数値 | 既定値を `value` に入れる | `value` は保存値のみ。既定値は `placeholder` |
| 選択肢つき (`<select>`) | 既定値の選択肢を `selected` | 保存値があればそれを選択。無ければ「-- 未指定 (既定: X) --」 |
| 真偽値 | チェックボックス（既定値で `checked`） | **3 択セレクト**（未指定 / True / False） |

### 1.3 #14 の絞り込みとの関係

未入力でも絞り込みは正しく動く。`PropertyHierarchyResolver` の実効値が
「入力値 → 無ければ `Default`」だから（F-2）。

例: Salesforce の `AuthScheme` を未選択のままでも、既定値 `OAuth` で解決され
`User` / `Password` / `SecurityToken` は非表示のままになる。
このとき選択欄には「-- 未指定 (既定: OAuth) --」と出るので、
画面と絞り込み結果の食い違いが分かる形になっている（D-2 の狙い）。

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/routes/ConnectionFormUrl.kt` | 新規 | `buildUrlFromForm` / `propertyValuesOf` を移設（internal・テスト可能に） |
| `web/routes/ConnectionsRoutes.kt` | 変更 | 上記を参照するだけにする |
| `web/views/ConnectionsView.kt` | 変更 | 既定値を `placeholder` に。真偽値を 3 択セレクトに |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/routes/ConnectionFormUrlTest.kt` | 新規 | 未指定は保存しない・保存する値・直接入力の優先 |

---

## 3. 実装

### 3.1 `ConnectionFormUrl`

```kotlin
/**
 * 接続フォームの送信値から JDBC 接続文字列を組み立てる。
 *
 * **未入力のプロパティは接続文字列に含めない。** フォームは既定値を入力欄に
 * 埋めないため、送られてこないプロパティ = 利用者が触っていないプロパティになる
 * (Issue #27)。既定値まで保存すると、Windows 用の既定値
 * (`%APPDATA%\...`) が Linux コンテナに持ち込まれ、OAuth キャッシュパスの
 * 一本化 (Issue #11) も効かなくなる。
 */
internal object ConnectionFormUrl {
    fun build(jdbcPrefix: String, form: Parameters, fallbackUrl: String? = null): String
    fun propertyValues(form: Parameters): Map<String, String>
}
```

挙動は現行の `buildUrlFromForm` と同じ（直接入力が最優先、空値は除外）。
移設の目的はテスト可能にすること。

### 3.2 真偽値の 3 択セレクト

```kotlin
prop.type == PropertyType.BOOLEAN -> {
    select {
        propertyFieldTargets(isDependency, "change")
        name = "prop.${prop.propertyName}"
        option {
            this.value = ""
            +unspecifiedLabel(prop.defaultValue)
        }
        listOf("True", "False").forEach { v ->
            option {
                this.value = v
                if (v.equals(currentValue, ignoreCase = true)) selected = true
                +v
            }
        }
    }
}
```

`currentValue` のみで `selected` を決める（既定値では選択しない）。

### 3.3 「未指定」ラベル

```kotlin
/** 既定値があれば併記する。未入力のときに何が使われるか分かるようにする。 */
private fun unspecifiedLabel(defaultValue: String?): String =
    if (defaultValue.isNullOrBlank()) "-- 未指定 --" else "-- 未指定 (既定: $defaultValue) --"
```

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 新規作成した接続の接続文字列 | 大幅に短くなる。利用者が入力した項目だけになる | 意図した変更 |
| 既存の接続 | 開いた時点では保存済みの値が入るため表示は変わらない。**保存し直すと既定値の項目が落ちる** | C-2。README に記載 |
| 接続文字列プレビュー | 未入力の項目が出なくなる | 意図した改善（AC-8） |
| `#14` の絞り込み | 未入力でも既定値で解決される（F-2） | 実機で確認 |
| `existingValuesOf` | 変更なし。保存済み URL の解析はそのまま | 変更なし |
| 真偽値プロパティの送信値 | `on` → `True` / `False` / 空 に変わる | CData ドライバーは `True` / `False` を受け付ける |
| 接続テスト・保存 | `buildUrlFromForm` の移設のみ。挙動は同じ | 変更なし |

---

## 5. テスト設計

### 5.1 `ConnectionFormUrlTest`

- 入力されたプロパティだけが接続文字列に含まれる
- 空文字のプロパティは含まれない（**本件の核心**）
- プロパティが 1 つも無ければ `jdbc:<product>:` を返す
- `fallbackUrl` があればそれを返す
- `jdbc.url.manual` があれば最優先で返す
- `jdbc.url.manual` が空文字ならプロパティから組み立てる
- `prop.` 以外のフォーム項目（`name` / `pool.*`）を含めない
- `propertyValues` が `prop.` の接頭辞を除いた名前をキーにする

### 5.2 実機確認

| 確認項目 | 期待 |
|---|---|
| 新規作成画面のテキスト欄 | `value` が空で `placeholder` に既定値 |
| 選択肢つきプロパティ | 「-- 未指定 (既定: OAuth) --」が選択済み |
| 真偽値プロパティ | 3 択セレクトで「未指定」が選択済み |
| `AuthScheme` 未選択時の絞り込み | 既定値 OAuth で解決され `User` 等が出ない |
| プレビュー | 入力した項目だけが出る |
| 既存接続の編集画面 | 保存済みの値が入力欄に入る |

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1 + F-1 | §5.1, §5.2 |
| AC-2 | D-1（入力値は `value` に入る） | §5.1 |
| AC-3 | D-5 | §5.2 |
| AC-4 | AC-1 の結果として | §5.2 |
| AC-5 | D-1 の `placeholder` + D-2 のラベル | §5.2 |
| AC-6 | D-3 | §5.2 |
| AC-7 | D-5 | §5.2 |
| AC-8 | AC-1 の結果として | §5.2 |
| AC-9 | F-2 | §5.2 |
| AC-10 | D-6 | §5.2 |
| AC-11 | §3.1（直接入力が最優先） | §5.1 |
| AC-12 | D-4 + §5.1 | — |
| AC-13 | — | `./gradlew test` |
