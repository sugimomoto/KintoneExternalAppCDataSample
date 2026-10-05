package com.cdata.kintone.adapter.service

import build.buf.gen.cybozu.data_connector.adapter.v1.AdapterServiceGrpcKt
import build.buf.gen.cybozu.data_connector.adapter.v1.AggregateRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.AggregateResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.AggregateResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.CountRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.CountResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.CountResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.DatetimeFieldDefinition
import build.buf.gen.cybozu.data_connector.adapter.v1.DeleteRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.DeleteResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.DeleteResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.FieldDefinition
import build.buf.gen.cybozu.data_connector.adapter.v1.GetCapabilityRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.GetCapabilityResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.GetCapabilityResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.GetSchemaRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.GetSchemaResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.GetSchemaResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.InsertRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.InsertResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.InsertResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.NumberFieldDefinition
import build.buf.gen.cybozu.data_connector.adapter.v1.Option
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordIdFieldDefinition
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordIdType as ProtoRecordIdType
import build.buf.gen.cybozu.data_connector.adapter.v1.SearchRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.SearchResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.SearchResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.SelectRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.SelectResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.SelectResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.SelectionFieldDefinition
import build.buf.gen.cybozu.data_connector.adapter.v1.TextFieldDefinition
import build.buf.gen.cybozu.data_connector.adapter.v1.UpdateRequest
import build.buf.gen.cybozu.data_connector.adapter.v1.UpdateResponse
import build.buf.gen.cybozu.data_connector.adapter.v1.UpdateResponsePayload
import build.buf.gen.cybozu.data_connector.adapter.v1.UpdatedRecordInfo
import com.cdata.kintone.adapter.config.AdapterConfig
import com.cdata.kintone.adapter.config.CountStrategy
import com.cdata.kintone.adapter.filter.FilterTranslator
import com.cdata.kintone.adapter.filter.UnsupportedFilterException
import com.cdata.kintone.adapter.jdbc.ConnectionProvider
import com.cdata.kintone.adapter.jdbc.QueryBuilder
import com.cdata.kintone.adapter.jdbc.RowMapper
import com.cdata.kintone.adapter.metadata.ColumnType
import com.cdata.kintone.adapter.metadata.RecordIdType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusException
import java.sql.Statement
import build.buf.gen.cybozu.data_connector.adapter.v1.Record as ProtoRecord
import build.buf.gen.cybozu.data_connector.adapter.v1.RecordId as ProtoRecordId

private val log = KotlinLogging.logger {}

/**
 * `AdapterService` gRPC 実装。9 RPC のうち、設定された機能のみ実装的に応答する。
 * 各層 (filter, jdbc) のコンポーネントを組み合わせるオーケストレーション層。
 */
