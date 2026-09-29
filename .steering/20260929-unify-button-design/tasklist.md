# Web UI ボタンデザインの統一 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | unify-button-design |
| 作成日 | 2026-09-29 |
| 対応する設計 | `design.md` |

進捗記号: `[ ]` 未着手 / `[~]` 着手中 / `[x]` 完了

---

## Phase 1: CSS 基盤 (app.css)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 1-1 | `:root` に `--color-danger-hover` / `--focus-ring` / `--focus-ring-danger` を追加 | 3 変数が定義され、既存トークンから導出されている | [x] |
| 1-2 | `.danger` に `a.button.danger` を追加し、`:hover` / `:active` を定義 | 赤系が hover でも保たれる。白文字のコントラストが維持される | [x] |
| 1-3 | 全ボタンに `:focus-visible` のリングを追加 | Tab でリングが出る。マウスクリックでは残らない | [x] |
| 1-4 | ダークモードで `:focus-visible` の `outline-color` を `--clarity` に差し替え | ダークモードでリングが視認できる | [x] |
| 1-5 | `:disabled` / `[aria-disabled="true"]` / `.disabled` のスタイルを追加 | `opacity: 0.45` + `cursor: not-allowed` + `pointer-events: none` | [x] |
| 1-6 | 入力欄の `:focus` を `--focus-ring` 参照に変更 | リテラル `rgba(255, 229, 0, 0.35)` が消える | [x] |
| 1-7 | `.quick-actions` のルールを削除 | `app.css` に `.quick-actions` が残っていない | [x] |
| 1-8 | Buttons セクションの先頭にバリアント一覧のコメントを追記 | クラスなし = primary であることがコメントから読める | [x] |
| 1-9 | **(実装中に追加)** Pico の `button[type="submit"] { width: 100% }` を同詳細度で打ち消す | submit ボタンが全幅の帯にならず、`a.button` と幅の基準が揃う | [x] |
| 1-10 | **(実装中に追加)** media query 側にも同詳細度以上のルールを足し、モバイル縦積みの全幅を維持する | 375px 幅で全ボタンが 359px の全幅縦積みになる | [x] |
| 1-11 | **(ユーザー指摘により追加)** base ルールのセレクタを全項目 (0,1,0) 以上に引き上げ、`margin: 0` を追加 | `<button>` と `a.button` で height / font-size / font-weight / padding / border-radius / border-width / margin が一致する。1-9 の独立ルールは base に統合 | [x] |

