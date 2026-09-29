# データソース画面の動線改善 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#42](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/42), [#43](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/43) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 実効 AuthScheme の解決（#43, 純粋関数）

- [x] **T-10** 🔴 `OAuthCapabilityTest` に実効値の解決と要否判定のテストを追加
- [x] **T-11** 🟢 `effectiveAuthScheme` と `requiresBrowserAuthorization(url, default)` を実装
- [x] **T-12** ビューのインライン実装を共有関数に置き換える（AC-12 の回帰なし）

## Phase 2. 接続テスト結果の表示位置（#42）

- [x] **T-20** テーブルの上に結果バナーの差し替え先を追加
- [x] **T-21** ボタンの `hx-target` をそこへ向け、ラッパ `div` を外す
- [x] **T-22** 応答に接続名を含め、`success-banner` / `warning-banner` で出し分ける

## Phase 3. 新規保存のリダイレクト（#43）

- [x] **T-30** 新規保存時にブラウザ認可の要否で遷移先を分ける
- [x] **T-31** 編集保存は変更しないことを確認

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-41** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-42** 結果がテーブル上に出て操作列に入らないことを確認（AC-1〜AC-4）
- [x] **T-43** 結果表示後もボタンが残ることを確認（AC-5, AC-6）
- [x] **T-44** 失敗メッセージのマスクを確認（AC-7）
- [x] **T-45** OAuth 接続の新規保存でウィザードへ遷移することを確認（AC-8, AC-10）
- [x] **T-46** 非 OAuth 接続の新規保存で一覧へ戻ることを確認（AC-9）
- [x] **T-47** 編集保存で一覧へ戻ることを確認（AC-11）
- [x] **T-48** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 5. 仕上げ

- [x] **T-50** コミット
- [x] **T-51** PR 作成・マージ、Issue #42 / #43 に結果を記録

---

## 実機検証の結果 (2026-09-29)

検証用の接続 `zz-fail` / `zz-oauth` / `zz-basic` / `zz-default` を作って確認した
（検証後に削除。`config.db` はバックアップと差分なしに復帰）。

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 / AC-2 | ✅ | `id="connection-test-result"` がテーブルより上（位置 1029 < 1105）。`conn-test-*` は HTML から消えた |
| AC-3 / AC-4 | ✅ | 成功: `<article class="success-banner"><p><code>GoogleSheetsOAuth</code> 接続成功: …` ／ 失敗: `<article class="warning-banner"><p><code>zz-fail</code> 接続失敗: …` |
| AC-5 | ✅ | ボタンは `hx-target="#connection-test-result"` で自分を差し替えないため残る |
| AC-6 | ✅ | 全行が同じ target を指すのでバナーが置き換わる |
| AC-7 | ✅ | `Password=wrongsecret` を仕込んだ接続でも `wrongsecret` が出ない |
| AC-8 | ✅ | `AuthScheme=OAuth` を新規保存 → `302 → /connections/zz-oauth/oauth` |
| AC-9 | ✅ | `AuthScheme=Basic` を新規保存 → `302 → /connections` |
| AC-10 | ✅ | `AuthScheme` を指定せず googlesheets を保存（URL は `jdbc:googlesheets:` のみ）→ `302 → /connections/zz-default/oauth`。既定値による判定が効いている |
| AC-11 | ✅ | 編集保存 → `302 → /connections` |
| AC-12 | ✅ | 編集画面の「OAuth 認可を行う →」は従来どおり。Basic 接続には出ない |

### 検証中に気づいたこと（今回は対応せず）

**接続テストは資格情報を検証していない。** `conn.metaData` までしか触らないため、
`Password` が誤っていても「接続成功」になる（Salesforce で確認）。失敗を再現するには
ドライバー JAR を存在しないパスにする必要があった。

接続テストの意義に関わるため別 Issue にする価値がある。本件とは独立。
