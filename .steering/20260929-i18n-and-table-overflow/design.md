# データソース / ドライバー画面の日本語化とテーブル横溢れの解消 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | i18n-and-table-overflow |
| 作成日 | 2026-09-29 |
| 対応する要求 | `requirements.md` |

---

## 1. 課題 A: テーブル横溢れの解消

### 1.1 なぜ `table { width: 100% }` では収まらないか

`app.css` は `table { width: 100% }` を指定しているが、CSS の表レイアウトでは
`table-layout: auto`（既定）のとき **`width` は下限にしかならず**、
セルの最小コンテンツ幅の合計が容器を超える場合はそちらが優先される。

マスク済み接続文字列は `;` を含むものの改行機会 (soft wrap opportunity) を持たない
1 トークンとして扱われるため、URL セルの最小コンテンツ幅が 3819px になっていた。

```
main.container 1024px
┌────────────────────────────────────────┐
│ ┌──────┬────────┬──────── 4301px ─────┼─────────────────→ [操作]
│ │ 名前 │ドライバ│ URL (3819px)        │                    画面外
└────────────────────────────────────────┘
        ↑ document 全体が横スクロールする
```

### 1.2 対策の three-layer 構成

3 つを組み合わせる。1 つだけでは不十分。

| 層 | 施策 | 役割 |
|---|---|---|
| ① セル内で収める | 長い値を内側のブロック要素で省略表示 | テーブル自体が容器を超えないようにする（本命） |
| ② 操作列を守る | `white-space: nowrap` | ボタンが縦に折り返すのを防ぐ |
| ③ 最後の砦 | テーブルを `overflow-x: auto` のラッパーで包む | それでも溢れる場合に、**ページ全体ではなくテーブル内だけ**をスクロールさせる |

### 1.3 ① 省略表示の実装方法

`td` に直接 `max-width` を付ける方法は採らない。
CSS 仕様上、表セルの `width` / `max-width` は「サジェスト」であり、
`white-space: nowrap` なコンテンツは auto レイアウトで `max-width` を超えて広がりうる。

**内側のブロック要素に `max-width` + `overflow: hidden` を掛ける**方式を使う。
これはセルの最小コンテンツ幅そのものを縮めるため確実に効く。

