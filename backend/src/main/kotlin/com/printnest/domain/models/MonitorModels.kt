package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// SYSTEM HEALTH MODELS
// =====================================================

@Serializable
data class SystemHealth(
    val status: String, // "healthy", "degraded", "unhealthy"
    val timestamp: String,
    val services: ServiceHealthStatus,
    val uptime: Long, // in seconds
    val version: String
)

@Serializable
data class ServiceHealthStatus(
    val database: ServiceHealth,
    val redis: ServiceHealth,
    val s3: ServiceHealth,
    val shipstation: ServiceHealth,
    val easypost: ServiceHealth
)

@Serializable
data class ServiceHealth(
    val name: String,
    val status: String, // "up", "down", "degraded"
    val latencyMs: Long? = null,
    val message: String? = null,
    val lastChecked: String
)

// =====================================================
// ORDER DEBUG INFO
// =====================================================

@Serializable
data class OrderDebugInfo(
    val order: OrderDebugSummary,
    val products: List<OrderProductDebugInfo>,
    val mappings: List<MappingDebugInfo>,
    val history: List<OrderHistoryItem>,
    val payments: List<PaymentDebugInfo>,
    val shippingLabel: ShippingLabelDebugInfo?,
    val user: UserDebugInfo?,
    val tenant: TenantDebugInfo?,
    val store: StoreDebugInfo?,
    val logs: List<ApiLogSummary>
)

@Serializable
data class OrderDebugSummary(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val intOrderId: String?,
    val externalOrderId: String?,
    val orderType: Int,
    val orderStatus: Int,
    val orderStatusName: String,
    val orderMapStatus: Int,
    val orderMapStatusName: String,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingAmount: BigDecimal,
    val customerEmail: String?,
    val customerName: String?,
    val trackingNumber: String?,
    val createdAt: String,
    val updatedAt: String,
    val rawOrderInfo: String?,
    val rawOrderDetail: String?,
    val rawPriceDetail: String?
)

@Serializable
data class OrderProductDebugInfo(
    val id: Long,
    val orderId: Long,
    val productId: Long?,
    val variantId: Long?,
    val listingId: String?,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val unitPrice: BigDecimal,
    val designId: Long?,
    val mappingId: Long?,
    val status: Int,
    val rawProductDetail: String?,
    val rawModificationDetail: String?,
    val rawPriceBreakdown: String?
)

@Serializable
data class MappingDebugInfo(
    val type: String, // "value" or "listing"
    val id: Long,
    val listingId: String?,
    val valueId1: String?,
    val valueId2: String?,
    val variantId: Long?,
    val modificationId: Long?,
    val lightDesignId: Long?,
    val darkDesignId: Long?
)

@Serializable
data class PaymentDebugInfo(
    val id: Long,
    val paymentMethod: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val status: String,
    val stripeSessionId: String?,
    val stripePaymentIntent: String?,
    val createdAt: String,
    val completedAt: String?
)

@Serializable
data class ShippingLabelDebugInfo(
    val id: Long,
    val carrier: String?,
    val service: String?,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val labelUrl: String?,
    val rateId: String?,
    val shipmentId: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal?,
    val createdAt: String,
    val voidedAt: String?
)

@Serializable
data class UserDebugInfo(
    val id: Long,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    val status: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal,
    val parentUserId: Long?,
    val priceProfileId: Long?,
    val shippingProfileId: Long?,
    val createdAt: String
)

@Serializable
data class TenantDebugInfo(
    val id: Long,
    val subdomain: String,
    val name: String,
    val status: Int,
    val customDomain: String?,
    val createdAt: String
)

@Serializable
data class StoreDebugInfo(
    val id: Long,
    val storeName: String,
    val marketplaceName: String?,
    val isActive: Boolean,
    val lastSyncAt: String?,
    val syncError: String?
)

// =====================================================
// API LOG MODELS
// =====================================================

@Serializable
data class ApiLog(
    val id: Long,
    val tenantId: Long?,
    val userId: Long?,
    val endpoint: String,
    val method: String,
    val statusCode: Int,
    val durationMs: Long,
    val requestSize: Long?,
    val responseSize: Long?,
    val userAgent: String?,
    val ipAddress: String?,
    val errorMessage: String?,
    val errorStackTrace: String?,
    val requestBody: String?,
    val responseBody: String?,
    val createdAt: String
)

