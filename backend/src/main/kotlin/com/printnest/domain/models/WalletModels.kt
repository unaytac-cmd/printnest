package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// TRANSACTION TYPES (from TeeDropV2)
// =====================================================
// 0 = PURCHASE (order payment - negative)
// 1 = ADD_FUNDS (balance top-up)
// 2 = PROMOTIONAL_CREDIT
// 3 = CC_PAYMENT (stripe payment - positive)
// 4 = REFERRAL_CREDIT
// 5 = ORDER_REFUND
// 6 = MULTI_PURCHASE (bulk order)

enum class TransactionType(val code: Int, val label: String) {
    PURCHASE(0, "Order Payment"),
    ADD_FUNDS(1, "Added Funds"),
    PROMOTIONAL_CREDIT(2, "Promotional Credit"),
    CC_PAYMENT(3, "Credit Card Payment"),
    REFERRAL_CREDIT(4, "Referral Credit"),
    ORDER_REFUND(5, "Order Refund"),
    MULTI_PURCHASE(6, "Bulk Order Payment");

    companion object {
        fun fromCode(code: Int): TransactionType? = entries.find { it.code == code }
    }
}

// =====================================================
// WALLET RESPONSE MODELS
// =====================================================

@Serializable
data class WalletBalance(
    val balance: Double,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceDecimal: BigDecimal,
    val formattedBalance: String
)

@Serializable
data class Transaction(
    val id: Long,
    val type: Int,
    val typeLabel: String,
    val amount: Double,
    val description: String?,
    val referenceId: String?,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val createdAt: String,
    // UI helpers
    val isCredit: Boolean, // true = money in, false = money out
    val formattedAmount: String
)

@Serializable
data class TransactionListResponse(
    val transactions: List<Transaction>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
    val currentBalance: Double
)

@Serializable
data class TransactionFilters(
    val page: Int = 1,
    val limit: Int = 20,
    val type: Int? = null, // Filter by transaction type
    val startDate: String? = null, // ISO date string
    val endDate: String? = null,
    val sortOrder: String = "DESC" // ASC or DESC
)

// =====================================================
// ADD FUNDS REQUEST/RESPONSE
// =====================================================

@Serializable
data class AddFundsRequest(
    val amount: Double, // Amount to add in dollars
    val successUrl: String, // Redirect URL after successful payment
    val cancelUrl: String // Redirect URL if payment cancelled
)

@Serializable
data class AddFundsResponse(
    val checkoutUrl: String, // Stripe checkout URL
    val sessionId: String // Stripe session ID for verification
)

@Serializable
data class AddFundsCompleteRequest(
    val sessionId: String // Stripe session ID to verify
)

@Serializable
data class AddFundsCompleteResponse(
    val success: Boolean,
    val newBalance: Double,
    val amountAdded: Double,
    val transactionId: Long?
)

// =====================================================
// ADMIN: ADD CREDIT MANUALLY
// =====================================================

@Serializable
data class AddCreditRequest(
    val userId: Long,
    val amount: Double,
    val type: Int = TransactionType.PROMOTIONAL_CREDIT.code,
    val description: String? = null
)

@Serializable
data class AddCreditResponse(
    val success: Boolean,
    val newBalance: Double,
    val transactionId: Long?
)

// =====================================================
// REFUND REQUEST
// =====================================================

@Serializable
data class WalletRefundRequest(
    val orderId: Long,
    val amount: Double? = null, // null = full refund
    val reason: String? = null
)

@Serializable
data class WalletRefundResponse(
    val success: Boolean,
    val refundedAmount: Double,
    val newBalance: Double,
    val transactionId: Long?
)