```css
.cell-truncate > code,
.cell-truncate > span {
  display: block;
  max-width: 24rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

`24rem` は base 14px 換算で 336px。名前 91px + ドライバー 275px + 336px + 操作 117px ≒ 819px で
`main.container` の 1024px に収まる。

### 1.4 全文の参照手段

`title` 属性に全文（マスク済み文字列）を入れ、ホバーで確認できるようにする。
さらに一覧の名前リンク (`/connections/{name}`) が接続フォームを開き、
下部の sticky URL プレビューに全文が出るため、参照経路は 2 つ確保される。

```kotlin
td(classes = "cell-truncate") {
    val masked = ConnectionStringMasker.mask(config?.url ?: "")
    attributes["title"] = masked
    code { +masked }
}
```

**マスク済みの値のみを `title` に入れる。** 生の接続文字列は入れない
（`ConnectionStringMasker` を通す意味が無くなり、DOM に平文の資格情報が載る）。

### 1.5 ③ ラッパー

```css
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
```

`table` は既に `width: 100%` なので、収まるときはラッパーがあっても見た目は変わらない。
モバイル幅で溢れたときだけテーブル内スクロールになる。

`article` / `section` は `overflow` を指定していないため、ラッパーの追加による
`box-shadow` や `border-radius` への影響は無い。

---

## 2. 課題 B: 日本語化

### 2.1 呼称の決定

既に確定している呼称（`Layout.kt` のナビ、`HelpView.kt` の用語カードとページ一覧）に合わせる。
**本作業で新しい呼称を発明しない。**

| 概念 | 採用する呼称 | 根拠 |
|---|---|---|
| `/connections` の画面 | データソース | `Layout.kt:55`, `HelpView.kt:123` |
| `/connections` の 1 レコード | データソース接続 | `HelpView.kt:89` 「データソース接続を作成」 |
| `/drivers` の画面 | ドライバー | `Layout.kt:56`, `HelpView.kt:124` |
| 件数の表記 | `(N 件)` | `TablesView.kt:50` 「連携 (11 件)」 |

「ドライバ」ではなく **「ドライバー」**（長音付き）で統一する。
`HelpView.kt:67,85` と `Layout.kt:56` が長音付きで、
`ConnectionsView.kt:143` の `-- ドライバを選択 --` と `DriversView.kt:59` のみが長音無しで揺れている。

### 2.2 `ConnectionsView.kt` の対応表

| 箇所 | 現状 | 変更後 |
|---|---|---|
| `:46` pageTitle | `"Connections"` | `"データソース"` |
| `:51` h2 | `"JDBC Connections (${names.size})"` | `"データソース接続 (${names.size} 件)"` |
| `:57` 空状態 | `"共通 JDBC 接続がまだ登録されていません。"` | `"データソース接続がまだ登録されていません。"` |
| `:63` th | `Name` | `名前` |
| `:64` th | `Driver` | `ドライバークラス` |
| `:65` th | `URL (masked)` | `接続文字列 (マスク済み)` |
| `:66` th | `Action` | `操作` |
| `:60` table | — | `div(classes = "table-scroll")` で包む |
| `:75` td | `code { masked }` | `cell-truncate` + `title` 属性 |
| `:76` td | 操作セル | `cell-actions` クラスを付与 |
| `:96` pageTitle | `"Edit Connection: $existingName"` / `"New Connection"` | `"データソース接続を編集: $existingName"` / `"新しいデータソース接続"` |
| `:111` リンク文言 | `" Drivers 画面"` | `"ドライバー画面"` |
| `:124` label | `"Name (識別子, 英数小文字):"` | `"名前 (識別子、英数小文字):"` |
| `:133` label | `"JDBC Driver:"` | `"JDBC ドライバー:"` |
| `:143` option | `"-- ドライバを選択 --"` | `"-- ドライバーを選択 --"` |
| `:171` h3 | `"Pool Settings"` | `"接続プール設定"` |
| `:173` label | `"Pool size: "` | `"最大接続数:"` |
| `:179` label | `"Connection timeout (ms): "` | `"接続タイムアウト (ミリ秒):"` |
| `:221` label | `"JDBC URL (直接入力): "` | `"JDBC 接続文字列 (直接入力):"` |
| `:237` 説明 | `" Required + Visible のみ展開、…"` | `" 必須かつ表示対象のものだけ展開しています。その他はカテゴリを開いて確認してください。"` |
| `:256` h3 | `+category` | `+categoryLabel(category)` |
| `:262` summary | `"$category (${catProps.size} props)"` | `"${categoryLabel(category)} (${catProps.size} 件)"` |
| `:277` バッジ | `" 🔐PASSWORD"` | `" パスワード"` |
| `:278` バッジ | `" 🔐"` | `" 機微情報"` |

### 2.3 カテゴリ表示名の変換 (F-9)

CData ドライバの `sys_connection_props` が返すカテゴリ名を**表示時だけ**日本語にする。
並び順のソートキーは生値のまま維持し、未知のカテゴリはそのまま表示する。

同梱の 4 ドライバー (bcart / googlesheets / salesforce / sapgateway) が実際に返すカテゴリを
`/connections/properties` を全ドライバー分たたいて収集した結果、**13 種**あった。
当初 4 種だけマッピングしていたが 6 種が英語のまま残ったため、全件を網羅する。

| 生の値 | 表示 | 備考 |
|---|---|---|
| `Authentication` | 認証 | |
| `Connection` | 接続 | |
| `Caching` | キャッシュ | |
| `Firewall` | ファイアウォール | |
| `Logging` | ログ | |
| `Proxy` | プロキシ | |
| `Schema` | スキーマ | |
| `Other` | その他 | |
| `OAuth` | OAuth | 固有名詞。意図的に翻訳しない |
| `JWT OAuth` | JWT OAuth | 同上 |
| `SSL` | SSL | 略語。同上 |
| `SSO` | SSO | 同上 |
| `BulkAPI` | BulkAPI | API 名。同上 |

```kotlin
private fun categoryLabel(category: String): String = when (category) {
    "Authentication" -> "認証"
    "Connection" -> "接続"
    "Caching" -> "キャッシュ"
    "Firewall" -> "ファイアウォール"
    "Logging" -> "ログ"
    "Proxy" -> "プロキシ"
    "Schema" -> "スキーマ"
    "Other" -> "その他"
    else -> category   // 固有名詞・略語・未知のカテゴリはそのまま
}
```

`orderedCategories` のソートは現状の実装（生値で `when` 分岐）をそのまま使うため、
表示順は 認証 → 接続 → OAuth → その他 のまま変わらない。

`isAuth` の判定も生値 `"Authentication"` で行っているので影響しない。

### 2.4 `DriversView.kt` の対応表

| 箇所 | 現状 | 変更後 |
|---|---|---|
| `:35` pageTitle | `"Drivers"` | `"ドライバー"` |
| `:39` h2 | `"JDBC Drivers in ${ctx.libDir} (${drivers.size})"` | `"ドライバー (${drivers.size} 件)"` + 直下に `small.muted` で `"配置先: {libDir}"` |
| `:42` h3 | `"Upload new driver"` | `"ドライバーを追加"` |
| `:59` 空状態 | `"ドライバがまだ配置されていません。"` | `"ドライバーがまだ配置されていません。"` |
| `:62` table | — | `div(classes = "table-scroll")` で包む |
| `:65` th | `Filename` | `ファイル名` |
| `:66` th | `Driver Class` | `ドライバークラス` |
| `:67` th | `License` | `ライセンス` |
| `:68` th | `Size` | `サイズ` |
| `:69` th | `Action` | `操作` |
| `:76` td | `"(unknown)"` | `"(不明)"` |
| `:79` td | 操作セル | `cell-actions` クラスを付与 |
| `:100` pageTitle | `"Activate $filename"` | `"トライアルを有効化: $filename"` |
| `:104` h2 | `"Activate trial license"` | `"トライアルライセンスを有効化"` |
| `:107` | `"対象: "` | 変更なし（既に日本語） |
| `:117` label | `"Name:"` | `"お名前:"` |
| `:120` placeholder | `"Taro Yamada"` | `"山田 太郎"` |
| `:124` label | `"Email:"` | `"メールアドレス:"` |
| `:131` label | `"Product Key:"` | `"プロダクトキー:"` |
| `:133` value | `"TRIAL"` | **変更なし**（送信値。AC-16） |
| `:154` pageTitle | `"Activate result"` | `"有効化の結果"` |
| `:158` h2 | `"Activation Result"` | `"有効化の結果"` |
| `:166` | `"stdout (debug):"` | `"標準出力 (デバッグ用):"` |
| `:179` badge | `"●Activated"` | `"有効"` |
| `:180` badge | `"○Not activated"` | `"未有効化"` |
| `:181` badge | `"status-badge"` to `"Unknown"` | `"status-badge unknown"` to `"不明"` |

### 2.5 ライセンスバッジの `●` `○` 削除 (F-7)

`app.css` の `.status-badge::before` が既に 6px のドットを描画している。

```css
.status-badge::before {
  content: "";
  display: inline-block;
  width: 6px; height: 6px;
  border-radius: var(--r-full);
  background: currentColor;
}
```

そこに `●Activated` を入れると **ドット + ● の二重**になる。
`TablesView` の状態バッジ（`稼働中` / `停止中`）は記号を入れていないので、そちらに揃える。

### 2.6 `.status-badge.unknown` の追加 (F-8)

`LicenseStatus.UNKNOWN` は `"status-badge"` のみで、`.serving` / `.stopped` のような
背景色・文字色の指定が無く、`::before` のドットが `currentColor`（継承した本文色）になる。

```css
.status-badge.unknown {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}
```

「判定できなかった」状態なので、成功 (`serving`) でも停止 (`stopped`) でもなく
警告系のトークンを割り当てる。

---

## 3. CSS の変更設計 (`resources/static/app.css`)

Tables セクションの末尾に追記する。

```css
/* 横溢れ対策: 収まらないときはページ全体ではなくテーブル内をスクロールさせる */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

