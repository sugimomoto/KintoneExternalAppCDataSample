# 認証方式に応じた接続プロパティの動的制御 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | auth-scheme-aware-properties |
| 対応 Issue | [#14](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/14) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. 条件のパース

- [x] **T-10** 🔴 `PropertyHierarchyTest` を作成（正常系のパース）
- [x] **T-11** 🟢 `jdbc/PropertyHierarchy.kt` を実装
- [x] **T-12** 🔴🟢 異常系（空文字 / `=` 無し / 空白混じり）と `accepts` の大文字小文字

## Phase 2. 解決ロジック

- [x] **T-20** 🔴 `PropertyHierarchyResolverTest`（条件なしは素通し / 条件成立は残る）
- [x] **T-21** 🟢 `jdbc/PropertyHierarchyResolver.kt` の `resolve` を実装
- [x] **T-22** 🔴🟢 既定値による判定と入力値の優先
- [x] **T-23** 🔴🟢 連鎖の解決（依存先が非表示なら自身も非表示）
- [x] **T-24** 🔴🟢 依存先が一覧に無い / 循環参照のガード
- [x] **T-25** 🔴🟢 値を持つプロパティの保持と `required=false`
- [x] **T-26** 🔴🟢 `dependencyNames`
- [x] **T-27** 🔵 Refactor（メモ化と循環ガードの整理）

## Phase 3. 画面への適用

- [x] **T-30** `propertiesFormContent` で `resolve` を通す
- [x] **T-31** 件数表示に非表示件数を出す
- [x] **T-32** 依存先の入力欄に `property-dependency` クラスを付ける
- [x] **T-33** `#properties-form-container` に再描画トリガーを付ける

## Phase 4. ルーティング

- [x] **T-40** `/connections/properties` を POST 化し、フォーム値と `prefill` を受け取る
- [x] **T-41** ドライバー選択を `hx-post` + `hx-include` に変更
- [x] **T-42** `GET /connections/properties` を削除

## Phase 5. 検証

- [x] **T-50** `./gradlew test` / lint（新規指摘が無いこと）
- [x] **T-51** `src/browserTest` が `/connections/properties` に依存していないか確認
- [x] **T-52** 実ドライバーで手動確認（Salesforce の 3 パターン、値の保持、ドライバー切替）

## Phase 6. 仕上げ

- [x] **T-60** `docs/` への影響確認
- [x] **T-61** コミット
- [x] **T-62** Issue #14 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 既定 (OAuth) で User / Password / SecurityToken が出ない | ✅ | 実機: 入力欄 84 件、必須は `Use Sandbox` のみ |
| AC-2 `Basic` で 3 つが必須表示される | ✅ | 実機: 必須 = User / Password / Security Token / Use Sandbox |
| AC-3 `OAuthJWT` で OAuthJWTCert 系が出る | ✅ | 実機確認済み |
| AC-4 `AuthScheme` 以外の条件も効く | ✅ | 実機: `UseBulkAPI=True` で `BulkPollingInterval` が出現 |
| AC-5 条件の連鎖 | ✅ | `PropertyHierarchyResolverTest` |
| AC-6 条件なしのプロパティはデグレなし | ✅ | 同上 |
| AC-7 大文字小文字を無視 | ✅ | `PropertyHierarchyTest` / `PropertyHierarchyResolverTest` |
| AC-8 変更でフォームが再描画される | ✅ | ブラウザ実機: `AuthScheme` を Basic に変更 → 84 件 → 78 件、User が出現 |
| AC-9 入力済みの値が残る | ✅ | ブラウザ実機: `APIVersion=60.0` が再描画後も保持 |
| AC-10 ドライバー切替で値を引き継がない | ✅ | `prefill=false` で入力値を捨てる |
| AC-11 保存済みの値は条件を満たさなくても残る | ✅ | 実機: `AuthScheme=OAuth` + `User=legacy@example.com` の接続を編集 → User は表示され必須マークなし |
| AC-12 条件を満たさず値も無いものは送らない | ✅ | 入力欄自体を出さないため接続文字列に含まれない |
| AC-13 ユニットテスト | ✅ | `PropertyHierarchyTest` 9 件 / `PropertyHierarchyResolverTest` 17 件 |
| AC-14 テストと lint | ✅ | `test` 成功。detekt の指摘数は変更前後で 85 件（増減なし） |

### 実装で変わった判断

- 再描画トリガーは**コンテナではなく入力欄側**に置いた。設計時は
  `hx-trigger="change from:.property-dependency"` をコンテナに付ける想定だったが、
  htmx 1.9 はこのセレクタを要素の初期化時にしか解決せず、
  後から差し込まれた入力欄を拾わない（ブラウザ実機で再描画されないことを確認）。
- その結果、条件に使われる入力欄はプレビュー更新を担えなくなるため、
  プロパティフォームの応答で `#url-preview-container` を out-of-band 差し替えする。

### 補足

`ktlintCheck` は**本変更前から** main / browserTest / build.gradle.kts に
895 件の指摘があり失敗する（新しい ktlint 標準ルールに既存コードが追随していない）。
本変更では周囲のコードと同じ書き方に揃え、一括整形は行っていない。
