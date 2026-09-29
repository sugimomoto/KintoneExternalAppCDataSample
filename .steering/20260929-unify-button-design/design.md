# Web UI ボタンデザインの統一 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | unify-button-design |
| 作成日 | 2026-09-29 |
| 対応する要求 | `requirements.md` |

---

## 1. 設計方針

CSS 側で**バリアントの意味を先に固定**し、Kotlin (kotlinx.html) 側は
「その操作がどの意味に当たるか」を選ぶだけにする。
判断の余地を CSS に閉じ込めることで、今後ボタンを追加するときの迷いを無くす。

```
┌──────────────────────────────────────┐
│ app.css : Buttons セクション          │  ← 見た目の定義はここだけ
│  base / primary / secondary /        │
│  secondary outline / danger /        │
│  :focus-visible / :disabled          │
└──────────────┬───────────────────────┘
               │ クラス名で参照
┌──────────────┴───────────────────────┐
│ web/views/*.kt                        │  ← 意味を選ぶだけ
│  classes = "button secondary" 等      │
└──────────────────────────────────────┘
```

---

## 2. バリアント定義

### 2.1 意味の固定

| バリアント | クラス指定 | 見た目 | 用途 | 1 アクション行あたり |
|---|---|---|---|---|
| primary | **クラスなし** (`button` / `classes = "button"`) | Agility 黄 + Depth 文字 | その行で次にやるべき主要操作 | **最大 1** |
| secondary | `secondary` | 白背景 + 罫線 + 通常文字 | 副次操作 (編集 / 開始 / 停止 / 有効化 / 一覧へ) | 複数可 |
| secondary outline | `secondary outline` | 透明背景 + 薄い罫線 + muted 文字 | 補助・参照系 (ログを見る / 接続テスト / キャンセル / 戻る / 外部リンク / ダウンロード) | 複数可 |
| danger | `danger` | `--color-danger` 赤 + 白文字 | 破壊的操作 (削除) | 複数可 |

### 2.2 廃止するもの

| 対象 | 理由 | 移行先 |
|---|---|---|
| `classes = "primary"` | `app.css` にも `pico.min.css` にも `.primary` の定義がなく、`:not()` の副作用で偶然 primary になっているだけ。クラスなしと完全に等価 | クラス指定を削除 |
| `outline` 単独 | Resolve 紺の塗り反転で、他のバリアントと明度差が大きく浮く。使用箇所は「ヘルプを開く」「Activate Trial」の 2 箇所のみ | 「ヘルプを開く」→ `secondary outline` / 「有効化」→ `secondary` |
| `.quick-actions` | `.action-bar` と CSS 定義がほぼ同一 (flex + wrap + gap) | `.action-bar` |

`.primary` を CSS に定義して残す案も検討したが、**primary は「クラスを付けない状態」がデフォルト**という
現行の CSS 構造 (`button:not(.secondary):not(.outline):not(.danger)`) と二重管理になるため、
クラス側を消す方向に寄せる。

### 2.3 primary は「アクション行あたり 1 つ」

要求 AC-4 は `.action-bar` 単位の制約とする。
`ConnectKintoneView` のように `section` が Step ごとに独立している画面では、
Step ごとに 1 つの primary を持つことを許容する (Step 1 の「鍵ペアを生成する」と
Step 3 の「接続して開始 ▶」は別のアクション行)。

一覧テーブルの行内ボタン (`TablesView.tableActions()`) は、行数ぶん黄色が並ぶのを避けるため
**開始 / 停止をどちらも `secondary`** にする。詳細画面でも同じ関数を使うため、
詳細画面の primary は「kintone と接続 →」に一本化される。

---

## 3. アイコン規約

### 3.1 許可する記号

| 記号 | 意味 | 位置 |
|---|---|---|
| `+` | 追加・新規作成 | 先頭 |
| `→` | 前進・次へ・遷移 | 末尾 |
| `←` | 戻る | 先頭 |
| `▶` | 起動・開始 (プロセスを走らせる) | 先頭 |
| `⏸` | 停止 | 先頭 |
| `↑` | アップロード | 先頭 |
| `↓` | ダウンロード | 先頭 |
| `↗` | 外部サイトを別タブで開く | 末尾 |