/* 長い値のセル (JDBC 接続文字列など) を 1 行省略表示にする。
   td に max-width を付けても table-layout: auto では効かないため、
   内側のブロック要素側で最小コンテンツ幅を縮める。 */
.cell-truncate > code,
.cell-truncate > span {
  display: block;
  max-width: 24rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作列: ボタンが縦に折り返さないようにする */
td.cell-actions {
  white-space: nowrap;
}

/* 状態不明のバッジ (.serving / .stopped に対する第 3 の状態) */
.status-badge.unknown {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}
```

モバイル幅では `24rem` が画面幅 (375px ≒ 26.8rem @13px) に近いので、
`@media (max-width: 768px)` で `max-width: 12rem` に縮める。
テーブル自体はラッパー内でスクロールするため、操作列にも到達できる。

---

## 4. ドキュメント変更設計

### 4.1 `docs/development-guidelines.md`

「3.4 Web UI ボタン規約」の後に 2 節を追加する。

- **3.5 Web UI 文言規約** — 画面の呼称表（ナビ・ヘルプが正）、件数表記 `(N 件)`、
  「ドライバー」の長音統一、翻訳しないもの（識別子・ドライバー由来の文字列）
- **3.6 テーブル表示規約** — `.table-scroll` で包む、長い値は `.cell-truncate` + `title`、
  操作列は `.cell-actions`、`td` への `max-width` が効かない理由

### 4.2 `docs/glossary.md`

「UI/UX 用語」に相当する節へ、本作業で確定した UI 操作語を追記する。

| 日本語 | 英語 | コード上の名前 | 説明 |
|---|---|---|---|
| データソース | Data Source | `/connections` | Web UI の画面名 |
| データソース接続 | Data Source Connection | `SharedJdbcConfig` | 共有 JDBC 設定 1 件 |
| ドライバー | Driver | `/drivers` | Web UI の画面名 |
| 接続文字列 | Connection String | `JdbcConfig.url` | JDBC URL |
| ライセンス状態 | License Status | `LicenseStatus` | 有効 / 未有効化 / 不明 |

### 4.3 `docs/` への影響

| ドキュメント | 影響 |
|---|---|
| `product-requirements.md` | なし（機能要件の変更なし） |
| `functional-design.md` | なし（画面遷移・データモデルの変更なし） |
| `architecture.md` | なし |
| `repository-structure.md` | なし（ファイル追加/削除なし） |
| `development-guidelines.md` | **3.5 / 3.6 を追記** |
| `glossary.md` | **UI 操作語を追記** |

---

## 5. テストへの影響

| テスト | 影響 | 対応 |
|---|---|---|
| `DriversAndConnectionsE2ETest.kt:14` | `text=Upload new driver` → `ドライバーを追加` | セレクタ更新 |
| `DriversAndConnectionsE2ETest.kt:22` | `text=New Connection` (`/connections/new` の h2) → `新しいデータソース接続` | セレクタ更新 |
| `DashboardE2ETest` | ダッシュボードの文言は変更しない | 対応不要 |
| `WizardE2ETest.kt:16` | `text=JDBC Connection が登録されていません` は `TableWizardView` 側の文言で本作業では触らない | 対応不要 |
| 単体テスト (`src/test`) | View を描画するテストは無い。`JdbcDriverManagerTest` は Kotlin API を見ており UI 文言に依存しない | 対応不要 |

---

## 6. リスクと対策

| リスク | 対策 |
|---|---|
| `title` 属性に生の接続文字列が入り、平文の資格情報が DOM に載る | `ConnectionStringMasker.mask()` を通した値のみを入れる（設計 1.4 で明記）。実装後に `title` の中身へ `***` が含まれることを確認する |
| 省略表示で一覧からデータソースを判別できなくなる | 先頭 24rem には `jdbc:bcart:AuthScheme=...` のようにスキーマ名が入るため判別可能。加えて `ドライバークラス` 列が別途ある |
| `max-width: 24rem` が将来のフォント変更で崩れる | `rem` 指定なので base font-size に追従する。px 固定にしない |
| カテゴリ日本語化で `orderedCategories` のソートが壊れる | ソートキーは生値のまま。`categoryLabel()` は表示時のみ適用し、`isAuth` 判定も生値で行う (C-5) |
| `LicenseStatus.UNKNOWN` に警告色を当てると「異常」に見える | 「不明」は実際に判定失敗を意味する（`.lic` ファイルの読み取りができていない）ため、注意を促す色で妥当 |
| `.table-scroll` の追加で `table tbody tr:hover` の背景が切れる | `overflow-x` はスクロールコンテナを作るだけで、行の背景は行全体に描画される。実機で確認する |
| ドライバー一覧の `h2` からパスを外すと配置先が分からなくなる | `small.muted` で直下に残す（F-6）。情報は失わない |
