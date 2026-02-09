package com.printnest.integrations.walmart

import com.printnest.integrations.redis.RedisService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Walmart OAuth 2.0 Authentication Service
 *
 * Handles:
 * - Client credentials OAuth flow
 * - Token caching with Redis
 * - Token validation
 * - Request header generation
 */
class WalmartAuthService(
    private val httpClient: HttpClient,
    private val redisService: RedisService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(WalmartAuthService::class.java)

    companion object {
        const val WALMART_BASE_URL = "https://marketplace.walmartapis.com"
        const val TOKEN_ENDPOINT = "/v3/token"
        const val TOKEN_DETAIL_ENDPOINT = "/v3/token/detail"
        const val SERVICE_NAME = "Walmart_PrintNest"
        const val TOKEN_CACHE_PREFIX = "walmart_token_"
        const val TOKEN_BUFFER_SECONDS = 300 // 5 minutes buffer before expiry
    }

    /**
     * Generate Basic Auth header from client credentials
     */
    fun generateBasicAuthHeader(clientId: String, clientSecret: String): String {
        val credentials = "$clientId:$clientSecret"
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encodedCredentials"
    }

    /**
     * Build standard Walmart API headers
     */
    fun buildAuthHeaders(accessToken: String? = null): Map<String, String> {
        val correlationId = UUID.randomUUID().toString()
        val headers = mutableMapOf(
            "Accept" to ContentType.Application.Json.toString(),
            "Content-Type" to ContentType.Application.Json.toString(),
            "WM_QOS.CORRELATION_ID" to correlationId,
            "WM_SVC.NAME" to SERVICE_NAME
        )

        accessToken?.let {
            headers["WM_SEC.ACCESS_TOKEN"] = it
        }

        return headers
    }

    /**
     * Build headers for token request (form-urlencoded)
     */
    fun buildTokenRequestHeaders(clientId: String, clientSecret: String): Map<String, String> {
        val correlationId = UUID.randomUUID().toString()
        return mapOf(
            "Accept" to ContentType.Application.Json.toString(),
            "Content-Type" to ContentType.Application.FormUrlEncoded.toString(),
            "Authorization" to generateBasicAuthHeader(clientId, clientSecret),
            "WM_QOS.CORRELATION_ID" to correlationId,
            "WM_SVC.NAME" to SERVICE_NAME
        )
    }

    /**
     * Get access token from Walmart OAuth endpoint
     */
    suspend fun getAccessToken(clientId: String, clientSecret: String): Result<WalmartTokenResponse> {
        return try {
            val url = "$WALMART_BASE_URL$TOKEN_ENDPOINT"
            val headers = buildTokenRequestHeaders(clientId, clientSecret)

            logger.debug("Requesting Walmart access token for clientId: ${clientId.take(8)}...")

            val response = httpClient.post(url) {
                headers.forEach { (key, value) -> header(key, value) }
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "client_credentials")
                }))
            }

            if (response.status.isSuccess()) {
                val tokenResponse: WalmartTokenResponse = response.body()
                logger.info("Successfully obtained Walmart access token, expires in ${tokenResponse.expiresIn}s")
                Result.success(tokenResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to get Walmart access token: ${response.status} - $errorBody")
                Result.failure(WalmartAuthException("Failed to get access token: ${response.status}", response.status.value))
            }
        } catch (e: Exception) {
            logger.error("Exception getting Walmart access token", e)
            Result.failure(WalmartAuthException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get or generate token with Redis caching
     *
     * @param storeId Unique store identifier for cache key
     * @param clientId Walmart client ID
     * @param clientSecret Walmart client secret
     * @return Access token or null if failed
     */
    suspend fun getOrGenerateToken(
        storeId: Long,
        clientId: String,
        clientSecret: String
    ): String? {
        val cacheKey = "$TOKEN_CACHE_PREFIX$storeId"

        // Try to get from cache
        try {
            val cachedToken = redisService.get(cacheKey)
            if (cachedToken != null) {
                // Validate the cached token
                if (isValidToken(cachedToken)) {
                    logger.debug("Using cached Walmart token for store $storeId")
                    return cachedToken
                } else {
                    logger.debug("Cached Walmart token for store $storeId is invalid, refreshing...")
                    redisService.delete(cacheKey)
                }
            }
        } catch (e: Exception) {
            logger.warn("Redis error while getting cached token: ${e.message}")
        }

        // Generate new token
        val tokenResult = getAccessToken(clientId, clientSecret)
        return tokenResult.fold(
            onSuccess = { tokenResponse ->
                try {
                    // Calculate TTL with buffer
                    val ttlSeconds = (tokenResponse.expiresIn - TOKEN_BUFFER_SECONDS).coerceAtLeast(60)

                    // Cache the token
                    redisService.set(cacheKey, tokenResponse.accessToken, ttlSeconds)
                    logger.info("Cached new Walmart token for store $storeId, TTL: ${ttlSeconds}s")
                } catch (e: Exception) {
                    logger.warn("Failed to cache Walmart token: ${e.message}")
                }
                tokenResponse.accessToken
            },
            onFailure = { error ->
                logger.error("Failed to generate Walmart access token for store $storeId: ${error.message}")
                null
            }
        )
    }

    /**
     * Validate if a token is still valid by calling Walmart's token detail endpoint
     */
    suspend fun isValidToken(token: String): Boolean {
        return try {
            val url = "$WALMART_BASE_URL$TOKEN_DETAIL_ENDPOINT"
            val headers = buildAuthHeaders(token)

            val response = httpClient.get(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }

            if (response.status.isSuccess()) {
                val detailResponse: WalmartTokenDetailResponse = response.body()
                detailResponse.isValid
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warn("Error validating Walmart token: ${e.message}")
            false
        }
    }

    /**
     * Test connection to Walmart API with provided credentials
     */
    suspend fun testConnection(clientId: String, clientSecret: String): Result<Boolean> {
        return try {
            val tokenResult = getAccessToken(clientId, clientSecret)
            tokenResult.fold(
                onSuccess = { tokenResponse ->
                    // Verify the token is valid
                    val isValid = isValidToken(tokenResponse.accessToken)
                    if (isValid) {
                        Result.success(true)
                    } else {
                        Result.failure(WalmartAuthException("Token validation failed", 401))
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error testing Walmart connection", e)
            Result.failure(WalmartAuthException("Connection test failed: ${e.message}", 0))
        }
    }

    /**
     * Invalidate cached token for a store
     */
    fun invalidateToken(storeId: Long) {
        try {
            val cacheKey = "$TOKEN_CACHE_PREFIX$storeId"
            redisService.delete(cacheKey)
            logger.info("Invalidated cached Walmart token for store $storeId")
        } catch (e: Exception) {
            logger.warn("Failed to invalidate Walmart token cache: ${e.message}")
        }
    }
}

/**
 * Walmart Authentication Exception
 */
class WalmartAuthException(
    message: String,
    val statusCode: Int
) : Exception(message)
