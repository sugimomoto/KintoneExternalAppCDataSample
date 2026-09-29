# 要求内容 — Agent 接続フローの不具合修正 (Issue #1-#4, #6, #7)

## 背景

Web UI の「接続して開始」で Sync (Categories) を接続しようとしたところ、以下の 2 つの症状が発生した。

1. `⚠ Agent コンテナ起動に失敗: Status 304:` というエラーが表示される
2. エラーを回避して接続に成功した後も、kintone からアクセスすると
   `Adapterが利用できません。システム管理者にお問い合わせください。(GAIA_AU01 ...)` になる

調査の結果、Agent 接続フローに 6 件の独立した不具合・設計不備が見つかった。
本作業ではこれらをまとめて修正する。

## 対象 Issue

| Issue | 区分 | 概要 |
|---|---|---|
| [#1](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/1) | bug | 起動済みコンテナの start で Docker 304 を失敗扱いしてしまう |
| [#2](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/2) | bug | connect 時に running コンテナを再起動せず、新しい接続キー・adapter_addr が反映されない |
| [#3](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/3) | bug | Sync 作成時のポートが 0 固定で、公開範囲外となり Agent から Adapter に到達できない |
| [#4](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/4) | bug | 接続確認が古いログの `successfully connected` を拾って誤判定する |
| [#6](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/6) | bug | web-ui 起動時に Adapter が自動復元されず、コンソール再起動後にアクセスできない |
| [#7](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/7) | enhancement | 接続完了時に Agent→Adapter の疎通を検証し、到達不可を UI で検知する |

> Issue #5（YAML 設定ストア廃止）は本作業の対象外。

## ユーザーストーリー

### US-1: 既に起動している Sync を再接続できる
**パートナー SI の構築担当者として**、既に Agent が動いている Sync に新しい接続キーを入力したとき、
**エラーではなく正常に再接続され、新しいキーが確実に反映されてほしい**。
（現状は `Status 304:` エラーになり、成功しても旧トークンのまま動き続ける）

### US-2: Sync を作れば Docker 構成のまま kintone から使える
**パートナー SI の構築担当者として**、Web UI でウィザードから Sync を作成したとき、
**ポートの知識なしに、そのまま kintone から接続できる状態になってほしい**。
（現状は `port: 0` で自動採番され、Docker が公開していないポートになるため必ず到達不可）

### US-3: 接続結果を信用できる
**パートナー SI の構築担当者として**、「接続が確立されました」と表示されたら、
**kintone から実際に使える状態であることを保証してほしい**。
（現状は Agent↔kintone しか検証しておらず、Agent↔Adapter が切れていても成功と表示される）

### US-4: 再起動しても復旧する
**エンドユーザーの運用担当者として**、Docker Desktop やホストを再起動したあとに、
**手作業なしで Sync が復旧してほしい**。
（現状は Agent コンテナだけ復帰し、Adapter は起動しないため必ず `Adapterが利用できません` になる）

## 受け入れ条件

### AC-1 (#1)
- 起動済みの Agent コンテナに対して「接続して開始」を実行してもエラーにならない
- 停止済みコンテナに対する stop 操作もエラーにならない
- 判定は例外メッセージの文字列マッチではなく `NotModifiedException` の型で行う

### AC-2 (#2)
- running 状態の Sync に新しい接続キーを入力すると、コンテナが再起動され新トークンで接続される
- 接続操作 1 回につき Agent ログの `starting agent` が 1 回記録される

### AC-3 (#3)
- 新規 Sync 作成時、Docker 公開範囲 (18000-18099) の空きポートが自動採番される
- 複数 Sync を連続作成してもポートが衝突しない
- `port: 0` の既存 Sync が公開範囲内のポートへ移行される
- 公開範囲の定義がコードと `docker-compose.yml` で二重管理にならない（定数へ集約）

### AC-4 (#4)
- 過去に接続成功したコンテナでも、今回の接続が失敗していれば成功と表示されない
- ログ出力量が多い Sync でも接続成功を検出できる
- トークン失効時はタイムアウトを待たずにエラー理由が表示される

### AC-5 (#6)
- `docker restart adapter-console` 後、設定済み Sync の Adapter がすべて自動起動する
- 1 件の Sync が設定不備で起動失敗しても、他の Sync と Web UI は正常に動作する
- 環境変数で自動起動を無効化できる

### AC-6 (#7)
- Adapter に到達できない状態では「接続が確立されました」と表示されない
- 公開範囲外のポートが原因の場合、その旨と対処方法がメッセージに含まれる
- 正常時は従来どおり成功と表示され、体感できる待ち時間の増加がない

## 制約事項

- **既存の設定ファイルを壊さない**: `port: 0` からの移行は既存の `server.yaml` / SQLite を書き換えるため、
  変更内容をログに残し、環境変数で無効化できるようにする
- **Docker socket が無い環境でも動く**: `AgentContainerManager` が null のケース（fat jar 単体実行など）の
  既存挙動を維持する
- **Docker 非依存のテスト**: Agent コンテナ制御のテストは mockk で `DockerClient` をモックし、
  実際の Docker を必要としない
- **後方互換**: `port: "auto"` / `port: 0` の設定自体は引き続き読み込めること（即時エラーにはしない）
- 既存の公開範囲 `18000-18099` は `docker-compose.yml` で定義済みのため、これに合わせる
  （`PortAllocator` の既定値は 18000-19000 でズレているため是正する）
