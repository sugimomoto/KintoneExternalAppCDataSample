# 手動 URL 欄の優先順位の修正 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | manual-url-precedence |
| 作成日 | 2026-10-05 |
| Issue | [#59](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/59) |
| 目的 | データソース編集でプロパティの変更が黙って破棄されないようにする |

---

## 1. 背景

### 1.1 課題

データソースの編集画面でプロパティを変更して保存しても**変更が反映されない**。
保存は HTTP 302 で成功したように見え、**警告も出ない**。

### 1.2 原因

編集画面の「JDBC 接続文字列 (直接入力)」テキストエリアが**既存の接続文字列で
事前入力**されており、保存時にそれが無条件で最優先される。

```kotlin
// web/views/ConnectionsView.kt:433
private fun kotlinx.html.FlowContent.manualUrlField(existingValues: Map<String, String>) {
    textArea {
        name = "jdbc.url.manual"
        +(existingValues["__url__"] ?: "")   // ← 既存の接続文字列を埋めている
    }
}
```

```kotlin
// web/routes/ConnectionFormUrl.kt:33
fun build(jdbcPrefix: String, form: Parameters, fallbackUrl: String? = null): String {
    form[MANUAL_URL_FIELD]?.takeIf { it.isNotBlank() }?.let { return it }   // ← 無条件で勝つ
    ...
}
```

ブラウザは事前入力された値をそのまま送信するため、利用者が `prop.Server` を変更しても
`jdbc.url.manual` が勝ち、**プロパティ側の編集はすべて捨てられる**。

### 1.3 再現（実測）

```
手動 URL 欄も送る（ブラウザと同じ）:
  prop.Server=NEW-SERVER + jdbc.url.manual=<既存の接続文字列>
  → 保存後: Server=sqlserver-sample   ❌ 変更が消える

手動 URL 欄を送らない:
  prop.Server=NEW-SERVER
  → 保存後: Server=NEW-SERVER          ✅ 反映される
```

### 1.4 描画条件

手動 URL 欄は常に出るわけではない。

```kotlin
if (result.properties.isEmpty()) {
    manualUrlField(existingValues)   // プロパティフォームが作れない場合（唯一の編集手段）
    return
}
propertyCategories(result.properties, existingValues)
if (result.isDegraded) {
    manualUrlField(existingValues)   // ← 縮退時は併記。ここで踏む
}
```

`#60` で config 接続文字列に切り替えたため SQL Server は縮退しなくなったが、
**縮退する状況は依然あり得る**（`config:` が効かないドライバー、非 CData ドライバー）。
原因が残っている限り同じ事故が起きる。

## 2. 目的

プロパティフォームと手動 URL 欄が併記される場合に、プロパティの編集が黙って
破棄されないようにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | プロパティフォームが併記される場合は手動 URL 欄を事前入力しない |
| F-2 | 手動 URL 欄が空なら、プロパティから接続文字列を組み立てる（既存動作） |
| F-3 | 利用者が手動 URL 欄に入力した場合は、その値を優先する（既存動作・逃げ道の維持） |
| F-4 | プロパティフォームが一切作れない場合は、従来どおり事前入力する（唯一の編集手段） |
| F-5 | 併記時の手動 URL 欄に、現在の接続文字列はマスク済みで参照できる旨を案内する |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| 手動 URL とプロパティのマージ | どちらを正とするか一意に決められない。優先順位を明示する方が予測しやすい |
| 保存内容の差分確認画面 | 本件の原因は「事前入力が黙って勝つ」ことで、確認画面は別の改善 |
| 縮退フォームそのものの解消 | #60 で主要な原因は解消済み。残る縮退は別途 |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | 検証を進める担当者として、プロパティを変更して保存したら反映されてほしい。黙って捨てられると、設定したつもりで進めて後段で原因不明の失敗に遭うため |
| US-2 | 検証を進める担当者として、プロパティフォームに無い項目は接続文字列で補いたい。縮退時の逃げ道は残してほしいため |
| US-3 | 検証を進める担当者として、手動 URL 欄が空のときに何が保存されるのか分かるようにしてほしい。空欄を見て「消えてしまうのでは」と不安になるため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | 縮退フォームの接続でプロパティを変更して保存すると、変更が反映される |
| AC-2 | 併記時の手動 URL 欄が事前入力されていない |
| AC-3 | 手動 URL 欄に利用者が入力した場合は、その値が優先される |
| AC-4 | プロパティフォームが作れない接続では、従来どおり既存の接続文字列が事前入力され編集できる |
| AC-5 | 完全なプロパティフォームが出る接続（Salesforce 等）は従来どおり動く |
| AC-6 | 併記時に、空欄のままならプロパティから組み立てられる旨の案内が出る |
| AC-7 | 優先順位の判定に単体テストがある |
| AC-8 | `./gradlew test` が通り、detekt がベースライン 84 件のままである |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | `ConnectionFormUrl.build` の「手動 URL が非空なら優先」という規則は変えない。変えると逃げ道が機能しなくなる |
| C-2 | 事前入力するかどうかの判断は**ビュー側**で行う。ハンドラはフォームの値だけを見る |
| C-3 | 画面に接続文字列を出す場合は必ず `ConnectionStringMasker` を通す（既存方針） |
| C-4 | `properties.isEmpty()` の経路（唯一の編集手段）の挙動は変えない |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `web/views/ConnectionsView.kt` | `manualUrlField` に事前入力の可否を渡す。併記時の案内文を追加 |
| `web/routes/ConnectionFormUrl.kt` | **変更なし**（C-1） |
| `web/routes/ConnectionsRoutes.kt` | **変更なし** |
| `docs/` | 永続的ドキュメントへの影響なし |