## Phase 2: 破壊的操作を danger に (F-5)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 2-1 | `TablesView.kt:120` 削除ボタンを `danger` に | 詳細画面の削除が赤 | [x] |
| 2-2 | `TablesView.kt:251` `Delete agent.json` を `danger` + `agent.json を削除` に | 赤 + 日本語ラベル | [x] |
| 2-3 | `DriversView.kt:87` `Delete` を `danger` + `削除` に | 赤 + 日本語ラベル | [x] |
| 2-4 | `grep -rn 'secondary outline' で削除系が残っていないか確認 | 削除系に `secondary outline` が無い | [x] |

## Phase 3: primary クラス全廃と CTA 整理 (F-6, F-8)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 3-1 | `TablesView.kt:247,364` の `classes = "primary"` を除去 | — | [x] |
| 3-2 | `TablesView.kt:368` `Save & Restart` を `secondary` に | 編集画面の黄が「保存」1 つ | [x] |
| 3-3 | `TablesView.kt:384` `▶ 開始` を `secondary` に | 一覧テーブルに黄が無い | [x] |
| 3-4 | `TableWizardView.kt:307` の `classes = "primary"` を除去 | — | [x] |
| 3-5 | `TableWizardView.kt:299,303` を `secondary outline` / `secondary` に再配分 | Step 4 の黄が「保存して kintone と接続 →」1 つ | [x] |
| 3-6 | `ConnectionsView.kt:187` の `classes = "primary"` を除去 | — | [x] |
| 3-7 | `ConnectKintoneView.kt:154` の `classes = "primary"` を除去 | — | [x] |
| 3-8 | `grep -rn 'classes = "primary"' src/main` が 0 件 | AC-3 | [x] |
| 3-9 | `grep -rn '"button outline"\|classes = "outline"' src/main` が 0 件 | `outline` 単独の廃止 | [x] |

## Phase 4: バリアントの統一 (F-7)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 4-1 | `ConnectionsView.kt:78` `Edit` を `button secondary` に | 編集が全画面で `secondary` | [x] |
| 4-2 | キャンセル/戻る系を `button secondary outline` に統一 (`TablesView:363` / `TableWizardView:75,117,175,298` / `ConnectionsView:186` / `DriversView:139,173`) | AC-6, AC-7 | [x] |
| 4-3 | `DriversView.kt:173` 戻るボタンから primary を外す | AC-7 | [x] |
| 4-4 | `DriversView.kt:81` `Activate Trial` を `button secondary` に | `outline` 単独の解消 | [x] |
| 4-5 | `DashboardView.kt:81` ヘルプを `button secondary outline` に | `outline` 単独の解消 | [x] |
| 4-6 | `ConnectKintoneView.kt:122,127` の Step 2 のバリアントを入れ替え | フォーム送信が `secondary`、外部リンクが `secondary outline` | [x] |

## Phase 5: ラベル日本語化とアイコン統一 (F-9, F-10, F-11)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 5-1 | `TableWizardView` の `Next →` 3 箇所を `次へ →` に | — | [x] |
| 5-2 | `TableWizardView` の `← Back` / `← Back to Step 1` を `← 戻る` / `← Step 1 に戻る` に | — | [x] |
| 5-3 | `Cancel` 4 箇所を `キャンセル` に | — | [x] |
| 5-4 | `ConnectionsView` の `Save` / `Test` / `+ New Connection` を日本語化 | `保存` / `接続テスト` / `+ 新しいデータソース接続` | [x] |
| 5-5 | `DriversView` の `⬆ Upload` / `Activate` / `Back to Drivers` を日本語化 | `↑ アップロード` / `有効化` / `← ドライバー一覧に戻る` | [x] |
| 5-6 | `TablesView` の `Save agent.json` / `Save` / `Save & Restart` を日本語化 | `agent.json を保存` / `保存` / `保存して再起動` | [x] |
| 5-7 | `ConnectKintoneView` の絵文字 `🔑` `📋` `⬇` を除去 (`⬇` → `↓`) | AC-9 | [x] |
| 5-8 | `DashboardView.kt:81` の `❓` を除去 | AC-9 | [x] |
| 5-9 | `TableWizardView.kt:307` の `▶` を `→` に | 遷移は `→` | [x] |
| 5-10 | `TablesView.kt:61` を `+ 新しい連携` に、`DashboardView.kt:93` を `+ 新しいデータソース接続` に | AC-10 | [x] |
| 5-11 | `HelpView.kt:106` の本文中の `🔑` 表記をボタンラベルに追随 | ヘルプの記述とボタンが一致 | [x] |
| 5-12 | `grep` で絵文字がボタンラベルに残っていないか確認 | AC-9 | [x] |

## Phase 6: アクション行コンテナの統合 (F-12)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 6-1 | `DashboardView.kt:91` を `action-bar` に | AC-11 | [x] |
| 6-2 | `ConnectionsView.kt:50-51` を `h2` 込みの `.action-bar` に | `TablesView.kt:49` と同形 | [x] |
| 6-3 | `ConnectKintoneView.kt:49,91-99,153-156` の `<p>` を `.action-bar` に | AC-12 | [x] |
| 6-4 | `DriversView.kt:138-141,172-174` の `<p>` を `.action-bar` に | AC-12 | [x] |
| 6-5 | `SyncLogsView.kt:29-31` の `<p>` を `.action-bar` に | AC-12 | [x] |

## Phase 7: ドキュメント (F-13)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 7-1 | `docs/development-guidelines.md` に「3.4 Web UI ボタン規約」を追記 | AC-15。バリアント表・primary の個数制約・アイコン規約を含む | [x] |

## Phase 8: テスト追随 (F-14)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 8-1 | `NewSyncFlowE2ETest` の `Next` セレクタ 4 箇所を `次へ` に | AC-17 | [x] |
| 8-2 | `DashboardE2ETest.kt:39` のセレクタを `新しいデータソース接続` に | AC-17 | [x] |
| 8-3 | 他の E2E テストがラベル変更の影響を受けないことを再確認 | `design.md` 7 章の表と一致 | [x] |

## Phase 9: 品質チェック

| # | タスク | 結果 | 状態 |
|---|---|---|---|
| 9-1 | `./gradlew detekt` | **既存の違反により FAILED**。ただし本作業前のベースラインが 91 weighted issues、本作業後が 90 で、**新規の違反は 0 件・1 件減少**。残る違反は `LongMethod` / `MagicNumber` / `MaxLineLength` など本作業以前から存在するもの（`git stash` でベースラインを取得して確認済み） | [x] |
| 9-2 | `./gradlew test` | BUILD SUCCESSFUL | [x] |
| 9-3 | `compileBrowserTestKotlin` | BUILD SUCCESSFUL（`ktlintCheck` タスクは本プロジェクトに存在しない） | [x] |
| 9-4 | Web UI を起動して全画面を目視確認 | 一覧 / 詳細 / 編集 / 接続 / ダッシュボード / データソース / ドライバー / ウィザード Step1 を確認。詳細画面は `▶ 開始`(secondary) / `kintone と接続 →`(黄) / `ログを見る`(outline) / `編集`(secondary) / `削除`(赤) となり、黄は 1 つ。一覧テーブルに黄なし。Tab フォーカスで `outline: rgb(21,21,28) solid 2px` を実測確認 | [x] |
| 9-5 | ダークモードで表示確認 | `削除` = bg `#b1262b` / fg `#ffffff`（コントラスト比 約 6.6:1）。`--focus-outline` がダークで `#F1EEE9` に切り替わることを実測確認 | [x] |
| 9-6 | 受け入れ条件 AC-1 〜 AC-19 の突き合わせ | AC-16 の「detekt 違反 0」のみ**未達**（9-1 の理由による既存違反。新規違反 0）。他は全てクリア | [x] |
| 9-7 | **(実装中に追加)** モバイル幅 (375px) での確認 | `.action-bar` のボタンが 359px の全幅で縦積みになることを実測確認 | [x] |
| 9-8 | **(ユーザー指摘により追加)** 全画面のボタンをプロパティ単位で実測 | 10 画面・計 51 ボタンで height / font-size / font-weight / padding / border-radius / border-width / margin-bottom がすべて単一値。`/syncs/{name}` の 5 ボタンは上端も 124px で一致 | [x] |

