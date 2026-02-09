package com.printnest.integrations.stripe

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import java.math.BigDecimal

fun Route.stripeRoutes() {
    val stripeService: StripeService = GlobalContext.get().get()

    route("/stripe") {

        // =====================================================
        // WALLET TOP-UP
        // =====================================================

        /**
         * POST /api/v1/stripe/wallet/topup
         * Create a checkout session for wallet top-up
         */
        post("/wallet/topup") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<WalletTopUpRequest>()

            val result = stripeService.createWalletTopUpSession(
                tenantId = tenantId,
                userId = userId,
                amount = BigDecimal.valueOf(request.amount),
                successUrl = request.successUrl,
                cancelUrl = request.cancelUrl
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        /**
         * POST /api/v1/stripe/wallet/topup/complete
         * Complete wallet top-up after Stripe redirect
         */
        post("/wallet/topup/complete") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CompletePaymentRequest>()

            val result = stripeService.completeWalletTopUp(
                tenantId = tenantId,
                userId = userId,
                sessionId = request.sessionId
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        // =====================================================
        // ORDER PAYMENT
        // =====================================================

        /**
         * POST /api/v1/stripe/order/payment-intent
         * Create a payment intent for order checkout (for custom Stripe Elements UI)
         */
        post("/order/payment-intent") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<OrderPaymentIntentRequest>()

            val result = stripeService.createOrderPaymentIntent(
                tenantId = tenantId,
                userId = userId,
                orderId = request.orderId,
                amount = BigDecimal.valueOf(request.amount),
                isUrgent = request.isUrgent
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        /**
         * POST /api/v1/stripe/order/checkout
         * Create a checkout session for order payment (Stripe hosted checkout)
         */
        post("/order/checkout") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<OrderCheckoutRequest>()

            val result = stripeService.createOrderCheckoutSession(
                tenantId = tenantId,
                userId = userId,
                orderId = request.orderId,
                amount = BigDecimal.valueOf(request.amount),
                orderSummary = request.orderSummary,
                successUrl = request.successUrl,
                cancelUrl = request.cancelUrl,
                isUrgent = request.isUrgent
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        /**
         * POST /api/v1/stripe/order/confirm
         * Confirm order payment after successful Stripe payment
         */
        post("/order/confirm") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ConfirmOrderPaymentRequest>()

            val result = stripeService.confirmOrderPayment(
                tenantId = tenantId,
                userId = userId,
                orderId = request.orderId,
                sessionId = request.sessionId,
                isUrgent = request.isUrgent
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        // =====================================================
        // REFUNDS
        // =====================================================

        /**
         * POST /api/v1/stripe/refund
         * Create a refund for a payment
         */
        post("/refund") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userRole = call.request.headers["X-User-Role"] ?: ""
            if (userRole != "owner" && userRole != "admin" && userRole != "producer") {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val request = call.receive<RefundRequest>()

            val result = stripeService.createRefund(
                tenantId = tenantId,
                paymentIntentId = request.paymentIntentId,
                amount = request.amount?.let { BigDecimal.valueOf(it) },
                reason = request.reason
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        // =====================================================
        // WEBHOOKS
        // =====================================================

        /**
         * POST /api/v1/stripe/webhook
         * Handle Stripe webhook events
         */
        post("/webhook") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val signature = call.request.headers["Stripe-Signature"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing Stripe signature"))

            val payload = call.receiveText()

            val result = stripeService.handleWebhookEvent(
                tenantId = tenantId,
                payload = payload,
                signature = signature
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }

        /**
         * POST /api/v1/stripe/webhook/{tenantId}
         * Handle Stripe webhook events with tenant ID in path (alternative routing)
         */
        post("/webhook/{tenantId}") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid Tenant ID"))

            val signature = call.request.headers["Stripe-Signature"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing Stripe signature"))

            val payload = call.receiveText()

            val result = stripeService.handleWebhookEvent(
                tenantId = tenantId,
                payload = payload,
                signature = signature
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
            )
        }
    }
}

// =====================================================
// REQUEST MODELS
// =====================================================

@Serializable
data class WalletTopUpRequest(
    val amount: Double,
    val successUrl: String,
    val cancelUrl: String
)

@Serializable
data class CompletePaymentRequest(
    val sessionId: String
)

@Serializable
data class OrderPaymentIntentRequest(
    val orderId: Long,
    val amount: Double,
    val isUrgent: Boolean = false
)

@Serializable
data class OrderCheckoutRequest(
    val orderId: Long,
    val amount: Double,
    val orderSummary: String,
    val successUrl: String,
    val cancelUrl: String,
    val isUrgent: Boolean = false
)

@Serializable
data class ConfirmOrderPaymentRequest(
    val orderId: Long,
    val sessionId: String,
    val isUrgent: Boolean = false
)

@Serializable
data class RefundRequest(
    val paymentIntentId: String,
    val amount: Double? = null,
    val reason: String? = null
)