### 3.2 廃止する絵文字

`🔑` `📋` `❓` `⬆` `⬇`

絵文字は OS / ブラウザでグリフの幅と色が変わり、ボタンの高さが揃わない。
バナーや空状態の装飾 (`✅` `⚠` `💤` `🔄`) は**ボタン外なので対象外**とする。

### 3.3 `→` と `▶` の使い分け

- `→` … 画面遷移を伴う前進 (`次へ →` / `kintone と接続 →` / `保存して kintone と接続 →`)
- `▶` … Adapter / Agent を実際に起動する (`▶ 開始` / `接続して開始 ▶`)

`保存して kintone と接続` はウィザード Step 4 から接続画面へ遷移する操作なので `→`。
`接続して開始` は Adapter 起動 + Agent コンテナ作成まで実行するので `▶`。

---

## 4. CSS の変更設計 (`resources/static/app.css`)

### 4.1 Buttons セクションの再構成

```
button, .button, a.button, input[type=button], input[type=submit]
  └ base: inline-flex / padding / radius / font / transition          … 変更なし

button:not(.secondary):not(.outline):not(.danger), ...                 … primary (変更なし)
button.secondary, ...                                                  … 変更なし
button.outline, ...                                                    … 残すが views からは使わない
button.secondary.outline, ...                                          … 変更なし

button.danger, .button.danger, a.button.danger                         ★ a.button.danger を追加
button.danger:hover, ...                                               ★ 新規
button.danger:active                                                   ★ 新規

button:focus-visible, .button:focus-visible, a.button:focus-visible    ★ 新規
button.danger:focus-visible                                            ★ 新規 (赤系リング)

button:disabled, button[aria-disabled="true"], .button.disabled        ★ 新規
```

### 4.2 追加する変数

```css
:root {
  --color-danger-hover: #8e1e22;   /* --color-danger (#b1262b) の暗色 */
  --focus-ring: 0 0 0 3px rgba(255, 229, 0, 0.45);          /* Agility 黄ベース */
  --focus-ring-danger: 0 0 0 3px rgba(177, 38, 43, 0.35);   /* danger 用 */
}
```

入力欄の `:focus` は既に `box-shadow: 0 0 0 3px rgba(255, 229, 0, 0.35)` を使っているため、
同じ Agility 黄系で揃える。`--focus-ring` を新設して入力欄側もこれを参照させる。

### 4.3 フォーカスリング

```css
button:focus-visible, .button:focus-visible, a.button:focus-visible {
  outline: 2px solid var(--depth);
  outline-offset: 1px;
  box-shadow: var(--focus-ring);
}
```

`:focus` ではなく `:focus-visible` を使い、マウスクリック時にリングが残らないようにする。
ダークモードでは `--depth` が背景側になるため、`outline-color` を
`@media (prefers-color-scheme: dark)` で `--clarity` に差し替える。

### 4.4 無効状態

```css
button:disabled, .button.disabled, a.button[aria-disabled="true"] {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: none;   /* a 要素でも押せないようにする */
}
```

現状 `disabled` なボタンは存在しないが、「削除が無効ボタンに見える」問題の裏返しとして
**本当に無効なときの見た目**を先に定義しておく。

### 4.5 Pico の submit ボタン全幅指定の打ち消し (実装中に追加)

Pico は次のルールで `button[type="submit"]` を全幅にする。

```css
/* pico.min.css */
button[type="submit"], input:not([type="checkbox"], [type="radio"]), select, textarea {
  width: 100%;
}
```

詳細度は属性セレクタを含む **(0, 1, 1)**。
一方 `app.css` の base ルールは `button, .button, a.button, ...` で **(0, 0, 1)** しかないため、
`width` をここに書いても Pico に負ける (実測で確認)。

結果として、`form.inline-form`（`display: inline-block` なので幅が収縮する）配下の
ボタンだけがコンテンツ幅になり、それ以外の submit ボタンは全幅の帯になっていた。

