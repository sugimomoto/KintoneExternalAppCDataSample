# 08. Adapter API → CData JDBC 変換規約（本プロジェクトの実装の核）

protobuf 定義された Adapter API と JDBC API の **完全な対応表**。本プロジェクト実装中、もっとも頻繁に参照すべき情報。

---

## 0. 設計の基本方針

| 観点 | サンプル（Prisma） | CData 版（JDBC） |
|---|---|---|
| 言語 | TypeScript | **Kotlin** |
| サーバ | connect-node + http2 | **Ktor + connect-kotlin** |
| スキーマ定義 | schema.prisma + db pull | `config/table.yaml` の手動定義（`adapter init-table` で自動生成可） |
| クエリ生成 | Prisma Query Engine | 動的 SQL + PreparedStatement |
| 接続管理 | PrismaClient（内蔵プール） | **HikariCP** |
| 型変換 | Prisma 自動 | 手動マッピング |
| トランザクション | `prisma.$transaction(async (tx) => ...)` | `Connection.setAutoCommit(false)` + commit/rollback |

---

## 1. GetCapability の実装

ほぼ静的レスポンス。設定ファイル（`config/capability.yaml`）の値を返すだけ。

```yaml
# config/capability.yaml
select-supported: true
insert-supported: true
update-supported: true
delete-supported: true
count-supported: true
count-strategy: ACTUAL  # ACTUAL | ALWAYS_ZERO
search-supported: false
aggregate-supported: false  # ※protobufには未公開だが将来用フラグ
record-id-type: NUMBER  # or TEXT
filterable-fields:
  - id
  - name
  - status
sortable-fields:
  - id
  - created_at
```

```kotlin
override suspend fun getCapability(req: GetCapabilityRequest): GetCapabilityResponse {
    return GetCapabilityResponse {
        payload = GetCapabilityResponsePayload {
            selectOperationSupported = config.capability.selectSupported
            // ... 略
            recordIdType = config.capability.recordIdType.toProto()
            filterableFields += config.capability.filterableFields
            sortableFields += config.capability.sortableFields
        }
    }
}
```

---

## 2. GetSchema の実装

設定ファイル（`config/table.yaml`）からカラム定義を読み、`map<string, FieldDefinition>` を組み立て。

```yaml
# config/table.yaml
name: Account  # CData JDBC のテーブル名
primary-key:
  jdbc-column: Id
  kintone-field-id: id
columns:
  - kintone-field-id: name
    jdbc-column: Name
    type: TEXT
  - kintone-field-id: revenue
    jdbc-column: AnnualRevenue
    type: NUMBER
  - kintone-field-id: created_at
    jdbc-column: CreatedDate
    type: DATETIME
  - kintone-field-id: industry
    jdbc-column: Industry
    type: SELECTION
    options:
      - Agriculture
      - Banking
      - Construction
```

```kotlin
override suspend fun getSchema(req: GetSchemaRequest): GetSchemaResponse {
    val schema = mutableMapOf<String, FieldDefinition>()
    schema[config.table.primaryKey.kintoneFieldId] = recordIdFieldDefinition(...)
    for (col in config.table.columns) {
        schema[col.kintoneFieldId] = when (col.type) {
            "TEXT" -> textFieldDefinition(col.kintoneFieldId)
            "NUMBER" -> numberFieldDefinition(col.kintoneFieldId)
            "DATETIME" -> datetimeFieldDefinition(col.kintoneFieldId)
            "SELECTION" -> selectionFieldDefinition(col.kintoneFieldId, col.options)
            else -> error("Unsupported type: ${col.type}")
        }
    }
    return GetSchemaResponse {
        payload = GetSchemaResponsePayload { this.schema += schema }
    }
}
```

`SELECTION` の `options` は静的設定でも良いし、`SELECT DISTINCT column FROM table` で動的取得しても可。

---

## 3. Select の実装

**動的 SQL 生成 + PreparedStatement バインド** が基本。

