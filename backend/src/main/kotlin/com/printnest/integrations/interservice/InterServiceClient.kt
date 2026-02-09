package com.printnest.integrations.interservice

import com.printnest.domain.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

/**
 * Client for making authenticated inter-service calls.
 * Handles service-to-service communication with JWT authentication and HMAC signatures.
 */
class InterServiceClient(
    private val httpClient: HttpClient,
    private val interServiceAuth: InterServiceAuth,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(InterServiceClient::class.java)

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val SIGNATURE_HEADER = "X-PrintNest-Signature"
        private const val TIMESTAMP_HEADER = "X-PrintNest-Timestamp"
        private const val SERVICE_NAME_HEADER = "X-PrintNest-Service"
    }

    /**
     * Call an external service with authentication.
     *
     * @param serviceUrl The full URL of the service endpoint
     * @param action The action/operation being performed
     * @param payload The request payload as JsonElement
     * @return Result containing InterServiceResponse or error
     */
    suspend fun callService(
        serviceUrl: String,
        action: String,
        payload: JsonElement
    ): Result<InterServiceResponse> {
        return try {
            // Generate service token
            val serviceToken = interServiceAuth.generateServiceToken(extractServiceName(serviceUrl))

            // Create the request body
            val timestamp = System.currentTimeMillis()
            val payloadJson = json.encodeToString(payload)
            val signature = interServiceAuth.generateSignature("$action.$payloadJson.$timestamp")

            val request = InterServiceRequest(
                action = action,
                payload = payload,
                signature = signature,
                timestamp = timestamp,
                serviceName = interServiceAuth.getServiceName()
            )

            val requestBody = json.encodeToString(request)

            logger.info("Calling service: {} with action: {}", serviceUrl, action)

            val response = httpClient.post(serviceUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${serviceToken.token}")
                header(SIGNATURE_HEADER, signature)
                header(TIMESTAMP_HEADER, timestamp.toString())
                header(SERVICE_NAME_HEADER, interServiceAuth.getServiceName())
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val responseBody = response.body<InterServiceResponse>()
                logger.info("Service call successful: {} -> {}", serviceUrl, action)
                Result.success(responseBody)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Service call failed: {} -> {} ({}): {}", serviceUrl, action, response.status, errorBody)
                Result.failure(InterServiceException("Service call failed: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            logger.error("Error calling service: {} -> {}: {}", serviceUrl, action, e.message)
            Result.failure(InterServiceException("Error calling service: ${e.message}", e))
        }
    }

    /**
     * Update tracking status across services.
     *
     * @param serviceUrl The tracking service URL
     * @param orderId The order ID
     * @param trackingPayload The tracking update payload
     * @return Result containing InterServiceResponse or error
     */
    suspend fun updateTrackingStatus(
        serviceUrl: String,
        orderId: String,
        trackingPayload: TrackingUpdatePayload
    ): Result<InterServiceResponse> {
        val payload = json.encodeToJsonElement(trackingPayload)
        return callService(
            serviceUrl = serviceUrl,
            action = "tracking.update",
            payload = payload
        )
    }

    /**
     * Notify other services that an order is complete.
     *
     * @param serviceUrl The service URL to notify
     * @param orderId The completed order ID
     * @param tenantId The tenant ID
     * @param trackingNumber Optional tracking number
     * @param carrier Optional carrier name
     * @return Result containing InterServiceResponse or error
     */
    suspend fun notifyOrderComplete(
        serviceUrl: String,
        orderId: String,
        tenantId: String,
        trackingNumber: String? = null,
        carrier: String? = null
    ): Result<InterServiceResponse> {
        val payload = OrderCompletePayload(
            orderId = orderId,
            tenantId = tenantId,
            trackingNumber = trackingNumber,
            carrier = carrier,
            notifyMarketplace = true
        )
        val jsonPayload = json.encodeToJsonElement(payload)
        return callService(
            serviceUrl = serviceUrl,
            action = "order.complete",
            payload = jsonPayload
        )
    }

    /**
     * Send a webhook to an external URL with proper signing.
     *
     * @param webhookUrl The webhook endpoint URL
     * @param eventType The type of event being sent
     * @param data The event data
     * @param secret Optional webhook secret for this specific endpoint
     * @return Result containing the response or error
     */
    suspend fun sendWebhook(
        webhookUrl: String,
        eventType: String,
        data: JsonElement,
        secret: String? = null
    ): Result<HttpResponse> {
        return try {
            val timestamp = System.currentTimeMillis()
            val dataJson = json.encodeToString(data)

            val payload = WebhookPayload(
                type = eventType,
                data = data,
                timestamp = timestamp,
                signature = interServiceAuth.generateWebhookSignature(dataJson, timestamp)
            )

            val requestBody = json.encodeToString(payload)
            val signature = if (secret != null) {
                interServiceAuth.generateSignature(requestBody, secret)
            } else {
                interServiceAuth.generateSignature(requestBody)
            }

            logger.info("Sending webhook to: {} for event: {}", webhookUrl, eventType)

            val response = httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                header(SIGNATURE_HEADER, signature)
                header(TIMESTAMP_HEADER, timestamp.toString())
                header(SERVICE_NAME_HEADER, interServiceAuth.getServiceName())
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                logger.info("Webhook sent successfully: {} -> {}", webhookUrl, eventType)
                Result.success(response)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Webhook failed: {} -> {} ({}): {}", webhookUrl, eventType, response.status, errorBody)
                Result.failure(InterServiceException("Webhook failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Error sending webhook to {}: {}", webhookUrl, e.message)
            Result.failure(InterServiceException("Error sending webhook: ${e.message}", e))
        }
    }

    /**
     * Make an authenticated GET request to another service.
     *
     * @param serviceUrl The full URL to call
     * @return Result containing the response body or error
     */
    suspend fun getFromService(serviceUrl: String): Result<String> {
        return try {
            val serviceToken = interServiceAuth.generateServiceToken(extractServiceName(serviceUrl))
            val timestamp = System.currentTimeMillis()

            val response = httpClient.get(serviceUrl) {
                header(HttpHeaders.Authorization, "Bearer ${serviceToken.token}")
                header(TIMESTAMP_HEADER, timestamp.toString())
                header(SERVICE_NAME_HEADER, interServiceAuth.getServiceName())
            }

            if (response.status.isSuccess()) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(InterServiceException("GET request failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Error making GET request to {}: {}", serviceUrl, e.message)
            Result.failure(InterServiceException("Error making GET request: ${e.message}", e))
        }
    }

    /**
     * Extract service name from URL for token generation.
     */
    private fun extractServiceName(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: "unknown-service"
        } catch (e: Exception) {
            "unknown-service"
        }
    }
}

/**
 * Exception for inter-service communication errors.
 */
class InterServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)