### 9-1 の補足: detekt の扱い

`./gradlew detekt` は**本作業の着手前から失敗していた**。

| 時点 | weighted issues |
|---|---|
| 着手前 (`git stash` でベースライン取得) | 91 |
| 本作業後 | 90 |

違反の内訳は `LongMethod`（`helpView` 177 行など）、`MagicNumber`、`MaxLineLength`、
`UnusedParameter` といった既存のもので、本作業で新規に追加したものは無い。
`web/views/*.kt` の `LongMethod` 解消は関数分割を伴い、ボタン統一とは別の作業になるため、
本作業のスコープ外とした（別 Issue で扱う）。

---

## 対象外の確認事項 (作業中に触らないもの)

- `web/routes/*.kt` … `href` / `action` / `name` / `value` は変更しない (C-1, C-5)
- `pico.min.css` … ベンダーファイル (C-2)
- `h2` / `h3` 見出しとテーブル列名の日本語化 … 別 Issue
- 連携一覧への削除ボタン追加 … Issue #8
- `.status-badge` のクラス名 … `app.js` が参照 (C-6)

---

## 実装中に見つかった追加の課題 (本作業で対応済み)

### Pico の submit ボタン全幅指定 (P-12 / F-15)

目視確認の段階で、`button[type="submit"]` だけが全幅の帯になっていることを発見した。

