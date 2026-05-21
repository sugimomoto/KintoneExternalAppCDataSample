# 06. サンプル Adapter の構造解析

サンプル `db-connector-sample` の TypeScript + Prisma 実装の各ファイル役割と読解ポイント。

実体：[reference/db-connector-sample/db-connector-sample/adapter/](../../../../reference/db-connector-sample/db-connector-sample/adapter/)

---

## ディレクトリ構成

```
adapter/
├ .env                  # MYSQL_PORT, MYSQL_URL, ADAPTER_PORT
├ .npmrc                # BSR (@buf) のregistry設定
├ biome.json            # Linter/Formatter設定
├ compose.yaml          # Docker Compose（DBコンテナ）
├ package.json          # 依存・スクリプト
├ pnpm-lock.yaml
├ pnpm-workspace.yaml
├ schema.prisma         # Prismaスキーマ（1モデル想定）
├ tsconfig.json
├ tsconfig.build.json
├ vitest.config.js
└ src/
   ├ index.ts           # エントリポイント（HTTP/2サーバ起動）
   ├ services.ts        # AdapterService 実装本体
   ├ query.ts           # FilterCondition → Prisma where 変換
   ├ FieldIds.ts        # field_id 配列定義（手動メンテ）
   ├ Fieldutils.ts      # Record ⇄ Prisma row 変換
   ├ selectionFields.ts # 選択肢オプション定義
   ├ utils.ts           # Timestamp 変換ヘルパー
   └ __tests__/         # vitest テスト
```

---

## src/index.ts（エントリポイント）

```typescript
import * as http2 from "node:http2";
import { AdapterService } from "@buf/cybozu_kintone-data-connector.bufbuild_es/cybozu/data_connector/adapter/v1/adapter_service_pb.js";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import { Service } from "./services.js";

const run = async () => {
  const server = http2.createServer(
    connectNodeAdapter({
      routes: (router) => {
        router.service(AdapterService, new Service());
      },
    }),
  );
  const port = Number.parseInt(process.env.ADAPTER_PORT ?? "8080", 10);
  server.listen(port, "localhost");
};
```

**ポイント**:
- `http2.createServer` で HTTP/2 サーバ起動（TLSなし）
- `connectNodeAdapter` で Connect RPC ルーターを HTTP/2 ハンドラに変換
- `AdapterService` は BSR 生成済みの protobuf 由来のサービス定義
- ポートは ADAPTER_PORT 環境変数（デフォルト 8080）

**CData JDBC 版での等価実装** (本プロジェクト確定構成):
- **Kotlin + Ktor + connect-kotlin**（HTTP/2 + Connect プロトコル ファーストクラス対応）
- protobuf からの Kotlin SDK 生成は `buf generate`（`build.buf` Gradle プラグイン）
- 詳細は [07-sdk-options.md](07-sdk-options.md) 参照

---

## src/services.ts（AdapterService 実装の中心）

`class Service implements AdapterService` の形で全 RPC メソッドを実装。

### getCapability
```typescript
async getCapability(_) {
  return {
    payload: {
      selectOperationSupported: true,
      insertOperationSupported: true,
      updateOperationSupported: true,
      deleteOperationSupported: true,
      countOperationSupported: true,
      searchOperationSupported: true,  // 実装はコメントアウトされてる…バグ？
    },
  };
}
```

**観察**: サンプルでは `searchOperationSupported: true` を返しているが `search` メソッドはコメントアウト。実際に kintone から呼ばれると 405/未実装エラーになると思われる → CData 版では実装方針をフラグと一致させる。

### getSchema
- `columnsFieldIds`（全field_id配列）を回し、`getFieldDefinition(fieldId)` で FieldDefinition を生成
- `getFieldDefinition` は field_id が text/number/datetime/selection/recordId のどれに属するかで分岐

### select
- `req.payload.fields` → `selectFields()` で Prisma の `select` 句に変換
- `req.payload.filterConditions` → `where()` で Prisma の `where` 句に変換
- `req.payload.sortConditions` → `orderBy` に変換
- `Prisma.Decimal` を `Number` にキャスト（Prismaの精度型対応）
- 空文字列を null に正規化
- 結果を `convertColumnsToRecord` で Record 配列に変換

### insert
- 各 Record を `convertRecordToColumns` で Prisma row 形式に変換
- PostgreSQL 系では `createManyAndReturn` を使い、自動採番されたIDを取得
- MySQL では `createMany` 後に `SELECT LAST_INSERT_ID()` で先頭ID取得 → 連番でIDを推定（**やや危うい設計**）
- トランザクション内で実行

### update
- ID 必須チェック
- 各 Record を `convertRecordToColumns<PartialColumns>` で部分更新形式に変換
- `prisma.columns.update({ where: { id }, data })` をループ実行

### delete
- `prisma.columns.deleteMany({ where: { id: { in: ids } } })`

### count
- `prisma.columns.count({ where: ... })`
- BigInt にラップして返却

### search（コメントアウト）
- MySQL FULLTEXT インデックス前提
- `searchFields` がハードコード（"code", "name"）
- リクエストごとの動的 search field 指定は不可（インデックス事前定義が必要なため）

---

## src/query.ts（FilterCondition → Prisma where 変換）

