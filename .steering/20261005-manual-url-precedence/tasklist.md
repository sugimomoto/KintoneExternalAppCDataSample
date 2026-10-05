# 手動 URL 欄の優先順位の修正 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#59](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/59) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 優先順位の明示（TDD）

- [x] **T-10** 🔴🟢 `ConnectionFormUrlTest` に優先順位のテストを追加（空→プロパティ / 非空→手動）

## Phase 2. ビュー

- [x] **T-20** `manualUrlField` に `prefill` を追加する
- [x] **T-21** 併記時は `prefill = false` にする
- [x] **T-22** 併記時の案内文と現在値（マスク済み）を追加する

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** 🔴🟢 `PropertiesFormContentTest` で描画を検証（AC-2, AC-4, AC-5, AC-6）
- [x] **T-33** 縮退フォームの実機再現を試行 → #60 以降は発生しないと判明。描画テストに切り替え
- [x] **T-34** 優先順位は `ConnectionFormUrlTest` で担保（AC-1, AC-3）
- [x] **T-35** 案内文と現在値（マスク済み）を描画テストで確認（AC-6）
- [x] **T-36** 完全なプロパティフォームの接続が従来どおりであることを確認（AC-5）
- [x] **T-37** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #59 に結果を記録

---

## 実機検証の結果 (2026-10-05)

### 検証方針を途中で変えた

縮退フォームを実機で再現しようとしたが、**#60 で config 接続文字列に切り替えた結果、
手元の全ドライバーで `config:` が成功し縮退が起きなくなっていた**。未認証の Jira でも
`sys_connection_props` はライセンス不要のため取得でき、縮退しなかった。

```
zz-degraded (cdata.jdbc.jira.JiraDriver) → 縮退バナー: なし / 手動URL欄: なし
```

実行時に到達できないパスを「実機で確認」とするのは成立しないため、
`propertiesFormContent` の描画を単体テストで直接検証する方針に変えた。

### 受け入れ条件

| AC | 結果 | 確認方法 |
|---|---|---|
| AC-1 | ✅ | `ConnectionFormUrlTest`（手動 URL が空 → プロパティから組み立て）＋ 描画テスト（事前入力しない）の組み合わせ |
| AC-2 | ✅ | `PropertiesFormContentTest`「併記時は手動 URL 欄を事前入力しない」 |
| AC-3 | ✅ | `ConnectionFormUrlTest`「手動 URL が入力されていればプロパティより優先される」 |
| AC-4 | ✅ | `PropertiesFormContentTest`「プロパティが無いときは従来どおり事前入力する」 |
| AC-5 | ✅ | 描画テスト＋実機（`SQLServer` / `Salesforce1` ともに手動 URL 欄 0 個） |
| AC-6 | ✅ | 描画テスト（案内文・現在の値がマスクされ `secret123` が出ない） |
| AC-8 | ✅ | 477 テストパス、detekt はベースライン 84 件のまま |

`config.db` はバックアップと差分なし。
