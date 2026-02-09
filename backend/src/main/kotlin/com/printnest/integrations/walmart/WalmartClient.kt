package com.printnest.integrations.walmart

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Walmart Marketplace API HTTP Client
 *
 * Handles all HTTP communication with Walmart's Marketplace API v3
 * Base URL: https://marketplace.walmartapis.com/v3
 */
class WalmartClient(
    private val httpClient: HttpClient,
    private val authService: WalmartAuthService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(WalmartClient::class.java)

    companion object {
        const val BASE_URL = "https://marketplace.walmartapis.com/v3"
        const val MAX_ORDERS_LIMIT = 200 // Walmart's maximum limit per request
    }

    /**
     * Get orders from Walmart API
     *
     * @param storeId PrintNest store ID for token caching
     * @param clientId Walmart client ID
     * @param clientSecret Walmart client secret
     * @param createdStartDate Start date for order creation (ISO 8601)
     * @param createdEndDate End date for order creation (ISO 8601)
     * @param status Order status filter (Created, Acknowledged, Shipped, Delivered, Cancelled)
     * @param limit Max orders to return (max 200)
     * @param cursor Pagination cursor for next page
     * @param productInfo Include product info in response
     */
    suspend fun getOrders(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        createdStartDate: String? = null,
        createdEndDate: String? = null,
        status: String? = null,
        limit: Int = MAX_ORDERS_LIMIT,
        cursor: String? = null,
        productInfo: Boolean = true
    ): Result<WalmartOrdersResponse> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/orders"
            val headers = authService.buildAuthHeaders(accessToken)

            logger.debug("Fetching Walmart orders for store $storeId")

            val response = httpClient.get(url) {
                headers.forEach { (key, value) -> header(key, value) }

                // Add query parameters
                createdStartDate?.let { parameter("createdStartDate", it) }
                createdEndDate?.let { parameter("createdEndDate", it) }
                status?.let { parameter("status", it) }
                parameter("limit", limit.coerceAtMost(MAX_ORDERS_LIMIT))
                parameter("productInfo", productInfo)
                cursor?.let { parameter("nextCursor", it) }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Exception fetching Walmart orders", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get a single order by Purchase Order ID
     */
    suspend fun getOrder(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        purchaseOrderId: String
    ): Result<WalmartOrder> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/orders/$purchaseOrderId"
            val headers = authService.buildAuthHeaders(accessToken)

            logger.debug("Fetching Walmart order $purchaseOrderId for store $storeId")

            val response = httpClient.get(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }

            if (response.status.isSuccess()) {
                val orderResponse: WalmartOrdersResponse = response.body()
                val order = orderResponse.list?.elements?.order?.firstOrNull()
                if (order != null) {
                    Result.success(order)
                } else {
                    Result.failure(WalmartApiException("Order not found: $purchaseOrderId", 404))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to get Walmart order $purchaseOrderId: ${response.status} - $errorBody")
                Result.failure(WalmartApiException("Failed to get order: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception getting Walmart order $purchaseOrderId", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Acknowledge an order
     * This tells Walmart you've received the order and will fulfill it
     */
    suspend fun acknowledgeOrder(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        purchaseOrderId: String
    ): Result<WalmartAcknowledgeResponse> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/orders/$purchaseOrderId/acknowledge"
            val headers = authService.buildAuthHeaders(accessToken)

            logger.info("Acknowledging Walmart order $purchaseOrderId for store $storeId")

            val response = httpClient.post(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }

            if (response.status.isSuccess()) {
                val acknowledgeResponse: WalmartAcknowledgeResponse = response.body()
                logger.info("Successfully acknowledged Walmart order $purchaseOrderId")
                Result.success(acknowledgeResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to acknowledge Walmart order $purchaseOrderId: ${response.status} - $errorBody")
                Result.failure(WalmartApiException("Failed to acknowledge order: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception acknowledging Walmart order $purchaseOrderId", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Update shipment/tracking information for an order
     */
    suspend fun updateShipment(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        purchaseOrderId: String,
        shipment: WalmartShipmentRequest
    ): Result<WalmartOrder> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/orders/$purchaseOrderId/shipping"
            val headers = authService.buildAuthHeaders(accessToken)

            logger.info("Updating shipment for Walmart order $purchaseOrderId")

            val response = httpClient.post(url) {
                headers.forEach { (key, value) -> header(key, value) }
                contentType(ContentType.Application.Json)
                setBody(shipment)
            }

            if (response.status.isSuccess()) {
                val orderResponse: WalmartOrdersResponse = response.body()
                val order = orderResponse.list?.elements?.order?.firstOrNull()
                if (order != null) {
                    logger.info("Successfully updated shipment for Walmart order $purchaseOrderId")
                    Result.success(order)
                } else {
                    // Sometimes Walmart returns success but with empty response
                    logger.info("Shipment update sent successfully for order $purchaseOrderId (empty response)")
                    Result.failure(WalmartApiException("Shipment updated but no order returned", 200))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to update shipment for Walmart order $purchaseOrderId: ${response.status} - $errorBody")
                Result.failure(WalmartApiException("Failed to update shipment: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception updating shipment for Walmart order $purchaseOrderId", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get item/product details by SKU
     */
    suspend fun getItemDetails(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        sku: String
    ): Result<WalmartProduct> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/items/$sku"
            val headers = authService.buildAuthHeaders(accessToken)

            logger.debug("Fetching Walmart item details for SKU: $sku")

            val response = httpClient.get(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }

            if (response.status.isSuccess()) {
                val itemResponse: WalmartItemResponse = response.body()
                val product = itemResponse.itemResponse.firstOrNull()
                if (product != null) {
                    Result.success(product)
                } else {
                    Result.failure(WalmartApiException("Item not found: $sku", 404))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to get Walmart item $sku: ${response.status} - $errorBody")
                Result.failure(WalmartApiException("Failed to get item: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception getting Walmart item $sku", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get all items/inventory with pagination
     */
    suspend fun getItems(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        limit: Int = 20,
        offset: Int = 0,
        lifecycleStatus: String? = null,
        publishedStatus: String? = null
    ): Result<WalmartItemResponse> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/items"
            val headers = authService.buildAuthHeaders(accessToken)

            logger.debug("Fetching Walmart items for store $storeId")

            val response = httpClient.get(url) {
                headers.forEach { (key, value) -> header(key, value) }
                parameter("limit", limit)
                parameter("offset", offset)
                lifecycleStatus?.let { parameter("lifecycleStatus", it) }
                publishedStatus?.let { parameter("publishedStatus", it) }
            }

            if (response.status.isSuccess()) {
                val itemResponse: WalmartItemResponse = response.body()
                Result.success(itemResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to get Walmart items: ${response.status} - $errorBody")
                Result.failure(WalmartApiException("Failed to get items: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception getting Walmart items", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Cancel order lines
     */
    suspend fun cancelOrder(
        storeId: Long,
        clientId: String,
        clientSecret: String,
        purchaseOrderId: String,
        lineNumbers: List<String>,
        cancellationReason: String = "CANCEL_BY_SELLER"
    ): Result<WalmartOrder> {
        val accessToken = authService.getOrGenerateToken(storeId, clientId, clientSecret)
            ?: return Result.failure(WalmartApiException("Failed to obtain access token", 401))

        return try {
            val url = "$BASE_URL/orders/$purchaseOrderId/cancel"
            val headers = authService.buildAuthHeaders(accessToken)

            // Build cancellation request body
            val cancellationBody = buildCancellationRequest(lineNumbers, cancellationReason)

            logger.info("Cancelling Walmart order $purchaseOrderId")

            val response = httpClient.post(url) {
                headers.forEach { (key, value) -> header(key, value) }
                contentType(ContentType.Application.Json)
                setBody(cancellationBody)
            }

            if (response.status.isSuccess()) {
                val orderResponse: WalmartOrdersResponse = response.body()
                val order = orderResponse.list?.elements?.order?.firstOrNull()
                if (order != null) {
                    logger.info("Successfully cancelled Walmart order $purchaseOrderId")
                    Result.success(order)
                } else {
                    Result.failure(WalmartApiException("Cancellation sent but no order returned", 200))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to cancel Walmart order $purchaseOrderId: ${response.status} - $errorBody")
                Result.failure(WalmartApiException("Failed to cancel order: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception cancelling Walmart order $purchaseOrderId", e)
            Result.failure(WalmartApiException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Helper to handle common response processing
     */
    private suspend inline fun <reified T> handleResponse(response: HttpResponse): Result<T> {
        return if (response.status.isSuccess()) {
            val body: T = response.body()
            Result.success(body)
        } else {
            val errorBody = response.bodyAsText()
            logger.error("Walmart API error: ${response.status} - $errorBody")
            Result.failure(WalmartApiException("API error: ${response.status}", response.status.value))
        }
    }

    /**
     * Build cancellation request body
     */
    private fun buildCancellationRequest(
        lineNumbers: List<String>,
        cancellationReason: String
    ): Map<String, Any> {
        return mapOf(
            "orderCancellation" to mapOf(
                "orderLines" to mapOf(
                    "orderLine" to lineNumbers.map { lineNumber ->
                        mapOf(
                            "lineNumber" to lineNumber,
                            "orderLineStatuses" to mapOf(
                                "orderLineStatus" to listOf(
                                    mapOf(
                                        "status" to "Cancelled",
                                        "cancellationReason" to cancellationReason,
                                        "statusQuantity" to mapOf(
                                            "unitOfMeasurement" to "EACH",
                                            "amount" to "1"
                                        )
                                    )
                                )
                            )
                        )
                    }
                )
            )
        )
    }
}

/**
 * Walmart API Exception
 */
class WalmartApiException(
    message: String,
    val statusCode: Int
) : Exception(message)
