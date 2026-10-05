# ウィザードの失敗を画面に出す — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#64](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/64) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 結果型

- [x] **T-10** `Step4Result` を追加（`Ready` / `NoPrimaryKey` / `Failed`）
- [x] **T-11** step4 の算出を関数に切り出し、例外を投げないようにする

## Phase 2. ビュー（TDD）

- [x] **T-20** 🔴 `WizardErrorTest` で失敗表示の描画を検証
- [x] **T-21** 🟢 `wizardStep3View` にエラー表示を追加（`Step3Content` で引数を束ねる）
- [x] **T-22** 主キーが無い場合の案内文を実装（仕様根拠を含める）

## Phase 3. ルート

- [x] **T-30** step4 で結果型により分岐する
- [x] **T-31** 例外は `ConnectionStringMasker` を通して `Failed` に畳み込む

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（83 件以下）
- [x] **T-41** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-42** 主キーの無いテーブルで 200 + 理由が出ることを確認（AC-1〜AC-4）
- [x] **T-43** 正常なテーブルが従来どおり step4 に進むことを確認（AC-7）
- [x] **T-44** 取得失敗時に理由が出ることを確認（AC-5）

## Phase 5. 仕上げ

- [x] **T-50** コミット
- [x] **T-51** PR 作成・マージ、Issue #64 に結果を記録

---

## 実機検証の結果 (2026-10-05)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | 主キーの無いビュー `vGetAllCategories` で **HTTP 200**（従来は 500 で真っ白） |
| AC-2 | ✅ | メッセージに `"vGetAllCategories"` が含まれる |
| AC-3 | ✅ | 「kintone の「外部システムのアプリ化」はレコード番号を必須とするため、レコード番号に使える列が無いテーブル・ビューは読み取り専用でも連携できません」 |
| AC-4 | ✅ | step3 の画面に戻り、戻る導線あり |
| AC-5 | ✅ | 到達不能な接続で `Failed to initialize pool: CORE Unable to connect to localhost:1433: Connection refused` が表示される |
| AC-6 | ✅ | `Password=secret123` を仕込んだ接続でも `secret123` が画面に出ない |
| AC-7 | ✅ | `SalesLT.Customer` は Step 4 に進み主キー `CustomerID`、警告バナーは出ない |
| AC-8 | ✅ | 500 テストパス、detekt は **82 件**（#63 完了時点の 83 件よりさらに 1 件減） |

検証データ（`zz-unreachable`）は削除し、`config.db` はバックアップと差分なし。

## 分かった限界（本 Issue では対応しない）

**存在しないテーブル名を渡すと「主キーが定義されていない」と表示される。**

```
テーブル "NoSuchTable" には主キーが定義されていないため、連携を作成できません。
```

`getPrimaryKeys` は存在しないテーブルでも例外を投げず 0 件を返すため、
「テーブルが無い」と「主キーが無い」を区別できていない。

ただしウィザードはテーブル一覧から選ばせる作りなので、存在しないテーブル名が
渡るのは直接 POST した場合に限られる。区別するには `getTables` を追加で引く必要があり、
正常系に往復を増やすことになるため見送る。
