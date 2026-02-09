package com.printnest.integrations.interservice

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.printnest.domain.models.ServicePrincipal
import com.printnest.domain.models.ServiceToken
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Inter-service authentication handler.
 * Provides JWT generation/verification and HMAC signature generation/verification
 * for secure service-to-service communication.
 */
class InterServiceAuth {

    private val logger = LoggerFactory.getLogger(InterServiceAuth::class.java)

    private val interServiceSecret: String = System.getenv("INTER_SERVICE_SECRET") ?: "dev-inter-service-secret-change-in-production"
    private val serviceName: String = System.getenv("SERVICE_NAME") ?: "printnest-backend"
    private val jwtIssuer: String = "printnest-interservice"
    private val jwtAudience: String = "printnest-services"

    private val algorithm: Algorithm = Algorithm.HMAC256(interServiceSecret)

    companion object {
        private const val SERVICE_TOKEN_VALIDITY_MS = 60 * 60 * 1000L // 1 hour
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L // 5 minutes tolerance
    }

    /**
     * Generate a JWT service token for service-to-service authentication.
     *
     * @param targetServiceName The name of the service this token is for
     * @return ServiceToken containing the JWT and metadata
     */
    fun generateServiceToken(targetServiceName: String): ServiceToken {
        val now = Date()
        val expiry = Date(now.time + SERVICE_TOKEN_VALIDITY_MS)

        val token = JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(serviceName)
            .withClaim("serviceName", targetServiceName)
            .withClaim("sourceService", serviceName)
            .withClaim("type", "service")
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)

        logger.debug("Generated service token for service: {}", targetServiceName)

        return ServiceToken(
            token = token,
            serviceName = targetServiceName,
            expiry = expiry.time,
            issuedAt = now.time
        )
    }

    /**
     * Verify a JWT service token.
     *
     * @param token The JWT token to verify
     * @return ServicePrincipal if valid, null otherwise
     */
    fun verifyServiceToken(token: String): ServicePrincipal? {
        return try {
            val verifier = JWT
                .require(algorithm)
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .withClaim("type", "service")
                .build()

            val decodedJWT = verifier.verify(token)

            val serviceName = decodedJWT.getClaim("serviceName").asString()
            val issuedAt = decodedJWT.issuedAt?.time ?: 0L
            val expiresAt = decodedJWT.expiresAt?.time ?: 0L

            if (serviceName.isNullOrBlank()) {
                logger.warn("Service token missing serviceName claim")
                return null
            }

            ServicePrincipal(
                serviceName = serviceName,
                issuedAt = issuedAt,
                expiresAt = expiresAt
            )
        } catch (e: JWTVerificationException) {
            logger.warn("Service token verification failed: {}", e.message)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error verifying service token: {}", e.message)
            null
        }
    }

    /**
     * Generate an HMAC-SHA256 signature for a payload.
     *
     * @param payload The payload string to sign
     * @param secret The secret key for signing (optional, uses inter-service secret if not provided)
     * @return Base64-encoded signature
     */
    fun generateSignature(payload: String, secret: String? = null): String {
        val secretKey = secret ?: interServiceSecret
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(keySpec)
        val hmacBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hmacBytes)
    }

    /**
     * Verify an HMAC-SHA256 signature.
     *
     * @param payload The original payload string
     * @param signature The signature to verify
     * @param secret The secret key used for signing (optional, uses inter-service secret if not provided)
     * @return true if signature is valid, false otherwise
     */
    fun verifySignature(payload: String, signature: String, secret: String? = null): Boolean {
        return try {
            val expectedSignature = generateSignature(payload, secret)
            MessageDigest.isEqual(
                expectedSignature.toByteArray(Charsets.UTF_8),
                signature.toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            logger.error("Error verifying signature: {}", e.message)
            false
        }
    }

    /**
     * Generate a webhook signature for outgoing webhooks.
     * Uses timestamp + payload for signature generation.
     *
     * @param payload The webhook payload as JSON string
     * @param timestamp The timestamp to include in signature
     * @return Webhook signature string
     */
    fun generateWebhookSignature(payload: String, timestamp: Long = System.currentTimeMillis()): String {
        val signaturePayload = "$timestamp.$payload"
        return generateSignature(signaturePayload)
    }

    /**
     * Verify a webhook signature from incoming webhooks.
     *
     * @param payload The webhook payload as JSON string
     * @param signature The signature to verify
     * @param timestamp The timestamp from the webhook
     * @param secret The webhook secret (optional, uses inter-service secret if not provided)
     * @return true if signature is valid and timestamp is within tolerance
     */
    fun verifyWebhookSignature(
        payload: String,
        signature: String,
        timestamp: Long,
        secret: String? = null
    ): Boolean {
        // Check timestamp tolerance to prevent replay attacks
        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - timestamp) > TIMESTAMP_TOLERANCE_MS) {
            logger.warn("Webhook timestamp outside tolerance: {} (current: {})", timestamp, now)
            return false
        }

        val signaturePayload = "$timestamp.$payload"
        return verifySignature(signaturePayload, signature, secret)
    }

    /**
     * Validate a service authentication request.
     *
     * @param serviceName The name of the requesting service
     * @param serviceSecret The secret provided by the service
     * @param timestamp The timestamp of the request
     * @return true if authentication is valid
     */
    fun validateServiceAuth(serviceName: String, serviceSecret: String, timestamp: Long): Boolean {
        // Check timestamp tolerance
        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - timestamp) > TIMESTAMP_TOLERANCE_MS) {
            logger.warn("Service auth timestamp outside tolerance for service: {}", serviceName)
            return false
        }

        // Verify the service secret matches our inter-service secret
        // In production, you might have different secrets per service
        val isValid = MessageDigest.isEqual(
            interServiceSecret.toByteArray(Charsets.UTF_8),
            serviceSecret.toByteArray(Charsets.UTF_8)
        )

        if (!isValid) {
            logger.warn("Invalid service secret for service: {}", serviceName)
        }

        return isValid
    }

    /**
     * Get the current service name.
     */
    fun getServiceName(): String = serviceName

    /**
     * Extract bearer token from Authorization header.
     *
     * @param authHeader The Authorization header value
     * @return The token if present, null otherwise
     */
    fun extractBearerToken(authHeader: String?): String? {
        if (authHeader.isNullOrBlank()) return null
        return if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim().takeIf { it.isNotBlank() }
        } else null
    }
}