| 画面 | ボタン | 修正前の幅 |
|---|---|---|
| `/syncs/new` | `キャンセル` (`a.button`) | 79px |
| `/syncs/new` | `次へ →` (`button[type=submit]`) | **1100px** |
| `/syncs/{name}/edit` | `保存` / `保存して再起動` | **各 1085px**（それぞれ 1 行を占有） |
| `/syncs/{name}/connect` | `接続して開始 ▶` / `保存して開く` | **各 1059px** |
| `/drivers` | `↑ アップロード` | **1074px** |
| `/syncs/{name}` | `削除` / `▶ 開始` (`form.inline-form` 配下) | 57px / 75px |

対策として、Pico と同じ詳細度の属性セレクタで打ち消す専用ルールを base の直後に置く。

```css
button[type="submit"], button[type="button"], button[type="reset"],
input[type="submit"], input[type="button"] {
  width: auto;
}
```

- `select` / `textarea` / テキスト入力の `width: 100%` は維持する（フォーム部品は全幅のままが正しい）
- **注意**: モバイルの縦積み全幅化は `@media (max-width: 768px)` 内の
  `.action-bar > * { width: 100% }` で行っているが、その詳細度は **(0, 1, 0)** で、
  上の打ち消しルール **(0, 1, 1)** に**負ける**。メディアクエリは詳細度を上げないため、
  そのままではモバイルで全幅にならなくなる。
  そのため media query 側にも同詳細度以上のルールを追加して全幅を取り戻す。

```css
@media (max-width: 768px) {
  .action-bar > button, .action-bar > .button, .action-bar > input[type="submit"],
  .action-bar > form button, .action-bar > form .button,
  .inline-form > button {
    width: 100%;
    justify-content: center;
  }
}
```

### 4.6 base ルール全体が Pico に負けていた問題 (追加対応)

4.5 で `width` だけを属性セレクタで打ち消したが、**同じ原因が base ルールの
他のプロパティすべてに当てはまっていた**ことが後から判明した。

#### 原因

Pico のフォーム要素ルールは 3 本ある。

```css
[role="button"], [type="button"], [type="reset"], [type="submit"], button {
  padding: ...; font-size: 1rem; font-weight: ...; border-radius: ...; line-height: ...;
}
[type="button"], [type="reset"], [type="submit"] { margin-bottom: var(--pico-spacing); }
button[type="submit"], input:not(...), select, textarea { width: 100%; }
```

セレクタリストの詳細度は**一致した中で最も高いもの**。
`<button type="submit">` は `[type="submit"]` に一致するため **(0,1,0)**。
`app.css` の base は `button, .button, a.button, input[type=...]` で、
`<button>` に対しては `button` = **(0,0,1)** しか一致しない。

→ **padding / font-size / font-weight / border-radius / line-height / margin / width が
すべて Pico 側で確定していた。**

#### 実測値 (`/syncs/ProductPlant` の action-bar)

| プロパティ | `a.button` | `<button>` | 差 |
|---|---|---|---|
| height | 27px | **44px** | 1.6 倍 |
| font-size | 11.375px | **14px** | |
| font-weight | 600 | **400** | |
| padding | 5.25 / 12.25px | **10.5 / 14px** | |
| border-width | 1px | **0.5px** | |
| border-radius | 3px | **6px** | |
| line-height | 14.79px | **21.7px** | |
| margin-bottom | 0 | **12.25px** | 縦位置もずれる |

色だけは正しく出ていた。`button:not(.secondary):not(.outline):not(.danger)` が
**(0,3,1)** あるため背景・文字色は app.css が勝っていた。
「色は合っているのにサイズだけ違う」という発見しづらい状態になっていた。

#### 対策

4.5 の独立した `width: auto` ルールを廃し、**base ルール自体のセレクタを
すべて (0,1,0) 以上に引き上げる**。プロパティごとに打ち消しルールを増やすより、
1 本にまとめたほうが漏れない。

```css
button[type="submit"], button[type="button"], button[type="reset"],
button:not([type]),                    /* type 属性の無い <button> 用。(0,1,1) */
input[type="submit"], input[type="button"],
.button, a.button {
  /* 既存の base 宣言 */
  margin: 0;      /* Pico の margin-bottom 打ち消し */
  width: auto;    /* Pico の width: 100% 打ち消し */
}
```

