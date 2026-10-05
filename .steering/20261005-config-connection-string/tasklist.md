# 接続プロパティ取得の config 接続文字列化 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#60](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/60) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 0. 方針の検証（完了）

- [x] **T-01** `config:` で `sys_connection_props` が取れることを確認（SQL Server 240 件）
- [x] **T-02** 短縮形では効かないことを確認
- [x] **T-03** `getPropertyInfo` では Hierarchy を代替できないことを確認
- [x] **T-04** `config:` でもライセンスが検証されることを確認（Jira で例外）

## Phase 1. config URL（TDD）

- [x] **T-10** 🔴 `ConnectionPropertyProbeTest` を `configUrl` のテストに置き換える
- [x] **T-11** 🟢 `configUrl` を実装し、ダミー値機構を削除する

## Phase 2. 呼び出し側

- [x] **T-20** `JdbcConnectionPropertyInspector` を `configUrl` 1 本にする
- [x] **T-21** `LicenseVerifier` を `configUrl` にする
- [x] **T-22** 影響を受けるテストを追従させる

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** SQL Server の編集画面が縮退しないことを確認（AC-1）
- [x] **T-33** Salesforce / Google Sheets が従来どおりであることを確認（AC-2）
- [x] **T-34** SQL Server のライセンス検証が「利用可能」になることを確認（AC-7）
- [x] **T-35** 未認証ドライバーが「要アクティベーション」のままであることを確認（AC-8）
- [x] **T-36** プロパティ取得で実接続が起きないことを確認（AC-3）

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #60 に結果を記録

---

## 実機検証の結果 (2026-10-05)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | SQL Server の編集画面が縮退しない。縮退バナーなし／手動 URL 欄 0 個／prop 59 個／**カテゴリー 9 件**（Bulk, Azure Authentication, SSL, SSH, ファイアウォール…） |
| AC-2 | ✅ | `Salesforce1` 縮退なし prop=84 カテゴリー 11 件／`googlesheets` 縮退なし prop=88 カテゴリー 9 件 |
| AC-3 | ✅ | 編集画面の応答が即時。到達不能な `Server=sqlserver-sample` でも待たされない |
| AC-4 | ✅ | `ConnectionPropertyProbeTest` を `configUrl` のテストに置き換え |
| AC-5 | ✅ | `PROBE_URL` / `PROBE_VALUE` / `candidateUrls` / `BOOLEAN_NAME_PREFIXES` / `SAFETY_PROPERTIES` / `dummyValueFor` / `isUrlProperty` / `isBooleanProperty` が `src/` から消滅 |
| AC-6 | ✅ | `configUrl` が非 CData ドライバークラスを `IllegalArgumentException` で拒否し、呼び出し側がフォールバックに落ちる |
| AC-7 | ✅ | `cdata.jdbc.sql.jar` → **利用可能**（修正前は「要アクティベーション」の偽陰性）／`salesforce` も利用可能 |
| AC-8 | ✅ | 未認証の `cdata.jdbc.jira.jar` → 「要アクティベーション」のまま |
| AC-9 | ✅ | 473 テストパス、detekt はベースライン 84 件のまま |

### 副産物

手動 URL 欄が描画されなくなったため、SQL Server では
[#59](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/59)
（編集が黙って破棄される）を**踏まなくなった**。ただし原因は別なので #59 は独立して修正する。

### 削除できたコード

`ConnectionPropertyProbe` のコメントにあった「ダミー値には書式検証があり、書式情報を得る
API は存在しない。そのためプロパティ名からの推測に留め」という苦労は、公式の config
接続文字列を使うことで**まるごと不要**になった。131 行 → 50 行。
