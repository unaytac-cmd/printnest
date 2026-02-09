package com.printnest.routes

import com.printnest.integrations.walmart.WalmartService
import com.printnest.integrations.walmart.WalmartSyncResult
import com.printnest.integrations.walmart.BulkAcknowledgeResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext

fun Route.walmartRoutes() {
    val walmartService: WalmartService = GlobalContext.get().get()

    route("/walmart") {

        /**
         * POST /api/v1/walmart/connect
         * Connect a new Walmart store with credentials
         */
        post("/connect") {
            val request = call.receive<ConnectWalmartRequest>()

            // Get tenant ID from JWT claims (will be implemented with auth)
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            // Validate credentials by testing connection
            val result = walmartService.testConnection(request.clientId, request.clientSecret)

            result.fold(
                onSuccess = { isValid ->
                    if (isValid) {
                        // TODO: Save store credentials to database (encrypted)
                        // For now, return success response
                        call.respond(HttpStatusCode.OK, ConnectWalmartResponse(
                            success = true,
                            message = "Walmart store connected successfully",
                            storeId = null // Will be set after DB insert
                        ))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf(
                            "error" to "Invalid credentials",
                            "message" to "Could not authenticate with Walmart API"
                        ))
                    }
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Connection failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/walmart/disconnect/{storeId}
         * Disconnect a Walmart store
         */
        post("/disconnect/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            // TODO: Mark store as inactive in database
            // TODO: Invalidate cached tokens

            call.respond(HttpStatusCode.OK, mapOf(
                "success" to true,
                "message" to "Walmart store disconnected"
            ))
        }

        /**
         * POST /api/v1/walmart/sync/{storeId}
         * Sync orders from Walmart store
         */
        post("/sync/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT
            val request = call.receive<SyncWalmartRequest>()

            val result = walmartService.syncOrders(
                tenantId = tenantId,
                storeId = storeId,
                clientId = request.clientId,
                clientSecret = request.clientSecret,
                status = request.status ?: "Created,Acknowledged",
                createdStartDate = request.createdStartDate
            )

            result.fold(
                onSuccess = { syncResult ->
                    call.respond(HttpStatusCode.OK, SyncWalmartResponse(
                        success = true,
                        message = "Sync completed successfully",
                        storeId = syncResult.storeId,
                        totalFetched = syncResult.totalFetched,
                        inserted = syncResult.inserted,
                        skipped = syncResult.skipped,
                        errors = syncResult.errors.take(10), // Limit error messages
                        syncedAt = syncResult.syncedAt
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Sync failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/walmart/acknowledge/{orderId}
         * Acknowledge a Walmart order
         */
        post("/acknowledge/{orderId}") {
            val orderId = call.parameters["orderId"]?.toLongOrNull()
            if (orderId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))
                return@post
            }

            val request = call.receive<AcknowledgeWalmartRequest>()

            val result = walmartService.acknowledgeOrders(
                storeId = request.storeId,
                clientId = request.clientId,
                clientSecret = request.clientSecret,
                purchaseOrderIds = listOf(request.purchaseOrderId)
            )

            result.fold(
                onSuccess = { ackResult ->
                    if (ackResult.acknowledged > 0) {
                        call.respond(HttpStatusCode.OK, mapOf(
                            "success" to true,
                            "message" to "Order acknowledged successfully"
                        ))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf(
                            "success" to false,
                            "message" to "Failed to acknowledge order",
                            "errors" to ackResult.errors
                        ))
                    }
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Acknowledge failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/walmart/acknowledge-bulk
         * Acknowledge multiple Walmart orders
         */
        post("/acknowledge-bulk") {
            val request = call.receive<BulkAcknowledgeWalmartRequest>()

            val result = walmartService.acknowledgeOrders(
                storeId = request.storeId,
                clientId = request.clientId,
                clientSecret = request.clientSecret,
                purchaseOrderIds = request.purchaseOrderIds
            )

            result.fold(
                onSuccess = { ackResult ->
                    call.respond(HttpStatusCode.OK, BulkAcknowledgeWalmartResponse(
                        success = ackResult.failed == 0,
                        total = ackResult.total,
                        acknowledged = ackResult.acknowledged,
                        failed = ackResult.failed,
                        errors = ackResult.errors
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Bulk acknowledge failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/walmart/tracking/{orderId}
         * Update tracking information for a Walmart order
         */
        post("/tracking/{orderId}") {
            val orderId = call.parameters["orderId"]?.toLongOrNull()
            if (orderId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))
                return@post
            }

            val request = call.receive<WalmartUpdateTrackingRequest>()

            val result = walmartService.updateTracking(
                storeId = request.storeId,
                clientId = request.clientId,
                clientSecret = request.clientSecret,
                purchaseOrderId = request.purchaseOrderId,
                lineNumber = request.lineNumber,
                orderId = orderId,
                carrier = request.carrier,
                trackingNumber = request.trackingNumber,
                trackingUrl = request.trackingUrl
            )

            result.fold(
                onSuccess = {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Tracking updated successfully"
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to update tracking",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/walmart/stores
         * Get list of connected Walmart stores for the tenant
         */
        get("/stores") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            // TODO: Fetch stores from database
            // For now, return empty list
            call.respond(HttpStatusCode.OK, WalmartStoresResponse(
                stores = emptyList()
            ))
        }

        /**
         * GET /api/v1/walmart/stores/{storeId}
         * Get details of a specific Walmart store
         */
        get("/stores/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@get
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            // TODO: Fetch store from database
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Store not found"))
        }

        /**
         * POST /api/v1/walmart/test-connection
         * Test Walmart API connection with provided credentials
         */
        post("/test-connection") {
            val request = call.receive<TestConnectionRequest>()

            val result = walmartService.testConnection(request.clientId, request.clientSecret)

            result.fold(
                onSuccess = { isValid ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to isValid,
                        "message" to if (isValid) "Connection successful" else "Connection failed"
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Connection failed")
                    ))
                }
            )
        }

        /**
         * POST /api/v1/walmart/sync-completed/{storeId}
         * Sync completed/delivered orders and update their status
         */
        post("/sync-completed/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT
            val request = call.receive<SyncWalmartRequest>()

            val result = walmartService.syncCompletedOrders(
                tenantId = tenantId,
                storeId = storeId,
                clientId = request.clientId,
                clientSecret = request.clientSecret
            )

            result.fold(
                onSuccess = { updatedCount ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Updated $updatedCount orders to completed",
                        "updatedCount" to updatedCount
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Sync completed orders failed",
                        "message" to error.message
                    ))
                }
            )
        }
    }
}

// =====================================================
// REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class ConnectWalmartRequest(
    val clientId: String,
    val clientSecret: String,
    val storeName: String? = null
)

@Serializable
data class ConnectWalmartResponse(
    val success: Boolean,
    val message: String,
    val storeId: Long? = null
)

@Serializable
data class SyncWalmartRequest(
    val clientId: String,
    val clientSecret: String,
    val status: String? = null,
    val createdStartDate: String? = null
)

@Serializable
data class SyncWalmartResponse(
    val success: Boolean,
    val message: String,
    val storeId: Long,
    val totalFetched: Int,
    val inserted: Int,
    val skipped: Int,
    val errors: List<String>,
    val syncedAt: String
)

@Serializable
data class AcknowledgeWalmartRequest(
    val storeId: Long,
    val clientId: String,
    val clientSecret: String,
    val purchaseOrderId: String
)

@Serializable
data class BulkAcknowledgeWalmartRequest(
    val storeId: Long,
    val clientId: String,
    val clientSecret: String,
    val purchaseOrderIds: List<String>
)

@Serializable
data class BulkAcknowledgeWalmartResponse(
    val success: Boolean,
    val total: Int,
    val acknowledged: Int,
    val failed: Int,
    val errors: List<String>
)

@Serializable
data class WalmartUpdateTrackingRequest(
    @SerialName("store_id")
    val storeId: Long,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("purchase_order_id")
    val purchaseOrderId: String,
    @SerialName("line_number")
    val lineNumber: String,
    val carrier: String,
    @SerialName("tracking_number")
    val trackingNumber: String,
    @SerialName("tracking_url")
    val trackingUrl: String? = null
)

@Serializable
data class TestConnectionRequest(
    val clientId: String,
    val clientSecret: String
)

@Serializable
data class WalmartStoresResponse(
    val stores: List<WalmartStoreInfo>
)

@Serializable
data class WalmartStoreInfo(
    val id: Long,
    val storeName: String,
    val clientId: String,
    val isActive: Boolean,
    val lastSyncAt: String? = null
)