```kotlin
override suspend fun select(req: SelectRequest): SelectResponse {
    val payload = req.payload ?: throw ConnectException(Code.InvalidArgument)
    
    // 1. SELECT 句
    val selectColumns = if (payload.fieldsList.isEmpty()) {
        config.allJdbcColumns()
    } else {
        payload.fieldsList.map { config.toJdbcColumn(it) }
    }
    
    // 2. WHERE 句（filter → SQL）
    val (whereClause, bindParams) = filterToSql(payload.filterConditionsList, payload.matchOperator)
    
    // 3. ORDER BY 句
    val orderByClause = payload.sortConditionsList.joinToString(", ") {
        "${config.toJdbcColumn(it.fieldId)} ${if (it.sortDirection == SortDirection.DESC) "DESC" else "ASC"}"
    }
    
    val sql = """
        SELECT ${selectColumns.joinToString(", ")}
        FROM ${config.table.name}
        WHERE $whereClause
        ${if (orderByClause.isNotBlank()) "ORDER BY $orderByClause" else ""}
        LIMIT ? OFFSET ?
    """.trimIndent()
    
    return dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            bindParams.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.setLong(bindParams.size + 1, payload.limit)
            stmt.setLong(bindParams.size + 2, payload.offset)
            val rs = stmt.executeQuery()
            val records = mutableListOf<Record>()
            while (rs.next()) records += resultSetRowToRecord(rs)
            SelectResponse { this.payload = SelectResponsePayload { this.records += records } }
        }
    }
}
```

**ポイント**:
- CData JDBC は内部で SQL を CData 仕様に変換するので、標準 SQL を書けば動く
- `LIMIT ? OFFSET ?` は CData が裏でデータソース固有のページネーションに変換
- 主キーが TEXT の場合は `setString` を使う

---

## 4. FilterCondition → SQL WHERE 変換表

| protobuf case | SQL 表現 | PreparedStatement バインド |
|---|---|---|
| `all_records` | `1=1` | （なし） |
| `record_id_equal` (NUMBER) | `id = ?` | `setLong` |
| `record_id_equal` (TEXT) | `id = ?` | `setString` |
| `record_id_not_equal` | `id <> ?` | 同上 |
| `record_id_greater_than` | `id > ?` | `setLong` |
| `record_id_greater_than_or_equal` | `id >= ?` | `setLong` |
| `record_id_less_than` | `id < ?` | `setLong` |
| `record_id_less_than_or_equal` | `id <= ?` | `setLong` |
| `record_id_in` | `id IN (?, ?, ...)` | 各値を `setLong`/`setString` |
| `record_id_not_in` | `id NOT IN (?, ?, ...)` | 同上 |
| `record_id_contains` (TEXT) | `id LIKE ?` | `setString("%" + value + "%")` |
| `record_id_not_contains` (TEXT) | `id NOT LIKE ?` | 同上 |
| `text_equal` | `field = ?` | `setString` |
| `text_not_equal` | `field <> ?` | `setString` |
| `text_in` | `field IN (?, ?, ...)` | `setString` ×N |
| `text_not_in` | `field NOT IN (?, ?, ...)` | 同上 |
| `text_contains` | `field LIKE ?` | `setString("%" + value + "%")` |
| `text_not_contains` | `field NOT LIKE ?` | 同上 |
| `text_is` (EMPTY) | `(field IS NULL OR field = '')` | （なし） |
| `text_is_not` (EMPTY) | `(field IS NOT NULL AND field <> '')` | （なし） |
| `datetime_equal` | `field = ?` | `setTimestamp` |
| `datetime_not_equal` | `field <> ?` | `setTimestamp` |
| `datetime_greater_than` | `field > ?` | `setTimestamp` |
| `datetime_greater_than_or_equal` | `field >= ?` | `setTimestamp` |
| `datetime_less_than` | `field < ?` | `setTimestamp` |
| `datetime_less_than_or_equal` | `field <= ?` | `setTimestamp` |
| `datetime_in_range` | `field >= ? AND field < ?` | `setTimestamp` × 2 |
| `datetime_not_in_range` | `NOT (field >= ? AND field < ?)` | 同上 |
| `number_equal` (value=null) | `field IS NULL` | （なし） |
| `number_equal` (value not null) | `field = ?` | `setDouble` |
| `number_not_equal` (value=null) | `field IS NOT NULL` | （なし） |
| `number_not_equal` (value not null) | `field <> ?` | `setDouble` |
| `number_greater_than` | `field > ?` | `setDouble` |
| `number_greater_than_or_equal` | `field >= ?` | `setDouble` |
| `number_less_than` | `field < ?` | `setDouble` |
| `number_less_than_or_equal` | `field <= ?` | `setDouble` |
| `number_in` | `field IN (?, ?, ...)` | `setDouble` ×N |
| `number_not_in` | `field NOT IN (?, ?, ...)` | 同上 |
| `selection_in` | `field IN (...) [OR field IS NULL]` | `setString` ×N |
| `selection_not_in` | `field NOT IN (...) [AND field IS NOT NULL]` | 同上 |
| `multiple_selection_in` | データソース依存（要設計） | - |
| `multiple_selection_not_in` | データソース依存（要設計） | - |

