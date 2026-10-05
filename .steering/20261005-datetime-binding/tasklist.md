# DATETIME の束縛を文字列に統一する — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#71](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/71) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 0. 原因の切り分け（完了）

- [x] **T-01** `setObject` / `setTimestamp` / `setObject(_, TIMESTAMP)` がすべて失敗することを確認
- [x] **T-02** 文字列束縛とリテラルが通ることを確認
- [x] **T-03** `QueryPassthrough` に依存しないことを確認
- [x] **T-04** 日時フィルタ（`WHERE ... > ?`）も同じ原因で失敗することを確認
- [x] **T-05** SaaS コネクタでの検証可否を調査（いずれも不可と判明）

## Phase 1. 変換（TDD）

- [x] **T-10** 🔴 `SqlDateTimeTest` を書く（タイムゾーンを引数化して環境非依存に）
- [x] **T-11** 🟢 `SqlDateTime` を実装

## Phase 2. 適用

- [x] **T-20** `RowMapper.fieldToValue` を文字列にする
- [x] **T-21** `FilterTranslator.timestampToSql` を文字列にする
- [x] **T-22** 既存テスト（`RowMapperTest` / `FilterTranslatorTest`）を追従

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（78 件以下）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** `modified_date` を含む Update が成功することを確認（AC-1）
- [x] **T-33** 書き込まれた値のタイムゾーンを確認（AC-6）
- [x] **T-34** 日時フィルタを含む Select が成功することを確認（AC-2）
- [x] **T-35** Insert で DATETIME が通ることを確認（AC-3）
- [x] **T-36** 文字列フィールドの更新を確認（AC-4）
- [x] **T-37** 検証データを片付け、元の値に復元

## Phase 4. 仕上げ

- [ ] **T-40** コミット
- [ ] **T-41** PR 作成・マージ、Issue #71 に結果を記録

---

## 実機検証の結果 (2026-10-05)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | `modified_date` を含む Update が成功（**修正前は `Conversion failed`**） |
| AC-2 | ✅ | `datetimeGreaterThan` を含む Select がレコードを返す |
| AC-3 | ✅ | Insert が成功し ID `30122` が発行された |
| AC-4 | ✅ | `last_name` の更新が従来どおり成功 |
| AC-6 | ✅ | `2026-10-05T12:34:56Z` → DB 上 `2026-10-05T21:34:56`（TZ=Asia/Tokyo）。従来の解釈と一致 |
| AC-8 | ✅ | 526 テストパス、detekt は **78 件**（変化なし） |

検証データ（`zz Probe Co`）は削除し、`CustomerID=1` の `LastName` / `ModifiedDate` も
元の値（`Gee` / `2005-08-01T00:00:00`）に復元。

## 残るリスク

**SaaS コネクタでの日時の書き込み・フィルタは検証できなかった。** 現在 `Timestamp` 束縛で
動いているものを文字列に変えたため、回帰の可能性が残る。Google Sheets で kintone から
日時フィルタを試していただくのが確実。
