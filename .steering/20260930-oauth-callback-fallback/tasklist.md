# OAuth コールバック URL の既定値 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#55](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/55) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 0. 方針の検証

- [x] **T-01** `oauth.cdata.com` を明示した場合の挙動を実機で確認
- [x] **T-02** `State` パラメータの有無と効果を確認
- [x] **T-03** `localhost:33333` 指定と未指定の結果を比較（→ 設計を見直し）

## Phase 1. 実効コールバック（TDD）

- [x] **T-10** 🔴 `OAuthCapabilityTest` に実効コールバックのテストを追加
- [x] **T-11** 🟢 `effectiveCallbackUrl` と `DEFAULT_CALLBACK_URL` を実装

## Phase 2. 適用

- [x] **T-20** 認可 URL 生成で `effectiveCallbackUrl` を使う
- [x] **T-21** トークン交換でも同じ関数を使う
- [x] **T-22** 接続設定を書き換えないことを確認

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** Google 系（未設定）で `redirect_uri` が localhost になることを確認（AC-1）
- [x] **T-33** 接続設定が書き換わらないことを確認（D-2）
- [x] **T-34** 他のプロパティが残ることを確認（AC-7）
- [x] **T-35** Salesforce 系（未設定）が `oauth.cdata.com` のままであることを確認（AC-4）
- [x] **T-36** 明示済みの接続がその値のままであることを確認（AC-5）
- [x] **T-37** 検証データを片付け、`config.db` をバックアップと突き合わせる

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #55 に結果を記録

---

## 実機検証の結果 (2026-09-30)

### Phase 0: 方針の見直し

当初の「生成結果が OOB なら差し替えて接続設定に保存し直す」設計を、検証の結果
「`CallbackURL` 未設定なら常に `http://localhost:33333` を渡す」に変更した。

| 検証 | 結果 |
|---|---|
| `oauth.cdata.com` を明示（`State` なし） | Google ❌ `OAUTH [30003]` / Salesforce ❌ `OAUTH [30006]` |
| `oauth.cdata.com` + `State=localhost` | Google ✅ 生成できる / Salesforce ❌ **`State` を付けても拒否** |
| `localhost:33333` を指定 vs 未指定 | Salesforce は **redirect_uri も state も完全一致** / Google は OOB → localhost |

**Salesforce は localhost を渡すとドライバーが自動的に `oauth.cdata.com` +
`state=base64(localhost)` に変換する。** これにより一律化が可能と分かった。

### Phase 3: 受け入れ条件

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 | ✅ | `googlesheets`（未設定）→ `redirect_uri=http%3A%2F%2Flocalhost%3A33333`（**修正前は OOB**） |
| AC-3 | ✅ | 認可とトークン交換が同じ `effectiveCallbackUrl` を通るコード経路 |
| AC-4 | ✅ | `SalesforceOAuth`（未設定）→ `redirect_uri=https%3A%2F%2Foauth.cdata.com%2Foauth%2F` + `state=aHR0cDovL2xvY2FsaG9zdDozMzMzMw%3D%3D`（**修正前と同一**） |
| AC-5 | ✅ | `GoogleSheetsOAuth`（明示済み）→ その値のまま |
| D-2 / AC-7 | ✅ | 認可ページを開いた後も接続設定に差分なし（当初案と違い書き換えない） |
| AC-8 | ✅ | `./gradlew test` パス、detekt はベースライン 84 件のまま |
