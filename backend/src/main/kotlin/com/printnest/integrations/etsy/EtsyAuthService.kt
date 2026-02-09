package com.printnest.integrations.etsy

import com.printnest.integrations.redis.RedisService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Etsy OAuth 2.0 with PKCE Authentication Service
 *
 * Handles the complete OAuth flow for Etsy API v3:
 * 1. Generate authorization URL with PKCE challenge
 * 2. Exchange authorization code for tokens
 * 3. Refresh expired tokens
 */
class EtsyAuthService(
    private val redisService: RedisService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(EtsyAuthService::class.java)

    companion object {
        // Etsy OAuth endpoints
        private const val ETSY_AUTH_URL = "https://www.etsy.com/oauth/connect"
        private const val ETSY_TOKEN_URL = "https://api.etsy.com/v3/public/oauth/token"

        // Required scopes for PrintNest
        private val REQUIRED_SCOPES = listOf(
            "transactions_r",  // Read transactions/orders
            "transactions_w",  // Write transactions (update tracking)
            "shops_r",         // Read shop info
            "listings_r"       // Read listings
        )

        // Redis key prefix for auth state
        private const val AUTH_STATE_PREFIX = "etsy:auth:state:"
        private const val AUTH_STATE_TTL = 600L // 10 minutes

        // Client ID from environment
        private val ETSY_CLIENT_ID: String
            get() = System.getenv("ETSY_CLIENT_ID") ?: throw IllegalStateException("ETSY_CLIENT_ID not configured")
    }

    /**
     * Generate OAuth authorization URL with PKCE
     *
     * @param tenantId The tenant initiating the connection
     * @param storeId Internal store ID for tracking
     * @param redirectUri The callback URL after authorization
     * @return Authorization URL to redirect user to
     */
    fun generateAuthUrl(
        tenantId: Long,
        storeId: Long,
        redirectUri: String
    ): String {
        logger.info("Generating Etsy OAuth URL for tenant $tenantId, store $storeId")

        // Generate PKCE code verifier and challenge
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        // Generate random state for CSRF protection
        val state = generateState()

        // Store auth state in Redis for callback validation
        val authState = EtsyAuthState(
            storeId = storeId,
            tenantId = tenantId,
            codeVerifier = codeVerifier,
            state = state,
            redirectUri = redirectUri
        )

        val stateKey = "$AUTH_STATE_PREFIX$state"
        val stateJson = json.encodeToString(authState)
        redisService.set(stateKey, stateJson, AUTH_STATE_TTL)

        logger.debug("Stored auth state in Redis with key: $stateKey")

        // Build authorization URL
        val scopeString = REQUIRED_SCOPES.joinToString(" ")
        val params = mapOf(
            "response_type" to "code",
            "client_id" to ETSY_CLIENT_ID,
            "redirect_uri" to redirectUri,
            "scope" to scopeString,
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }

        val authUrl = "$ETSY_AUTH_URL?$queryString"
        logger.info("Generated Etsy OAuth URL for store $storeId")

        return authUrl
    }

    /**
     * Retrieve stored auth state from Redis
     *
     * @param state The state parameter from callback
     * @return Auth state if found and valid, null otherwise
     */
    fun getAuthState(state: String): EtsyAuthState? {
        val stateKey = "$AUTH_STATE_PREFIX$state"
        val stateJson = redisService.get(stateKey)

        if (stateJson == null) {
            logger.warn("Auth state not found for state: $state")
            return null
        }

        return try {
            val authState = json.decodeFromString<EtsyAuthState>(stateJson)
            // Delete state after retrieval (one-time use)
            redisService.delete(stateKey)
            authState
        } catch (e: Exception) {
            logger.error("Failed to parse auth state", e)
            null
        }
    }

    /**
     * Build token exchange request body
     *
     * @param code Authorization code from callback
     * @param codeVerifier PKCE code verifier
     * @param redirectUri The callback URL used in authorization
     * @return URL-encoded form body for token request
     */
    fun buildTokenRequestBody(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): String {
        val params = mapOf(
            "grant_type" to "authorization_code",
            "client_id" to ETSY_CLIENT_ID,
            "redirect_uri" to redirectUri,
            "code" to code,
            "code_verifier" to codeVerifier
        )

        return params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
    }

    /**
     * Build refresh token request body
     *
     * @param refreshToken The refresh token to use
     * @return URL-encoded form body for refresh request
     */
    fun buildRefreshTokenRequestBody(refreshToken: String): String {
        val params = mapOf(
            "grant_type" to "refresh_token",
            "client_id" to ETSY_CLIENT_ID,
            "refresh_token" to refreshToken
        )

        return params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
    }

    /**
     * Get Etsy token endpoint URL
     */
    fun getTokenEndpoint(): String = ETSY_TOKEN_URL

    /**
     * Get Etsy client ID for API requests
     */
    fun getClientId(): String = ETSY_CLIENT_ID

    /**
     * Calculate token expiry timestamp
     *
     * @param expiresIn Seconds until token expires
     * @return Unix timestamp of expiry
     */
    fun calculateTokenExpiry(expiresIn: Long): Long {
        return System.currentTimeMillis() / 1000 + expiresIn
    }

    /**
     * Check if a token is expired or about to expire
     *
     * @param expiryTimestamp Unix timestamp of token expiry
     * @param bufferSeconds Seconds before actual expiry to consider expired (default 5 minutes)
     * @return true if token should be refreshed
     */
    fun isTokenExpired(expiryTimestamp: Long, bufferSeconds: Long = 300): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= (expiryTimestamp - bufferSeconds)
    }

    // =====================================================
    // PKCE HELPERS
    // =====================================================

    /**
     * Generate a cryptographically random code verifier
     * Per RFC 7636, should be 43-128 characters of unreserved URI characters
     */
    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32) // 256 bits
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Generate code challenge from verifier using SHA-256
     * code_challenge = BASE64URL(SHA256(code_verifier))
     */
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /**
     * Generate random state parameter for CSRF protection
     */
    private fun generateState(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16) // 128 bits
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
