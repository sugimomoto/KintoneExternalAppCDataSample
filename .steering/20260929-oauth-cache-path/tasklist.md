# OAuth キャッシュパスの一本化 — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | oauth-cache-path |
| 対応 Issue | [#11](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/11) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

TDD（規約 4.2）で進める。

---

## Phase 1. キャッシュパスの決定

- [x] **T-10** 🔴 `JdbcUrlEnhancerTest` を `withOAuthCache` 名に更新し、異常系を追加
- [x] **T-11** 🟢 `JdbcUrlEnhancer` を改名・引数名変更

## Phase 2. 参照名の取得

- [x] **T-20** 🔴 `SqliteConfigSourceTest` に `sharedJdbcRefOf` のテストを追加
- [x] **T-21** 🟢 `ConfigSource` に既定実装つきで追加し、`SqliteConfigSource` で実装

## Phase 3. プロバイダでの一律付与

- [x] **T-30** `JdbcConnectionProvider` に `oauthCacheKey` を必須追加し、`init` で付与
- [x] **T-31** コンパイルエラーになる 7 箇所を確認（付与忘れ防止が効いていること）
- [x] **T-32** `TableAdapterServer` から URL 付与を削除し、キーを受け取る形に変更
- [x] **T-33** `MultiAdapterRunner` でキーを解決して渡す
- [x] **T-34** Web UI の接続テスト・ウィザード 3 経路を修正
- [x] **T-35** CLI (`test-connection` / `list-tables`) を修正
- [x] **T-36** テストの Fake ファクトリ 3 箇所を 2 引数に修正

## Phase 4. 検証

- [x] **T-40** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-41** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-42** 接続テスト・ウィザード・実行時のログで同じキャッシュパスが出ることを確認

## Phase 5. 仕上げ

- [x] **T-50** README に既存キャッシュの扱い（一度の再認可が必要）を明記
- [x] **T-51** `docs/extending.md` の R-10 を更新
- [x] **T-52** コミット
- [x] **T-53** PR 作成・マージ、Issue #11 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 全経路で同じ規則で付与 | ✅ | `JdbcConnectionProvider` に一本化。7 経路すべてがここを通る |
| AC-2 決定ロジックが 1 箇所 | ✅ | `JdbcUrlEnhancer.applyOAuthCache` |
| AC-3 忘れるとコンパイルエラー | ✅ | 必須引数化により main 7 箇所 + test 10 箇所がコンパイルエラーになった |
| AC-4 明示指定を尊重 | ✅ | `JdbcUrlEnhancerTest`。**既存バグを修正**（下記） |
| AC-5 同一接続の連携でキャッシュ共有 | ✅ | `SqliteConfigSourceTest` + `TableAdapterServerTest` |
| AC-6 テスト・ウィザード・実行時が同一 | ✅ | 実機ログで同一パスを確認 |
| AC-7 インライン設定でも動作 | ✅ | `sharedJdbcRefOf` が null → 連携名にフォールバック |
| AC-8 キー決定のユニットテスト | ✅ | `JdbcUrlEnhancerTest` に 8 件追加 |
| AC-9 ディレクトリを抜け出さない | ✅ | `cachePathFor` のテストを `../../etc/passwd` 等で追加 |
| AC-10 テストと lint | ✅ | `test` 365 件成功。detekt は origin/main と同じ 84 件 |
| AC-11 既存キャッシュの扱い | ✅ | README に「一度だけ再認可が必要」と明記 |

### 実装で見つけた 2 つの問題

**1. 全 URL に付与するのは誤りだった**

最初の実装で H2 を使うテストが 5 件落ちた。

```
org.h2.jdbc.JdbcSQLNonTransientConnectionException
```

`OAuthSettingsLocation` は CData 固有のプロパティで、H2 は未知の `;KEY=VALUE` を
接続エラーにする。`isCDataDriver(driverClass)` で CData 以外を除外した。
本プロジェクトは非 CData ドライバーも受け付ける（#15 の `NONE_NOT_CDATA_DRIVER`）ため、
この分岐は必要だった。

**2. 明示指定の検出が最初のプロパティで効いていなかった（既存バグ）**

新しく追加した「明示指定を上書きしない」テストが落ちた。検出の正規表現が
区切りを `;` だけと想定していたため、CData の接続文字列で**最初のプロパティ**に
書かれた場合を検出できず、二重付与していた。

```
jdbc:salesforce:OAuthSettingsLocation=./existing.txt;   ← 直前が ':' なので検出できない
jdbc:salesforce:User=u;OAuthSettingsLocation=./x.txt;   ← 既存テストはこちらだけ見ていた
```

区切りを `[;:]` に修正し、回帰テストを追加した。

### 実装で変わった判断

設計では `JdbcUrlEnhancer.withOAuthCache` + `cachePathFor` を呼び出し側で組み合わせる
想定だったが、CData 判定が必要になったため `applyOAuthCache(config, cacheKey, baseDir)`
として `JdbcConfig` を受け取る入口を追加した。これにより判定・パス生成・付与の 3 つが
1 つの純粋関数に収まり、テストしやすくなった。