`where(filters, matchOperator)` 関数が中核。`switch (filter.condition.case)` で 全39ケースを分岐し、Prismaのwhere表現に変換する。

**CData JDBC 版の最大の翻訳ポイント**: この query.ts と等価な Java/Kotlin 実装を、Prisma の代わりに **動的SQL生成** で書く。具体的には `08-jdbc-mapping.md` 参照。

主要ヘルパー：
- `convertNullableOptionsToInClause`: NULL を含む `IN` 句の分割（重要なエッジケース）
- `formatSearchKeywords`: MySQL Boolean Mode 用キーワード整形
- `splitMySQLBooleanOperators`: 特殊文字除去

---

## src/Fieldutils.ts（Record ⇄ row 変換）

protobuf の `Record` と DB row (`Columns` 型) の相互変換。

### `convertRecordToColumns<T>(record, initialColumns)`
- `record.fields` (`map<string, Field>`) を走査
- 各 Field の oneof case で分岐：
  - `recordIdField` → `{ id: value }` (BigInt)
  - `textField` → `{ [field_id]: value }` (string)
  - `datetimeField` → Timestamp を Date に変換
  - `numberField` → `value ?? null`
  - `selectionField` → 選択肢検証してから `{ [field_id]: value }`
- 結果を `Object.assign` でマージ

### `convertColumnsToRecord(columns)`
- DB row の各キーについて `getFieldType()` で型判定
- 型に応じた Field（recordIdField/textField/datetimeField/numberField/selectionField）を組み立て
- `map<string, Field>` 形式に整形

### `getFieldDefinition(fieldId)`
- `GetSchema` レスポンス用の FieldDefinition 生成
- field_id がどの配列（columnsRecordIdFieldId, columnsTextFieldIds, ...）に属するかで分岐

---

## src/FieldIds.ts（field_id 振り分け）

```typescript
export const columnsRecordIdFieldId = "id";
export const columnsTextFieldIds = ["code", "name", "category"] as const;
export const columnsNumberFieldIds = ["price", "price_decimal"] as const;
export const columnsDatetimeFieldIds = ["created_at", "start_date"] as const;
```

**「Auto-generated from schema.prisma」** とコメントがあるが、実際は手動メンテのよう。CData 版は **設定ファイルから自動生成** にするのが望ましい。

---

## src/selectionFields.ts

```typescript
export const selectionFieldOptions = {
  valid: ["on", "off"] as const,
  code_status: ["active", "inactive", "pending"] as const,
  code_no: ["1", "2", "3"] as const
} as const;
```

`SelectionFieldDefinition.options` で kintone に返すための選択肢定義。Adapter 起動時に固定。

CData JDBC 版で動的に取得する場合は、`GetSchema` 呼出時に `SELECT DISTINCT column FROM table` で取得することも可能（ただしパフォーマンス注意）。

---

## src/utils.ts

```typescript
export const convertTimestampToDate = (timestamp?: Timestamp): Date => {
  if (!timestamp) throw new ConnectError("timestamp is not specified", Code.InvalidArgument);
  return timestampDate(timestamp);
};
```

protobuf の `Timestamp` ↔ JS の `Date` 変換。CData JDBC 版では `java.time.Instant` ↔ `com.google.protobuf.Timestamp` の変換が等価。

---

## schema.prisma（必ず特殊な編集が必要）

サンプルでは：
- 1モデルのみ残す（他は削除）
- モデル名を `columns` に変更（Prisma 側のテンプレート都合）
- 元のテーブル名は `@@map("元のテーブル名")` で指定
- `id` 以外のフィールドは `?`（nullable）に変更
- リレーション・インデックスは全削除

```prisma
model columns {
  id         BigInt    @id @default(autoincrement()) @db.UnsignedBigInt
  name       String?   @db.VarChar(200)
  email      String?   @db.VarChar(320)
  // ...
  @@map("customers")
}
```

CData JDBC 版では **Prisma 不要**。設定ファイル（YAML 等）でテーブル名・カラム定義を指定する。

---

## package.json の重要部分

```json
{
  "dependencies": {
    "@bufbuild/protobuf": "...",
    "@buf/cybozu_kintone-data-connector.bufbuild_es": "...",
    "@connectrpc/connect": "...",
    "@connectrpc/connect-node": "...",
    "@prisma/client": "..."
  },
  "scripts": {
    "build": "prisma generate && tsc",
    "start": "node dist/index.js",
    "db:pull": "prisma db pull"
  }
}
```

`.npmrc`:
```
@buf:registry=https://buf.build/gen/npm/v1/
```

**CData JDBC 版での代替**:
- Prisma 系を全削除
- CData JDBC Driver の jar を依存に追加（Gradle Kotlin DSL）
- HikariCP で接続プール
- Connect-kotlin + Ktor で Connect RPC サーバ実装
- 詳細な依存定義は [07-sdk-options.md](07-sdk-options.md) 参照

---

## テストファイル

`src/__tests__/` 配下に vitest テストあり：
- `customer.small.test.ts`
- `query.small.test.ts`
- `service.large.test.ts`
- `helper.ts`

CData 版でも、`query.ts` 相当の FilterCondition → SQL 変換を **同様のテスト粒度** で検証することが望ましい（全39 case のテストカバレッジ）。