- `button:not([type])` … `:not([type])` は引数 `[type]` の (0,1,0) を持つので
  `button:not([type])` = (0,1,1)。これが無いと `type` 無しの `<button>` だけ Pico に戻る
- `.button` は (0,1,0) で Pico と同値だが、`app.css` が後に読み込まれるので後勝ちで問題ない
- モバイルの全幅化は media query 側（同詳細度・後勝ち）でそのまま機能する

#### 検証結果

全 10 画面・計 51 ボタンで、`height` / `font-size` / `font-weight` / `padding` /
`border-radius` / `border-width` / `margin-bottom` がすべて**単一値**になった。

| 画面 | ボタン数 | height | font-size | padding |
|---|---|---|---|---|
| `/` | 3 | 27px | 11.375px | 5.25 / 12.25px |
| `/syncs` | 12 | 27px | 11.375px | 5.25 / 12.25px |
| `/syncs/{name}` | 7 | 27px | 11.375px | 5.25 / 12.25px |
| `/syncs/{name}/edit` | 3 | 27px | 11.375px | 5.25 / 12.25px |
| `/syncs/{name}/connect` | 5 | 27px | 11.375px | 5.25 / 12.25px |
| `/syncs/{name}/logs` | 1 | 27px | 11.375px | 5.25 / 12.25px |
| `/syncs/new` | 2 | 27px | 11.375px | 5.25 / 12.25px |
| `/connections` | 11 | 27px | 11.375px | 5.25 / 12.25px |
| `/connections/new` | 2 | 27px | 11.375px | 5.25 / 12.25px |
| `/drivers` | 5 | 27px | 11.375px | 5.25 / 12.25px |

#### 計測時の注意

base ルールに `transition: all 0.12s ease` があるため、CSS を差し替えた直後の
`getComputedStyle` は**アニメーション中の中間値**を返す。
最初この罠にかかり「修正が効いていない」と誤判断した。計測前に 300〜500ms 待つ。

### 4.7 `.quick-actions` の統合

`.quick-actions` のルールを削除し、`.action-bar` に寄せる。
`.action-bar` は `margin-block: var(--sp-3)` を持つが、`.quick-actions` は持たない。
Dashboard のクイックアクションは `article > h3` の直後に置かれるため、
`margin-block` が付く方が現状より余白が揃う。

`.empty-state .actions` は中央寄せの `inline-flex` で目的が違うため**残す**。

---

## 5. 画面ごとの変更設計

### 5.1 `TablesView.kt`

| 行 | 現状ラベル / クラス | 変更後 | 理由 |
|---|---|---|---|
| 51 | `+ 新しい連携` / `button` | 変更なし | 一覧ヘッダの CTA |
| 61 | `+ 新しい連携を追加` / `button` | `+ 新しい連携` | F-11 ラベル統一 |
| 115 | `kintone と接続 →` / `button` | 変更なし | 詳細画面の唯一の primary |
| 116 | `ログを見る` / `button secondary outline` | 変更なし | 参照系 |
| 117 | `編集` / `button secondary` | 変更なし | 副次操作の基準形 |
| 120 | `削除` / `secondary outline` | **`danger`** | F-5 |
| 247 | `Save agent.json` / `primary` | `agent.json を保存` / クラスなし | F-6, F-9 |
| 251 | `Delete agent.json` / `secondary outline` | `agent.json を削除` / **`danger`** | F-5, F-9 |
| 363 | `Cancel` / `button secondary` | `キャンセル` / `button secondary outline` | F-7, F-9 |
| 364 | `Save` / `primary` | `保存` / クラスなし | F-6, F-9 |
| 368 | `Save & Restart` / クラスなし | `保存して再起動` / `secondary` | F-8 (黄 2 つ解消), F-9 |
| 380 | `⏸ 停止` / `secondary` | 変更なし | — |
| 384 | `▶ 開始` / クラスなし | `secondary` | F-8 / 行内 primary の解消 |

`tableActions()` は一覧の行内と詳細画面の両方から呼ばれる。
`secondary` に統一することで、一覧では黄色が消え、詳細では primary が
「kintone と接続 →」だけになる。

