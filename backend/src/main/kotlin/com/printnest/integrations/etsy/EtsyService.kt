package com.printnest.integrations.etsy

import com.printnest.domain.models.Address
import com.printnest.domain.models.OrderFull
import com.printnest.domain.models.OrderInfoFull
import com.printnest.domain.repository.OrderRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

/**
 * Etsy Integration Service
 *
 * High-level business logic for Etsy integration:
 * - Syncing orders from Etsy
 * - Converting Etsy orders to PrintNest orders
 * - Sending tracking information back to Etsy
 * - Managing store connections
 */
class EtsyService(
    private val client: EtsyClient,
    private val authService: EtsyAuthService,
    private val storeRepository: EtsyStoreRepository,
    private val orderRepository: OrderRepository,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(EtsyService::class.java)

    // =====================================================
    // STORE MANAGEMENT
    // =====================================================

    /**
     * Get OAuth authorization URL for connecting an Etsy store
     *
     * @param tenantId Tenant ID
     * @param redirectUri Callback URL after authorization
     * @return Authorization URL
     */
    fun getAuthorizationUrl(tenantId: Long, redirectUri: String): String {
        // Create a placeholder store ID (will be updated after connection)
        val storeId = System.currentTimeMillis()
        return authService.generateAuthUrl(tenantId, storeId, redirectUri)
    }

    /**
     * Complete OAuth flow and connect Etsy store
     *
     * @param code Authorization code from callback
     * @param state State parameter from callback
     * @return Connected store on success
     */
    suspend fun handleOAuthCallback(code: String, state: String): Result<EtsyStore> {
        logger.info("Handling OAuth callback with state: $state")

        // Retrieve auth state from Redis
        val authState = authService.getAuthState(state)
            ?: return Result.failure(EtsyException("Invalid or expired OAuth state", 400))

        // Exchange code for tokens
        val tokenResult = client.exchangeCodeForToken(
            code = code,
            codeVerifier = authState.codeVerifier,
            redirectUri = authState.redirectUri
        )

        if (tokenResult.isFailure) {
            return Result.failure(tokenResult.exceptionOrNull() ?: EtsyException("Token exchange failed", 500))
        }

        val tokens = tokenResult.getOrThrow()
        val tokenExpiry = authService.calculateTokenExpiry(tokens.expiresIn)

        // Get shop information
        val shopResult = client.getMyShop(tokens.accessToken)
        if (shopResult.isFailure) {
            return Result.failure(shopResult.exceptionOrNull() ?: EtsyException("Failed to get shop info", 500))
        }

        val shop = shopResult.getOrThrow()

        // Create or update store record
        val store = EtsyStore(
            tenantId = authState.tenantId,
            shopId = shop.shopId,
            shopName = shop.shopName ?: "Etsy Shop",
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            tokenExpiry = tokenExpiry,
            userId = shop.userId,
            isActive = true
        )

        val savedStore = storeRepository.upsert(store)
        logger.info("Successfully connected Etsy store: ${savedStore.shopName} (ID: ${savedStore.shopId})")

        return Result.success(savedStore)
    }

    /**
     * Disconnect an Etsy store
     *
     * @param storeId Internal store ID
     * @return true if disconnected
     */
    fun disconnectStore(storeId: Long): Boolean {
        logger.info("Disconnecting Etsy store: $storeId")
        return storeRepository.deactivate(storeId)
    }

    /**
     * Get all connected Etsy stores for a tenant
     *
     * @param tenantId Tenant ID
     * @return List of connected stores
     */
    fun getConnectedStores(tenantId: Long): List<EtsyStore> {
        return storeRepository.findByTenantId(tenantId)
    }

    /**
     * Get a single store by ID
     *
     * @param storeId Internal store ID
     * @return Store if found
     */
    fun getStore(storeId: Long): EtsyStore? {
        return storeRepository.findById(storeId)
    }

    // =====================================================
    // ORDER SYNCHRONIZATION
    // =====================================================

    /**
     * Sync orders from an Etsy store
     *
     * @param storeId Internal store ID
     * @param minLastModified Only sync orders modified after this timestamp
     * @return Sync result with count of synced orders
     */
    suspend fun syncOrders(storeId: Long, minLastModified: Long? = null): Result<OrderSyncResult> {
        logger.info("Starting order sync for Etsy store: $storeId")

        val store = storeRepository.findById(storeId)
            ?: return Result.failure(EtsyException("Store not found", 404))

        // Check and refresh token if needed
        val accessToken = ensureValidToken(store)
            ?: return Result.failure(EtsyException("Failed to get valid access token", 401))

        // Calculate default minLastModified if not provided (last 7 days)
        val effectiveMinLastModified = minLastModified
            ?: (System.currentTimeMillis() / 1000 - 7 * 24 * 60 * 60)

        // Fetch receipts from Etsy
        var totalSynced = 0
        var totalNew = 0
        var totalUpdated = 0
        var offset = 0
        val limit = 100

        do {
            val receiptsResult = client.getShopReceipts(
                accessToken = accessToken,
                shopId = store.shopId,
                minLastModified = effectiveMinLastModified,
                wasPaid = true, // Only fetch paid orders
                limit = limit,
                offset = offset
            )

            if (receiptsResult.isFailure) {
                logger.error("Failed to fetch receipts", receiptsResult.exceptionOrNull())
                return Result.failure(receiptsResult.exceptionOrNull() ?: EtsyException("Failed to fetch orders", 500))
            }

            val receiptsResponse = receiptsResult.getOrThrow()
            val receipts = receiptsResponse.results

            for (receipt in receipts) {
                try {
                    val isNew = processReceipt(store, receipt)
                    if (isNew) {
                        totalNew++
                    } else {
                        totalUpdated++
                    }
                    totalSynced++
                } catch (e: Exception) {
                    logger.error("Failed to process receipt ${receipt.receiptId}", e)
                }
            }

            offset += receipts.size

            // Rate limiting - Etsy allows ~10 requests per second
            if (receipts.size == limit) {
                kotlinx.coroutines.delay(200)
            }

        } while (offset < receiptsResponse.count && totalSynced < 1000) // Safety limit

        // Update last sync timestamp
        storeRepository.updateLastSyncAt(storeId)

        val result = OrderSyncResult(
            storeId = storeId,
            totalSynced = totalSynced,
            newOrders = totalNew,
            updatedOrders = totalUpdated
        )

        logger.info("Order sync completed for store $storeId: $totalSynced orders ($totalNew new, $totalUpdated updated)")
        return Result.success(result)
    }

    /**
     * Process a single Etsy receipt and convert to PrintNest order
     *
     * @param store Etsy store
     * @param receipt Etsy receipt
     * @return true if new order, false if updated
     */
    private fun processReceipt(store: EtsyStore, receipt: EtsyReceipt): Boolean {
        val externalOrderId = "ETSY-${receipt.receiptId}"

        // Check if order already exists
        val existingOrder = orderRepository.findByExternalOrderId(externalOrderId, store.tenantId)
        val isNew = existingOrder == null

        // Parse shipping address
        val shippingAddress = Address(
            name = receipt.buyerName,
            street1 = receipt.addressFirstLine,
            street2 = receipt.addressSecondLine,
            city = receipt.city,
            state = receipt.state,
            postalCode = receipt.zip,
            country = receipt.countryIso
        )

        // Calculate totals
        val totalAmount = BigDecimal.valueOf(receipt.grandtotal?.toDouble() ?: receipt.totalPrice?.toDouble() ?: 0.0)
        val shippingAmount = BigDecimal.valueOf(receipt.totalShippingCost?.toDouble() ?: 0.0)
        val taxAmount = BigDecimal.valueOf(receipt.totalTaxCost?.toDouble() ?: 0.0)

        // Determine order status
        val orderStatus = when {
            receipt.isShipped -> 4 // Shipped
            receipt.isPaid -> 1 // Paid/Processing
            else -> 0 // New
        }

        // Build order info
        val orderInfo = OrderInfoFull(
            toAddress = shippingAddress,
            orderNote = receipt.messageFromBuyer,
            giftNote = receipt.giftMessage
        )

        val orderInfoJson = json.encodeToString(orderInfo)
        val shippingAddressJson = json.encodeToString(shippingAddress)

        // Build order detail with raw Etsy data
        val orderDetail = json.encodeToString(EtsyOrderDetail(
            receiptId = receipt.receiptId,
            buyerUserId = receipt.buyerUserId,
            sellerUserId = receipt.sellerUserId,
            paymentMethod = receipt.paymentMethod,
            transactions = receipt.transactions.map { tx ->
                EtsyTransactionSummary(
                    transactionId = tx.transactionId,
                    listingId = tx.listingId,
                    title = tx.title,
                    quantity = tx.quantity,
                    sku = tx.sku,
                    variations = tx.variations.map { v ->
                        "${v.formattedName}: ${v.formattedValue}"
                    }
                )
            }
        ))

        if (isNew) {
            // Insert new order
            orderRepository.insertEtsyOrder(
                tenantId = store.tenantId,
                userId = store.userId ?: 0L,
                externalOrderId = externalOrderId,
                orderInfo = orderInfoJson,
                orderDetail = orderDetail,
                customerName = receipt.buyerName,
                customerEmail = receipt.buyerEmail,
                shippingAddress = shippingAddressJson,
                totalAmount = totalAmount,
                shippingAmount = shippingAmount,
                taxAmount = taxAmount,
                orderStatus = orderStatus,
                isGift = receipt.isGift,
                giftNote = receipt.giftMessage
            )
        } else {
            // Update existing order status if changed
            val currentOrder = existingOrder!!
            if (currentOrder.orderStatus != orderStatus) {
                orderRepository.updateStatus(currentOrder.id, store.tenantId, null, orderStatus, "Updated from Etsy sync")
            }
        }

        return isNew
    }

    // =====================================================
    // TRACKING UPDATES
    // =====================================================

    /**
     * Send tracking information to Etsy
     *
     * @param orderId PrintNest order ID
     * @param trackingCode Tracking number
     * @param carrier Carrier name
     * @return true if successful
     */
    suspend fun updateTracking(
        orderId: Long,
        trackingCode: String,
        carrier: String
    ): Result<Boolean> {
        logger.info("Updating tracking for order $orderId: $trackingCode ($carrier)")

        // Get order and extract Etsy receipt ID
        val order = orderRepository.findById(orderId)
            ?: return Result.failure(EtsyException("Order not found", 404))

        val externalOrderId = order.externalOrderId
            ?: return Result.failure(EtsyException("Order has no external ID", 400))

        if (!externalOrderId.startsWith("ETSY-")) {
            return Result.failure(EtsyException("Order is not from Etsy", 400))
        }

        val receiptId = externalOrderId.removePrefix("ETSY-").toLongOrNull()
            ?: return Result.failure(EtsyException("Invalid Etsy receipt ID", 400))

        // Find the Etsy store for this tenant
        val stores = storeRepository.findByTenantId(order.tenantId)
        val store = stores.firstOrNull { it.isActive }
            ?: return Result.failure(EtsyException("No active Etsy store found", 404))

        // Get valid access token
        val accessToken = ensureValidToken(store)
            ?: return Result.failure(EtsyException("Failed to get valid access token", 401))

        // Send tracking to Etsy
        val result = client.createShipment(
            accessToken = accessToken,
            shopId = store.shopId,
            receiptId = receiptId,
            trackingCode = trackingCode,
            carrierName = carrier
        )

        return if (result.isSuccess) {
            // Update order with tracking info
            orderRepository.updateTracking(orderId, trackingCode, null)
            logger.info("Successfully sent tracking to Etsy for order $orderId")
            Result.success(true)
        } else {
            logger.error("Failed to send tracking to Etsy", result.exceptionOrNull())
            Result.failure(result.exceptionOrNull() ?: EtsyException("Failed to update tracking", 500))
        }
    }

    // =====================================================
    // LISTING OPERATIONS
    // =====================================================

    /**
     * Get listings from an Etsy store
     *
     * @param storeId Internal store ID
     * @param state Listing state (active, inactive, sold_out, draft, expired)
     * @param limit Number of results per page
     * @param offset Pagination offset
     * @return Paginated list of listings
     */
    suspend fun getStoreListings(
        storeId: Long,
        state: String = "active",
        limit: Int = 25,
        offset: Int = 0
    ): Result<EtsyPaginatedResponse<EtsyListing>> {
        val store = storeRepository.findById(storeId)
            ?: return Result.failure(EtsyException("Store not found", 404))

        val accessToken = ensureValidToken(store)
            ?: return Result.failure(EtsyException("Failed to get valid access token", 401))

        return client.getShopListings(
            accessToken = accessToken,
            shopId = store.shopId,
            state = state,
            limit = limit,
            offset = offset
        )
    }

    /**
     * Get a single listing with images
     *
     * @param storeId Internal store ID
     * @param listingId Etsy listing ID
     * @return Listing with images
     */
    suspend fun getListing(storeId: Long, listingId: Long): Result<EtsyListing> {
        val store = storeRepository.findById(storeId)
            ?: return Result.failure(EtsyException("Store not found", 404))

        val accessToken = ensureValidToken(store)
            ?: return Result.failure(EtsyException("Failed to get valid access token", 401))

        return client.getListing(
            accessToken = accessToken,
            listingId = listingId,
            includes = listOf("images", "inventory")
        )
    }

    // =====================================================
    // TOKEN MANAGEMENT
    // =====================================================

    /**
     * Ensure we have a valid access token, refreshing if needed
     *
     * @param store Etsy store
     * @return Valid access token, or null if refresh failed
     */
    private suspend fun ensureValidToken(store: EtsyStore): String? {
        if (!authService.isTokenExpired(store.tokenExpiry)) {
            return store.accessToken
        }

        logger.info("Access token expired for store ${store.id}, refreshing...")

        val refreshResult = client.refreshToken(store.refreshToken)
        if (refreshResult.isFailure) {
            logger.error("Failed to refresh token for store ${store.id}", refreshResult.exceptionOrNull())
            return null
        }

        val tokens = refreshResult.getOrThrow()
        val tokenExpiry = authService.calculateTokenExpiry(tokens.expiresIn)

        // Update store with new tokens
        storeRepository.updateTokens(
            storeId = store.id,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            tokenExpiry = tokenExpiry
        )

        logger.info("Successfully refreshed token for store ${store.id}")
        return tokens.accessToken
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Format Unix timestamp to ISO string
     */
    private fun formatTimestamp(timestamp: Long?): String {
        return if (timestamp != null) {
            Instant.ofEpochSecond(timestamp).toString()
        } else {
            Instant.now().toString()
        }
    }
}

/**
 * Result of order synchronization
 */
data class OrderSyncResult(
    val storeId: Long,
    val totalSynced: Int,
    val newOrders: Int,
    val updatedOrders: Int
)

/**
 * Etsy order detail stored in order_detail column
 */
@kotlinx.serialization.Serializable
data class EtsyOrderDetail(
    val receiptId: Long,
    val buyerUserId: Long?,
    val sellerUserId: Long?,
    val paymentMethod: String?,
    val transactions: List<EtsyTransactionSummary>
)

/**
 * Summary of Etsy transaction for storage
 */
@kotlinx.serialization.Serializable
data class EtsyTransactionSummary(
    val transactionId: Long,
    val listingId: Long?,
    val title: String?,
    val quantity: Int,
    val sku: String?,
    val variations: List<String>
)
