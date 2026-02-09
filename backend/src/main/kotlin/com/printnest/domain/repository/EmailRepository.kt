package com.printnest.domain.repository

import com.printnest.domain.models.*
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// =====================================================
// TABLE DEFINITION
// =====================================================

object EmailLogs : LongIdTable("email_logs") {
    val tenantId = long("tenant_id")
    val userId = long("user_id").nullable()
    val recipientEmail = varchar("recipient_email", 255)
    val recipientName = varchar("recipient_name", 255).nullable()
    val subject = varchar("subject", 500)
    val template = varchar("template", 100).nullable()
    val templateVariables = text("template_variables").nullable()
    val status = integer("status").default(0)
    val statusMessage = text("status_message").nullable()
    val messageId = varchar("message_id", 255).nullable()
    val sentAt = timestamp("sent_at").nullable()
    val openedAt = timestamp("opened_at").nullable()
    val clickedAt = timestamp("clicked_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// =====================================================
// REPOSITORY
// =====================================================

class EmailRepository : KoinComponent {

    // =====================================================
    // LOGGING
    // =====================================================

    /**
     * Log a sent email
     */
    fun logEmail(
        tenantId: Long,
        userId: Long? = null,
        recipientEmail: String,
        recipientName: String? = null,
        subject: String,
        template: String? = null,
        templateVariables: String? = null,
        status: Int,
        statusMessage: String? = null,
        messageId: String? = null
    ): Long = transaction {
        val now = Instant.now()

        EmailLogs.insertAndGetId {
            it[EmailLogs.tenantId] = tenantId
            it[EmailLogs.userId] = userId
            it[EmailLogs.recipientEmail] = recipientEmail
            it[EmailLogs.recipientName] = recipientName
            it[EmailLogs.subject] = subject.take(500)
            it[EmailLogs.template] = template
            it[EmailLogs.templateVariables] = templateVariables
            it[EmailLogs.status] = status
            it[EmailLogs.statusMessage] = statusMessage
            it[EmailLogs.messageId] = messageId
            if (status == EmailStatus.SENT.code) {
                it[sentAt] = now
            }
        }.value
    }

    // =====================================================
    // QUERY
    // =====================================================

    /**
     * Find email logs with pagination and filtering
     */
    fun findAll(tenantId: Long, filters: EmailLogFilters): Pair<List<EmailLog>, Int> = transaction {
        var query = EmailLogs.selectAll()
            .where { EmailLogs.tenantId eq tenantId }

        // Apply filters
        filters.status?.let { status ->
            query = query.andWhere { EmailLogs.status eq status }
        }

        filters.template?.let { template ->
            if (template.isNotBlank()) {
                query = query.andWhere { EmailLogs.template eq template }
            }
        }

        filters.recipientEmail?.let { email ->
            if (email.isNotBlank()) {
                query = query.andWhere { EmailLogs.recipientEmail like "%$email%" }
            }
        }

        filters.startDate?.let { startDateStr ->
            try {
                val startDate = LocalDate.parse(startDateStr).atStartOfDay().toInstant(ZoneOffset.UTC)
                query = query.andWhere { EmailLogs.createdAt greaterEq startDate }
            } catch (e: Exception) {
                // Invalid date format, ignore
            }
        }

        filters.endDate?.let { endDateStr ->
            try {
                val endDate = LocalDate.parse(endDateStr).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                query = query.andWhere { EmailLogs.createdAt less endDate }
            } catch (e: Exception) {
                // Invalid date format, ignore
            }
        }

        // Get total count
        val total = query.count().toInt()

        // Apply sorting
        val sortColumn = when (filters.sortBy) {
            "createdAt" -> EmailLogs.createdAt
            "sentAt" -> EmailLogs.sentAt
            "status" -> EmailLogs.status
            "recipientEmail" -> EmailLogs.recipientEmail
            "subject" -> EmailLogs.subject
            else -> EmailLogs.createdAt
        }

        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Apply pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val logs = query.map { it.toEmailLog() }

        Pair(logs, total)
    }

    /**
     * Find email log by ID
     */
    fun findById(id: Long, tenantId: Long): EmailLog? = transaction {
        EmailLogs.selectAll()
            .where { (EmailLogs.id eq id) and (EmailLogs.tenantId eq tenantId) }
            .singleOrNull()
            ?.toEmailLog()
    }

    /**
     * Find email log by message ID (Brevo message ID)
     */
    fun findByMessageId(messageId: String): EmailLog? = transaction {
        EmailLogs.selectAll()
            .where { EmailLogs.messageId eq messageId }
            .singleOrNull()
            ?.toEmailLog()
    }

    /**
     * Find recent emails for a user
     */
    fun findByUserId(tenantId: Long, userId: Long, limit: Int = 10): List<EmailLog> = transaction {
        EmailLogs.selectAll()
            .where { (EmailLogs.tenantId eq tenantId) and (EmailLogs.userId eq userId) }
            .orderBy(EmailLogs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toEmailLog() }
    }

    /**
     * Find emails by recipient
     */
    fun findByRecipient(tenantId: Long, recipientEmail: String, limit: Int = 10): List<EmailLog> = transaction {
        EmailLogs.selectAll()
            .where { (EmailLogs.tenantId eq tenantId) and (EmailLogs.recipientEmail eq recipientEmail) }
            .orderBy(EmailLogs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toEmailLog() }
    }

    // =====================================================
    // STATUS UPDATES (for webhooks)
    // =====================================================

    /**
     * Update email status when opened
     */
    fun markAsOpened(messageId: String): Boolean = transaction {
        val now = Instant.now()
        EmailLogs.update(where = { EmailLogs.messageId eq messageId }) {
            it[status] = EmailStatus.OPENED.code
            it[openedAt] = now
            it[updatedAt] = now
        } > 0
    }

    /**
     * Update email status when clicked
     */
    fun markAsClicked(messageId: String): Boolean = transaction {
        val now = Instant.now()
        EmailLogs.update(where = { EmailLogs.messageId eq messageId }) {
            it[status] = EmailStatus.CLICKED.code
            it[clickedAt] = now
            it[updatedAt] = now
        } > 0
    }

    /**
     * Update email status when bounced
     */
    fun markAsBounced(messageId: String, reason: String? = null): Boolean = transaction {
        val now = Instant.now()
        EmailLogs.update(where = { EmailLogs.messageId eq messageId }) {
            it[status] = EmailStatus.BOUNCED.code
            if (reason != null) {
                it[statusMessage] = reason
            }
            it[updatedAt] = now
        } > 0
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    /**
     * Get email statistics for a tenant
     */
    fun getStats(tenantId: Long, startDate: Instant? = null, endDate: Instant? = null): EmailStats = transaction {
        var baseQuery = EmailLogs.selectAll()
            .where { EmailLogs.tenantId eq tenantId }

        startDate?.let { start ->
            baseQuery = baseQuery.andWhere { EmailLogs.createdAt greaterEq start }
        }

        endDate?.let { end ->
            baseQuery = baseQuery.andWhere { EmailLogs.createdAt less end }
        }

        val allLogs = baseQuery.toList()

        val totalSent = allLogs.count { it[EmailLogs.status] >= EmailStatus.SENT.code }
        val totalFailed = allLogs.count { it[EmailLogs.status] == EmailStatus.FAILED.code }
        val totalBounced = allLogs.count { it[EmailLogs.status] == EmailStatus.BOUNCED.code }
        val totalOpened = allLogs.count { it[EmailLogs.status] >= EmailStatus.OPENED.code }
        val totalClicked = allLogs.count { it[EmailLogs.status] == EmailStatus.CLICKED.code }

        val byTemplate = allLogs
            .filter { it[EmailLogs.template] != null }
            .groupBy { it[EmailLogs.template]!! }
            .mapValues { it.value.size }

        val openRate = if (totalSent > 0) (totalOpened.toDouble() / totalSent * 100).toInt() else 0
        val clickRate = if (totalOpened > 0) (totalClicked.toDouble() / totalOpened * 100).toInt() else 0

        EmailStats(
            totalSent = totalSent,
            totalFailed = totalFailed,
            totalBounced = totalBounced,
            totalOpened = totalOpened,
            totalClicked = totalClicked,
            openRate = openRate,
            clickRate = clickRate,
            byTemplate = byTemplate
        )
    }

    // =====================================================
    // CLEANUP
    // =====================================================

    /**
     * Delete old email logs (older than X days)
     */
    fun deleteOldLogs(tenantId: Long, olderThanDays: Int): Int = transaction {
        val cutoffDate = Instant.now().minusSeconds(olderThanDays * 24L * 60 * 60)
        EmailLogs.deleteWhere {
            (EmailLogs.tenantId eq tenantId) and (createdAt less cutoffDate)
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun ResultRow.toEmailLog(): EmailLog = EmailLog(
        id = this[EmailLogs.id].value,
        tenantId = this[EmailLogs.tenantId],
        userId = this[EmailLogs.userId],
        recipientEmail = this[EmailLogs.recipientEmail],
        recipientName = this[EmailLogs.recipientName],
        subject = this[EmailLogs.subject],
        template = this[EmailLogs.template],
        templateVariables = this[EmailLogs.templateVariables],
        status = this[EmailLogs.status],
        statusMessage = this[EmailLogs.statusMessage],
        messageId = this[EmailLogs.messageId],
        sentAt = this[EmailLogs.sentAt]?.toString(),
        openedAt = this[EmailLogs.openedAt]?.toString(),
        clickedAt = this[EmailLogs.clickedAt]?.toString(),
        createdAt = this[EmailLogs.createdAt].toString(),
        updatedAt = this[EmailLogs.updatedAt].toString()
    )
}

// =====================================================
// STATS MODEL
// =====================================================

data class EmailStats(
    val totalSent: Int,
    val totalFailed: Int,
    val totalBounced: Int,
    val totalOpened: Int,
    val totalClicked: Int,
    val openRate: Int, // Percentage
    val clickRate: Int, // Percentage
    val byTemplate: Map<String, Int>
)
