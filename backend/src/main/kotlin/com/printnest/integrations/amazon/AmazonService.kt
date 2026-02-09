package com.printnest.integrations.amazon

import com.printnest.domain.models.Address
import com.printnest.domain.models.OrderStatus
import com.printnest.domain.repository.OrderRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Amazon Service
 * Business logic for Amazon integration - order syncing, processing, and tracking updates
 */
class AmazonService(
    private val amazonClient: AmazonClient,
    private val amazonStoreRepository: AmazonStoreRepository,
    private val orderRepository: OrderRepository,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(AmazonService::class.java)

    /**
     * Sync orders from Amazon for a specific store
     * Fetches unshipped orders and inserts new ones into PrintNest
     */
    suspend fun syncOrders(
        tenantId: Long,
        storeId: Long
    ): Result<AmazonSyncResult> {
        logger.info("Starting Amazon order sync for tenant $tenantId, store $storeId")

        // Get store credentials
        val store = amazonStoreRepository.findByStoreId(tenantId, storeId)
            ?: return Result.failure(AmazonServiceException("Amazon store not found: $storeId"))

        if (!store.isActive) {
            return Result.failure(AmazonServiceException("Amazon store is not active: $storeId"))
        }

        val errors = mutableListOf<String>()
        var ordersProcessed = 0
        var ordersInserted = 0
        var ordersSkipped = 0

        try {
            // Fetch unshipped orders
            val ordersResult = amazonClient.getUnshippedOrders(
                storeId = store.storeId,
                refreshToken = store.refreshToken,
                marketplaceIds = listOf(store.marketplaceId)
            )

            if (ordersResult.isFailure) {
                return Result.failure(ordersResult.exceptionOrNull()!!)
            }

            val orders = ordersResult.getOrThrow()
            logger.info("Fetched ${orders.size} unshipped orders from Amazon for store $storeId")

            for (order in orders) {
                ordersProcessed++

                try {
                    // Check if order already exists
                    val existingOrder = orderRepository.findByIntOrderId(tenantId, order.amazonOrderId)
                    if (existingOrder != null) {
                        logger.debug("Order ${order.amazonOrderId} already exists, skipping")
                        ordersSkipped++
                        continue
                    }

                    // Process and insert the order
                    val insertResult = processAndInsertOrder(tenantId, store, order)
                    if (insertResult.isSuccess) {
                        ordersInserted++
                        logger.info("Inserted Amazon order: ${order.amazonOrderId}")
                    } else {
                        val errorMsg = "Failed to insert order ${order.amazonOrderId}: ${insertResult.exceptionOrNull()?.message}"
                        errors.add(errorMsg)
                        logger.error(errorMsg)
                    }

                    // Rate limiting between order processing
                    delay(AmazonConstants.RATE_LIMIT_DELAY_MS)

                } catch (e: Exception) {
                    val errorMsg = "Error processing order ${order.amazonOrderId}: ${e.message}"
                    errors.add(errorMsg)
                    logger.error(errorMsg, e)
                }
            }

            // Update store's last sync time
            amazonStoreRepository.updateLastSyncTime(store.id)

            logger.info("Amazon sync completed for store $storeId: processed=$ordersProcessed, inserted=$ordersInserted, skipped=$ordersSkipped")

            return Result.success(
                AmazonSyncResult(
                    success = errors.isEmpty(),
                    ordersProcessed = ordersProcessed,
                    ordersInserted = ordersInserted,
                    ordersSkipped = ordersSkipped,
                    errors = errors
                )
            )

        } catch (e: Exception) {
            logger.error("Amazon sync failed for store $storeId", e)
            return Result.failure(AmazonServiceException("Sync failed: ${e.message}", e))
        }
    }

    /**
     * Process a single Amazon order and insert into PrintNest
     */
    private suspend fun processAndInsertOrder(
        tenantId: Long,
        store: AmazonStore,
        order: AmazonOrder
    ): Result<Long> {
        val amazonOrderId = order.amazonOrderId

        try {
            // Fetch order items
            val orderItemsResult = amazonClient.getOrderItems(
                storeId = store.storeId,
                refreshToken = store.refreshToken,
                orderId = amazonOrderId
            )

            if (orderItemsResult.isFailure) {
                return Result.failure(orderItemsResult.exceptionOrNull()!!)
            }
            val orderItems = orderItemsResult.getOrThrow().payload?.orderItems ?: emptyList()

            if (orderItems.isEmpty()) {
                return Result.failure(AmazonServiceException("No order items found for order $amazonOrderId"))
            }

            // Fetch shipping address
            val addressResult = amazonClient.getOrderAddress(
                storeId = store.storeId,
                refreshToken = store.refreshToken,
                orderId = amazonOrderId
            )

            val shippingAddress = if (addressResult.isSuccess) {
                addressResult.getOrThrow().payload?.shippingAddress
            } else {
                // Use address from order if available
                order.shippingAddress
            }

            if (shippingAddress == null) {
                return Result.failure(AmazonServiceException("No shipping address found for order $amazonOrderId"))
            }

            // Fetch buyer customization info (for personalized products)
            val buyerInfoResult = amazonClient.getOrderItemsBuyerInfo(
                storeId = store.storeId,
                refreshToken = store.refreshToken,
                orderId = amazonOrderId
            )

            val orderItemsBuyerInfo = if (buyerInfoResult.isSuccess) {
                buyerInfoResult.getOrThrow().payload?.orderItems ?: emptyList()
            } else {
                emptyList()
            }

            // Convert to PrintNest order format
            val printNestOrder = convertToPrintNestOrder(
                tenantId = tenantId,
                store = store,
                amazonOrder = order,
                orderItems = orderItems,
                shippingAddress = shippingAddress,
                orderItemsBuyerInfo = orderItemsBuyerInfo
            )

            // Insert order into database
            val orderId = orderRepository.insert(printNestOrder)

            // Insert order products
            for (item in orderItems) {
                val buyerInfo = orderItemsBuyerInfo.find { it.orderItemId == item.orderItemId }
                insertOrderProduct(tenantId, orderId, item, buyerInfo)
            }

            return Result.success(orderId)

        } catch (e: Exception) {
            logger.error("Failed to process order $amazonOrderId", e)
            return Result.failure(AmazonServiceException("Failed to process order: ${e.message}", e))
        }
    }

    /**
     * Convert Amazon order to PrintNest order format
     */
    private fun convertToPrintNestOrder(
        tenantId: Long,
        store: AmazonStore,
        amazonOrder: AmazonOrder,
        orderItems: List<AmazonOrderItem>,
        shippingAddress: AmazonAddress,
        orderItemsBuyerInfo: List<AmazonOrderItemBuyerInfoItem>
    ): PrintNestOrderData {
        // Build order detail (raw marketplace data)
        val orderDetail = buildOrderDetail(amazonOrder, orderItems, shippingAddress, orderItemsBuyerInfo)

        // Build order info (structured data for processing)
        val orderInfo = buildOrderInfo(shippingAddress)

        // Format address for display
        val formattedAddress = formatAddress(shippingAddress)

        return PrintNestOrderData(
            tenantId = tenantId,
            userId = 0, // Will be set based on store assignment
            storeId = store.storeId,
            intOrderId = amazonOrder.amazonOrderId,
            externalOrderId = amazonOrder.sellerOrderId ?: amazonOrder.amazonOrderId,
            orderType = 0, // Standard order
            orderStatus = OrderStatus.NEW_ORDER.code,
            orderMapStatus = 0, // Unmapped
            orderDetail = json.encodeToString(orderDetail),
            orderInfo = json.encodeToString(orderInfo),
            customerEmail = amazonOrder.buyerInfo?.buyerEmail,
            customerName = shippingAddress.name ?: amazonOrder.buyerInfo?.buyerName,
            shippingAddress = json.encodeToString(
                Address(
                    name = shippingAddress.name,
                    street1 = shippingAddress.addressLine1,
                    street2 = shippingAddress.addressLine2,
                    city = shippingAddress.city,
                    state = shippingAddress.stateOrRegion,
                    postalCode = shippingAddress.postalCode,
                    country = shippingAddress.countryCode,
                    phone = shippingAddress.phone
                )
            ),
            totalAmount = parseMoneyAmount(amazonOrder.orderTotal?.amount),
            marketplace = "amazon",
            marketplaceOrderId = amazonOrder.amazonOrderId
        )
    }

    /**
     * Build order detail JSON for storage
     */
    private fun buildOrderDetail(
        order: AmazonOrder,
        orderItems: List<AmazonOrderItem>,
        shippingAddress: AmazonAddress,
        orderItemsBuyerInfo: List<AmazonOrderItemBuyerInfoItem>
    ): Map<String, Any?> {
        val buyerInfoMap = orderItemsBuyerInfo.associateBy { it.orderItemId }

        val transactions = orderItems.map { item ->
            val buyerInfo = buyerInfoMap[item.orderItemId]
            mutableMapOf<String, Any?>(
                "title" to item.title,
                "sku" to item.sellerSku,
                "quantity" to item.quantityOrdered,
                "transaction_id" to item.orderItemId,
                "asin" to item.asin,
                "variations" to listOf(
                    mapOf(
                        "formatted_name" to "Size",
                        "formatted_value" to ""
                    )
                ),
                "total_shipping_cost" to mapOf(
                    "amount" to parseMoneyAmount(item.shippingPrice?.amount)
                )
            ).also { map ->
                buyerInfo?.buyerCustomizedInfo?.customizedUrl?.let {
                    map["customized_url"] = it
                }
            }
        }

        return mapOf(
            "name" to shippingAddress.name,
            "formatted_address" to formatAddress(shippingAddress),
            "transactions" to transactions,
            "created_at" to order.purchaseDate,
            "update_timestamp" to (order.lastUpdateDate ?: order.purchaseDate)
        )
    }

    /**
     * Build order info JSON for processing
     */
    private fun buildOrderInfo(shippingAddress: AmazonAddress): Map<String, Any?> {
        return mapOf(
            "to_address" to mapOf(
                "zip" to shippingAddress.postalCode,
                "name" to shippingAddress.name,
                "country_iso" to shippingAddress.countryCode,
                "street1" to shippingAddress.addressLine1,
                "street2" to (shippingAddress.addressLine2 ?: ""),
                "city" to shippingAddress.city,
                "state" to shippingAddress.stateOrRegion,
                "state_iso" to shippingAddress.stateOrRegion,
                "country" to shippingAddress.countryCode,
                "formatted_address" to formatAddress(shippingAddress),
                "company" to "",
                "verify" to false,
                "phone" to shippingAddress.phone
            )
        )
    }

    /**
     * Format Amazon address to display string
     */
    private fun formatAddress(address: AmazonAddress): String {
        val parts = listOfNotNull(
            address.name,
            address.addressLine1,
            address.addressLine2,
            listOfNotNull(
                address.city,
                address.stateOrRegion,
                address.postalCode
            ).joinToString(", "),
            address.countryCode
        )
        return parts.joinToString("\n")
    }

    /**
     * Insert order product from Amazon order item
     */
    private fun insertOrderProduct(
        tenantId: Long,
        orderId: Long,
        item: AmazonOrderItem,
        buyerInfo: AmazonOrderItemBuyerInfoItem?
    ) {
        val productDetail = mapOf(
            "title" to item.title,
            "sku" to item.sellerSku,
            "asin" to item.asin,
            "quantity" to item.quantityOrdered,
            "transaction_id" to item.orderItemId
        )

        orderRepository.insertOrderProduct(
            tenantId = tenantId,
            orderId = orderId,
            listingId = item.orderItemId,
            quantity = item.quantityOrdered,
            unitPrice = parseMoneyAmount(item.itemPrice?.amount),
            productDetail = json.encodeToString(productDetail),
            listingImageUrl = buyerInfo?.buyerCustomizedInfo?.customizedUrl
        )
    }

    /**
     * Update tracking information for an order
     */
    suspend fun updateTracking(
        tenantId: Long,
        orderId: Long,
        trackingNumber: String,
        carrier: String,
        carrierName: String? = null,
        shippingMethod: String? = null
    ): Result<Unit> {
        logger.info("Updating tracking for order $orderId: tracking=$trackingNumber, carrier=$carrier")

        // Get order details
        val order = orderRepository.findById(tenantId, orderId)
            ?: return Result.failure(AmazonServiceException("Order not found: $orderId"))

        val intOrderId = order.intOrderId
            ?: return Result.failure(AmazonServiceException("Order has no Amazon order ID: $orderId"))

        val storeId = order.storeId
            ?: return Result.failure(AmazonServiceException("Order has no store ID: $orderId"))

        // Get store credentials
        val store = amazonStoreRepository.findByStoreId(tenantId, storeId)
            ?: return Result.failure(AmazonServiceException("Amazon store not found: $storeId"))

        // Get order items to include in shipment confirmation
        val orderProducts = orderRepository.findOrderProducts(tenantId, orderId)
        val orderItems = orderProducts.map { product ->
            AmazonConfirmShipmentOrderItem(
                orderItemId = product.listingId ?: "",
                quantity = product.quantity
            )
        }

        if (orderItems.none()) {
            return Result.failure(AmazonServiceException("No order items found for order $orderId"))
        }

        // Build tracking info
        val trackingInfo = AmazonTrackingInfo(
            orderId = intOrderId,
            trackingNumber = trackingNumber,
            carrierCode = carrier.uppercase(),
            carrierName = carrierName,
            shippingMethod = shippingMethod,
            shipDate = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            orderItems = orderItems
        )

        // Send to Amazon
        val result = amazonClient.confirmShipment(
            storeId = store.storeId,
            refreshToken = store.refreshToken,
            orderId = intOrderId,
            trackingInfo = trackingInfo
        )

        if (result.isSuccess) {
            // Update order in database
            orderRepository.updateTracking(
                tenantId = tenantId,
                orderId = orderId,
                trackingNumber = trackingNumber,
                trackingUrl = null,
                carrier = carrier
            )
            orderRepository.updateStatus(tenantId, orderId, OrderStatus.SHIPPED.code)
            logger.info("Successfully updated tracking for Amazon order $intOrderId")
        }

        return result
    }

    /**
     * Acknowledge order (mark as accepted)
     * Currently a no-op for Amazon as orders are automatically acknowledged
     */
    suspend fun acknowledgeOrder(
        tenantId: Long,
        orderId: Long
    ): Result<Unit> {
        logger.info("Acknowledging order $orderId (no-op for Amazon)")
        // Amazon doesn't require explicit acknowledgment
        return Result.success(Unit)
    }

    /**
     * Get all Amazon stores for a tenant
     */
    fun getStores(tenantId: Long): List<AmazonStoreResponse> {
        return amazonStoreRepository.findByTenantId(tenantId).map { store ->
            AmazonStoreResponse(
                id = store.id,
                storeId = store.storeId,
                sellerId = store.sellerId,
                marketplaceId = store.marketplaceId,
                storeName = store.storeName,
                isActive = store.isActive,
                isConnected = store.refreshToken.isNotBlank(),
                lastSyncAt = store.lastSyncAt
            )
        }
    }

    /**
     * Disconnect an Amazon store
     */
    fun disconnectStore(tenantId: Long, storeId: Long): Result<AmazonDisconnectResponse> {
        val store = amazonStoreRepository.findByStoreId(tenantId, storeId)
            ?: return Result.failure(AmazonServiceException("Store not found: $storeId"))

        amazonStoreRepository.deactivate(store.id)

        return Result.success(
            AmazonDisconnectResponse(
                success = true,
                message = "Amazon store disconnected successfully"
            )
        )
    }

    /**
     * Save or update Amazon store credentials after OAuth
     */
    fun saveStoreCredentials(
        tenantId: Long,
        storeId: Long,
        sellerId: String,
        marketplaceId: String,
        refreshToken: String,
        storeName: String? = null
    ): Long {
        return amazonStoreRepository.upsert(
            tenantId = tenantId,
            storeId = storeId,
            sellerId = sellerId,
            marketplaceId = marketplaceId,
            refreshToken = refreshToken,
            storeName = storeName
        )
    }

    /**
     * Parse money amount string to BigDecimal
     */
    private fun parseMoneyAmount(amount: String?): java.math.BigDecimal {
        return try {
            amount?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        } catch (e: Exception) {
            java.math.BigDecimal.ZERO
        }
    }
}

/**
 * Internal data class for PrintNest order creation
 */
data class PrintNestOrderData(
    val tenantId: Long,
    val userId: Long,
    val storeId: Long,
    val intOrderId: String,
    val externalOrderId: String,
    val orderType: Int,
    val orderStatus: Int,
    val orderMapStatus: Int,
    val orderDetail: String,
    val orderInfo: String,
    val customerEmail: String?,
    val customerName: String?,
    val shippingAddress: String,
    val totalAmount: java.math.BigDecimal,
    val marketplace: String,
    val marketplaceOrderId: String
)

/**
 * Exception for Amazon service errors
 */
class AmazonServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
