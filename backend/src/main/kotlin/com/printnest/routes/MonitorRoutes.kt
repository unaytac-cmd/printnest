package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.MonitorService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.monitorRoutes() {
    val monitorService: MonitorService = GlobalContext.get().get()

    route("/monitor") {

        // =====================================================
        // SYSTEM HEALTH
        // =====================================================

        /**
         * GET /api/v1/monitor/health
         * Returns overall system health status
         */
        get("/health") {
            val health = monitorService.getSystemHealth()

            val statusCode = when (health.status) {
                "healthy" -> HttpStatusCode.OK
                "degraded" -> HttpStatusCode.OK // Still operational
                else -> HttpStatusCode.ServiceUnavailable
            }

            call.respond(statusCode, health)
        }

        // =====================================================
        // ORDER DEBUG
        // =====================================================

        /**
         * GET /api/v1/monitor/order/{orderId}
         * Returns detailed debug information for an order
         */
        get("/order/{orderId}") {
            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()

            monitorService.getOrderDebugInfo(orderId, tenantId)
                .onSuccess { debugInfo ->
                    call.respond(debugInfo)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }

        /**
         * GET /api/v1/monitor/order/search
         * Search for an order by internal order ID or external order ID
         */
        get("/order/search") {
            val query = call.request.queryParameters["q"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query parameter 'q' required"))

            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            // Try to find by different identifiers
            val orderId = query.toLongOrNull()

            if (orderId != null) {
                monitorService.getOrderDebugInfo(orderId, tenantId)
                    .onSuccess { debugInfo ->
                        call.respond(debugInfo)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                    }
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Please provide a valid order ID"))
            }
        }

        // =====================================================
        // API LOGS
        // =====================================================

        /**
         * GET /api/v1/monitor/logs
         * Returns paginated API logs with optional filters
         */
        get("/logs") {
            val filters = ApiLogFilters(
                tenantId = call.request.queryParameters["tenantId"]?.toLongOrNull(),
                userId = call.request.queryParameters["userId"]?.toLongOrNull(),
                endpoint = call.request.queryParameters["endpoint"],
                method = call.request.queryParameters["method"],
                statusCode = call.request.queryParameters["statusCode"]?.toIntOrNull(),
                minDuration = call.request.queryParameters["minDuration"]?.toLongOrNull(),
                hasError = call.request.queryParameters["hasError"]?.toBooleanStrictOrNull(),
                startDate = call.request.queryParameters["startDate"],
                endDate = call.request.queryParameters["endDate"],
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            )

            val response = monitorService.getApiLogs(filters)
            call.respond(response)
        }

        /**
         * GET /api/v1/monitor/logs/{id}
         * Returns detailed information for a specific API log
         */
        get("/logs/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid log ID"))

            val log = monitorService.getApiLogById(id)
            if (log != null) {
                call.respond(log)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Log not found"))
            }
        }

        /**
         * GET /api/v1/monitor/logs/stats
         * Returns API log statistics
         */
        get("/logs/stats") {
            val tenantId = call.request.queryParameters["tenantId"]?.toLongOrNull()
            val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24

            val stats = monitorService.getApiLogStats(tenantId, hours)
            call.respond(stats)
        }

        /**
         * GET /api/v1/monitor/logs/errors
         * Returns recent error logs
         */
        get("/logs/errors") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val errors = monitorService.getRecentErrors(limit)
            call.respond(mapOf("errors" to errors, "count" to errors.size))
        }

        /**
         * GET /api/v1/monitor/logs/slow
         * Returns slow request logs
         */
        get("/logs/slow") {
            val minDuration = call.request.queryParameters["minDuration"]?.toLongOrNull() ?: 5000
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val slowRequests = monitorService.getSlowRequests(minDuration, limit)
            call.respond(mapOf("slowRequests" to slowRequests, "count" to slowRequests.size))
        }

        // =====================================================
        // SYNC STATUS
        // =====================================================

        /**
         * GET /api/v1/monitor/sync-status
         * Returns marketplace sync status for a tenant
         */
        get("/sync-status") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val syncStatus = monitorService.getSyncStatus(tenantId)
            call.respond(syncStatus)
        }

        /**
         * GET /api/v1/monitor/sync-status/{storeId}
         * Returns sync status for a specific store
         */
        get("/sync-status/{storeId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val storeId = call.parameters["storeId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))

            val syncStatus = monitorService.getSyncStatus(tenantId)
            val storeStatus = syncStatus.stores.find { it.storeId == storeId }

            if (storeStatus != null) {
                call.respond(storeStatus)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Sync status not found for store"))
            }
        }

        // =====================================================
        // DATABASE STATS
        // =====================================================

        /**
         * GET /api/v1/monitor/database
         * Returns database statistics
         */
        get("/database") {
            val stats = monitorService.getDatabaseStats()
            call.respond(stats)
        }

        // =====================================================
        // QUEUE STATUS
        // =====================================================

        /**
         * GET /api/v1/monitor/queues
         * Returns background job queue status
         */
        get("/queues") {
            val queueStatus = monitorService.getQueueStatus()
            call.respond(queueStatus)
        }

        // =====================================================
        // DASHBOARD
        // =====================================================

        /**
         * GET /api/v1/monitor/dashboard
         * Returns complete monitoring dashboard data
         */
        get("/dashboard") {
            val dashboard = monitorService.getMonitorDashboard()
            call.respond(dashboard)
        }

        // =====================================================
        // CLEANUP OPERATIONS
        // =====================================================

        /**
         * DELETE /api/v1/monitor/logs/cleanup
         * Deletes old API logs
         */
        delete("/logs/cleanup") {
            val olderThanDays = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
            val deleted = monitorService.cleanupOldLogs(olderThanDays)
            call.respond(mapOf(
                "message" to "Deleted $deleted old log entries",
                "deletedCount" to deleted,
                "olderThanDays" to olderThanDays
            ))
        }

        /**
         * DELETE /api/v1/monitor/queues/cleanup
         * Deletes old completed jobs
         */
        delete("/queues/cleanup") {
            val olderThanDays = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
            val deleted = monitorService.cleanupOldJobs(olderThanDays)
            call.respond(mapOf(
                "message" to "Deleted $deleted old job entries",
                "deletedCount" to deleted,
                "olderThanDays" to olderThanDays
            ))
        }
    }
}
