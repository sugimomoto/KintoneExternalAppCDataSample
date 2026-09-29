# 接続テストの資格情報検証 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | connection-test-validation |
| 作成日 | 2026-09-29 |
| Issue | [#45](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/45) |
| 目的 | 接続テストが実際に資格情報を検証するようにし、誤った設定で「成功」と表示されないようにする |

---

## 1. 背景

### 1.1 現状の実装

```kotlin
// web/routes/ConnectionsRoutes.kt
JdbcConnectionProvider(config, oauthCacheKey = name).use { provider ->
    provider.connection().use { conn ->
        conn.metaData.let { md ->
            "${md.databaseProductName} ${md.databaseProductVersion} / ${md.driverName} ${md.driverVersion}"
        }
    }
}
```

例外が出なければ「接続成功」としている。

### 1.2 課題

**接続テストは資格情報を検証していない。** 誤った資格情報でも「接続成功」になる。

```
jdbc:salesforce:AuthScheme=Basic;User=nobody@example.com;Password=wrongsecret;
→ ●接続成功: Salesforce 25.0.9540 / CData JDBC Driver for Salesforce 2025J 25.0.9540.0
```

原因は 2 つ重なっている。

1. `databaseProductName` / `driverVersion` は**ドライバー側の静的な情報**で、
   サーバーへの問い合わせを伴わない
2. CData ドライバーは**認証を遅延させる**ため `getConnection()` 自体も通る

さらに HikariCP は生成直後の接続を `aliveBypassWindow`（既定 500ms）内では検証しないため、
プールによる検証もかからない。

結果として、接続テストの目的（設定が正しいかを確かめる）を果たしていない。
誤った設定のまま連携を作り、実行時になって初めて失敗する。

### 1.3 実測

同じ Salesforce・同じ Basic 認証で、資格情報だけを変えて各操作を比較した
（コンテナ内、JDBC 直叩き）。

| 操作 | 誤った資格情報 | 正しい資格情報 | 所要時間 |
|---|---|---|---|
| `getConnection()` | ✅ 成功してしまう | ✅ 成功 | 0.3 秒 |
| `getMetaData()` ← **現状の実装** | ✅ 成功してしまう | ✅ 成功 | 0.006 秒 |
| **`isValid(10)`** | ❌ **false** | ✅ **true** | 0.9〜1.8 秒 |
| `getMetaData().getTables()` | 0 件（例外なし） | 3 件以上 | 1.3 秒／**Google Sheets では 90 秒** |
| `SELECT TableName FROM sys_tables` | 0 件（例外なし） | 3 件以上 | 0.05 秒 |

`isValid` が `false` のときの理由取得も調べた。

| 手段 | 結果 |
|---|---|
| `getWarnings()` | `null`（理由は取れない） |
| 実データへの `SELECT`（`SELECT * FROM Account LIMIT 1`） | `HTTP [40003] [sf:INVALID_LOGIN] INVALID_LOGIN: Invalid username, password, security token; or user locked out.` |

理由が取れるのは実データへのクエリだけだが、**テーブル名を知る必要があり汎用には使えない**。

## 2. 目的

接続テストで誤った資格情報を検出し、失敗として表示する。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 | 内容 |
|---|---|---|
| F-1 | `Connection.isValid(timeout)` による検証 | JDBC 4.0 標準。誤った資格情報を唯一検出できた |
| F-2 | `false` を失敗として扱う | `isValid` は例外を投げない。例外の有無だけを見ると今と同じ誤判定になる |
| F-3 | 検証結果を型で表す | 成功・失敗をビューが取り違えられないようにする |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| 失敗理由の取得 | `getWarnings()` は null。理由が取れるのは実データへのクエリだけで、テーブル名を汎用に決められない。まず正しい判定を入れることを優先する |
| `getMetaData().getTables()` による検証 | Google Sheets で **90 秒** かかった。接続テストの応答時間として許容できない |
| `SELECT FROM sys_tables` による検証 | 速いが失敗時も例外を投げず 0 件を返すだけ。「テーブルが無い」のか「認証失敗」なのか区別できない |
| `test-connection` CLI サブコマンドの修正 | 同じ問題があるが、まず画面側を直す |
| 連携の起動時の事前検証 | 起動経路に接続テストを挟むと起動が遅くなる |
| タイムアウト値の設定可能化 | まず固定値で入れる |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | デモ環境を構築する担当者として、資格情報を間違えたら接続テストで失敗してほしい。「成功」を信じて連携を作り、実行時に初めて失敗するのは手戻りが大きいため |
| US-2 | デモ環境を構築する担当者として、接続テストが数秒で返ってほしい。設定を試行錯誤する間ずっと待たされるのは困るため |
| US-3 | デモ環境を構築する担当者として、成功時にはこれまでどおり接続先の製品名・バージョンを見たい。正しいデータソースに繋がっているかの確認に使っているため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | 誤った資格情報の接続で接続テストが**失敗**と表示される |
| AC-2 | 正しい資格情報の接続で成功と表示され、ドライバー情報（製品名・バージョン）も出る |
| AC-3 | `isValid` が `false` を返した場合が失敗として扱われる（例外が出なくても） |
| AC-4 | 接続の確立自体に失敗した場合（ドライバー JAR が無い等）も従来どおり失敗と表示される |
| AC-5 | 接続テストの応答が数秒以内に返る |
| AC-6 | 失敗メッセージがマスクされている（#42 の回帰なし） |
| AC-7 | 判定ロジックに単体テストがある（`Connection` をモックして `isValid` の真偽で分岐すること） |
| AC-8 | `./gradlew test` が通り、detekt がベースライン 84 件のままである |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | 検証は `Connection.isValid(int)`（JDBC 4.0 標準）で行う。ドライバー固有の手段に依存しない |
| C-2 | `isValid` の戻り値 `false` を必ず失敗として扱う。例外の有無だけで判定しない |
| C-3 | 画面に例外メッセージを出す場合は必ず `ConnectionStringMasker` を通す |
| C-4 | 成功時の表示内容（製品名・バージョン）は変更しない |
| C-5 | 検証ロジックは `Connection` を引数に取る形にし、DB 無しでモックしてテストできること |
| C-6 | `JdbcConnectionProvider` は変更しない。接続の張り方ではなく検証の仕方の問題 |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `jdbc/ConnectionValidator.kt` | 新規。`isValid` による検証 |
| `web/routes/ConnectionsRoutes.kt` | 接続テストが検証結果で分岐する |
| `jdbc/JdbcConnectionProvider.kt` | **変更なし**（C-6） |
| `cli/TestConnectionCommand.kt` | **変更なし**（スコープ外） |
| 接続テストの応答時間 | 0.006 秒 → 1〜2 秒程度に増える。検証していなかったものを検証するので必要なコスト |
| `docs/` | 永続的ドキュメントへの影響なし |
