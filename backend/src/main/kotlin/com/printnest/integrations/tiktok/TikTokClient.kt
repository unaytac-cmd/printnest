package com.printnest.integrations.tiktok

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TikTok Shop API Client
 *
 * Handles all HTTP communication with TikTok Shop API including signature generation.
 */
class TikTokClient(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(TikTokClient::class.java)

    companion object {
        const val BASE_URL = "https://open-api.tiktokglobalshop.com"
        const val API_VERSION = "202309"
    }

    private val clientKey: String
        get() = System.getenv("TIKTOK_CLIENT_KEY") ?: ""

    private val clientSecret: String
        get() = System.getenv("TIKTOK_CLIENT_SECRET") ?: ""

    /**
     * Execute a TikTok API request with automatic signature generation
     */
    private suspend inline fun <reified T> executeRequest(
        path: String,
        method: HttpMethod,
        shopCipher: String? = null,
        accessToken: String,
        queryParams: Map<String, String> = emptyMap(),
        body: Any? = null
    ): Result<T> {
        return try {
            val timestamp = Instant.now().epochSecond
            val fullPath = "/$path"

            // Build query parameters
            val params = mutableMapOf(
                "app_key" to clientKey,
                "timestamp" to timestamp.toString()
            ).apply {
                shopCipher?.let { put("shop_cipher", it) }
                putAll(queryParams)
            }

            // Serialize body if present
            val bodyString = body?.let { json.encodeToString(it) }

            // Generate signature
            val signature = TikTokSignatureUtil.generateSign(
                path = fullPath,
                queryParams = params,
                body = bodyString,
                appSecret = clientSecret
            )

            params["sign"] = signature

            val url = "$BASE_URL$fullPath"

            val response = httpClient.request(url) {
                this.method = method
                params.forEach { (key, value) -> parameter(key, value) }
                header("x-tts-access-token", accessToken)
                contentType(ContentType.Application.Json)

                if (body != null && method != HttpMethod.Get) {
                    setBody(bodyString)
                }
            }

            if (response.status.isSuccess()) {
                val result: T = response.body()
                Result.success(result)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("TikTok API request failed: $path - ${response.status} - $errorBody")
                Result.failure(TikTokApiException("Request failed: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("TikTok API request exception: $path", e)
            Result.failure(TikTokApiException("Request error: ${e.message}", 0))
        }
    }

    /**
     * Search orders with filters
     *
     * @param shopCipher Shop cipher for multi-shop access
     * @param accessToken Valid access token
     * @param status Order status filter (optional)
     * @param pageSize Number of orders per page (default 100, max 100)
     * @param pageToken Pagination token for next page
     * @return Result containing TikTokOrdersSearchData
     */
    suspend fun getOrders(
        shopCipher: String,
        accessToken: String,
        status: TikTokOrderStatus? = null,
        pageSize: Int = 100,
        pageToken: String? = null
    ): Result<TikTokApiResponse<TikTokOrdersSearchData>> {
        val body = buildMap<String, Any> {
            status?.let { put("order_status", it.value) }
            put("page_size", pageSize.coerceIn(1, 100))
            pageToken?.let { put("page_token", it) }
        }

        return executeRequest(
            path = "order/$API_VERSION/orders/search",
            method = HttpMethod.Post,
            shopCipher = shopCipher,
            accessToken = accessToken,
            body = body
        )
    }

    /**
     * Get order details by order ID
     *
     * @param shopCipher Shop cipher for multi-shop access
     * @param accessToken Valid access token
     * @param orderId TikTok order ID
     * @return Result containing order details
     */
    suspend fun getOrderDetail(
        shopCipher: String,
        accessToken: String,
        orderId: String
    ): Result<TikTokApiResponse<TikTokOrder>> {
        return executeRequest(
            path = "order/$API_VERSION/orders/$orderId",
            method = HttpMethod.Get,
            shopCipher = shopCipher,
            accessToken = accessToken
        )
    }

    /**
     * Get available shipping providers for a delivery option
     *
     * @param shopCipher Shop cipher for multi-shop access
     * @param accessToken Valid access token
     * @param deliveryOptionId Delivery option ID from order
     * @return Result containing list of shipping providers
     */
    suspend fun getShippingProviders(
        shopCipher: String,
        accessToken: String,
        deliveryOptionId: String
    ): Result<TikTokApiResponse<TikTokShippingProvidersData>> {
        return executeRequest(
            path = "logistics/$API_VERSION/delivery_options/$deliveryOptionId/shipping_providers",
            method = HttpMethod.Get,
            shopCipher = shopCipher,
            accessToken = accessToken
        )
    }

    /**
     * Update order shipping information (send tracking number)
     *
     * @param shopCipher Shop cipher for multi-shop access
     * @param accessToken Valid access token
     * @param orderId TikTok order ID
     * @param trackingNumber Tracking number from carrier
     * @param shippingProviderId TikTok shipping provider ID
     * @return Result indicating success or failure
     */
    suspend fun updateShipment(
        shopCipher: String,
        accessToken: String,
        orderId: String,
        trackingNumber: String,
        shippingProviderId: String
    ): Result<TikTokUpdateShippingInfoResponse> {
        val body = TikTokUpdateShippingInfoRequest(
            trackingNumber = trackingNumber,
            shippingProviderId = shippingProviderId
        )

        return executeRequest(
            path = "fulfillment/$API_VERSION/orders/$orderId/shipping_info/update",
            method = HttpMethod.Post,
            shopCipher = shopCipher,
            accessToken = accessToken,
            body = body
        )
    }

    /**
     * Get product details by product ID
     *
     * @param shopCipher Shop cipher for multi-shop access
     * @param accessToken Valid access token
     * @param productId TikTok product ID
     * @return Result containing product details
     */
    suspend fun getProductDetails(
        shopCipher: String,
        accessToken: String,
        productId: String
    ): Result<TikTokApiResponse<TikTokProductData>> {
        return executeRequest(
            path = "product/$API_VERSION/products/$productId",
            method = HttpMethod.Get,
            shopCipher = shopCipher,
            accessToken = accessToken
        )
    }

    /**
     * Get authorized shops
     *
     * @param accessToken Valid access token
     * @return Result containing list of authorized shops
     */
    suspend fun getAuthorizedShops(accessToken: String): Result<TikTokApiResponse<TikTokShopsData>> {
        return executeRequest(
            path = "authorization/$API_VERSION/shops",
            method = HttpMethod.Get,
            shopCipher = null,
            accessToken = accessToken
        )
    }
}

/**
 * Utility object for generating TikTok API signatures
 */
object TikTokSignatureUtil {

    /**
     * Generate HMAC-SHA256 signature for TikTok API requests
     *
     * Steps:
     * 1. Extract all query parameters excluding "sign" and "access_token", reorder alphabetically
     * 2. Concatenate all parameters in the format {key}{value}
     * 3. Append to API request path
     * 4. If not multipart/form-data, append request body
     * 5. Wrap with app_secret
     * 6. Encode using HMAC-SHA256
     */
    fun generateSign(
        path: String,
        queryParams: Map<String, String>,
        body: String?,
        appSecret: String
    ): String {
        val excludeKeys = setOf("access_token", "sign")

        // Step 1 & 2: Sort and concatenate parameters
        val sortedParams = queryParams
            .filterKeys { it !in excludeKeys }
            .toSortedMap()
            .entries
            .joinToString("") { "${it.key}${it.value}" }

        // Step 3: Append to path
        var signString = "$path$sortedParams"

        // Step 4: Append body if present
        if (!body.isNullOrEmpty()) {
            signString += body
        }

        // Step 5: Wrap with app_secret
        signString = "$appSecret$signString$appSecret"

        // Step 6: HMAC-SHA256
        return hmacSha256(signString, appSecret)
    }

    private fun hmacSha256(data: String, secret: String): String {
        val algorithm = "HmacSHA256"
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm)
        val mac = Mac.getInstance(algorithm)
        mac.init(keySpec)
        val result = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return result.joinToString("") { "%02x".format(it) }
    }
}

/**
 * TikTok API Exception
 */
class TikTokApiException(
    message: String,
    val statusCode: Int
) : Exception(message)
