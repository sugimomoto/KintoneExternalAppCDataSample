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

- [ ] **T-10** 🔴 `PropertyHierarchyTest` を作成（正常系のパース）
- [ ] **T-11** 🟢 `jdbc/PropertyHierarchy.kt` を実装
- [ ] **T-12** 🔴🟢 異常系（空文字 / `=` 無し / 空白混じり）と `accepts` の大文字小文字

## Phase 2. 解決ロジック

- [ ] **T-20** 🔴 `PropertyHierarchyResolverTest`（条件なしは素通し / 条件成立は残る）
- [ ] **T-21** 🟢 `jdbc/PropertyHierarchyResolver.kt` の `resolve` を実装
- [ ] **T-22** 🔴🟢 既定値による判定と入力値の優先
- [ ] **T-23** 🔴🟢 連鎖の解決（依存先が非表示なら自身も非表示）
- [ ] **T-24** 🔴🟢 依存先が一覧に無い / 循環参照のガード
- [ ] **T-25** 🔴🟢 値を持つプロパティの保持と `required=false`
- [ ] **T-26** 🔴🟢 `dependencyNames`
- [ ] **T-27** 🔵 Refactor（メモ化と循環ガードの整理）

## Phase 3. 画面への適用

- [ ] **T-30** `propertiesFormContent` で `resolve` を通す
- [ ] **T-31** 件数表示に非表示件数を出す
- [ ] **T-32** 依存先の入力欄に `property-dependency` クラスを付ける
- [ ] **T-33** `#properties-form-container` に再描画トリガーを付ける

## Phase 4. ルーティング

- [ ] **T-40** `/connections/properties` を POST 化し、フォーム値と `prefill` を受け取る
- [ ] **T-41** ドライバー選択を `hx-post` + `hx-include` に変更
- [ ] **T-42** `GET /connections/properties` を削除

## Phase 5. 検証

- [ ] **T-50** `./gradlew test` / lint（新規指摘が無いこと）
- [ ] **T-51** `src/browserTest` が `/connections/properties` に依存していないか確認
- [ ] **T-52** 実ドライバーで手動確認（Salesforce の 3 パターン、値の保持、ドライバー切替）

## Phase 6. 仕上げ

- [ ] **T-60** `docs/` への影響確認
- [ ] **T-61** コミット
- [ ] **T-62** Issue #14 に結果を記録
