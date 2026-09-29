# 接続プロパティ取得のフォールバック — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | connection-props-fetch-fallback |
| 対応 Issue | [#15](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/15) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。各 Phase はテスト → 実装の順。

---

## Phase 0. 準備

- [ ] **T-00** ブランチ `fix/15-connection-props-fetch-fallback` を作成
- [ ] **T-01** 現行の `sys_connection_props` 取得件数を実ドライバ 4 本で記録（回帰比較の基準値）

## Phase 1. 値オブジェクトと純粋関数（TDD の主戦場）

- [ ] **T-10** `jdbc/DriverProperty.kt` を作成（`DriverPropertyInfo` からの変換、名前 trim）
- [ ] **T-11** 🔴 `ConnectionPropertyProbeTest` に `dummyValueFor` のテストを追加（URL 系）
- [ ] **T-12** 🟢 `jdbc/ConnectionPropertyProbe.kt` に `dummyValueFor` を実装
- [ ] **T-13** 🔴🟢 `dummyValueFor` の残りのケース（真偽値系 / Port / 汎用）を追加・実装
- [ ] **T-14** 🔴 `candidateUrls` のテストを追加（安全プロパティのみの候補が先頭）
- [ ] **T-15** 🟢 `candidateUrls` を実装
- [ ] **T-16** 🔴🟢 `candidateUrls` の残りのケース（安全プロパティ無し / ダミー値付き候補 / 重複排除 / `;` を含めない）
- [ ] **T-17** 🔵 Refactor（`SAFETY_PROPERTIES` の定義位置、ヒューリスティックの表現）

## Phase 2. 機密判定の共有

- [ ] **T-20** 🔴 `ConnectionStringMaskerTest` に `isSensitive` の直接テストを追加
- [ ] **T-21** 🟢 `ConnectionStringMasker.isSensitive` を公開に変更（既存の `mask` のテストが緑のままであること）

## Phase 3. 縮退マッピング

- [ ] **T-30** 🔴 `DegradedPropertyMapperTest` を作成（名前 trim / `required` 引き継ぎ）
- [ ] **T-31** 🟢 `jdbc/DegradedPropertyMapper.kt` を実装
- [ ] **T-32** 🔴🟢 機密判定（`PASSWORD` / `NONE`）のテスト・実装
- [ ] **T-33** 🔴🟢 `defaultValue` にダミー値を持ち込まないこと・`ordinal` の順序保持

## Phase 4. IO 境界の切り出し

- [ ] **T-40** `jdbc/DriverMetadataSource.kt` にインターフェースを定義
- [ ] **T-41** 同ファイルに `JdbcDriverMetadataSource`（`DriverManager` 実装）を追加。
      `sys_connection_props` のクエリと `parseRow` は現行の `JdbcConnectionPropertyInspector` から移設
- [ ] **T-42** `JdbcConnectionPropertyInspector` が `DriverMetadataSource` を受け取る形に変更（既定は実装クラス）

## Phase 5. 段階的プローブの統括

- [ ] **T-50** `ConnectionPropertiesResult` / `PropertySource` を定義
- [ ] **T-51** 🔴 `JdbcConnectionPropertyInspectorTest` に fake `DriverMetadataSource` を用意し、
      1 番目の候補で成功するケースを追加
- [ ] **T-52** 🟢 `listProperties` を段階的プローブに書き換え
- [ ] **T-53** 🔴🟢 3 番目の候補で成功するケース（26.x 相当）
- [ ] **T-54** 🔴🟢 全候補失敗 → `DRIVER_PROPERTY_INFO` 縮退
- [ ] **T-55** 🔴🟢 全候補失敗 + `getPropertyInfo` 空 → `NONE_FETCH_FAILED`
- [ ] **T-56** 🔴🟢 JAR 不在 → `NONE_JAR_MISSING`（ドライバをロードしない）
- [ ] **T-57** 🔴🟢 非 CData ドライバクラス → `NONE_NOT_CDATA_DRIVER`
- [ ] **T-58** 🔴🟢 キャッシュ方針（完全取得はキャッシュ / 縮退はキャッシュしない / `invalidateCache`）
- [ ] **T-59** 🔵 ログ整理（候補ごとの失敗は `debug`、全滅時のみ `warn` を 1 回）

## Phase 6. UI の出し分け

- [ ] **T-60** `propertiesFormContent` を `ConnectionPropertiesResult` 受け取りに変更
- [ ] **T-61** `PropertySource` 5 ケースのバナー文言を実装（規約 3.5 の文言規約に従う）
- [ ] **T-62** 縮退時は縮退フォーム + URL 直接入力欄を併記
- [ ] **T-63** カテゴリ展開ルールを「`Authentication` または必須を含む」に変更
- [ ] **T-64** `ConnectionsRoutes` / `ConnectionsView` の呼び出し 2 箇所を追従

## Phase 7. 検証

- [ ] **T-70** `scripts/check-connection-props.sh`（実ドライバ 4 本の `source` と件数を出力）を追加
- [ ] **T-71** T-70 を実行し、T-01 の基準値と照合（AC-2）
- [ ] **T-72** `./gradlew ktlintCheck detekt test` を実行
- [ ] **T-73** `src/browserTest` のバナー文言依存を確認し、必要なら更新
- [ ] **T-74** Web UI で手動確認（SAP Gateway / Salesforce / 展開ルール）

## Phase 8. 仕上げ

- [ ] **T-80** `docs/` への影響確認（`architecture.md` のドライバ取り扱い記述、`development-guidelines.md`）
- [ ] **T-81** コミット（規約 5.2 / 5.4 のパターンに従い、テストコミットを先に）
- [ ] **T-82** Issue #15 に結果を記録
