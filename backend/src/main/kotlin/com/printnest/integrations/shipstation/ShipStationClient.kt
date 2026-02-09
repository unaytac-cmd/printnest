package com.printnest.integrations.shipstation

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

class ShipStationClient(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ShipStationClient::class.java)
    private val baseUrl = "https://ssapi.shipstation.com"

    private fun getAuthHeader(apiKey: String, apiSecret: String): String {
        val credentials = "$apiKey:$apiSecret"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    /**
     * Get all stores from ShipStation account
     */
    suspend fun getStores(apiKey: String, apiSecret: String): Result<List<ShipStationStoreResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/stores") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey, apiSecret))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val stores: List<ShipStationStoreResponse> = response.body()
                Result.success(stores)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("ShipStation getStores failed: ${response.status} - $errorBody")
                Result.failure(ShipStationException("Failed to get stores: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("ShipStation getStores exception", e)
            Result.failure(ShipStationException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get orders from ShipStation
     */
    suspend fun getOrders(
        apiKey: String,
        apiSecret: String,
        storeId: Long? = null,
        orderStatus: String? = null,
        modifyDateStart: String? = null,
        modifyDateEnd: String? = null,
        createDateStart: String? = null,
        createDateEnd: String? = null,
        page: Int = 1,
        pageSize: Int = 100
    ): Result<ShipStationOrdersResponse> {
        return try {
            val response = httpClient.get("$baseUrl/orders") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey, apiSecret))
                header(HttpHeaders.ContentType, ContentType.Application.Json)

                storeId?.let { parameter("storeId", it) }
                orderStatus?.let { parameter("orderStatus", it) }
                modifyDateStart?.let { parameter("modifyDateStart", it) }
                modifyDateEnd?.let { parameter("modifyDateEnd", it) }
                createDateStart?.let { parameter("createDateStart", it) }
                createDateEnd?.let { parameter("createDateEnd", it) }
                parameter("page", page)
                parameter("pageSize", pageSize)
            }

            if (response.status.isSuccess()) {
                val orders: ShipStationOrdersResponse = response.body()
                Result.success(orders)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("ShipStation getOrders failed: ${response.status} - $errorBody")
                Result.failure(ShipStationException("Failed to get orders: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("ShipStation getOrders exception", e)
            Result.failure(ShipStationException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get a single order by ID
     */
    suspend fun getOrder(
        apiKey: String,
        apiSecret: String,
        orderId: Long
    ): Result<ShipStationOrderResponse> {
        return try {
            val response = httpClient.get("$baseUrl/orders/$orderId") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey, apiSecret))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val order: ShipStationOrderResponse = response.body()
                Result.success(order)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("ShipStation getOrder failed: ${response.status} - $errorBody")
                Result.failure(ShipStationException("Failed to get order: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("ShipStation getOrder exception", e)
            Result.failure(ShipStationException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Mark order as shipped
     */
    suspend fun markOrderAsShipped(
        apiKey: String,
        apiSecret: String,
        request: ShipStationMarkShippedRequest
    ): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/orders/markasshipped") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey, apiSecret))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("ShipStation markAsShipped failed: ${response.status} - $errorBody")
                Result.failure(ShipStationException("Failed to mark order as shipped: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("ShipStation markAsShipped exception", e)
            Result.failure(ShipStationException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Create shipping label
     */
    suspend fun createLabel(
        apiKey: String,
        apiSecret: String,
        request: ShipStationCreateLabelRequest
    ): Result<ShipStationLabelResponse> {
        return try {
            val response = httpClient.post("$baseUrl/shipments/createlabel") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey, apiSecret))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val label: ShipStationLabelResponse = response.body()
                Result.success(label)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("ShipStation createLabel failed: ${response.status} - $errorBody")
                Result.failure(ShipStationException("Failed to create label: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("ShipStation createLabel exception", e)
            Result.failure(ShipStationException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Validate API credentials by fetching stores
     */
    suspend fun validateCredentials(apiKey: String, apiSecret: String): Boolean {
        return getStores(apiKey, apiSecret).isSuccess
    }
}

class ShipStationException(
    message: String,
    val statusCode: Int
) : Exception(message)
