package com.printnest.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Service token for inter-service authentication.
 */
@Serializable
data class ServiceToken(
    val token: String,
    val serviceName: String,
    val expiry: Long,
    val issuedAt: Long = System.currentTimeMillis()
)

/**
 * Generic inter-service request wrapper.
 */
@Serializable
data class InterServiceRequest(
    val action: String,
    val payload: JsonElement,
    val signature: String,
    val timestamp: Long = System.currentTimeMillis(),
    val serviceName: String? = null
)

/**
 * Webhook payload for external integrations.
 */
@Serializable
data class WebhookPayload(
    val type: String,
    val data: JsonElement,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String
)

/**
 * Tracking update payload for order tracking updates.
 */
@Serializable
data class TrackingUpdatePayload(
    val orderId: String,
    val trackingNumber: String,
    val carrier: String,
    val status: TrackingStatus,
    val statusDetails: String? = null,
    val estimatedDelivery: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

// TrackingStatus is defined in ShippingModels.kt

/**
 * Order completion notification payload.
 */
@Serializable
data class OrderCompletePayload(
    val orderId: String,
    val tenantId: String,
    val completedAt: Long = System.currentTimeMillis(),
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val notifyMarketplace: Boolean = true
)

/**
 * Inter-service authentication request.
 */
@Serializable
data class ServiceAuthRequest(
    val serviceName: String,
    val serviceSecret: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Inter-service authentication response.
 */
@Serializable
data class ServiceAuthResponse(
    val success: Boolean,
    val token: String? = null,
    val expiresIn: Long? = null,
    val message: String? = null
)

/**
 * Generic inter-service response.
 */
@Serializable
data class InterServiceResponse(
    val success: Boolean,
    val message: String? = null,
    val data: JsonElement? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Webhook registration request.
 */
@Serializable
data class WebhookRegistration(
    val url: String,
    val events: List<String>,
    val secret: String? = null,
    val isActive: Boolean = true
)

/**
 * Webhook event types.
 */
object WebhookEventTypes {
    const val ORDER_CREATED = "order.created"
    const val ORDER_UPDATED = "order.updated"
    const val ORDER_COMPLETED = "order.completed"
    const val ORDER_CANCELLED = "order.cancelled"
    const val TRACKING_UPDATED = "tracking.updated"
    const val GANGSHEET_GENERATED = "gangsheet.generated"
    const val SHIPMENT_CREATED = "shipment.created"
    const val PAYMENT_RECEIVED = "payment.received"
}

/**
 * Service principal for authenticated inter-service requests.
 */
data class ServicePrincipal(
    val serviceName: String,
    val issuedAt: Long,
    val expiresAt: Long
) : io.ktor.server.auth.Principal {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
}
