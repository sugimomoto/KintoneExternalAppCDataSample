# RPC の例外を見えるようにする — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#70](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/70) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 説明文の生成（TDD）

- [x] **T-10** 🔴 `RpcErrorsTest` を書く（マスク・null・空文字・クラス名）
- [x] **T-11** 🟢 `RpcErrors` を実装

## Phase 2. 全 RPC への適用

- [x] **T-20** `withRpcErrors` ラッパを追加
- [x] **T-21** 9 メソッドすべてを囲む
- [x] **T-22** `select` の既存 try/catch をラッパに置き換える

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（82 件以下）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** `modified_date` を含む Update でメッセージ付きエラーが返ることを確認（AC-1, AC-2）
- [x] **T-33** ログに例外とスタックトレースが出ることを確認（AC-1）
- [x] **T-34** `select` が従来どおり動くことを確認（AC-7）
- [x] **T-35** 主キーを省いた Update で `INVALID_ARGUMENT` が返ることを確認（AC-4）

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #70 に結果を記録

---

## 実機検証の結果 (2026-10-05)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | `ERROR ... Update failed with unexpected exception` とスタックトレースがログに出る |
| AC-2 | ✅ | `Code: Internal` / `Message: DATA_SOURCE The SQL Error Number is 241 ... 'Conversion failed when converting date and/or time from character string.'`（**修正前は `Unknown` / 空**） |
| AC-3 | ✅ | 9 メソッドすべてが `withRpcErrors` を通る（`grep` で確認） |
| AC-4 | ✅ | 主キーを省いた Update → `Code: InvalidArgument` / `Message: Update には主キー（id）が必要です`（そのまま返る） |
| AC-7 | ✅ | `Select` が従来どおりレコードを返す |
| AC-9 | ✅ | 510 テストパス、detekt は **78 件**（82 件から 4 件減） |

## detekt が 4 件減った理由

9 メソッドの本体をラッパで囲んだ結果、`LongMethod` や `CyclomaticComplexMethod` の
対象になっていたメソッドが構造的に変わり、閾値を下回ったため。

## 設計上の確認事項

ラッパを `inline` にしているため、本体の `return` は**非ローカル return** として
そのまま機能する。例外ではないので catch は走らず、意図どおり呼び出し元へ返る。
`inline` でなければ `return@withRpcErrors` が必要になり、9 箇所の書き換えが
煩雑になっていた。
