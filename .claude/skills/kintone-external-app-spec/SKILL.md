---
name: kintone-external-app-spec
description: kintoneの「外部システムのアプリ化」機能における Adapter 実装に必要な仕様リファレンス。Connect RPC API（9メソッド）、フィールド型（6種）、フィルター条件（39種）、サンプル解析、CData JDBC マッピングを包括的に提供する。kintone Adapter を実装・拡張・デバッグするときに参照すること。「Adapter のAPI仕様は？」「フィルター条件の一覧は？」「サンプルのservices.tsの構造は？」「CData JDBC でフィルター条件をどう変換する？」などの質問で使用する。
---

# kintone-external-app-spec

kintone「外部システムのアプリ化」機能における **Adapter 実装** のための完全リファレンス集。

本プロジェクトは、サンプルadapter（TypeScript + Prisma）の代わりに **CData JDBC Driver** で多様なデータソース（Salesforce / SAP / Oracle / Google Sheets 等）に接続する Adapter を実装することが目的。

## このスキルを参照すべき場面

- Adapter で実装すべき API のシグネチャ・必須/任意・呼ばれるタイミングを知りたい
- protobuf フィールド型と JDBC の型マッピングを知りたい
- フィルター条件の全種類と、SQL/JDBC への翻訳方法を知りたい
- サンプル adapter の各ソースファイルの役割を知りたい
- 各言語向け SDK の取得方法（buf CLI 等）を知りたい
- curl/grpcurl で Adapter をローカルテストしたい
- kintone 側の制約（フィールド型、機能、コネクター上限）を確認したい

## 参照ファイル

実装中の場面に応じて、以下のファイルを必要に応じて Read してください：

| ファイル | 内容 | 主な用途 |
|---|---|---|
| [reference/01-architecture.md](reference/01-architecture.md) | Connector/Agent/Adapter の全体像、Connect RPC、認証フロー、ポート | 設計初期、運用構成検討時 |
| [reference/02-api-surface.md](reference/02-api-surface.md) | 全9 RPC メソッドの protobuf シグネチャ、呼出タイミング、必須/任意、validation 制約 | API 実装時 |
| [reference/03-field-types.md](reference/03-field-types.md) | 6つの Field 型と FieldDefinition、RecordId(NUMBER/TEXT)、Option | スキーマ実装時、Salesforce等TEXT-ID対応検討時 |
| [reference/04-filter-conditions.md](reference/04-filter-conditions.md) | 全39 FilterCondition、サンプルの Prisma 訳と CData JDBC 訳 | Select/Count/Search/Aggregate 実装時 |
| [reference/05-constraints.md](reference/05-constraints.md) | kintoneアプリ機能・フィールド型の対応可否、コネクター数等の制約 | 仕様検討時、ユーザー説明時 |
| [reference/06-sample-adapter-anatomy.md](reference/06-sample-adapter-anatomy.md) | サンプル各ファイルの役割と読解ポイント、Prisma パターン | サンプル参考にコード書く時 |
| [reference/07-sdk-options.md](reference/07-sdk-options.md) | 言語別 SDK 入手方法、Buf CLI、Connect vs gRPC | 実装言語決定後、依存追加時 |
| [reference/08-jdbc-mapping.md](reference/08-jdbc-mapping.md) | Adapter API → CData JDBC 変換規約（本プロジェクトの実装の核） | コア実装時に頻繁参照 |
| [reference/09-curl-test-snippets.md](reference/09-curl-test-snippets.md) | curl / grpcurl で全 RPC をテストするコマンド集 | ローカルデバッグ時 |

## 情報源の出典

本スキルは以下を統合・構造化したもの：

1. `reference/外部システムのアプリ化構築マニュアル_1.0版.pdf`（30ページ）
2. `reference/Adapter開発ガイド_20251219.pdf`（5ページ）
3. `reference/db-connector-sample/` のサンプル TypeScript ソース
4. `reference/kintone-data-connector/*.proto`（公式 protobuf 7ファイル）
5. https://buf.build/cybozu/kintone-data-connector （BSR 公開リポジトリ）

仕様の最新化が必要な場合は、上記原典に立ち戻ること。本スキルは **2026-05-15 時点の v1 protobuf スキーマと Agent v0.9.2** に対応。

## 重要事項：本プロジェクトの実装方針（確定済み）

- 言語：**Kotlin + Ktor + connect-kotlin**（`07-sdk-options.md` 参照）
- ビルド：**Gradle (Kotlin DSL)** + buf プラグイン
- データソース層：**CData JDBC Driver**（Prisma の代替）
- 接続プール：**HikariCP**
- CLI：**Clikt**（サブコマンド: `serve` / `init-table` / `list-tables` / `test-connection`）
- 設定ファイル：4ファイル分割（`server.yaml` / `jdbc.yaml` / `table.yaml` / `capability.yaml`）
- 最初のデータソース：**Salesforce**
- フェーズ1スコープ：単一テーブル・単一ポート・単一 Agent、設定ファイルベース
- 詳細：[.steering/20260515-initial-implementation/approach-draft.md](../../../.steering/20260515-initial-implementation/approach-draft.md) 参照
