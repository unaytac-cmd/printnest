package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.*
import com.printnest.domain.tables.*
import com.printnest.integrations.aws.S3Service
import com.printnest.integrations.redis.RedisService
import com.printnest.integrations.shipstation.ShipStationClient
import com.printnest.integrations.easypost.EasyPostService
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.Duration

class MonitorService(
    private val apiLogRepository: ApiLogRepository,
    private val orderRepository: OrderRepository,
    private val jobRepository: JobRepository,
    private val redisService: RedisService,
    private val s3Service: S3Service
) : KoinComponent {

    private val logger = LoggerFactory.getLogger(MonitorService::class.java)
    private val json: Json by inject()

    private val startTime = Instant.now()
    private val version = "1.0.0"

    // =====================================================
    // SYSTEM HEALTH
    // =====================================================

    fun getSystemHealth(): SystemHealth {
        val services = ServiceHealthStatus(
            database = checkDatabaseHealth(),
            redis = checkRedisHealth(),
            s3 = checkS3Health(),
            shipstation = checkShipStationHealth(),
            easypost = checkEasyPostHealth()
        )

        val overallStatus = when {
            services.database.status == "down" -> "unhealthy"
            listOf(services.redis, services.s3).any { it.status == "down" } -> "degraded"
            listOf(services.shipstation, services.easypost).all { it.status == "down" } -> "degraded"
            else -> "healthy"
        }

        val uptime = Duration.between(startTime, Instant.now()).seconds

        return SystemHealth(
            status = overallStatus,
            timestamp = Instant.now().toString(),
            services = services,
            uptime = uptime,
            version = version
        )
    }

    private fun checkDatabaseHealth(): ServiceHealth {
        val startTime = System.currentTimeMillis()
        return try {
            transaction {
                exec("SELECT 1") { rs ->
                    rs.next()
                }
            }
            val latency = System.currentTimeMillis() - startTime
            ServiceHealth(
                name = "PostgreSQL",
                status = "up",
                latencyMs = latency,
                message = "Database connection successful",
                lastChecked = Instant.now().toString()
            )
        } catch (e: Exception) {
            logger.error("Database health check failed", e)
            ServiceHealth(
                name = "PostgreSQL",
                status = "down",
                latencyMs = null,
                message = "Connection failed: ${e.message}",
                lastChecked = Instant.now().toString()
            )
        }
    }

    private fun checkRedisHealth(): ServiceHealth {
        val startTime = System.currentTimeMillis()
        return try {
            val isHealthy = redisService.isHealthy()
            val latency = System.currentTimeMillis() - startTime
            ServiceHealth(
                name = "Redis",
                status = if (isHealthy) "up" else "down",
                latencyMs = latency,
                message = if (isHealthy) "Redis ping successful" else "Redis ping failed",
                lastChecked = Instant.now().toString()
            )
        } catch (e: Exception) {
            logger.error("Redis health check failed", e)
            ServiceHealth(
                name = "Redis",
                status = "down",
                latencyMs = null,
                message = "Connection failed: ${e.message}",
                lastChecked = Instant.now().toString()
            )
        }
    }

    private fun checkS3Health(): ServiceHealth {
        val startTime = System.currentTimeMillis()
        return try {
            // Just verify the service is initialized
            // A full health check would require listing buckets or similar
            val latency = System.currentTimeMillis() - startTime
            ServiceHealth(
                name = "AWS S3",
                status = "up",
                latencyMs = latency,
                message = "S3 service initialized",
                lastChecked = Instant.now().toString()
            )
        } catch (e: Exception) {
            logger.error("S3 health check failed", e)
            ServiceHealth(
                name = "AWS S3",
                status = "down",
                latencyMs = null,
                message = "S3 check failed: ${e.message}",
                lastChecked = Instant.now().toString()
            )
        }
    }

    private fun checkShipStationHealth(): ServiceHealth {
        // ShipStation health is checked via API key validity
        // For now, we return a simplified status
        return ServiceHealth(
            name = "ShipStation",
            status = "up",
            latencyMs = null,
            message = "ShipStation integration active",
            lastChecked = Instant.now().toString()
        )
    }

    private fun checkEasyPostHealth(): ServiceHealth {
        return ServiceHealth(
            name = "EasyPost",
            status = "up",
            latencyMs = null,
            message = "EasyPost integration active",
            lastChecked = Instant.now().toString()
        )
    }

    // =====================================================
    // ORDER DEBUG INFO
    // =====================================================

    fun getOrderDebugInfo(orderId: Long, tenantId: Long? = null): Result<OrderDebugInfo> {
        return try {
            val order = if (tenantId != null) {
                orderRepository.findByIdWithProducts(orderId, tenantId)
            } else {
                orderRepository.findById(orderId)?.let { o ->
                    orderRepository.findByIdWithProducts(orderId, o.tenantId)
                }
            }

            if (order == null) {
                return Result.failure(Exception("Order not found: $orderId"))
            }

            val orderDebugSummary = OrderDebugSummary(
                id = order.id,
                tenantId = order.tenantId,
                userId = order.userId,
                intOrderId = order.intOrderId,
                externalOrderId = order.externalOrderId,
                orderType = order.orderType,
                orderStatus = order.orderStatus,
                orderStatusName = OrderStatus.fromCode(order.orderStatus).name,
                orderMapStatus = order.orderMapStatus,
                orderMapStatusName = OrderMapStatus.fromCode(order.orderMapStatus).name,
                totalAmount = order.totalAmount,
                shippingAmount = order.shippingAmount,
                customerEmail = order.customerEmail,
                customerName = order.customerName,
                trackingNumber = order.trackingNumber,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt,
                rawOrderInfo = order.orderInfo?.let { json.encodeToString(OrderInfoFull.serializer(), it) },
                rawOrderDetail = order.orderDetail?.let { json.encodeToString(OrderDetail.serializer(), it) },
                rawPriceDetail = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(PriceDetailItem.serializer()), order.priceDetail)
            )

            val products = order.products.map { product ->
                OrderProductDebugInfo(
                    id = product.id,
                    orderId = product.orderId,
                    productId = product.productId,
                    variantId = product.variantId,
                    listingId = product.listingId,
                    quantity = product.quantity,
                    unitPrice = product.unitPrice,
                    designId = product.designId,
                    mappingId = product.mappingId,
                    status = product.status,
                    rawProductDetail = product.productDetail?.let { json.encodeToString(ProductDetailFull.serializer(), it) },
                    rawModificationDetail = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ModificationDetailItem.serializer()), product.modificationDetail),
                    rawPriceBreakdown = product.priceBreakdown?.let { json.encodeToString(PriceBreakdownItem.serializer(), it) }
                )
            }

            val mappings = getMappingsForOrder(order)

            val payments = getPaymentsForOrder(orderId, order.tenantId)

            val shippingLabel = getShippingLabelForOrder(orderId, order.tenantId)

            val user = getUserDebugInfo(order.userId, order.tenantId)

            val tenant = getTenantDebugInfo(order.tenantId)

            val store = order.storeId?.let { getStoreDebugInfo(it, order.tenantId) }

            val logs = apiLogRepository.findByOrderId(orderId)

            Result.success(
                OrderDebugInfo(
                    order = orderDebugSummary,
                    products = products,
                    mappings = mappings,
                    history = order.history,
                    payments = payments,
                    shippingLabel = shippingLabel,
                    user = user,
                    tenant = tenant,
                    store = store,
                    logs = logs
                )
            )
        } catch (e: Exception) {
            logger.error("Error getting order debug info for order $orderId", e)
            Result.failure(e)
        }
    }

    private fun getMappingsForOrder(order: OrderFull): List<MappingDebugInfo> = transaction {
        val mappings = mutableListOf<MappingDebugInfo>()

        order.products.forEach { product ->
            product.listingId?.let { listingId ->
                // Find map_listings
                MapListings.selectAll()
                    .where { (MapListings.tenantId eq order.tenantId) and (MapListings.listingId eq listingId) }
                    .forEach { row ->
                        mappings.add(
                            MappingDebugInfo(
                                type = "listing",
                                id = row[MapListings.id].value,
                                listingId = row[MapListings.listingId],
                                valueId1 = null,
                                valueId2 = null,
                                variantId = null,
                                modificationId = row[MapListings.modificationId]?.value,
                                lightDesignId = row[MapListings.lightDesignId]?.value,
                                darkDesignId = row[MapListings.darkDesignId]?.value
                            )
                        )
                    }
            }

            // Find map_values for variant
            product.variantId?.let { variantId ->
                MapValues.selectAll()
                    .where { (MapValues.tenantId eq order.tenantId) and (MapValues.variantId eq variantId) }
                    .forEach { row ->
                        mappings.add(
                            MappingDebugInfo(
                                type = "value",
                                id = row[MapValues.id].value,
                                listingId = null,
                                valueId1 = row[MapValues.valueId1],
                                valueId2 = row[MapValues.valueId2],
                                variantId = row[MapValues.variantId]?.value,
                                modificationId = null,
                                lightDesignId = null,
                                darkDesignId = null
                            )
                        )
                    }
            }
        }

        mappings
    }

    private fun getPaymentsForOrder(orderId: Long, tenantId: Long): List<PaymentDebugInfo> = transaction {
        Payments.selectAll()
            .where { (Payments.orderId eq orderId) and (Payments.tenantId eq tenantId) }
            .map { row ->
                PaymentDebugInfo(
                    id = row[Payments.id].value,
                    paymentMethod = row[Payments.paymentMethod],
                    amount = row[Payments.amount],
                    status = row[Payments.status],
                    stripeSessionId = row[Payments.stripeSessionId],
                    stripePaymentIntent = row[Payments.stripePaymentIntent],
                    createdAt = row[Payments.createdAt].toString(),
                    completedAt = row[Payments.completedAt]?.toString()
                )
            }
    }

    private fun getShippingLabelForOrder(orderId: Long, tenantId: Long): ShippingLabelDebugInfo? = transaction {
        ShippingLabels.selectAll()
            .where { (ShippingLabels.orderId eq orderId) and (ShippingLabels.tenantId eq tenantId) }
            .singleOrNull()
            ?.let { row ->
                ShippingLabelDebugInfo(
                    id = row[ShippingLabels.id].value,
                    carrier = row[ShippingLabels.carrier],
                    service = row[ShippingLabels.service],
                    trackingNumber = row[ShippingLabels.trackingNumber],
                    trackingUrl = row[ShippingLabels.trackingUrl],
                    labelUrl = row[ShippingLabels.labelUrl],
                    rateId = row[ShippingLabels.rateId],
                    shipmentId = row[ShippingLabels.shipmentId],
                    cost = row[ShippingLabels.cost],
                    createdAt = row[ShippingLabels.createdAt].toString(),
                    voidedAt = row[ShippingLabels.voidedAt]?.toString()
                )
            }
    }

    private fun getUserDebugInfo(userId: Long, tenantId: Long): UserDebugInfo? = transaction {
        Users.selectAll()
            .where { (Users.id eq userId) and (Users.tenantId eq tenantId) }
            .singleOrNull()
            ?.let { row ->
                UserDebugInfo(
                    id = row[Users.id].value,
                    email = row[Users.email],
                    firstName = row[Users.firstName],
                    lastName = row[Users.lastName],
                    role = row[Users.role],
                    status = row[Users.status],
                    balance = row[Users.totalCredit],
                    parentUserId = row[Users.parentUserId]?.value,
                    priceProfileId = row[Users.priceProfileId],
                    shippingProfileId = row[Users.shippingProfileId],
                    createdAt = row[Users.createdAt].toString()
                )
            }
    }

    private fun getTenantDebugInfo(tenantId: Long): TenantDebugInfo? = transaction {
        Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.let { row ->
                TenantDebugInfo(
                    id = row[Tenants.id].value,
                    subdomain = row[Tenants.subdomain],
                    name = row[Tenants.name],
                    status = row[Tenants.status],
                    customDomain = row[Tenants.customDomain],
                    createdAt = row[Tenants.createdAt].toString()
                )
            }
    }

    private fun getStoreDebugInfo(storeId: Long, tenantId: Long): StoreDebugInfo? = transaction {
        Stores.selectAll()
            .where { (Stores.id eq storeId) and (Stores.tenantId eq tenantId) }
            .singleOrNull()
            ?.let { row ->
                StoreDebugInfo(
                    id = row[Stores.id].value,
                    storeName = row[Stores.storeName],
                    marketplaceName = null, // Would need to join with Marketplaces
                    isActive = row[Stores.status] == 1,
                    lastSyncAt = row[Stores.lastSyncAt]?.toString(),
                    syncError = row[Stores.syncError]
                )
            }
    }

    // =====================================================
    // API LOGS
    // =====================================================

    fun getApiLogs(filters: ApiLogFilters): ApiLogListResponse {
        val (logs, total) = apiLogRepository.findAll(filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return ApiLogListResponse(
            logs = logs,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getApiLogById(id: Long): ApiLog? {
        return apiLogRepository.findById(id)
    }

    fun getApiLogStats(tenantId: Long? = null, hours: Int = 24): ApiLogStats {
        return apiLogRepository.getStats(tenantId, hours)
    }

    fun getRecentErrors(limit: Int = 50): List<ApiLogSummary> {
        return apiLogRepository.findRecentErrors(limit)
    }

    fun getSlowRequests(minDurationMs: Long = 5000, limit: Int = 50): List<ApiLogSummary> {
        return apiLogRepository.findSlowRequests(minDurationMs, limit)
    }

    // =====================================================
    // SYNC STATUS
    // =====================================================

    fun getSyncStatus(tenantId: Long): SyncStatusSummary {
        val statuses = apiLogRepository.findAllSyncStatuses(tenantId)

        val activeStores = statuses.count { it.syncState == "idle" || it.syncState == "syncing" }
        val errorStores = statuses.count { it.syncState == "error" }
        val lastGlobalSync = statuses.maxByOrNull { it.lastSyncAt ?: "" }?.lastSyncAt

        return SyncStatusSummary(
            tenantId = tenantId,
            stores = statuses,
            totalStores = statuses.size,
            activeStores = activeStores,
            errorStores = errorStores,
            lastGlobalSync = lastGlobalSync
        )
    }

    fun updateSyncStatus(
        tenantId: Long,
        storeId: Long?,
        marketplace: String,
        storeName: String?,
        syncState: String,
        errorMessage: String? = null,
        ordersImported: Int = 0,
        ordersSynced: Int = 0
    ) {
        apiLogRepository.upsertSyncStatus(
            tenantId = tenantId,
            storeId = storeId,
            marketplace = marketplace,
            storeName = storeName,
            syncState = syncState,
            errorMessage = errorMessage,
            ordersImported = ordersImported,
            ordersSynced = ordersSynced
        )
    }

    // =====================================================
    // DATABASE STATS
    // =====================================================

    fun getDatabaseStats(): DatabaseStats = transaction {
        val tableCounts = mutableMapOf<String, Long>()

        // Get counts for major tables
        tableCounts["tenants"] = Tenants.selectAll().count()
        tableCounts["users"] = Users.selectAll().count()
        tableCounts["orders"] = Orders.selectAll().count()
        tableCounts["order_products"] = OrderProducts.selectAll().count()
        tableCounts["products"] = Products.selectAll().count()
        tableCounts["variants"] = Variants.selectAll().count()
        tableCounts["designs"] = Designs.selectAll().count()
        tableCounts["stores"] = Stores.selectAll().count()
        tableCounts["gangsheets"] = Gangsheets.selectAll().count()
        tableCounts["scheduled_jobs"] = ScheduledJobs.selectAll().count()
        tableCounts["api_logs"] = ApiLogs.selectAll().count()

        // Get PostgreSQL version
        val version = try {
            exec("SELECT version()") { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        } catch (e: Exception) {
            null
        }

        // Get database size (PostgreSQL specific)
        val dbSize = try {
            exec("SELECT pg_size_pretty(pg_database_size(current_database()))") { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        } catch (e: Exception) {
            null
        }

        DatabaseStats(
            connectionPoolSize = 50, // From HikariCP config
            activeConnections = 0, // Would need HikariCP metrics
            idleConnections = 0,
            pendingConnections = 0,
            maxConnections = 50,
            tableCounts = tableCounts,
            databaseSize = dbSize,
            postgresVersion = version
        )
    }

    // =====================================================
    // QUEUE STATUS
    // =====================================================

    fun getQueueStatus(): QueueStatus {
        val statistics = jobRepository.getStatistics()

        val runningJobs = jobRepository.findRunningJobs().map { job ->
            val startedAt = job.startedAt?.let { Instant.parse(it) }
            val runningDuration = startedAt?.let {
                Duration.between(it, Instant.now()).toMillis()
            } ?: 0

            RunningJobInfo(
                id = job.id,
                jobType = job.jobType,
                tenantId = job.tenantId,
                startedAt = job.startedAt,
                runningDurationMs = runningDuration
            )
        }

        val recentFailedJobs = transaction {
            ScheduledJobs.selectAll()
                .where { ScheduledJobs.jobStatus eq JobStatus.FAILED.code }
                .orderBy(ScheduledJobs.completedAt, SortOrder.DESC)
                .limit(10)
                .map { row ->
                    FailedJobInfo(
                        id = row[ScheduledJobs.id].value,
                        jobType = row[ScheduledJobs.jobType],
                        tenantId = row[ScheduledJobs.tenantId]?.value,
                        errorMessage = row[ScheduledJobs.errorMessage],
                        failedAt = row[ScheduledJobs.completedAt]?.toString()
                    )
                }
        }

        val jobsByType = statistics.jobsByType.mapValues { (type, _) ->
            val stats = transaction {
                val pending = ScheduledJobs.selectAll()
                    .where { (ScheduledJobs.jobType eq type) and (ScheduledJobs.jobStatus eq JobStatus.PENDING.code) }
                    .count().toInt()
                val running = ScheduledJobs.selectAll()
                    .where { (ScheduledJobs.jobType eq type) and (ScheduledJobs.jobStatus eq JobStatus.RUNNING.code) }
                    .count().toInt()
                val completed = ScheduledJobs.selectAll()
                    .where { (ScheduledJobs.jobType eq type) and (ScheduledJobs.jobStatus eq JobStatus.COMPLETED.code) }
                    .count().toInt()
                val failed = ScheduledJobs.selectAll()
                    .where { (ScheduledJobs.jobType eq type) and (ScheduledJobs.jobStatus eq JobStatus.FAILED.code) }
                    .count().toInt()

                JobTypeStats(
                    pending = pending,
                    running = running,
                    completed = completed,
                    failed = failed,
                    avgDurationMs = null // Would need more complex query
                )
            }
            stats
        }

        return QueueStatus(
            jobStatistics = statistics,
            runningJobs = runningJobs,
            recentFailedJobs = recentFailedJobs,
            jobsByType = jobsByType
        )
    }

    // =====================================================
    // DASHBOARD
    // =====================================================

    fun getMonitorDashboard(): MonitorDashboard {
        return MonitorDashboard(
            systemHealth = getSystemHealth(),
            queueStatus = getQueueStatus(),
            recentErrors = getRecentErrors(20),
            syncStatusSummary = getAllSyncStatusSummaries(),
            databaseStats = getDatabaseStats()
        )
    }

    private fun getAllSyncStatusSummaries(): List<SyncStatusSummary> = transaction {
        // Get all distinct tenant IDs from sync_statuses
        SyncStatuses.select(SyncStatuses.tenantId)
            .groupBy(SyncStatuses.tenantId)
            .map { it[SyncStatuses.tenantId] }
            .map { tenantId -> getSyncStatus(tenantId) }
    }

    // =====================================================
    // CLEANUP
    // =====================================================

    fun cleanupOldLogs(olderThanDays: Int = 30): Int {
        return apiLogRepository.deleteOldLogs(olderThanDays)
    }

    fun cleanupOldJobs(olderThanDays: Int = 30): Int {
        return jobRepository.deleteOldJobs(olderThanDays)
    }
}
