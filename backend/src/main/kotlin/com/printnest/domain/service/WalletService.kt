package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.WalletRepository
import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

class WalletService(
    private val walletRepository: WalletRepository,
    private val settingsService: SettingsService
) {
    private val logger = LoggerFactory.getLogger(WalletService::class.java)

    // =====================================================
    // BALANCE OPERATIONS
    // =====================================================

    fun getBalance(tenantId: Long, userId: Long): WalletBalance {
        val balance = walletRepository.getBalance(tenantId, userId)
        return WalletBalance(
            balance = balance.toDouble(),
            balanceDecimal = balance,
            formattedBalance = formatCurrency(balance)
        )
    }

    // =====================================================
    // TRANSACTION HISTORY
    // =====================================================

    fun getTransactions(
        tenantId: Long,
        userId: Long,
        filters: TransactionFilters
    ): TransactionListResponse {
        val (transactions, total) = walletRepository.getTransactions(tenantId, userId, filters)
        val totalPages = (total + filters.limit - 1) / filters.limit
        val balance = walletRepository.getBalance(tenantId, userId)

        return TransactionListResponse(
            transactions = transactions,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages,
            currentBalance = balance.toDouble()
        )
    }

    // =====================================================
    // ADD FUNDS VIA STRIPE
    // =====================================================

    fun initiateAddFunds(
        tenantId: Long,
        userId: Long,
        request: AddFundsRequest
    ): Result<AddFundsResponse> {
        try {
            // Validate amount
            if (request.amount <= 0) {
                return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
            }
            if (request.amount > 10000) {
                return Result.failure(IllegalArgumentException("Maximum amount is $10,000"))
            }

            // Get Stripe API key from tenant settings
            val stripeSecretKey = getStripeSecretKey(tenantId)
                ?: return Result.failure(IllegalStateException("Stripe is not configured for this tenant"))

            Stripe.apiKey = stripeSecretKey

            // Convert to cents for Stripe
            val amountCents = (request.amount * 100).toLong()

            // Create Stripe Checkout Session
            val sessionParams = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("${request.successUrl}?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(request.cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(amountCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Add Funds to Wallet")
                                        .setDescription("Add \$${String.format("%.2f", request.amount)} to your PrintNest wallet")
                                        .build()
                                )
                                .build()
                        )
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("tenant_id", tenantId.toString())
                .putMetadata("user_id", userId.toString())
                .putMetadata("type", "add_funds")
                .build()

            val session = Session.create(sessionParams)

            // Store pending payment in database
            walletRepository.createPendingPayment(
                tenantId = tenantId,
                userId = userId,
                amount = BigDecimal.valueOf(request.amount).setScale(2, RoundingMode.HALF_UP),
                stripeSessionId = session.id
            )

            logger.info("Created Stripe checkout session ${session.id} for user $userId, amount: ${request.amount}")

            return Result.success(
                AddFundsResponse(
                    checkoutUrl = session.url,
                    sessionId = session.id
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to create Stripe checkout session", e)
            return Result.failure(e)
        }
    }

    fun completeAddFunds(
        tenantId: Long,
        userId: Long,
        sessionId: String
    ): Result<AddFundsCompleteResponse> {
        try {
            // Find payment record
            val payment = walletRepository.getPaymentBySessionId(sessionId)
                ?: return Result.failure(IllegalArgumentException("Payment session not found"))

            // Verify tenant and user match
            if (payment.tenantId != tenantId || payment.userId != userId) {
                return Result.failure(IllegalAccessException("Payment does not belong to this user"))
            }

            // Check if already processed
            if (payment.status == "completed") {
                val balance = walletRepository.getBalance(tenantId, userId)
                return Result.success(
                    AddFundsCompleteResponse(
                        success = true,
                        newBalance = balance.toDouble(),
                        amountAdded = payment.amount.toDouble(),
                        transactionId = null // Already processed
                    )
                )
            }

            if (payment.status == "failed") {
                return Result.failure(IllegalStateException("Payment has failed"))
            }

            // Verify payment with Stripe
            val stripeSecretKey = getStripeSecretKey(tenantId)
                ?: return Result.failure(IllegalStateException("Stripe is not configured"))

            Stripe.apiKey = stripeSecretKey
            val session = Session.retrieve(sessionId)

            if (session.paymentStatus != "paid") {
                walletRepository.failPayment(payment.id, "Payment not completed: ${session.paymentStatus}")
                return Result.failure(IllegalStateException("Payment was not completed"))
            }

            // Get current balance
            val currentBalance = walletRepository.getBalance(tenantId, userId)
            val newBalance = currentBalance.add(payment.amount)

            // Create dual transactions (TeeDropV2 pattern):
            // 1. CC_PAYMENT (type 3) - positive amount (credit card received)
            // 2. ADD_FUNDS (type 1) - positive amount (funds added to wallet)

            // Transaction 1: CC Payment received
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

            // Transaction 2: Funds added
            val transactionId = walletRepository.createTransaction(
                tenantId = tenantId,
                userId = userId,
                type = TransactionType.ADD_FUNDS.code,
                amount = payment.amount,
                description = "Added funds to wallet",
                referenceId = sessionId,
                balanceBefore = currentBalance,
                balanceAfter = newBalance
            )

            // Update user balance
            walletRepository.updateBalance(tenantId, userId, newBalance)

            // Mark payment as completed
            walletRepository.completePayment(payment.id, session.paymentIntent)

            // Send notification
            settingsService.notifyUser(
                tenantId = tenantId,
                userId = userId,
                title = "Funds Added",
                message = "Successfully added ${formatCurrency(payment.amount)} to your wallet.",
                type = NotificationType.PAYMENT,
                priority = NotificationPriority.MEDIUM,
                url = "/wallet"
            )

            logger.info("Completed add funds for user $userId, amount: ${payment.amount}, new balance: $newBalance")

            return Result.success(
                AddFundsCompleteResponse(
                    success = true,
                    newBalance = newBalance.toDouble(),
                    amountAdded = payment.amount.toDouble(),
                    transactionId = transactionId
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to complete add funds", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // ADMIN: MANUAL CREDIT
    // =====================================================

    fun addCredit(
        tenantId: Long,
        adminUserId: Long,
        request: AddCreditRequest
    ): Result<AddCreditResponse> {
        try {
            if (request.amount <= 0) {
                return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
            }

            val currentBalance = walletRepository.getBalance(tenantId, request.userId)
            val amount = BigDecimal.valueOf(request.amount).setScale(2, RoundingMode.HALF_UP)
            val newBalance = currentBalance.add(amount)

            val description = request.description ?: when (request.type) {
                TransactionType.PROMOTIONAL_CREDIT.code -> "Promotional credit"
                TransactionType.REFERRAL_CREDIT.code -> "Referral credit"
                else -> "Credit added by admin"
            }

            val transactionId = walletRepository.createTransaction(
                tenantId = tenantId,
                userId = request.userId,
                type = request.type,
                amount = amount,
                description = description,
                referenceId = "ADMIN-$adminUserId",
                balanceBefore = currentBalance,
                balanceAfter = newBalance
            )

            walletRepository.updateBalance(tenantId, request.userId, newBalance)

            // Notify user
            settingsService.notifyUser(
                tenantId = tenantId,
                userId = request.userId,
                title = "Credit Added",
                message = "You received ${formatCurrency(amount)}: $description",
                type = NotificationType.PAYMENT,
                priority = NotificationPriority.MEDIUM,
                url = "/wallet"
            )

            logger.info("Admin $adminUserId added credit ${request.amount} to user ${request.userId}")

            return Result.success(
                AddCreditResponse(
                    success = true,
                    newBalance = newBalance.toDouble(),
                    transactionId = transactionId
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to add credit", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // REFUNDS
    // =====================================================

    fun processRefund(
        tenantId: Long,
        userId: Long,
        request: WalletRefundRequest
    ): Result<WalletRefundResponse> {
        try {
            // TODO: Verify order exists and belongs to user
            // TODO: Verify refund amount doesn't exceed order total

            val refundAmount = BigDecimal.valueOf(request.amount ?: 0.0)
                .setScale(2, RoundingMode.HALF_UP)

            if (refundAmount <= BigDecimal.ZERO) {
                return Result.failure(IllegalArgumentException("Refund amount must be greater than 0"))
            }

            val currentBalance = walletRepository.getBalance(tenantId, userId)
            val newBalance = currentBalance.add(refundAmount)

            val description = request.reason?.let { "Order refund: $it" } ?: "Order refund"

            val transactionId = walletRepository.createTransaction(
                tenantId = tenantId,
                userId = userId,
                type = TransactionType.ORDER_REFUND.code,
                amount = refundAmount,
                description = description,
                referenceId = "ORDER-${request.orderId}",
                balanceBefore = currentBalance,
                balanceAfter = newBalance
            )

            walletRepository.updateBalance(tenantId, userId, newBalance)

            // Notify user
            settingsService.notifyUser(
                tenantId = tenantId,
                userId = userId,
                title = "Refund Processed",
                message = "You received a refund of ${formatCurrency(refundAmount)}",
                type = NotificationType.ORDER,
                priority = NotificationPriority.MEDIUM,
                url = "/orders/${request.orderId}"
            )

            logger.info("Processed refund ${refundAmount} for order ${request.orderId} to user $userId")

            return Result.success(
                WalletRefundResponse(
                    success = true,
                    refundedAmount = refundAmount.toDouble(),
                    newBalance = newBalance.toDouble(),
                    transactionId = transactionId
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to process refund", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // DEDUCT BALANCE (for order payments)
    // =====================================================

    fun deductBalance(
        tenantId: Long,
        userId: Long,
        amount: BigDecimal,
        orderId: Long,
        description: String = "Order payment"
    ): Result<Long> {
        try {
            val currentBalance = walletRepository.getBalance(tenantId, userId)

            if (currentBalance < amount) {
                return Result.failure(IllegalStateException("Insufficient balance"))
            }

            val newBalance = currentBalance.subtract(amount)

            // PURCHASE type is negative (money out)
            val transactionId = walletRepository.createTransaction(
                tenantId = tenantId,
                userId = userId,
                type = TransactionType.PURCHASE.code,
                amount = amount.negate(), // Negative for deduction
                description = description,
                referenceId = "ORDER-$orderId",
                balanceBefore = currentBalance,
                balanceAfter = newBalance
            )

            walletRepository.updateBalance(tenantId, userId, newBalance)

            logger.info("Deducted $amount from user $userId for order $orderId, new balance: $newBalance")

            return Result.success(transactionId)
        } catch (e: Exception) {
            logger.error("Failed to deduct balance", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun formatCurrency(amount: BigDecimal): String {
        return "$${String.format("%.2f", amount)}"
    }

    private fun getStripeSecretKey(tenantId: Long): String? {
        // Get from tenant settings
        // This should be implemented in SettingsService
        return settingsService.getTenantSetting(tenantId, "stripe_secret_key")
    }
}
