package com.printnest.integrations.tiktok

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
 * TikTok Shop OAuth 2.0 Authentication Service
 *
 * Handles OAuth flow, token exchange, and token refresh for TikTok Shop API integration.
 */
class TikTokAuthService(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(TikTokAuthService::class.java)

    companion object {
        const val TIKTOK_AUTH_URL = "https://services.tiktokshop.com/open/authorize"
        const val TIKTOK_TOKEN_URL = "https://auth.tiktok-shops.com/api/v2/token/get"
        const val TIKTOK_REFRESH_URL = "https://auth.tiktok-shops.com/api/v2/token/refresh"
        const val TIKTOK_SHOPS_URL = "https://open-api.tiktokglobalshop.com/authorization/202309/shops"

        const val GRANT_TYPE_AUTH_CODE = "authorized_code"
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
    }

    private val clientKey: String
        get() = System.getenv("TIKTOK_CLIENT_KEY") ?: ""

    private val clientSecret: String
        get() = System.getenv("TIKTOK_CLIENT_SECRET") ?: ""

    private val serviceId: String
        get() = System.getenv("TIKTOK_SERVICE_ID") ?: ""

    private val redirectUri: String
        get() = System.getenv("TIKTOK_REDIRECT_URI") ?: "https://api.printnest.com/api/v1/tiktok/callback"

    /**
     * Generate OAuth authorization URL for TikTok Shop
     *
     * @param storeId The internal store ID to use as state parameter
     * @param customRedirectUri Optional custom redirect URI
     * @return The authorization URL to redirect the user to
     */
    fun generateAuthUrl(storeId: String, customRedirectUri: String? = null): String {
        val params = buildString {
            append("service_id=${URLEncoder.encode(serviceId, "UTF-8")}")
            append("&state=${URLEncoder.encode(storeId, "UTF-8")}")
        }

        val authUrl = "$TIKTOK_AUTH_URL?$params"
        logger.info("Generated TikTok auth URL for store: $storeId")
        return authUrl
    }

    /**
     * Exchange authorization code for access token
     *
     * @param code The authorization code received from TikTok callback
     * @return Result containing TikTokTokenData or error
     */
    suspend fun exchangeCodeForToken(code: String): Result<TikTokTokenData> {
        if (clientKey.isBlank() || clientSecret.isBlank()) {
            return Result.failure(TikTokAuthException("TIKTOK_CLIENT_KEY or TIKTOK_CLIENT_SECRET not configured"))
        }

        return try {
            logger.info("Exchanging authorization code for access token")

            val response = httpClient.get(TIKTOK_TOKEN_URL) {
                parameter("app_key", clientKey)
                parameter("app_secret", clientSecret)
                parameter("auth_code", code)
                parameter("grant_type", GRANT_TYPE_AUTH_CODE)
                contentType(ContentType.Application.FormUrlEncoded)
            }

            if (response.status.isSuccess()) {
                val tokenResponse: TikTokTokenResponse = response.body()

                if (tokenResponse.code == 0 && tokenResponse.data != null) {
                    logger.info("Successfully exchanged code for access token")
                    Result.success(tokenResponse.data)
                } else {
                    val errorMsg = "TikTok token exchange failed: ${tokenResponse.message}"
                    logger.error(errorMsg)
                    Result.failure(TikTokAuthException(errorMsg))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("TikTok token exchange failed: ${response.status} - $errorBody")
                Result.failure(TikTokAuthException("Token exchange failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Exception during token exchange", e)
            Result.failure(TikTokAuthException("Token exchange error: ${e.message}"))
        }
    }

    /**
     * Refresh an expired access token using refresh token
     *
     * @param refreshToken The refresh token
     * @return Result containing new TikTokTokenData or error
     */
    suspend fun refreshToken(refreshToken: String): Result<TikTokTokenData> {
        if (clientKey.isBlank() || clientSecret.isBlank()) {
            return Result.failure(TikTokAuthException("TIKTOK_CLIENT_KEY or TIKTOK_CLIENT_SECRET not configured"))
        }

        return try {
            logger.info("Refreshing TikTok access token")

            val response = httpClient.get(TIKTOK_REFRESH_URL) {
                parameter("app_key", clientKey)
                parameter("app_secret", clientSecret)
                parameter("refresh_token", refreshToken)
                parameter("grant_type", GRANT_TYPE_REFRESH_TOKEN)
            }

            if (response.status.isSuccess()) {
                val tokenResponse: TikTokTokenResponse = response.body()

                if (tokenResponse.code == 0 && tokenResponse.data != null) {
                    logger.info("Successfully refreshed access token")
                    Result.success(tokenResponse.data)
                } else {
                    val errorMsg = "TikTok token refresh failed: ${tokenResponse.message}"
                    logger.error(errorMsg)
                    Result.failure(TikTokAuthException(errorMsg))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("TikTok token refresh failed: ${response.status} - $errorBody")
                Result.failure(TikTokAuthException("Token refresh failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Exception during token refresh", e)
            Result.failure(TikTokAuthException("Token refresh error: ${e.message}"))
        }
    }

    /**
     * Get authorized shops for an access token
     *
     * @param accessToken Valid access token
     * @return Result containing list of TikTokShop or error
     */
    suspend fun getAuthorizedShops(accessToken: String): Result<List<TikTokShop>> {
        return try {
            logger.info("Fetching authorized TikTok shops")

            val timestamp = Instant.now().epochSecond
            val queryParams = mapOf(
                "app_key" to clientKey,
                "timestamp" to timestamp.toString()
            )

            // Generate signature
            val signature = TikTokSignatureUtil.generateSign(
                path = "/authorization/202309/shops",
                queryParams = queryParams,
                body = null,
                appSecret = clientSecret
            )

            val response = httpClient.get(TIKTOK_SHOPS_URL) {
                parameter("app_key", clientKey)
                parameter("timestamp", timestamp)
                parameter("sign", signature)
                header("x-tts-access-token", accessToken)
                contentType(ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val shopsResponse: TikTokApiResponse<TikTokShopsData> = response.body()

                if (shopsResponse.code == 0 && shopsResponse.data != null) {
                    val shops = shopsResponse.data.shops
                    logger.info("Found ${shops.size} authorized shops")
                    Result.success(shops)
                } else {
                    val errorMsg = "Failed to get authorized shops: ${shopsResponse.message}"
                    logger.error(errorMsg)
                    Result.failure(TikTokAuthException(errorMsg))
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to get authorized shops: ${response.status} - $errorBody")
                Result.failure(TikTokAuthException("Failed to get shops: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Exception getting authorized shops", e)
            Result.failure(TikTokAuthException("Error getting shops: ${e.message}"))
        }
    }

    /**
     * Check if an access token is expired
     *
     * @param expireAt Token expiration timestamp (Unix epoch seconds)
     * @return true if token is expired
     */
    fun isTokenExpired(expireAt: Long): Boolean {
        return Instant.now().epochSecond > expireAt
    }

    /**
     * Get a valid access token, refreshing if necessary
     *
     * @param store TikTok store with token information
     * @param updateTokenCallback Callback to persist updated tokens
     * @return Result containing valid access token or error
     */
    suspend fun getValidAccessToken(
        store: TikTokStore,
        updateTokenCallback: suspend (TikTokTokenData) -> Unit
    ): Result<String> {
        val accessToken = store.accessToken
        val refreshTokenValue = store.refreshToken
        val accessTokenExpireAt = store.accessTokenExpireAt
        val refreshTokenExpireAt = store.refreshTokenExpireAt

        if (accessToken.isNullOrBlank()) {
            return Result.failure(TikTokAuthException("No access token available. Please re-authorize the store."))
        }

        if (accessTokenExpireAt == null || refreshTokenExpireAt == null) {
            return Result.failure(TikTokAuthException("Token expiration information missing."))
        }

        // Check if access token is still valid
        if (!isTokenExpired(accessTokenExpireAt)) {
            return Result.success(accessToken)
        }

        // Access token expired, check refresh token
        if (refreshTokenValue.isNullOrBlank()) {
            return Result.failure(TikTokAuthException("No refresh token available. Please re-authorize the store."))
        }

        if (isTokenExpired(refreshTokenExpireAt)) {
            return Result.failure(TikTokAuthException("Refresh token expired. Please re-authorize the store."))
        }

        // Refresh the access token
        return refreshToken(refreshTokenValue).fold(
            onSuccess = { newTokenData ->
                // Persist new tokens via callback
                updateTokenCallback(newTokenData)
                Result.success(newTokenData.accessToken)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }
}

/**
 * TikTok Authentication Exception
 */
class TikTokAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
