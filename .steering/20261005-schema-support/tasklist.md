# スキーマの選択と保持 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#63](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/63) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 修飾名（TDD）

- [x] **T-10** 🔴 `SqlIdentifierTest` に `qualified` のテストを追加
- [x] **T-11** 🟢 `SqlIdentifier.qualified` を実装

## Phase 2. 設定と実行時クエリ（TDD）

- [x] **T-20** 🔴 `TableConfig.schema` と `qualifiedName()` のテスト（後方互換を含む）
- [x] **T-21** 🟢 `TableConfig` に省略可能な `schema` を追加
- [x] **T-22** 🔴🟢 `QueryBuilderTest` で `FROM [S].[T]` と後方互換を検証
- [x] **T-23** `QueryBuilder` の 5 箇所を `qualifiedName()` に置き換え

## Phase 3. メタデータ取得

- [x] **T-30** `listColumns` / `findPrimaryKey` / `distinctValues` にスキーマ引数を追加

## Phase 4. ウィザード

- [x] **T-40** step2 にスキーマ選択を追加（2 種類以上のときだけ）
- [x] **T-41** step2 → step3 → step4 → `POST /syncs` でスキーマを引き回す
- [x] **T-42** `substringAfter(".")` を 3 箇所から削除
- [x] **T-43** 保存時に `TableConfig.schema` を設定

## Phase 5. 検証

- [x] **T-50** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-51** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-52** SQL Server の step2 にスキーマ選択が出ることを確認（AC-2）
- [x] **T-53** `SalesLT.Customer` で step4 まで進み `CustomerID` が検出されることを確認（AC-1）
- [x] **T-54** 保存した `table_json` に `schema` が入ることを確認（AC-4）
- [x] **T-55** 単一スキーマの接続で選択 UI が出ないことを確認（AC-3）
- [x] **T-56** 既存のスキーマ無し連携が従来どおり動くことを確認（AC-7）
- [x] **T-57** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 6. 仕上げ

- [x] **T-60** `docs/functional-design.md` のデータモデルに `TableConfig.schema` を追記
- [x] **T-61** コミット
- [x] **T-62** PR 作成・マージ、Issue #63 に結果を記録

---

## 実機検証の結果 (2026-10-05)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | `SalesLT.Customer` で step4 が **HTTP 200**、主キー `CustomerID` を検出（**修正前は 500 で真っ白**） |
| AC-2 | ✅ | step2 にスキーマ選択が出る（`SalesLT` / `dbo`）。既定で `SalesLT` に絞り込み、テーブル 13 件 |
| AC-3 | ✅ | `googlesheets`（単一スキーマ）では選択 UI も hidden も出ない |
| AC-4 | ✅ | 保存された `table_json` が `name=Customer` / `schema=SalesLT` |
| AC-5 | ✅ | `QueryBuilderTest` で SELECT / INSERT / UPDATE / DELETE / COUNT が `[SalesLT].[Customer]` になることを確認 |
| AC-6 | ✅ | スキーマを独立した hidden で送るため、同名テーブルでも選択したスキーマが使われる |
| AC-7 | ✅ | `schema` を持つ連携 1 件 / 全 12 件の状態で連携一覧・詳細がいずれも HTTP 200 |
| AC-8 | ✅ | `SqlIdentifierTest`（9 件）／`QueryBuilderTest`（5 件追加） |
| AC-9 | ✅ | 495 テストパス、detekt は **83 件**（ベースライン 84 件より 1 件減） |

検証データ（`zz-sqlcustomer`）は削除し、連携一覧はバックアップと差分なし。

## 途中で直した 2 点

### 1. `distinctValues` の識別子クォートを戻した

当初 `SqlIdentifier.qualified` で修飾したが、H2 が角括弧クォートを受け付けず既存テストが
2 件落ちた。この関数はウィザードの選択肢検出用の補助クエリで、クォート形式の変更は
本件のスコープ外。スキーマは接頭辞として付けるだけに留めた（識別子をクォートしないのは
既存の制約で、本件では変えない）。

### 2. 引数を `TableInfo` に束ねた

`schema` を引数に足した結果、`wizardStep3View` が `LongParameterList` に引っかかった
（拡張関数のレシーバも 1 つとして数えられるため、宣言 6 個で閾値超過）。
`table: TableInfo` として束ねることで解消し、あわせて「テーブル名とスキーマは対で扱う」
ことがシグネチャに表れるようになった。

`wizardStep2View` の `LongMethod` も、スキーマ選択を `schemaSelector` に切り出して解消した。