```
修正前 /syncs/new : [キャンセル 79px] [        次へ →  1100px        ]
修正後 /syncs/new : [キャンセル 79px] [次へ → 75px]
```

原因は `pico.min.css` の `button[type="submit"] { width: 100% }`（詳細度 0,1,1）が
`app.css` の base ルール（詳細度 0,0,1）に勝つこと。
`form.inline-form`（`display: inline-block`）配下のボタンだけが偶然コンテンツ幅に収まっていた。

詳細と対策は `design.md` 4.5 を参照。

## 本作業では対応しなかった追加の発見

| 発見 | 内容 | 扱い |
|---|---|---|
| `ConnectionsView` の一覧テーブルが横溢れする | JDBC URL 列が長く、`操作` 列が画面外に押し出される。行内の `接続テスト` / `編集` ボタンが見えない | 別 Issue。テーブルのレイアウト課題でボタン統一とは独立 |
| `h2` / `h3` 見出しとテーブル列名が英語のまま | `Drivers` / `Connections` 画面は見出し・列名が英語で、ボタンだけ日本語になる中間状態 | 別 Issue（`requirements.md` 3.2 でスコープ外と明記） |
| ヘッダーナビの絵文字 (`🏠🔄🔌📦❓`) | 5 項目すべてに付いていて内部的には一貫している。ボタンではなくナビゲーション | 現状維持 |
| detekt の既存違反 90 件 | `LongMethod` / `MagicNumber` / `MaxLineLength` など | 別 Issue |

---

## 追加対応: base ルール全体が Pico に負けていた (ユーザー指摘)

### 何を見落としたか

9-4 の目視確認と 9-7 の実測では **幅 (`width`) しか測っていなかった**。
幅はコンテンツ依存で揃って見えるため「サイズは統一できた」と誤って報告した。

ユーザーから連携詳細画面とデータソース一覧のスクリーンショットで指摘を受け、
**高さ・font-size・padding・border-radius・border-width・margin が揃っていない**ことが判明した。

```
指摘された状態 (/syncs/ProductPlant)
[  ⏸ 停止  ] [kintone と接続 →] [ログを見る] [編集] [  削除  ]
   44px          27px             27px       27px    44px
   ↑ <button>    ↑ a.button                          ↑ <button>
```

### 原因

4.5 で `width` だけを属性セレクタで打ち消したが、**同じ詳細度の問題が base ルールの
他の全プロパティに当てはまっていた**。詳細は `design.md` 4.6 を参照。

色だけは `:not(.secondary):not(.outline):not(.danger)` が (0,3,1) あるため正しく出ており、
「色は合っているのにサイズだけ違う」という気づきにくい状態になっていた。

### 対策

base ルールのセレクタを全項目 (0,1,0) 以上に引き上げ、`margin: 0` / `width: auto` を
base に統合した（プロパティごとに打ち消しルールを足すより漏れにくい）。

### 再発防止

`docs/development-guidelines.md` に 2 節を追加した。

- **3.4.6 ⚠ セレクタの詳細度 — Pico に負けないこと** …
  Pico の 3 本のルールと詳細度、実測の差分表、base セレクタを短くしてはいけない理由
- **3.4.7 変更したら「プロパティ単位」で実測する** …
  全ボタンの計算値を集めて単一値か確認するスニペット。
  `transition: all` があるため計測前に 300〜500ms 待つ必要があることも明記
  （最初この罠で「修正が効いていない」と誤判断した）
