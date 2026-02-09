package com.printnest.integrations.shopify

import com.printnest.domain.models.Address
import com.printnest.domain.models.OrderStatus
import com.printnest.domain.repository.OrderRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Shopify Business Logic Service
 *
 * Handles:
 * - Order synchronization from Shopify
 * - Order conversion to PrintNest format
 * - Fulfillment updates back to Shopify
 * - Webhook processing
 */
class ShopifyService(
    private val client: ShopifyClient,
    private val authService: ShopifyAuthService,
    private val storeRepository: ShopifyStoreRepository,
    private val orderRepository: OrderRepository,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ShopifyService::class.java)

    // =====================================================
    // STORE MANAGEMENT
    // =====================================================

    /**
     * Save or update a Shopify store connection
     */
    fun saveStore(
        tenantId: Long,
        shopUrl: String,
        accessToken: String,
        scope: String?
    ): ShopifyStore {
        val normalizedShop = authService.normalizeShopDomain(shopUrl)

        val existingStore = storeRepository.findByShopUrl(normalizedShop)

        return if (existingStore != null) {
            storeRepository.update(
                storeId = existingStore.id,
                accessToken = accessToken,
                scope = scope
            )
        } else {
            storeRepository.create(
                tenantId = tenantId,
                shopUrl = normalizedShop,
                accessToken = accessToken,
                scope = scope
            )
        }
    }

    /**
     * Get store by ID
     */
    fun getStore(storeId: Long): ShopifyStore? {
        return storeRepository.findById(storeId)
    }

    /**
     * Get all stores for a tenant
     */
    fun getStores(tenantId: Long): List<ShopifyStore> {
        return storeRepository.findByTenantId(tenantId)
    }

    /**
     * Get active stores for a tenant
     */
    fun getActiveStores(tenantId: Long): List<ShopifyStore> {
        return storeRepository.findByTenantIdAndActive(tenantId)
    }

    /**
     * Disconnect a Shopify store
     */
    fun disconnectStore(storeId: Long): Boolean {
        return storeRepository.deactivate(storeId)
    }

    // =====================================================
    // ORDER SYNCHRONIZATION
    // =====================================================

    /**
     * Sync new orders from Shopify store
     *
     * @param storeId The ShopifyStore ID
     * @return Result with sync statistics
     */
    suspend fun syncOrders(storeId: Long): Result<SyncResult> {
        val store = storeRepository.findById(storeId)
            ?: return Result.failure(ShopifyServiceException("Store not found: $storeId"))

        if (store.accessToken.isNullOrBlank()) {
            return Result.failure(ShopifyServiceException("Store not connected: ${store.shopUrl}"))
        }

        logger.info("Starting order sync for store: ${store.shopUrl}")

        var totalFetched = 0
        var totalInserted = 0
        var totalSkipped = 0
        var cursor: String? = null
        var shopOwnerEmail: String? = null

        try {
            // Fetch unfulfilled, paid orders
            val filterQuery = "financial_status:PAID AND fulfillment_status:UNFULFILLED"

            do {
                val result = client.getOrders(
                    shop = store.shopUrl,
                    accessToken = store.accessToken,
                    filterQuery = filterQuery,
                    cursor = cursor
                )

                val response = result.getOrThrow()
                val ordersData = response.data ?: break

                // Capture shop owner email on first fetch
                if (shopOwnerEmail == null) {
                    shopOwnerEmail = ordersData.shop?.email
                    if (shopOwnerEmail != null) {
                        storeRepository.updateShopOwnerEmail(storeId, shopOwnerEmail)
                    }
                }

                val orders = ordersData.orders?.edges ?: emptyList()
                val pageInfo = ordersData.orders?.pageInfo

                if (orders.isEmpty()) {
                    logger.info("No new orders found for store: ${store.shopUrl}")
                    break
                }

                totalFetched += orders.size
                logger.info("Fetched ${orders.size} orders from Shopify")

                // Process each order
                for (orderEdge in orders) {
                    val shopifyOrder = orderEdge.node
                    val intOrderId = shopifyOrder.name.removePrefix("#")

                    // Check if order already exists
                    val existingOrder = orderRepository.findByIntOrderIdAndStoreId(
                        intOrderId = intOrderId,
                        storeId = storeId
                    )

                    if (existingOrder != null) {
                        logger.debug("Order $intOrderId already exists, skipping")
                        totalSkipped++
                        continue
                    }

                    // Convert and insert order
                    val inserted = processOrder(store.tenantId, storeId, shopifyOrder)
                    if (inserted) {
                        totalInserted++
                        logger.info("Inserted order: $intOrderId")
                    }
                }

                // Update cursor for next page
                cursor = if (pageInfo?.hasNextPage == true) {
                    orders.lastOrNull()?.cursor
                } else {
                    null
                }

                // Rate limiting - avoid hitting Shopify API limits
                if (cursor != null) {
                    kotlinx.coroutines.delay(500)
                }

            } while (cursor != null && totalFetched < 1000)

            // Update last sync time
            storeRepository.updateLastSyncAt(storeId)

            logger.info("Sync completed for ${store.shopUrl}: fetched=$totalFetched, inserted=$totalInserted, skipped=$totalSkipped")

            return Result.success(SyncResult(
                totalFetched = totalFetched,
                totalInserted = totalInserted,
                totalSkipped = totalSkipped,
                shopOwnerEmail = shopOwnerEmail
            ))

        } catch (e: Exception) {
            logger.error("Error syncing orders for store ${store.shopUrl}", e)
            return Result.failure(ShopifyServiceException("Sync failed: ${e.message}"))
        }
    }

    /**
     * Process a single Shopify order and convert to PrintNest order
     */
    private fun processOrder(
        tenantId: Long,
        storeId: Long,
        shopifyOrder: ShopifyOrder
    ): Boolean {
        try {
            val intOrderId = shopifyOrder.name.removePrefix("#")
            val billingAddress = shopifyOrder.shippingAddress ?: shopifyOrder.billingAddress

            if (billingAddress == null) {
                logger.warn("Order ${shopifyOrder.id} has no shipping/billing address, skipping")
                return false
            }

            // Extract fulfillment order ID for later fulfillment
            val fulfillmentOrderId = shopifyOrder.fulfillmentOrders?.edges
                ?.firstOrNull()?.node?.id

            // Build order info JSON
            val orderInfo = buildOrderInfo(shopifyOrder, billingAddress, fulfillmentOrderId)

            // Build order detail JSON
            val lineItems = shopifyOrder.lineItems?.edges ?: emptyList()
            val orderDetail = buildOrderDetail(shopifyOrder, billingAddress, lineItems)

            // Convert address
            val shippingAddress = convertAddress(billingAddress)

            // Insert order into database
            val orderId = orderRepository.createFromShopify(
                tenantId = tenantId,
                storeId = storeId,
                intOrderId = intOrderId,
                externalOrderId = shopifyOrder.id,
                orderInfo = json.encodeToString(kotlinx.serialization.serializer(), orderInfo),
                orderDetail = json.encodeToString(kotlinx.serialization.serializer(), orderDetail),
                customerEmail = shopifyOrder.customer?.email,
                customerName = billingAddress.name,
                shippingAddress = shippingAddress
            )

            // Insert line items as order products
            for (itemEdge in lineItems) {
                val item = itemEdge.node
                insertOrderProduct(orderId, tenantId, item)
            }

            return true

        } catch (e: Exception) {
            logger.error("Error processing order ${shopifyOrder.id}", e)
            return false
        }
    }

    /**
     * Build order info structure
     */
    private fun buildOrderInfo(
        order: ShopifyOrder,
        address: ShopifyAddress,
        fulfillmentOrderId: String?
    ): Map<String, Any?> {
        return mapOf(
            "order_id" to order.id,
            "fulfillmentOrderId" to fulfillmentOrderId,
            "to_address" to mapOf(
                "zip" to address.zip,
                "name" to address.name,
                "street1" to address.address1,
                "street2" to address.address2,
                "city" to address.city,
                "state" to address.province,
                "state_iso" to address.provinceCode,
                "country" to address.country,
                "country_iso" to address.countryCode,
                "formatted_address" to address.formatForDisplay(),
                "company" to address.company,
                "verify" to false
            )
        )
    }

    /**
     * Build order detail structure
     */
    private fun buildOrderDetail(
        order: ShopifyOrder,
        address: ShopifyAddress,
        lineItems: List<ShopifyLineItemEdge>
    ): Map<String, Any?> {
        val transactions = lineItems.map { itemEdge ->
            val item = itemEdge.node
            val variant = item.variant
            val (size, color) = parseSizeAndColor(variant?.selectedOptions ?: emptyList())

            mapOf(
                "title" to item.title,
                "sku" to item.sku,
                "quantity" to item.quantity,
                "transaction_id" to extractShopifyId(item.id),
                "variations" to listOf(
                    mapOf(
                        "formatted_name" to size,
                        "formatted_value" to color
                    )
                ),
                "total_shipping_cost" to mapOf(
                    "amount" to 0
                )
            )
        }

        // Extract design links from metafields
        val links = extractLinksFromMetafields(order.metafields?.nodes ?: emptyList())

        return mapOf(
            "name" to address.name,
            "formatted_address" to address.formatForDisplay(),
            "transactions" to transactions,
            "created_at" to order.createdAt,
            "update_timestamp" to order.createdAt,
            "links" to links
        )
    }

    /**
     * Extract design links from order metafields
     */
    private fun extractLinksFromMetafields(metafields: List<ShopifyMetafield>): List<String> {
        val links = mutableListOf<String>()

        for (metafield in metafields) {
            val jsonValue = metafield.jsonValue ?: continue
            findLinksRecursively(jsonValue, links)
        }

        return links
    }

    /**
     * Recursively find URLs in JSON structure
     */
    private fun findLinksRecursively(node: JsonElement, links: MutableList<String>) {
        when {
            node is kotlinx.serialization.json.JsonObject -> {
                val obj = node.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "link") {
                    obj["url"]?.jsonPrimitive?.content?.let { links.add(it) }
                }
                obj.values.forEach { findLinksRecursively(it, links) }
            }
            node is kotlinx.serialization.json.JsonArray -> {
                node.jsonArray.forEach { findLinksRecursively(it, links) }
            }
        }
    }

    /**
     * Insert order product (line item)
     */
    private fun insertOrderProduct(
        orderId: Long,
        tenantId: Long,
        item: ShopifyLineItem
    ) {
        val variant = item.variant
        val (size, color) = parseSizeAndColor(variant?.selectedOptions ?: emptyList())

        val productDetail = mapOf(
            "option_1" to size,
            "option_2" to color,
            "quantity" to item.quantity,
            "personalization" to ""
        )

        orderRepository.createOrderProduct(
            orderId = orderId,
            tenantId = tenantId,
            listingId = extractShopifyId(item.id),
            quantity = item.quantity,
            productDetail = json.encodeToString(kotlinx.serialization.serializer(), productDetail),
            listingImageUrl = item.image?.src
        )
    }

    /**
     * Convert Shopify address to PrintNest Address
     */
    private fun convertAddress(shopifyAddress: ShopifyAddress): Address {
        return Address(
            name = shopifyAddress.name,
            company = shopifyAddress.company,
            street1 = shopifyAddress.address1,
            street2 = shopifyAddress.address2,
            city = shopifyAddress.city,
            state = shopifyAddress.provinceCode ?: shopifyAddress.province,
            postalCode = shopifyAddress.zip,
            country = shopifyAddress.countryCode ?: shopifyAddress.country,
            phone = shopifyAddress.phone
        )
    }

    // =====================================================
    // FULFILLMENT UPDATES
    // =====================================================

    /**
     * Update fulfillment on Shopify when order is shipped
     *
     * @param orderId PrintNest order ID
     * @param trackingNumber Tracking number
     * @param carrier Carrier name
     * @param trackingUrl Optional tracking URL
     */
    suspend fun updateFulfillment(
        orderId: Long,
        trackingNumber: String,
        carrier: String,
        trackingUrl: String? = null
    ): Result<FulfillmentResult> {
        // Get order with Shopify info
        val order = orderRepository.findById(orderId)
            ?: return Result.failure(ShopifyServiceException("Order not found: $orderId"))

        // Get store
        val storeId = order.storeId
            ?: return Result.failure(ShopifyServiceException("Order has no associated store"))

        val store = storeRepository.findById(storeId)
            ?: return Result.failure(ShopifyServiceException("Store not found: $storeId"))

        if (store.accessToken.isNullOrBlank()) {
            return Result.failure(ShopifyServiceException("Store not connected"))
        }

        // Parse order info to get fulfillment order ID
        val orderInfoJson = order.orderInfo
        val fulfillmentOrderId = extractFulfillmentOrderId(orderInfoJson)
            ?: return Result.failure(ShopifyServiceException("No fulfillment order ID found"))

        val company = carrier.uppercase()

        try {
            // Create fulfillment
            val createResult = client.createFulfillment(
                shop = store.shopUrl,
                accessToken = store.accessToken,
                fulfillmentOrderId = fulfillmentOrderId,
                trackingNumber = trackingNumber,
                trackingCompany = company
            )

            val createResponse = createResult.getOrThrow()
            val fulfillmentData = createResponse.data?.fulfillmentCreateV2

            if (fulfillmentData?.userErrors?.isNotEmpty() == true) {
                val errors = fulfillmentData.userErrors.joinToString { it.message }
                logger.error("Fulfillment creation failed: $errors")
                return Result.failure(ShopifyServiceException("Fulfillment failed: $errors"))
            }

            val fulfillmentId = fulfillmentData?.fulfillment?.id
                ?: return Result.failure(ShopifyServiceException("No fulfillment ID returned"))

            // Update tracking if URL provided
            if (trackingUrl != null) {
                val updateResult = client.updateFulfillmentTracking(
                    shop = store.shopUrl,
                    accessToken = store.accessToken,
                    fulfillmentId = fulfillmentId,
                    trackingNumber = trackingNumber,
                    trackingCompany = company,
                    trackingUrl = trackingUrl
                )

                val updateResponse = updateResult.getOrThrow()
                if (updateResponse.data?.fulfillmentTrackingInfoUpdate?.userErrors?.isNotEmpty() == true) {
                    logger.warn("Tracking update had errors, but fulfillment was created")
                }
            }

            logger.info("Fulfillment created for order $orderId: $fulfillmentId")

            return Result.success(FulfillmentResult(
                fulfillmentId = fulfillmentId,
                trackingNumber = trackingNumber,
                carrier = company
            ))

        } catch (e: Exception) {
            logger.error("Error creating fulfillment for order $orderId", e)
            return Result.failure(ShopifyServiceException("Fulfillment failed: ${e.message}"))
        }
    }

    /**
     * Extract fulfillment order ID from order info JSON
     */
    private fun extractFulfillmentOrderId(orderInfo: Any?): String? {
        return try {
            when (orderInfo) {
                is String -> {
                    val parsed = json.parseToJsonElement(orderInfo).jsonObject
                    parsed["fulfillmentOrderId"]?.jsonPrimitive?.content
                }
                is Map<*, *> -> orderInfo["fulfillmentOrderId"]?.toString()
                else -> null
            }
        } catch (e: Exception) {
            logger.warn("Could not extract fulfillment order ID", e)
            null
        }
    }

    // =====================================================
    // WEBHOOK HANDLING
    // =====================================================

    /**
     * Handle incoming webhook from Shopify
     *
     * @param topic Webhook topic (e.g., "orders/create")
     * @param shopDomain Shop domain from headers
     * @param payload Raw webhook payload
     */
    suspend fun handleWebhook(
        topic: String,
        shopDomain: String,
        payload: String
    ): WebhookResult {
        logger.info("Processing Shopify webhook: topic=$topic, shop=$shopDomain")

        return try {
            when (topic) {
                "orders/create" -> handleOrderCreated(shopDomain, payload)
                "orders/updated" -> handleOrderUpdated(shopDomain, payload)
                "orders/fulfilled" -> handleOrderFulfilled(shopDomain, payload)
                "orders/cancelled" -> handleOrderCancelled(shopDomain, payload)
                "customers/data_request" -> handleCustomerDataRequest(payload)
                "customers/redact" -> handleCustomerRedact(payload)
                "shop/redact" -> handleShopRedact(payload)
                else -> {
                    logger.warn("Unhandled webhook topic: $topic")
                    WebhookResult(success = true, message = "Topic not handled")
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing webhook: $topic", e)
            WebhookResult(success = false, message = "Error: ${e.message}")
        }
    }

    private suspend fun handleOrderCreated(shopDomain: String, payload: String): WebhookResult {
        // Parse the webhook payload and create order if not exists
        // This is a backup for order sync
        logger.info("Order created webhook received for $shopDomain")

        // Trigger a sync for this store
        val store = storeRepository.findByShopUrl(shopDomain)
        if (store != null) {
            syncOrders(store.id)
        }

        return WebhookResult(success = true, message = "Order processed")
    }

    private fun handleOrderUpdated(shopDomain: String, payload: String): WebhookResult {
        logger.info("Order updated webhook received for $shopDomain")
        // Handle order updates if needed
        return WebhookResult(success = true, message = "Order update noted")
    }

    private fun handleOrderFulfilled(shopDomain: String, payload: String): WebhookResult {
        logger.info("Order fulfilled webhook received for $shopDomain")
        // Update order status in our system
        return WebhookResult(success = true, message = "Fulfillment noted")
    }

    private fun handleOrderCancelled(shopDomain: String, payload: String): WebhookResult {
        logger.info("Order cancelled webhook received for $shopDomain")
        // Update order status to cancelled
        return WebhookResult(success = true, message = "Cancellation noted")
    }

    private fun handleCustomerDataRequest(payload: String): WebhookResult {
        logger.info("Customer data request received")
        // GDPR: Return customer data
        return WebhookResult(success = true, message = "Data request acknowledged")
    }

    private fun handleCustomerRedact(payload: String): WebhookResult {
        logger.info("Customer redact request received")
        // GDPR: Delete customer data
        return WebhookResult(success = true, message = "Redact request acknowledged")
    }

    private fun handleShopRedact(payload: String): WebhookResult {
        logger.info("Shop redact request received")
        // GDPR: Delete all shop data
        return WebhookResult(success = true, message = "Shop redact acknowledged")
    }

    // =====================================================
    // WEBHOOK REGISTRATION
    // =====================================================

    /**
     * Register required webhooks for a store
     */
    suspend fun registerWebhooks(storeId: Long, baseUrl: String): Result<List<ShopifyWebhookInfo>> {
        val store = storeRepository.findById(storeId)
            ?: return Result.failure(ShopifyServiceException("Store not found"))

        if (store.accessToken.isNullOrBlank()) {
            return Result.failure(ShopifyServiceException("Store not connected"))
        }

        val topics = listOf(
            "orders/create",
            "orders/updated",
            "orders/fulfilled",
            "orders/cancelled"
        )

        val registeredWebhooks = mutableListOf<ShopifyWebhookInfo>()

        for (topic in topics) {
            val address = "$baseUrl/api/v1/shopify/webhook"

            val result = client.registerWebhook(
                shop = store.shopUrl,
                accessToken = store.accessToken,
                topic = topic,
                address = address
            )

            result.fold(
                onSuccess = { response ->
                    response.webhook?.let { registeredWebhooks.add(it) }
                },
                onFailure = { error ->
                    logger.warn("Failed to register webhook $topic: ${error.message}")
                }
            )
        }

        return Result.success(registeredWebhooks)
    }
}

// =====================================================
// RESULT CLASSES
// =====================================================

data class SyncResult(
    val totalFetched: Int,
    val totalInserted: Int,
    val totalSkipped: Int,
    val shopOwnerEmail: String? = null
)

data class FulfillmentResult(
    val fulfillmentId: String,
    val trackingNumber: String,
    val carrier: String
)

data class WebhookResult(
    val success: Boolean,
    val message: String
)

class ShopifyServiceException(message: String) : Exception(message)
