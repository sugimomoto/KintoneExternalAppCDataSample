# QueryPassthrough の注意書き — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#69](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/69) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 0. 原因の切り分け（完了）

- [x] **T-01** `QueryPassthrough` の既定値が `true` であることを確認
- [x] **T-02** `False` で `LIMIT` が通ることを確認
- [x] **T-03** `Name` 列は表示名で `PropertyName` 列が識別子であることを確認

## Phase 1. 判定（TDD）

- [x] **T-10** 🔴 `QueryPassthroughAdviceTest` を書く（退避済み）
- [x] **T-11** 🔴🟢 `JdbcUrlEnhancer.propertyOf` を追加
- [x] **T-12** 🟢 `QueryPassthroughAdvice` を実装

## Phase 2. ビュー

- [x] **T-20** 接続フォームに注意書きを描画

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（78 件以下）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** 明示設定を外した接続で注意書きが出ることを確認（AC-1〜AC-3）
- [x] **T-33** 設定済みの接続で出ないことを確認（AC-4）
- [x] **T-34** Google Sheets で出ないことを確認（AC-5）
- [x] **T-35** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #69 に結果を記録

---

## 実機検証の結果 (2026-10-05)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 / AC-2 / AC-3 | ✅ | 明示設定を外した SQL Server 接続で注意書きが出る。設定方法（`QueryPassthrough` を False に）と放置した結果（「kintone からレコードを読んだ時点で初めて失敗します」）の両方を含む |
| AC-4 | ✅ | `QueryPassthrough=False` 設定済みの `SQLServer` では出ない |
| AC-5 | ✅ | `googlesheets` / `Salesforce1` では出ない |
| AC-8 | ✅ | 532 テストパス、detekt は **78 件**（変化なし） |

実際の表示:

> このデータソースは QueryPassthrough が既定で有効です。クエリがそのままデータソースへ
> 渡されるため、レコードの参照時に "Incorrect syntax near 'LIMIT'" のようなエラーに
> なります。接続テストやウィザードは成功するため、kintone からレコードを読んだ時点で
> 初めて失敗します。下のプロパティで QueryPassthrough を False に設定してください。

検証用の接続（`zz-nopt`）は削除し、`config.db` はバックアップと差分なし。

## 途中で直した点

### メッセージのエスケープ

当初 `'Incorrect syntax near \'LIMIT\''` と書いたため画面に `\'LIMIT\'` と
バックスラッシュが出た。Kotlin の二重引用符文字列では単一引用符のエスケープは
不要。二重引用符で囲む形に直した。

### detekt の `CyclomaticComplexMethod`

注意書きの条件を `connectionFormView` にインラインで書いたところ、複雑度が
閾値を超えた。`queryPassthroughNotice` に切り出して解消した。
