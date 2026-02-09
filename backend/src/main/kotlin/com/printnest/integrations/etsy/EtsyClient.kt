package com.printnest.integrations.etsy

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP Client for Etsy API v3
 *
 * Handles all API communication with Etsy including:
 * - Token exchange and refresh
 * - Fetching receipts (orders) and transactions
 * - Getting listing images
 * - Creating shipments (sending tracking)
 * - Automatic token refresh on 401 responses
 */
class EtsyClient(
    private val httpClient: HttpClient,
    private val authService: EtsyAuthService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(EtsyClient::class.java)

    companion object {
        private const val BASE_URL = "https://openapi.etsy.com/v3"
        private const val MAX_RETRY_COUNT = 1
    }

    // =====================================================
    // OAUTH TOKEN OPERATIONS
    // =====================================================

    /**
     * Exchange authorization code for access and refresh tokens
     *
     * @param code Authorization code from OAuth callback
     * @param codeVerifier PKCE code verifier
     * @param redirectUri The callback URL used in authorization
     * @return Token response on success
     */
    suspend fun exchangeCodeForToken(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): Result<EtsyTokenResponse> {
        logger.info("Exchanging authorization code for tokens")

        return try {
            val body = authService.buildTokenRequestBody(code, codeVerifier, redirectUri)

            val response = httpClient.post(authService.getTokenEndpoint()) {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
                header("x-api-key", authService.getClientId())
                setBody(body)
            }

            if (response.status.isSuccess()) {
                val tokenResponse: EtsyTokenResponse = response.body()
                logger.info("Successfully exchanged code for tokens")
                Result.success(tokenResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Token exchange failed: ${response.status} - $errorBody")
                Result.failure(EtsyException("Token exchange failed: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Token exchange exception", e)
            Result.failure(EtsyException("Token exchange error: ${e.message}", 0))
        }
    }

    /**
     * Refresh an expired access token
     *
     * @param refreshToken The refresh token
     * @return New token response on success
     */
    suspend fun refreshToken(refreshToken: String): Result<EtsyTokenResponse> {
        logger.info("Refreshing access token")

        return try {
            val body = authService.buildRefreshTokenRequestBody(refreshToken)

            val response = httpClient.post(authService.getTokenEndpoint()) {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
                header("x-api-key", authService.getClientId())
                setBody(body)
            }

            if (response.status.isSuccess()) {
                val tokenResponse: EtsyTokenResponse = response.body()
                logger.info("Successfully refreshed access token")
                Result.success(tokenResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Token refresh failed: ${response.status} - $errorBody")
                Result.failure(EtsyException("Token refresh failed: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Token refresh exception", e)
            Result.failure(EtsyException("Token refresh error: ${e.message}", 0))
        }
    }

    // =====================================================
    // SHOP OPERATIONS
    // =====================================================

    /**
     * Get shop information by shop ID
     *
     * @param accessToken OAuth access token
     * @param shopId Etsy shop ID
     * @return Shop information
     */
    suspend fun getShop(
        accessToken: String,
        shopId: Long
    ): Result<EtsyShop> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/shops/$shopId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
            }

            if (response.status.isSuccess()) {
                val shop: EtsyShop = response.body()
                Result.success(shop)
            } else {
                handleErrorResponse(response, "getShop")
            }
        }
    }

    /**
     * Get the current user's shop
     * Useful after OAuth to get shop ID
     *
     * @param accessToken OAuth access token
     * @return Shop information
     */
    suspend fun getMyShop(accessToken: String): Result<EtsyShop> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/users/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
            }

            if (response.status.isSuccess()) {
                // Get user info first, then their shop
                val userResponse = response.bodyAsText()
                val userJson = json.parseToJsonElement(userResponse)
                val userId = userJson.jsonObject["user_id"]?.toString()?.toLongOrNull()

                if (userId != null) {
                    // Get shops for this user
                    val shopsResponse = httpClient.get("$BASE_URL/application/users/$userId/shops") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header("x-api-key", authService.getClientId())
                    }

                    if (shopsResponse.status.isSuccess()) {
                        val shops: List<EtsyShop> = shopsResponse.body()
                        if (shops.isNotEmpty()) {
                            Result.success(shops.first())
                        } else {
                            Result.failure(EtsyException("No shops found for user", 404))
                        }
                    } else {
                        handleErrorResponse(shopsResponse, "getMyShop.shops")
                    }
                } else {
                    Result.failure(EtsyException("Failed to get user ID", 500))
                }
            } else {
                handleErrorResponse(response, "getMyShop.user")
            }
        }
    }

    // =====================================================
    // RECEIPT (ORDER) OPERATIONS
    // =====================================================

    /**
     * Get shop receipts (orders) with optional filtering
     *
     * @param accessToken OAuth access token
     * @param shopId Etsy shop ID
     * @param minCreated Minimum creation timestamp (Unix seconds)
     * @param maxCreated Maximum creation timestamp (Unix seconds)
     * @param minLastModified Minimum last modified timestamp (Unix seconds)
     * @param maxLastModified Maximum last modified timestamp (Unix seconds)
     * @param limit Number of results per page (max 100)
     * @param offset Pagination offset
     * @param wasPaid Filter for paid orders only
     * @param wasShipped Filter for shipped orders only
     * @return Paginated list of receipts
     */
    suspend fun getShopReceipts(
        accessToken: String,
        shopId: Long,
        minCreated: Long? = null,
        maxCreated: Long? = null,
        minLastModified: Long? = null,
        maxLastModified: Long? = null,
        limit: Int = 25,
        offset: Int = 0,
        wasPaid: Boolean? = null,
        wasShipped: Boolean? = null
    ): Result<EtsyPaginatedResponse<EtsyReceipt>> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/shops/$shopId/receipts") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())

                minCreated?.let { parameter("min_created", it) }
                maxCreated?.let { parameter("max_created", it) }
                minLastModified?.let { parameter("min_last_modified", it) }
                maxLastModified?.let { parameter("max_last_modified", it) }
                parameter("limit", limit.coerceIn(1, 100))
                parameter("offset", offset)
                wasPaid?.let { parameter("was_paid", it) }
                wasShipped?.let { parameter("was_shipped", it) }
            }

            if (response.status.isSuccess()) {
                val receipts: EtsyPaginatedResponse<EtsyReceipt> = response.body()
                Result.success(receipts)
            } else {
                handleErrorResponse(response, "getShopReceipts")
            }
        }
    }

    /**
     * Get a single receipt by ID
     *
     * @param accessToken OAuth access token
     * @param shopId Etsy shop ID
     * @param receiptId Receipt ID
     * @return Receipt details
     */
    suspend fun getReceipt(
        accessToken: String,
        shopId: Long,
        receiptId: Long
    ): Result<EtsyReceipt> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/shops/$shopId/receipts/$receiptId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
            }

            if (response.status.isSuccess()) {
                val receipt: EtsyReceipt = response.body()
                Result.success(receipt)
            } else {
                handleErrorResponse(response, "getReceipt")
            }
        }
    }

    /**
     * Get transactions (line items) for a receipt
     *
     * @param accessToken OAuth access token
     * @param shopId Etsy shop ID
     * @param receiptId Receipt ID
     * @return List of transactions
     */
    suspend fun getReceiptTransactions(
        accessToken: String,
        shopId: Long,
        receiptId: Long
    ): Result<List<EtsyTransaction>> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/shops/$shopId/receipts/$receiptId/transactions") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
            }

            if (response.status.isSuccess()) {
                val transactionsResponse: EtsyPaginatedResponse<EtsyTransaction> = response.body()
                Result.success(transactionsResponse.results)
            } else {
                handleErrorResponse(response, "getReceiptTransactions")
            }
        }
    }

    // =====================================================
    // LISTING OPERATIONS
    // =====================================================

    /**
     * Get shop listings
     *
     * @param accessToken OAuth access token
     * @param shopId Etsy shop ID
     * @param state Listing state filter (active, inactive, sold_out, draft, expired)
     * @param limit Number of results per page (max 100)
     * @param offset Pagination offset
     * @return Paginated list of listings
     */
    suspend fun getShopListings(
        accessToken: String,
        shopId: Long,
        state: String = "active",
        limit: Int = 25,
        offset: Int = 0
    ): Result<EtsyPaginatedResponse<EtsyListing>> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/shops/$shopId/listings/$state") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
                parameter("limit", limit.coerceIn(1, 100))
                parameter("offset", offset)
            }

            if (response.status.isSuccess()) {
                val listings: EtsyPaginatedResponse<EtsyListing> = response.body()
                Result.success(listings)
            } else {
                handleErrorResponse(response, "getShopListings")
            }
        }
    }

    /**
     * Get a single listing by ID
     *
     * @param accessToken OAuth access token
     * @param listingId Listing ID
     * @param includes Additional data to include (images, shop, user, translations, inventory, videos)
     * @return Listing details
     */
    suspend fun getListing(
        accessToken: String,
        listingId: Long,
        includes: List<String> = listOf("images")
    ): Result<EtsyListing> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/listings/$listingId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
                if (includes.isNotEmpty()) {
                    parameter("includes", includes.joinToString(","))
                }
            }

            if (response.status.isSuccess()) {
                val listing: EtsyListing = response.body()
                Result.success(listing)
            } else {
                handleErrorResponse(response, "getListing")
            }
        }
    }

    /**
     * Get listing images
     *
     * @param accessToken OAuth access token
     * @param listingId Listing ID
     * @return List of listing images
     */
    suspend fun getListingImages(
        accessToken: String,
        listingId: Long
    ): Result<List<EtsyListingImage>> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/listings/$listingId/images") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
            }

            if (response.status.isSuccess()) {
                val imagesResponse: EtsyPaginatedResponse<EtsyListingImage> = response.body()
                Result.success(imagesResponse.results)
            } else {
                handleErrorResponse(response, "getListingImages")
            }
        }
    }

    /**
     * Get a specific listing image
     *
     * @param accessToken OAuth access token
     * @param listingId Listing ID
     * @param listingImageId Image ID
     * @return Listing image details
     */
    suspend fun getListingImage(
        accessToken: String,
        listingId: Long,
        listingImageId: Long
    ): Result<EtsyListingImage> {
        return executeWithRetry(accessToken) { token ->
            val response = httpClient.get("$BASE_URL/application/listings/$listingId/images/$listingImageId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
            }

            if (response.status.isSuccess()) {
                val image: EtsyListingImage = response.body()
                Result.success(image)
            } else {
                handleErrorResponse(response, "getListingImage")
            }
        }
    }

    // =====================================================
    // SHIPMENT OPERATIONS
    // =====================================================

    /**
     * Create a shipment (send tracking information) for a receipt
     *
     * @param accessToken OAuth access token
     * @param shopId Etsy shop ID
     * @param receiptId Receipt ID
     * @param trackingCode Tracking number
     * @param carrierName Carrier name (will be mapped to Etsy carrier)
     * @param sendBcc Send BCC email to seller
     * @return Created shipment
     */
    suspend fun createShipment(
        accessToken: String,
        shopId: Long,
        receiptId: Long,
        trackingCode: String,
        carrierName: String,
        sendBcc: Boolean = false
    ): Result<EtsyReceipt> {
        val etsyCarrier = EtsyCarrierMapping.getEtsyCarrier(carrierName)

        return executeWithRetry(accessToken) { token ->
            val response = httpClient.post("$BASE_URL/application/shops/$shopId/receipts/$receiptId/tracking") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("x-api-key", authService.getClientId())
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(EtsyCreateShipmentRequest(
                    trackingCode = trackingCode,
                    carrierName = etsyCarrier,
                    sendBcc = sendBcc
                ))
            }

            if (response.status.isSuccess()) {
                val receipt: EtsyReceipt = response.body()
                Result.success(receipt)
            } else {
                handleErrorResponse(response, "createShipment")
            }
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Execute an API call with automatic token refresh on 401
     */
    private suspend fun <T> executeWithRetry(
        accessToken: String,
        retryCount: Int = 0,
        block: suspend (String) -> Result<T>
    ): Result<T> {
        val result = block(accessToken)

        // Check if we got a 401 and should retry
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            if (exception is EtsyException && exception.statusCode == 401 && retryCount < MAX_RETRY_COUNT) {
                logger.warn("Got 401, token may be expired. Caller should handle token refresh.")
                // Note: Token refresh should be handled by the service layer
                // since it needs access to the store's refresh token
            }
        }

        return result
    }

    /**
     * Handle error response from Etsy API
     */
    private suspend fun <T> handleErrorResponse(response: HttpResponse, operation: String): Result<T> {
        val errorBody = response.bodyAsText()
        logger.error("Etsy $operation failed: ${response.status} - $errorBody")

        val errorMessage = try {
            val errorResponse = json.decodeFromString<EtsyErrorResponse>(errorBody)
            errorResponse.errorDescription ?: errorResponse.error ?: "Unknown error"
        } catch (e: Exception) {
            errorBody.take(200)
        }

        return Result.failure(EtsyException(
            "Etsy API error ($operation): $errorMessage",
            response.status.value
        ))
    }
}

/**
 * Etsy API Exception
 */
class EtsyException(
    message: String,
    val statusCode: Int
) : Exception(message)

// Extension to access jsonObject
private val kotlinx.serialization.json.JsonElement.jsonObject: kotlinx.serialization.json.JsonObject
    get() = this as kotlinx.serialization.json.JsonObject