### NULL を含む IN 句のパターン
`selection_in` の `values` に NULL（`NullableOption` の `option = null`）が混在する場合：

```sql
-- ダメ
WHERE status IN ('a', 'b', NULL)

-- 正解
WHERE (status IN ('a', 'b') OR status IS NULL)
```

### `MatchOperator` の扱い
- `MATCH_OPERATOR_ALL` → 各条件を `AND` で結合
- `MATCH_OPERATOR_ANY` → 各条件を `OR` で結合
- 条件が空配列の場合は `1=1` を返す

---

## 5. Insert の実装

```kotlin
override suspend fun insert(req: InsertRequest): InsertResponse {
    val payload = req.payload ?: throw ConnectException(Code.InvalidArgument)
    return dataSource.connection.use { conn ->
        conn.autoCommit = false
        try {
            val ids = mutableListOf<Long>()
            val textIds = mutableListOf<RecordId>()
            for (record in payload.recordsList) {
                val (columns, values) = recordToInsertParams(record)
                val sql = "INSERT INTO ${config.table.name} (${columns.joinToString(",")}) VALUES (${columns.map { "?" }.joinToString(",")})"
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                    values.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
                    stmt.executeUpdate()
                    val keys = stmt.generatedKeys
                    if (keys.next()) {
                        if (config.recordIdType == NUMBER) ids += keys.getLong(1)
                        else textIds += RecordId { valueText = keys.getString(1) }
                    }
                }
            }
            conn.commit()
            InsertResponse {
                payload = InsertResponsePayload {
                    if (config.recordIdType == NUMBER) this.ids += ids
                    else this.recordIds += textIds
                }
            }
        } catch (e: Exception) {
            conn.rollback()
            throw e
        }
    }
}
```

**注意**：
- CData JDBC Driver は `Statement.RETURN_GENERATED_KEYS` を対応するドライバと未対応ドライバがある
- Salesforce では INSERT 後に生成 ID（18文字英数）が返る
- BigInt auto-increment は MySQL/PostgreSQL では `getLong(1)`
- ドライバごとの挙動差を吸収するため、INSERT 後に主キーで再 SELECT する設計も検討

---

## 6. Update の実装

```kotlin
override suspend fun update(req: UpdateRequest): UpdateResponse {
    val payload = req.payload ?: throw ConnectException(Code.InvalidArgument)
    return dataSource.connection.use { conn ->
        conn.autoCommit = false
        try {
            val updatedRecords = mutableListOf<UpdatedRecordInfo>()
            for (record in payload.recordsList) {
                val idField = record.fieldsMap[config.primaryKey.kintoneFieldId]
                    ?: throw ConnectException(Code.InvalidArgument, "ID required")
                val (id, idIsText) = extractRecordId(idField)
                
                val (setColumns, setValues) = recordToUpdateParams(record, excludeId = true)
                val sql = "UPDATE ${config.table.name} SET ${setColumns.joinToString(", ") { "$it = ?" }} WHERE ${config.primaryKey.jdbcColumn} = ?"
                conn.prepareStatement(sql).use { stmt ->
                    setValues.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
                    if (idIsText) stmt.setString(setValues.size + 1, id as String)
                    else stmt.setLong(setValues.size + 1, id as Long)
                    stmt.executeUpdate()
                }
                updatedRecords += UpdatedRecordInfo {
                    if (idIsText) recordId = RecordId { valueText = id as String }
                    else this.id = id as Long
                }
            }
            conn.commit()
            UpdateResponse { payload = UpdateResponsePayload { records += updatedRecords } }
        } catch (e: Exception) {
            conn.rollback(); throw e
        }
    }
}
```

