# データソース一覧の堅牢性改善 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#39](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/39), [#40](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/40) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 読み込みの失敗許容（#39, 純粋関数）

- [x] **T-10** 🔴 `ConnectionRowTest` を書く（読めた / 例外 / メッセージ無し例外 / null）
- [x] **T-11** 🟢 `ConnectionRow` と `load` を実装

## Phase 2. マスク（#40）

- [x] **T-20** 🔴🟢 例外メッセージ中の接続文字列がマスクされるテストを追加
- [x] **T-21** 接続テストの失敗メッセージを `ConnectionStringMasker` に通す

## Phase 3. 画面（#39 + #40）

- [x] **T-30** `connectionRow` が `ConnectionRow` を受けるよう変更し、読めない行の表示を追加
- [x] **T-31** 読めない行は操作を削除のみにする
- [x] **T-32** 接続テストを htmx 化（`hx-post` / `hx-target` / `hx-disabled-elt` / `htmx-indicator`）
- [x] **T-33** 接続テストハンドラの KDoc を実態に合わせる

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-41** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-42** 壊れた行を仕込み、一覧が 200 で表示されることを確認（AC-1, AC-2, AC-4）
- [x] **T-43** 壊れた行を画面から削除できることを確認（AC-3）
- [x] **T-44** 壊れた行がある状態で削除拒否画面が 200 になることを確認（AC-5）
- [x] **T-45** 接続テストが行内を target にしていることを確認（AC-6〜AC-9）
- [x] **T-46** 仕込んだ行を片付け、`config.db` をバックアップと突き合わせる

## Phase 5. 仕上げ

- [x] **T-50** コミット
- [x] **T-51** PR 作成・マージ、Issue #39 / #40 に結果を記録

---

## 実機検証の結果 (2026-09-29)

壊れ方の違う行を 2 件仕込んで検証した（検証後に削除。`config.db` はバックアップと差分なしに復帰）。

- `zz-broken-missing` … 必須フィールド欠落（#36 検証時に実際に踏んだ壊れ方）。
  `config_json` に `Password=leakme` を仕込んで漏洩も確認した
- `zz-broken-syntax` … JSON 構文エラー (`{not json`)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | 壊れた行 2 件がある状態で `/connections` が HTTP 200 |
| AC-2 | ✅ | 「読み込めません」＋ 理由（`Fields [driver-class, driver-jar] are required…` / `Unexpected JSON token at offset 1…`） |
| AC-3 | ✅ | 壊れた行を削除 → HTTP 302、`shared_jdbcs` から消えた |
| AC-4 | ✅ | 接続テストボタン 8 個（正常行のみ）／削除ボタン 10 個（全行） |
| AC-5 | ✅ | 壊れた行がある状態で削除拒否画面が HTTP 200、参照元 `zz-sync-ref` が出た |
| AC-6〜AC-9 | ✅ | `<button type="button" hx-post="/connections/{name}/test" hx-target="#conn-test-{name}" hx-disabled-elt="this">接続テスト<span class="htmx-indicator"> 実行中…</span></button>`。target は行ごとに一意（8 行すべて異なる） |
| AC-10 | ✅ | 壊れた JSON に仕込んだ `Password=leakme` が画面に出ない。応答は断片のまま（`<div><p>●Connected: …</p></div>`） |

### 想定外に良かった点

`ConnectionStringMasker` は `Name=Value` の形を拾うため、**壊れた JSON の断片に残っていた
資格情報もマスクされた**。読み込み失敗の理由をそのまま画面に出しても漏れない。

### AC-8 の限界

`htmx-indicator` と `hx-disabled-elt` は HTML 属性として出ていることを確認したが、
実際の見え方（実行中に「実行中…」が表示され、ボタンが無効化される）はブラウザ操作が
必要なため未検証。htmx 標準の仕組みで、`DriversView` のライセンス検証と同じ流儀。
