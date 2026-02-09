package com.printnest.domain.repository

import com.printnest.domain.models.Transaction
import com.printnest.domain.models.TransactionFilters
import com.printnest.domain.models.TransactionType
import com.printnest.domain.tables.Transactions
import com.printnest.domain.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class WalletRepository {

    // =====================================================
    // BALANCE OPERATIONS
    // =====================================================

    fun getBalance(tenantId: Long, userId: Long): BigDecimal = transaction {
        Users.selectAll()
            .where { (Users.tenantId eq tenantId) and (Users.id eq userId) }
            .singleOrNull()
            ?.get(Users.totalCredit)
            ?: BigDecimal.ZERO
    }

    fun updateBalance(tenantId: Long, userId: Long, newBalance: BigDecimal): Boolean = transaction {
        Users.update({
            (Users.tenantId eq tenantId) and (Users.id eq userId)
        }) {
            it[totalCredit] = newBalance
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // TRANSACTION OPERATIONS
    // =====================================================

    fun createTransaction(
        tenantId: Long,
        userId: Long,
        type: Int,
        amount: BigDecimal,
        description: String?,
        referenceId: String?,
        balanceBefore: BigDecimal,
        balanceAfter: BigDecimal
    ): Long = transaction {
        Transactions.insertAndGetId {
            it[Transactions.tenantId] = tenantId
            it[Transactions.userId] = userId
            it[Transactions.type] = type
            it[Transactions.amount] = amount
            it[Transactions.description] = description
            it[Transactions.referenceId] = referenceId
            it[Transactions.balanceBefore] = balanceBefore
            it[Transactions.balanceAfter] = balanceAfter
        }.value
    }

    fun getTransactions(
        tenantId: Long,
        userId: Long,
        filters: TransactionFilters
    ): Pair<List<Transaction>, Int> = transaction {
        var query = Transactions.selectAll()
            .where { (Transactions.tenantId eq tenantId) and (Transactions.userId eq userId) }

        // Type filter
        filters.type?.let { type ->
            query = query.andWhere { Transactions.type eq type }
        }

        // Date range filter
        filters.startDate?.let { startStr ->
            try {
                val startDate = LocalDate.parse(startStr)
                val startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                query = query.andWhere { Transactions.createdAt greaterEq startInstant }
            } catch (_: Exception) {}
        }

        filters.endDate?.let { endStr ->
            try {
                val endDate = LocalDate.parse(endStr)
                val endInstant = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                query = query.andWhere { Transactions.createdAt less endInstant }
            } catch (_: Exception) {}
        }

        // Count total
        val total = query.count().toInt()

        // Sorting
        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(Transactions.createdAt, sortOrder)

        // Pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val transactions = query.map { row ->
            val typeCode = row[Transactions.type]
            val typeLabel = TransactionType.fromCode(typeCode)?.label ?: "Unknown"
            val amount = row[Transactions.amount].toDouble()

            // Determine if it's a credit (money in) or debit (money out)
            val isCredit = when (typeCode) {
                TransactionType.PURCHASE.code, TransactionType.MULTI_PURCHASE.code -> false
                else -> amount > 0
            }

            Transaction(
                id = row[Transactions.id].value,
                type = typeCode,
                typeLabel = typeLabel,
                amount = amount,
                description = row[Transactions.description],
                referenceId = row[Transactions.referenceId],
                balanceBefore = row[Transactions.balanceBefore].toDouble(),
                balanceAfter = row[Transactions.balanceAfter].toDouble(),
                createdAt = row[Transactions.createdAt].toString(),
                isCredit = isCredit,
                formattedAmount = formatAmount(amount, isCredit)
            )
        }

        Pair(transactions, total)
    }

    fun getTransactionById(transactionId: Long, tenantId: Long): Transaction? = transaction {
        Transactions.selectAll()
            .where { (Transactions.id eq transactionId) and (Transactions.tenantId eq tenantId) }
            .singleOrNull()
            ?.let { row ->
                val typeCode = row[Transactions.type]
                val typeLabel = TransactionType.fromCode(typeCode)?.label ?: "Unknown"
                val amount = row[Transactions.amount].toDouble()
                val isCredit = amount > 0

                Transaction(
                    id = row[Transactions.id].value,
                    type = typeCode,
                    typeLabel = typeLabel,
                    amount = amount,
                    description = row[Transactions.description],
                    referenceId = row[Transactions.referenceId],
                    balanceBefore = row[Transactions.balanceBefore].toDouble(),
                    balanceAfter = row[Transactions.balanceAfter].toDouble(),
                    createdAt = row[Transactions.createdAt].toString(),
                    isCredit = isCredit,
                    formattedAmount = formatAmount(amount, isCredit)
                )
            }
    }

    // Find by reference ID (for order payments, stripe sessions, etc.)
    fun getTransactionsByReference(
        tenantId: Long,
        referenceId: String
    ): List<Transaction> = transaction {
        Transactions.selectAll()
            .where { (Transactions.tenantId eq tenantId) and (Transactions.referenceId eq referenceId) }
            .orderBy(Transactions.createdAt, SortOrder.DESC)
            .map { row ->
                val typeCode = row[Transactions.type]
                val typeLabel = TransactionType.fromCode(typeCode)?.label ?: "Unknown"
                val amount = row[Transactions.amount].toDouble()
                val isCredit = amount > 0

                Transaction(
                    id = row[Transactions.id].value,
                    type = typeCode,
                    typeLabel = typeLabel,
                    amount = amount,
                    description = row[Transactions.description],
                    referenceId = row[Transactions.referenceId],
                    balanceBefore = row[Transactions.balanceBefore].toDouble(),
                    balanceAfter = row[Transactions.balanceAfter].toDouble(),
                    createdAt = row[Transactions.createdAt].toString(),
                    isCredit = isCredit,
                    formattedAmount = formatAmount(amount, isCredit)
                )
            }
    }

    // =====================================================
    // STRIPE SESSION TRACKING
    // =====================================================

    // We can use the Payments table to track Stripe sessions for add funds
    fun createPendingPayment(
        tenantId: Long,
        userId: Long,
        amount: BigDecimal,
        stripeSessionId: String
    ): Long = transaction {
        com.printnest.domain.tables.Payments.insertAndGetId {
            it[com.printnest.domain.tables.Payments.tenantId] = tenantId
            it[com.printnest.domain.tables.Payments.userId] = userId
            it[com.printnest.domain.tables.Payments.orderId] = null
            it[paymentMethod] = "stripe"
            it[com.printnest.domain.tables.Payments.amount] = amount
            it[status] = "pending"
            it[com.printnest.domain.tables.Payments.stripeSessionId] = stripeSessionId
            it[metadata] = """{"type": "add_funds"}"""
        }.value
    }

    fun getPaymentBySessionId(stripeSessionId: String): PaymentRecord? = transaction {
        com.printnest.domain.tables.Payments.selectAll()
            .where { com.printnest.domain.tables.Payments.stripeSessionId eq stripeSessionId }
            .singleOrNull()
            ?.let { row ->
                PaymentRecord(
                    id = row[com.printnest.domain.tables.Payments.id].value,
                    tenantId = row[com.printnest.domain.tables.Payments.tenantId].value,
                    userId = row[com.printnest.domain.tables.Payments.userId].value,
                    orderId = row[com.printnest.domain.tables.Payments.orderId]?.value,
                    amount = row[com.printnest.domain.tables.Payments.amount],
                    status = row[com.printnest.domain.tables.Payments.status],
                    stripeSessionId = row[com.printnest.domain.tables.Payments.stripeSessionId],
                    stripePaymentIntent = row[com.printnest.domain.tables.Payments.stripePaymentIntent]
                )
            }
    }

    fun completePayment(paymentId: Long, stripePaymentIntent: String?): Boolean = transaction {
        com.printnest.domain.tables.Payments.update({
            com.printnest.domain.tables.Payments.id eq paymentId
        }) {
            it[status] = "completed"
            it[com.printnest.domain.tables.Payments.stripePaymentIntent] = stripePaymentIntent
            it[completedAt] = Instant.now()
        } > 0
    }

    fun failPayment(paymentId: Long, reason: String?): Boolean = transaction {
        com.printnest.domain.tables.Payments.update({
            com.printnest.domain.tables.Payments.id eq paymentId
        }) {
            it[status] = "failed"
            it[metadata] = """{"error": "${reason ?: "Unknown error"}"}"""
        } > 0
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun formatAmount(amount: Double, isCredit: Boolean): String {
        val absAmount = kotlin.math.abs(amount)
        val formatted = String.format("$%.2f", absAmount)
        return if (isCredit) "+$formatted" else "-$formatted"
    }
}

// Internal data class for payment records
data class PaymentRecord(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val orderId: Long?,
    val amount: BigDecimal,
    val status: String,
    val stripeSessionId: String?,
    val stripePaymentIntent: String?
)
