# 接続テストの資格情報検証 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#45](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/45) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 検証ロジック（TDD）

- [x] **T-10** 🔴 `ConnectionValidatorTest` を書く（MockK で `Connection` をモック）
- [x] **T-11** 🟢 `ConnectionValidation` と `ConnectionValidator.validate` を実装

## Phase 2. ルート

- [x] **T-20** 接続テストを検証結果で分岐させる
- [x] **T-21** 断片の組み立ては #42 の形（バナー + 接続名）を維持する

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** 誤った資格情報で失敗表示になることを確認（AC-1）
- [x] **T-33** 正しい資格情報で成功表示・製品名が出ることを確認（AC-2）
- [x] **T-34** ドライバー JAR が無い接続で失敗表示になることを確認（AC-4）
- [x] **T-35** 応答時間を測る（AC-5）
- [x] **T-36** 失敗メッセージのマスクを確認（AC-6）
- [x] **T-37** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #45 に結果を記録

---

## 実機検証の結果 (2026-09-29)

検証用の接続 `zz-badcreds`（誤った資格情報）と `zz-nojar`（ドライバー JAR 欠落）を
作って確認した（検証後に削除。`config.db` はバックアップと差分なしに復帰）。

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 / AC-3 | ✅ | `zz-badcreds` → `warning-banner` で「接続失敗: 接続は確立できましたが、データソースが応答しませんでした。資格情報…を確認してください。」**修正前は「接続成功」になっていた** |
| AC-2 | ✅ | `Salesforce1` → `接続成功: Salesforce 25.0.9540 / CData JDBC Driver for Salesforce 2025J 25.0.9540.0` ／ `GoogleSheetsOAuth` も成功 |
| AC-4 | ✅ | `zz-nojar` → `接続失敗: ドライバ JAR が見つかりません: ./lib/does-not-exist.jar` |
| AC-5 | ✅ | 誤った資格情報 1.58 秒 / Salesforce 1.11 秒 / Google Sheets 1.55 秒 / JAR 欠落 0.01 秒 |
| AC-6 | ✅ | `Password=wrongsecret` を仕込んだ接続でも `wrongsecret` が出ない |

### 応答時間の変化

検証前は `getMetaData()` のみで **0.006 秒**だった。`isValid` を加えて **1〜1.6 秒**になった。
検証していなかったものを検証するので必要なコスト。`getTables()` 方式なら
Google Sheets で 90 秒かかっていたため、この選択で妥当。

### 残る制約

`isValid` が `false` のとき**理由は取れない**。`getWarnings()` は `null` を返し、
理由が分かるのは実データへのクエリ（`SELECT * FROM Account LIMIT 1` →
`INVALID_LOGIN: Invalid username, password, security token; or user locked out.`）
だけだが、テーブル名を汎用に決められないため採用しなかった。
