# kintone ドメイン入力の削除 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#51](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/51) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 入力欄・保存経路・環境変数依存を**まとめて削除**する | ドメインは連携の動作に不要（参照は UI のリンク組み立てのみ）。機能上不要な値のために状態と経路を維持しない | 永続化を実装する案 → 持つ必要のない状態を増やす。`localStorage` 案 → 入力欄自体をなくすので保存対象がない |
| D-2 | 管理画面リンクを**パスの案内に置き換える** | ドメインが無いと URL を組み立てられない。ただ Step 2 の目的（kintone 側で何をするか伝える）はパスを示せば果たせる | リンクを消すだけの案 → Step 2 から手順の情報が失われる |
| D-3 | パスは `code` 要素で**コピーしやすく**出す | 利用者が自分のドメインに付けて開く前提。選択しやすい形にする | 平文で書く案 |
| D-4 | `authRejectedGuide()` の文言は「(下の Step 2 のリンク)」→ 案内を指す表現に直す | 存在しないリンクを指したままにしない（AC-4）。#21 で足した案内の意図は維持する | 文言をそのまま残す案 → 嘘の案内になる |
| D-5 | エンドポイントは**完全に削除**する（410 等を返さない） | 内部 UI 専用の経路で、外部から叩かれる想定がない。残すと「実装されている」と誤読される | 非推奨として残す案 |
| D-6 | Step 1 / Step 3 は触らない | 動作しているものを変更する理由がない（C-1） | Step 構成を作り直す案 |

### 1.2 変更前後の Step 2

```
変更前:
  Step 2: kintone 管理画面でコネクタを追加
    [ kintone ドメイン (例: example.cybozu.com) ]  ← 常に空（環境変数が渡っていない）
    [ 保存して開く ]                               ← 押しても保存も遷移もしない
    （「kintone 管理画面を開く ↗」は domain が空なので出ない）

変更後:
  Step 2: kintone 管理画面でコネクタを追加
    kintone の管理画面で「外部システムのアプリ化」を開き、この連携に対応する
    コネクタを追加してください。発行された接続キーを Step 3 に貼り付けます。

    管理画面のパス: https://<自分の kintone ドメイン>/k/admin/system/admin/dataConnector.html
```

---

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/ConnectKintoneView.kt` | 変更 | Step 2 を案内に置き換え。`readKintoneDomain()` 削除。`authRejectedGuide()` の文言修正 |
| `web/routes/ConnectKintoneRoutes.kt` | 変更 | `POST /syncs/{name}/connect/save-domain` を削除 |

**テストの追加はしない。** 削除が主で、残るのは静的なマークアップのみ。判定ロジックや
分岐が増えないため、単体テストで守る対象がない。AC は実機で確認する。

---

## 3. 実装

### 3.1 Step 2

```kotlin
// Step 2: kintone 管理画面での操作案内
section {
    h3 { +"Step 2: kintone 管理画面でコネクタを追加" }
    p {
        +"kintone の管理画面で「外部システムのアプリ化」を開き、この連携に対応する"
        +"コネクタを追加してください。発行された接続キーを Step 3 に貼り付けます。"
    }
    // ドメインは Adapter 側では持たない。Agent が kintone へアウトバウンドで
    // 接続する構成のため、Adapter は kintone のドメインを必要としない (Issue #51)。
    p {
        +"管理画面のパス: "
        code { +"https://<kintone ドメイン>$ADMIN_CONNECTOR_PATH" }
    }
}
```

```kotlin
/** kintone 管理画面の「外部システムのアプリ化」のパス。 */
private const val ADMIN_CONNECTOR_PATH = "/k/admin/system/admin/dataConnector.html"
```

### 3.2 `authRejectedGuide()`

```kotlin
-li { +"kintone の「外部システムのアプリ化」を開く (下の Step 2 のリンク)" }
+li { +"kintone の管理画面で「外部システムのアプリ化」を開く (パスは下の Step 2)" }
```

### 3.3 削除するもの

- `readKintoneDomain()`
- Step 2 の `form` / `input` / `button` / 条件付きリンク
- `POST /syncs/{name}/connect/save-domain`
- 不要になった import（`InputType` などが他で使われていなければ）

---

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| 接続画面 Step 2 | 入力欄とボタンが消え、案内文とパスになる | 意図した変更（AC-1, AC-3） |
| `POST .../save-domain` | 404 になる | 内部 UI 専用の経路。外部利用の想定なし（D-5, AC-2） |
| `KINTONE_DOMAIN` | 参照が消える。設定していた利用者は影響を受けるが、**元から効いていなかった**（コンテナに渡っていない） | AC-5 |
| `docker-compose.yml` / `.env.example` | **変更なし**（元から記述が無い） | C-4 で確認 |
| Step 1 / Step 3 | **変更なし** | C-1, AC-6 |
| `authRejectedGuide()` | 文言のみ変更。#21 の意図（どの画面のどの操作か示す）は維持 | AC-4 |
| `docs/` | 影響なし | — |

---

## 5. 検証

実機で確認する（単体テストの対象になるロジックがないため）。

1. 接続画面にドメイン入力欄と「保存して開く」が無い（AC-1）
2. `POST /syncs/{name}/connect/save-domain` が 404（AC-2）
3. Step 2 に管理画面のパスが出る（AC-3）
4. 接続キー拒否時の案内がリンクを指していない（AC-4）
5. `grep KINTONE_DOMAIN` でコードに参照が無い（AC-5）
6. Step 1 の鍵ペア生成と Step 3 の接続キー入力欄が従来どおり出る（AC-6）
7. `./gradlew test` / detekt（AC-7）

---

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | D-1, §3.1 | §5-1 |
| AC-2 | D-5, §3.3 | §5-2 |
| AC-3 | D-2, D-3, §3.1 | §5-3 |
| AC-4 | D-4, §3.2 | §5-4 |
| AC-5 | §3.3 | §5-5 |
| AC-6 | D-6 | §5-6 |
| AC-7 | — | §5-7 |
