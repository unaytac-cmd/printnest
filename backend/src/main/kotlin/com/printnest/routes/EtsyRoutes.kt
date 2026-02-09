package com.printnest.routes

import com.printnest.integrations.etsy.EtsyService
import com.printnest.integrations.etsy.EtsyStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("EtsyRoutes")

fun Route.etsyRoutes() {
    val etsyService: EtsyService = GlobalContext.get().get()

    route("/etsy") {

        // =====================================================
        // OAUTH AUTHENTICATION
        // =====================================================

        /**
         * GET /api/v1/etsy/auth/{storeId}
         * Get OAuth authorization URL to connect Etsy store
         *
         * Query params:
         * - redirect_uri: Callback URL after authorization
         */
        get("/auth/{storeId}") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            val redirectUri = call.request.queryParameters["redirect_uri"]
                ?: "${getBaseUrl(call)}/api/v1/etsy/callback"

            try {
                val authUrl = etsyService.getAuthorizationUrl(tenantId, redirectUri)

                call.respond(HttpStatusCode.OK, EtsyAuthUrlResponse(
                    authUrl = authUrl,
                    redirectUri = redirectUri
                ))
            } catch (e: Exception) {
                logger.error("Failed to generate Etsy auth URL", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Failed to generate authorization URL",
                    "message" to e.message
                ))
            }
        }

        /**
         * GET /api/v1/etsy/callback
         * OAuth callback endpoint - handles authorization code exchange
         *
         * Query params:
         * - code: Authorization code from Etsy
         * - state: State parameter for CSRF protection
         */
        get("/callback") {
            val code = call.request.queryParameters["code"]
            val state = call.request.queryParameters["state"]
            val error = call.request.queryParameters["error"]
            val errorDescription = call.request.queryParameters["error_description"]

            // Handle error response from Etsy
            if (error != null) {
                logger.warn("Etsy OAuth error: $error - $errorDescription")
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to error,
                    "message" to (errorDescription ?: "OAuth authorization failed")
                ))
                return@get
            }

            // Validate required parameters
            if (code.isNullOrBlank() || state.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Missing parameters",
                    "message" to "Both 'code' and 'state' parameters are required"
                ))
                return@get
            }

            // Handle callback
            val result = etsyService.handleOAuthCallback(code, state)

            result.fold(
                onSuccess = { store ->
                    call.respond(HttpStatusCode.OK, EtsyConnectResponse(
                        success = true,
                        message = "Successfully connected Etsy store: ${store.shopName}",
                        store = store.toResponse()
                    ))
                },
                onFailure = { error ->
                    logger.error("Etsy OAuth callback failed", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Connection failed",
                        "message" to error.message
                    ))
                }
            )
        }

        // =====================================================
        // STORE MANAGEMENT
        // =====================================================

        /**
         * GET /api/v1/etsy/stores
         * List all connected Etsy stores for the current tenant
         */
        get("/stores") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            val stores = etsyService.getConnectedStores(tenantId)

            call.respond(HttpStatusCode.OK, EtsyStoresResponse(
                stores = stores.map { it.toResponse() },
                count = stores.size
            ))
        }

        /**
         * GET /api/v1/etsy/stores/{storeId}
         * Get a single Etsy store by ID
         */
        get("/stores/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@get
            }

            val store = etsyService.getStore(storeId)
            if (store == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Store not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, store.toResponse())
        }

        /**
         * POST /api/v1/etsy/disconnect/{storeId}
         * Disconnect an Etsy store
         */
        post("/disconnect/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val success = etsyService.disconnectStore(storeId)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "Store disconnected successfully"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf(
                    "error" to "Store not found or already disconnected"
                ))
            }
        }

        // =====================================================
        // ORDER SYNCHRONIZATION
        // =====================================================

        /**
         * POST /api/v1/etsy/sync/{storeId}
         * Manually trigger order synchronization for an Etsy store
         *
         * Body (optional):
         * - minLastModified: Unix timestamp to sync orders modified after this time
         */
        post("/sync/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val request = try {
                call.receiveNullable<EtsySyncRequest>()
            } catch (e: Exception) {
                null
            }

            val result = etsyService.syncOrders(storeId, request?.minLastModified)

            result.fold(
                onSuccess = { syncResult ->
                    call.respond(HttpStatusCode.OK, EtsySyncResponse(
                        success = true,
                        message = "Synced ${syncResult.totalSynced} orders",
                        storeId = syncResult.storeId,
                        totalSynced = syncResult.totalSynced,
                        newOrders = syncResult.newOrders,
                        updatedOrders = syncResult.updatedOrders
                    ))
                },
                onFailure = { error ->
                    logger.error("Order sync failed for store $storeId", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Sync failed",
                        "message" to error.message
                    ))
                }
            )
        }

        // =====================================================
        // TRACKING UPDATES
        // =====================================================

        /**
         * POST /api/v1/etsy/tracking/{orderId}
         * Send tracking information to Etsy for an order
         *
         * Body:
         * - trackingCode: Tracking number
         * - carrier: Carrier name (will be mapped to Etsy carrier)
         */
        post("/tracking/{orderId}") {
            val orderId = call.parameters["orderId"]?.toLongOrNull()
            if (orderId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))
                return@post
            }

            val request = call.receive<EtsyTrackingRequest>()

            if (request.trackingCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tracking code is required"))
                return@post
            }

            if (request.carrier.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Carrier is required"))
                return@post
            }

            val result = etsyService.updateTracking(orderId, request.trackingCode, request.carrier)

            result.fold(
                onSuccess = {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Tracking information sent to Etsy"
                    ))
                },
                onFailure = { error ->
                    logger.error("Failed to send tracking for order $orderId", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to send tracking",
                        "message" to error.message
                    ))
                }
            )
        }

        // =====================================================
        // LISTING OPERATIONS
        // =====================================================

        /**
         * GET /api/v1/etsy/stores/{storeId}/listings
         * Get listings from an Etsy store
         *
         * Query params:
         * - state: Listing state (active, inactive, sold_out, draft, expired)
         * - limit: Number of results per page (max 100)
         * - offset: Pagination offset
         */
        get("/stores/{storeId}/listings") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@get
            }

            val state = call.request.queryParameters["state"] ?: "active"
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 25
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val result = etsyService.getStoreListings(storeId, state, limit, offset)

            result.fold(
                onSuccess = { listings ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "listings" to listings.results,
                        "count" to listings.count,
                        "limit" to limit,
                        "offset" to offset
                    ))
                },
                onFailure = { error ->
                    logger.error("Failed to get listings for store $storeId", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to get listings",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/etsy/stores/{storeId}/listings/{listingId}
         * Get a single listing with images
         */
        get("/stores/{storeId}/listings/{listingId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            val listingId = call.parameters["listingId"]?.toLongOrNull()

            if (storeId == null || listingId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID or listing ID"))
                return@get
            }

            val result = etsyService.getListing(storeId, listingId)

            result.fold(
                onSuccess = { listing ->
                    call.respond(HttpStatusCode.OK, listing)
                },
                onFailure = { error ->
                    logger.error("Failed to get listing $listingId from store $storeId", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to get listing",
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
data class EtsyAuthUrlResponse(
    val authUrl: String,
    val redirectUri: String
)

@Serializable
data class EtsyConnectResponse(
    val success: Boolean,
    val message: String,
    val store: EtsyStoreResponse
)

@Serializable
data class EtsyStoreResponse(
    val id: Long,
    val shopId: Long,
    val shopName: String,
    val isActive: Boolean,
    val lastSyncAt: String? = null,
    val createdAt: String? = null
)

@Serializable
data class EtsyStoresResponse(
    val stores: List<EtsyStoreResponse>,
    val count: Int
)

@Serializable
data class EtsySyncRequest(
    val minLastModified: Long? = null
)

@Serializable
data class EtsySyncResponse(
    val success: Boolean,
    val message: String,
    val storeId: Long,
    val totalSynced: Int,
    val newOrders: Int,
    val updatedOrders: Int
)

@Serializable
data class EtsyTrackingRequest(
    val trackingCode: String,
    val carrier: String
)

// =====================================================
// HELPER FUNCTIONS
// =====================================================

/**
 * Convert EtsyStore to response DTO (hides sensitive token data)
 */
private fun EtsyStore.toResponse(): EtsyStoreResponse {
    return EtsyStoreResponse(
        id = this.id,
        shopId = this.shopId,
        shopName = this.shopName,
        isActive = this.isActive,
        lastSyncAt = this.lastSyncAt,
        createdAt = this.createdAt
    )
}

/**
 * Get base URL from request
 */
private fun getBaseUrl(call: io.ktor.server.application.ApplicationCall): String {
    val scheme = call.request.headers["X-Forwarded-Proto"]
        ?: if (call.request.local.scheme == "https") "https" else "http"
    val host = call.request.headers["X-Forwarded-Host"]
        ?: call.request.host()
    val port = call.request.headers["X-Forwarded-Port"]?.toIntOrNull()
        ?: call.request.port()

    return if ((scheme == "http" && port == 80) || (scheme == "https" && port == 443)) {
        "$scheme://$host"
    } else {
        "$scheme://$host:$port"
    }
}
