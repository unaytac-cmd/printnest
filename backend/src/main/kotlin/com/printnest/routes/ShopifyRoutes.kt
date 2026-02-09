package com.printnest.routes

import com.printnest.integrations.shopify.ShopifyAuthService
import com.printnest.integrations.shopify.ShopifyService
import com.printnest.integrations.shopify.ShopifyStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ShopifyRoutes")

fun Route.shopifyRoutes() {
    val shopifyAuthService: ShopifyAuthService = GlobalContext.get().get()
    val shopifyService: ShopifyService = GlobalContext.get().get()

    route("/shopify") {

        // =====================================================
        // OAUTH FLOW
        // =====================================================

        /**
         * GET /api/v1/shopify/auth
         * Start OAuth flow - returns auth URL for Shopify
         *
         * Query params:
         * - shop: Shopify store domain (required)
         * - state: Optional state (defaults to tenant ID from JWT)
         */
        get("/auth") {
            val shop = call.parameters["shop"]
            if (shop.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Missing shop parameter",
                    "message" to "Please provide the shop domain (e.g., my-store.myshopify.com)"
                ))
                return@get
            }

            // Validate shop domain format
            if (!shopifyAuthService.isValidShopDomain(shopifyAuthService.normalizeShopDomain(shop))) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Invalid shop domain",
                    "message" to "Shop domain must be in format: store-name.myshopify.com"
                ))
                return@get
            }

            // Get tenant ID from JWT or query param (for state)
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT
            val state = call.parameters["state"] ?: tenantId.toString()

            try {
                val authUrl = shopifyAuthService.generateAuthUrl(
                    shop = shop,
                    state = state
                )

                call.respond(HttpStatusCode.OK, ShopifyAuthUrlResponse(
                    authUrl = authUrl,
                    shop = shopifyAuthService.normalizeShopDomain(shop)
                ))
            } catch (e: Exception) {
                logger.error("Error generating Shopify auth URL", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Failed to generate auth URL",
                    "message" to e.message
                ))
            }
        }

        /**
         * GET /api/v1/shopify/callback
         * OAuth callback from Shopify
         *
         * Query params from Shopify:
         * - code: Authorization code
         * - shop: Shop domain
         * - state: State we passed (tenant ID)
         * - hmac: HMAC signature
         * - host: Base64 encoded host
         */
        get("/callback") {
            val queryParams = call.request.queryParameters.entries()
                .associate { it.key to (it.value.firstOrNull() ?: "") }

            val code = queryParams["code"]
            val shop = queryParams["shop"]
            val state = queryParams["state"]
            val host = queryParams["host"]

            logger.info("Shopify OAuth callback: shop=$shop, state=$state")

            // Validate required params
            if (code.isNullOrBlank() || shop.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Missing required parameters",
                    "message" to "code and shop are required"
                ))
                return@get
            }

            // Verify HMAC signature
            if (!shopifyAuthService.verifyOAuthCallback(queryParams)) {
                logger.warn("HMAC verification failed for shop: $shop")
                call.respond(HttpStatusCode.Unauthorized, mapOf(
                    "error" to "Invalid signature",
                    "message" to "HMAC verification failed"
                ))
                return@get
            }

            try {
                // Exchange code for access token
                val tokenResult = shopifyAuthService.exchangeCodeForToken(shop, code)

                tokenResult.fold(
                    onSuccess = { tokenResponse ->
                        // Parse state to get tenant ID
                        val tenantId = state?.toLongOrNull() ?: 1L // TODO: Handle properly

                        // Save store connection
                        val store = shopifyService.saveStore(
                            tenantId = tenantId,
                            shopUrl = shop,
                            accessToken = tokenResponse.accessToken,
                            scope = tokenResponse.scope
                        )

                        logger.info("Shopify store connected: ${store.shopUrl} for tenant $tenantId")

                        // Redirect to success page or return JSON
                        val redirectUrl = System.getenv("SHOPIFY_SUCCESS_REDIRECT")
                            ?: "https://printnest.com/dashboard/integrations?shopify=connected&shop=$shop"

                        if (host != null) {
                            // For embedded app flow
                            call.respondRedirect("$redirectUrl&host=$host")
                        } else {
                            call.respond(HttpStatusCode.OK, ShopifyConnectResponse(
                                success = true,
                                message = "Shopify store connected successfully",
                                store = ShopifyStoreInfo(
                                    id = store.id,
                                    shopUrl = store.shopUrl,
                                    isActive = store.isActive
                                )
                            ))
                        }
                    },
                    onFailure = { error ->
                        logger.error("Failed to exchange Shopify code for token", error)
                        call.respond(HttpStatusCode.BadRequest, mapOf(
                            "error" to "Token exchange failed",
                            "message" to error.message
                        ))
                    }
                )
            } catch (e: Exception) {
                logger.error("Error in Shopify callback", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Callback processing failed",
                    "message" to e.message
                ))
            }
        }

        /**
         * GET /api/v1/shopify/auth-check
         * Check shop and enforce OAuth (for embedded apps)
         */
        get("/auth-check") {
            val shop = call.parameters["shop"]
            val host = call.parameters["host"]

            if (shop.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Missing shop parameter"
                ))
                return@get
            }

            val normalizedShop = shopifyAuthService.normalizeShopDomain(shop)

            // Check if store is already connected
            val stores = shopifyService.getStores(1L) // TODO: Get tenant ID from context
            val existingStore = stores.find { it.shopUrl == normalizedShop && it.isActive }

            if (existingStore != null && !existingStore.accessToken.isNullOrBlank()) {
                // Store is connected, redirect to embedded app
                val embedUrl = "https://printnest.com/shopify/embedded?shop=$shop&host=$host"
                call.respondRedirect(embedUrl)
            } else {
                // Need to authenticate
                val authUrl = shopifyAuthService.generateAuthUrl(shop = shop)
                call.respondRedirect(authUrl)
            }
        }

        // =====================================================
        // STORE MANAGEMENT
        // =====================================================

        /**
         * GET /api/v1/shopify/stores
         * Get all Shopify stores for the tenant
         */
        get("/stores") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT
            val includeInactive = call.parameters["includeInactive"]?.toBoolean() ?: false

            val stores = if (includeInactive) {
                shopifyService.getStores(tenantId)
            } else {
                shopifyService.getActiveStores(tenantId)
            }

            call.respond(HttpStatusCode.OK, stores.map { it.toInfo() })
        }

        /**
         * GET /api/v1/shopify/stores/{storeId}
         * Get a specific Shopify store
         */
        get("/stores/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@get
            }

            val store = shopifyService.getStore(storeId)
            if (store == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Store not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, store.toInfo())
        }

        /**
         * POST /api/v1/shopify/disconnect/{storeId}
         * Disconnect a Shopify store
         */
        post("/disconnect/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val success = shopifyService.disconnectStore(storeId)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "Store disconnected successfully"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf(
                    "error" to "Store not found"
                ))
            }
        }

        // =====================================================
        // ORDER SYNC
        // =====================================================

        /**
         * POST /api/v1/shopify/sync/{storeId}
         * Sync orders from a Shopify store
         */
        post("/sync/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            logger.info("Starting Shopify sync for store: $storeId")

            val result = shopifyService.syncOrders(storeId)

            result.fold(
                onSuccess = { syncResult ->
                    call.respond(HttpStatusCode.OK, ShopifySyncResponse(
                        success = true,
                        message = "Sync completed successfully",
                        totalFetched = syncResult.totalFetched,
                        totalInserted = syncResult.totalInserted,
                        totalSkipped = syncResult.totalSkipped
                    ))
                },
                onFailure = { error ->
                    logger.error("Shopify sync failed for store $storeId", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Sync failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/shopify/sync-all
         * Sync orders from all active Shopify stores for the tenant
         */
        post("/sync-all") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            val stores = shopifyService.getActiveStores(tenantId)

            if (stores.isEmpty()) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "No active Shopify stores to sync"
                ))
                return@post
            }

            var totalFetched = 0
            var totalInserted = 0
            var totalSkipped = 0
            val errors = mutableListOf<String>()

            for (store in stores) {
                val result = shopifyService.syncOrders(store.id)
                result.fold(
                    onSuccess = { syncResult ->
                        totalFetched += syncResult.totalFetched
                        totalInserted += syncResult.totalInserted
                        totalSkipped += syncResult.totalSkipped
                    },
                    onFailure = { error ->
                        errors.add("${store.shopUrl}: ${error.message}")
                    }
                )
            }

            call.respond(HttpStatusCode.OK, mapOf(
                "success" to errors.isEmpty(),
                "message" to "Synced ${stores.size} stores",
                "totalFetched" to totalFetched,
                "totalInserted" to totalInserted,
                "totalSkipped" to totalSkipped,
                "errors" to errors
            ))
        }

        // =====================================================
        // FULFILLMENT
        // =====================================================

        /**
         * POST /api/v1/shopify/fulfill/{orderId}
         * Create fulfillment for an order in Shopify
         */
        post("/fulfill/{orderId}") {
            val orderId = call.parameters["orderId"]?.toLongOrNull()
            if (orderId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))
                return@post
            }

            val request = call.receive<ShopifyFulfillRequest>()

            val result = shopifyService.updateFulfillment(
                orderId = orderId,
                trackingNumber = request.trackingNumber,
                carrier = request.carrier,
                trackingUrl = request.trackingUrl
            )

            result.fold(
                onSuccess = { fulfillmentResult ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Fulfillment created",
                        "fulfillmentId" to fulfillmentResult.fulfillmentId,
                        "trackingNumber" to fulfillmentResult.trackingNumber,
                        "carrier" to fulfillmentResult.carrier
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Fulfillment failed",
                        "message" to error.message
                    ))
                }
            )
        }

        // =====================================================
        // WEBHOOKS
        // =====================================================

        /**
         * POST /api/v1/shopify/webhook
         * Receive webhooks from Shopify
         */
        post("/webhook") {
            val topic = call.request.headers["X-Shopify-Topic"]
            val shopDomain = call.request.headers["X-Shopify-Shop-Domain"]
            val hmacHeader = call.request.headers["X-Shopify-Hmac-Sha256"]

            if (topic.isNullOrBlank() || shopDomain.isNullOrBlank()) {
                logger.warn("Webhook missing required headers")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing headers"))
                return@post
            }

            val rawBody = call.receiveText()

            // Verify HMAC
            if (!hmacHeader.isNullOrBlank()) {
                val isValid = shopifyAuthService.verifyHmac(rawBody.toByteArray(), hmacHeader)
                if (!isValid) {
                    logger.warn("Webhook HMAC verification failed for $topic from $shopDomain")
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid signature"))
                    return@post
                }
            }

            logger.info("Received Shopify webhook: topic=$topic, shop=$shopDomain")

            val result = shopifyService.handleWebhook(topic, shopDomain, rawBody)

            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to result.message
                ))
            }
        }

        // =====================================================
        // GDPR WEBHOOKS (Required by Shopify)
        // =====================================================

        /**
         * POST /api/v1/shopify/webhooks/customers/data_request
         * Customer data request (GDPR)
         */
        post("/webhooks/customers/data_request") {
            handleGdprWebhook(call, shopifyAuthService, shopifyService, "customers/data_request")
        }

        /**
         * POST /api/v1/shopify/webhooks/customers/redact
         * Customer data deletion request (GDPR)
         */
        post("/webhooks/customers/redact") {
            handleGdprWebhook(call, shopifyAuthService, shopifyService, "customers/redact")
        }

        /**
         * POST /api/v1/shopify/webhooks/shop/redact
         * Shop data deletion request (GDPR)
         */
        post("/webhooks/shop/redact") {
            handleGdprWebhook(call, shopifyAuthService, shopifyService, "shop/redact")
        }

        // =====================================================
        // WEBHOOK REGISTRATION
        // =====================================================

        /**
         * POST /api/v1/shopify/webhooks/register/{storeId}
         * Register webhooks for a store
         */
        post("/webhooks/register/{storeId}") {
            val storeId = call.parameters["storeId"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@post
            }

            val baseUrl = System.getenv("API_BASE_URL") ?: "https://api.printnest.com"

            val result = shopifyService.registerWebhooks(storeId, baseUrl)

            result.fold(
                onSuccess = { webhooks ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Registered ${webhooks.size} webhooks",
                        "webhooks" to webhooks.map { mapOf(
                            "id" to it.id,
                            "topic" to it.topic,
                            "address" to it.address
                        )}
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Registration failed",
                        "message" to error.message
                    ))
                }
            )
        }

        // =====================================================
        // CONNECTION STATUS
        // =====================================================

        /**
         * POST /api/v1/shopify/connection-status
         * Check connection status for multiple stores
         */
        post("/connection-status") {
            val request = call.receive<ShopifyConnectionStatusRequest>()

            val statusMap = mutableMapOf<Long, Boolean>()
            for (storeId in request.storeIds) {
                val store = shopifyService.getStore(storeId)
                statusMap[storeId] = store?.accessToken?.isNotBlank() == true && store.isActive
            }

            call.respond(HttpStatusCode.OK, statusMap)
        }
    }
}