編集画面の primary をどちらにするかは、**「保存」を primary / 「保存して再起動」を secondary** とする。
Adapter が停止中のときに「保存して再起動」は意味を持たないため、常に成立する方を primary に置く。

### 5.2 `TableWizardView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 75 | `Cancel` / `button secondary` | `キャンセル` / `button secondary outline` |
| 76 | `Next →` / クラスなし | `次へ →` / クラスなし |
| 117 | `← Back` / `button secondary` | `← 戻る` / `button secondary outline` |
| 118 | `Next →` | `次へ →` |
| 175 | `← Back to Step 1` / `button secondary` | `← Step 1 に戻る` / `button secondary outline` |
| 176 | `Next →` | `次へ →` |
| 298 | `← 最初に戻る` / `button secondary` | `button secondary outline` |
| 299 | `保存のみ` / `secondary` | `secondary outline` |
| 303 | `保存して起動` / クラスなし | `secondary` |
| 307 | `保存して kintone と接続 ▶` / `primary` | `保存して kintone と接続 →` / クラスなし |

Step 4 は「保存のみ (補助) / 保存して起動 (副次) / 保存して kintone と接続 (主要)」の 3 段階に整理する。
`name` / `value` 属性 (`andStart` / `andConnect`) は制約 C-5 により変更しない。

### 5.3 `ConnectionsView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 50-51 | `h2` + 素の `<p>` の中に `+ New Connection` | `.action-bar` に `h2` と `+ 新しいデータソース接続` をまとめる (`TablesView.kt:49` と同形) |
| 76 | `Test` / `secondary outline` | `接続テスト` / `secondary outline` |
| 78 | `Edit` / `button secondary outline` | `編集` / `button secondary` |
| 186 | `Cancel` / `button secondary` | `キャンセル` / `button secondary outline` |
| 187 | `Save` / `primary` | `保存` / クラスなし |

### 5.4 `ConnectKintoneView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 49 | `連携の詳細を見る →` / 素の `<p>` | `.action-bar` に変更。クラスは `button` のまま |
| 76 | `🔑 鍵ペアを生成する` / クラスなし | `鍵ペアを生成する` / クラスなし |
| 92 | `📋 公開鍵をコピー` / `secondary` | `公開鍵をコピー` / `secondary` |
| 96 | `⬇ ダウンロード` / `button secondary outline` | `↓ ダウンロード` |
| 91-99 | 素の `<p>` | `.action-bar` |
| 122 | `保存して開く` / `secondary outline` | `保存して開く` / `secondary` |
| 127 | `kintone 管理画面を開く ↗` / `button secondary` | `button secondary outline` |
| 154 | `接続して開始 ▶` / `primary` | クラスなし |
| 153-156 | 素の `<p>` | `.action-bar` |

Step 2 は「フォーム送信 (保存して開く) が副次操作、外部リンクが補助」という関係なので、
現状の `secondary outline` / `secondary` を入れ替える。

### 5.5 `DashboardView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 66 | `新階層に移行する` / クラスなし | 変更なし (バナー内の唯一のアクション) |
| 79 | `連携一覧へ` / `button secondary` | 変更なし |
| 80 | `+ 新しい連携` / `button` | 変更なし |
| 81 | `❓ ヘルプを開く` / `button outline` | `ヘルプを開く` / `button secondary outline` |
| 91 | `div(classes = "quick-actions")` | `div(classes = "action-bar")` |
| 93 | `+ データソース接続を追加` / `button secondary` | `+ 新しいデータソース接続` / `button secondary` |
| 94 | `ドライバー管理` / `button secondary` | 変更なし |

### 5.6 `DriversView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 52 | `⬆ Upload` / クラスなし | `↑ アップロード` / クラスなし |
| 81 | `Activate Trial` / `button outline` | `トライアルを有効化` / `button secondary` |
| 87 | `Delete` / `secondary outline` | `削除` / **`danger`** |
| 138-141 | 素の `<p>` + `Cancel` (`button secondary`) + `Activate` (クラスなし) | `.action-bar` + `キャンセル` (`button secondary outline`) + `有効化` (クラスなし) |
| 172-174 | 素の `<p>` + `Back to Drivers` (`button` = 黄) | `.action-bar` + `← ドライバー一覧に戻る` (`button secondary outline`) |

