package com.printnest.integrations.amazon

import com.printnest.domain.service.CacheService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Instant

/**
 * Amazon LWA (Login with Amazon) Authentication Service
 * Handles OAuth flow and token management for Amazon SP-API
 */
class AmazonAuthService(
    private val httpClient: HttpClient,
    private val cacheService: CacheService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(AmazonAuthService::class.java)

    // Environment variables for Amazon credentials
    private val clientId: String
        get() = System.getenv("AMAZON_CLIENT_ID") ?: throw AmazonAuthException("AMAZON_CLIENT_ID not configured")

    private val clientSecret: String
        get() = System.getenv("AMAZON_CLIENT_SECRET") ?: throw AmazonAuthException("AMAZON_CLIENT_SECRET not configured")

    private val applicationId: String
        get() = System.getenv("AMAZON_APPLICATION_ID") ?: throw AmazonAuthException("AMAZON_APPLICATION_ID not configured")

    private val redirectUri: String
        get() = System.getenv("AMAZON_REDIRECT_URI") ?: "https://api.printnest.com/api/v1/amazon/callback"

    /**
     * Generate Amazon authorization URL for OAuth flow
     * @param storeId The internal store ID to use as state parameter
     * @param customRedirectUri Optional custom redirect URI
     * @return Authorization URL for user to authorize the app
     */
    fun generateAuthUrl(storeId: Long, customRedirectUri: String? = null): AmazonAuthUrlResponse {
        val state = "store_$storeId"
        val redirect = customRedirectUri ?: redirectUri

        val params = mapOf(
            "application_id" to applicationId,
            "version" to "beta",
            "state" to state,
            "redirect_uri" to redirect
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        val authUrl = "${AmazonConstants.AMAZON_AUTH_URL}?$queryString"

        logger.info("Generated Amazon auth URL for store $storeId")
        return AmazonAuthUrlResponse(authUrl = authUrl, state = state)
    }

    /**
     * Exchange authorization code for access and refresh tokens
     * @param code The authorization code from Amazon callback
     * @return LWA token response with access_token and refresh_token
     */
    suspend fun exchangeCodeForToken(code: String): Result<LwaTokenResponse> {
        logger.info("Exchanging authorization code for tokens")

        return try {
            val response = httpClient.submitForm(
                url = AmazonConstants.LWA_TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", AmazonConstants.AUTH_GRANT_TYPE)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("code", code)
                    append("redirect_uri", redirectUri)
                }
            )

            if (response.status.isSuccess()) {
                val tokenResponse: LwaTokenResponse = response.body()
                logger.info("Successfully exchanged code for tokens")
                Result.success(tokenResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to exchange code: ${response.status} - $errorBody")
                try {
                    val errorResponse = json.decodeFromString<LwaErrorResponse>(errorBody)
                    Result.failure(AmazonAuthException("Token exchange failed: ${errorResponse.errorDescription ?: errorResponse.error}"))
                } catch (e: Exception) {
                    Result.failure(AmazonAuthException("Token exchange failed: ${response.status}"))
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during token exchange", e)
            Result.failure(AmazonAuthException("Token exchange error: ${e.message}"))
        }
    }

    /**
     * Refresh access token using refresh token
     * @param refreshToken The refresh token from initial authorization
     * @return New LWA token response with fresh access_token
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<LwaTokenResponse> {
        logger.debug("Refreshing Amazon access token")

        return try {
            val response = httpClient.submitForm(
                url = AmazonConstants.LWA_TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", AmazonConstants.REFRESH_TOKEN_GRANT_TYPE)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("refresh_token", refreshToken)
                }
            )

            if (response.status.isSuccess()) {
                val tokenResponse: LwaTokenResponse = response.body()
                logger.debug("Successfully refreshed access token")
                Result.success(tokenResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to refresh token: ${response.status} - $errorBody")
                try {
                    val errorResponse = json.decodeFromString<LwaErrorResponse>(errorBody)
                    Result.failure(AmazonAuthException("Token refresh failed: ${errorResponse.errorDescription ?: errorResponse.error}"))
                } catch (e: Exception) {
                    Result.failure(AmazonAuthException("Token refresh failed: ${response.status}"))
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during token refresh", e)
            Result.failure(AmazonAuthException("Token refresh error: ${e.message}"))
        }
    }

    /**
     * Get valid access token for a store, refreshing if needed
     * Uses Redis cache to store and retrieve tokens
     *
     * @param storeId The internal store ID
     * @param refreshToken The stored refresh token for this store
     * @return Valid access token
     */
    suspend fun getAccessToken(storeId: Long, refreshToken: String): Result<String> {
        val cacheKey = "amazon_token:$storeId"

        // Try to get cached token
        val cachedToken = cacheService.get(cacheKey)
        if (cachedToken != null) {
            logger.debug("Using cached access token for store $storeId")
            return Result.success(cachedToken)
        }

        // Token not in cache or expired, refresh it
        logger.info("Access token not in cache for store $storeId, refreshing...")
        return refreshAccessToken(refreshToken).map { tokenResponse ->
            // Cache the new token (expires_in is in seconds, cache for slightly less)
            val cacheTtlSeconds = (tokenResponse.expiresIn - AmazonConstants.TOKEN_EXPIRY_BUFFER_SECONDS).coerceAtLeast(60)
            cacheService.set(cacheKey, tokenResponse.accessToken, cacheTtlSeconds.toLong())

            logger.info("Cached new access token for store $storeId (TTL: ${cacheTtlSeconds}s)")
            tokenResponse.accessToken
        }
    }

    /**
     * Invalidate cached token for a store (e.g., when disconnecting)
     * @param storeId The internal store ID
     */
    fun invalidateToken(storeId: Long) {
        val cacheKey = "amazon_token:$storeId"
        cacheService.delete(cacheKey)
        logger.info("Invalidated cached token for store $storeId")
    }

    /**
     * Check if a refresh token is still valid by attempting to get an access token
     * @param storeId The internal store ID
     * @param refreshToken The refresh token to validate
     * @return true if the refresh token is valid
     */
    suspend fun isTokenValid(storeId: Long, refreshToken: String): Boolean {
        return getAccessToken(storeId, refreshToken).isSuccess
    }

    /**
     * Parse the state parameter from OAuth callback to extract store ID
     * @param state The state parameter from callback
     * @return Store ID if valid, null otherwise
     */
    fun parseStateToStoreId(state: String): Long? {
        return try {
            if (state.startsWith("store_")) {
                state.removePrefix("store_").toLongOrNull()
            } else {
                state.toLongOrNull()
            }
        } catch (e: Exception) {
            logger.error("Failed to parse state parameter: $state", e)
            null
        }
    }
}

/**
 * Exception for Amazon authentication errors
 */
class AmazonAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
