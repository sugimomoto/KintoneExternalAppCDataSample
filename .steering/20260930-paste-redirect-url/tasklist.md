# リダイレクト URL の貼り付け対応 — タスクリスト

| 項目 | 内容 |
|---|---|
| 対応 Issue | [#57](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/57) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. 抽出（TDD）

- [x] **T-10** 🔴 `AuthorizationCodeExtractorTest` を書く（実際に観測された URL を中心に）
- [x] **T-11** 🟢 `AuthorizationCodeInput` と `AuthorizationCodeExtractor` を実装

## Phase 2. 適用

- [x] **T-20** ルートで抽出結果により分岐する
- [x] **T-21** `error=` の場合の案内を追加する
- [x] **T-22** ラベルと案内文を更新する

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（ベースライン 84 件のまま）
- [x] **T-31** `docker compose build && up -d --force-recreate` + イメージ ID 一致確認
- [x] **T-32** ラベルと案内文が変わったことを確認（AC-7）
- [x] **T-33** `error=` を含む URL で拒否メッセージが出ることを確認（AC-6）
- [x] **T-34** 実際の URL 形式で `code` が抽出されることを確認（AC-1, AC-2）
- [x] **T-35** ログに入力値が出ていないことを確認（AC-8）

## Phase 4. 仕上げ

- [x] **T-40** コミット
- [x] **T-41** PR 作成・マージ、Issue #57 に結果を記録

---

## 実機検証の結果 (2026-09-30)

| AC | 結果 | 確認内容 |
|---|---|---|
| AC-1 / AC-2 | ✅ | 実 URL 形式（`?iss=...&code=zzExtractProbe123&scope=...`）を貼ると、抽出されたコードがプロバイダに送られ Google が `[invalid_grant] Malformed auth code` を返した。URL ごと送られていれば別のエラーになる |
| AC-6 | ✅ | `?error=access_denied&state=xyz` → 「認可が拒否されました (access_denied)。Step 1 からやり直してください。」 |
| AC-7 | ✅ | 見出し「Step 2: リダイレクト先の URL を貼り付ける」／ラベル「リダイレクト先の URL（または認可コード）:」／案内「リダイレクト後のアドレスバーの URL を全部コピーする」「URL から code= の値を自動で取り出します」 |
| AC-8 | ✅ | ダミーコードも URL もログに出ない |
| 空入力 | ✅ | 「認可コード、またはリダイレクト先の URL を入力してください。」 |
| AC-10 | ✅ | `./gradlew test` パス、detekt はベースライン 84 件のまま |

AC-3（素のコード）・AC-4（パーセントエンコード）・AC-5（`+` の保持）は単体テストで担保。
実際の認可完了はコードが使い切りのため、ブラウザ操作が必要で未実施。

## detekt で直した点

`extract` が `ReturnCount`（上限 3）に引っかかったため、クエリ解釈を `fromQuery` に
切り出した。結果として「クエリとして解釈できるか」「できなければ入力全体」という
構造が明示され、読みやすくもなった。