### 5.7 `SyncLogsView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 29-31 | 素の `<p>` + `← 連携詳細に戻る` (`button secondary outline`) | `.action-bar` に変更。クラス・ラベルは維持 |

### 5.8 `HelpView.kt`

| 行 | 現状 | 変更後 |
|---|---|---|
| 106 | 本文中の「🔑 鍵ペアを生成する」 | 「鍵ペアを生成する」 |

---

## 6. ドキュメント変更設計

### 6.1 `docs/development-guidelines.md`

「3.3 import 整理」の後に **「3.4 Web UI ボタン規約」** を追加する。

- バリアント表 (本設計 2.1)
- primary は 1 アクション行に 1 つ
- 破壊的操作は必ず `danger`
- `primary` / `outline` 単独クラスは使わない
- アクション行は `.action-bar` で囲む
- アイコンの許可記号と位置 (本設計 3.1)
- ボタンラベルは日本語

### 6.2 `docs/` 永続ドキュメントへの影響

| ドキュメント | 影響 |
|---|---|
| `product-requirements.md` | なし (機能要件の変更なし) |
| `functional-design.md` | なし (画面遷移・データモデルの変更なし) |
| `architecture.md` | なし (技術スタックの変更なし) |
| `repository-structure.md` | なし (ファイル追加/削除なし) |
| `development-guidelines.md` | **3.4 を追記** |
| `glossary.md` | なし (UI 操作語の追加は見出し日本語化とあわせて別作業) |

---

## 7. テストへの影響

| テスト | 影響 | 対応 |
|---|---|---|
| `NewSyncFlowE2ETest.kt:62,74,83,108` | `button:has-text("Next")` が `次へ →` になる | セレクタを `次へ` に更新 |
| `DashboardE2ETest.kt:39` | `text=データソース接続を追加` が `+ 新しいデータソース接続` になる | セレクタを `新しいデータソース接続` に更新 |
| `DashboardE2ETest.kt:37,38` | `+ 新しい連携` / `ドライバー管理` は変更なし | 対応不要 |
| `KeyPairGenerationE2ETest.kt:46,48,66` | `button:has-text('鍵ペアを生成')` は部分一致。絵文字除去の影響を受けない | 対応不要 |
| `ConnectKintoneE2ETest.kt:80` | `button:has-text("接続して開始")` は部分一致 | 対応不要 |
| `DriversAndConnectionsE2ETest.kt:14` | `text=Upload new driver` は `h3` 見出し。見出しはスコープ外 | 対応不要 |
| `DriversAndConnectionsE2ETest.kt:22` | `text=New Connection` は `/connections/new` の `h2`。ボタンラベルではない | 対応不要 |
| 単体テスト (`src/test`) | View を描画するテストは存在しない (`web/views` を参照するテストなし) | 対応不要 |

---

## 8. リスクと対策

| リスク | 対策 |
|---|---|
| `danger` (赤) がダークモードで沈む | `--color-danger` は明度 #b1262b 固定で白文字を載せるため、両モードで 4.5:1 を満たす。hover の `--color-danger-hover` も白文字前提で選ぶ |
| 一覧の「開始」を `secondary` にすると押しづらく見える | 状態バッジ (`status-badge stopped`) が隣に出ているため、操作の所在は維持される。行内で唯一のアクションなので埋没しない |
| `.quick-actions` 削除でダッシュボードの余白が変わる | `.action-bar` の `margin-block: var(--sp-3)` が付くだけ。`h3` 直下なので余白が増える方向で、崩れない |
| アクション行の `<p>` → `div.action-bar` 置換で `<p>` 内インライン前提の JS が壊れる | `app.js` を確認。ボタンを含む `<p>` を参照する処理は無く、`copyPublicKey()` は `.public-key-display code` を読むだけ |
| `pointer-events: none` を `a.button[aria-disabled]` に付けると将来 tooltip が出せない | 現状 tooltip の仕組みが無いため許容。必要になれば wrapper 側で扱う |
