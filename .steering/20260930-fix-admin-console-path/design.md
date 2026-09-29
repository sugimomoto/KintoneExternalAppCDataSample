# kintone 管理画面パスの修正 — 設計

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#53](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/53) |
| 作成日 | 2026-09-30 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

| # | 判断 | 理由 |
|---|---|---|
| D-1 | 定数の値を差し替えるだけにする | 生きた参照は `ADMIN_CONNECTOR_PATH` の 1 箇所。他に手を入れる理由がない |
| D-2 | 値は実環境で確認されたものを使う | 推測で別の誤りを入れない（C-1）。今回の誤りは未検証の値を引き継いだことが原因 |
| D-3 | 定数の KDoc に**確認済みである旨**を残す | 同じ誤りの再発を防ぐ。次に触る人が「検証された値」と分かるようにする |
| D-4 | テストは追加しない | 定数 1 つの値で、分岐も判定もない。文字列の同値テストは実装の写経になり、誤った値でも通ってしまうため守りにならない |

## 2. 変更するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `web/views/ConnectKintoneView.kt` | 変更 | `ADMIN_CONNECTOR_PATH` の値と KDoc |

## 3. 実装

```kotlin
/**
 * kintone 管理画面の「外部システムのアプリ化」のパス。
 *
 * 実環境で確認済み (Issue #53)。以前は `/k/admin/system/admin/dataConnector.html` と
 * していたが誤りだった。`KINTONE_DOMAIN` が渡っておらずリンクが表示されなかったため
 * (#51)、長く気づかれなかった。変更する場合は実環境で確認すること。
 */
private const val ADMIN_CONNECTOR_PATH = "/k/admin/system/externalapp/"
```

## 4. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| Step 2 の案内 | 正しいパスになる | AC-1 |
| Step 2 の他の文言 / Step 1 / Step 3 | **変更なし** | AC-3 |
| `.steering/` の過去ドキュメント | **変更なし**（当時の記録として保持） | C-2 |
| `docs/` | 影響なし | — |

## 5. 検証

1. Step 2 の案内が `/k/admin/system/externalapp/` を示す（AC-1）
2. `grep dataConnector` で `src/` に残っていない（AC-2）
3. Step 1 / Step 3 の見出しと Step 3 の入力欄が従来どおり（AC-3）
4. `./gradlew test` / detekt（AC-4）

## 6. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 / AC-2 | §3 | §5-1, §5-2 |
| AC-3 | D-1 | §5-3 |
| AC-4 | — | §5-4 |
