# データソース / ドライバー画面の日本語化とテーブル横溢れの解消 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | i18n-and-table-overflow |
| 作成日 | 2026-09-29 |
| 対応する設計 | `design.md` |

進捗記号: `[ ]` 未着手 / `[~]` 着手中 / `[x]` 完了

---

## Phase 1: CSS (課題 A の基盤 + バッジ)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 1-1 | `.table-scroll` を追加 | `overflow-x: auto`。収まるときは見た目が変わらない | [x] |
| 1-2 | `.cell-truncate > code` / `> span` の省略表示を追加 | `max-width: 24rem` + `text-overflow: ellipsis`。内側ブロック方式 | [x] |
| 1-3 | `td.cell-actions { white-space: nowrap }` を追加 | 操作列のボタンが折り返さない | [x] |
| 1-4 | `.status-badge.unknown` を追加 | 警告系トークンで背景色・文字色が付く | [x] |
| 1-5 | モバイル幅で `.cell-truncate` の `max-width` を `12rem` に縮める | 375px でテーブルが極端に広がらない | [x] |

## Phase 2: 課題 A (ConnectionsView / DriversView のテーブル)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 2-1 | `ConnectionsView` のテーブルを `.table-scroll` で包む | AC-6 | [x] |
| 2-2 | 接続文字列セルに `cell-truncate` と `title`（**マスク済みの値のみ**）を付与 | AC-4。`title` に `***` が含まれる | [x] |
| 2-3 | `ConnectionsView` の操作セルに `cell-actions` を付与 | AC-5 | [x] |
| 2-4 | `DriversView` のテーブルを `.table-scroll` で包む | AC-6 | [x] |
| 2-5 | `DriversView` の操作セルに `cell-actions` を付与 | AC-5 | [x] |

## Phase 3: 課題 B (ConnectionsView の日本語化)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 3-1 | pageTitle を `データソース` / `新しいデータソース接続` / `データソース接続を編集: {name}` に | AC-15 | [x] |
| 3-2 | h2 を `データソース接続 (N 件)` に | AC-7 | [x] |
| 3-3 | 列名 4 つを日本語化 | AC-7 | [x] |
| 3-4 | 空状態の文言を `データソース接続がまだ登録されていません。` に | — | [x] |
| 3-5 | 入力ラベル（名前 / JDBC ドライバー / JDBC 接続文字列）を日本語化 | AC-8 | [x] |
| 3-6 | `Pool Settings` を `接続プール設定`、配下 2 ラベルを日本語化 | AC-8 | [x] |
| 3-7 | `-- ドライバを選択 --` を長音付きに、`Drivers 画面` を `ドライバー画面` に | — | [x] |
| 3-8 | `categoryLabel()` を追加し、カテゴリ見出しと `summary` に適用 | AC-14。ソートキーは生値のまま | [x] |
| 3-8b | **(検証中に判明)** カテゴリ辞書を実測 13 種に拡張 | 当初 4 種のみ登録したところ 6 種が英語のまま残った。全ドライバーの `/connections/properties` を収集して網羅 | [x] |
| 3-9 | `props` 単位を `件` に、プロパティ説明文を日本語化 | — | [x] |
| 3-10 | 機微情報バッジを `パスワード` / `機微情報` に | — | [x] |

## Phase 4: 課題 B (DriversView の日本語化)

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 4-1 | pageTitle 3 つを日本語化 | AC-15 | [x] |
| 4-2 | h2 を `ドライバー (N 件)` にし、配置先パスを `small.muted` で直下に分離 | AC-9。見出しが 1 行に収まる | [x] |
| 4-3 | `Upload new driver` を `ドライバーを追加` に | — | [x] |
| 4-4 | 列名 5 つを日本語化 | AC-10 | [x] |
| 4-5 | `(unknown)` を `(不明)` に、空状態を長音付きに | — | [x] |
| 4-6 | 有効化画面の h2 と 3 ラベル、placeholder を日本語化 | AC-13 | [x] |
| 4-7 | 結果画面の h2 と `stdout (debug):` を日本語化 | AC-13 | [x] |
| 4-8 | `licenseBadge()` のラベルを `有効` / `未有効化` / `不明` にし、`●` `○` を削除 | AC-11 | [x] |
| 4-9 | `LicenseStatus.UNKNOWN` に `unknown` クラスを付与 | AC-12 | [x] |
| 4-10 | `TRIAL` / ドライバークラス名 / 接続文字列を翻訳していないことを確認 | AC-16 | [x] |

