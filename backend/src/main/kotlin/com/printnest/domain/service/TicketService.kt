package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.TicketRepository
import com.printnest.domain.tables.Users
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class TicketService(
    private val ticketRepository: TicketRepository,
    private val settingsService: SettingsService
) {

    // =====================================================
    // CATEGORIES
    // =====================================================

    fun getCategories(): List<TicketCategory> {
        return ticketRepository.findAllCategories()
    }

    // =====================================================
    // TICKETS
    // =====================================================

    fun getTickets(tenantId: Long, userId: Long, userRole: String, filters: TicketFilters): TicketListResponse {
        val (tickets, total) = ticketRepository.findAll(tenantId, userId, userRole, filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return TicketListResponse(
            tickets = tickets,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getTicket(id: Long, tenantId: Long, userId: Long): Result<Ticket> {
        val ticket = ticketRepository.findByIdWithMessages(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Ticket not found"))

        // Check access
        if (!canAccessTicket(ticket, userId)) {
            return Result.failure(IllegalAccessException("Access denied"))
        }

        // Mark as read
        ticketRepository.markAsRead(id, tenantId, userId)

        return Result.success(ticket)
    }

    fun createTicket(tenantId: Long, userId: Long, request: CreateTicketRequest): Result<Ticket> {
        // Validate
        if (request.title.isBlank()) {
            return Result.failure(IllegalArgumentException("Title is required"))
        }
        if (request.message.isBlank()) {
            return Result.failure(IllegalArgumentException("Message is required"))
        }

        // Check if category requires order
        val category = request.categoryId?.let { catId ->
            ticketRepository.findAllCategories().find { it.id == catId }
        }
        if (category?.requiresOrder == true && request.orderId == null) {
            return Result.failure(IllegalArgumentException("Order is required for this category"))
        }

        // Find parent dealer (producer) to assign ticket to
        val assignedTo = findParentDealer(userId, tenantId)

        // Create ticket
        val ticket = ticketRepository.create(tenantId, userId, assignedTo, request)

        // Send notification to assignee
        assignedTo?.let { parentId ->
            notifyNewTicket(tenantId, parentId, ticket)
        }

        return Result.success(ticket)
    }

    fun sendMessage(ticketId: Long, tenantId: Long, userId: Long, request: SendMessageRequest): Result<TicketMessage> {
        val ticket = ticketRepository.findById(ticketId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Ticket not found"))

        // Check access
        if (!canAccessTicket(ticket, userId)) {
            return Result.failure(IllegalAccessException("Access denied"))
        }

        // Check if ticket is closed
        if (ticket.status == TicketStatus.CLOSED.code) {
            return Result.failure(IllegalStateException("Cannot send message to closed ticket"))
        }

        // Validate
        if (request.content.isBlank()) {
            return Result.failure(IllegalArgumentException("Message content is required"))
        }

        // Create message
        val message = ticketRepository.createMessage(
            ticketId = ticketId,
            senderId = userId,
            content = request.content,
            messageType = request.messageType,
            attachments = request.attachments
        )

        // Notify the other party
        val recipientId = if (userId == ticket.createdBy) ticket.assignedTo else ticket.createdBy
        recipientId?.let { notifyNewMessage(tenantId, it, ticket, message) }

        return Result.success(message)
    }

    fun closeTicket(id: Long, tenantId: Long, userId: Long): Result<Ticket> {
        val ticket = ticketRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Ticket not found"))

        if (!canAccessTicket(ticket, userId)) {
            return Result.failure(IllegalAccessException("Access denied"))
        }

        if (ticket.status == TicketStatus.CLOSED.code) {
            return Result.failure(IllegalStateException("Ticket is already closed"))
        }

        ticketRepository.updateStatus(id, tenantId, userId, TicketStatus.CLOSED.code)

        // Notify other party
        val recipientId = if (userId == ticket.createdBy) ticket.assignedTo else ticket.createdBy
        recipientId?.let {
            settingsService.notifyUser(
                tenantId = tenantId,
                userId = it,
                title = "Ticket Closed",
                message = "Ticket \"${ticket.title}\" has been closed.",
                type = NotificationType.SUPPORT,
                url = "/tickets/$id"
            )
        }

        return Result.success(ticketRepository.findByIdWithMessages(id, tenantId)!!)
    }

    fun reopenTicket(id: Long, tenantId: Long, userId: Long): Result<Ticket> {
        val ticket = ticketRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Ticket not found"))

        if (!canAccessTicket(ticket, userId)) {
            return Result.failure(IllegalAccessException("Access denied"))
        }

        if (ticket.status != TicketStatus.CLOSED.code) {
            return Result.failure(IllegalStateException("Ticket is not closed"))
        }

        ticketRepository.updateStatus(id, tenantId, userId, TicketStatus.OPEN.code)

        // Notify other party
        val recipientId = if (userId == ticket.createdBy) ticket.assignedTo else ticket.createdBy
        recipientId?.let {
            settingsService.notifyUser(
                tenantId = tenantId,
                userId = it,
                title = "Ticket Reopened",
                message = "Ticket \"${ticket.title}\" has been reopened.",
                type = NotificationType.SUPPORT,
                url = "/tickets/$id"
            )
        }

        return Result.success(ticketRepository.findByIdWithMessages(id, tenantId)!!)
    }

    fun markAsRead(id: Long, tenantId: Long, userId: Long): Result<Boolean> {
        val ticket = ticketRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Ticket not found"))

        if (!canAccessTicket(ticket, userId)) {
            return Result.failure(IllegalAccessException("Access denied"))
        }

        return Result.success(ticketRepository.markAsRead(id, tenantId, userId))
    }

    // =====================================================
    // STATS
    // =====================================================

    fun getStats(tenantId: Long, userId: Long, userRole: String): TicketStats {
        return ticketRepository.getStats(tenantId, userId, userRole)
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun canAccessTicket(ticket: Ticket, userId: Long): Boolean {
        // User can access if they created it, are assigned to it, or are a participant
        return ticket.createdBy == userId ||
               ticket.assignedTo == userId
        // TODO: Check participants table for group tickets
    }

    private fun findParentDealer(userId: Long, tenantId: Long): Long? = transaction {
        // Find the parent_user_id (producer) for this subdealer
        Users.selectAll()
            .where { (Users.id eq userId) and (Users.tenantId eq tenantId) }
            .singleOrNull()
            ?.get(Users.parentUserId)?.value
    }

    private fun notifyNewTicket(tenantId: Long, recipientId: Long, ticket: Ticket) {
        settingsService.notifyUser(
            tenantId = tenantId,
            userId = recipientId,
            title = "New Support Ticket",
            message = "New ticket: \"${ticket.title}\"",
            type = NotificationType.SUPPORT,
            priority = NotificationPriority.HIGH,
            url = "/tickets/${ticket.id}"
        )
    }

    private fun notifyNewMessage(tenantId: Long, recipientId: Long, ticket: Ticket, message: TicketMessage) {
        settingsService.notifyUser(
            tenantId = tenantId,
            userId = recipientId,
            title = "New Message",
            message = "New message in ticket \"${ticket.title}\": ${message.content.take(50)}...",
            type = NotificationType.SUPPORT,
            priority = NotificationPriority.MEDIUM,
            url = "/tickets/${ticket.id}"
        )
    }

    // =====================================================
    // ORDER-BASED TICKET HELPERS
    // =====================================================

    fun getTicketsByOrder(orderId: Long, tenantId: Long, userId: Long, userRole: String): List<TicketListItem> {
        val filters = TicketFilters(orderId = orderId)
        val (tickets, _) = ticketRepository.findAll(tenantId, userId, userRole, filters)
        return tickets
    }

    fun hasOpenTicketForOrder(orderId: Long, tenantId: Long): Boolean {
        val filters = TicketFilters(orderId = orderId, statuses = listOf(0, 1))
        val (tickets, _) = ticketRepository.findAll(tenantId, 0L, "owner", filters)
        return tickets.isNotEmpty()
    }
}
