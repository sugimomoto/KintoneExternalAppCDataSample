# kintone ドメイン入力の削除 — 要求定義

| 項目 | 内容 |
|---|---|
| 開発タイトル | remove-kintone-domain-input |
| 作成日 | 2026-09-29 |
| Issue | [#51](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/51) |
| 目的 | 連携に不要な kintone ドメイン入力と、動作しない保存経路を削除する |

---

## 1. 背景

### 1.1 現状の実装

| 箇所 | 内容 |
|---|---|
| `web/views/ConnectKintoneView.kt:126` | Step 2 のドメイン入力欄 + 「保存して開く」ボタン |
| `web/views/ConnectKintoneView.kt:136` | `domain.isNotBlank()` のときだけ「kintone 管理画面を開く ↗」リンクを描画 |
| `web/views/ConnectKintoneView.kt:223` `readKintoneDomain()` | `System.getenv("KINTONE_DOMAIN") ?: ""` |
| `web/routes/ConnectKintoneRoutes.kt:89` | `POST /syncs/{name}/connect/save-domain`。**未実装スタブ**で何も保存しない |
| `web/views/ConnectKintoneView.kt:207` `authRejectedGuide()` | 「kintone の「外部システムのアプリ化」を開く (下の Step 2 のリンク)」 |

### 1.2 課題

#### 課題 1: 「保存して開く」が保存も開くもしない

エンドポイントはコード上も「未実装スタブ」と明記され、素のテキストを返すだけ。
押すと画面から外れる。

```
ドメインは現状 KINTONE_DOMAIN 環境変数から読み込んでいます。永続化は未実装です。
```

#### 課題 2: `KINTONE_DOMAIN` がコンテナに渡っていない

`docker-compose.yml` の `environment:` に無く、`.env.example` にも記載が無い。
コンテナ内で確認したところ空だった（`KINTONE_DOMAIN=[]`）。

Docker で動かす限り（＝推奨の起動方法）常に空なので、入力欄は常に空で、
管理画面リンクは `domain.isNotBlank()` が偽となり**一度も表示されない**。
Step 2 は「押しても何も起きないボタン」と「出ないリンク」だけになっている。

#### 課題 3: そもそもドメインは連携に不要

- `readKintoneDomain()` の参照は 3 箇所、**すべて `ConnectKintoneView.kt` 内**。
  用途は管理画面リンクの URL 組み立てだけ
- サービス層・Agent 層・gRPC 層に `domain` / `iss` / `aud` 相当の参照は**一切ない**
- `agent.json` に必要なのは `token` / `adapter_addr` / `adapter_plaintext` /
  `private_key_path` の 4 つで、ドメインは含まれない
- 接続キー（JWT）の検証にも使っていない

アーキテクチャ上も不要。**Agent が kintone へアウトバウンドで接続**し、Adapter は
Agent に gRPC を提供するだけで、Adapter 自身は kintone に接続しない。

## 2. 目的

機能上不要な値のための入力欄・保存経路・環境変数依存を削除し、Step 2 を
「実際に役に立つ案内」だけにする。

## 3. スコープ

### 3.1 今回実装する機能

| ID | 機能 |
|---|---|
| F-1 | Step 2 からドメイン入力欄と「保存して開く」ボタンを削除 |
| F-2 | `POST /syncs/{name}/connect/save-domain` を削除 |
| F-3 | `readKintoneDomain()` と `KINTONE_DOMAIN` 依存を削除 |
| F-4 | ドメインに依存する「kintone 管理画面を開く ↗」リンクを削除 |
| F-5 | 代わりに kintone 管理画面のパスを案内として表示（利用者が自分のドメインに付けて開ける） |
| F-6 | `authRejectedGuide()` の「(下の Step 2 のリンク)」を、リンクがない前提に修正 |

### 3.2 今回実装しない機能 (スコープ外)

| 項目 | 理由 |
|---|---|
| ドメインの設定ストアへの永続化 | 連携の動作に不要な値。持つ必要のない状態を増やすだけ |
| `localStorage` によるブラウザ内保存 | 同じ理由。入力欄自体をなくすので保存対象がない |
| `docker-compose.yml` への `KINTONE_DOMAIN` 追加 | 依存そのものを削除するため不要 |
| Step 1 / Step 3 の変更 | 本件と独立。動作しているものは触らない |

## 4. ユーザーストーリー

| ID | ストーリー |
|---|---|
| US-1 | デモ環境を構築する担当者として、押しても何も起きないボタンを消してほしい。動くと思って押し、素のテキスト画面に飛ばされて戻る操作が無駄なため |
| US-2 | デモ環境を構築する担当者として、Step 2 で何をすればよいか分かるようにしてほしい。リンクが出ない状態では手順が読み取れないため |
| US-3 | このサンプルを引き継ぐ開発者として、使われていない環境変数と未実装スタブを残さないでほしい。実装済みの機能と区別がつかず、調べる手間がかかるため |

## 5. 受け入れ条件

| ID | 条件 |
|---|---|
| AC-1 | 接続画面にドメイン入力欄と「保存して開く」ボタンが表示されない |
| AC-2 | `POST /syncs/{name}/connect/save-domain` が存在しない (404) |
| AC-3 | Step 2 に kintone 管理画面のパスが案内として表示される |
| AC-4 | `authRejectedGuide()` の案内が、存在しないリンクを指していない |
| AC-5 | `KINTONE_DOMAIN` への参照がコードから消えている |
| AC-6 | Step 1 (鍵ペア) と Step 3 (接続キー入力) は従来どおり表示・動作する |
| AC-7 | `./gradlew test` が通り、detekt がベースライン 84 件のままである |

## 6. 制約事項

| ID | 制約 |
|---|---|
| C-1 | Step 1 / Step 3 のマークアップとハンドラは変更しない |
| C-2 | `SyncConnectionService` / `KeyPairGeneratorService` は変更しない |
| C-3 | 管理画面のパス (`/k/admin/system/admin/dataConnector.html`) は現行の値を維持する |
| C-4 | 削除後に `KINTONE_DOMAIN` の記述が `docker-compose.yml` / `.env.example` に残らないこと（元から無いが確認する） |

## 7. 影響範囲

| 対象 | 影響 |
|---|---|
| `web/views/ConnectKintoneView.kt` | Step 2 の入力欄・ボタン・リンクを削除。パス案内を追加。`readKintoneDomain()` を削除。`authRejectedGuide()` の文言修正 |
| `web/routes/ConnectKintoneRoutes.kt` | スタブエンドポイントを削除 |
| `docker-compose.yml` / `.env.example` | **変更なし**（元から `KINTONE_DOMAIN` の記述が無い） |
| `config/` | **変更なし** |
| `docs/` | 永続的ドキュメントへの影響なし（基本設計・データモデルの変更を伴わない） |