## Phase 5: ドキュメント

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 5-1 | `docs/development-guidelines.md` に「3.5 Web UI 文言規約」を追記 | 呼称表・件数表記・長音・翻訳しないものを含む | [x] |
| 5-2 | `docs/development-guidelines.md` に「3.6 テーブル表示規約」を追記 | `td` への `max-width` が効かない理由を含む | [x] |
| 5-3 | `docs/glossary.md` に UI 操作語を追記 | データソース接続 / ドライバー / 接続文字列 / ライセンス状態 | [x] |

## Phase 6: テスト追随

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 6-1 | `DriversAndConnectionsE2ETest.kt:14` を `ドライバーを追加` に | AC-19 | [x] |
| 6-2 | `DriversAndConnectionsE2ETest.kt:22` を `新しいデータソース接続` に | AC-19 | [x] |
| 6-3 | 他の E2E が影響を受けないことを再確認 | `design.md` 5 章の表と一致 | [x] |

## Phase 7: 品質チェック

| # | タスク | 結果 | 状態 |
|---|---|---|---|
| 7-1 | `./gradlew test` | BUILD SUCCESSFUL | [x] |
| 7-2 | `./gradlew compileBrowserTestKotlin` | BUILD SUCCESSFUL | [x] |
| 7-3 | `./gradlew detekt` | **既存の違反により FAILED**。ただし 90 → **88** と 2 件減少し、新規違反は 0。減少分は `ConnectionsView.kt:77` と `DriversView.kt:85` の `MaxLineLength` で、`form(...)` 呼び出しを複数行に整形したことで解消 | [x] |
| 7-4 | 1024px で `/connections` の幅を実測 | `main` 1024px / `table` **4301px → 1024px** / `documentElement.scrollWidth` **4301 → 1024**（ページ全体の横スクロール解消） | [x] |
| 7-5 | 行内の「接続テスト」「編集」がビューポート内にあることを実測 | 「接続テスト」右端 937px、`ボタンが画面内: true` | [x] |
| 7-6 | `title` 属性にマスク済みの値だけが入っていることを確認 | `title` 先頭 = `jdbc:bcart:AuthScheme=PersonalAccessToken;PersonalAccessToken=***;...`。`***` を含み平文の資格情報なし | [x] |
| 7-7 | 375px で `/connections` と `/drivers` を確認 | `documentElement.scrollWidth` 375 = ビューポート（ページは横スクロールしない）、ラッパーは `clientWidth` 359 / `scrollWidth` 669 でテーブル内スクロール | [x] |
| 7-8 | ダークモードで確認 | `.status-badge.unknown` は `bg #3a2410` / `fg #b85c00`、コントラスト比 **3.17**。既存の「有効」バッジ (2.60) より良好で、バッジ族の既存パターンに整合 | [x] |
| 7-9 | 残った英語表記の grep 確認 | 残存は `CData`（社名）と `JDBC`（略語）のみ。AC-16 の想定どおり | [x] |
| 7-10 | 受け入れ条件 AC-1 〜 AC-19 の突き合わせ | AC-18 の「detekt の違反が増えていない」はクリア（減少）。detekt 自体は既存違反で FAILED のまま。他は全項目クリア | [x] |
| 7-11 | **(追加)** カテゴリ 13 種の表示を全ドライバーで実測 | 8 種が日本語化、5 種 (`OAuth` / `JWT OAuth` / `SSL` / `SSO` / `BulkAPI`) は固有名詞・略語として意図的に英語のまま | [x] |

## Phase 8: 環境反映

