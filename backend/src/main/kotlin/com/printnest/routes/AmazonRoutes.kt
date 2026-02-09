package com.printnest.routes

import com.printnest.domain.models.ErrorResponse
import com.printnest.integrations.amazon.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AmazonRoutes")

fun Route.amazonRoutes() {
    val amazonAuthService: AmazonAuthService = GlobalContext.get().get()
    val amazonService: AmazonService = GlobalContext.get().get()

    route("/amazon") {

        /**
         * GET /api/v1/amazon/auth/{storeId}
         * Generate Amazon OAuth authorization URL
         *
         * Response: { authUrl: string, state: string }
         */
        get("/auth/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid store ID"))
                return@get
            }

            // Get tenant ID from JWT claims (will be implemented with auth)
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            try {
                val customRedirectUri = call.request.queryParameters["redirect_uri"]
                val authUrlResponse = amazonAuthService.generateAuthUrl(storeId, customRedirectUri)

                logger.info("Generated Amazon auth URL for tenant $tenantId, store $storeId")
                call.respond(HttpStatusCode.OK, authUrlResponse)
            } catch (e: Exception) {
                logger.error("Failed to generate Amazon auth URL", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to generate auth URL: ${e.message}"))
            }
        }

        /**
         * GET /api/v1/amazon/callback
         * Handle Amazon OAuth callback
         *
         * Query params:
         * - spapi_oauth_code: Authorization code from Amazon
         * - state: Store ID passed during auth
         * - selling_partner_id: Amazon seller ID (optional)
         *
         * Redirects to frontend after processing
         */
        get("/callback") {
            val code = call.request.queryParameters["spapi_oauth_code"]
            val state = call.request.queryParameters["state"]
            val sellingPartnerId = call.request.queryParameters["selling_partner_id"]

            if (code.isNullOrBlank() || state.isNullOrBlank()) {
                logger.error("Amazon callback missing required parameters: code=$code, state=$state")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing authorization code or state"))
                return@get
            }

            val storeId = amazonAuthService.parseStateToStoreId(state)
            if (storeId == null) {
                logger.error("Invalid state parameter: $state")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid state parameter"))
                return@get
            }

            // Get tenant ID - in callback, we need to derive it from the store
            // For now, using a placeholder - will be properly implemented with auth
            val tenantId = 1L // TODO: Derive from store ID

            try {
                // Exchange code for tokens
                val tokenResult = amazonAuthService.exchangeCodeForToken(code)

                tokenResult.fold(
                    onSuccess = { tokenResponse ->
                        // Save credentials
                        amazonService.saveStoreCredentials(
                            tenantId = tenantId,
                            storeId = storeId,
                            sellerId = sellingPartnerId ?: "",
                            marketplaceId = AmazonConstants.US_MARKETPLACE_ID,
                            refreshToken = tokenResponse.refreshToken ?: "",
                            storeName = null
                        )

                        logger.info("Amazon OAuth successful for store $storeId")

                        // Redirect to frontend settings page
                        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
                        call.respondRedirect("$frontendUrl/settings/integrations?amazon=connected")
                    },
                    onFailure = { error ->
                        logger.error("Amazon OAuth failed for store $storeId", error)
                        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
                        call.respondRedirect("$frontendUrl/settings/integrations?amazon=error&message=${error.message}")
                    }
                )
            } catch (e: Exception) {
                logger.error("Exception during Amazon callback", e)
                val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
                call.respondRedirect("$frontendUrl/settings/integrations?amazon=error&message=${e.message}")
            }
        }

        /**
         * POST /api/v1/amazon/callback
         * Alternative callback handler for POST requests
         */
        post("/callback") {
            val request = try {
                call.receive<AmazonCallbackRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                return@post
            }

            val storeId = amazonAuthService.parseStateToStoreId(request.state)
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid state parameter"))
                return@post
            }

            val tenantId = 1L // TODO: Get from JWT

            try {
                val tokenResult = amazonAuthService.exchangeCodeForToken(request.code)

                tokenResult.fold(
                    onSuccess = { tokenResponse ->
                        amazonService.saveStoreCredentials(
                            tenantId = tenantId,
                            storeId = storeId,
                            sellerId = request.sellingPartnerId ?: "",
                            marketplaceId = AmazonConstants.US_MARKETPLACE_ID,
                            refreshToken = tokenResponse.refreshToken ?: "",
                            storeName = null
                        )

                        call.respond(HttpStatusCode.OK, mapOf(
                            "success" to true,
                            "message" to "Amazon connected successfully"
                        ))
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("OAuth failed: ${error.message}"))
                    }
                )
            } catch (e: Exception) {
                logger.error("Exception during Amazon callback POST", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Callback failed: ${e.message}"))
            }
        }

        /**
         * POST /api/v1/amazon/disconnect/{storeId}
         * Disconnect Amazon store
         */
        post("/disconnect/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid store ID"))
                return@post
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            try {
                val result = amazonService.disconnectStore(tenantId, storeId)

                result.fold(
                    onSuccess = { response ->
                        // Invalidate cached tokens
                        amazonAuthService.invalidateToken(storeId)
                        logger.info("Disconnected Amazon store $storeId for tenant $tenantId")
                        call.respond(HttpStatusCode.OK, response)
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(error.message ?: "Disconnect failed"))
                    }
                )
            } catch (e: Exception) {
                logger.error("Failed to disconnect Amazon store $storeId", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Disconnect failed: ${e.message}"))
            }
        }

        /**
         * POST /api/v1/amazon/sync/{storeId}
         * Sync orders from Amazon
         */
        post("/sync/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid store ID"))
                return@post
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            try {
                logger.info("Starting Amazon sync for tenant $tenantId, store $storeId")

                val result = amazonService.syncOrders(tenantId, storeId)

                result.fold(
                    onSuccess = { syncResult ->
                        call.respond(HttpStatusCode.OK, AmazonSyncResponse(
                            success = syncResult.success,
                            message = "Sync completed",
                            ordersProcessed = syncResult.ordersProcessed,
                            ordersInserted = syncResult.ordersInserted,
                            ordersSkipped = syncResult.ordersSkipped,
                            errors = syncResult.errors
                        ))
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Sync failed: ${error.message}"))
                    }
                )
            } catch (e: Exception) {
                logger.error("Amazon sync failed for store $storeId", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Sync failed: ${e.message}"))
            }
        }

        /**
         * GET /api/v1/amazon/stores
         * Get all Amazon stores for the tenant
         */
        get("/stores") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            try {
                val stores = amazonService.getStores(tenantId)
                call.respond(HttpStatusCode.OK, AmazonStoresResponse(
                    success = true,
                    stores = stores
                ))
            } catch (e: Exception) {
                logger.error("Failed to get Amazon stores", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to get stores: ${e.message}"))
            }
        }

        /**
         * GET /api/v1/amazon/stores/{storeId}
         * Get specific Amazon store
         */
        get("/stores/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid store ID"))
                return@get
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            try {
                val stores = amazonService.getStores(tenantId)
                val store = stores.find { it.storeId == storeId }

                if (store != null) {
                    call.respond(HttpStatusCode.OK, store)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Store not found"))
                }
            } catch (e: Exception) {
                logger.error("Failed to get Amazon store $storeId", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to get store: ${e.message}"))
            }
        }

        /**
         * POST /api/v1/amazon/stores/{storeId}/tracking
         * Update tracking information for an Amazon order
         */
        post("/stores/{storeId}/tracking") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid store ID"))
                return@post
            }

            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            val request = try {
                call.receive<AmazonUpdateTrackingRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                return@post
            }

            try {
                val result = amazonService.updateTracking(
                    tenantId = tenantId,
                    orderId = request.orderId,
                    trackingNumber = request.trackingNumber,
                    carrier = request.carrier,
                    carrierName = request.carrierName,
                    shippingMethod = request.shippingMethod
                )

                result.fold(
                    onSuccess = {
                        call.respond(HttpStatusCode.OK, mapOf(
                            "success" to true,
                            "message" to "Tracking updated successfully"
                        ))
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update tracking: ${error.message}"))
                    }
                )
            } catch (e: Exception) {
                logger.error("Failed to update tracking", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update tracking: ${e.message}"))
            }
        }

        /**
         * POST /api/v1/amazon/connection-check
         * Check connection status for multiple stores
         */
        post("/connection-check") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            val request = try {
                call.receive<ConnectionCheckRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                return@post
            }

            try {
                val stores = amazonService.getStores(tenantId)
                val statusMap = request.storeIds.associateWith { storeId ->
                    val store = stores.find { it.storeId == storeId }
                    store?.isConnected ?: false
                }

                call.respond(HttpStatusCode.OK, ConnectionCheckResponse(
                    success = true,
                    connectionStatus = statusMap
                ))
            } catch (e: Exception) {
                logger.error("Failed to check connections", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Connection check failed: ${e.message}"))
            }
        }
    }
}

// =====================================================
// REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class AmazonSyncResponse(
    val success: Boolean,
    val message: String,
    val ordersProcessed: Int = 0,
    val ordersInserted: Int = 0,
    val ordersSkipped: Int = 0,
    val errors: List<String> = emptyList()
)

@Serializable
data class AmazonStoresResponse(
    val success: Boolean,
    val stores: List<AmazonStoreResponse>
)

@Serializable
data class AmazonUpdateTrackingRequest(
    val orderId: Long,
    val trackingNumber: String,
    val carrier: String,
    val carrierName: String? = null,
    val shippingMethod: String? = null
)

@Serializable
data class ConnectionCheckRequest(
    val storeIds: List<Long>
)

@Serializable
data class ConnectionCheckResponse(
    val success: Boolean,
    val connectionStatus: Map<Long, Boolean>
)
