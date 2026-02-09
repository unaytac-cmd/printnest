package com.printnest.integrations.walmart

import com.printnest.domain.repository.OrderRepository
import com.printnest.integrations.redis.RedisService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Walmart Integration Service
 *
 * High-level business logic for Walmart marketplace integration:
 * - Order synchronization
 * - Order processing and conversion
 * - Bulk acknowledgment
 * - Tracking updates
 */
class WalmartService(
    private val walmartClient: WalmartClient,
    private val authService: WalmartAuthService,
    private val orderRepository: OrderRepository,
    private val redisService: RedisService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(WalmartService::class.java)

    companion object {
        const val MAX_ORDERS_PER_SYNC = 1000
        const val SYNC_LOCK_PREFIX = "walmart_sync_lock_"
        const val SYNC_LOCK_TTL_SECONDS = 300 // 5 minutes
    }

    /**
     * Sync orders from Walmart for a specific store
     *
     * @param tenantId Tenant ID
     * @param storeId PrintNest store ID
     * @param clientId Walmart client ID
     * @param clientSecret Walmart client secret
     * @param status Order status filter (defaults to Created,Acknowledged)
     * @param createdStartDate Start date for filtering
     * @return SyncResult with statistics
     */
    suspend fun syncOrders(
        tenantId: Long,
        storeId: Long,
        clientId: String,
        clientSecret: String,
        status: String = "${WalmartOrderStatus.CREATED.value},${WalmartOrderStatus.ACKNOWLEDGED.value}",
        createdStartDate: String? = null
    ): Result<WalmartSyncResult> {
        val lockKey = "$SYNC_LOCK_PREFIX$storeId"

        // Try to acquire sync lock
        val lockAcquired = try {
            redisService.setNx(lockKey, Instant.now().toString())
        } catch (e: Exception) {
            logger.warn("Failed to acquire sync lock for store $storeId: ${e.message}")
            true // Proceed anyway if Redis is unavailable
        }

        if (!lockAcquired) {
            logger.info("Sync already in progress for store $storeId")
            return Result.failure(WalmartSyncException("Sync already in progress"))
        }

        try {
            // Set lock expiry
            try {
                redisService.expire(lockKey, SYNC_LOCK_TTL_SECONDS.toLong())
            } catch (e: Exception) {
                logger.warn("Failed to set sync lock expiry: ${e.message}")
            }

            logger.info("Starting Walmart order sync for tenant $tenantId, store $storeId")

            var fetchedCount = 0
            var insertedCount = 0
            var skippedCount = 0
            var cursor: String? = null
            val errors = mutableListOf<String>()

            while (fetchedCount < MAX_ORDERS_PER_SYNC) {
                val ordersResult = walmartClient.getOrders(
                    storeId = storeId,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    status = status,
                    createdStartDate = createdStartDate,
                    cursor = cursor,
                    productInfo = true
                )

                if (ordersResult.isFailure) {
                    val error = ordersResult.exceptionOrNull()!!
                    logger.error("Failed to fetch Walmart orders: ${error.message}")
                    if (fetchedCount == 0) {
                        return Result.failure(error)
                    }
                    break
                }

                val response = ordersResult.getOrThrow()

                val orders = response.list?.elements?.order ?: emptyList()
                if (orders.isEmpty()) {
                    logger.info("No more Walmart orders to sync for store $storeId")
                    break
                }

                fetchedCount += orders.size
                logger.info("Fetched ${orders.size} Walmart orders (total: $fetchedCount)")

                // Process each order
                for (order in orders) {
                    try {
                        val result = processOrder(tenantId, storeId, order, clientId, clientSecret)
                        if (result.isSuccess) {
                            if (result.getOrNull() == true) {
                                insertedCount++
                            } else {
                                skippedCount++
                            }
                        } else {
                            errors.add("Order ${order.customerOrderId}: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        logger.error("Error processing Walmart order ${order.customerOrderId}", e)
                        errors.add("Order ${order.customerOrderId}: ${e.message}")
                    }
                }

                // Check for next page
                cursor = response.list?.meta?.nextCursor
                if (cursor == null) {
                    logger.info("No more pages, sync complete")
                    break
                }
            }

            val syncResult = WalmartSyncResult(
                storeId = storeId,
                totalFetched = fetchedCount,
                inserted = insertedCount,
                skipped = skippedCount,
                errors = errors,
                syncedAt = Instant.now().toString()
            )

            logger.info("Walmart sync complete for store $storeId: $syncResult")
            return Result.success(syncResult)

        } finally {
            // Release sync lock
            try {
                redisService.delete(lockKey)
            } catch (e: Exception) {
                logger.warn("Failed to release sync lock: ${e.message}")
            }
        }
    }

    /**
     * Process a single Walmart order and convert to PrintNest format
     *
     * @return true if inserted, false if skipped (already exists)
     */
    suspend fun processOrder(
        tenantId: Long,
        storeId: Long,
        walmartOrder: WalmartOrder,
        clientId: String,
        clientSecret: String
    ): Result<Boolean> {
        val intOrderId = walmartOrder.customerOrderId

        // Check if order already exists
        val existingOrder = orderRepository.findByIntOrderIdAndStoreId(intOrderId, storeId)
        if (existingOrder != null) {
            logger.debug("Walmart order $intOrderId already exists for store $storeId, skipping")
            return Result.success(false)
        }

        // Fetch item details for each order line
        val orderLines = walmartOrder.orderLines.orderLine
        val enrichedLines = mutableListOf<EnrichedOrderLine>()

        for (orderLine in orderLines) {
            val sku = orderLine.item.sku
            val itemDetailsResult = walmartClient.getItemDetails(storeId, clientId, clientSecret, sku)

            val itemDetails = itemDetailsResult.getOrElse {
                logger.warn("Failed to fetch item details for SKU $sku: ${it.message}")
                null
            }

            enrichedLines.add(EnrichedOrderLine(orderLine, itemDetails))
        }

        // Convert to PrintNest order format
        val orderInfo = buildOrderInfo(walmartOrder)
        val orderDetail = buildOrderDetail(walmartOrder, enrichedLines)

        // Insert order
        try {
            orderRepository.insertWalmartOrder(
                tenantId = tenantId,
                storeId = storeId,
                intOrderId = intOrderId,
                purchaseOrderId = walmartOrder.purchaseOrderId,
                orderInfo = json.encodeToString(orderInfo),
                orderDetail = json.encodeToString(orderDetail),
                customerName = walmartOrder.shippingInfo.postalAddress.name,
                orderDate = walmartOrder.orderDate
            )

            logger.info("Inserted Walmart order $intOrderId for store $storeId")
            return Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to insert Walmart order $intOrderId", e)
            return Result.failure(e)
        }
    }

    /**
     * Acknowledge multiple orders in bulk
     */
    suspend fun acknowledgeOrders(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        purchaseOrderIds: List<String>
    ): Result<BulkAcknowledgeResult> {
        val results = mutableMapOf<String, Boolean>()
        val errors = mutableListOf<String>()

        for (purchaseOrderId in purchaseOrderIds) {
            val result = walmartClient.acknowledgeOrder(storeId, clientId, clientSecret, purchaseOrderId)
            result.fold(
                onSuccess = {
                    results[purchaseOrderId] = true
                    logger.info("Acknowledged Walmart order $purchaseOrderId")
                },
                onFailure = { error ->
                    results[purchaseOrderId] = false
                    errors.add("Order $purchaseOrderId: ${error.message}")
                    logger.error("Failed to acknowledge Walmart order $purchaseOrderId: ${error.message}")
                }
            )
        }

        return Result.success(BulkAcknowledgeResult(
            total = purchaseOrderIds.size,
            acknowledged = results.count { it.value },
            failed = results.count { !it.value },
            errors = errors
        ))
    }

    /**
     * Update tracking information for an order
     */
    suspend fun updateTracking(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        purchaseOrderId: String,
        lineNumber: String,
        orderId: Long,
        carrier: String,
        trackingNumber: String,
        trackingUrl: String? = null
    ): Result<Boolean> {
        val shipDateTime = Instant.now().toEpochMilli()

        val shipmentRequest = WalmartShipmentRequest(
            orderShipment = WalmartOrderShipment(
                orderLines = WalmartShipmentOrderLines(
                    orderLine = listOf(
                        WalmartShipmentOrderLine(
                            lineNumber = lineNumber,
                            sellerOrderId = orderId.toString(),
                            orderLineStatuses = WalmartShipmentStatuses(
                                orderLineStatus = listOf(
                                    WalmartShipmentStatus(
                                        status = "Shipped",
                                        statusQuantity = WalmartQuantity(
                                            unitOfMeasurement = "EACH",
                                            amount = "1"
                                        ),
                                        trackingInfo = WalmartShipmentTrackingInfo(
                                            shipDateTime = shipDateTime,
                                            carrierName = WalmartCarrier(carrier = carrier.uppercase()),
                                            methodCode = "Standard",
                                            trackingNumber = trackingNumber,
                                            trackingURL = trackingUrl
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = walmartClient.updateShipment(storeId, clientId, clientSecret, purchaseOrderId, shipmentRequest)

        return result.fold(
            onSuccess = {
                logger.info("Successfully updated tracking for Walmart order $purchaseOrderId")
                Result.success(true)
            },
            onFailure = { error ->
                // If status code is 200, it means the shipment was updated but empty response
                if (error is WalmartApiException && error.statusCode == 200) {
                    Result.success(true)
                } else {
                    logger.error("Failed to update tracking for Walmart order $purchaseOrderId: ${error.message}")
                    Result.failure(error)
                }
            }
        )
    }

    /**
     * Test connection to Walmart API
     */
    suspend fun testConnection(clientId: String, clientSecret: String): Result<Boolean> {
        return authService.testConnection(clientId, clientSecret)
    }

    /**
     * Sync completed/delivered orders and update their status
     */
    suspend fun syncCompletedOrders(
        tenantId: Long,
        storeId: Long,
        clientId: String,
        clientSecret: String
    ): Result<Int> {
        var cursor: String? = null
        val deliveredOrderIds = mutableListOf<String>()

        while (true) {
            val result = walmartClient.getOrders(
                storeId = storeId,
                clientId = clientId,
                clientSecret = clientSecret,
                status = WalmartOrderStatus.DELIVERED.value,
                cursor = cursor
            )

            val response = result.getOrElse { error ->
                logger.error("Failed to fetch delivered orders: ${error.message}")
                return Result.failure(error)
            }

            val orders = response.list?.elements?.order ?: emptyList()
            deliveredOrderIds.addAll(orders.map { it.customerOrderId })

            cursor = response.list?.meta?.nextCursor
            if (cursor == null || deliveredOrderIds.size >= MAX_ORDERS_PER_SYNC) {
                break
            }
        }

        if (deliveredOrderIds.isNotEmpty()) {
            val updatedCount = orderRepository.updateOrdersToCompleted(deliveredOrderIds, storeId)
            logger.info("Updated $updatedCount orders to completed status for store $storeId")
            return Result.success(updatedCount)
        }

        return Result.success(0)
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun buildOrderInfo(order: WalmartOrder): Map<String, Any?> {
        val postalAddress = order.shippingInfo.postalAddress
        return mapOf(
            "to_address" to mapOf(
                "zip" to postalAddress.postalCode,
                "city" to postalAddress.city,
                "name" to postalAddress.name,
                "state" to postalAddress.state,
                "verify" to false,
                "company" to "",
                "country" to postalAddress.country,
                "street1" to postalAddress.address1,
                "street2" to postalAddress.address2,
                "state_iso" to getStateIso(postalAddress.state),
                "country_iso" to getCountryIso(postalAddress.country),
                "formatted_address" to formatAddress(postalAddress)
            ),
            "purchase_order_id" to order.purchaseOrderId,
            "estimated_ship_date" to order.estimatedShipDate,
            "estimated_delivery_date" to order.estimatedDeliveryDate
        )
    }

    private fun buildOrderDetail(
        order: WalmartOrder,
        enrichedLines: List<EnrichedOrderLine>
    ): Map<String, Any?> {
        val postalAddress = order.shippingInfo.postalAddress
        val transactions = enrichedLines.map { enriched ->
            val line = enriched.orderLine
            val itemDetails = enriched.itemDetails
            val qty = line.orderLineQuantity.amount

            val (size, color) = extractSizeAndColor(itemDetails?.variantGroupInfo?.groupingAttributes)

            mapOf(
                "title" to line.item.productName,
                "sku" to line.item.sku,
                "quantity" to qty,
                "transaction_id" to (itemDetails?.wpid ?: line.item.sku),
                "line_number" to line.lineNumber,
                "variations" to listOf(
                    mapOf(
                        "formatted_name" to size,
                        "formatted_value" to color
                    )
                ),
                "total_shipping_cost" to mapOf(
                    "amount" to 0
                ),
                "image_url" to line.item.imageUrl
            )
        }

        return mapOf(
            "name" to postalAddress.name,
            "formatted_address" to formatAddress(postalAddress),
            "transactions" to transactions,
            "created_at" to order.orderDate,
            "update_timestamp" to order.orderDate,
            "order_type" to (order.orderType ?: "REGULAR")
        )
    }

    private fun formatAddress(address: WalmartAddress): String {
        val lines = listOfNotNull(
            address.name,
            address.address1,
            address.address2?.takeIf { it.isNotBlank() },
            "${address.city}, ${address.state} ${address.postalCode}",
            address.country
        )
        return lines.joinToString("\n")
    }

    private fun extractSizeAndColor(attributes: List<WalmartVariantAttribute>?): Pair<String, String> {
        if (attributes.isNullOrEmpty()) return "" to ""

        val size = attributes.getOrNull(0)?.value ?: ""
        val color = attributes.getOrNull(1)?.value ?: ""
        return size to color
    }

    private fun getStateIso(state: String): String {
        // Common US state abbreviations
        val stateMap = mapOf(
            "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR",
            "California" to "CA", "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE",
            "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
            "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS",
            "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
            "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
            "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
            "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
            "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
            "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC",
            "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT",
            "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV",
            "Wisconsin" to "WI", "Wyoming" to "WY", "District of Columbia" to "DC"
        )
        return stateMap[state] ?: state
    }

    private fun getCountryIso(country: String): String {
        return when (country.lowercase()) {
            "united states", "usa", "us", "united states of america" -> "US"
            "canada" -> "CA"
            "mexico" -> "MX"
            "united kingdom", "uk", "great britain" -> "GB"
            else -> country.take(2).uppercase()
        }
    }
}

/**
 * Helper class to hold order line with its item details
 */
data class EnrichedOrderLine(
    val orderLine: WalmartOrderLine,
    val itemDetails: WalmartProduct?
)

/**
 * Sync result statistics
 */
data class WalmartSyncResult(
    val storeId: Long,
    val totalFetched: Int,
    val inserted: Int,
    val skipped: Int,
    val errors: List<String>,
    val syncedAt: String
)

/**
 * Bulk acknowledge result
 */
data class BulkAcknowledgeResult(
    val total: Int,
    val acknowledged: Int,
    val failed: Int,
    val errors: List<String>
)

/**
 * Walmart Sync Exception
 */
class WalmartSyncException(message: String) : Exception(message)
