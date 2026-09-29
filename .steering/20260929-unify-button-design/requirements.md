# Web UI ボタンデザインの統一 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | unify-button-design |
| 作成日 | 2026-09-29 |
| 前提 | Phase 2-C (Sync 概念 + Docker API 制御) 完了済み |
| Issue | [#9](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/9) |
| 目的 | Web UI 全体のボタンのバリアント・ラベル・アイコン・フォーカス状態を統一し、主要操作と破壊的操作を視覚的に判別できるようにする |

---

## 1. 背景

### 1.1 発端

連携詳細画面 (`/syncs/{name}`) のアクションバーで、5 つのボタンがすべて違う見た目になっている。

```
[⏸ 停止]      [kintone と接続 →]  [ログを見る]        [編集]      [削除]
 secondary     (primary/黄)       secondary outline   secondary   secondary outline
```

`web/views/TablesView.kt:113-122`

特に **「削除」が「ログを見る」と同じ `secondary outline`** で、無効化されたボタンのようにも見える。
一方で `app.css:318` の `.danger` は定義済みだが**どこからも使われていない**。

### 1.2 Web UI 全体の棚卸し結果

`web/views/*.kt` 8 ファイルと `static/app.css` を確認し、以下のばらつきを確認した。

| ID | 課題 | 該当箇所 |
|---|---|---|
| P-1 | 破壊的操作に `.danger` が使われていない | `TablesView.kt:120` 削除 / `TablesView.kt:251` Delete agent.json / `DriversView.kt:87` Delete |
| P-2 | 同じ意味の操作でバリアントが違う | 編集 (`TablesView.kt:117` = `secondary`) vs Edit (`ConnectionsView.kt:78` = `secondary outline`) / キャンセル・戻る系が `secondary` と `secondary outline` に分裂 |
| P-3 | 戻る操作が最も目立つ primary になっている | `DriversView.kt:173` Back to Drivers = `button` (黄) |
| P-4 | 未定義クラス `primary` の混在 | `.primary` は `app.css` にも `pico.min.css` にも存在しない。primary は `button:not(.secondary):not(.outline):not(.danger)` で表現されているため `classes = "primary"` はクラスなしと等価。5 箇所に残存 |
| P-5 | 1 つのアクションバーに黄色 CTA が 2 つ | `TableWizardView.kt:297-310` (Step 4) / `TablesView.kt:362-372` (Save / Save & Restart) |
| P-6 | 一覧テーブルの行内ボタンが primary | `TablesView.kt:384` `▶ 開始` がクラスなし = 黄。行数ぶん黄色ボタンが並ぶ |
| P-7 | ラベルの言語が日英混在 | `ConnectionsView` / `DriversView` はほぼ英語、`TablesView` / `ConnectKintoneView` はほぼ日本語 |
| P-8 | アイコン表記のルールがない | `⏸ ▶ ⬆ 🔑 📋 ❓ ⬇ + → ← ↗` が混在。絵文字と記号が同列に使われている |
| P-9 | 同一導線でラベルが違う | `+ 新しい連携` (`TablesView.kt:51`, `DashboardView.kt:80,92`) vs `+ 新しい連携を追加` (`TablesView.kt:61`) |
| P-10 | アクション行のコンテナが不統一 | `.action-bar` / `.quick-actions` (CSS 定義がほぼ同じ) / 素の `<p>` (`ConnectionsView.kt:51`, `ConnectKintoneView.kt:49`, `DriversView.kt:138,172`, `SyncLogsView.kt:29`) |
| P-11 | フォーカス / 無効状態のスタイルがない | `app.css` に `:focus-visible` と `:disabled` の指定が一切ない。`.danger` には `:hover` すらない |
| P-12 | submit ボタンだけが全幅の帯になる **(実装中に発見)** | Pico の `button[type="submit"] { width: 100% }` (詳細度 0,1,1) が `app.css` の base ルール (詳細度 0,0,1) に勝つため。`form.inline-form` (`display: inline-block`) 配下だけが偶然コンテンツ幅に収まっており、同じアクション行の `a.button` (79px) と `button[type=submit]` (1100px) で幅が 14 倍違っていた |

### 1.3 既存の関連作業

`.steering/20260929-syncs-list-delete-button/` (Issue #8) が **requirements.md のみで未実装**。
一覧画面への削除ボタン追加を扱い、その AC-7 で `button.danger` の hover 追加を要求している。
本作業で `.danger` の hover を先に整備するため、#8 側はスタイル定義済みの状態から着手できる。
**一覧画面への削除ボタン追加そのものは本作業のスコープ外**とする。

---

## 2. 目的

1. バリアント (`primary` / `secondary` / `secondary outline` / `danger`) の**意味を固定**し、全画面で同じ基準で使う
2. 破壊的操作を `danger` で明示し、参照系・キャンセル系と見間違えないようにする
3. ユーザー向けボタンラベルを日本語に統一し、アイコン表記のルールを決める
4. キーボード操作時のフォーカスリングを表示する

---

## 3. スコープ

### 3.1 今回実装する

| ID | 機能 | 対応する課題 |
|---|---|---|
| F-1 | `app.css` の Buttons セクションを再構成。バリアントごとに背景/文字/罫線/hover を明示 | P-1, P-4 |
| F-2 | `.danger` に `:hover` を追加 | P-1, P-11 |
| F-3 | 全ボタンに `:focus-visible` のフォーカスリングを追加 | P-11 |
| F-4 | `:disabled` / `[aria-disabled="true"]` のスタイルを追加 | P-11 |
| F-5 | 削除系ボタン 3 箇所を `danger` に変更 | P-1 |
| F-6 | `classes = "primary"` の記述を全廃 (クラスなしに統一) | P-4 |
| F-7 | 編集 / キャンセル / 戻る の各操作を全画面で同一バリアントに揃える | P-2, P-3 |
| F-8 | 1 つのアクションバーに primary が 1 つだけになるよう配分し直す | P-5, P-6 |
| F-9 | ユーザー向けボタンラベルを日本語に統一 | P-7 |
| F-10 | アイコンを許可記号のみに統一 (絵文字を廃止) | P-8 |
| F-11 | 「新しい連携」「新しいデータソース接続」のラベルを 1 種類に統一 | P-9 |
| F-12 | `.quick-actions` を `.action-bar` に統合し、素の `<p>` によるアクション行を `.action-bar` に置換 | P-10 |
| F-13 | `docs/development-guidelines.md` に「3.4 Web UI ボタン規約」を追記 | — |
| F-14 | ラベル変更に追随して E2E テストのセレクタを更新 | — |
| F-15 | Pico の submit ボタン全幅指定を打ち消し、全ボタンをコンテンツ幅に揃える | P-12 |

### 3.2 今回実装しない (スコープ外)

| 項目 | 理由 |
|---|---|
| 見出し (h2 / h3) とテーブル列名の日本語化 | `ConnectionsView` / `DriversView` はほぼ全面英語で、翻訳範囲が画面全体に及ぶ。用語は `docs/glossary.md` との突き合わせが必要で、ボタン統一とは独立した作業。**本作業後、Drivers / Connections 画面はボタンだけ日本語という中間状態になる**ことを許容し、別 Issue で扱う |
| 連携一覧画面への削除ボタン追加 | Issue #8 / `.steering/20260929-syncs-list-delete-button/` の担当範囲 |
| ボタンのサイズバリアント (`small` / `large`) の新設 | 現状サイズ違いの要求がない。必要になった時点で追加する |
| トースト / フラッシュメッセージの仕組み | 既存の Web UI に仕組みがなく、ボタン統一とは別レイヤー |
| ダークモードの配色見直し | `prefers-color-scheme: dark` のトークンは既に定義済み。バリアント整理でトークン参照に寄せるため、追加の配色作業は発生しない想定 |
| `pico.min.css` 側の改変 | ベンダーファイルは触らない (既存方針) |

---

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | デモ環境を構築する担当者として、連携詳細画面で「削除」が破壊的操作だと一目で分かるようにしたい。削除は非可逆で Agent コンテナと `agent.json` まで消えるため |
| US-2 | デモ環境を構築する担当者として、どの画面でも「次にやるべき操作」が黄色いボタン 1 つで分かるようにしたい。黄色が複数あると迷うため |
| US-3 | デモ環境を構築する担当者として、「編集」「キャンセル」「戻る」が画面ごとに違う見た目にならないようにしたい。同じ操作だと認識するのに時間がかかるため |
| US-4 | デモを見せる担当者として、UI のボタンラベルが日本語で統一されていてほしい。顧客の前で英語ラベルを都度言い換えるのは説明の流れが止まるため |
| US-5 | この UI を拡張する開発者として、新しいボタンを追加するときどのクラスを付ければよいか迷わないようにしたい。現状 `primary` という存在しないクラスが混在しており判断材料がないため |
| US-6 | キーボードで操作する利用者として、Tab でフォーカスした位置が分かるようにしたい。現状フォーカスリングが出ないため |

---

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | 削除系ボタン (`TablesView` の削除 / agent.json 削除、`DriversView` の削除) がすべて `danger` バリアントで赤く表示される |
| AC-2 | `.danger` に hover 時のスタイルがあり、hover しても赤系が保たれる |
| AC-3 | `classes = "primary"` の記述が `web/views/` に 1 件も残っていない |
| AC-4 | 1 つの `.action-bar` に primary (黄) ボタンが 2 つ以上並んでいない |
| AC-5 | 一覧テーブルの行内ボタンに primary が使われていない |
| AC-6 | 「編集」が全画面で `button secondary`、「キャンセル」「戻る」が全画面で `button secondary outline` になっている |
| AC-7 | 戻る操作に primary が使われていない (`DriversView` の「ドライバー一覧に戻る」を含む) |
| AC-8 | ユーザー向けボタンラベルがすべて日本語になっている |
| AC-9 | ボタン内のアイコンが許可記号 (`+` `→` `←` `▶` `⏸` `↑` `↓` `↗`) のみで、絵文字 (🔑 📋 ❓ ⬆ ⬇) が使われていない |
| AC-10 | 「新しい連携」「新しいデータソース接続」のラベルがそれぞれ 1 種類に統一されている |
| AC-11 | `.quick-actions` が CSS・Kotlin の両方から消え、`.action-bar` に統合されている |
| AC-12 | アクション行が素の `<p>` で組まれている箇所がない |
| AC-13 | キーボードの Tab でボタンにフォーカスすると視認できるフォーカスリングが出る |
| AC-14 | `:disabled` なボタンが押せない見た目 (不透明度低下 + `cursor: not-allowed`) になる |
| AC-15 | `docs/development-guidelines.md` にボタンバリアントの使い分けとアイコン規約が明記されている |
| AC-16 | `./gradlew detekt` と `./gradlew test` が通る |
| AC-17 | ラベル変更で壊れる E2E テストのセレクタが更新されている (`NewSyncFlowE2ETest` の `Next`、`DashboardE2ETest` の `データソース接続を追加`) |
| AC-18 | 各画面の既存の遷移・POST 先が変わっていない (回帰なし) |
| AC-19 | `button[type="submit"]` が全幅の帯にならず、同じアクション行の `a.button` と高さ・幅の基準が揃っている。モバイル幅では従来どおり `.action-bar > *` により全幅で縦積みになる |

---

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | ルーティング (`web/routes/*.kt`) は変更しない。`href` / `action` / `name` / `value` 属性は現状維持 |
| C-2 | `pico.min.css` は変更しない。上書きは `app.css` 側で行う |
| C-3 | CSS のカラーは `:root` のトークン (`--agility` / `--color-danger` 等) 経由で参照する。リテラル色を新規に増やさない |
| C-4 | ダークモードで文字と背景のコントラストが破綻しないこと |
| C-5 | `form` の submit ボタンの `name` / `value` は変更しない (サーバ側の分岐に使われている) |
| C-6 | `app.js` の SSE ハンドラは `tr[data-table]` 内の `.status` セルのみ書き換える。操作列のクラス名に依存した JS は無いことを確認済みだが、`.status-badge` のクラス名は変更しない |
| C-7 | ボタンラベルを参照している E2E テストは、ラベル変更と同じコミットで更新する |

---

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `resources/static/app.css` | Buttons セクション再構成、`.danger:hover` / `:focus-visible` / `:disabled` 追加、Pico の submit 全幅指定の打ち消し、`.quick-actions` 削除 |
| `web/views/TablesView.kt` | 削除を `danger` に、`primary` クラス除去、開始/停止を `secondary` に、ラベル日本語化 |
| `web/views/TableWizardView.kt` | Step 1-4 のボタンのバリアント再配分、ラベル日本語化 |
| `web/views/ConnectionsView.kt` | Edit / Test / Cancel / Save のバリアント統一とラベル日本語化、`<p>` → `.action-bar` |
| `web/views/ConnectKintoneView.kt` | 絵文字除去、`primary` クラス除去、`<p>` → `.action-bar` |
| `web/views/DashboardView.kt` | `.quick-actions` → `.action-bar`、`outline` 単独の除去、ラベル統一 |
| `web/views/DriversView.kt` | 削除を `danger` に、戻るを `secondary outline` に、ラベル日本語化、`<p>` → `.action-bar` |
| `web/views/SyncLogsView.kt` | `<p>` → `.action-bar` |
| `web/views/HelpView.kt` | 本文中の「🔑 鍵ペアを生成する」の表記をボタンラベル変更に追随 |
| `web/routes/*.kt` | **変更なし** (C-1) |
| `docs/development-guidelines.md` | 「3.4 Web UI ボタン規約」を追記 |
| `docs/glossary.md` | **変更なし** (UI 操作語の追加は見出し日本語化とあわせて別作業) |
| `src/browserTest` | `NewSyncFlowE2ETest` / `DashboardE2ETest` のセレクタ更新 |