---

## 7. Delete の実装

```kotlin
override suspend fun delete(req: DeleteRequest): DeleteResponse {
    val payload = req.payload ?: throw ConnectException(Code.InvalidArgument)
    val ids = payload.idsList.takeIf { it.isNotEmpty() } ?: payload.recordIdsList.map { it.valueText }
    if (ids.isEmpty()) return DeleteResponse { payload = DeleteResponsePayload {} }
    
    return dataSource.connection.use { conn ->
        val sql = "DELETE FROM ${config.table.name} WHERE ${config.primaryKey.jdbcColumn} IN (${ids.map { "?" }.joinToString(",")})"
        conn.prepareStatement(sql).use { stmt ->
            ids.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
            stmt.executeUpdate()
        }
        DeleteResponse { payload = DeleteResponsePayload {} }
    }
}
```

---

## 8. Count の実装

データソースによっては `COUNT(*)` が大量データで重い（Salesforce の大規模オブジェクト等）。
そのため `count-strategy` 設定でクエリスキップを可能にする：

| `count-strategy` | 挙動 |
|---|---|
| `ACTUAL`（デフォルト） | `SELECT COUNT(*) FROM table WHERE ...` を実行 |
| `ALWAYS_ZERO` | DB クエリせず即座に `count: 0` を返却 |

```kotlin
override suspend fun count(req: CountRequest): CountResponse {
    val payload = req.payload ?: throw ConnectException(Code.InvalidArgument)
    
    // ALWAYS_ZERO 戦略: クエリスキップ
    if (config.capability.countStrategy == CountStrategy.ALWAYS_ZERO) {
        return CountResponse { payload = CountResponsePayload { count = 0L } }
    }
    
    // ACTUAL 戦略: 通常の COUNT(*) 実行
    val (whereClause, bindParams) = filterToSql(payload.filterConditionsList, payload.matchOperator)
    val sql = "SELECT COUNT(*) FROM ${config.table.name} WHERE $whereClause"
    return dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            bindParams.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            val rs = stmt.executeQuery()
            rs.next()
            CountResponse { payload = CountResponsePayload { count = rs.getLong(1) } }
        }
    }
}
```

**トレードオフ**: `ALWAYS_ZERO` 時は kintone のページング UI（「全N件中M件表示」等）が正確に表示されない可能性がある。README で明示的に説明する。

---

## 9. Search の実装（オプション）

データソースによって実装方針が大きく異なる：

- **Salesforce**: `SOSL` クエリ（CData JDBC の `SEARCH` 文）
- **MySQL FULLTEXT**: `MATCH(col) AGAINST(? IN BOOLEAN MODE)`
- **PostgreSQL**: `tsvector @@ tsquery`
- **その他**: `LIKE '%keyword%'` で OR 連結（パフォーマンス劣化）

設定ファイルで `search-strategy: SOSL | FULLTEXT | LIKE` を選べるようにするのが汎用的。

---

## 10. Aggregate の実装（オプション）

```sql
SELECT 
    [GROUP BY 列],
    COUNT(*) AS count,
    SUM(col) AS sum,
    AVG(col) AS avg
FROM table
WHERE [filter]
GROUP BY [GROUP BY 列]
ORDER BY [sort]
LIMIT ?
```

`GroupingMethod.DATETIME_*` の翻訳：

| GroupingMethod | SQL 表現 |
|---|---|
| `DATETIME_YEAR` | `EXTRACT(YEAR FROM field)` または `DATE_TRUNC('year', field)` |
| `DATETIME_MONTH` | `DATE_TRUNC('month', field)` |
| `DATETIME_DAY` | `DATE(field)` または `DATE_TRUNC('day', field)` |
| `DATETIME_HOUR` | `DATE_TRUNC('hour', field)` |
| `DATETIME_MINUTE` | `DATE_TRUNC('minute', field)` |
| `DATETIME_QUARTER` | （未対応） |
| `DATETIME_WEEK` | （未対応） |

