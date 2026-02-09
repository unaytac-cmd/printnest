package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.ApiLogs
import com.printnest.domain.tables.SyncStatuses
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class ApiLogRepository : KoinComponent {

    private val logger = LoggerFactory.getLogger(ApiLogRepository::class.java)
    private val json: Json by inject()

    // =====================================================
    // CREATE
    // =====================================================

    fun create(
        tenantId: Long?,
        userId: Long?,
        endpoint: String,
        method: String,
        statusCode: Int,
        durationMs: Long,
        requestSize: Long? = null,
        responseSize: Long? = null,
        userAgent: String? = null,
        ipAddress: String? = null,
        errorMessage: String? = null,
        errorStackTrace: String? = null,
        requestBody: String? = null,
        responseBody: String? = null
    ): Long = transaction {
        ApiLogs.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.endpoint] = endpoint.take(500)
            it[this.method] = method.take(10)
            it[this.statusCode] = statusCode
            it[this.durationMs] = durationMs
            it[this.requestSize] = requestSize
            it[this.responseSize] = responseSize
            it[this.userAgent] = userAgent?.take(500)
            it[this.ipAddress] = ipAddress?.take(50)
            it[this.errorMessage] = errorMessage?.take(2000)
            it[this.errorStackTrace] = errorStackTrace?.take(10000)
            it[this.requestBody] = requestBody?.take(10000)
            it[this.responseBody] = responseBody?.take(10000)
        }.value
    }

    // =====================================================
    // READ
    // =====================================================

    fun findById(id: Long): ApiLog? = transaction {
        ApiLogs.selectAll()
            .where { ApiLogs.id eq id }
            .singleOrNull()
            ?.toApiLog()
    }

    fun findAll(filters: ApiLogFilters): Pair<List<ApiLog>, Int> = transaction {
        var query = ApiLogs.selectAll()

        filters.tenantId?.let { tid ->
            query = query.andWhere { ApiLogs.tenantId eq tid }
        }

        filters.userId?.let { uid ->
            query = query.andWhere { ApiLogs.userId eq uid }
        }

        filters.endpoint?.let { ep ->
            query = query.andWhere { ApiLogs.endpoint like "%$ep%" }
        }

        filters.method?.let { m ->
            query = query.andWhere { ApiLogs.method eq m.uppercase() }
        }

        filters.statusCode?.let { sc ->
            query = query.andWhere { ApiLogs.statusCode eq sc }
        }

        filters.minDuration?.let { md ->
            query = query.andWhere { ApiLogs.durationMs greaterEq md }
        }

        filters.hasError?.let { he ->
            if (he) {
                query = query.andWhere { ApiLogs.statusCode greaterEq 400 }
            } else {
                query = query.andWhere { ApiLogs.statusCode less 400 }
            }
        }

        filters.startDate?.let { sd ->
            try {
                val startInstant = Instant.parse(sd)
                query = query.andWhere { ApiLogs.createdAt greaterEq startInstant }
            } catch (e: Exception) {
                logger.warn("Invalid start date format: $sd")
            }
        }

        filters.endDate?.let { ed ->
            try {
                val endInstant = Instant.parse(ed)
                query = query.andWhere { ApiLogs.createdAt lessEq endInstant }
            } catch (e: Exception) {
                logger.warn("Invalid end date format: $ed")
            }
        }

        val total = query.count().toInt()

        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query
            .orderBy(ApiLogs.createdAt, SortOrder.DESC)
            .limit(filters.limit)
            .offset(offset)

        val logs = query.map { it.toApiLog() }

        Pair(logs, total)
    }

    fun findByOrderId(orderId: Long, limit: Int = 100): List<ApiLogSummary> = transaction {
        ApiLogs.selectAll()
            .where { ApiLogs.endpoint like "%$orderId%" }
            .orderBy(ApiLogs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toApiLogSummary() }
    }

    fun findRecentErrors(limit: Int = 50): List<ApiLogSummary> = transaction {
        ApiLogs.selectAll()
            .where { ApiLogs.statusCode greaterEq 400 }
            .orderBy(ApiLogs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toApiLogSummary() }
    }

    fun findSlowRequests(minDurationMs: Long, limit: Int = 50): List<ApiLogSummary> = transaction {
        ApiLogs.selectAll()
            .where { ApiLogs.durationMs greaterEq minDurationMs }
            .orderBy(ApiLogs.durationMs, SortOrder.DESC)
            .limit(limit)
            .map { it.toApiLogSummary() }
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    fun getStats(tenantId: Long? = null, hours: Int = 24): ApiLogStats = transaction {
        val cutoff = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS)

        val baseQuery = if (tenantId != null) {
            ApiLogs.selectAll().where { (ApiLogs.tenantId eq tenantId) and (ApiLogs.createdAt greaterEq cutoff) }
        } else {
            ApiLogs.selectAll().where { ApiLogs.createdAt greaterEq cutoff }
        }

        val totalRequests = baseQuery.count()

        val errorCount = ApiLogs.selectAll()
            .where {
                (ApiLogs.createdAt greaterEq cutoff) and
                (ApiLogs.statusCode greaterEq 400)
            }
            .apply { tenantId?.let { andWhere { ApiLogs.tenantId eq it } } }
            .count()

        val errorRate = if (totalRequests > 0) {
            (errorCount.toDouble() / totalRequests.toDouble()) * 100
        } else 0.0

        val avgDurationQuery = ApiLogs.select(ApiLogs.durationMs.avg())
            .where { ApiLogs.createdAt greaterEq cutoff }
            .apply { tenantId?.let { andWhere { ApiLogs.tenantId eq it } } }
        val avgDuration = avgDurationQuery
            .singleOrNull()
            ?.get(ApiLogs.durationMs.avg())
            ?.toDouble() ?: 0.0

        val requestsByEndpoint = ApiLogs.select(ApiLogs.endpoint, ApiLogs.id.count())
            .where { ApiLogs.createdAt greaterEq cutoff }
            .apply { tenantId?.let { andWhere { ApiLogs.tenantId eq it } } }
            .groupBy(ApiLogs.endpoint)
            .associate { it[ApiLogs.endpoint] to it[ApiLogs.id.count()] }

        val requestsByMethod = ApiLogs.select(ApiLogs.method, ApiLogs.id.count())
            .where { ApiLogs.createdAt greaterEq cutoff }
            .apply { tenantId?.let { andWhere { ApiLogs.tenantId eq it } } }
            .groupBy(ApiLogs.method)
            .associate { it[ApiLogs.method] to it[ApiLogs.id.count()] }

        val errorsByStatusCode = ApiLogs.select(ApiLogs.statusCode, ApiLogs.id.count())
            .where {
                (ApiLogs.createdAt greaterEq cutoff) and
                (ApiLogs.statusCode greaterEq 400)
            }
            .apply { tenantId?.let { andWhere { ApiLogs.tenantId eq it } } }
            .groupBy(ApiLogs.statusCode)
            .associate { it[ApiLogs.statusCode] to it[ApiLogs.id.count()] }

        // Slowest endpoints - simplified query
        val slowestEndpoints = ApiLogs.select(
            ApiLogs.endpoint,
            ApiLogs.durationMs.avg(),
            ApiLogs.durationMs.max(),
            ApiLogs.id.count()
        )
            .where { ApiLogs.createdAt greaterEq cutoff }
            .apply { tenantId?.let { andWhere { ApiLogs.tenantId eq it } } }
            .groupBy(ApiLogs.endpoint)
            .orderBy(ApiLogs.durationMs.avg(), SortOrder.DESC)
            .limit(10)
            .map { row ->
                EndpointStats(
                    endpoint = row[ApiLogs.endpoint],
                    avgDurationMs = row[ApiLogs.durationMs.avg()]?.toDouble() ?: 0.0,
                    maxDurationMs = row[ApiLogs.durationMs.max()] ?: 0L,
                    requestCount = row[ApiLogs.id.count()],
                    errorCount = 0 // Simplified - would need subquery for accurate count
                )
            }

        ApiLogStats(
            totalRequests = totalRequests,
            errorRate = errorRate,
            avgDurationMs = avgDuration,
            requestsByEndpoint = requestsByEndpoint,
            requestsByMethod = requestsByMethod,
            errorsByStatusCode = errorsByStatusCode,
            slowestEndpoints = slowestEndpoints
        )
    }

    // =====================================================
    // CLEANUP
    // =====================================================

    fun deleteOldLogs(olderThanDays: Int = 30): Int = transaction {
        val cutoff = Instant.now().minusSeconds(olderThanDays.toLong() * 24 * 60 * 60)

        ApiLogs.deleteWhere {
            createdAt lessEq cutoff
        }
    }

    fun deleteByTenant(tenantId: Long): Int = transaction {
        ApiLogs.deleteWhere {
            ApiLogs.tenantId eq tenantId
        }
    }

    // =====================================================
    // SYNC STATUS METHODS
    // =====================================================

    fun findSyncStatus(tenantId: Long, storeId: Long): SyncStatus? = transaction {
        SyncStatuses.selectAll()
            .where { (SyncStatuses.tenantId eq tenantId) and (SyncStatuses.storeId eq storeId) }
            .singleOrNull()
            ?.toSyncStatus()
    }

    fun findAllSyncStatuses(tenantId: Long): List<SyncStatus> = transaction {
        SyncStatuses.selectAll()
            .where { SyncStatuses.tenantId eq tenantId }
            .orderBy(SyncStatuses.updatedAt, SortOrder.DESC)
            .map { it.toSyncStatus() }
    }

    fun upsertSyncStatus(
        tenantId: Long,
        storeId: Long?,
        marketplace: String,
        storeName: String?,
        syncState: String,
        errorMessage: String? = null,
        ordersImported: Int = 0,
        ordersSynced: Int = 0
    ): Long = transaction {
        val existing = storeId?.let { sid ->
            SyncStatuses.selectAll()
                .where { (SyncStatuses.tenantId eq tenantId) and (SyncStatuses.storeId eq sid) }
                .singleOrNull()
        }

        if (existing != null) {
            val id = existing[SyncStatuses.id].value
            SyncStatuses.update({ SyncStatuses.id eq id }) {
                it[this.syncState] = syncState
                it[lastSyncAt] = Instant.now()
                if (syncState == "error") {
                    it[lastErrorAt] = Instant.now()
                    it[lastErrorMessage] = errorMessage
                    it[errorCount] = existing[SyncStatuses.errorCount] + 1
                } else if (syncState == "idle") {
                    it[lastSuccessAt] = Instant.now()
                    it[errorCount] = 0
                }
                it[this.ordersImported] = ordersImported
                it[this.ordersSynced] = ordersSynced
                it[updatedAt] = Instant.now()
            }
            id
        } else {
            SyncStatuses.insertAndGetId {
                it[this.tenantId] = tenantId
                it[this.storeId] = storeId
                it[this.marketplace] = marketplace
                it[this.storeName] = storeName
                it[this.syncState] = syncState
                it[lastSyncAt] = Instant.now()
                if (syncState == "error") {
                    it[lastErrorAt] = Instant.now()
                    it[lastErrorMessage] = errorMessage
                    it[errorCount] = 1
                } else {
                    it[lastSuccessAt] = Instant.now()
                    it[errorCount] = 0
                }
                it[this.ordersImported] = ordersImported
                it[this.ordersSynced] = ordersSynced
            }.value
        }
    }

    fun updateSyncSuccess(tenantId: Long, storeId: Long, ordersImported: Int, ordersSynced: Int): Boolean = transaction {
        SyncStatuses.update({
            (SyncStatuses.tenantId eq tenantId) and (SyncStatuses.storeId eq storeId)
        }) {
            it[syncState] = "idle"
            it[lastSyncAt] = Instant.now()
            it[lastSuccessAt] = Instant.now()
            it[errorCount] = 0
            it[lastErrorMessage] = null
            it[this.ordersImported] = ordersImported
            it[this.ordersSynced] = ordersSynced
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateSyncError(tenantId: Long, storeId: Long, errorMessage: String): Boolean = transaction {
        val existing = SyncStatuses.selectAll()
            .where { (SyncStatuses.tenantId eq tenantId) and (SyncStatuses.storeId eq storeId) }
            .singleOrNull()

        val currentErrorCount = existing?.get(SyncStatuses.errorCount) ?: 0

        SyncStatuses.update({
            (SyncStatuses.tenantId eq tenantId) and (SyncStatuses.storeId eq storeId)
        }) {
            it[syncState] = "error"
            it[lastSyncAt] = Instant.now()
            it[lastErrorAt] = Instant.now()
            it[lastErrorMessage] = errorMessage
            it[errorCount] = currentErrorCount + 1
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toApiLog(): ApiLog = ApiLog(
        id = this[ApiLogs.id].value,
        tenantId = this[ApiLogs.tenantId],
        userId = this[ApiLogs.userId],
        endpoint = this[ApiLogs.endpoint],
        method = this[ApiLogs.method],
        statusCode = this[ApiLogs.statusCode],
        durationMs = this[ApiLogs.durationMs],
        requestSize = this[ApiLogs.requestSize],
        responseSize = this[ApiLogs.responseSize],
        userAgent = this[ApiLogs.userAgent],
        ipAddress = this[ApiLogs.ipAddress],
        errorMessage = this[ApiLogs.errorMessage],
        errorStackTrace = this[ApiLogs.errorStackTrace],
        requestBody = this[ApiLogs.requestBody],
        responseBody = this[ApiLogs.responseBody],
        createdAt = this[ApiLogs.createdAt].toString()
    )

    private fun ResultRow.toApiLogSummary(): ApiLogSummary = ApiLogSummary(
        id = this[ApiLogs.id].value,
        endpoint = this[ApiLogs.endpoint],
        method = this[ApiLogs.method],
        statusCode = this[ApiLogs.statusCode],
        durationMs = this[ApiLogs.durationMs],
        errorMessage = this[ApiLogs.errorMessage],
        createdAt = this[ApiLogs.createdAt].toString()
    )

    private fun ResultRow.toSyncStatus(): SyncStatus = SyncStatus(
        id = this[SyncStatuses.id].value,
        tenantId = this[SyncStatuses.tenantId],
        storeId = this[SyncStatuses.storeId],
        marketplace = this[SyncStatuses.marketplace],
        storeName = this[SyncStatuses.storeName],
        lastSyncAt = this[SyncStatuses.lastSyncAt]?.toString(),
        lastSuccessAt = this[SyncStatuses.lastSuccessAt]?.toString(),
        lastErrorAt = this[SyncStatuses.lastErrorAt]?.toString(),
        errorCount = this[SyncStatuses.errorCount],
        lastErrorMessage = this[SyncStatuses.lastErrorMessage],
        syncState = this[SyncStatuses.syncState],
        ordersImported = this[SyncStatuses.ordersImported],
        ordersSynced = this[SyncStatuses.ordersSynced],
        createdAt = this[SyncStatuses.createdAt].toString(),
        updatedAt = this[SyncStatuses.updatedAt].toString()
    )
}