| # | タスク | 完了条件 | 状態 |
|---|---|---|---|
| 8-1 | `docker compose build adapter-console` | イメージが更新される | [x] |
| 8-2 | `docker compose up -d adapter-console` | コンテナが再作成され、Web UI が 8080 で応答する | [x] |
| 8-3 | Adapter が `active-adapters.json` から正常復帰することを確認 | `BindException` **0 件**、稼働中 7 件で復帰 | [x] |

---

## 対象外の確認事項 (作業中に触らないもの)

- `web/routes/*.kt` … ルーティングは変更しない (C-1)
- フォームの `name` 属性 (`prop.*` / `pool.*` / `jdbc.url.manual`) (C-2)
- `pico.min.css` (C-3)
- `.status-badge` のクラス名 (C-7)
- CData ドライバ由来の `propertyName` / `shortDescription` の翻訳
- ドライバークラス名 / JDBC 接続文字列 / `TRIAL`
- データソース一覧の空状態を `.empty-state` に揃える作業
- ページネーション / 検索 … `.steering/20260929-table-list-pagination-search/`
- detekt の既存違反 90 件

---

## 検証中に判明したこと

### カテゴリ辞書の網羅漏れ (3-8b)

`design.md` 2.3 の当初案は `Authentication` / `Connection` / `OAuth` / `Other` の 4 種だった。
これは `orderedCategories` のソート用 `when` 分岐に出てくる 4 種を根拠にしたもので、
**実際にドライバーが返すカテゴリの一覧ではなかった**。

実機で `/connections/properties` を同梱 4 ドライバー分たたいて収集した結果 **13 種**あり、
`Caching` / `Firewall` / `Logging` / `Proxy` / `Schema` / `SSL` などが
`else -> category` のフォールバックで英語のまま表示されていた。

```
修正前: [認証] [OAuth (11 件)] [その他 (16 件)] [SSL (1 件)] [Firewall (5 件)]
        [Proxy (8 件)] [Logging (5 件)] [Schema (4 件)] [Caching (7 件)]
                        ↑ 8 個中 6 個が英語

修正後: [認証] [OAuth (11 件)] [その他 (16 件)] [SSL (1 件)] [ファイアウォール (5 件)]
        [プロキシ (8 件)] [ログ (5 件)] [スキーマ (4 件)] [キャッシュ (7 件)]
```

`else -> category` のフォールバックは壊れない代わりに**翻訳漏れを画面上で目立たなくする**。
この教訓は `docs/development-guidelines.md` 3.5.2 に記載した。

## 本作業では対応しなかった追加の発見

| 発見 | 内容 | 扱い |
|---|---|---|
| ダークモードの状態バッジ全体がコントラスト不足 | 「有効」= 2.60、「未有効化」、`TablesView` の「稼働中」/「停止中」も同様に WCAG AA (4.5:1) 未達。フォントも 0.6875rem (約 9.6px) と小さい。本作業で追加した `.unknown` (3.17) は既存パターンに合わせたもので、**この族全体の既存課題**。修正には `@media (prefers-color-scheme: dark)` 内で `--color-success` / `--color-warning` / `--color-danger` の前景色を明るくする必要があり、`.warning-banner` や `.property-row .required` など他の利用箇所にも波及する | 別 Issue（アクセシビリティ改善としてまとめて扱うのが適切） |
| データソース一覧の空状態が `.empty-state` でない | `TablesView` は `.empty-state`（アイコン + 見出し + 誘導ボタン）だが、`ConnectionsView` は素の `<p>` 1 行。文言のみ日本語化した | 別 Issue（`requirements.md` 3.2 でスコープ外と明記） |
| `GET /connections/{name}` が編集フォームを返す | 詳細表示専用ビューが無く、一覧の名前リンクが編集画面に飛ぶ。省略表示した接続文字列の全文参照先として機能はしている | 別 Issue（`requirements.md` 3.2 でスコープ外と明記） |
| detekt の既存違反 88 件 | `LongMethod`（`helpView` 177 行など）/ `MagicNumber` / `UnusedParameter` | 別 Issue |