CData JDBC は `DATE_TRUNC` を対応データソースで正規化。

---

## 11. 型変換ヘルパー実装

### protobuf Timestamp ⇄ java.sql.Timestamp

```kotlin
fun protoTimestampToSqlTimestamp(ts: com.google.protobuf.Timestamp): java.sql.Timestamp {
    return java.sql.Timestamp.from(java.time.Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()))
}

fun sqlTimestampToProtoTimestamp(ts: java.sql.Timestamp): com.google.protobuf.Timestamp {
    val instant = ts.toInstant()
    return com.google.protobuf.Timestamp.newBuilder()
        .setSeconds(instant.epochSecond)
        .setNanos(instant.nano)
        .build()
}
```

### ResultSet row ⇄ Record

```kotlin
fun resultSetRowToRecord(rs: ResultSet): Record {
    val fields = mutableMapOf<String, Field>()
    for (col in config.table.allColumns) {
        val raw = rs.getObject(col.jdbcColumn)
        if (rs.wasNull() && col.type != "RECORD_ID") {
            // NULL: 該当 Field を追加しないか、明示的に空を入れるか
            continue
        }
        fields[col.kintoneFieldId] = when (col.type) {
            "RECORD_ID" -> recordIdField(col.kintoneFieldId, raw)
            "TEXT" -> textField(col.kintoneFieldId, raw as String)
            "NUMBER" -> numberField(col.kintoneFieldId, (raw as Number).toDouble())
            "DATETIME" -> datetimeField(col.kintoneFieldId, sqlTimestampToProtoTimestamp(raw as Timestamp))
            "SELECTION" -> selectionField(col.kintoneFieldId, raw as String?)
            else -> error("Unknown type")
        }
    }
    return Record { this.fields += fields }
}
```

---

## 12. CData JDBC 固有の注意点

- **ライセンスキー**：本番ではランタイムキーまたは OEM キー。開発時はトライアル。
- **接続文字列**：`jdbc:salesforce:User=...;Password=...;SecurityToken=...;OAuthClientId=...;OAuthClientSecret=...;`
- **メタデータキャッシュ**：CData JDBC は `CacheLocation` 設定でローカル SQLite キャッシュ可能。スキーマ取得を高速化
- **PreparedStatement の `setObject`**：CData は型推論するが、明示的に `setString` / `setLong` 等を使うほうが安全
- **トランザクション**：データソースによっては未サポート（Salesforce 等）。`autoCommit = false` がエラーになる場合は、エラーハンドリングで補完
- **接続プール**：HikariCP の `maximum-pool-size` は **kintone の同時リクエスト数** に合わせる。Adapter は 1 テーブル専用なので 5〜10 で十分

---

## 13. SQL インジェクション対策

**絶対に文字列結合で値を埋め込まないこと**。常に PreparedStatement の `setXxx` でバインド。

```kotlin
// NG
val sql = "SELECT * FROM tbl WHERE name = '${value}'"

// OK
val sql = "SELECT * FROM tbl WHERE name = ?"
stmt.setString(1, value)
```

特に **`IN` 句の動的展開** が要注意：

```kotlin
// 値の数だけ ? を生成
val placeholders = values.map { "?" }.joinToString(",")
val sql = "SELECT * FROM tbl WHERE id IN ($placeholders)"
values.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
```

---

## 14. テストカバレッジ指標

最低限、以下のテストケースを用意：

- [ ] 全 39 FilterCondition case の SQL 生成テスト
- [ ] NULL 値を含む `IN` 句のテスト
- [ ] TEXT / NUMBER 両 RecordIdType のテスト
- [ ] Select / Insert / Update / Delete / Count の基本動作テスト
- [ ] 大量レコード（500 件 = limit 上限）のテスト
- [ ] トランザクション ロールバックのテスト
- [ ] 各種データソース（Salesforce / Google Sheets / SQL Server 等）の動作確認
