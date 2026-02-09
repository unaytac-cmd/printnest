package com.printnest.integrations.stripe

import com.printnest.domain.models.TransactionType
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.SettingsRepository
import com.printnest.domain.repository.WalletRepository
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Stripe Service
 *
 * Centralized service for all Stripe operations:
 * - Wallet top-up (Checkout Sessions)
 * - Order payment (Payment Intents)
 * - Webhook handling
 */
class StripeService(
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository,
    private val orderRepository: OrderRepository
) {
    private val logger = LoggerFactory.getLogger(StripeService::class.java)

    // =====================================================
    // CONFIGURATION
    // =====================================================

    private fun getStripeSecretKey(tenantId: Long): String? {
        return settingsRepository.getTenantSetting(tenantId, "stripe_secret_key")
    }

    private fun getStripeWebhookSecret(tenantId: Long): String? {
        return settingsRepository.getTenantSetting(tenantId, "stripe_webhook_secret")
    }

    private fun initStripe(tenantId: Long): Boolean {
        val secretKey = getStripeSecretKey(tenantId)
        if (secretKey == null) {
            logger.warn("Stripe not configured for tenant $tenantId")
            return false
        }
        Stripe.apiKey = secretKey
        return true
    }

    // =====================================================
    // WALLET TOP-UP (Checkout Session)
    // =====================================================

    /**
     * Create a Checkout Session for wallet top-up
     */
    fun createWalletTopUpSession(
        tenantId: Long,
        userId: Long,
        amount: BigDecimal,
        successUrl: String,
        cancelUrl: String
    ): Result<CheckoutSessionResponse> {
        try {
            if (!initStripe(tenantId)) {
                return Result.failure(IllegalStateException("Stripe is not configured"))
            }

            if (amount <= BigDecimal.ZERO) {
                return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
            }

            if (amount > BigDecimal("10000")) {
                return Result.failure(IllegalArgumentException("Maximum amount is $10,000"))
            }

            // Convert to cents
            val amountCents = amount.multiply(BigDecimal(100)).toLong()

            val sessionParams = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("$successUrl?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(amountCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Add Funds to Wallet")
                                        .setDescription("Add \$${amount.setScale(2, RoundingMode.HALF_UP)} to your PrintNest wallet")
                                        .build()
                                )
                                .build()
                        )
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("tenant_id", tenantId.toString())
                .putMetadata("user_id", userId.toString())
                .putMetadata("type", "wallet_topup")
                .putMetadata("amount", amount.toPlainString())
                .build()

            val session = Session.create(sessionParams)

            // Store pending payment
            walletRepository.createPendingPayment(
                tenantId = tenantId,
                userId = userId,
                amount = amount.setScale(2, RoundingMode.HALF_UP),
                stripeSessionId = session.id
            )

            logger.info("Created wallet top-up session ${session.id} for user $userId, amount: $amount")

            return Result.success(CheckoutSessionResponse(
                sessionId = session.id,
                checkoutUrl = session.url,
                amount = amount.toDouble()
            ))

        } catch (e: Exception) {
            logger.error("Failed to create wallet top-up session", e)
            return Result.failure(e)
        }
    }

    /**
     * Verify and complete wallet top-up after Stripe redirect
     */
    fun completeWalletTopUp(
        tenantId: Long,
        userId: Long,
        sessionId: String
    ): Result<WalletTopUpResult> {
        try {
            if (!initStripe(tenantId)) {
                return Result.failure(IllegalStateException("Stripe is not configured"))
            }

            // Find pending payment
            val payment = walletRepository.getPaymentBySessionId(sessionId)
                ?: return Result.failure(IllegalArgumentException("Payment session not found"))

            if (payment.tenantId != tenantId || payment.userId != userId) {
                return Result.failure(IllegalAccessException("Payment does not belong to this user"))
            }

            if (payment.status == "completed") {
                val balance = walletRepository.getBalance(tenantId, userId)
                return Result.success(WalletTopUpResult(
                    success = true,
                    alreadyProcessed = true,
                    amountAdded = payment.amount.toDouble(),
                    newBalance = balance.toDouble()
                ))
            }

            // Verify with Stripe
            val session = Session.retrieve(sessionId)

            if (session.paymentStatus != "paid") {
                walletRepository.failPayment(payment.id, "Payment not completed: ${session.paymentStatus}")
                return Result.failure(IllegalStateException("Payment was not completed"))
            }

            // Process the top-up
            val currentBalance = walletRepository.getBalance(tenantId, userId)
            val newBalance = currentBalance.add(payment.amount)

            // Create transactions
            walletRepository.createTransaction(
                tenantId = tenantId,
                userId = userId,
                type = TransactionType.CC_PAYMENT.code,
                amount = payment.amount,
                description = "Credit card payment via Stripe",
                referenceId = session.paymentIntent,
                balanceBefore = currentBalance,
                balanceAfter = newBalance
            )

            walletRepository.createTransaction(
                tenantId = tenantId,
                userId = userId,
                type = TransactionType.ADD_FUNDS.code,
                amount = payment.amount,
                description = "Added funds to wallet",
                referenceId = sessionId,
                balanceBefore = currentBalance,
                balanceAfter = newBalance
            )

            // Update balance and payment status
            walletRepository.updateBalance(tenantId, userId, newBalance)
            walletRepository.completePayment(payment.id, session.paymentIntent)

            logger.info("Completed wallet top-up for user $userId, amount: ${payment.amount}, new balance: $newBalance")

            return Result.success(WalletTopUpResult(
                success = true,
                alreadyProcessed = false,
                amountAdded = payment.amount.toDouble(),
                newBalance = newBalance.toDouble()
            ))

        } catch (e: Exception) {
            logger.error("Failed to complete wallet top-up", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // ORDER PAYMENT (Payment Intent)
    // =====================================================

    /**
     * Create a Payment Intent for order checkout
     */
    fun createOrderPaymentIntent(
        tenantId: Long,
        userId: Long,
        orderId: Long,
        amount: BigDecimal,
        isUrgent: Boolean = false
    ): Result<PaymentIntentResponse> {
        try {
            if (!initStripe(tenantId)) {
                return Result.failure(IllegalStateException("Stripe is not configured"))
            }

            if (amount <= BigDecimal.ZERO) {
                return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
            }

            // Convert to cents
            val amountCents = amount.multiply(BigDecimal(100)).toLong()

            val params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .putMetadata("tenant_id", tenantId.toString())
                .putMetadata("user_id", userId.toString())
                .putMetadata("order_id", orderId.toString())
                .putMetadata("type", "order_payment")
                .putMetadata("is_urgent", isUrgent.toString())
                .build()

            val paymentIntent = PaymentIntent.create(params)

            // Create pending payment record
            orderRepository.createPayment(
                tenantId = tenantId,
                userId = userId,
                orderId = orderId,
                paymentMethod = "stripe",
                amount = amount,
                stripePaymentIntentId = paymentIntent.id
            )

            logger.info("Created payment intent ${paymentIntent.id} for order $orderId, amount: $amount")

            return Result.success(PaymentIntentResponse(
                paymentIntentId = paymentIntent.id,
                clientSecret = paymentIntent.clientSecret,
                amount = amount.toDouble(),
                status = paymentIntent.status
            ))

        } catch (e: Exception) {
            logger.error("Failed to create payment intent for order $orderId", e)
            return Result.failure(e)
        }
    }

    /**
     * Create a Checkout Session for order payment (alternative to Payment Intent)
     */
    fun createOrderCheckoutSession(
        tenantId: Long,
        userId: Long,
        orderId: Long,
        amount: BigDecimal,
        orderSummary: String,
        successUrl: String,
        cancelUrl: String,
        isUrgent: Boolean = false
    ): Result<CheckoutSessionResponse> {
        try {
            if (!initStripe(tenantId)) {
                return Result.failure(IllegalStateException("Stripe is not configured"))
            }

            if (amount <= BigDecimal.ZERO) {
                return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
            }

            val amountCents = amount.multiply(BigDecimal(100)).toLong()

            val sessionParams = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("$successUrl?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(amountCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Order #$orderId")
                                        .setDescription(orderSummary)
                                        .build()
                                )
                                .build()
                        )
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("tenant_id", tenantId.toString())
                .putMetadata("user_id", userId.toString())
                .putMetadata("order_id", orderId.toString())
                .putMetadata("type", "order_payment")
                .putMetadata("is_urgent", isUrgent.toString())
                .build()

            val session = Session.create(sessionParams)

            // Create pending payment record
            orderRepository.createPayment(
                tenantId = tenantId,
                userId = userId,
                orderId = orderId,
                paymentMethod = "stripe",
                amount = amount,
                stripePaymentIntentId = session.id
            )

            logger.info("Created order checkout session ${session.id} for order $orderId, amount: $amount")

            return Result.success(CheckoutSessionResponse(
                sessionId = session.id,
                checkoutUrl = session.url,
                amount = amount.toDouble()
            ))

        } catch (e: Exception) {
            logger.error("Failed to create checkout session for order $orderId", e)
            return Result.failure(e)
        }
    }

    /**
     * Confirm order payment after successful Stripe payment
     */
    fun confirmOrderPayment(
        tenantId: Long,
        userId: Long,
        orderId: Long,
        sessionId: String,
        isUrgent: Boolean = false
    ): Result<OrderPaymentResult> {
        try {
            if (!initStripe(tenantId)) {
                return Result.failure(IllegalStateException("Stripe is not configured"))
            }

            // Verify session with Stripe
            val session = Session.retrieve(sessionId)

            if (session.paymentStatus != "paid") {
                return Result.failure(IllegalStateException("Payment was not completed: ${session.paymentStatus}"))
            }

            // Get amount from session
            val amountCents = session.amountTotal ?: 0L
            val amount = BigDecimal(amountCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

            // Update payment record
            val payment = orderRepository.findPaymentByStripeId(tenantId, sessionId)
            if (payment != null) {
                orderRepository.updatePaymentStatus(payment.id, "completed", session.paymentIntent)
            }

            // Update order status
            val newStatus = if (isUrgent) 14 else 12 // URGENT or PENDING
            orderRepository.updateOrderPaymentStatus(orderId, tenantId, newStatus, session.paymentIntent)

            logger.info("Confirmed order payment for order $orderId, amount: $amount")

            return Result.success(OrderPaymentResult(
                success = true,
                orderId = orderId,
                amountPaid = amount.toDouble(),
                newStatus = newStatus,
                paymentIntentId = session.paymentIntent
            ))

        } catch (e: Exception) {
            logger.error("Failed to confirm order payment for order $orderId", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // WEBHOOK HANDLING
    // =====================================================

    /**
     * Process Stripe webhook event
     */
    fun handleWebhookEvent(
        tenantId: Long,
        payload: String,
        signature: String
    ): Result<WebhookResult> {
        try {
            val webhookSecret = getStripeWebhookSecret(tenantId)
                ?: return Result.failure(IllegalStateException("Webhook secret not configured"))

            // Verify signature
            val event: Event = try {
                Webhook.constructEvent(payload, signature, webhookSecret)
            } catch (e: SignatureVerificationException) {
                logger.error("Invalid webhook signature", e)
                return Result.failure(IllegalArgumentException("Invalid signature"))
            }

            logger.info("Processing webhook event: ${event.type}")

            return when (event.type) {
                "checkout.session.completed" -> handleCheckoutSessionCompleted(tenantId, event)
                "payment_intent.succeeded" -> handlePaymentIntentSucceeded(tenantId, event)
                "payment_intent.payment_failed" -> handlePaymentIntentFailed(tenantId, event)
                else -> {
                    logger.info("Unhandled event type: ${event.type}")
                    Result.success(WebhookResult(
                        processed = false,
                        eventType = event.type,
                        message = "Event type not handled"
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error("Failed to process webhook", e)
            return Result.failure(e)
        }
    }

    private fun handleCheckoutSessionCompleted(tenantId: Long, event: Event): Result<WebhookResult> {
        val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
            ?: return Result.failure(IllegalStateException("Could not deserialize session"))

        val metadata = session.metadata
        val type = metadata["type"]
        val userId = metadata["user_id"]?.toLongOrNull()

        return when (type) {
            "wallet_topup" -> {
                if (userId != null) {
                    completeWalletTopUp(tenantId, userId, session.id)
                    Result.success(WebhookResult(
                        processed = true,
                        eventType = event.type,
                        message = "Wallet top-up completed"
                    ))
                } else {
                    Result.failure(IllegalStateException("User ID not found in metadata"))
                }
            }
            "order_payment" -> {
                val orderId = metadata["order_id"]?.toLongOrNull()
                val isUrgent = metadata["is_urgent"]?.toBoolean() ?: false
                if (userId != null && orderId != null) {
                    confirmOrderPayment(tenantId, userId, orderId, session.id, isUrgent)
                    Result.success(WebhookResult(
                        processed = true,
                        eventType = event.type,
                        message = "Order payment completed"
                    ))
                } else {
                    Result.failure(IllegalStateException("User ID or Order ID not found in metadata"))
                }
            }
            else -> {
                Result.success(WebhookResult(
                    processed = false,
                    eventType = event.type,
                    message = "Unknown checkout type: $type"
                ))
            }
        }
    }

    private fun handlePaymentIntentSucceeded(tenantId: Long, event: Event): Result<WebhookResult> {
        val paymentIntent = event.dataObjectDeserializer.`object`.orElse(null) as? PaymentIntent
            ?: return Result.failure(IllegalStateException("Could not deserialize payment intent"))

        val metadata = paymentIntent.metadata
        val type = metadata["type"]
        val userId = metadata["user_id"]?.toLongOrNull()
        val orderId = metadata["order_id"]?.toLongOrNull()

        if (type == "order_payment" && userId != null && orderId != null) {
            val isUrgent = metadata["is_urgent"]?.toBoolean() ?: false
            val amountCents = paymentIntent.amount
            val amount = BigDecimal(amountCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

            // Update payment record
            val payment = orderRepository.findPaymentByStripeId(tenantId, paymentIntent.id)
            if (payment != null) {
                orderRepository.updatePaymentStatus(payment.id, "completed", paymentIntent.id)
            }

            // Update order status
            val newStatus = if (isUrgent) 14 else 12
            orderRepository.updateOrderPaymentStatus(orderId, tenantId, newStatus, paymentIntent.id)

            logger.info("Payment intent succeeded for order $orderId")
        }

        return Result.success(WebhookResult(
            processed = true,
            eventType = event.type,
            message = "Payment intent processed"
        ))
    }

    private fun handlePaymentIntentFailed(tenantId: Long, event: Event): Result<WebhookResult> {
        val paymentIntent = event.dataObjectDeserializer.`object`.orElse(null) as? PaymentIntent
            ?: return Result.failure(IllegalStateException("Could not deserialize payment intent"))

        val metadata = paymentIntent.metadata
        val orderId = metadata["order_id"]?.toLongOrNull()

        if (orderId != null) {
            // Update payment record to failed
            val payment = orderRepository.findPaymentByStripeId(tenantId, paymentIntent.id)
            if (payment != null) {
                orderRepository.updatePaymentStatus(payment.id, "failed", paymentIntent.id)
            }

            logger.warn("Payment intent failed for order $orderId: ${paymentIntent.lastPaymentError?.message}")
        }

        return Result.success(WebhookResult(
            processed = true,
            eventType = event.type,
            message = "Payment failure recorded"
        ))
    }

    // =====================================================
    // REFUNDS
    // =====================================================

    /**
     * Create a refund for a payment
     */
    fun createRefund(
        tenantId: Long,
        paymentIntentId: String,
        amount: BigDecimal? = null, // null = full refund
        reason: String? = null
    ): Result<RefundResult> {
        try {
            if (!initStripe(tenantId)) {
                return Result.failure(IllegalStateException("Stripe is not configured"))
            }

            val params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)

            amount?.let {
                params.setAmount(it.multiply(BigDecimal(100)).toLong())
            }

            reason?.let {
                params.setReason(
                    when (it.lowercase()) {
                        "duplicate" -> com.stripe.param.RefundCreateParams.Reason.DUPLICATE
                        "fraudulent" -> com.stripe.param.RefundCreateParams.Reason.FRAUDULENT
                        else -> com.stripe.param.RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER
                    }
                )
            }

            val refund = com.stripe.model.Refund.create(params.build())

            logger.info("Created refund ${refund.id} for payment $paymentIntentId")

            return Result.success(RefundResult(
                refundId = refund.id,
                amount = BigDecimal(refund.amount).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toDouble(),
                status = refund.status
            ))

        } catch (e: Exception) {
            logger.error("Failed to create refund for $paymentIntentId", e)
            return Result.failure(e)
        }
    }
}

// =====================================================
// RESPONSE MODELS
// =====================================================

@Serializable
data class CheckoutSessionResponse(
    val sessionId: String,
    val checkoutUrl: String,
    val amount: Double
)

@Serializable
data class PaymentIntentResponse(
    val paymentIntentId: String,
    val clientSecret: String,
    val amount: Double,
    val status: String
)

@Serializable
data class WalletTopUpResult(
    val success: Boolean,
    val alreadyProcessed: Boolean = false,
    val amountAdded: Double,
    val newBalance: Double
)

@Serializable
data class OrderPaymentResult(
    val success: Boolean,
    val orderId: Long,
    val amountPaid: Double,
    val newStatus: Int,
    val paymentIntentId: String?
)

@Serializable
data class WebhookResult(
    val processed: Boolean,
    val eventType: String,
    val message: String
)

@Serializable
data class RefundResult(
    val refundId: String,
    val amount: Double,
    val status: String
)
