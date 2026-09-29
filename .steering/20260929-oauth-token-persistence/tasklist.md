# OAuth トークンの保存 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#34](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/34) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 純粋関数

- [x] **T-10** 🔴🟢 `JdbcUrlEnhancer.withProperty`（追加・置換・区切り・大文字小文字）
- [x] **T-11** `withInitiateOAuthOff` を `withProperty` で書き直す（既存テストは緑のまま）
- [x] **T-12** 🔴🟢 `OAuthTokens.from`（列名の解釈）

## Phase 2. 取得と保存

- [x] **T-20** `fetchAccessToken` が `OAuthTokens` を返すように変更
- [x] **T-21** ルート側で接続設定を更新（`InitiateOAuth=REFRESH` + `OAuthRefreshToken`）
- [x] **T-22** リフレッシュトークンが取れない場合を失敗として扱う

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt
- [x] **T-31** `docker compose build && up -d --force-recreate`（CLAUDE.md 手順 7）
- [x] **T-32** 再認可してトークンが保存されることを確認
- [x] **T-33** 接続テストが成功することを確認
- [x] **T-34** 一覧・プレビューでマスクされていることを確認

## Phase 4. 仕上げ

- [x] **T-40** #12 の設計文書に C-5 撤回の経緯を追記
- [x] **T-41** コミット
- [x] **T-42** PR 作成・マージ、Issue #34 に結果を記録

---

## 実機検証の結果 (2026-09-29)

Google Sheets (`GoogleSheetsOAuth`) で認可 → 保存 → 接続テストまで通した。

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | `OAuthRefreshToken` (103 文字) が接続設定に入った |
| AC-2 | ✅ | `InitiateOAuth=GETANDREFRESH` → `REFRESH` に置換された |
| AC-3 | ✅ | 接続テストが `Connected: Google Sheets 25.0.9540` で成功。再認可は走らない |
| AC-6 | ✅ | 一覧・認可画面・ログに露出なし。編集フォームは `type="password"` で描画（既存の `Password` 等と同じ扱い） |
| AC-7 | ✅ | `CallbackURL=http://localhost:33333` が残った |

保存後の接続文字列:
`jdbc:googlesheets:AuthScheme=OAuth;InitiateOAuth=REFRESH;CallbackURL=http://localhost:33333;OAuthRefreshToken=<103 文字>`

### 副産物: ライセンス nodeid の不安定さ

`--force-recreate` するたびに CData ライセンスが「認証されていない」状態に
戻る問題を発見した。コンテナの hostname が既定でコンテナ ID になるため、
nodeid が毎回変わっていた（CLAUDE.md 手順 7 を実行するたびに再アクティベーションが
必要になる構造）。`docker-compose.yml` に `hostname` と `mac_address` を
固定して解消した。