@Serializable
data class ApiLogSummary(
    val id: Long,
    val endpoint: String,
    val method: String,
    val statusCode: Int,
    val durationMs: Long,
    val errorMessage: String?,
    val createdAt: String
)

@Serializable
data class ApiLogFilters(
    val tenantId: Long? = null,
    val userId: Long? = null,
    val endpoint: String? = null,
    val method: String? = null,
    val statusCode: Int? = null,
    val minDuration: Long? = null,
    val hasError: Boolean? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val page: Int = 1,
    val limit: Int = 50
)

@Serializable
data class ApiLogListResponse(
    val logs: List<ApiLog>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class ApiLogStats(
    val totalRequests: Long,
    val errorRate: Double,
    val avgDurationMs: Double,
    val requestsByEndpoint: Map<String, Long>,
    val requestsByMethod: Map<String, Long>,
    val errorsByStatusCode: Map<Int, Long>,
    val slowestEndpoints: List<EndpointStats>
)

@Serializable
data class EndpointStats(
    val endpoint: String,
    val avgDurationMs: Double,
    val maxDurationMs: Long,
    val requestCount: Long,
    val errorCount: Long
)

// =====================================================
// SYNC STATUS MODELS
// =====================================================

@Serializable
data class SyncStatus(
    val id: Long,
    val tenantId: Long,
    val storeId: Long?,
    val marketplace: String,
    val storeName: String?,
    val lastSyncAt: String?,
    val lastSuccessAt: String?,
    val lastErrorAt: String?,
    val errorCount: Int,
    val lastErrorMessage: String?,
    val syncState: String, // "idle", "syncing", "error", "disabled"
    val ordersImported: Int,
    val ordersSynced: Int,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SyncStatusSummary(
    val tenantId: Long,
    val stores: List<SyncStatus>,
    val totalStores: Int,
    val activeStores: Int,
    val errorStores: Int,
    val lastGlobalSync: String?
)

// =====================================================
// DATABASE STATS
// =====================================================

@Serializable
data class DatabaseStats(
    val connectionPoolSize: Int,
    val activeConnections: Int,
    val idleConnections: Int,
    val pendingConnections: Int,
    val maxConnections: Int,
    val tableCounts: Map<String, Long>,
    val databaseSize: String?,
    val postgresVersion: String?
)

// =====================================================
// QUEUE STATUS
// =====================================================

@Serializable
data class QueueStatus(
    val jobStatistics: JobStatistics,
    val runningJobs: List<RunningJobInfo>,
    val recentFailedJobs: List<FailedJobInfo>,
    val jobsByType: Map<String, JobTypeStats>
)

@Serializable
data class RunningJobInfo(
    val id: Long,
    val jobType: String,
    val tenantId: Long?,
    val startedAt: String?,
    val runningDurationMs: Long
)

@Serializable
data class FailedJobInfo(
    val id: Long,
    val jobType: String,
    val tenantId: Long?,
    val errorMessage: String?,
    val failedAt: String?
)

@Serializable
data class JobTypeStats(
    val pending: Int,
    val running: Int,
    val completed: Int,
    val failed: Int,
    val avgDurationMs: Long?
)

// =====================================================
// LOG SEARCH
// =====================================================

@Serializable
data class LogSearchRequest(
    val orderId: Long? = null,
    val query: String? = null,
    val logLevel: String? = null, // "ERROR", "WARN", "INFO", "DEBUG"
    val startDate: String? = null,
    val endDate: String? = null,
    val limit: Int = 100
)

@Serializable
data class LogSearchResult(
    val entries: List<LogEntry>,
    val totalFound: Int,
    val searchQuery: String?
)

@Serializable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val logger: String?,
    val orderId: Long?,
    val tenantId: Long?,
    val stackTrace: String?
)

// =====================================================
// MONITOR DASHBOARD
// =====================================================

@Serializable
data class MonitorDashboard(
    val systemHealth: SystemHealth,
    val queueStatus: QueueStatus,
    val recentErrors: List<ApiLogSummary>,
    val syncStatusSummary: List<SyncStatusSummary>,
    val databaseStats: DatabaseStats
)
