package com.printnest.integrations.amazon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Amazon SP-API Client
 * Handles all API calls to Amazon Selling Partner API
 */
class AmazonClient(
    private val httpClient: HttpClient,
    private val authService: AmazonAuthService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(AmazonClient::class.java)

    private val baseUrl = AmazonConstants.SP_API_BASE_URL_NA

    /**
     * Get orders from Amazon SP-API
     *
     * @param storeId Internal store ID for token management
     * @param refreshToken Store's refresh token
     * @param createdAfter Filter orders created after this date (ISO 8601)
     * @param createdBefore Filter orders created before this date (ISO 8601)
     * @param marketplaceIds List of marketplace IDs to filter
     * @param orderStatuses Filter by order statuses
     * @param nextToken Pagination token for next page
     * @return AmazonOrdersResponse with orders and pagination info
     */
    suspend fun getOrders(
        storeId: Long,
        refreshToken: String,
        createdAfter: String? = null,
        createdBefore: String? = null,
        marketplaceIds: List<String> = listOf(AmazonConstants.US_MARKETPLACE_ID),
        orderStatuses: List<String>? = null,
        nextToken: String? = null,
        maxResultsPerPage: Int = 100
    ): Result<AmazonOrdersResponse> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        // Default to last 90 days if no date specified
        val effectiveCreatedAfter = createdAfter ?: run {
            val ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS)
            DateTimeFormatter.ISO_INSTANT.format(ninetyDaysAgo)
        }

        return try {
            val response = httpClient.get("$baseUrl/orders/v0/orders") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)

                parameter("MarketplaceIds", marketplaceIds.joinToString(","))
                parameter("CreatedAfter", effectiveCreatedAfter)
                createdBefore?.let { parameter("CreatedBefore", it) }
                orderStatuses?.let { parameter("OrderStatuses", it.joinToString(",")) }
                nextToken?.let { parameter("NextToken", it) }
                parameter("MaxResultsPerPage", maxResultsPerPage)
            }

            handleResponse(response, "getOrders")
        } catch (e: Exception) {
            logger.error("Exception in getOrders", e)
            Result.failure(AmazonClientException("Failed to get orders: ${e.message}", e))
        }
    }

    /**
     * Get a single order by Amazon Order ID
     */
    suspend fun getOrder(
        storeId: Long,
        refreshToken: String,
        orderId: String
    ): Result<AmazonOrdersResponse> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        return try {
            val response = httpClient.get("$baseUrl/orders/v0/orders/$orderId") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            handleResponse(response, "getOrder")
        } catch (e: Exception) {
            logger.error("Exception in getOrder for $orderId", e)
            Result.failure(AmazonClientException("Failed to get order $orderId: ${e.message}", e))
        }
    }

    /**
     * Get order items (line items) for an order
     */
    suspend fun getOrderItems(
        storeId: Long,
        refreshToken: String,
        orderId: String,
        nextToken: String? = null
    ): Result<AmazonOrderItemsResponse> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        return try {
            val response = httpClient.get("$baseUrl/orders/v0/orders/$orderId/orderItems") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                nextToken?.let { parameter("NextToken", it) }
            }

            handleOrderItemsResponse(response, "getOrderItems")
        } catch (e: Exception) {
            logger.error("Exception in getOrderItems for $orderId", e)
            Result.failure(AmazonClientException("Failed to get order items for $orderId: ${e.message}", e))
        }
    }

    /**
     * Get shipping address for an order
     */
    suspend fun getOrderAddress(
        storeId: Long,
        refreshToken: String,
        orderId: String
    ): Result<AmazonAddressResponse> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        return try {
            val response = httpClient.get("$baseUrl/orders/v0/orders/$orderId/address") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            handleAddressResponse(response, "getOrderAddress")
        } catch (e: Exception) {
            logger.error("Exception in getOrderAddress for $orderId", e)
            Result.failure(AmazonClientException("Failed to get address for $orderId: ${e.message}", e))
        }
    }

    /**
     * Get buyer info for an order
     */
    suspend fun getOrderBuyerInfo(
        storeId: Long,
        refreshToken: String,
        orderId: String
    ): Result<AmazonOrderBuyerInfoResponse> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        return try {
            val response = httpClient.get("$baseUrl/orders/v0/orders/$orderId/buyerInfo") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            handleBuyerInfoResponse(response, "getOrderBuyerInfo")
        } catch (e: Exception) {
            logger.error("Exception in getOrderBuyerInfo for $orderId", e)
            Result.failure(AmazonClientException("Failed to get buyer info for $orderId: ${e.message}", e))
        }
    }

    /**
     * Get buyer info for order items (includes customization URLs)
     */
    suspend fun getOrderItemsBuyerInfo(
        storeId: Long,
        refreshToken: String,
        orderId: String,
        nextToken: String? = null
    ): Result<AmazonOrderItemsBuyerInfoResponse> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        return try {
            val response = httpClient.get("$baseUrl/orders/v0/orders/$orderId/orderItems/buyerInfo") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                nextToken?.let { parameter("NextToken", it) }
            }

            handleOrderItemsBuyerInfoResponse(response, "getOrderItemsBuyerInfo")
        } catch (e: Exception) {
            logger.error("Exception in getOrderItemsBuyerInfo for $orderId", e)
            Result.failure(AmazonClientException("Failed to get order items buyer info for $orderId: ${e.message}", e))
        }
    }

    /**
     * Confirm shipment for an order
     * Sends tracking information to Amazon
     */
    suspend fun confirmShipment(
        storeId: Long,
        refreshToken: String,
        orderId: String,
        trackingInfo: AmazonTrackingInfo
    ): Result<Unit> {
        val accessTokenResult = authService.getAccessToken(storeId, refreshToken)
        if (accessTokenResult.isFailure) {
            return Result.failure(accessTokenResult.exceptionOrNull()!!)
        }
        val accessToken = accessTokenResult.getOrThrow()

        val request = AmazonShipmentConfirmationRequest(
            marketplaceId = AmazonConstants.US_MARKETPLACE_ID,
            packageDetail = AmazonPackageDetail(
                packageReferenceId = orderId,
                carrierCode = trackingInfo.carrierCode,
                carrierName = trackingInfo.carrierName,
                shippingMethod = trackingInfo.shippingMethod,
                trackingNumber = trackingInfo.trackingNumber,
                shipDate = trackingInfo.shipDate,
                orderItems = trackingInfo.orderItems
            )
        )

        return try {
            val response = httpClient.post("$baseUrl/orders/v0/orders/$orderId/shipmentConfirmation") {
                header("x-amz-access-token", accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }

            // Amazon returns 204 No Content on success
            if (response.status == HttpStatusCode.NoContent || response.status.isSuccess()) {
                logger.info("Successfully confirmed shipment for order $orderId")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to confirm shipment: ${response.status} - $errorBody")
                Result.failure(AmazonClientException("Failed to confirm shipment: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Exception in confirmShipment for $orderId", e)
            Result.failure(AmazonClientException("Failed to confirm shipment for $orderId: ${e.message}", e))
        }
    }

    /**
     * Get unshipped orders with rate limiting
     */
    suspend fun getUnshippedOrders(
        storeId: Long,
        refreshToken: String,
        marketplaceIds: List<String> = listOf(AmazonConstants.US_MARKETPLACE_ID),
        maxOrders: Int = AmazonConstants.MAX_ORDERS_PER_SYNC
    ): Result<List<AmazonOrder>> {
        val allOrders = mutableListOf<AmazonOrder>()
        var nextToken: String? = null
        var totalFetched = 0

        while (totalFetched < maxOrders) {
            val result = getOrders(
                storeId = storeId,
                refreshToken = refreshToken,
                marketplaceIds = marketplaceIds,
                orderStatuses = listOf(AmazonConstants.ORDER_STATUS_UNSHIPPED),
                nextToken = nextToken
            )

            result.fold(
                onSuccess = { response ->
                    val orders = response.payload?.orders ?: emptyList()
                    if (orders.isEmpty() && allOrders.isEmpty()) {
                        logger.info("No unshipped orders found for store $storeId")
                        return Result.success(emptyList())
                    }

                    allOrders.addAll(orders)
                    totalFetched += orders.size
                    nextToken = response.payload?.nextToken

                    if (nextToken == null) {
                        return Result.success(allOrders)
                    }

                    // Rate limiting
                    delay(AmazonConstants.RATE_LIMIT_DELAY_MS)
                },
                onFailure = { error ->
                    return Result.failure(error)
                }
            )
        }

        logger.info("Fetched $totalFetched unshipped orders for store $storeId")
        return Result.success(allOrders)
    }

    // =====================================================
    // RESPONSE HANDLERS
    // =====================================================

    private suspend inline fun <reified T> handleResponse(
        response: HttpResponse,
        operation: String
    ): Result<T> {
        return if (response.status.isSuccess()) {
            try {
                val body: T = response.body()
                Result.success(body)
            } catch (e: Exception) {
                logger.error("Failed to parse response for $operation", e)
                Result.failure(AmazonClientException("Failed to parse response: ${e.message}", e))
            }
        } else {
            val errorBody = response.bodyAsText()
            logger.error("$operation failed: ${response.status} - $errorBody")

            try {
                val errorResponse = json.decodeFromString<AmazonOrdersResponse>(errorBody)
                val errorMessage = errorResponse.errors?.firstOrNull()?.message ?: "Unknown error"
                Result.failure(AmazonClientException("$operation failed: $errorMessage", statusCode = response.status.value))
            } catch (e: Exception) {
                Result.failure(AmazonClientException("$operation failed: ${response.status}", statusCode = response.status.value))
            }
        }
    }

    private suspend fun handleOrderItemsResponse(
        response: HttpResponse,
        operation: String
    ): Result<AmazonOrderItemsResponse> {
        return if (response.status.isSuccess()) {
            try {
                val body: AmazonOrderItemsResponse = response.body()
                Result.success(body)
            } catch (e: Exception) {
                logger.error("Failed to parse response for $operation", e)
                Result.failure(AmazonClientException("Failed to parse response: ${e.message}", e))
            }
        } else {
            val errorBody = response.bodyAsText()
            logger.error("$operation failed: ${response.status} - $errorBody")
            Result.failure(AmazonClientException("$operation failed: ${response.status}", statusCode = response.status.value))
        }
    }

    private suspend fun handleAddressResponse(
        response: HttpResponse,
        operation: String
    ): Result<AmazonAddressResponse> {
        return if (response.status.isSuccess()) {
            try {
                val body: AmazonAddressResponse = response.body()
                Result.success(body)
            } catch (e: Exception) {
                logger.error("Failed to parse response for $operation", e)
                Result.failure(AmazonClientException("Failed to parse response: ${e.message}", e))
            }
        } else {
            val errorBody = response.bodyAsText()
            logger.error("$operation failed: ${response.status} - $errorBody")
            Result.failure(AmazonClientException("$operation failed: ${response.status}", statusCode = response.status.value))
        }
    }

    private suspend fun handleBuyerInfoResponse(
        response: HttpResponse,
        operation: String
    ): Result<AmazonOrderBuyerInfoResponse> {
        return if (response.status.isSuccess()) {
            try {
                val body: AmazonOrderBuyerInfoResponse = response.body()
                Result.success(body)
            } catch (e: Exception) {
                logger.error("Failed to parse response for $operation", e)
                Result.failure(AmazonClientException("Failed to parse response: ${e.message}", e))
            }
        } else {
            val errorBody = response.bodyAsText()
            logger.error("$operation failed: ${response.status} - $errorBody")
            Result.failure(AmazonClientException("$operation failed: ${response.status}", statusCode = response.status.value))
        }
    }

    private suspend fun handleOrderItemsBuyerInfoResponse(
        response: HttpResponse,
        operation: String
    ): Result<AmazonOrderItemsBuyerInfoResponse> {
        return if (response.status.isSuccess()) {
            try {
                val body: AmazonOrderItemsBuyerInfoResponse = response.body()
                Result.success(body)
            } catch (e: Exception) {
                logger.error("Failed to parse response for $operation", e)
                Result.failure(AmazonClientException("Failed to parse response: ${e.message}", e))
            }
        } else {
            val errorBody = response.bodyAsText()
            logger.error("$operation failed: ${response.status} - $errorBody")
            Result.failure(AmazonClientException("$operation failed: ${response.status}", statusCode = response.status.value))
        }
    }
}

/**
 * Exception for Amazon API client errors
 */
class AmazonClientException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int = 0
) : Exception(message, cause)
