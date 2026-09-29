# 既定値を保存しない接続フォーム — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | no-default-prefill |
| 対応 Issue | [#27](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/27) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. URL 組み立てのテスト可能化

- [x] **T-10** 🔴 `ConnectionFormUrlTest` を作成（未指定は保存しない）
- [x] **T-11** 🟢 `web/routes/ConnectionFormUrl.kt` に移設し、`ConnectionsRoutes` から参照
- [x] **T-12** 🔴🟢 直接入力の優先・`prop.` 以外を含めない・空の扱い

## Phase 2. フォームの描画

- [x] **T-20** テキスト / パスワード / 数値の既定値を `placeholder` に変更
- [x] **T-21** 選択肢つきプロパティの「未指定」に既定値を併記
- [x] **T-22** 真偽値を 3 択セレクト（未指定 / True / False）に変更
- [x] **T-23** 保存済みの値は従来どおり入力欄に入ることを確認

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-31** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-32** 新規作成画面の入力欄・プレビューを確認
- [x] **T-33** `AuthScheme` 未選択時に #14 の絞り込みが効くことを確認
- [x] **T-34** 既存接続の編集画面で保存済みの値が入ることを確認
- [x] **T-35** 新規接続を作って接続文字列に既定値が含まれないことを確認

## Phase 4. 仕上げ

- [x] **T-40** README に「既存接続は保存し直すと既定値の項目が落ちる」を記載
- [x] **T-41** コミット
- [x] **T-42** PR 作成・マージ、Issue #27 に結果を記録（方針変更の経緯も）

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 触っていない項目が保存されない | ✅ | 実機で新規作成 → `jdbc:googlesheets:AuthScheme=OAuth;SpreadsheetId=test-sheet-id;` のみ |
| AC-2 入力した項目は保存される | ✅ | 同上 |
| AC-3 既存接続の編集で保存値が維持される | ✅ | 編集画面で `value` / `selected` を確認 |
| AC-4 新規接続で #11 が効く | ✅ | 接続テストのログに `OAuthSettingsLocation=./run/oauth/verify-27.txt` |
| AC-5 未入力時の既定値が分かる | ✅ | `placeholder` 19 件 + `-- 未指定 (既定: OAuth) --` |
| AC-6 真偽値で「未指定」を選べる | ✅ | チェックボックス 0 件、3 択セレクトに置き換え |
| AC-7 編集時のデグレなし | ✅ | AC-3 と同じ確認 |
| AC-8 プレビューに未入力項目が出ない | ✅ | `jdbc:salesforce:AuthScheme=Basic;User=alice;` |
| AC-9 #14 の絞り込みが未入力でも動く | ✅ | `AuthScheme` 未選択で入力欄 84 件・`User` 非表示・必須は `Use Sandbox` のみ |
| AC-10 プール設定は変更なし | ✅ | `pool.*` は従来どおり既定値を表示 |
| AC-11 直接入力が優先される | ✅ | `ConnectionFormUrlTest` |
| AC-12 ユニットテスト | ✅ | `ConnectionFormUrlTest` 8 件 |
| AC-13 テストと lint | ✅ | `test` 成功。detekt は origin/main と同じ 84 件 |

### 実機での確認

修正前（既存の googlesheets 接続、約 30 プロパティ）:

```
jdbc:googlesheets:AuthScheme=OAuth;SpreadsheetId=...;IgnoreErrorValues=on;
InitiateOAuth=GETANDREFRESH;OAuthSettingsLocation=%APPDATA%\CData\GoogleSheets Data Provider\OAuthSettings.txt;
Pagesize=1000;RowScanDepth=50;BatchSize=0;...;CacheTolerance=600;
```

修正後（新規作成した接続）:

```
jdbc:googlesheets:AuthScheme=OAuth;SpreadsheetId=test-sheet-id;
```

接続テスト時に #11 の一本化が効く:

```
JDBC 接続プールを初期化: jdbc:googlesheets:AuthScheme=OAuth;SpreadsheetId=test-sheet-id;OAuthSettingsLocation=./run/oauth/verify-27.txt
```

検証で作成した `verify-27` 接続は削除済み。

### 方針の変更（Issue 記載の案から）

Issue の方針 1 は「`buildUrlFromForm` で既定値と一致する値を除外する」だったが、
**既定値を入力欄に埋めない**方式に変えた。

理由は方針 2 に挙げられていた懸念そのもの。`IgnoreErrorValues` は既定値 `true` に
対して `on` が送られてくるため、除外方式では真偽値の正規化が必要になり、
ドライバーごとの表記差に追従し続けることになる。そもそも送らせなければ比較が不要。

代償として、チェックボックスでは「未指定」を表現できないため
真偽値プロパティを 3 択セレクト（未指定 / True / False）に変更した。
