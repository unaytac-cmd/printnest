package com.printnest.integrations.shopify

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Shopify OAuth 2.0 Authentication Service
 *
 * Handles the OAuth flow for connecting Shopify stores:
 * 1. Generate auth URL to redirect merchant
 * 2. Exchange authorization code for access token
 * 3. Verify HMAC signatures for webhooks and requests
 */
class ShopifyAuthService(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ShopifyAuthService::class.java)

    companion object {
        const val API_VERSION = "2024-01"

        // Default scopes required for order management
        val DEFAULT_SCOPES = listOf(
            "read_orders",
            "write_orders",
            "read_products",
            "read_customers",
            "read_assigned_fulfillment_orders",
            "read_fulfillments",
            "write_fulfillments",
            "read_merchant_managed_fulfillment_orders",
            "write_merchant_managed_fulfillment_orders"
        )
    }

    private val clientId: String
        get() = System.getenv("SHOPIFY_CLIENT_ID") ?: throw IllegalStateException("SHOPIFY_CLIENT_ID not configured")

    private val clientSecret: String
        get() = System.getenv("SHOPIFY_CLIENT_SECRET") ?: throw IllegalStateException("SHOPIFY_CLIENT_SECRET not configured")

    private val redirectUri: String
        get() = System.getenv("SHOPIFY_REDIRECT_URI") ?: "https://api.printnest.com/api/v1/shopify/callback"

    /**
     * Generate the OAuth authorization URL for a Shopify store
     *
     * @param shop The shop domain (e.g., "my-store.myshopify.com")
     * @param state Optional state parameter for CSRF protection (e.g., tenant ID)
     * @param scopes Optional list of scopes (defaults to DEFAULT_SCOPES)
     * @param customRedirectUri Optional custom redirect URI
     * @return The full authorization URL
     */
    fun generateAuthUrl(
        shop: String,
        state: String? = null,
        scopes: List<String> = DEFAULT_SCOPES,
        customRedirectUri: String? = null
    ): String {
        val normalizedShop = normalizeShopDomain(shop)
        val scopeString = scopes.joinToString(",")
        val redirect = customRedirectUri ?: redirectUri

        val params = buildMap {
            put("client_id", clientId)
            put("scope", scopeString)
            put("redirect_uri", redirect)
            state?.let { put("state", it) }
        }

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }

        val authUrl = "https://$normalizedShop/admin/oauth/authorize?$queryString"
        logger.info("Generated Shopify auth URL for shop: $normalizedShop")

        return authUrl
    }

    /**
     * Exchange authorization code for access token
     *
     * @param shop The shop domain
     * @param code The authorization code from Shopify callback
     * @return Result containing token response or error
     */
    suspend fun exchangeCodeForToken(
        shop: String,
        code: String
    ): Result<ShopifyTokenResponse> {
        val normalizedShop = normalizeShopDomain(shop)
        val tokenUrl = "https://$normalizedShop/admin/oauth/access_token"

        logger.info("Exchanging auth code for token: shop=$normalizedShop")

        return try {
            val response = httpClient.post(tokenUrl) {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "code" to code
                ))
            }

            if (response.status.isSuccess()) {
                val tokenResponse: ShopifyTokenResponse = response.body()
                logger.info("Successfully obtained access token for shop: $normalizedShop")
                Result.success(tokenResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to exchange code for token: ${response.status} - $errorBody")
                Result.failure(ShopifyAuthException(
                    "Failed to obtain access token: ${response.status}",
                    response.status.value
                ))
            }
        } catch (e: Exception) {
            logger.error("Exception during token exchange", e)
            Result.failure(ShopifyAuthException("Token exchange failed: ${e.message}", 0))
        }
    }

    /**
     * Verify HMAC signature from Shopify request
     *
     * Used for:
     * - Webhook verification
     * - OAuth callback verification
     * - Embedded app request verification
     *
     * @param query The query string or raw body data
     * @param hmacHeader The HMAC signature from X-Shopify-Hmac-Sha256 header
     * @return True if signature is valid
     */
    fun verifyHmac(data: ByteArray, hmacHeader: String): Boolean {
        return try {
            val computedHmac = computeHmac(data)
            val isValid = secureCompare(computedHmac, hmacHeader)

            if (!isValid) {
                logger.warn("HMAC verification failed")
            }

            isValid
        } catch (e: Exception) {
            logger.error("Error verifying HMAC", e)
            false
        }
    }

    /**
     * Verify HMAC for OAuth callback query parameters
     *
     * @param queryParams Map of query parameters from callback
     * @return True if signature is valid
     */
    fun verifyOAuthCallback(queryParams: Map<String, String>): Boolean {
        val hmac = queryParams["hmac"] ?: return false

        // Build the message string (sorted params without hmac)
        val sortedParams = queryParams
            .filterKeys { it != "hmac" }
            .toSortedMap()
            .entries
            .joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }

        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(clientSecret.toByteArray(), "HmacSHA256")
            mac.init(secretKey)
            val hash = mac.doFinal(sortedParams.toByteArray())
            val computedHmac = hash.joinToString("") { "%02x".format(it) }

            secureCompare(computedHmac, hmac)
        } catch (e: Exception) {
            logger.error("Error verifying OAuth callback HMAC", e)
            false
        }
    }

    /**
     * Verify shop domain format
     *
     * @param shop The shop domain to verify
     * @return True if valid Shopify shop domain
     */
    fun isValidShopDomain(shop: String): Boolean {
        val pattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9-]*\\.myshopify\\.com$")
        return pattern.matches(shop)
    }

    /**
     * Normalize shop domain to ensure consistent format
     */
    fun normalizeShopDomain(shop: String): String {
        var normalized = shop.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")

        // Add .myshopify.com if not present
        if (!normalized.endsWith(".myshopify.com")) {
            normalized = "$normalized.myshopify.com"
        }

        return normalized
    }

    /**
     * Compute HMAC-SHA256 signature
     */
    private fun computeHmac(data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(clientSecret.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(data)
        return java.util.Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private fun secureCompare(a: String, b: String): Boolean {
        if (a.length != b.length) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

class ShopifyAuthException(
    message: String,
    val statusCode: Int
) : Exception(message)
