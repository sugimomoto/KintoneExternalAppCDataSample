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

- [x] **T-00** ブランチ `fix/15-connection-props-fetch-fallback` を作成
- [x] **T-01** 現行の取得件数を実ドライバ 4 本で記録（salesforce 268 / googlesheets 238 / bcart 193 / sapgateway 取得不可）

## Phase 1. 値オブジェクトと純粋関数（TDD の主戦場）

- [x] **T-10** `jdbc/DriverProperty.kt` を作成（`DriverPropertyInfo` からの変換、名前 trim）
- [x] **T-11** 🔴 `ConnectionPropertyProbeTest` に `dummyValueFor` のテストを追加（URL 系）
- [x] **T-12** 🟢 `jdbc/ConnectionPropertyProbe.kt` に `dummyValueFor` を実装
- [x] **T-13** 🔴🟢 `dummyValueFor` の残りのケース（真偽値系 / Port / 汎用）を追加・実装
- [x] **T-14** 🔴 `candidateUrls` のテストを追加（安全プロパティのみの候補が先頭）
- [x] **T-15** 🟢 `candidateUrls` を実装
- [x] **T-16** 🔴🟢 `candidateUrls` の残りのケース（安全プロパティ無し / ダミー値付き候補 / 重複排除 / `;` を含めない）
- [x] **T-17** 🔵 Refactor（`SAFETY_PROPERTIES` の定義位置、ヒューリスティックの表現）

## Phase 2. 機密判定の共有

- [x] **T-20** 🔴 `ConnectionStringMaskerTest` に `isSensitive` の直接テストを追加
- [x] **T-21** 🟢 `ConnectionStringMasker.isSensitive` を公開に変更（既存の `mask` のテストが緑のままであること）

## Phase 3. 縮退マッピング

- [x] **T-30** 🔴 `DegradedPropertyMapperTest` を作成（名前 trim / `required` 引き継ぎ）
- [x] **T-31** 🟢 `jdbc/DegradedPropertyMapper.kt` を実装
- [x] **T-32** 🔴🟢 機密判定（`PASSWORD` / `NONE`）のテスト・実装
- [x] **T-33** 🔴🟢 `defaultValue` にダミー値を持ち込まないこと・`ordinal` の順序保持

## Phase 4. IO 境界の切り出し

- [x] **T-40** `jdbc/DriverMetadataSource.kt` にインターフェースを定義
- [x] **T-41** 同ファイルに `JdbcDriverMetadataSource`（`DriverManager` 実装）を追加。
      `sys_connection_props` のクエリと `parseRow` は現行の `JdbcConnectionPropertyInspector` から移設
- [x] **T-42** `JdbcConnectionPropertyInspector` が `DriverMetadataSource` を受け取る形に変更（既定は実装クラス）

## Phase 5. 段階的プローブの統括

- [x] **T-50** `ConnectionPropertiesResult` / `PropertySource` を定義
- [x] **T-51** 🔴 `JdbcConnectionPropertyInspectorTest` に fake `DriverMetadataSource` を用意し、
      1 番目の候補で成功するケースを追加
- [x] **T-52** 🟢 `listProperties` を段階的プローブに書き換え
- [x] **T-53** 🔴🟢 3 番目の候補で成功するケース（26.x 相当）
- [x] **T-54** 🔴🟢 全候補失敗 → `DRIVER_PROPERTY_INFO` 縮退
- [x] **T-55** 🔴🟢 全候補失敗 + `getPropertyInfo` 空 → `NONE_FETCH_FAILED`
- [x] **T-56** 🔴🟢 JAR 不在 → `NONE_JAR_MISSING`（ドライバをロードしない）
- [x] **T-57** 🔴🟢 非 CData ドライバクラス → `NONE_NOT_CDATA_DRIVER`
- [x] **T-58** 🔴🟢 キャッシュ方針（完全取得はキャッシュ / 縮退はキャッシュしない / `invalidateCache`）
- [x] **T-59** 🔵 ログ整理（候補ごとの失敗は `debug`、全滅時のみ `warn` を 1 回）

## Phase 6. UI の出し分け

- [x] **T-60** `propertiesFormContent` を `ConnectionPropertiesResult` 受け取りに変更
- [x] **T-61** `PropertySource` 5 ケースのバナー文言を実装（規約 3.5 の文言規約に従う）
- [x] **T-62** 縮退時は縮退フォーム + URL 直接入力欄を併記
- [x] **T-63** カテゴリ展開ルールを「`Authentication` または必須を含む」に変更
- [x] **T-64** `ConnectionsRoutes` / `ConnectionsView` の呼び出し 2 箇所を追従

## Phase 7. 検証

- [x] **T-70** `scripts/check-connection-props.sh`（実ドライバ 4 本の `source` と件数を出力）を追加
- [x] **T-71** T-70 を実行し、T-01 の基準値と照合（AC-2）
- [x] **T-72** `./gradlew ktlintCheck detekt test` を実行（`test` は成功。lint 2 つは本変更前から失敗しており、本変更で追加した指摘は 0 件）
- [x] **T-73** `src/browserTest` のバナー文言依存を確認し、必要なら更新
- [x] **T-74** Web UI で手動確認（SAP Gateway / Salesforce / 展開ルール）

## Phase 8. 仕上げ

- [x] **T-80** `docs/` への影響確認（`architecture.md` のドライバ取り扱い記述、`development-guidelines.md`）
- [x] **T-81** コミット（規約 5.2 / 5.4 のパターンに従い、テストコミットを先に）
- [x] **T-82** Issue #15 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 SAP Gateway で動的フォームが出る | ✅ | `/connections/properties` が 72 件の入力欄を返す（従来は警告バナー + URL 直接入力） |
| AC-2 25.x 系の取得結果が変わらない | ✅ | `scripts/check-connection-props.sh`: salesforce 268 / googlesheets 238 / bcart 193（いずれも従来と同数） |
| AC-3 縮退フォームを出す | ✅ | `JdbcConnectionPropertyInspectorTest` |
| AC-4 縮退が画面で分かる | ✅ | `noticeFor(DRIVER_PROPERTY_INFO)` の案内バナー |
| AC-5 非 CData ドライバーは URL 直接入力 | ✅ | 実機確認済み |
| AC-6 「CData ドライバーでない」の断定を限定 | ✅ | 実機確認済み（非 CData 指定時のみ表示） |
| AC-7 3 ケースの出し分け | ✅ | 実機確認済み（非 CData / JAR 不在 / 完全取得） |
| AC-8 実通信・OAuth 認可を起こさない | ✅ | `Offline=true` / `InitiateOAuth=OFF` / `.invalid` ドメイン |
| AC-9 ダミー値を初期値にしない | ✅ | `DegradedPropertyMapperTest`、`Value` 列は読まない |
| AC-10 縮退でも機密は平文にしない | ✅ | `DegradedPropertyMapperTest` |
| AC-11 ユニットテスト | ✅ | probe 16 件 / mapper 10 件 / inspector 10 件 |
| AC-12 `ktlintCheck detekt test` | ⚠️ | `test` は成功。`ktlintCheck` / `detekt` は**本変更前から** browserTest・build.gradle.kts・既存 View 等で失敗している（本変更で追加した指摘は 0 件） |

### 環境メモ

ローカルに JDK 21 が無く `./gradlew` が起動しないため、Dockerfile と同じ
`eclipse-temurin:21-jdk-jammy` コンテナで Gradle を実行した。
CData ドライバーのライセンスはコンテナ内でも有効だった。
