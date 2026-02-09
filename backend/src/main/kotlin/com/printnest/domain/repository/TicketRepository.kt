package com.printnest.domain.repository

import com.printnest.domain.models.*
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import java.time.Instant

// =====================================================
// TABLE DEFINITIONS
// =====================================================

object TicketCategories : Table("ticket_categories") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val requiresOrder = bool("requires_order").default(false)
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1)

    override val primaryKey = PrimaryKey(id)
}

object Tickets : LongIdTable("tickets") {
    val tenantId = long("tenant_id")
    val createdBy = long("created_by")
    val assignedTo = long("assigned_to").nullable()
    val orderId = long("order_id").nullable()
    val categoryId = integer("category_id").nullable()
    val title = varchar("title", 255)
    val status = integer("status").default(1)
    val priority = varchar("priority", 20).default("medium")
    val lastMessageAt = timestamp("last_message_at").nullable()
    val lastMessageBy = long("last_message_by").nullable()
    val lastMessagePreview = text("last_message_preview").nullable()
    val unreadCountCreator = integer("unread_count_creator").default(0)
    val unreadCountAssignee = integer("unread_count_assignee").default(0)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    val closedAt = timestamp("closed_at").nullable()
    val closedBy = long("closed_by").nullable()
}

object TicketMessages : LongIdTable("ticket_messages") {
    val ticketId = long("ticket_id")
    val senderId = long("sender_id")
    val content = text("content")
    val messageType = varchar("message_type", 20).default("text")
    val readBy = text("read_by").default("{}")
    val isSystemMessage = bool("is_system_message").default(false)
    val isEdited = bool("is_edited").default(false)
    val isDeleted = bool("is_deleted").default(false)
    val editedAt = timestamp("edited_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object TicketAttachments : LongIdTable("ticket_attachments") {
    val messageId = long("message_id")
    val fileName = varchar("file_name", 255)
    val fileUrl = text("file_url")
    val fileType = varchar("file_type", 50).nullable()
    val fileSize = long("file_size").nullable()
    val thumbnailUrl = text("thumbnail_url").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object TicketParticipants : LongIdTable("ticket_participants") {
    val ticketId = long("ticket_id")
    val userId = long("user_id")
    val role = varchar("role", 50).default("participant")
    val lastReadAt = timestamp("last_read_at").nullable()
    val isMuted = bool("is_muted").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// =====================================================
// REPOSITORY
// =====================================================

class TicketRepository : KoinComponent {

    // =====================================================
    // CATEGORIES
    // =====================================================

    fun findAllCategories(): List<TicketCategory> = transaction {
        TicketCategories.selectAll()
            .where { TicketCategories.status eq 1 }
            .orderBy(TicketCategories.sortOrder, SortOrder.ASC)
            .map { it.toTicketCategory() }
    }

    // =====================================================
    // TICKETS - CRUD
    // =====================================================

    fun findAll(tenantId: Long, userId: Long, userRole: String, filters: TicketFilters): Pair<List<TicketListItem>, Int> = transaction {
        var query = Tickets.selectAll()
            .where { Tickets.tenantId eq tenantId }

        // Role-based filtering
        when (userRole) {
            "customer", "subdealer" -> {
                // Can only see tickets they created
                query = query.andWhere { Tickets.createdBy eq userId }
            }
            "producer", "admin", "subscriber" -> {
                // Can see tickets assigned to them or created by their subdealers
                query = query.andWhere {
                    (Tickets.assignedTo eq userId) or (Tickets.createdBy eq userId)
                }
            }
            // Owner/super-admin sees all
        }

        // Apply filters
        filters.status?.let { status ->
            query = query.andWhere { Tickets.status eq status }
        }

        filters.statuses?.let { statuses ->
            if (statuses.isNotEmpty()) {
                query = query.andWhere { Tickets.status inList statuses }
            }
        }

        filters.categoryId?.let { catId ->
            query = query.andWhere { Tickets.categoryId eq catId }
        }

        filters.orderId?.let { orderId ->
            query = query.andWhere { Tickets.orderId eq orderId }
        }

        filters.createdBy?.let { createdBy ->
            query = query.andWhere { Tickets.createdBy eq createdBy }
        }

        filters.assignedTo?.let { assignedTo ->
            query = query.andWhere { Tickets.assignedTo eq assignedTo }
        }

        filters.search?.let { search ->
            if (search.isNotBlank()) {
                query = query.andWhere {
                    (Tickets.title like "%$search%") or
                    (Tickets.lastMessagePreview like "%$search%")
                }
            }
        }

        // Get total count
        val total = query.count().toInt()

        // Apply sorting
        val sortColumn = when (filters.sortBy) {
            "createdAt" -> Tickets.createdAt
            "updatedAt" -> Tickets.updatedAt
            "lastMessageAt" -> Tickets.lastMessageAt
            "status" -> Tickets.status
            "priority" -> Tickets.priority
            else -> Tickets.lastMessageAt
        }

        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Apply pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val tickets = query.map { row ->
            val ticketId = row[Tickets.id].value
            val creatorId = row[Tickets.createdBy]
            val assigneeId = row[Tickets.assignedTo]
            val catId = row[Tickets.categoryId]

            // Determine unread count based on current user
            val unreadCount = when (userId) {
                creatorId -> row[Tickets.unreadCountCreator]
                assigneeId -> row[Tickets.unreadCountAssignee]
                else -> 0
            }

            TicketListItem(
                id = ticketId,
                tenantId = row[Tickets.tenantId],
                title = row[Tickets.title],
                status = row[Tickets.status],
                priority = row[Tickets.priority],
                categoryId = catId,
                categoryName = catId?.let { findCategoryName(it) },
                orderId = row[Tickets.orderId],
                orderNumber = row[Tickets.orderId]?.let { findOrderNumber(it) },
                createdBy = creatorId,
                creatorName = findUserName(creatorId),
                assignedTo = assigneeId,
                assigneeName = assigneeId?.let { findUserName(it) },
                lastMessageAt = row[Tickets.lastMessageAt]?.toString(),
                lastMessagePreview = row[Tickets.lastMessagePreview],
                unreadCount = unreadCount,
                createdAt = row[Tickets.createdAt].toString(),
                updatedAt = row[Tickets.updatedAt].toString()
            )
        }

        Pair(tickets, total)
    }

    fun findById(id: Long, tenantId: Long): Ticket? = transaction {
        Tickets.selectAll()
            .where { (Tickets.id eq id) and (Tickets.tenantId eq tenantId) }
            .singleOrNull()
            ?.toTicket()
    }

    fun findByIdWithMessages(id: Long, tenantId: Long): Ticket? = transaction {
        val ticket = findById(id, tenantId) ?: return@transaction null

        val messages = findMessagesByTicketId(id)

        ticket.copy(
            messages = messages,
            messageCount = messages.size
        )
    }

    fun create(tenantId: Long, createdBy: Long, assignedTo: Long?, request: CreateTicketRequest): Ticket = transaction {
        val now = Instant.now()

        val ticketId = Tickets.insertAndGetId {
            it[Tickets.tenantId] = tenantId
            it[Tickets.createdBy] = createdBy
            it[Tickets.assignedTo] = assignedTo
            it[orderId] = request.orderId
            it[categoryId] = request.categoryId
            it[title] = request.title
            it[status] = TicketStatus.OPEN.code
            it[priority] = request.priority
            it[lastMessageAt] = now
            it[lastMessageBy] = createdBy
            it[lastMessagePreview] = request.message.take(100)
            it[unreadCountAssignee] = if (assignedTo != null) 1 else 0
        }.value

        // Add participants
        addParticipant(ticketId, createdBy, "creator")
        assignedTo?.let { addParticipant(ticketId, it, "assignee") }

        // Create initial message
        createMessage(ticketId, createdBy, request.message, "text", request.attachments)

        findByIdWithMessages(ticketId, tenantId)!!
    }

    fun updateStatus(id: Long, tenantId: Long, userId: Long, newStatus: Int): Boolean = transaction {
        val now = Instant.now()

        val updated = Tickets.update(
            where = { (Tickets.id eq id) and (Tickets.tenantId eq tenantId) }
        ) {
            it[status] = newStatus
            it[updatedAt] = now
            if (newStatus == TicketStatus.CLOSED.code) {
                it[closedAt] = now
                it[closedBy] = userId
            }
        } > 0

        if (updated && newStatus == TicketStatus.CLOSED.code) {
            // Add system message
            createSystemMessage(id, userId, "Ticket closed")
        } else if (updated && newStatus == TicketStatus.OPEN.code) {
            // Reopen - clear closed info and add system message
            Tickets.update(where = { Tickets.id eq id }) {
                it[closedAt] = null
                it[closedBy] = null
            }
            createSystemMessage(id, userId, "Ticket reopened")
        }

        updated
    }

    fun markAsRead(id: Long, tenantId: Long, userId: Long): Boolean = transaction {
        val ticket = findById(id, tenantId) ?: return@transaction false

        // Update unread count based on who is reading
        when (userId) {
            ticket.createdBy -> {
                Tickets.update(where = { Tickets.id eq id }) {
                    it[unreadCountCreator] = 0
                }
            }
            ticket.assignedTo -> {
                Tickets.update(where = { Tickets.id eq id }) {
                    it[unreadCountAssignee] = 0
                }
            }
        }

        // Update ticket status to READ if it was OPEN and read by assignee
        if (ticket.status == TicketStatus.OPEN.code && userId == ticket.assignedTo) {
            Tickets.update(where = { Tickets.id eq id }) {
                it[status] = TicketStatus.READ.code
            }
        }

        true
    }

    // =====================================================
    // MESSAGES
    // =====================================================

    fun findMessagesByTicketId(ticketId: Long): List<TicketMessage> = transaction {
        TicketMessages.selectAll()
            .where { (TicketMessages.ticketId eq ticketId) and (TicketMessages.isDeleted eq false) }
            .orderBy(TicketMessages.createdAt, SortOrder.ASC)
            .map { row ->
                val messageId = row[TicketMessages.id].value
                val senderId = row[TicketMessages.senderId]

                TicketMessage(
                    id = messageId,
                    ticketId = row[TicketMessages.ticketId],
                    senderId = senderId,
                    senderName = findUserName(senderId),
                    senderEmail = findUserEmail(senderId),
                    senderRole = findUserRole(senderId),
                    content = row[TicketMessages.content],
                    messageType = row[TicketMessages.messageType],
                    isSystemMessage = row[TicketMessages.isSystemMessage],
                    isEdited = row[TicketMessages.isEdited],
                    isDeleted = row[TicketMessages.isDeleted],
                    editedAt = row[TicketMessages.editedAt]?.toString(),
                    createdAt = row[TicketMessages.createdAt].toString(),
                    attachments = findAttachmentsByMessageId(messageId)
                )
            }
    }

    fun createMessage(
        ticketId: Long,
        senderId: Long,
        content: String,
        messageType: String = "text",
        attachments: List<AttachmentInput> = emptyList(),
        isSystem: Boolean = false
    ): TicketMessage = transaction {
        val messageId = TicketMessages.insertAndGetId {
            it[TicketMessages.ticketId] = ticketId
            it[TicketMessages.senderId] = senderId
            it[TicketMessages.content] = content
            it[TicketMessages.messageType] = messageType
            it[isSystemMessage] = isSystem
        }.value

        // Add attachments
        attachments.forEach { attachment ->
            TicketAttachments.insert {
                it[TicketAttachments.messageId] = messageId
                it[fileName] = attachment.fileName
                it[fileUrl] = attachment.fileUrl
                it[fileType] = attachment.fileType
                it[fileSize] = attachment.fileSize
                it[thumbnailUrl] = attachment.thumbnailUrl
            }
        }

        // Update ticket status to OPEN (unread) if it was READ
        val ticket = Tickets.selectAll().where { Tickets.id eq ticketId }.single()
        if (ticket[Tickets.status] == TicketStatus.READ.code) {
            Tickets.update(where = { Tickets.id eq ticketId }) {
                it[status] = TicketStatus.OPEN.code
            }
        }

        // Note: Trigger handles updating lastMessage fields and unread counts

        TicketMessage(
            id = messageId,
            ticketId = ticketId,
            senderId = senderId,
            senderName = findUserName(senderId),
            content = content,
            messageType = messageType,
            isSystemMessage = isSystem,
            createdAt = Instant.now().toString(),
            attachments = findAttachmentsByMessageId(messageId)
        )
    }

    fun createSystemMessage(ticketId: Long, userId: Long, content: String): TicketMessage {
        return createMessage(ticketId, userId, content, "system", emptyList(), isSystem = true)
    }

    // =====================================================
    // ATTACHMENTS
    // =====================================================

    fun findAttachmentsByMessageId(messageId: Long): List<TicketAttachment> = transaction {
        TicketAttachments.selectAll()
            .where { TicketAttachments.messageId eq messageId }
            .map { row ->
                TicketAttachment(
                    id = row[TicketAttachments.id].value,
                    messageId = row[TicketAttachments.messageId],
                    fileName = row[TicketAttachments.fileName],
                    fileUrl = row[TicketAttachments.fileUrl],
                    fileType = row[TicketAttachments.fileType],
                    fileSize = row[TicketAttachments.fileSize],
                    thumbnailUrl = row[TicketAttachments.thumbnailUrl],
                    createdAt = row[TicketAttachments.createdAt].toString()
                )
            }
    }

    // =====================================================
    // PARTICIPANTS
    // =====================================================

    fun addParticipant(ticketId: Long, userId: Long, role: String): Boolean = transaction {
        TicketParticipants.insert {
            it[TicketParticipants.ticketId] = ticketId
            it[TicketParticipants.userId] = userId
            it[TicketParticipants.role] = role
        }
        true
    }

    // =====================================================
    // STATS
    // =====================================================

    fun getStats(tenantId: Long, userId: Long, userRole: String): TicketStats = transaction {
        var baseQuery = Tickets.selectAll()
            .where { Tickets.tenantId eq tenantId }

        // Role-based filtering (same as findAll)
        when (userRole) {
            "customer", "subdealer" -> {
                baseQuery = baseQuery.andWhere { Tickets.createdBy eq userId }
            }
            "producer", "admin", "subscriber" -> {
                baseQuery = baseQuery.andWhere {
                    (Tickets.assignedTo eq userId) or (Tickets.createdBy eq userId)
                }
            }
        }

        val allTickets = baseQuery.toList()

        val totalOpen = allTickets.count { it[Tickets.status] >= 0 }
        val totalClosed = allTickets.count { it[Tickets.status] == -1 }

        // Count unread based on user perspective
        val totalUnread = allTickets.count { row ->
            when (userId) {
                row[Tickets.createdBy] -> row[Tickets.unreadCountCreator] > 0
                row[Tickets.assignedTo] -> row[Tickets.unreadCountAssignee] > 0
                else -> false
            }
        }

        val byCategory = allTickets
            .filter { it[Tickets.status] >= 0 }
            .groupBy { it[Tickets.categoryId] }
            .mapKeys { findCategoryName(it.key ?: 0) ?: "Other" }
            .mapValues { it.value.size }

        val byPriority = allTickets
            .filter { it[Tickets.status] >= 0 }
            .groupBy { it[Tickets.priority] }
            .mapValues { it.value.size }

        TicketStats(
            totalOpen = totalOpen,
            totalUnread = totalUnread,
            totalClosed = totalClosed,
            byCategory = byCategory,
            byPriority = byPriority
        )
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun ResultRow.toTicketCategory(): TicketCategory = TicketCategory(
        id = this[TicketCategories.id],
        name = this[TicketCategories.name],
        description = this[TicketCategories.description],
        requiresOrder = this[TicketCategories.requiresOrder],
        sortOrder = this[TicketCategories.sortOrder],
        status = this[TicketCategories.status]
    )

    private fun ResultRow.toTicket(): Ticket {
        val creatorId = this[Tickets.createdBy]
        val assigneeId = this[Tickets.assignedTo]
        val catId = this[Tickets.categoryId]

        return Ticket(
            id = this[Tickets.id].value,
            tenantId = this[Tickets.tenantId],
            createdBy = creatorId,
            assignedTo = assigneeId,
            orderId = this[Tickets.orderId],
            categoryId = catId,
            title = this[Tickets.title],
            status = this[Tickets.status],
            priority = this[Tickets.priority],
            lastMessageAt = this[Tickets.lastMessageAt]?.toString(),
            lastMessageBy = this[Tickets.lastMessageBy],
            lastMessagePreview = this[Tickets.lastMessagePreview],
            unreadCountCreator = this[Tickets.unreadCountCreator],
            unreadCountAssignee = this[Tickets.unreadCountAssignee],
            createdAt = this[Tickets.createdAt].toString(),
            updatedAt = this[Tickets.updatedAt].toString(),
            closedAt = this[Tickets.closedAt]?.toString(),
            closedBy = this[Tickets.closedBy],
            category = catId?.let { findCategory(it) },
            creatorName = findUserName(creatorId),
            creatorEmail = findUserEmail(creatorId),
            assigneeName = assigneeId?.let { findUserName(it) },
            assigneeEmail = assigneeId?.let { findUserEmail(it) },
            orderNumber = this[Tickets.orderId]?.let { findOrderNumber(it) }
        )
    }

    private fun findCategoryName(categoryId: Int): String? = transaction {
        TicketCategories.selectAll()
            .where { TicketCategories.id eq categoryId }
            .singleOrNull()
            ?.get(TicketCategories.name)
    }

    private fun findCategory(categoryId: Int): TicketCategory? = transaction {
        TicketCategories.selectAll()
            .where { TicketCategories.id eq categoryId }
            .singleOrNull()
            ?.toTicketCategory()
    }

    private fun findUserName(userId: Long): String? = transaction {
        // Using raw SQL to query users table
        exec("SELECT first_name, last_name FROM users WHERE id = $userId") { rs ->
            if (rs.next()) {
                val firstName = rs.getString("first_name") ?: ""
                val lastName = rs.getString("last_name") ?: ""
                "$firstName $lastName".trim().ifEmpty { null }
            } else null
        }
    }

    private fun findUserEmail(userId: Long): String? = transaction {
        exec("SELECT email FROM users WHERE id = $userId") { rs ->
            if (rs.next()) rs.getString("email") else null
        }
    }

    private fun findUserRole(userId: Long): String? = transaction {
        exec("SELECT role FROM users WHERE id = $userId") { rs ->
            if (rs.next()) rs.getString("role") else null
        }
    }

    private fun findOrderNumber(orderId: Long): String? = transaction {
        exec("SELECT int_order_id FROM orders WHERE id = $orderId") { rs ->
            if (rs.next()) rs.getString("int_order_id") else null
        }
    }
}