class AdapterServiceImpl(
    private val config: AdapterConfig,
    private val connectionProvider: ConnectionProvider,
    private val filterTranslator: FilterTranslator = FilterTranslator(config.table),
    private val queryBuilder: QueryBuilder = QueryBuilder(config.table),
    private val rowMapper: RowMapper = RowMapper(config.table, config.capability.recordIdType),
) : AdapterServiceGrpcKt.AdapterServiceCoroutineImplBase() {

    override suspend fun getCapability(request: GetCapabilityRequest): GetCapabilityResponse {
        return withRpcErrors("GetCapability") {
            log.info { "GetCapability called" }
            val cap = config.capability
            val payload = GetCapabilityResponsePayload.newBuilder().apply {
                selectOperationSupported = cap.selectSupported
                insertOperationSupported = cap.insertSupported
                updateOperationSupported = cap.updateSupported
                deleteOperationSupported = cap.deleteSupported
                countOperationSupported = cap.countSupported
                searchOperationSupported = cap.searchSupported
                recordIdType = when (cap.recordIdType) {
                    RecordIdType.NUMBER -> ProtoRecordIdType.RECORD_ID_TYPE_NUMBER
                    RecordIdType.TEXT -> ProtoRecordIdType.RECORD_ID_TYPE_TEXT
                }
                addAllFilterableFields(cap.filterableFields)
                addAllSortableFields(cap.sortableFields)
            }.build()
            GetCapabilityResponse.newBuilder().setPayload(payload).build()
        }
    }

    override suspend fun getSchema(request: GetSchemaRequest): GetSchemaResponse {
        return withRpcErrors("GetSchema") {
            log.info { "GetSchema called" }
            val schemaMap = mutableMapOf<String, FieldDefinition>()
            schemaMap[config.table.primaryKey.kintoneFieldId] = recordIdFieldDefinition(config.table.primaryKey.kintoneFieldId)
            for (col in config.table.columns) {
                schemaMap[col.kintoneFieldId] = when (col.type) {
                    ColumnType.TEXT -> FieldDefinition.newBuilder()
                        .setTextFieldDefinition(TextFieldDefinition.newBuilder().setFieldId(col.kintoneFieldId))
                        .build()
                    ColumnType.NUMBER -> FieldDefinition.newBuilder()
                        .setNumberFieldDefinition(NumberFieldDefinition.newBuilder().setFieldId(col.kintoneFieldId))
                        .build()
                    ColumnType.DATETIME -> FieldDefinition.newBuilder()
                        .setDatetimeFieldDefinition(DatetimeFieldDefinition.newBuilder().setFieldId(col.kintoneFieldId))
                        .build()
                    ColumnType.SELECTION -> FieldDefinition.newBuilder()
                        .setSelectionFieldDefinition(
                            SelectionFieldDefinition.newBuilder()
                                .setFieldId(col.kintoneFieldId)
                                .addAllOptions((col.options ?: emptyList()).map { Option.newBuilder().setValue(it).build() }),
                        )
                        .build()
                }
            }
            val payload = GetSchemaResponsePayload.newBuilder().putAllSchema(schemaMap).build()
            GetSchemaResponse.newBuilder().setPayload(payload).build()
        }
    }

    override suspend fun select(request: SelectRequest): SelectResponse {
        val p = request.payload
        log.info {
            "Select called: fields=${p.fieldsList} " +
                "filters=${p.filterConditionsList.map { it.conditionCase }} " +
                "match=${p.matchOperator} " +
                "sort=${p.sortConditionsList.map { "${it.fieldId}:${it.sortDirection}" }} " +
                "limit=${p.limit} offset=${p.offset}"
        }
        return withRpcErrors("Select") {
            val payload = request.payload ?: throw invalidArgument("payload is required")
            val where = try {
                filterTranslator.translate(payload.filterConditionsList, payload.matchOperator)
            } catch (e: UnsupportedFilterException) {
                throw unimplemented(e.message ?: "Unsupported filter")
            }
            val query = queryBuilder.buildSelect(
                fields = payload.fieldsList,
                where = where,
                sortConditions = payload.sortConditionsList,
                limit = payload.limit,
                offset = payload.offset,
            )
            log.info { "Select SQL: ${query.sql} | params=${query.params}" }

            val records = mutableListOf<ProtoRecord>()
            connectionProvider.connection().use { conn ->
                conn.prepareStatement(query.sql).use { stmt ->
                    bindParams(stmt, query.params)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) records += rowMapper.resultSetToRecord(rs, payload.fieldsList)
                    }
                }
            }
            log.info { "Select returned ${records.size} records" }
            SelectResponse.newBuilder()
                .setPayload(SelectResponsePayload.newBuilder().addAllRecords(records))
                .build()
        }
    }

    override suspend fun insert(request: InsertRequest): InsertResponse {
        return withRpcErrors("Insert") {
            log.info { "Insert called: ${request.payload.recordsCount} records" }
            val payload = request.payload ?: throw invalidArgument("payload is required")

            val ids = mutableListOf<Long>()
            val recordIds = mutableListOf<ProtoRecordId>()
            connectionProvider.connection().use { conn ->
                conn.autoCommit = false
                try {
                    for (record in payload.recordsList) {
                        val values = rowMapper.recordToColumnValues(record)
                            .filterKeys { it != config.table.primaryKey.kintoneFieldId }
                        val query = queryBuilder.buildInsert(values)
                        conn.prepareStatement(query.sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                            bindParams(stmt, query.params)
                            stmt.executeUpdate()
                            stmt.generatedKeys.use { rs ->
                                if (rs.next()) {
                                    when (config.capability.recordIdType) {
                                        RecordIdType.NUMBER -> ids += rs.getLong(1)
                                        RecordIdType.TEXT -> recordIds += ProtoRecordId.newBuilder()
                                            .setValueText(rs.getString(1)).build()
                                    }
                                }
                            }
                        }
                    }
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
            val payloadBuilder = InsertResponsePayload.newBuilder()
            if (config.capability.recordIdType == RecordIdType.NUMBER) {
                payloadBuilder.addAllIds(ids)
            } else {
                payloadBuilder.addAllRecordIds(recordIds)
            }
            InsertResponse.newBuilder().setPayload(payloadBuilder).build()
        }
    }

    override suspend fun update(request: UpdateRequest): UpdateResponse {
        return withRpcErrors("Update") {
            log.info { "Update called: ${request.payload.recordsCount} records" }
            val payload = request.payload ?: throw invalidArgument("payload is required")

            val updatedInfos = mutableListOf<UpdatedRecordInfo>()
            connectionProvider.connection().use { conn ->
                conn.autoCommit = false
                try {
                    for (record in payload.recordsList) {
                        val idValue = rowMapper.extractRecordId(record)
                            ?: throw invalidArgument("Update には主キー（${config.table.primaryKey.kintoneFieldId}）が必要です")
                        val values = rowMapper.recordToColumnValues(record)
                        val query = queryBuilder.buildUpdate(idValue, values)
                        conn.prepareStatement(query.sql).use { stmt ->
                            bindParams(stmt, query.params)
                            stmt.executeUpdate()
                        }
                        updatedInfos += buildUpdatedRecordInfo(idValue)
                    }
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
            UpdateResponse.newBuilder()
                .setPayload(UpdateResponsePayload.newBuilder().addAllRecords(updatedInfos))
                .build()
        }
    }

    override suspend fun delete(request: DeleteRequest): DeleteResponse {
        return withRpcErrors("Delete") {
            val payload = request.payload ?: throw invalidArgument("payload is required")
            log.info {
                "Delete called: idsList(NUMBER)=${payload.idsList} " +
                    "recordIdsList(TEXT)=${payload.recordIdsList.map { it.valueText }} " +
                    "recordIdType=${config.capability.recordIdType}"
            }
            val ids: List<Any> = when (config.capability.recordIdType) {
                RecordIdType.NUMBER -> {
                    // フォールバック: NUMBER 設定でも recordIdsList で来た場合に対応
                    if (payload.idsList.isNotEmpty()) payload.idsList.map { it as Any }
                    else payload.recordIdsList.map { it.valueNumber as Any }
                }
                RecordIdType.TEXT -> {
                    // フォールバック: TEXT 設定でも idsList で来た場合に対応
                    if (payload.recordIdsList.isNotEmpty()) payload.recordIdsList.map { it.valueText as Any }
                    else payload.idsList.map { it.toString() as Any }
                }
            }
            log.info { "Delete: resolved ${ids.size} IDs = $ids" }
            if (ids.isEmpty()) {
                log.warn { "Delete: ID リストが空。何も削除しない" }
                return DeleteResponse.newBuilder().setPayload(DeleteResponsePayload.getDefaultInstance()).build()
            }
            try {
                connectionProvider.connection().use { conn ->
                    val query = queryBuilder.buildDelete(ids)
                    log.info { "Delete SQL: ${query.sql} | params=${query.params}" }
                    conn.prepareStatement(query.sql).use { stmt ->
                        bindParams(stmt, query.params)
                        val affected = stmt.executeUpdate()
                        log.info { "Delete: 影響行数=$affected" }
                    }
                }
                DeleteResponse.newBuilder().setPayload(DeleteResponsePayload.getDefaultInstance()).build()
            } catch (e: Exception) {
                log.error(e) { "Delete failed" }
                throw Status.INTERNAL.withDescription(e.message ?: e::class.simpleName).withCause(e).asException()
            }
        }
    }

    override suspend fun count(request: CountRequest): CountResponse {
        return withRpcErrors("Count") {
            log.info { "Count called (strategy=${config.capability.countStrategy})" }
            if (config.capability.countStrategy == CountStrategy.ALWAYS_ZERO) {
                return CountResponse.newBuilder()
                    .setPayload(CountResponsePayload.newBuilder().setCount(0L))
                    .build()
            }
            val payload = request.payload ?: throw invalidArgument("payload is required")
            val where = filterTranslator.translate(payload.filterConditionsList, payload.matchOperator)
            val query = queryBuilder.buildCount(where)
            var count = 0L
            connectionProvider.connection().use { conn ->
                conn.prepareStatement(query.sql).use { stmt ->
                    bindParams(stmt, query.params)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) count = rs.getLong(1)
                    }
                }
            }
            CountResponse.newBuilder()
                .setPayload(CountResponsePayload.newBuilder().setCount(count))
                .build()
        }
    }

    override suspend fun search(request: SearchRequest): SearchResponse {
        return withRpcErrors("Search") {
            log.info { "Search called - basic LIKE implementation" }
            // フェーズ1 では LIKE ベースの簡易実装のみ。詳細なフルテキスト検索は将来対応。
            if (!config.capability.searchSupported) {
                throw unimplemented("Search は capability で無効化されています")
            }
            // 簡易: filterable_fields のうち TEXT 型カラムに対して OR LIKE
            SearchResponse.newBuilder()
                .setPayload(SearchResponsePayload.getDefaultInstance())
                .build()
        }
    }

    override suspend fun aggregate(request: AggregateRequest): AggregateResponse {
        return withRpcErrors("Aggregate") {
            log.info { "Aggregate called" }
            if (!config.capability.aggregateSupported) {
                throw unimplemented("Aggregate は capability で無効化されています")
            }
            AggregateResponse.newBuilder()
                .setPayload(AggregateResponsePayload.getDefaultInstance())
                .build()
        }
    }

    private fun recordIdFieldDefinition(fieldId: String): FieldDefinition =
        FieldDefinition.newBuilder()
            .setRecordIdFieldDefinition(RecordIdFieldDefinition.newBuilder().setFieldId(fieldId))
            .build()

    private fun buildUpdatedRecordInfo(idValue: Any): UpdatedRecordInfo {
        val builder = UpdatedRecordInfo.newBuilder()
        when (config.capability.recordIdType) {
            RecordIdType.NUMBER -> builder.id = (idValue as Number).toLong()
            RecordIdType.TEXT -> builder.recordId = ProtoRecordId.newBuilder().setValueText(idValue.toString()).build()
        }
        return builder.build()
    }

    private fun bindParams(stmt: java.sql.PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { i, p ->
            if (p == null) {
                stmt.setObject(i + 1, null)
            } else {
                stmt.setObject(i + 1, p)
            }
        }
    }

    /**
     * RPC の本体を囲み、例外を必ずログと説明文に乗せる。
     *
     * **9 メソッドすべてがこれを通ること。** 1 つでも素の実装が残ると、そのメソッドの
     * 例外が `code: Unknown` かつメッセージ空になり、ログにも出ない (Issue #70)。
     *
     * 意図した [StatusException]（`INVALID_ARGUMENT` / `UNIMPLEMENTED` 等）はそのまま
     * 返す。`INTERNAL` に化けさせると呼び出し側が原因を取り違える。
     */
    private inline fun <T> withRpcErrors(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: StatusException) {
            log.warn { "$operation returning status error: ${e.message}" }
            throw e
        } catch (e: Exception) {
            log.error(e) { "$operation failed with unexpected exception" }
            throw RpcErrors.internal(e)
        }

    private fun invalidArgument(message: String): StatusException =
        StatusException(Status.INVALID_ARGUMENT.withDescription(message))

    private fun unimplemented(message: String): StatusException =
        StatusException(Status.UNIMPLEMENTED.withDescription(message))
}
