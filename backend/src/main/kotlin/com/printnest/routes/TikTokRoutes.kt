package com.printnest.routes

import com.printnest.integrations.tiktok.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext

fun Route.tikTokRoutes() {
    val tikTokService: TikTokService = GlobalContext.get().get()
    val tikTokAuthService: TikTokAuthService = GlobalContext.get().get()

    route("/tiktok") {

        /**
         * GET /api/v1/tiktok/auth/{storeId}
         * Generate OAuth authorization URL for TikTok Shop
         */
        get("/auth/{storeId}") {
            val storeId = call.parameters["storeId"]
            if (storeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "success" to false,
                    "message" to "Store ID is required"
                ))
                return@get
            }

            try {
                val authUrl = tikTokAuthService.generateAuthUrl(storeId)
                call.respond(HttpStatusCode.OK, TikTokAuthResponse(
                    success = true,
                    authUrl = authUrl
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, TikTokAuthResponse(
                    success = false,
                    message = "Failed to generate auth URL: ${e.message}"
                ))
            }
        }

        /**
         * GET /api/v1/tiktok/callback
         * OAuth callback handler for TikTok Shop
         */
        get("/callback") {
            val code = call.request.queryParameters["code"]
            val storeId = call.request.queryParameters["state"]

            if (code.isNullOrBlank() || storeId.isNullOrBlank()) {
                call.respondRedirect("/dashboard/integrations?error=missing_params")
                return@get
            }

            // Get tenant ID from session/JWT (TODO: implement proper auth)
            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L
            val userId = call.request.queryParameters["user_id"]?.toLongOrNull() ?: 1L

            try {
                // Exchange code for token
                val tokenResult = tikTokAuthService.exchangeCodeForToken(code)

                tokenResult.fold(
                    onSuccess = { tokenData ->
                        // Connect store
                        val storeResult = tikTokService.connectStore(
                            tenantId = tenantId,
                            userId = userId,
                            storeId = storeId,
                            tokenData = tokenData
                        )

                        storeResult.fold(
                            onSuccess = { store ->
                                call.respondRedirect("/dashboard/integrations?success=tiktok_connected&shop=${store.shopName}")
                            },
                            onFailure = { error ->
                                call.respondRedirect("/dashboard/integrations?error=${error.message}")
                            }
                        )
                    },
                    onFailure = { error ->
                        call.respondRedirect("/dashboard/integrations?error=${error.message}")
                    }
                )
            } catch (e: Exception) {
                call.respondRedirect("/dashboard/integrations?error=${e.message}")
            }
        }

        /**
         * POST /api/v1/tiktok/disconnect/{storeId}
         * Disconnect a TikTok Shop store
         */
        post("/disconnect/{storeId}") {
            val storeId = call.parameters["storeId"]
            if (storeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, TikTokDisconnectResponse(
                    success = false,
                    message = "Store ID is required"
                ))
                return@post
            }

            // Get tenant ID from JWT (TODO: implement proper auth)
            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L

            val success = tikTokService.disconnectStore(tenantId, storeId)

            if (success) {
                call.respond(HttpStatusCode.OK, TikTokDisconnectResponse(
                    success = true,
                    message = "TikTok store disconnected successfully"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, TikTokDisconnectResponse(
                    success = false,
                    message = "Store not found"
                ))
            }
        }

        /**
         * POST /api/v1/tiktok/sync/{storeId}
         * Sync orders from TikTok Shop
         */
        post("/sync/{storeId}") {
            val storeId = call.parameters["storeId"]
            if (storeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, TikTokSyncResponse(
                    success = false,
                    message = "Store ID is required"
                ))
                return@post
            }

            // Get tenant ID from JWT (TODO: implement proper auth)
            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L

            // Optional: Get status filter from request body
            val request = try {
                call.receiveNullable<SyncOrdersFilterRequest>()
            } catch (e: Exception) {
                null
            }

            val status = request?.status?.let { statusStr ->
                try {
                    TikTokOrderStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    TikTokOrderStatus.AWAITING_SHIPMENT
                }
            } ?: TikTokOrderStatus.AWAITING_SHIPMENT

            val result = tikTokService.syncOrders(tenantId, storeId, status)

            result.fold(
                onSuccess = { syncResult ->
                    call.respond(HttpStatusCode.OK, TikTokSyncResponse(
                        success = true,
                        message = "Sync completed successfully",
                        ordersSynced = syncResult.ordersSynced,
                        ordersSkipped = syncResult.ordersSkipped,
                        errors = syncResult.errors
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, TikTokSyncResponse(
                        success = false,
                        message = error.message ?: "Sync failed"
                    ))
                }
            )
        }

        /**
         * GET /api/v1/tiktok/stores
         * Get list of connected TikTok stores
         */
        get("/stores") {
            // Get tenant ID from JWT (TODO: implement proper auth)
            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L
            val activeOnly = call.request.queryParameters["active"]?.toBoolean() ?: false

            val stores = if (activeOnly) {
                tikTokService.getActiveStores(tenantId)
            } else {
                tikTokService.getStores(tenantId)
            }

            call.respond(HttpStatusCode.OK, stores.map { store ->
                TikTokStoreResponse(
                    id = store.id,
                    storeId = store.storeId,
                    shopId = store.shopId,
                    shopName = store.shopName,
                    region = store.region,
                    isActive = store.isActive,
                    isConnected = !store.shopCipher.isNullOrBlank() && !store.accessToken.isNullOrBlank(),
                    lastSyncAt = store.lastSyncAt
                )
            })
        }

        /**
         * GET /api/v1/tiktok/stores/{storeId}
         * Get a specific TikTok store
         */
        get("/stores/{storeId}") {
            val storeId = call.parameters["storeId"]
            if (storeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Store ID is required"))
                return@get
            }

            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L

            val stores = tikTokService.getStores(tenantId)
            val store = stores.find { it.storeId == storeId }

            if (store != null) {
                call.respond(HttpStatusCode.OK, TikTokStoreResponse(
                    id = store.id,
                    storeId = store.storeId,
                    shopId = store.shopId,
                    shopName = store.shopName,
                    region = store.region,
                    isActive = store.isActive,
                    isConnected = !store.shopCipher.isNullOrBlank() && !store.accessToken.isNullOrBlank(),
                    lastSyncAt = store.lastSyncAt
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Store not found"))
            }
        }

        /**
         * GET /api/v1/tiktok/shipping-providers/{storeId}
         * Get available shipping providers for a store/delivery option
         */
        get("/shipping-providers/{storeId}") {
            val storeId = call.parameters["storeId"]
            val deliveryOptionId = call.request.queryParameters["delivery_option_id"]

            if (storeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Store ID is required"))
                return@get
            }

            if (deliveryOptionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Delivery option ID is required"))
                return@get
            }

            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L

            val result = tikTokService.getShippingOptions(tenantId, storeId, deliveryOptionId)

            result.fold(
                onSuccess = { providers ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "shipping_providers" to providers.map { provider ->
                            mapOf(
                                "id" to provider.id,
                                "name" to provider.name,
                                "is_available" to provider.isAvailable,
                                "support_tracking_url" to provider.supportTrackingUrl
                            )
                        }
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "success" to false,
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/tiktok/tracking/{storeId}
         * Update tracking information for a TikTok order
         */
        post("/tracking/{storeId}") {
            val storeId = call.parameters["storeId"]
            if (storeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "success" to false,
                    "message" to "Store ID is required"
                ))
                return@post
            }

            val request = call.receive<TikTokUpdateTrackingRequest>()
            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L

            val result = tikTokService.updateTracking(
                tenantId = tenantId,
                storeId = storeId,
                orderId = request.orderId,
                trackingNumber = request.trackingNumber,
                carrier = request.carrier,
                deliveryOptionId = request.deliveryOptionId
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
                        "success" to false,
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/tiktok/connection-check
         * Check connection status for multiple stores
         */
        post("/connection-check") {
            val request = call.receive<TikTokConnectionCheckRequest>()
            val tenantId = call.request.queryParameters["tenant_id"]?.toLongOrNull() ?: 1L

            val statusMap = tikTokService.checkConnectionStatus(tenantId, request.storeIds)

            call.respond(HttpStatusCode.OK, mapOf(
                "success" to true,
                "connection_status" to statusMap
            ))
        }
    }
}

// =====================================================
// REQUEST MODELS
// =====================================================

@Serializable
data class SyncOrdersFilterRequest(
    val status: String? = null
)

@Serializable
data class TikTokUpdateTrackingRequest(
    @SerialName("order_id")
    val orderId: String,
    @SerialName("tracking_number")
    val trackingNumber: String,
    val carrier: String,
    @SerialName("delivery_option_id")
    val deliveryOptionId: String
)
