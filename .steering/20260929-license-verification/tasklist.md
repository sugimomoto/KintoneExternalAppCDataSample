# ライセンス状態の実検証 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#31](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/31) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 検証ロジック

- [x] **T-10** `DriverMetadataSource` に `sys_procedures` を読むメソッドを追加
- [x] **T-11** 🔴 `LicenseVerifierTest` を作成（成功 / ライセンスエラー）
- [x] **T-12** 🟢 `jdbc/LicenseVerifier.kt` を実装
- [x] **T-13** 🔴🟢 JAR 不在 / 非 CData / 候補の切り替え

## Phase 2. 画面

- [x] **T-20** `AppContext` に `LicenseVerifier` を追加
- [x] **T-21** `DriversRoutes` に検証エンドポイントを追加
- [x] **T-22** `DriversView` に検証ボタンと結果表示を追加

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt
- [x] **T-31** `docker compose build adapter-console && docker compose up -d adapter-console`
- [x] **T-32** コンテナ内で 4 本を検証して結果を確認
- [x] **T-33** `.lic` のタイムスタンプが変わらないことを確認

## Phase 4. ドキュメント

- [x] **T-40** README にコンテナでのアクティベーション手順
- [x] **T-41** README にホスト / コンテナで `.lic` を共有する際の注意
- [x] **T-42** README に機能ごとのライセンス要件の表
- [x] **T-43** `docs/architecture.md` §3.3 ライセンス制約に追記

## Phase 5. 仕上げ

- [x] **T-50** コミット
- [x] **T-51** PR 作成・マージ、Issue #31 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 実際に使えるか検証できる | ✅ | 実機: 4 本すべて「要アクティベーション」 |
| AC-2 生メッセージを含む | ✅ | 「このシステム上には、…ライセンスがインストールされていますが…」 |
| AC-3 ロケール依存のパースをしない | ✅ | 有効/無効の 2 値。`LicenseVerifierTest` で日本語・英語どちらも `Invalid` |
| AC-4 `.lic` を書き換えない | ✅ | 実機: 検証前後でタイムスタンプ変化なし |
| AC-5 外部通信しない | ✅ | `Offline=true` を含むプローブ接続を使う |
| AC-6 2 軸で表示 | ✅ | 「ライセンスファイル」「実際に使えるか」の 2 列 |
| AC-7 明示的な操作で検証 | ✅ | 行ごとの「検証」ボタン（htmx で差し替え） |
| AC-8 何をすべきか分かる | ✅ | 生メッセージ + README のアクティベーション手順 |
| AC-9〜11 ドキュメント | ✅ | README / `docs/architecture.md` §3.3 |
| AC-12 ユニットテスト | ✅ | `LicenseVerifierTest` 6 件 |
| AC-13 テストと lint | ✅ | `test` 成功。detekt は origin/main と同じ 84 件 |

### 実機での確認

```
cdata.jdbc.salesforce.jar    要アクティベーション  このシステム上には、CData JDBC Driver for Salesforce 2025J用の…
cdata.jdbc.sapgateway.jar    要アクティベーション  …SAP Gateway 2026J用の…
cdata.jdbc.bcart.jar         要アクティベーション  …Bcart 2025J用の…
cdata.jdbc.googlesheets.jar  要アクティベーション  …Google Sheets 2025J用の…

.lic のタイムスタンプ: 変化なし ✓
```

ドライバー画面の列構成:

```
ファイル名 | ドライバークラス | ライセンスファイル | 実際に使えるか | サイズ | 操作
           |                  | 有効               | [検証]        |        |
```

修正前は「ライセンス」列が**「有効」と表示するだけ**で、実際には使えない状態を
見分けられなかった。

### 切り分けで分かったこと

コンテナ内で機能別に実測した。

| 操作 | 結果 |
|---|---|
| `sys_connection_props` の SELECT | ✅ 動く |
| `DatabaseMetaData.getTables()`（`list-tables`） | ✅ 動く（SAP 64 件 / BCart 18 件） |
| `sys_procedures` の SELECT | ❌ ライセンス認証されていない |

**ライセンスチェックの厳しさが機能ごとに違う**。接続フォームやテーブル一覧は
未認証でも動くため、「接続は作れるのに OAuth 認可ウィザードだけ動かない」という
分かりにくい状態になっていた。

### 実装で変わった判断

- 検証に `sys_procedures` を選んだ。実データのクエリでも検証できるが、接続先の
  資格情報が必要で、OAuth 未認可の接続では確認できない
- 結果を**分類しない**（有効/無効の 2 値）。ライセンスエラーのメッセージは
  ロケール依存で、`[コード：I]` のような分類子も「コード」の表記自体が
  言語で変わる。#19 で同じ判断をしている
- `driversRoutes` のアップロード処理を `receiveDriverJar` に切り出した。
  検証エンドポイントの追加で `LongMethod` の閾値を超えたため

### スコープ外にしたこと

**コンテナ内でのアクティベーション実行は手順のドキュメント化までとした。**
`lib/` はホストと bind マウントしているため、コンテナで認証すると
**ホスト側の `.lic` が書き換わる**。ホストで CLI を使う運用があるなら影響するため、
実行は利用者の判断に委ねる。