/**
 * Handle GDPR webhook requests
 */
private suspend fun handleGdprWebhook(
    call: io.ktor.server.application.ApplicationCall,
    authService: ShopifyAuthService,
    shopifyService: ShopifyService,
    topic: String
) {
    val hmacHeader = call.request.headers["X-Shopify-Hmac-Sha256"]
    val rawBody = call.receiveText()

    // Verify HMAC
    if (!hmacHeader.isNullOrBlank()) {
        val isValid = authService.verifyHmac(rawBody.toByteArray(), hmacHeader)
        if (!isValid) {
            logger.warn("GDPR webhook HMAC verification failed for $topic")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid signature"))
            return
        }
    }

    val shopDomain = try {
        val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(rawBody)
        jsonElement.jsonObject["shop_domain"]?.jsonPrimitive?.content ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    logger.info("GDPR webhook received: topic=$topic, shop=$shopDomain")

    val result = shopifyService.handleWebhook(topic, shopDomain, rawBody)

    if (result.success) {
        call.respond(HttpStatusCode.OK, mapOf("status" to "acknowledged"))
    } else {
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
    }
}

// =====================================================
// REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class ShopifyAuthUrlResponse(
    val authUrl: String,
    val shop: String
)

@Serializable
data class ShopifyConnectResponse(
    val success: Boolean,
    val message: String,
    val store: ShopifyStoreInfo
)

@Serializable
data class ShopifyStoreInfo(
    val id: Long,
    val shopUrl: String,
    val shopOwnerEmail: String? = null,
    val isActive: Boolean,
    val lastSyncAt: String? = null
)

@Serializable
data class ShopifySyncResponse(
    val success: Boolean,
    val message: String,
    val totalFetched: Int,
    val totalInserted: Int,
    val totalSkipped: Int
)

@Serializable
data class ShopifyFulfillRequest(
    val trackingNumber: String,
    val carrier: String,
    val trackingUrl: String? = null
)

@Serializable
data class ShopifyConnectionStatusRequest(
    val storeIds: List<Long>
)

/**
 * Extension to convert ShopifyStore to ShopifyStoreInfo
 */
private fun ShopifyStore.toInfo(): ShopifyStoreInfo {
    return ShopifyStoreInfo(
        id = this.id,
        shopUrl = this.shopUrl,
        shopOwnerEmail = this.shopOwnerEmail,
        isActive = this.isActive,
        lastSyncAt = this.lastSyncAt
    )
}
