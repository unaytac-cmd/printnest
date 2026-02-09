package com.printnest.integrations.tiktok

import com.printnest.domain.repository.TikTokStoreRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * TikTok Shop Integration Service
 *
 * Handles high-level business logic for TikTok Shop integration including
 * order synchronization, tracking updates, and store management.
 */
class TikTokService(
    private val client: TikTokClient,
    private val authService: TikTokAuthService,
    private val storeRepository: TikTokStoreRepository,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(TikTokService::class.java)

    /**
     * Sync orders from TikTok Shop for a specific store
     *
     * @param tenantId Tenant ID
     * @param storeId Internal store ID
     * @param status Order status to filter (default: AWAITING_SHIPMENT)
     * @return SyncResult with counts of synced and skipped orders
     */
    suspend fun syncOrders(
        tenantId: Long,
        storeId: String,
        status: TikTokOrderStatus = TikTokOrderStatus.AWAITING_SHIPMENT
    ): Result<TikTokSyncResult> {
        logger.info("Starting TikTok order sync for tenant $tenantId, store $storeId")

        // Get store from database
        val store = storeRepository.findByStoreId(tenantId, storeId)
            ?: return Result.failure(TikTokServiceException("Store not found: $storeId"))

        if (store.shopCipher.isNullOrBlank()) {
            return Result.failure(TikTokServiceException("Store not connected. Shop cipher is missing."))
        }

        // Get valid access token
        val accessTokenResult = authService.getValidAccessToken(store) { newTokenData ->
            // Update tokens in database
            storeRepository.updateTokens(
                tenantId = tenantId,
                storeId = storeId,
                accessToken = newTokenData.accessToken,
                refreshToken = newTokenData.refreshToken,
                accessTokenExpireAt = Instant.now().epochSecond + newTokenData.accessTokenExpireIn,
                refreshTokenExpireAt = Instant.now().epochSecond + newTokenData.refreshTokenExpireIn
            )
        }

        val accessToken = accessTokenResult.getOrElse { error ->
            return Result.failure(error)
        }

        // Fetch orders with pagination
        var syncedCount = 0
        var skippedCount = 0
        val errors = mutableListOf<String>()
        var pageToken: String? = null
        var fetchedCount = 0
        val maxOrders = 1000

        do {
            val ordersResult = client.getOrders(
                shopCipher = store.shopCipher,
                accessToken = accessToken,
                status = status,
                pageToken = pageToken
            )

            if (ordersResult.isFailure) {
                val error = ordersResult.exceptionOrNull()
                logger.error("Failed to fetch orders: ${error?.message}")
                errors.add("Failed to fetch orders: ${error?.message}")
                break
            }

            val ordersResponse = ordersResult.getOrThrow()

            if (ordersResponse.code != 0) {
                errors.add("API error: ${ordersResponse.message}")
                break
            }

            val ordersData = ordersResponse.data ?: break
            val orders = ordersData.orders

            fetchedCount += orders.size
            logger.info("Fetched ${orders.size} orders (total: $fetchedCount)")

            for (order in orders) {
                try {
                    val processed = processOrder(tenantId, store, order)
                    if (processed) {
                        syncedCount++
                    } else {
                        skippedCount++
                    }
                } catch (e: Exception) {
                    logger.error("Error processing order ${order.id}: ${e.message}")
                    errors.add("Order ${order.id}: ${e.message}")
                    skippedCount++
                }
            }

            // Update last sync time
            storeRepository.updateLastSyncAt(tenantId, storeId)

            pageToken = ordersData.nextPageToken

        } while (!pageToken.isNullOrBlank() && fetchedCount < maxOrders)

        logger.info("TikTok sync complete: synced=$syncedCount, skipped=$skippedCount")

        return Result.success(TikTokSyncResult(
            ordersSynced = syncedCount,
            ordersSkipped = skippedCount,
            errors = errors
        ))
    }

    /**
     * Process a single TikTok order and convert to PrintNest format
     *
     * @param tenantId Tenant ID
     * @param store TikTok store
     * @param tiktokOrder Order from TikTok API
     * @return true if order was processed, false if skipped
     */
    private suspend fun processOrder(
        tenantId: Long,
        store: TikTokStore,
        tiktokOrder: TikTokOrder
    ): Boolean {
        val orderId = tiktokOrder.id

        // Check if order already exists
        if (storeRepository.orderExists(tenantId, orderId)) {
            logger.debug("Order $orderId already exists, skipping")
            return false
        }

        // Extract address information
        val address = tiktokOrder.recipientAddress
        val addressInfo = extractAddressInfo(address?.districtInfo ?: emptyList())

        // Build order info JSONB
        val orderInfo = buildOrderInfo(tiktokOrder, addressInfo)

        // Build order detail JSONB
        val orderDetail = buildOrderDetail(tiktokOrder, address?.name ?: "", address?.fullAddress ?: "")

        // Insert order
        storeRepository.insertOrder(
            tenantId = tenantId,
            userId = store.userId,
            storeId = store.id,
            intOrderId = orderId,
            orderType = 0, // Standard order
            orderStatus = 0, // New order
            orderInfo = json.encodeToString(orderInfo),
            orderDetail = json.encodeToString(orderDetail),
            orderMapStatus = 0 // Not mapped
        )

        // Insert order products
        for (item in tiktokOrder.lineItems) {
            storeRepository.insertOrderProduct(
                tenantId = tenantId,
                orderId = orderId,
                productDetail = json.encodeToString(buildProductDetail(item))
            )
        }

        logger.info("Inserted TikTok order: $orderId")
        return true
    }

    /**
     * Update tracking information for a TikTok order
     *
     * @param tenantId Tenant ID
     * @param storeId Internal store ID
     * @param orderId TikTok order ID
     * @param trackingNumber Carrier tracking number
     * @param carrier Carrier name (e.g., "USPS", "UPS", "FEDEX")
     * @param deliveryOptionId TikTok delivery option ID from order
     * @return Result indicating success or failure
     */
    suspend fun updateTracking(
        tenantId: Long,
        storeId: String,
        orderId: String,
        trackingNumber: String,
        carrier: String,
        deliveryOptionId: String
    ): Result<Boolean> {
        logger.info("Updating tracking for TikTok order $orderId: $trackingNumber ($carrier)")

        // Get store from database
        val store = storeRepository.findByStoreId(tenantId, storeId)
            ?: return Result.failure(TikTokServiceException("Store not found: $storeId"))

        if (store.shopCipher.isNullOrBlank()) {
            return Result.failure(TikTokServiceException("Store not connected"))
        }

        // Get valid access token
        val accessTokenResult = authService.getValidAccessToken(store) { newTokenData ->
            storeRepository.updateTokens(
                tenantId = tenantId,
                storeId = storeId,
                accessToken = newTokenData.accessToken,
                refreshToken = newTokenData.refreshToken,
                accessTokenExpireAt = Instant.now().epochSecond + newTokenData.accessTokenExpireIn,
                refreshTokenExpireAt = Instant.now().epochSecond + newTokenData.refreshTokenExpireIn
            )
        }

        val accessToken = accessTokenResult.getOrElse { error ->
            return Result.failure(error)
        }

        // Get shipping providers to find the matching provider ID
        val providersResult = client.getShippingProviders(
            shopCipher = store.shopCipher,
            accessToken = accessToken,
            deliveryOptionId = deliveryOptionId
        )

        val providersResponse = providersResult.getOrElse { error ->
            return Result.failure(TikTokServiceException("Failed to get shipping providers: ${error.message}"))
        }

        if (providersResponse.code != 0) {
            return Result.failure(TikTokServiceException("API error: ${providersResponse.message}"))
        }

        val providers = providersResponse.data?.shippingProviders ?: emptyList()
        val shippingProviderId = findShippingProviderId(providers, carrier)
            ?: return Result.failure(TikTokServiceException("Carrier not found: $carrier"))

        // Update shipping info
        val updateResult = client.updateShipment(
            shopCipher = store.shopCipher,
            accessToken = accessToken,
            orderId = orderId,
            trackingNumber = trackingNumber,
            shippingProviderId = shippingProviderId
        )

        return updateResult.fold(
            onSuccess = { response ->
                if (response.code == 0) {
                    logger.info("Successfully updated tracking for order $orderId")
                    Result.success(true)
                } else {
                    Result.failure(TikTokServiceException("Failed to update tracking: ${response.message}"))
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Get shipping options for a store
     *
     * @param tenantId Tenant ID
     * @param storeId Internal store ID
     * @param deliveryOptionId TikTok delivery option ID
     * @return Result containing list of shipping providers
     */
    suspend fun getShippingOptions(
        tenantId: Long,
        storeId: String,
        deliveryOptionId: String
    ): Result<List<TikTokShippingProvider>> {
        val store = storeRepository.findByStoreId(tenantId, storeId)
            ?: return Result.failure(TikTokServiceException("Store not found: $storeId"))

        if (store.shopCipher.isNullOrBlank()) {
            return Result.failure(TikTokServiceException("Store not connected"))
        }

        val accessTokenResult = authService.getValidAccessToken(store) { newTokenData ->
            storeRepository.updateTokens(
                tenantId = tenantId,
                storeId = storeId,
                accessToken = newTokenData.accessToken,
                refreshToken = newTokenData.refreshToken,
                accessTokenExpireAt = Instant.now().epochSecond + newTokenData.accessTokenExpireIn,
                refreshTokenExpireAt = Instant.now().epochSecond + newTokenData.refreshTokenExpireIn
            )
        }

        val accessToken = accessTokenResult.getOrElse { error ->
            return Result.failure(error)
        }

        val result = client.getShippingProviders(
            shopCipher = store.shopCipher,
            accessToken = accessToken,
            deliveryOptionId = deliveryOptionId
        )

        return result.fold(
            onSuccess = { response ->
                if (response.code == 0 && response.data != null) {
                    Result.success(response.data.shippingProviders)
                } else {
                    Result.failure(TikTokServiceException("Failed to get shipping options: ${response.message}"))
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Get all TikTok stores for a tenant
     */
    fun getStores(tenantId: Long): List<TikTokStore> {
        return storeRepository.findByTenantId(tenantId)
    }

    /**
     * Get active TikTok stores for a tenant
     */
    fun getActiveStores(tenantId: Long): List<TikTokStore> {
        return storeRepository.findActiveByTenantId(tenantId)
    }

    /**
     * Connect a TikTok store after OAuth callback
     */
    suspend fun connectStore(
        tenantId: Long,
        userId: Long,
        storeId: String,
        tokenData: TikTokTokenData
    ): Result<TikTokStore> {
        // Get authorized shops
        val shopsResult = authService.getAuthorizedShops(tokenData.accessToken)

        val shop = shopsResult.getOrElse { error ->
            return Result.failure(error)
        }.firstOrNull()
            ?: return Result.failure(TikTokServiceException("No authorized shops found"))

        val now = Instant.now().epochSecond

        // Upsert store in database
        val store = storeRepository.upsert(
            tenantId = tenantId,
            userId = userId,
            storeId = storeId,
            shopId = shop.id,
            shopName = shop.name,
            shopCipher = shop.cipher,
            accessToken = tokenData.accessToken,
            refreshToken = tokenData.refreshToken,
            accessTokenExpireAt = now + tokenData.accessTokenExpireIn,
            refreshTokenExpireAt = now + tokenData.refreshTokenExpireIn,
            region = shop.region
        )

        logger.info("Connected TikTok store: ${shop.name} (${shop.id})")
        return Result.success(store)
    }

    /**
     * Disconnect a TikTok store
     */
    fun disconnectStore(tenantId: Long, storeId: String): Boolean {
        return storeRepository.disconnect(tenantId, storeId)
    }

    /**
     * Check connection status for stores
     */
    suspend fun checkConnectionStatus(
        tenantId: Long,
        storeIds: List<String>
    ): Map<String, Boolean> {
        val statusMap = mutableMapOf<String, Boolean>()

        for (storeId in storeIds) {
            val store = storeRepository.findByStoreId(tenantId, storeId)
            if (store == null) {
                statusMap[storeId] = false
                continue
            }

            // Try to get valid access token
            val result = authService.getValidAccessToken(store) { newTokenData ->
                storeRepository.updateTokens(
                    tenantId = tenantId,
                    storeId = storeId,
                    accessToken = newTokenData.accessToken,
                    refreshToken = newTokenData.refreshToken,
                    accessTokenExpireAt = Instant.now().epochSecond + newTokenData.accessTokenExpireIn,
                    refreshTokenExpireAt = Instant.now().epochSecond + newTokenData.refreshTokenExpireIn
                )
            }

            statusMap[storeId] = result.isSuccess
        }

        return statusMap
    }

    // =====================================================
    // HELPER FUNCTIONS
    // =====================================================

    private fun extractAddressInfo(districtInfo: List<TikTokDistrictInfo>): Map<String, String?> {
        val addressMap = mapOf(
            "L0" to "country",
            "L1" to "state",
            "L2" to "county",
            "L3" to "city"
        )

        val result = mutableMapOf<String, String?>()
        addressMap.values.forEach { result[it] = null }

        for (item in districtInfo) {
            val level = item.addressLevel
            val name = item.addressName
            if (level != null && addressMap.containsKey(level)) {
                result[addressMap[level]!!] = name
            }
        }

        return result
    }

    private fun buildOrderInfo(order: TikTokOrder, addressInfo: Map<String, String?>): Map<String, Any?> {
        val address = order.recipientAddress

        return mapOf(
            "to_address" to mapOf(
                "zip" to address?.postalCode,
                "name" to address?.name,
                "country_iso" to address?.regionCode,
                "street1" to address?.addressDetail,
                "street2" to "",
                "city" to addressInfo["city"],
                "state" to addressInfo["state"],
                "country" to addressInfo["country"],
                "formatted_address" to address?.fullAddress,
                "company" to "",
                "phone" to address?.phoneNumber,
                "verify" to false
            ),
            "delivery_option_id" to order.deliveryOptionId,
            "marketplace" to "tiktok",
            "buyer_message" to order.buyerMessage
        )
    }

    private fun buildOrderDetail(
        order: TikTokOrder,
        recipientName: String,
        formattedAddress: String
    ): Map<String, Any?> {
        val transactions = order.lineItems.map { item ->
            mapOf(
                "title" to item.productName,
                "sku" to item.skuId,
                "quantity" to item.quantity,
                "transaction_id" to item.productId,
                "image_url" to item.skuImage?.url,
                "sale_price" to item.salePrice,
                "original_price" to item.originalPrice
            )
        }

        return mapOf(
            "name" to recipientName,
            "formatted_address" to formattedAddress,
            "transactions" to transactions,
            "marketplace_shipping_fee" to order.payment?.shippingFee,
            "created_at" to order.createTime,
            "update_timestamp" to order.updateTime
        )
    }

    private fun buildProductDetail(item: TikTokOrderItem): Map<String, Any?> {
        return mapOf(
            "product_id" to item.productId,
            "product_name" to item.productName,
            "sku_id" to item.skuId,
            "sku_name" to item.skuName,
            "quantity" to item.quantity,
            "sale_price" to item.salePrice,
            "original_price" to item.originalPrice,
            "image_url" to item.skuImage?.url
        )
    }

    private fun findShippingProviderId(
        providers: List<TikTokShippingProvider>,
        carrierName: String
    ): String? {
        // Try exact match first (case-insensitive)
        val exactMatch = providers.find {
            it.name.equals(carrierName, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch.id

        // Try partial match
        val partialMatch = providers.find {
            it.name.contains(carrierName, ignoreCase = true) ||
            carrierName.contains(it.name, ignoreCase = true)
        }
        return partialMatch?.id
    }
}

/**
 * Sync result data class
 */
data class TikTokSyncResult(
    val ordersSynced: Int,
    val ordersSkipped: Int,
    val errors: List<String>
)

/**
 * TikTok Service Exception
 */
class TikTokServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
